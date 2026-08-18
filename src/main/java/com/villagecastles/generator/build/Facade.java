package com.villagecastles.generator.build;

import com.villagecastles.util.StructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wall faces with the grammar vanilla village buildings actually use.
 *
 * <p>Read a vanilla plains house in section and the wall is not one material standing in a box:
 *
 * <pre>
 *   y10|===========|  eave, oversailing the wall
 *   y 5| =       = |  upper body
 *   y 4| | ===== | |  storey band, with log posts at the corners
 *   y 3| c*     *c |  lower body
 *   y 1| cb      c |  plinth, in the heavier material
 *   y 0| dc cccc d |
 * </pre>
 *
 * <p>Five parts: a plinth in a heavier material, a body, a band marking each storey line, posts at
 * the corners and at intervals along the run, and openings framed by those. The mod's own walls
 * had one of the five. {@code generatePlainsManor} changed material at the storey line but laid no
 * band, so the change reads as the stone simply running out; its upper storey placed vertical log
 * posts with no sill or head beam to carry, which is a pattern no builder uses and the eye reads
 * as stripes. That absence is the "bland and weird" of it, and no swap of block palette fixes it.
 */
public final class Facade {

    private Facade() {}

    /** A storey: how tall its body is, what it is built from, and what marks its head. */
    public record Storey(int height, BlockState body, BlockState post, BlockState band) {}

    /**
     * Raise the four walls of a rectangular building, storey by storey.
     *
     * <p>Walls are one block thick. Two-block-thick walls were used throughout the mod's desert
     * and savanna work; vanilla reserves that for fortification, not dwelling, and at these
     * footprints it costs more interior than it buys silhouette.
     *
     * @param min      minimum corner; its Y is the plinth's first course.
     * @param max      maximum corner; only X and Z are read.
     * @param plinth   material for the base course(s).
     * @param plinthH  how many courses of plinth; 1 or 2 reads best.
     * @param storeys  storeys from the ground up.
     * @param postStep spacing between intermediate posts along each run; 0 for corners only.
     * @return Y of the topmost wall course.
     */
    public static int walls(ServerLevel world, BlockPos min, BlockPos max,
                            BlockState plinth, int plinthH, java.util.List<Storey> storeys, int postStep) {
        int minX = Math.min(min.getX(), max.getX());
        int maxX = Math.max(min.getX(), max.getX());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxZ = Math.max(min.getZ(), max.getZ());
        int y = min.getY();

        for (int c = 0; c < plinthH; c++) {
            ring(world, minX, maxX, minZ, maxZ, y, plinth);
            y++;
        }

        for (Storey storey : storeys) {
            for (int c = 0; c < storey.height(); c++) {
                ring(world, minX, maxX, minZ, maxZ, y, storey.body());
                if (storey.post() != null) {
                    posts(world, minX, maxX, minZ, maxZ, y, postStep, storey.post());
                }
                y++;
            }
            // The band is what makes the storey line read as structure rather than as the material
            // running out. It is a full ring, so it also ties the four posts together.
            if (storey.band() != null) {
                ring(world, minX, maxX, minZ, maxZ, y, storey.band());
                y++;
            }
        }
        return y - 1;
    }

    /** One closed rectangular ring of wall at a single course. */
    public static void ring(ServerLevel world, int minX, int maxX, int minZ, int maxZ, int y, BlockState state) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            world.setBlock(cursor.set(x, y, minZ), state, StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(x, y, maxZ), state, StructureHelper.SET_FLAGS);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            world.setBlock(cursor.set(minX, y, z), state, StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(maxX, y, z), state, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Corner posts, plus intermediate posts every {@code step} blocks measured inward from each
     * corner. Measuring from the corners rather than from a world coordinate keeps the rhythm
     * symmetric and keeps it identical wherever in the world the building lands.
     */
    private static void posts(ServerLevel world, int minX, int maxX, int minZ, int maxZ,
                              int y, int step, BlockState post) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int[] c : new int[][]{{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}}) {
            world.setBlock(cursor.set(c[0], y, c[1]), post, StructureHelper.SET_FLAGS);
        }
        if (step <= 0) return;

        for (int x = minX + step; x < maxX; x += step) {
            if (maxX - x < step) break;
            world.setBlock(cursor.set(x, y, minZ), post, StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(x, y, maxZ), post, StructureHelper.SET_FLAGS);
        }
        for (int z = minZ + step; z < maxZ; z += step) {
            if (maxZ - z < step) break;
            world.setBlock(cursor.set(minX, y, z), post, StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(maxX, y, z), post, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Hollow the inside of a wall box and lay a floor at its base.
     *
     * @param floorY Y of the floor course. Anything standing on this floor occupies floorY + 1;
     *               see {@link Furnish}.
     */
    public static void room(ServerLevel world, BlockPos min, BlockPos max, int floorY, int ceilingY,
                            BlockState floor) {
        int minX = Math.min(min.getX(), max.getX()) + 1;
        int maxX = Math.max(min.getX(), max.getX()) - 1;
        int minZ = Math.min(min.getZ(), max.getZ()) + 1;
        int maxZ = Math.max(min.getZ(), max.getZ()) - 1;
        if (minX > maxX || minZ > maxZ) return;

        StructureHelper.clearInterior(world,
            new BlockPos(minX, floorY + 1, minZ), new BlockPos(maxX, ceilingY, maxZ));
        StructureHelper.fillFloor(world,
            new BlockPos(minX, floorY, minZ), new BlockPos(maxX, floorY, maxZ), floorY, floor);
    }
}
