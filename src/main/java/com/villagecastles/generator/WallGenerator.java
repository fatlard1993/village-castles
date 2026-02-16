package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Generates castle walls with walkways, crenellations, and defensive features.
 */
public class WallGenerator {

    private final BiomePalette palette;
    private final Random random;

    // Wall dimensions
    private static final int WALL_THICKNESS = 2;
    private static final int WALL_HEIGHT = 8;
    private static final int WALKWAY_HEIGHT = 6;

    public WallGenerator(BiomePalette palette, Random random) {
        this.palette = palette;
        this.random = random;
    }

    /**
     * Generate a wall segment between two points.
     */
    public void generate(ServerWorld world, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int length = (int) Math.sqrt(dx * dx + dz * dz);

        if (length == 0) return;

        // Determine wall direction
        Direction facing;
        if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        // Build wall block by block
        for (int i = 0; i <= length; i++) {
            double t = (double) i / length;
            int x = start.getX() + (int) (dx * t);
            int z = start.getZ() + (int) (dz * t);

            BlockPos basePos = new BlockPos(x, start.getY(), z);
            buildWallSection(world, basePos, facing);
        }
    }

    /**
     * Build a single wall section (vertical slice).
     */
    private void buildWallSection(ServerWorld world, BlockPos base, Direction facing) {
        Direction perpendicular = facing.rotateYClockwise();

        // Foundation
        for (int p = -WALL_THICKNESS / 2; p <= WALL_THICKNESS / 2; p++) {
            BlockPos foundPos = base.offset(perpendicular, p).down(2);
            for (int y = 0; y < 3; y++) {
                world.setBlockState(foundPos.up(y), Blocks.COBBLESTONE.getDefaultState());
            }
        }

        // Main wall body
        for (int y = 0; y < WALL_HEIGHT; y++) {
            for (int p = -WALL_THICKNESS / 2; p <= WALL_THICKNESS / 2; p++) {
                BlockPos wallPos = base.offset(perpendicular, p).up(y);
                BlockState wallBlock = palette.getRandomWallBlock(random).getDefaultState();
                world.setBlockState(wallPos, wallBlock);
            }
        }

        // Walkway (interior side hollow)
        for (int y = WALKWAY_HEIGHT; y < WALL_HEIGHT; y++) {
            // Clear walkway space (one side)
            BlockPos walkwayPos = base.offset(perpendicular, WALL_THICKNESS / 2).up(y);
            if (y < WALL_HEIGHT - 1) {
                world.setBlockState(walkwayPos, Blocks.AIR.getDefaultState());
            }
        }

        // Floor of walkway
        world.setBlockState(base.offset(perpendicular, WALL_THICKNESS / 2).up(WALKWAY_HEIGHT - 1),
            palette.getFloorState());

        // Crenellations on top
        // Alternating pattern - skip every other block
        int posHash = base.getX() + base.getZ();
        if (posHash % 2 == 0) {
            world.setBlockState(base.up(WALL_HEIGHT), palette.getPrimaryWallState());
        }
    }

    /**
     * Generate a wall with arrow slits at intervals.
     */
    public void generateWithArrowSlits(ServerWorld world, BlockPos start, BlockPos end, int slitInterval) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int length = (int) Math.sqrt(dx * dx + dz * dz);

        if (length == 0) return;

        Direction facing;
        if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        for (int i = 0; i <= length; i++) {
            double t = (double) i / length;
            int x = start.getX() + (int) (dx * t);
            int z = start.getZ() + (int) (dz * t);

            BlockPos basePos = new BlockPos(x, start.getY(), z);
            buildWallSection(world, basePos, facing);

            // Add arrow slit
            if (i > 0 && i < length && i % slitInterval == 0) {
                addArrowSlit(world, basePos, facing);
            }
        }
    }

    /**
     * Add an arrow slit (narrow window) to the wall.
     */
    private void addArrowSlit(ServerWorld world, BlockPos base, Direction facing) {
        // Arrow slits are 1 wide, 3 tall, at eye level
        int slitHeight = 3;
        int slitStart = 3;

        for (int y = slitStart; y < slitStart + slitHeight; y++) {
            world.setBlockState(base.up(y), palette.getBarsState());
        }
    }

    /**
     * Generate a wall corner (L-shaped).
     */
    public void generateCorner(ServerWorld world, BlockPos corner, Direction from, Direction to) {
        // Short wall segments meeting at corner
        int cornerLength = WALL_THICKNESS * 2;

        BlockPos fromEnd = corner.offset(from.getOpposite(), cornerLength);
        BlockPos toEnd = corner.offset(to, cornerLength);

        generate(world, fromEnd, corner);
        generate(world, corner, toEnd);

        // Fill in corner block
        for (int y = 0; y <= WALL_HEIGHT; y++) {
            world.setBlockState(corner.up(y), palette.getPrimaryWallState());
        }
    }

    /**
     * Generate wall with torch/lantern placement.
     */
    public void generateWithLighting(ServerWorld world, BlockPos start, BlockPos end, int lightInterval) {
        generateWithArrowSlits(world, start, end, 6);

        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int length = (int) Math.sqrt(dx * dx + dz * dz);

        Direction facing;
        if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        Direction perpendicular = facing.rotateYClockwise();

        // Add lights on walkway (sitting on floor)
        for (int i = lightInterval / 2; i <= length; i += lightInterval) {
            double t = (double) i / length;
            int x = start.getX() + (int) (dx * t);
            int z = start.getZ() + (int) (dz * t);

            BlockPos lightPos = new BlockPos(x, start.getY() + WALKWAY_HEIGHT, z)
                .offset(perpendicular, WALL_THICKNESS / 2);
            // Lantern sitting on the walkway floor
            world.setBlockState(lightPos, palette.light.getDefaultState().with(LanternBlock.HANGING, false));
        }
    }

    public static int getWallHeight() {
        return WALL_HEIGHT;
    }

    public static int getWalkwayHeight() {
        return WALKWAY_HEIGHT;
    }
}
