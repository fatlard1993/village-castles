package com.villagecastles.generator.ancient;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The material vocabulary a castle generator draws through. Two voices implement it: the
 * ancients' deepslate language ({@link AncientPalette}) and the villagers' biome-timber imitation
 * of it - same grammar, different stone, which is what makes the two lineages read as kin.
 */
public interface CastlePalette {

    /** Wall masonry at a position: the body material, position-varied in patches. */
    BlockState masonryAt(int x, int y, int z);

    /** Dressed trim: string courses, corridor ceilings, bridge decks. */
    BlockState trimAt(int x, int y, int z);

    /** Interior paving. */
    BlockState floorAt(int x, int y, int z);

    BlockState plinth();

    BlockState monolith();

    BlockState dressed();

    BlockState roof();

    BlockState roofStairs();

    BlockState roofSlab();

    BlockState parapet();

    BlockState lantern();

    BlockState stairs();

    BlockState slab();

    BlockState rubble(RandomSource random);

    /** Roof-beam timber: what a collapsed roof leaves behind, and what a built one frames with. */
    BlockState beam();

    Condition condition();

    long seed();

    default Geo.Material masonry() {
        return this::masonryAt;
    }

    default Geo.Material trim() {
        return this::trimAt;
    }

    default Geo.Material paving() {
        return this::floorAt;
    }

    /** Masonry with a trim string course at every storey line above the base. */
    default Geo.Material banded(int baseY, int storeyHeight) {
        return (x, y, z) -> y > baseY && (y - baseY) % storeyHeight == 0
            ? trimAt(x, y, z) : masonryAt(x, y, z);
    }
}
