package com.villagecastles.mixin;

import com.villagecastles.VillageCastles;
import com.villagecastles.util.StructureHelper;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.PoolStructurePiece;
import net.minecraft.structure.StructureLiquidSettings;
import net.minecraft.structure.StructurePiecesCollector;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.pool.alias.StructurePoolAliasLookup;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.gen.structure.DimensionPadding;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * After a village finishes jigsaw assembly, attach a castle to the village edge.
 * Targets the collector-based generate method (method_39824) which is the code path
 * villages use in 1.21.11.
 */
@Mixin(StructurePoolBasedGenerator.class)
public class VillageCastleAttachmentMixin {

    private static final String[] BIOMES = {"plains", "desert", "savanna", "taiga", "snowy"};
    private static final int CLEARANCE = 5;

    @Inject(
        method = "method_39824(Lnet/minecraft/structure/PoolStructurePiece;IILnet/minecraft/world/gen/structure/JigsawStructure$MaxDistanceFromCenter;ILnet/minecraft/world/HeightLimitView;Lnet/minecraft/world/gen/structure/DimensionPadding;ILnet/minecraft/util/math/BlockBox;Lnet/minecraft/world/gen/structure/Structure$Context;ZLnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/structure/StructureTemplateManager;Lnet/minecraft/util/math/random/ChunkRandom;Lnet/minecraft/registry/Registry;Lnet/minecraft/structure/pool/alias/StructurePoolAliasLookup;Lnet/minecraft/structure/StructureLiquidSettings;Lnet/minecraft/structure/StructurePiecesCollector;)V",
        at = @At("RETURN")
    )
    private static void villagecastles$attachCastle(
        PoolStructurePiece firstPiece,
        int p1,
        int p2,
        JigsawStructure.MaxDistanceFromCenter maxDist,
        int p4,
        HeightLimitView heightLimitView,
        DimensionPadding dimensionPadding,
        int p7,
        BlockBox structureBox,
        Structure.Context context,
        boolean p10,
        ChunkGenerator chunkGenerator,
        StructureTemplateManager structureTemplateManager,
        ChunkRandom chunkRandom,
        Registry<StructurePool> poolRegistry,
        StructurePoolAliasLookup aliasLookup,
        StructureLiquidSettings liquidSettings,
        StructurePiecesCollector collector,
        CallbackInfo ci
    ) {
        try {
        String biome = detectVillageBiome(firstPiece);
        if (biome == null) return;

        Random random = chunkRandom;

        // 85% of villages get a castle (15% skip)
        if (random.nextInt(100) >= 85) return;

        String size = pickCastleSize(random);
        String structureId = "villagecastles:" + biome + "/castle_" + size;
        if (!StructureHelper.structureNbtExists(biome + "/castle_" + size)) {
            VillageCastles.LOGGER.warn("NBT missing: {}", structureId);
            return;
        }

        StructurePoolElement element = StructurePoolElement.ofSingle(structureId)
            .apply(StructurePool.Projection.RIGID);

        // Village center from structure bounding box
        int centerX = (structureBox.getMinX() + structureBox.getMaxX()) / 2;
        int centerZ = (structureBox.getMinZ() + structureBox.getMaxZ()) / 2;

        // Try placing the castle extending outward from each edge of the structure box
        int[][] offsets = {
            {structureBox.getMaxX() + CLEARANCE, centerZ, 1, 0},   // East
            {structureBox.getMinX() - CLEARANCE, centerZ, -1, 0},  // West
            {centerX, structureBox.getMaxZ() + CLEARANCE, 0, 1},   // South
            {centerX, structureBox.getMinZ() - CLEARANCE, 0, -1},  // North
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

            BlockRotation rotation;
            if (dx > 0) rotation = BlockRotation.CLOCKWISE_90;
            else if (dx < 0) rotation = BlockRotation.COUNTERCLOCKWISE_90;
            else if (dz > 0) rotation = BlockRotation.NONE;
            else rotation = BlockRotation.CLOCKWISE_180;

            int castleY = structureBox.getMinY();
            BlockPos castlePos = new BlockPos(castleX, castleY, castleZ);

            BlockBox castleBox = element.getBoundingBox(structureTemplateManager, castlePos, rotation);

            // Check overlap with the raw village bounding box (no expansion)
            if (castleBox.intersects(structureBox)) {
                continue;
            }

            PoolStructurePiece castlePiece = new PoolStructurePiece(
                structureTemplateManager,
                element,
                castlePos,
                1,
                rotation,
                castleBox,
                liquidSettings
            );

            collector.addPiece(castlePiece);

            VillageCastles.LOGGER.info("Attached {} {} castle to {} village at {}",
                size, biome, biome, castlePos.toShortString());
            return;
        }

        VillageCastles.LOGGER.debug("Could not find clear position for castle in {} village", biome);
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Castle attachment failed: {}", e.getMessage(), e);
        }
    }

    private static String detectVillageBiome(PoolStructurePiece firstPiece) {
        String elementStr = firstPiece.getPoolElement().toString();
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
    private static String pickCastleSize(Random random) {
        int roll = random.nextInt(100);
        if (roll < 35) return "small";
        if (roll < 70) return "medium";
        return "large";
    }
}
