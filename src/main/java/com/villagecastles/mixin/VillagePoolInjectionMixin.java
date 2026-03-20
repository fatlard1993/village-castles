package com.villagecastles.mixin;

import com.mojang.datafixers.util.Pair;
import com.villagecastles.VillageCastles;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import net.minecraft.structure.processor.StructureProcessorList;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.villagecastles.util.StructureHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Injects castle pieces into vanilla village town_centers template pools.
 * Castles become the village center with streets and houses radiating outward
 * via jigsaw connectors embedded in the castle NBT.
 */
@Mixin(MinecraftServer.class)
public class VillagePoolInjectionMixin {

    @Inject(method = "loadWorld", at = @At("HEAD"))
    private void villagecastles$injectIntoPools(CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;

        Registry<StructurePool> poolRegistry = server.getRegistryManager()
            .getOrThrow(RegistryKeys.TEMPLATE_POOL);
        Registry<StructureProcessorList> processorRegistry = server.getRegistryManager()
            .getOrThrow(RegistryKeys.PROCESSOR_LIST);

        Optional<RegistryEntry.Reference<StructureProcessorList>> processorEntry =
            processorRegistry.getEntry(Identifier.of("villagecastles", "castle_aging"));

        if (processorEntry.isEmpty()) {
            VillageCastles.LOGGER.warn("Castle aging processor not found, skipping village pool injection");
            return;
        }

        RegistryEntry<StructureProcessorList> agingProcessor = processorEntry.get();

        int injected = injectCastles(poolRegistry, agingProcessor);

        if (injected > 0) {
            VillageCastles.LOGGER.info("Injected {} castle pieces into village pools (only structures with NBT files)", injected);
        } else {
            VillageCastles.LOGGER.warn("No structures were injected — NBT files may be missing. "
                + "Use /villagecastles status to check.");
        }
    }

    /**
     * Inject castle structures into village houses pools.
     * Villages generate normally — vanilla center, streets, houses, villagers.
     * Castles appear as rare terminal pieces at the end of village streets,
     * with their entrance connecting to the path.
     *
     * Weights relative to vanilla houses pool (~50-100 total):
     * small=15 (~15%), medium=8 (~8%), large=2 (~2%).
     * Most villages get a small castle. Large castles are a real find.
     */
    private int injectCastles(Registry<StructurePool> poolRegistry,
                              RegistryEntry<StructureProcessorList> agingProcessor) {

        String[] biomes = {"plains", "desert", "savanna", "taiga", "snowy"};
        String[][] sizes = {{"small", "15"}, {"medium", "8"}, {"large", "2"}};

        int injected = 0;
        for (String biome : biomes) {
            String poolId = "minecraft:village/" + biome + "/houses";
            StructurePool pool = poolRegistry.get(
                RegistryKey.of(RegistryKeys.TEMPLATE_POOL, Identifier.of(poolId))
            );
            if (pool == null) {
                VillageCastles.LOGGER.warn("Pool {} not found!", poolId);
                continue;
            }

            StructurePoolAccessor accessor = (StructurePoolAccessor) pool;
            int elementsBefore = accessor.getElements().size();

            for (String[] sizeEntry : sizes) {
                String structureId = "villagecastles:" + biome + "/castle_" + sizeEntry[0];
                int weight = Integer.parseInt(sizeEntry[1]);

                if (!structureNbtExists(structureId)) {
                    VillageCastles.LOGGER.warn("NBT missing for {}", structureId);
                    continue;
                }

                StructurePoolElement element = StructurePoolElement.ofProcessedSingle(
                    structureId, agingProcessor
                ).apply(StructurePool.Projection.RIGID);

                addToPool(pool, element, weight);
                injected++;
            }

            int elementsAfter = accessor.getElements().size();
            VillageCastles.LOGGER.info("Pool {}: elements {} -> {}",
                poolId, elementsBefore, elementsAfter);
        }

        return injected;
    }

    /**
     * Add an element to a structure pool, properly expanding by weight.
     * Vanilla StructurePool pre-expands elements by weight into the elements list,
     * so we must add the element weight-many times to match.
     */
    private void addToPool(StructurePool pool, StructurePoolElement element, int weight) {
        StructurePoolAccessor accessor = (StructurePoolAccessor) pool;

        // Update the weighted list (raw weights)
        List<Pair<StructurePoolElement, Integer>> counts =
            new ArrayList<>(accessor.getElementWeights());
        counts.add(Pair.of(element, weight));
        accessor.setElementWeights(counts);

        // Expand into the elements list (weight times, matching vanilla behavior)
        for (int i = 0; i < weight; i++) {
            accessor.getElements().add(element);
        }
    }

    /**
     * Check if an NBT structure file exists in the mod's resources.
     * Converts identifiers like "villagecastles:plains/castle_small" to resource paths.
     */
    private boolean structureNbtExists(String identifier) {
        String[] parts = identifier.split(":");
        if (parts.length != 2) return false;
        return StructureHelper.structureNbtExists(parts[1]);
    }
}
