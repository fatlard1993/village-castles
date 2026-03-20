package com.villagecastles.mixin;

import net.minecraft.structure.pool.StructurePool;
import net.minecraft.structure.pool.StructurePoolElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.datafixers.util.Pair;

import java.util.List;

/**
 * Accessor mixin for StructurePool's internal element list.
 * Needed to inject castle pieces into vanilla village template pools.
 */
@Mixin(StructurePool.class)
public interface StructurePoolAccessor {

    @Accessor("elements")
    it.unimi.dsi.fastutil.objects.ObjectArrayList<StructurePoolElement> getElements();

    @Accessor("elementWeights")
    List<Pair<StructurePoolElement, Integer>> getElementWeights();

    @Mutable
    @Accessor("elementWeights")
    void setElementWeights(List<Pair<StructurePoolElement, Integer>> elementWeights);
}
