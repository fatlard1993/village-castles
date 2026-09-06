package com.villagecastles.generator.villager;

import com.villagecastles.generator.BiomePalette;
import com.villagecastles.generator.ancient.CastlePalette;
import com.villagecastles.generator.ancient.Condition;
import com.villagecastles.generator.ancient.Erode;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The villagers' castle voice: the ancient grammar spoken in the 21-slot villager material
 * vocabulary {@link BiomePalette} already carries. Walls come through the palette's own
 * patch-hash mix (60/30/10 primary/secondary/accent - the same blotch logic the ancients use),
 * trim and dressing through the biome's accent stone, roofs and furniture through its timber.
 * Condition is always {@link Condition#PRISTINE}: these are new-built.
 */
public final class VillagerPalette implements CastlePalette {

    private final BiomePalette biome;
    private final long seed;

    private VillagerPalette(BiomePalette biome, long seed) {
        this.biome = biome;
        this.seed = seed;
    }

    /** Null or unknown biome falls back to PLAINS, so a bad id still builds. */
    public static VillagerPalette of(BiomePalette biome, long seed) {
        return new VillagerPalette(biome == null ? BiomePalette.PLAINS : biome, seed);
    }

    public BiomePalette biome() {
        return biome;
    }

    @Override
    public BlockState masonryAt(int x, int y, int z) {
        return biome.getWallBlockAt(x, y, z);
    }

    @Override
    public BlockState trimAt(int x, int y, int z) {
        return biome.getAccentWallState();
    }

    @Override
    public BlockState floorAt(int x, int y, int z) {
        int roll = Math.floorMod(Erode.hash(seed, x, y, z + 29), 10);
        if (roll < 7) return biome.getFloorState();
        return biome.getPlanksState();
    }

    @Override
    public BlockState plinth() {
        return biome.getFoundationState();
    }

    @Override
    public BlockState monolith() {
        return biome.getAccentWallState();
    }

    @Override
    public BlockState dressed() {
        return biome.getAccentWallState();
    }

    @Override
    public BlockState roof() {
        return biome.getRoofState();
    }

    @Override
    public BlockState roofStairs() {
        return biome.woodStairs.defaultBlockState();
    }

    @Override
    public BlockState roofSlab() {
        return biome.woodSlab.defaultBlockState();
    }

    @Override
    public BlockState parapet() {
        return biome.getWallState();
    }

    @Override
    public BlockState lantern() {
        return biome.getLightState();
    }

    @Override
    public BlockState stairs() {
        return biome.stoneStairs.defaultBlockState();
    }

    @Override
    public BlockState slab() {
        return biome.stoneSlab.defaultBlockState();
    }

    @Override
    public BlockState rubble(RandomSource random) {
        return biome.getSecondaryWallState();
    }

    @Override
    public BlockState beam() {
        return biome.getLogState();
    }

    @Override
    public Condition condition() {
        return Condition.PRISTINE;
    }

    @Override
    public long seed() {
        return seed;
    }

    // ------------------------------------------------ the lived-in vocabulary the interiors need

    public BlockState planks() {
        return biome.getPlanksState();
    }

    public BlockState log() {
        return biome.getLogState();
    }

    public BlockState fence() {
        return biome.getFenceState();
    }

    public BlockState fenceGate() {
        return biome.getFenceGateState();
    }

    public BlockState door() {
        return biome.door.defaultBlockState();
    }

    public BlockState trapdoor() {
        return biome.trapdoor.defaultBlockState();
    }

    public BlockState bed() {
        return biome.getBedState();
    }

    public BlockState carpet() {
        return biome.getCarpetState();
    }

    public BlockState bars() {
        return biome.getBarsState();
    }
}
