package com.villagecastles.generator.ancient;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * The terrain under one castle, sampled once at site selection and carried on the structure piece.
 *
 * <p>Generators may not touch the world, and a piece's {@code postProcess} has no way to re-ask
 * the chunk generator for heights, so every terrain question a generator asks is answered from
 * this grid. Samples sit on a coarse step; {@link #surfaceY} interpolates between them, which can
 * drift a block or two from real terrain. The generator contract absorbs that: plinths extend
 * well below {@code surfaceY}, interiors clear well above.
 *
 * <p>{@code ground} holds OCEAN_FLOOR heightmap values (water excluded), {@code cover} holds
 * WORLD_SURFACE values (water included); both are heightmap convention, the first free Y. Their
 * difference is water depth.
 */
public final class HeightField {

    private final int originX;
    private final int originZ;
    private final int step;
    private final int width;
    private final int depth;
    private final int[] ground;
    private final int[] cover;

    public HeightField(int originX, int originZ, int step, int width, int depth,
                       int[] ground, int[] cover) {
        this.originX = originX;
        this.originZ = originZ;
        this.step = step;
        this.width = width;
        this.depth = depth;
        this.ground = ground;
        this.cover = cover;
    }

    private int sample(int[] grid, int gx, int gz) {
        gx = Math.clamp(gx, 0, width - 1);
        gz = Math.clamp(gz, 0, depth - 1);
        return grid[gx * depth + gz];
    }

    /** Bilinear over one grid, returning heightmap convention (the first free Y). */
    private int interpolate(int[] grid, int x, int z) {
        // Positions are interpolated in fixed point (step denominator) to stay float-free and
        // therefore bit-identical whichever chunk asks.
        int rx = x - originX;
        int rz = z - originZ;
        int gx = Math.floorDiv(rx, step);
        int gz = Math.floorDiv(rz, step);
        int fx = Math.floorMod(rx, step);
        int fz = Math.floorMod(rz, step);

        int h00 = sample(grid, gx, gz);
        int h10 = sample(grid, gx + 1, gz);
        int h01 = sample(grid, gx, gz + 1);
        int h11 = sample(grid, gx + 1, gz + 1);

        int north = h00 * (step - fx) + h10 * fx;
        int south = h01 * (step - fx) + h11 * fx;
        return (north * (step - fz) + south * fz) / (step * step);
    }

    /** Y of the ground surface block at a column: bilinear over the grid, water ignored. */
    public int surfaceY(int x, int z) {
        return interpolate(ground, x, z) - 1;
    }

    /**
     * Y of the topmost block at a column with water counted: the water's surface on a wet column,
     * and exactly {@link #surfaceY} on a dry one.
     *
     * <p>Shares {@link #interpolate} with {@code surfaceY} rather than repeating the arithmetic,
     * which is what makes {@code coverY >= surfaceY} hold everywhere and not just at the samples:
     * every cover sample is at or above its ground sample, and bilinear with identical weights
     * preserves that between them. Callers clearing space above the terrain rely on it.
     */
    public int coverY(int x, int z) {
        return interpolate(cover, x, z) - 1;
    }

    /** Blocks of standing water over a column, from the nearest sample. 0 on dry land. */
    public int waterDepth(int x, int z) {
        int gx = Math.floorDiv(x - originX + step / 2, step);
        int gz = Math.floorDiv(z - originZ + step / 2, step);
        return Math.max(0, sample(cover, gx, gz) - sample(ground, gx, gz));
    }

    /** Lowest ground sample in the whole grid (heightmap convention, first free Y). */
    public int minY() {
        int min = Integer.MAX_VALUE;
        for (int h : ground) min = Math.min(min, h);
        return min;
    }

    /** Highest ground sample in the whole grid (heightmap convention, first free Y). */
    public int maxY() {
        int max = Integer.MIN_VALUE;
        for (int h : ground) max = Math.max(max, h);
        return max;
    }

    /** The highest dry ground column within {@code radius} of a center, at sample resolution. */
    public BlockPos highestWithin(int centerX, int centerZ, int radius) {
        int bestX = centerX, bestZ = centerZ, bestY = Integer.MIN_VALUE;
        for (int gx = 0; gx < width; gx++) {
            for (int gz = 0; gz < depth; gz++) {
                int x = originX + gx * step;
                int z = originZ + gz * step;
                int dx = x - centerX, dz = z - centerZ;
                if (dx * dx + dz * dz > radius * radius) continue;
                int h = ground[gx * depth + gz];
                if (cover[gx * depth + gz] - h > 0) continue;
                if (h > bestY) {
                    bestY = h;
                    bestX = x;
                    bestZ = z;
                }
            }
        }
        if (bestY == Integer.MIN_VALUE) return new BlockPos(centerX, surfaceY(centerX, centerZ), centerZ);
        return new BlockPos(bestX, bestY - 1, bestZ);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("OriginX", originX);
        tag.putInt("OriginZ", originZ);
        tag.putInt("Step", step);
        tag.putInt("Width", width);
        tag.putInt("Depth", depth);
        tag.putIntArray("Ground", ground);
        tag.putIntArray("Cover", cover);
        return tag;
    }

    public static HeightField fromTag(CompoundTag tag) {
        int width = tag.getIntOr("Width", 1);
        int depth = tag.getIntOr("Depth", 1);
        int[] ground = tag.getIntArray("Ground").orElse(new int[width * depth]);
        int[] cover = tag.getIntArray("Cover").orElse(ground);
        return new HeightField(
            tag.getIntOr("OriginX", 0),
            tag.getIntOr("OriginZ", 0),
            tag.getIntOr("Step", 4),
            width, depth, ground, cover);
    }
}
