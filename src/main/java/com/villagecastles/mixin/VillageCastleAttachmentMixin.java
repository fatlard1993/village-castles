package com.villagecastles.mixin;

import com.villagecastles.VillageCastles;
import com.villagecastles.util.StructureHelper;
import com.villagecastles.worldgen.CastleGroundsPiece;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * After a village finishes jigsaw assembly, attach a castle to the village edge.
 *
 * Targets {@code lambda$addPieces$2} in JigsawPlacement: the {@code Structure.GenerationStub}
 * generator body extracted from the public {@code addPieces()}. It fires ONCE PER STRUCTURE
 * (not per piece): it places the root piece, runs the full recursive jigsaw expansion, and
 * adds every assembled piece to the StructurePiecesBuilder before returning. Injecting at
 * RETURN therefore sees the completed village in {@code collector}.
 *
 * PARAMETER SEMANTICS (verified against the 26.3-snapshot-8 bytecode with javap -c; the
 * captured locals are positional and unnamed, so these are easy to misread):
 *   int #1  = max jigsaw depth budget (villages: 6). The lambda bails early if <= 0.
 *             THIS IS NOT THE CURRENT RECURSION DEPTH. A previous revision gated on
 *             "depth == 0" here, which made the whole mixin a permanent no-op.
 *   int #2  = start position X (center of the expansion AABB)
 *   int #4  = start position Y
 *   int #7  = start position Z
 *   BoundingBox #8 = the ROOT PIECE's bounding box only (the town center template,
 *             carved out of the expansion VoxelShape). NOT the assembled village box.
 *             Use {@code collector.getBoundingBox()} at RETURN for the real village extent.
 *
 * FRAGILITY: The {@code $2} suffix is a compiler-generated ordinal (0-indexed count of
 * lambdas inside addPieces). If Mojang adds or removes lambdas before this one, the
 * number shifts and injection fails with InvalidInjectionException at world-gen time.
 * When that happens: run {@code javap -p JigsawPlacement.class}, find the lambda whose
 * parameter list starts with {@code PoolElementStructurePiece, int, int, JigsawStructure$MaxDistance}
 * and ends with {@code StructurePiecesBuilder}, update this descriptor, and re-verify the
 * positional int meanings against the bytecode before naming them.
 *
 * NOTE: biome detection uses getElement().toString(), a heuristic on the internal pool
 * element string. Works for vanilla villages; fragile if Mojang changes the toString format.
 *
 * PLACEMENT, and the three bugs a 69-village survey turned up in the previous revision:
 *   1. Sides EAST and WEST were unreachable. The old code fed one anchor to all four
 *      rotations, but a pool element's anchor is a rotation-dependent CORNER of its box, so
 *      the two 90-degree rotations always extended their box back through the village and
 *      were always thrown out by the overlap test. Result: 0/69 castles on an X-axis side,
 *      32 south and 37 north. Placement is now computed as a box delta off a probe box taken
 *      at the origin, which is rotation-agnostic.
 *   2. Nothing faced the village. The rotation table had the two Z-axis cases inverted, so a
 *      castle north of a village pointed its gatehouse further north. Result: 0/69 faced the
 *      village. Rotation is now derived from where the village actually is.
 *   3. Castles sat off the centre line by up to half their own width (median 22 blocks),
 *      because the anchor, not the box, was centred. Fixed by the same box-delta placement.
 *
 * The castle also carries a {@link com.villagecastles.worldgen.CastleGroundsPiece} appended
 * right after it, which underfills the footprint and seeds the garrison; see that class.
 */
@Mixin(JigsawPlacement.class)
public class VillageCastleAttachmentMixin {

    private static final String[] BIOMES = {"plains", "desert", "savanna", "taiga", "snowy"};

    /**
     * Empty blocks left between the village bounding box and the castle wall. Deliberately
     * small: the castle is meant to read as part of the town, and every extra block of offset
     * is a block further out of villager POI range (AcquirePoi scans 48 blocks from the
     * villager's own position).
     */
    private static final int CLEARANCE = 2;

    /**
     * The direction the entrance faces in unrotated template space. Every castle generator
     * builds its main gatehouse on the south wall
     * ({@code gateGenerator.generate(world, southGatePos, Direction.SOUTH)} in
     * CastleGenerator, for MEDIUM and LARGE, and a south fence-gate entrance for SMALL), and
     * StructureExporter cuts the doorway by scanning inward from the template's max Z. A
     * voxel sweep of all 15 exported NBTs agrees: the +Z wall plane has at least as many
     * openings as any other on every template, and strictly more on 10 of 15.
     */
    private static final Direction TEMPLATE_ENTRANCE = Direction.SOUTH;

