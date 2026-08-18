package com.villagecastles.generator.build;

import com.villagecastles.util.StructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Doors and windows, framed, with the door actually hung.
 *
 * <p>Twelve of the fifteen shipped castles had no door anywhere in them: every generator punched
 * its entrance by clearing a column to air and moved on. A late patch in the exporter tried to make
 * that good by scanning a wall row for gaps and stamping oak doors into whatever it found, in every
 * biome, which matched dozens of positions per castle, hung nothing that survived, and left 135
 * identical north-facing stone stairs strewn along the walls of plains/castle_large.
 *
 * <p>The lesson is that an opening is not a hole. It is a hole, a frame, a threshold and a door,
 * and the four have to be placed together or the wall reads as damaged. That is what these do.
 */
public final class Openings {

    private Openings() {}

    /**
     * A hung door in a wall, with frame and threshold.
     *
     * @param bottom  position of the door's lower half. The block beneath it becomes the threshold.
     * @param facing  the direction someone walks to pass through, i.e. into the building.
     * @param door    the door block for this biome's palette.
     * @param frame   material for the jambs and lintel; null to leave the wall material.
     * @param sill    material for the threshold under the door.
     * @param twoWide true for a double door, which is hung to the right of {@code bottom}.
     */
    public static void door(ServerLevel world, BlockPos bottom, Direction facing,
                            BlockState door, BlockState frame, BlockState sill, boolean twoWide) {
        Direction right = facing.getClockWise();
        int leaves = twoWide ? 2 : 1;

        for (int i = 0; i < leaves; i++) {
            BlockPos at = bottom.relative(right, i);
            // A single door hangs on the left. A double door mirrors: left leaf LEFT, right leaf
            // RIGHT, so the pair opens outward from the centre instead of both swinging one way.
            DoorHingeSide hinge = (twoWide && i == 1) ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT;

            world.setBlock(at, door
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, hinge), StructureHelper.SET_FLAGS);
            world.setBlock(at.above(), door
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)
                .setValue(DoorBlock.HINGE, hinge), StructureHelper.SET_FLAGS);

            if (sill != null) {
                world.setBlock(at.below(), sill, StructureHelper.SET_FLAGS);
            }
        }

        if (frame == null) return;

        // Jambs either side and a lintel across the head, so the opening is trimmed rather than
        // punched. Placed after the leaves so a mis-sized frame can never bury the door itself.
        BlockPos leftJamb = bottom.relative(right.getOpposite());
        BlockPos rightJamb = bottom.relative(right, leaves);
        for (int dy = 0; dy <= 1; dy++) {
            world.setBlock(leftJamb.above(dy), frame, StructureHelper.SET_FLAGS);
            world.setBlock(rightJamb.above(dy), frame, StructureHelper.SET_FLAGS);
        }
        for (int i = -1; i <= leaves; i++) {
            world.setBlock(bottom.relative(right, i).above(2), frame, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * A window with a sill and a lintel.
     *
     * @param bottom position of the window's lowest pane.
     * @param along  direction the window runs along the wall face.
     * @param width  panes across.
     * @param height panes up.
     * @param pane   glass pane, iron bars, or air for an unglazed slit.
     * @param trim   sill and lintel material; null to leave the wall material.
     */
    public static void window(ServerLevel world, BlockPos bottom, Direction along,
                              int width, int height, BlockState pane, BlockState trim) {
        for (int i = 0; i < width; i++) {
            BlockPos column = bottom.relative(along, i);
            for (int dy = 0; dy < height; dy++) {
                world.setBlock(column.above(dy), pane, StructureHelper.SET_FLAGS);
            }
            if (trim != null) {
                world.setBlock(column.below(), trim, StructureHelper.SET_FLAGS);
                world.setBlock(column.above(height), trim, StructureHelper.SET_FLAGS);
            }
        }
    }

    /**
     * An arched gateway: a wide, tall opening with a proper head rather than a square hole.
     *
     * @param centre  centre of the opening at its lowest course.
     * @param along   direction the gateway's width runs.
     * @param halfW   half-width; the opening is {@code halfW * 2 + 1} wide.
     * @param height  clear height at the centre.
     * @param arch    material for the arch ring.
     */
    public static void gateway(ServerLevel world, BlockPos centre, Direction along,
                               int halfW, int height, BlockState arch) {
        BlockState air = Blocks.AIR.defaultBlockState();

        // Clear the opening, stepping the head down toward the jambs so it reads as an arch.
        for (int i = -halfW; i <= halfW; i++) {
            int clear = height - (int) Math.round((double) Math.abs(i) * Math.abs(i) / halfW);
            BlockPos column = centre.relative(along, i);
            for (int dy = 0; dy < clear; dy++) {
                world.setBlock(column.above(dy), air, StructureHelper.SET_FLAGS);
            }
            if (arch != null) {
                world.setBlock(column.above(clear), arch, StructureHelper.SET_FLAGS);
            }
        }
    }
}
