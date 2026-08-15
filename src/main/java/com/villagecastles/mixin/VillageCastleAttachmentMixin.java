package com.villagecastles.mixin;

import com.villagecastles.VillageCastles;
import com.villagecastles.util.StructureHelper;
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
 */
@Mixin(JigsawPlacement.class)
public class VillageCastleAttachmentMixin {

    private static final String[] BIOMES = {"plains", "desert", "savanna", "taiga", "snowy"};
    private static final int CLEARANCE = 5;

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

        // Try placing the castle extending outward from each edge of the village box
        int[][] offsets = {
            {villageBox.maxX() + CLEARANCE, centerZ, 1, 0},   // East
            {villageBox.minX() - CLEARANCE, centerZ, -1, 0},  // West
            {centerX, villageBox.maxZ() + CLEARANCE, 0, 1},   // South
            {centerX, villageBox.minZ() - CLEARANCE, 0, -1},  // North
        };

        // Shuffle to avoid bias
        for (int i = offsets.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int[] tmp = offsets[i];
            offsets[i] = offsets[j];
            offsets[j] = tmp;
        }

        for (int[] offset : offsets) {
            int castleX = offset[0];
            int castleZ = offset[1];
            int dx = offset[2];
            int dz = offset[3];

            Rotation rotation;
            if (dx > 0) rotation = Rotation.CLOCKWISE_90;
            else if (dx < 0) rotation = Rotation.COUNTERCLOCKWISE_90;
            else if (dz > 0) rotation = Rotation.NONE;
            else rotation = Rotation.CLOCKWISE_180;

            // Sample actual surface Y at the castle's anchor position.
            // villageBox.minY() is the village floor: fine for flat terrain, wrong when the
            // castle sits on a cliff or hillside 5-70 blocks from the village center.
            int castleY = chunkGenerator.getFirstOccupiedHeight(
                castleX, castleZ,
                Heightmap.Types.WORLD_SURFACE_WG,
                heightLimitView,
                context.randomState()
            );
            BlockPos castlePos = new BlockPos(castleX, castleY, castleZ);

            BoundingBox castleBox = element.getBoundingBox(structureTemplateManager, castlePos, rotation);

            // X/Z-only overlap check. A large castle bounding box extends 35+ blocks back toward
            // the village from its anchor: the overlap check must catch this. 3D intersects()
            // misses it when castle Y differs from village Y (no Y-range overlap → false clear).
            if (castleBox.minX() <= villageBox.maxX() && castleBox.maxX() >= villageBox.minX() &&
                castleBox.minZ() <= villageBox.maxZ() && castleBox.maxZ() >= villageBox.minZ()) {
                continue;
            }

            PoolElementStructurePiece castlePiece = new PoolElementStructurePiece(
                structureTemplateManager,
                element,
                castlePos,
                1,
                rotation,
                castleBox,
                liquidSettings
            );

            collector.addPiece(castlePiece);

            VillageCastles.LOGGER.info("Attached {} {} castle at {} (village box {}, aging: {})",
                size, biome, castlePos.toShortString(), villageBox, processorOpt.isPresent() ? "yes" : "no");
            return;
        }

        VillageCastles.LOGGER.debug("Could not find clear position for castle in {} village", biome);
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Castle attachment failed: {}", e.getMessage(), e);
        }
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