    /**
     * Site score at or below which a side is taken immediately. The score is the height
     * spread across the footprint plus {@link #WATER_PENALTY} per sample standing in water
     * deeper than {@link #MAX_WATER_DEPTH}, so it reads in blocks-of-ugliness.
     */
    private static final int GOOD_SITE_SCORE = 8;

    /** Score beyond which no castle is placed at all, rather than one on stilts or a pier. */
    private static final int WORST_ACCEPTABLE_SCORE = 28;

    /** Standing water deeper than this counts against a site. */
    private static final int MAX_WATER_DEPTH = 2;

    /** Score added per footprint sample standing in water deeper than the threshold. */
    private static final int WATER_PENALTY = 4;

    /** Grid resolution of the terrain survey over a candidate footprint. */
    private static final int TERRAIN_SAMPLES = 4;

    @Inject(
        method = "lambda$addPieces$2(Lnet/minecraft/world/level/levelgen/structure/PoolElementStructurePiece;IILnet/minecraft/world/level/levelgen/structure/structures/JigsawStructure$MaxDistance;ILnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/levelgen/structure/pools/DimensionPadding;ILnet/minecraft/world/level/levelgen/structure/BoundingBox;Lnet/minecraft/world/level/levelgen/structure/Structure$GenerationContext;ZLnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;Lnet/minecraft/world/level/levelgen/WorldgenRandom;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/structure/pools/alias/PoolAliasLookup;Lnet/minecraft/world/level/levelgen/structure/templatesystem/LiquidSettings;Lnet/minecraft/world/level/levelgen/structure/pieces/StructurePiecesBuilder;)V",
        at = @At("RETURN")
    )
    private static void villagecastles$attachCastle(
        PoolElementStructurePiece firstPiece,
        int maxDepth,        // max jigsaw depth BUDGET (villages: 6), NOT current recursion depth
        int startX,          // structure start X
        JigsawStructure.MaxDistance maxDist,
        int startY,          // structure start Y
        LevelHeightAccessor heightLimitView,
        DimensionPadding dimensionPadding,
        int startZ,          // structure start Z
        BoundingBox rootPieceBox, // town-center piece bbox only, NOT the assembled village
        Structure.GenerationContext context,
        boolean useExpansionHack,
        ChunkGenerator chunkGenerator,
        StructureTemplateManager structureTemplateManager,
        WorldgenRandom chunkRandom,
        Registry<StructureTemplatePool> poolRegistry,
        PoolAliasLookup aliasLookup,
        LiquidSettings liquidSettings,
        StructurePiecesBuilder collector,
        CallbackInfo ci
    ) {
        try {
        // The lambda has an early-return path (maxDepth <= 0) that never adds pieces to
        // the collector; RETURN injection fires there too. An empty collector has no
        // bounding box, so bail before touching it.
        if (maxDepth <= 0 || collector.isEmpty()) return;

        String biome = detectVillageBiome(firstPiece);
        if (biome == null) {
            VillageCastles.LOGGER.debug("[VillageCastles] biome detection returned null for: {}",
                firstPiece.getElement().toString());
            return;
        }

        RandomSource random = chunkRandom;

        // 85% of villages get a castle (15% skip)
        if (random.nextInt(100) >= 85) return;

        String size = pickCastleSize(random);
        String structureId = "villagecastles:" + biome + "/castle_" + size;
        if (!StructureHelper.structureNbtExists(biome + "/castle_" + size)) {
            VillageCastles.LOGGER.warn("NBT missing: {}", structureId);
            return;
        }

        Registry<StructureProcessorList> processorRegistry = context.registryAccess()
            .lookupOrThrow(Registries.PROCESSOR_LIST);
        Optional<Holder.Reference<StructureProcessorList>> processorOpt =
            processorRegistry.get(Identifier.fromNamespaceAndPath("villagecastles", "castle_aging"));

        StructurePoolElement element = processorOpt.isPresent()
            ? StructurePoolElement.single(structureId, processorOpt.get()).apply(StructureTemplatePool.Projection.RIGID)
            : StructurePoolElement.single(structureId).apply(StructureTemplatePool.Projection.RIGID);

        // The ASSEMBLED village bounding box: every street/house/farm piece the jigsaw
        // expansion added to the collector. At @At("RETURN") this is complete. Using the
        // root-piece box here instead would anchor the castle 5 blocks from the town
        // center, plowing through houses; using the assembled box puts it at the true
        // village edge.
        BoundingBox villageBox = collector.getBoundingBox();

        // Village center from the assembled bounding box
        int centerX = (villageBox.minX() + villageBox.maxX()) / 2;
        int centerZ = (villageBox.minZ() + villageBox.maxZ()) / 2;

        // The side of the village the castle is placed on. Shuffled so no direction is favoured.
        Direction[] sides = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        for (int i = sides.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Direction tmp = sides[i];
            sides[i] = sides[j];
            sides[j] = tmp;
        }

        Placement fallback = null;

        for (Direction side : sides) {
            Placement placement = planPlacement(
                element, structureTemplateManager, chunkGenerator, heightLimitView, context,
                villageBox, centerX, centerZ, side);
            if (placement == null) continue;

            VillageCastles.LOGGER.debug("Castle site candidate {} score={} (terrain spread {}, wet samples {})",
                side, placement.score(), placement.terrainDelta(), placement.wetSamples());
            if (placement.score() <= GOOD_SITE_SCORE) {
                place(collector, structureTemplateManager, element, liquidSettings,
                    placement, biome, size, villageBox, processorOpt.isPresent());
                return;
            }
            // Too lumpy or too wet for a clean sit-down, but remember the best reject: a castle
            // on a mild slope with a foundation skirt beats no castle at all. Scoring rather
            // than hard-rejecting matters: an early revision refused any side with a single
            // deep-water sample and lost 16 of 69 surveyed castles outright.
            if (fallback == null || placement.score() < fallback.score()) {
                fallback = placement;
            }
        }

        if (fallback != null && fallback.score() <= WORST_ACCEPTABLE_SCORE) {
            place(collector, structureTemplateManager, element, liquidSettings,
                fallback, biome, size, villageBox, processorOpt.isPresent());
            return;
        }

        // Rare but deliberate: 6 of 69 surveyed villages sit in terrain where no side scores
        // under WORST_ACCEPTABLE_SCORE, and those get no castle rather than one on stilts.
        VillageCastles.LOGGER.info("No buildable castle site around {} village at {} (best site score {})",
            biome, villageBox, fallback == null ? "none" : fallback.score());
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Castle attachment failed: {}", e.getMessage(), e);
        }
    }

