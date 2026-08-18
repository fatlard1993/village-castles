package com.villagecastles.generator.build;

import com.villagecastles.util.StructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Furniture placed against a named floor, not against a remembered number.
 *
 * <p>One convention, stated once: {@code floorY} is the Y of the floor <em>block</em>. Everything
 * that rests on that floor occupies {@code floorY + 1}. Every generator in this mod re-derived that
 * by hand and several got it wrong in opposite directions at once. The plains manor's dining table
 * sank its fence legs into the floor course and put the tabletop at seat height, so the table was
 * flush with the carpet and the diners sat level with their dinner; the taiga longhouse laid its
 * sleeping platforms as bottom slabs and then placed the beds a full block above them, leaving four
 * beds hovering half a block over the boards.
 *
 * <p>Taking {@code floorY} and doing the arithmetic here is the whole fix.
 */
public final class Furnish {

    private Furnish() {}

    /** The course things stand in, given the floor they stand on. */
    public static int standing(int floorY) {
        return floorY + 1;
    }

    /**
     * A table: legs in the standing course, top one above, so it meets a seated chair at the
     * right height.
     *
     * @param from  one end of the table, at floor level.
     * @param along direction the table runs.
     * @param length blocks long.
     * @param leg   leg material, normally a fence.
     * @param top   tabletop material, normally a slab.
     */
    public static void table(ServerLevel world, BlockPos from, Direction along, int length,
                             BlockState leg, BlockState top) {
        int y = standing(from.getY());
        BlockState surface = top.hasProperty(SlabBlock.TYPE) ? top.setValue(SlabBlock.TYPE, SlabType.TOP) : top;
        for (int i = 0; i < length; i++) {
            BlockPos at = new BlockPos(from.getX(), y, from.getZ()).relative(along, i);
            world.setBlock(at, leg, StructureHelper.SET_FLAGS);
            world.setBlock(at.above(), surface, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * A chair: a stair in the standing course, facing what it is drawn up to.
     *
     * @param at     position at floor level.
     * @param facing the direction the sitter looks.
     */
    public static void chair(ServerLevel world, BlockPos at, Direction facing, BlockState stairs) {
        world.setBlock(new BlockPos(at.getX(), standing(at.getY()), at.getZ()),
            stairs.setValue(StairBlock.FACING, facing.getOpposite()), StructureHelper.SET_FLAGS);
    }

    /**
     * A bed, both halves, in the standing course.
     *
     * @param foot   position of the foot at floor level.
     * @param facing direction from foot toward head.
     */
    public static void bed(ServerLevel world, BlockPos foot, Direction facing, BlockState bed) {
        BlockPos f = new BlockPos(foot.getX(), standing(foot.getY()), foot.getZ());
        world.setBlock(f, bed
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, facing), StructureHelper.SET_FLAGS);
        world.setBlock(f.relative(facing), bed
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(BedBlock.FACING, facing), StructureHelper.SET_FLAGS);
    }

    /**
     * A raised platform to stand furniture on.
     *
     * <p>Top slabs, not bottom slabs. A bottom slab's surface sits half a block below the next
     * course, so anything placed on top of it hovers; a top slab fills the upper half and gives a
     * flush surface at the same height. This is the distinction that left the longhouse's beds in
     * mid-air.
     */
    public static void platform(ServerLevel world, BlockPos min, BlockPos max, int floorY, BlockState slab) {
        BlockState top = slab.hasProperty(SlabBlock.TYPE) ? slab.setValue(SlabBlock.TYPE, SlabType.TOP) : slab;
        StructureHelper.fillFloor(world, min, max, standing(floorY), top);
    }

    /** A block that simply stands on the floor: workstation, chest, barrel, pot. */
    public static void onFloor(ServerLevel world, BlockPos at, BlockState state) {
        world.setBlock(new BlockPos(at.getX(), standing(at.getY()), at.getZ()), state, StructureHelper.SET_FLAGS);
    }

    /** A block that stands on the floor and faces a direction. */
    public static void onFloor(ServerLevel world, BlockPos at, BlockState state, Direction facing) {
        BlockState placed = state.hasProperty(HorizontalDirectionalBlock.FACING)
            ? state.setValue(HorizontalDirectionalBlock.FACING, facing) : state;
        world.setBlock(new BlockPos(at.getX(), standing(at.getY()), at.getZ()), placed, StructureHelper.SET_FLAGS);
    }

    /**
     * A hearth: a fire set into a surround of the given material, at floor level rather than
     * perched a course above it.
     */
    public static void hearth(ServerLevel world, BlockPos centre, int floorY, BlockState surround, BlockState fire) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.setBlock(cursor.set(centre.getX() + dx, floorY, centre.getZ() + dz),
                    surround, StructureHelper.SET_FLAGS);
            }
        }
        world.setBlock(cursor.set(centre.getX(), standing(floorY), centre.getZ()),
            fire, StructureHelper.SET_FLAGS);
    }

    /** A lantern hanging from the ceiling block above it. */
    public static void hangingLantern(ServerLevel world, BlockPos at, BlockState lantern) {
        world.setBlock(at, lantern.setValue(
            net.minecraft.world.level.block.LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
    }

    /** Empty air, for carving a room out after the fact. */
    public static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }
}
