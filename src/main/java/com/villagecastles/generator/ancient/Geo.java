package com.villagecastles.generator.ancient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry for {@link Plan} writers: nothing here touches a world, rolls a random, or knows
 * what a castle is. The offset math is ported from {@code StructureHelper}, minus the
 * {@code ServerLevel} it writes through.
 *
 * <p>Everything takes a {@link Material} rather than a state so walls can be drawn through a
 * palette's position-hashed picks in one call.
 */
public final class Geo {

    private Geo() {}

    /** A block choice per position. The palette's position-hashed picks implement this. */
    public interface Material {
        BlockState at(int x, int y, int z);
    }

    public static Material solid(BlockState state) {
        return (x, y, z) -> state;
    }

    public static final Material AIR = solid(Blocks.AIR.defaultBlockState());
    public static final Material CAVE_AIR = solid(Blocks.CAVE_AIR.defaultBlockState());

    /** One-block-thick circle outline offsets. */
    public static List<int[]> ring(int radius) {
        return band(radius, 1);
    }

    /** Circle band offsets: everything between radius and radius - thickness (exclusive). */
    public static List<int[]> band(int radius, int thickness) {
        List<int[]> offsets = new ArrayList<>();
        int outerSq = radius * radius + radius;
        int inner = radius - thickness;
        int innerSq = inner * inner + inner;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distSq = dx * dx + dz * dz;
                if (distSq <= outerSq && distSq > innerSq) offsets.add(new int[] {dx, dz});
            }
        }
        return offsets;
    }

    /** Filled circle offsets. */
    public static List<int[]> disc(int radius) {
        List<int[]> offsets = new ArrayList<>();
        int radiusSq = radius * radius + radius;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz <= radiusSq) offsets.add(new int[] {dx, dz});
            }
        }
        return offsets;
    }

    /** One-block-thick square outline offsets. */
    public static List<int[]> squareRing(int radius) {
        List<int[]> offsets = new ArrayList<>();
        for (int d = -radius; d <= radius; d++) {
            offsets.add(new int[] {d, -radius});
            offsets.add(new int[] {d, radius});
            if (d > -radius && d < radius) {
                offsets.add(new int[] {-radius, d});
                offsets.add(new int[] {radius, d});
            }
        }
        return offsets;
    }

    /** Filled box, both corners inclusive, any corner order. */
    public static void box(Plan plan, int x1, int y1, int z1, int x2, int y2, int z2,
                           Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    plan.set(x, y, z, material.at(x, y, z));
                }
            }
        }
    }

    /** The four vertical faces of a box; no floor, no ceiling. */
    public static void shell(Plan plan, int x1, int y1, int z1, int x2, int y2, int z2,
                             Material material) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = minX; x <= maxX; x++) {
                plan.set(x, y, minZ, material.at(x, y, minZ));
                plan.set(x, y, maxZ, material.at(x, y, maxZ));
            }
            for (int z = minZ + 1; z <= maxZ - 1; z++) {
                plan.set(minX, y, z, material.at(minX, y, z));
                plan.set(maxX, y, z, material.at(maxX, y, z));
            }
        }
    }

    /** One horizontal layer of a filled rectangle. */
    public static void floor(Plan plan, int x1, int z1, int x2, int z2, int y, Material material) {
        box(plan, x1, y, z1, x2, y, z2, material);
    }

    /** A vertical run of one column, both ends inclusive. */
    public static void column(Plan plan, int x, int z, int yFrom, int yTo, Material material) {
        for (int y = Math.min(yFrom, yTo); y <= Math.max(yFrom, yTo); y++) {
            plan.set(x, y, z, material.at(x, y, z));
        }
    }

    /** Hollow cylinder shell between two heights, both inclusive. */
    public static void cylinder(Plan plan, int centerX, int centerZ, int yFrom, int yTo,
                                int radius, Material material) {
        for (int[] o : ring(radius)) {
            column(plan, centerX + o[0], centerZ + o[1], yFrom, yTo, material);
        }
    }

    /** Filled disc at one height. */
    public static void deck(Plan plan, int centerX, int centerZ, int y, int radius,
                            Material material) {
        for (int[] o : disc(radius)) {
            plan.set(centerX + o[0], y, centerZ + o[1], material.at(centerX + o[0], y, centerZ + o[1]));
        }
    }

    /**
     * A stepped cone roof over a round tower: full discs shrinking one block of radius per
     * course, capped with a slab. Reads as a slate spire rather than a flat deck.
     */
    public static void cone(Plan plan, int centerX, int centerZ, int baseY, int radius,
                            Material material, BlockState cap) {
        int y = baseY;
        for (int r = radius; r >= 1; r--, y++) {
            deck(plan, centerX, centerZ, y, r, material);
        }
        plan.set(centerX, y, centerZ, cap);
    }

    /**
     * A stepped pyramid roof over a square tower: full squares shrinking one block of half-width
     * per course, capped with a slab.
     */
    public static void pyramid(Plan plan, int centerX, int centerZ, int baseY, int half,
                               Material material, BlockState cap) {
        int y = baseY;
        for (int h = half; h >= 1; h--, y++) {
            box(plan, centerX - h, y, centerZ - h, centerX + h, y, centerZ + h, material);
        }
        plan.set(centerX, y, centerZ, cap);
    }

    /**
     * An arched opening punched through whatever was drawn before it: a clear rectangle whose top
     * course rounds off parabolically, rimmed in the arch material. Call after the wall it
     * pierces; last write wins.
     */
    public static void archway(Plan plan, BlockPos centre, Direction along, int halfWidth,
                               int height, Material arch) {
        Direction across = along.getClockWise();
        for (int d = -halfWidth - 1; d <= halfWidth + 1; d++) {
            int x = centre.getX() + across.getStepX() * d;
            int z = centre.getZ() + across.getStepZ() * d;
            // Opening height falls off toward the jambs; the +1 columns are the jambs themselves.
            int span = Math.abs(d);
            int clear = span > halfWidth ? 0
                : height - (span * span * 2) / Math.max(1, halfWidth * halfWidth);
            for (int y = 0; y < clear; y++) {
                plan.set(x, centre.getY() + y, z, Blocks.AIR.defaultBlockState());
            }
            int rimY = centre.getY() + clear;
            plan.set(x, rimY, z, arch.at(x, rimY, z));
        }
    }

    /**
     * A spiral stair climbing the inside of a round tower: stair blocks walking the wall, a solid
     * newel down the middle, and the stairwell cut through whatever it climbs past.
     *
     * <p><b>The stairwell is the point of the clearing.</b> A keep lays a solid floor across its
     * whole interior every storey, and the spiral used only to replace the one block each step
     * landed on - which leaves the slab directly over your head intact. Climbing then meant
     * mining the ceiling every six blocks, in a building nobody is supposed to have to dig
     * through. Two blocks above each tread is exactly a player's headroom, and cutting it as the
     * stair is placed means the hole is helical: it follows the walk instead of opening the floor
     * out into a hole anybody could fall down.
     */
    public static void spiralStairs(Plan plan, int centerX, int centerZ, int yFrom, int yTo,
                                    int radius, BlockState stair, Material newel) {
        // ring() rasterises in scan order; a stair has to walk the circle, so sort by angle.
        List<int[]> walk = new ArrayList<>(ring(radius));
        walk.sort(java.util.Comparator.comparingDouble(o -> Math.atan2(o[1], o[0])));
        int steps = walk.size();
        Direction[] quarters = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int y = yFrom, i = 0; y <= yTo; y++, i++) {
            int[] o = walk.get(((i % steps) + steps) % steps);
            Direction facing = quarters[(i / Math.max(1, steps / 4)) % 4];
            int x = centerX + o[0];
            int z = centerZ + o[1];

            plan.set(x, y, z, stair.setValue(StairBlock.FACING, facing));
            // Cleared after the tread is laid, and above it only: the column a player occupies
            // standing on this step, and nothing wider
            plan.set(x, y + 1, z, Blocks.AIR.defaultBlockState());
            plan.set(x, y + 2, z, Blocks.AIR.defaultBlockState());

            plan.set(centerX, y, centerZ, newel.at(centerX, y, centerZ));
        }
    }
}