    /**
     * A candidate castle site: where it goes, how it is turned, and how bad the ground is.
     * {@code score} is the footprint height spread plus {@link #WATER_PENALTY} per sample
     * standing in deep water; lower is better.
     */
    private record Placement(Direction side, Rotation rotation, BlockPos anchor, BoundingBox box,
                             int terrainDelta, int wetSamples) {
        int score() {
            return terrainDelta + wetSamples * WATER_PENALTY;
        }
    }

    /**
     * Work out where a castle would sit if it were attached to {@code side} of the village.
     * Returns null only if the site is unusable outright (it would still clip the village).
     *
     * <p>Placement is done by BOX DELTA, not by anchor. A pool element's anchor is a
     * rotation-dependent CORNER of its bounding box: for {@code Rotation.NONE} the box runs
     * anchor..anchor+size, for {@code CLOCKWISE_180} it runs anchor-size..anchor, and the 90s
     * swap the axes. The previous revision fed the same anchor to all four rotations, which
     * meant EAST and WEST always extended their box back through the village and were always
     * rejected by the overlap test (measured: 69/69 surveyed castles landed north or south),
     * and the surviving north/south placements sat a full castle width off the village's
     * centre line. Probing the box at the origin and shifting by the delta removes the whole
     * class of bug.
     */
    private static Placement planPlacement(
            StructurePoolElement element,
            StructureTemplateManager structureTemplateManager,
            ChunkGenerator chunkGenerator,
            LevelHeightAccessor heightLimitView,
            Structure.GenerationContext context,
            BoundingBox villageBox,
            int centerX, int centerZ,
            Direction side) {

        // Turn the castle so its gatehouse looks back at the village it belongs to.
        Rotation rotation = rotationSoEntranceFaces(side.getOpposite());

        BoundingBox probe = element.getBoundingBox(structureTemplateManager, BlockPos.ZERO, rotation);
        int width = probe.getXSpan();
        int depth = probe.getZSpan();

        int minX;
        int minZ;
        switch (side) {
            case EAST -> {
                minX = villageBox.maxX() + 1 + CLEARANCE;
                minZ = centerZ - depth / 2;
            }
            case WEST -> {
                minX = villageBox.minX() - CLEARANCE - width;
                minZ = centerZ - depth / 2;
            }
            case SOUTH -> {
                minX = centerX - width / 2;
                minZ = villageBox.maxZ() + 1 + CLEARANCE;
            }
            default -> { // NORTH
                minX = centerX - width / 2;
                minZ = villageBox.minZ() - CLEARANCE - depth;
            }
        }

        // Terrain survey over the whole footprint, not just the anchor corner. A single
        // corner sample is what lets a castle hang off a cliff: the corner is on grass and
        // the far side is over open air.
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        int wetSamples = 0;
        for (int i = 0; i < TERRAIN_SAMPLES; i++) {
            for (int j = 0; j < TERRAIN_SAMPLES; j++) {
                int sx = minX + (width - 1) * i / (TERRAIN_SAMPLES - 1);
                int sz = minZ + (depth - 1) * j / (TERRAIN_SAMPLES - 1);
                int surface = chunkGenerator.getFirstOccupiedHeight(
                    sx, sz, Heightmap.Types.WORLD_SURFACE_WG, heightLimitView, context.randomState());
                int seabed = chunkGenerator.getFirstOccupiedHeight(
                    sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, heightLimitView, context.randomState());
                // Standing water deeper than a puddle: the underfill pass would have to build a
                // pier here. Counted rather than fatal, so a lakeside village still gets a
                // castle on its driest side.
                if (surface - seabed > MAX_WATER_DEPTH) wetSamples++;
                lowest = Math.min(lowest, surface);
                highest = Math.max(highest, surface);
            }
        }

        // Sit on the HIGHEST ground under the footprint so no part of the castle is buried;
        // CastleGroundsPiece fills the gap down to the ground on the low side.
        BlockPos anchor = new BlockPos(minX - probe.minX(), highest - probe.minY(), minZ - probe.minZ());
        BoundingBox box = element.getBoundingBox(structureTemplateManager, anchor, rotation);

        // X/Z-only overlap guard. With a positive CLEARANCE this cannot trip, but a template
        // whose box does not match its declared size would otherwise plough through houses.
        // 3D intersects() is not enough: it misses an overlap whenever the castle's Y range
        // happens not to meet the village's.
        if (box.minX() <= villageBox.maxX() && box.maxX() >= villageBox.minX() &&
            box.minZ() <= villageBox.maxZ() && box.maxZ() >= villageBox.minZ()) {
            return null;
        }

        return new Placement(side, rotation, anchor, box, highest - lowest, wetSamples);
    }

