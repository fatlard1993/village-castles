package com.villagecastles.mixin;

import com.villagecastles.VillageCastles;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolBasedGenerator;
import net.minecraft.world.gen.structure.JigsawStructure;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Optional;

/**
 * Increases the jigsaw generation depth for vanilla villages when castle pieces
 * have been injected into their pools.
 *
 * Castle center pieces are significantly larger than vanilla town_centers pieces
 * (~22x35x22 vs ~9x5x9). Without additional jigsaw depth, the village cannot
 * generate enough streets and houses around the larger center. This mixin
 * increases the depth from 6 to 10 for village generation, giving the jigsaw
 * system room to fill in around castles.
 *
 * When the mod is removed, vanilla depth (6) applies automatically — no residue.
 * When Village Builder is present, its runtime building system handles expansion
 * independently (it doesn't use jigsaw at all).
 */
@Mixin(JigsawStructure.class)
public class VillageSizeMixin {

    /** Extra jigsaw depth added to village generation. 6 + 4 = 10. */
    private static final int VILLAGE_DEPTH_INCREASE = 4;

    @Shadow
    private RegistryEntry<StructurePool> startPool;

    /**
     * Intercept the maxDepth parameter passed to StructurePoolBasedGenerator.generate()
     * and increase it when generating a vanilla village.
     */
    @ModifyArg(
        method = "getStructurePosition",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/structure/pool/StructurePoolBasedGenerator;generate(Lnet/minecraft/world/gen/structure/Structure$Context;Lnet/minecraft/registry/entry/RegistryEntry;Ljava/util/Optional;ILnet/minecraft/util/math/BlockPos;ZLjava/util/Optional;Lnet/minecraft/world/gen/structure/JigsawStructure$MaxDistanceFromCenter;Lnet/minecraft/structure/pool/alias/StructurePoolAliasLookup;Lnet/minecraft/world/gen/structure/DimensionPadding;Lnet/minecraft/structure/StructureLiquidSettings;)Ljava/util/Optional;"
        ),
        index = 3
    )
    private int villagecastles$expandVillageDepth(int maxDepth) {
        if (isVillagePool()) {
            int expanded = maxDepth + VILLAGE_DEPTH_INCREASE;
            VillageCastles.LOGGER.info("Village size expanded: {} -> {} for pool {}",
                maxDepth, expanded, startPool.getKey().map(k -> k.getValue().toString()).orElse("?"));
            return expanded;
        }
        return maxDepth;
    }

    /**
     * Check if this jigsaw structure's start pool is a vanilla village town_centers pool.
     * Returns false for any non-village structure (ruins, bastions, etc.) so their
     * depth is left unchanged.
     */
    private boolean isVillagePool() {
        if (startPool == null) return false;
        Optional<RegistryKey<StructurePool>> key = startPool.getKey();
        if (key.isEmpty()) return false;

        String id = key.get().getValue().toString();
        return id.startsWith("minecraft:village/") && id.endsWith("/town_centers");
    }
}
