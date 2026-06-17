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
 * Targets {@code lambda$addPieces$2} in JigsawPlacement — the per-piece recursive lambda
 * extracted from the private {@code addPieces()} call in MC 26.1. This lambda receives
 * StructurePiecesBuilder and Structure.GenerationContext, equivalent to {@code method_39824}
 * from 1.21.11.
 *
 * FRAGILITY: The {@code $2} suffix is a compiler-generated ordinal (0-indexed count of
 * lambdas inside addPieces). If Mojang adds or removes lambdas before this one, the
 * number shifts and injection fails with InvalidInjectionException at world-gen time.
 * When that happens: run {@code javap -p JigsawPlacement.class}, find the lambda whose
 * parameter list starts with {@code PoolElementStructurePiece, int, int, JigsawStructure$MaxDistance}
 * and ends with {@code StructurePiecesBuilder}, and update this descriptor.
 *
 * NOTE: biome detection uses getElement().toString() — a heuristic on the internal pool
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
        int depth,           // current jigsaw recursion depth (0 = root)
        int maxDepth,        // configured max depth from JigsawStructure
        JigsawStructure.MaxDistance maxDist,
        int boundaryY,       // Y height constraint for piece placement
        LevelHeightAccessor heightLimitView,
        DimensionPadding dimensionPadding,
        int minY,            // minimum allowed Y for pieces
        BoundingBox structureBox,
        Structure.GenerationContext context,
        boolean keepJigsaws, // preserve jigsaw blocks in output (debug)
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
        // Only process the root piece (depth == 0 = town center template).
        // The lambda fires once per jigsaw piece, not once per village.
        if (depth > 0) return;

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

        // Village center from structure bounding box
        int centerX = (structureBox.minX() + structureBox.maxX()) / 2;
        int centerZ = (structureBox.minZ() + structureBox.maxZ()) / 2;

        // Try placing the castle extending outward from each edge of the structure box
        int[][] offsets = {
            {structureBox.maxX() + CLEARANCE, centerZ, 1, 0},   // East
            {structureBox.minX() - CLEARANCE, centerZ, -1, 0},  // West
            {centerX, structureBox.maxZ() + CLEARANCE, 0, 1},   // South
            {centerX, structureBox.minZ() - CLEARANCE, 0, -1},  // North
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
            // structureBox.minY() is the village floor — fine for flat terrain, wrong when the
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
            // the village from its anchor — the overlap check must catch this. 3D intersects()
            // misses it when castle Y differs from village Y (no Y-range overlap → false clear).
            if (castleBox.minX() <= structureBox.maxX() && castleBox.maxX() >= structureBox.minX() &&
                castleBox.minZ() <= structureBox.maxZ() && castleBox.maxZ() >= structureBox.minZ()) {
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

            VillageCastles.LOGGER.info("Attached {} {} castle to {} village at {} (aging: {})",
                size, biome, biome, castlePos.toShortString(), processorOpt.isPresent() ? "yes" : "no");
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
