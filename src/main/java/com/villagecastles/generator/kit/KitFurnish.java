package com.villagecastles.generator.kit;

import com.villagecastles.generator.ancient.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Furniture for the {@link Plan} sink, ported from the NBT-era {@code build/Furnish}. The one
 * convention everything hangs on: {@code floorY} is the Y of the floor BLOCK; things resting on
 * it occupy {@code floorY + 1} (the standing course).
 */
public final class KitFurnish {

    private KitFurnish() {}

    /** The course things stand in, given the floor they stand on. */
    public static int standing(int floorY) {
        return floorY + 1;
    }

    /** A table: legs in the standing course, top-slab surface one above. */
    public static void table(Plan plan, BlockPos from, Direction along, int length,
                             BlockState leg, BlockState top) {
        int y = standing(from.getY());
        BlockState surface = top.hasProperty(SlabBlock.TYPE)
            ? top.setValue(SlabBlock.TYPE, SlabType.TOP) : top;
        for (int i = 0; i < length; i++) {
            BlockPos at = new BlockPos(from.getX(), y, from.getZ()).relative(along, i);
            plan.set(at, leg);
            plan.set(at.above(), surface);
        }
    }

    /** A chair: a stair in the standing course, facing what it is drawn up to. */
    public static void chair(Plan plan, BlockPos at, Direction facing, BlockState stairs) {
        plan.set(new BlockPos(at.getX(), standing(at.getY()), at.getZ()),
            stairs.setValue(StairBlock.FACING, facing.getOpposite()));
    }

    /** A bed, both halves, in the standing course; {@code facing} runs foot toward head. */
    public static void bed(Plan plan, BlockPos foot, Direction facing, BlockState bed) {
        BlockPos f = new BlockPos(foot.getX(), standing(foot.getY()), foot.getZ());
        plan.set(f, bed
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, facing));
        plan.set(f.relative(facing), bed
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(BedBlock.FACING, facing));
    }

    /** A block that simply stands on the floor: workstation, chest, barrel, pot. */
    public static void onFloor(Plan plan, BlockPos at, BlockState state) {
        plan.set(new BlockPos(at.getX(), standing(at.getY()), at.getZ()), state);
    }

    /** A block that stands on the floor and faces a direction. */
    public static void onFloor(Plan plan, BlockPos at, BlockState state, Direction facing) {
        BlockState placed = state.hasProperty(HorizontalDirectionalBlock.FACING)
            ? state.setValue(HorizontalDirectionalBlock.FACING, facing) : state;
        plan.set(new BlockPos(at.getX(), standing(at.getY()), at.getZ()), placed);
    }

    /** A hearth: fire set into a surround at floor level rather than perched a course above. */
    public static void hearth(Plan plan, BlockPos centre, int floorY, BlockState surround,
                              BlockState fire) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                plan.set(centre.getX() + dx, floorY, centre.getZ() + dz, surround);
            }
        }
        plan.set(centre.getX(), standing(floorY), centre.getZ(), fire);
    }

    /** A lantern hanging from the ceiling block above it. */
    public static void hangingLantern(Plan plan, BlockPos at, BlockState lantern) {
        plan.set(at, lantern.hasProperty(LanternBlock.HANGING)
            ? lantern.setValue(LanternBlock.HANGING, true) : lantern);
    }
}
