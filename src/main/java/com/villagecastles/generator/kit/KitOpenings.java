package com.villagecastles.generator.kit;

import com.villagecastles.generator.ancient.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Doors and windows for the {@link Plan} sink, ported from the NBT-era {@code build/Openings}
 * with the geometry kept verbatim. Frames are placed after leaves so a mis-sized frame can never
 * bury the door itself; last write wins in the plan just as it did in the world.
 */
public final class KitOpenings {

    private KitOpenings() {}

    /**
     * A hung door in a wall, with frame and threshold.
     *
     * @param bottom  position of the door's lower half; the block beneath becomes the threshold.
     * @param facing  the direction someone walks to pass through, i.e. into the building.
     * @param frame   jambs and lintel; null to leave the wall material.
     * @param sill    threshold under the door; null to leave it.
     * @param twoWide true for a double door, hung to the right of {@code bottom}.
     */
    public static void door(Plan plan, BlockPos bottom, Direction facing,
                            BlockState door, BlockState frame, BlockState sill, boolean twoWide) {
        Direction right = facing.getClockWise();
        int leaves = twoWide ? 2 : 1;

        for (int i = 0; i < leaves; i++) {
            BlockPos at = bottom.relative(right, i);
            // A single door hangs on the left; a double door mirrors so the pair opens outward
            // from the centre instead of both swinging one way.
            DoorHingeSide hinge = (twoWide && i == 1) ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;

            plan.set(at, door
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, hinge));
            plan.set(at.above(), door
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, hinge));

            if (sill != null) plan.set(at.below(), sill);
        }

        if (frame == null) return;

        BlockPos leftJamb = bottom.relative(right.getOpposite());
        BlockPos rightJamb = bottom.relative(right, leaves);
        for (int dy = 0; dy <= 1; dy++) {
            plan.set(leftJamb.above(dy), frame);
            plan.set(rightJamb.above(dy), frame);
        }
        for (int i = -1; i <= leaves; i++) {
            plan.set(bottom.relative(right, i).above(2), frame);
        }
    }

    /**
     * A window with a sill and a lintel.
     *
     * @param pane glass pane, iron bars, or air for an unglazed slit.
     * @param trim sill and lintel material; null to leave the wall material.
     */
    public static void window(Plan plan, BlockPos bottom, Direction along,
                              int width, int height, BlockState pane, BlockState trim) {
        for (int i = 0; i < width; i++) {
            BlockPos column = bottom.relative(along, i);
            for (int dy = 0; dy < height; dy++) {
                plan.set(column.above(dy), pane);
            }
            if (trim != null) {
                plan.set(column.below(), trim);
                plan.set(column.above(height), trim);
            }
        }
    }
}
