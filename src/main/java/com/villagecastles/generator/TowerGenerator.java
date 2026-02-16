package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Generates defensive towers for castle walls.
 * Towers can be:
 * - Corner towers (larger, cylindrical)
 * - Wall towers (medium, square)
 * - Watch towers (small, for walls)
 */
public class TowerGenerator {

    public enum TowerType {
        CORNER(5, 20, true),    // Large cylindrical corner towers
        WALL(4, 16, false),     // Medium square wall towers
        WATCH(3, 12, false);    // Small square watch towers

        public final int radius;
        public final int height;
        public final boolean cylindrical;

        TowerType(int radius, int height, boolean cylindrical) {
            this.radius = radius;
            this.height = height;
            this.cylindrical = cylindrical;
        }
    }

    private final BiomePalette palette;
    private final Random random;

    public TowerGenerator(BiomePalette palette, Random random) {
        this.palette = palette;
        this.random = random;
    }

    /**
     * Generate a tower at the specified position.
     */
    public void generate(ServerWorld world, BlockPos center, TowerType type) {
        if (type.cylindrical) {
            generateCylindricalTower(world, center, type);
        } else {
            generateSquareTower(world, center, type);
        }
    }

    private void generateCylindricalTower(ServerWorld world, BlockPos center, TowerType type) {
        int radius = type.radius;
        int height = type.height;

        // Foundation
        StructureHelper.buildCylinder(world, center.down(2), radius + 1, 3, Blocks.COBBLESTONE.getDefaultState(), false);

        // Main tower walls
        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    boolean onEdge = dist <= radius && dist > radius - 1.5;
                    boolean inside = dist < radius - 1.5;

                    BlockPos pos = center.add(x, y, z);

                    if (onEdge) {
                        BlockState wall = palette.getRandomWallBlock(random).getDefaultState();
                        world.setBlockState(pos, wall);
                    } else if (inside) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Floors every 5 blocks
        for (int floor = 0; floor < height; floor += 5) {
            for (int x = -radius + 1; x < radius; x++) {
                for (int z = -radius + 1; z < radius; z++) {
                    if (Math.sqrt(x * x + z * z) < radius - 1) {
                        world.setBlockState(center.add(x, floor, z), palette.getFloorState());
                    }
                }
            }
        }

        // Spiral staircase
        StructureHelper.createSpiralStairs(world, center, radius - 1, height,
            palette.woodStairs.getDefaultState(), palette.getLogState());

        // Windows on each floor
        for (int floor = 2; floor < height; floor += 5) {
            addCircularWindows(world, center, radius, floor);
        }

        // Crenellated top
        StructureHelper.addCircularCrenellations(world, center, radius, height, palette.getPrimaryWallState());

        // Conical roof cap
        for (int y = 1; y <= 3; y++) {
            int roofRadius = radius - y;
            if (roofRadius > 0) {
                for (int x = -roofRadius; x <= roofRadius; x++) {
                    for (int z = -roofRadius; z <= roofRadius; z++) {
                        if (Math.sqrt(x * x + z * z) <= roofRadius) {
                            world.setBlockState(center.add(x, height + y, z), palette.getRoofState());
                        }
                    }
                }
            }
        }

        // Flag pole at top
        world.setBlockState(center.up(height + 4), palette.getFenceState());
        world.setBlockState(center.up(height + 5), palette.getFenceState());
    }