    private static void place(
            StructurePiecesBuilder collector,
            StructureTemplateManager structureTemplateManager,
            StructurePoolElement element,
            LiquidSettings liquidSettings,
            Placement placement,
            String biome, String size,
            BoundingBox villageBox,
            boolean aging) {

        PoolElementStructurePiece castlePiece = new PoolElementStructurePiece(
            structureTemplateManager,
            element,
            placement.anchor(),
            1,
            placement.rotation(),
            placement.box(),
            liquidSettings
        );
        collector.addPiece(castlePiece);

        // Added AFTER the castle so its postProcess runs after the castle's blocks are down in
        // each chunk: StructureStart.placeInChunk walks PiecesContainer.pieces() in list order.
        collector.addPiece(new CastleGroundsPiece(placement.box(), biome));

        VillageCastles.LOGGER.info("Attached {} {} castle at {} facing {} (village box {}, site score {}, aging: {})",
            size, biome, placement.anchor().toShortString(), placement.side().getOpposite(),
            villageBox, placement.score(), aging ? "yes" : "no");
    }

    /**
     * The rotation that makes the template's entrance point at {@code target}.
     *
     * <p>{@code Rotation.CLOCKWISE_90.rotate(d)} is {@code d.getClockWise()},
     * {@code CLOCKWISE_180} is {@code d.getOpposite()} and {@code COUNTERCLOCKWISE_90} is
     * {@code d.getCounterClockWise()} (26.3-snapshot-8 bytecode), so this is just a search
     * over the four rotations for the one that maps {@link #TEMPLATE_ENTRANCE} onto the
     * direction we want.
     */
    private static Rotation rotationSoEntranceFaces(Direction target) {
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(TEMPLATE_ENTRANCE) == target) return rotation;
        }
        return Rotation.NONE;
    }

    private static String detectVillageBiome(PoolElementStructurePiece firstPiece) {
        String elementStr = firstPiece.getElement().toString();
        for (String biome : BIOMES) {
            if (elementStr.contains("village/" + biome + "/")) {
                return biome;
            }
        }
        return null;
    }

    /**
     * Pick castle size. Distribution within the 85% that get a castle:
     * ~35% small, ~35% medium, ~30% large.
     * Overall: 15% nothing, 30% small, 30% medium, 25% large.
     */
    private static String pickCastleSize(RandomSource random) {
        int roll = random.nextInt(100);
        if (roll < 35) return "small";
        if (roll < 70) return "medium";
        return "large";
    }
}
