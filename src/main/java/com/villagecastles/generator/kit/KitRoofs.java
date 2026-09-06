package com.villagecastles.generator.kit;

import com.villagecastles.generator.ancient.Geo;
import com.villagecastles.generator.ancient.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Roofs that are closed by construction, drawing into a {@link Plan}. Ported from the NBT-era
 * {@code build/Roofs} with the geometry kept verbatim: a plane is walked from both eaves inward
 * until the rows meet, so the covered span is the full span by definition, and openings are cut
 * afterwards through a bounded rectangle.
 *
 * <p>The vanilla grammar (read off {@code plains_big_house_1}): the roof oversails the wall by a
 * block, the eave course reads as a fascia, the pitch rises from there, the ridge is a distinct
 * material, and the gable triangle is filled so the loft is enclosed.
 */
public final class KitRoofs {

    private KitRoofs() {}

    /** Which horizontal axis the ridge runs along. */
    public enum Ridge { X, Z }

    /**
     * A pitched roof over a rectangular building.
     *
     * @param wallTopY  Y of the wall's topmost course; the eave sits one above it.
     * @param overhang  how far the roof oversails the wall on every side; 1 matches vanilla.
     * @param gableFill material closing the triangle at each gable end; null leaves it open.
     * @return Y of the ridge course.
     */
    public static int gable(Plan plan, BlockPos wallMin, BlockPos wallMax, int wallTopY,
                            Ridge ridge, int overhang,
                            BlockState plane, BlockState ridgeBeam, BlockState gableFill) {
        int minX = Math.min(wallMin.getX(), wallMax.getX());
        int maxX = Math.max(wallMin.getX(), wallMax.getX());
        int minZ = Math.min(wallMin.getZ(), wallMax.getZ());
        int maxZ = Math.max(wallMin.getZ(), wallMax.getZ());

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
            layRow(plan, alongX, runMin, runMax, low, y, plane);
            layRow(plan, alongX, runMin, runMax, high, y, plane);
            lastY = y;
            low++;
            high--;
            y++;
        }
        if (low == high) {
            // Odd span: one row left, and it is the ridge.
            layRow(plan, alongX, runMin, runMax, low, y, plane);
            lastY = y;
        }

        // An even span closes on a pair of rows - a two-wide flat ridge; both get the beam.
        if (ridgeBeam != null) {
            if (low == high) {
                layRow(plan, alongX, runMin, runMax, low, lastY, ridgeBeam);
            } else {
                layRow(plan, alongX, runMin, runMax, high, lastY, ridgeBeam);
                layRow(plan, alongX, runMin, runMax, low, lastY, ridgeBeam);
            }
        }

        if (gableFill != null) {
            int endA = alongX ? minX : minZ;
            int endB = alongX ? maxX : maxZ;
            fillGable(plan, alongX, endA, spanMin, spanMax, wallTopY, gableFill);
            fillGable(plan, alongX, endB, spanMin, spanMax, wallTopY, gableFill);
        }

        return lastY;
    }

    private static void layRow(Plan plan, boolean alongX, int runMin, int runMax,
                               int spanAt, int y, BlockState state) {
        for (int r = runMin; r <= runMax; r++) {
            if (alongX) plan.set(r, y, spanAt, state);
            else plan.set(spanAt, y, r, state);
        }
    }

    /** Close the triangle at one gable end so the loft is a room, not a tunnel. */
    private static void fillGable(Plan plan, boolean alongX, int endAt,
                                  int spanMin, int spanMax, int wallTopY, BlockState state) {
        int low = spanMin;
        int high = spanMax;
        int y = wallTopY + 1;
        while (low < high) {
            for (int s = low + 1; s <= high - 1; s++) {
                if (alongX) plan.set(endAt, y, s, state);
                else plan.set(s, y, endAt, state);
            }
            low++;
            high--;
            y++;
        }
    }

    /** A flat roof with a crenellated parapet: deck on the wall top, merlons on the rim. */
    public static void flat(Plan plan, BlockPos wallMin, BlockPos wallMax, int wallTopY,
                            BlockState deck, BlockState parapet) {
        int minX = Math.min(wallMin.getX(), wallMax.getX());
        int maxX = Math.max(wallMin.getX(), wallMax.getX());
        int minZ = Math.min(wallMin.getZ(), wallMax.getZ());
        int maxZ = Math.max(wallMin.getZ(), wallMax.getZ());
        Geo.floor(plan, minX, minZ, maxX, maxZ, wallTopY, Geo.solid(deck));
        if (parapet == null) return;
        int i = 0;
        for (int x = minX; x <= maxX; x++) {
            if (i++ % 2 == 0) plan.set(x, wallTopY + 1, minZ, parapet);
            if (i % 2 == 0) plan.set(x, wallTopY + 1, maxZ, parapet);
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            if (i++ % 2 == 0) plan.set(minX, wallTopY + 1, z, parapet);
            if (i % 2 == 0) plan.set(maxX, wallTopY + 1, z, parapet);
        }
    }

    /** Cut a bounded opening through a roof: a smoke hole, a dormer well. */
    public static void openHole(Plan plan, int minX, int maxX, int minY, int maxY,
                                int minZ, int maxZ) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    plan.set(x, y, z, air);
                }
            }
        }
    }

    /**
     * Stair trim along the eave: the fascia that reads as a built roof edge. Stairs face outward
     * and hang upside down, tucking under the oversail.
     */
    public static void eaveTrim(Plan plan, BlockPos wallMin, BlockPos wallMax, int eaveY,
                                int overhang, BlockState stairs) {
        int minX = Math.min(wallMin.getX(), wallMax.getX()) - overhang;
        int maxX = Math.max(wallMin.getX(), wallMax.getX()) + overhang;
        int minZ = Math.min(wallMin.getZ(), wallMax.getZ()) - overhang;
        int maxZ = Math.max(wallMin.getZ(), wallMax.getZ()) + overhang;
        BlockState fascia = stairs.hasProperty(StairBlock.HALF)
            ? stairs.setValue(StairBlock.HALF, Half.TOP) : stairs;

        for (int x = minX; x <= maxX; x++) {
            plan.set(x, eaveY, minZ, fascia.setValue(StairBlock.FACING, Direction.SOUTH));
            plan.set(x, eaveY, maxZ, fascia.setValue(StairBlock.FACING, Direction.NORTH));
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            plan.set(minX, eaveY, z, fascia.setValue(StairBlock.FACING, Direction.EAST));
            plan.set(maxX, eaveY, z, fascia.setValue(StairBlock.FACING, Direction.WEST));
        }
    }
}
