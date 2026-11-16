package com.villagecastles.generator.ancient;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.function.Predicate;

/**
 * {@link HeightSampler#worldgen} for noise terrain, reading both heights off one noise column.
 *
 * <p>Each heightmap query on a noise generator works out the whole column's density before it
 * looks for the surface, so asking for the surface and then the floor at one spot paid for that
 * twice. Here one column answers both, and a spot asked about again is answered from memory.
 * Lives as long as the one site survey that made it.
 */
final class NoiseColumnSampler implements HeightSampler {

    private static final Predicate<BlockState> COVER = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
    private static final Predicate<BlockState> GROUND = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
    private static final long UNKNOWN = Long.MIN_VALUE;

    private final ChunkGenerator generator;
    private final LevelHeightAccessor heights;
    private final RandomState randomState;
    private final Long2LongOpenHashMap known = new Long2LongOpenHashMap();

    NoiseColumnSampler(ChunkGenerator generator, LevelHeightAccessor heights, RandomState randomState) {
        this.generator = generator;
        this.heights = heights;
        this.randomState = randomState;
        known.defaultReturnValue(UNKNOWN);
    }

    @Override
    public int surface(int x, int z) {
        return (int) (column(x, z) >> 32);
    }

    @Override
    public int floor(int x, int z) {
        return (int) column(x, z);
    }

    /** Surface in the high half, floor in the low, both as {@code getFirstOccupiedHeight} gives them. */
    private long column(int x, int z) {
        long key = ChunkPos.pack(x, z);
        long both = known.get(key);
        if (both != UNKNOWN) return both;

        NoiseColumn column = generator.getBaseColumn(x, z, heights, randomState);
        int surface = heights.getMinY();
        int floor = heights.getMinY();
        boolean covered = false;
        for (int y = heights.getMaxY(); y >= heights.getMinY(); y--) {
            BlockState state = column.getBlock(y);
            if (!covered && COVER.test(state)) {
                surface = y + 1;
                covered = true;
            }
            if (GROUND.test(state)) {
                floor = y + 1;
                break;
            }
        }

        both = ((long) (surface - 1) << 32) | ((floor - 1) & 0xFFFFFFFFL);
        known.put(key, both);
        return both;
    }
}
