package com.villagecastles.generator.build;

import com.villagecastles.util.StructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Roofs that are closed by construction.
 *
 * <p>Every roof this mod shipped was written as an ad-hoc loop over Y, and each loop found its own
 * way to leave the building open to the sky: the plains manor stepped its rows in by one per course
 * and stopped two short of the ridge, leaving a pair of full-length slots either side of the cap;
 * the taiga longhouse cut its "smoke hole" by clearing the roof row at {@code x = +/-roofWidth} for
 * every Y at once, which is not a hole in the roof but the removal of both roof planes from ridge to
 * eave. Neither is expressible here: a plane is walked from both eaves inward until the rows meet,
 * so the covered span is the full span by definition, and openings are cut afterwards through a
 * method that takes a bounded rectangle.
 *
 * <p>The vanilla grammar these follow, read off {@code plains_big_house_1} and
 * {@code plains_medium_house_1}: the roof oversails the wall by a block, the eave course is flat
 * and reads as a fascia, the pitch rises from there, the ridge is a distinct material, and the
 * gable triangle is filled so the loft is enclosed rather than open at both ends.
 */
public final class Roofs {

    private Roofs() {}

    /** Which horizontal axis the ridge runs along. */
    public enum Ridge { X, Z }

    /**
     * A pitched roof over a rectangular building.
     *
     * @param wallMin    minimum corner of the wall box (Y ignored).
     * @param wallMax    maximum corner of the wall box (Y ignored).
     * @param wallTopY   Y of the wall's topmost course. The eave sits one above it.
     * @param ridge      axis the ridge runs along.
     * @param overhang   how far the roof oversails the wall on every side; 1 matches vanilla.
     * @param plane      roof plane material.
     * @param ridgeBeam  ridge material, laid over the topmost course.
     * @param gableFill  material closing the triangle at each gable end; null leaves it open.
     * @return Y of the ridge course.
     */
    public static int gable(ServerLevel world, BlockPos wallMin, BlockPos wallMax, int wallTopY,
                            Ridge ridge, int overhang,
                            BlockState plane, BlockState ridgeBeam, BlockState gableFill) {
        int minX = Math.min(wallMin.getX(), wallMax.getX());
        int maxX = Math.max(wallMin.getX(), wallMax.getX());
        int minZ = Math.min(wallMin.getZ(), wallMax.getZ());
        int maxZ = Math.max(wallMin.getZ(), wallMax.getZ());

        // Along the ridge the roof simply runs end to end; across it, the two planes climb toward
        // each other. "Low"/"high" are the two moving eave lines on the sloping axis.
        boolean alongX = ridge == Ridge.X;
        int spanMin = (alongX ? minZ : minX) - overhang;
        int spanMax = (alongX ? maxZ : maxX) + overhang;
        int runMin = (alongX ? minX : minZ) - overhang;
        int runMax = (alongX ? maxX : maxZ) + overhang;

        int low = spanMin;
        int high = spanMax;
        int y = wallTopY + 1;
        int lastY = y;

        while (low < high) {
            layRow(world, alongX, runMin, runMax, low, y, plane);
            layRow(world, alongX, runMin, runMax, high, y, plane);
            lastY = y;
            low++;
            high--;
            y++;
        }
        if (low == high) {
            // Odd span: one row left, and it is the ridge.
            layRow(world, alongX, runMin, runMax, low, y, plane);
            lastY = y;
        }

        // Ridge beam over whichever course closed the roof. An even span closes on a pair of rows,
        // which is a two-block-wide flat ridge; both get the beam so the cap reads as one piece.
        if (ridgeBeam != null) {
            if (low == high) {
                layRow(world, alongX, runMin, runMax, low, lastY, ridgeBeam);
            } else {
                layRow(world, alongX, runMin, runMax, high, lastY, ridgeBeam);
                layRow(world, alongX, runMin, runMax, low, lastY, ridgeBeam);
            }
        }

        if (gableFill != null) {
            int endA = alongX ? minX : minZ;
            int endB = alongX ? maxX : maxZ;
            fillGable(world, alongX, endA, spanMin, spanMax, wallTopY, overhang, gableFill);
            fillGable(world, alongX, endB, spanMin, spanMax, wallTopY, overhang, gableFill);
        }

        return lastY;
    }

    /** One roof row: a line at a fixed position on the sloping axis, running the length of the ridge. */
    private static void layRow(ServerLevel world, boolean alongX, int runMin, int runMax,
                               int spanAt, int y, BlockState state) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int r = runMin; r <= runMax; r++) {
            if (alongX) cursor.set(r, y, spanAt);
            else cursor.set(spanAt, y, r);
            world.setBlock(cursor, state, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Close the triangle between the wall top and the underside of the roof at one gable end, so
     * the loft is a room rather than a tunnel open at both ends.
     */
    private static void fillGable(ServerLevel world, boolean alongX, int endAt,
                                  int spanMin, int spanMax, int wallTopY, int overhang,
                                  BlockState state) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int low = spanMin;
        int high = spanMax;
        int y = wallTopY + 1;
        while (low < high) {
            // Everything strictly between the two roof rows at this course is open sky at the
            // gable end until it is filled.
            for (int s = low + 1; s <= high - 1; s++) {
                if (alongX) cursor.set(endAt, y, s);
                else cursor.set(s, y, endAt);
                world.setBlock(cursor, state, StructureHelper.SET_FLAGS);
            }
            low++;
            high--;
            y++;
        }
    }

    /**
     * A flat roof with a parapet: the deck one course above the wall top, and a crenellated rim.
     *
     * @param wallTopY Y of the wall's topmost course; the deck lands on it and the rim one higher.
     */
    public static void flat(ServerLevel world, BlockPos wallMin, BlockPos wallMax, int wallTopY,
                            BlockState deck, BlockState parapet) {
        StructureHelper.fillFloor(world, wallMin, wallMax, wallTopY, deck);
        if (parapet != null) {
            StructureHelper.addCrenellations(world, wallMin, wallMax, wallTopY, parapet);
        }
    }

    /**
     * Cut a bounded opening through a roof: a smoke hole, a dormer well, a collapsed patch.
     *
     * <p>Bounded on every axis on purpose. The taiga longhouse's smoke hole was cut by clearing a
     * position for all Y at once, which took both roof planes out from ridge to eave.
     */
    public static void openHole(ServerLevel world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlock(cursor.set(x, y, z), air, StructureHelper.SET_FLAGS);
                }
            }
        }
    }

    /**
     * Stair trim along the eave: the fascia detail that reads as a built roof edge rather than a
     * stack of cubes. Stairs face outward and sit upside down, tucking under the oversail.
     */
    public static void eaveTrim(ServerLevel world, BlockPos wallMin, BlockPos wallMax, int eaveY,
                                int overhang, BlockState stairs) {
        int minX = Math.min(wallMin.getX(), wallMax.getX()) - overhang;
        int maxX = Math.max(wallMin.getX(), wallMax.getX()) + overhang;
        int minZ = Math.min(wallMin.getZ(), wallMax.getZ()) - overhang;
        int maxZ = Math.max(wallMin.getZ(), wallMax.getZ()) + overhang;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            world.setBlock(cursor.set(x, eaveY, minZ),
                stairs.setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(x, eaveY, maxZ),
                stairs.setValue(StairBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            world.setBlock(cursor.set(minX, eaveY, z),
                stairs.setValue(StairBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(maxX, eaveY, z),
                stairs.setValue(StairBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }
    }
}