    private void generateSquareTower(ServerWorld world, BlockPos center, TowerType type) {
        int halfSize = type.radius;
        int height = type.height;

        BlockPos corner1 = center.add(-halfSize, 0, -halfSize);
        BlockPos corner2 = center.add(halfSize, height, halfSize);

        // Foundation
        BlockPos foundCorner1 = corner1.add(-1, -2, -1);
        BlockPos foundCorner2 = corner2.add(1, -height + 1, 1);
        StructureHelper.fillBox(world, foundCorner1, foundCorner2.withY(center.getY() - 1), Blocks.COBBLESTONE.getDefaultState());

        // Walls
        for (int y = 0; y < height; y++) {
            for (int x = -halfSize; x <= halfSize; x++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    boolean isEdge = x == -halfSize || x == halfSize || z == -halfSize || z == halfSize;
                    BlockPos pos = center.add(x, y, z);

                    if (isEdge) {
                        world.setBlockState(pos, palette.getRandomWallBlock(random).getDefaultState());
                    } else {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Floors
        for (int floor = 0; floor < height; floor += 5) {
            for (int x = -halfSize + 1; x < halfSize; x++) {
                for (int z = -halfSize + 1; z < halfSize; z++) {
                    world.setBlockState(center.add(x, floor, z), palette.getFloorState());
                }
            }
        }

        // Ladder with backing block - ladder faces west (away from east wall)
        // Ensure the wall block behind it is solid
        for (int y = 1; y < height; y++) {
            // Backing block on the wall (already placed as part of walls, but ensure it's solid)
            BlockPos backingPos = center.add(halfSize, y, 0);
            if (world.getBlockState(backingPos).isAir()) {
                world.setBlockState(backingPos, palette.getPrimaryWallState());
            }
            // Ladder facing west (back against east wall)
            world.setBlockState(center.add(halfSize - 1, y, 0),
                Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.WEST));
        }

        // Windows
        for (int floor = 2; floor < height; floor += 5) {
            addSquareWindows(world, center, halfSize, floor);
        }

        // Crenellations
        StructureHelper.addCrenellations(world, corner1.withY(center.getY() + height),
            corner2.withY(center.getY() + height), height + 1, palette.getPrimaryWallState());

        // Lighting - place lanterns on floor (not hanging)
        // Floor lanterns sit on the floor block
        for (int floor = 0; floor < height; floor += 5) {
            BlockPos lanternPos = center.up(floor + 1);
            // Make sure there's a floor block below
            if (!world.getBlockState(lanternPos.down()).isAir()) {
                world.setBlockState(lanternPos,
                    palette.light.getDefaultState().with(LanternBlock.HANGING, false));
            }
        }
    }

    private void addCircularWindows(ServerWorld world, BlockPos center, int radius, int y) {
        BlockState bars = palette.getBarsState();

        // 4 windows, one each direction
        world.setBlockState(center.add(radius, y, 0), bars);
        world.setBlockState(center.add(radius, y + 1, 0), bars);

        world.setBlockState(center.add(-radius, y, 0), bars);
        world.setBlockState(center.add(-radius, y + 1, 0), bars);

        world.setBlockState(center.add(0, y, radius), bars);
        world.setBlockState(center.add(0, y + 1, radius), bars);

        world.setBlockState(center.add(0, y, -radius), bars);
        world.setBlockState(center.add(0, y + 1, -radius), bars);
    }

    private void addSquareWindows(ServerWorld world, BlockPos center, int halfSize, int y) {
        BlockState bars = palette.getBarsState();

        // Window on each wall
        world.setBlockState(center.add(halfSize, y, 0), bars);
        world.setBlockState(center.add(halfSize, y + 1, 0), bars);

        world.setBlockState(center.add(-halfSize, y, 0), bars);
        world.setBlockState(center.add(-halfSize, y + 1, 0), bars);

        world.setBlockState(center.add(0, y, halfSize), bars);
        world.setBlockState(center.add(0, y + 1, halfSize), bars);

        world.setBlockState(center.add(0, y, -halfSize), bars);
        world.setBlockState(center.add(0, y + 1, -halfSize), bars);
    }

    /**
     * Generate a tower with connection points for walls.
     * Returns positions where walls should connect.
     */
    public BlockPos[] generateWithConnections(ServerWorld world, BlockPos center, TowerType type, Direction... wallDirections) {
        generate(world, center, type);

        // Return connection points at base of tower
        BlockPos[] connections = new BlockPos[wallDirections.length];
        for (int i = 0; i < wallDirections.length; i++) {
            Direction dir = wallDirections[i];
            connections[i] = center.offset(dir, type.radius + 1);
        }
        return connections;
    }
}
