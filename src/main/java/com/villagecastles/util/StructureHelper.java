package com.villagecastles.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Utility class for procedurally building castle structures.
 */
public class StructureHelper {

    private static final int SET_FLAGS = net.minecraft.block.Block.NOTIFY_LISTENERS; // Flag 2: no neighbor updates

    /**
     * Fill a 3D box with a block state.
     */
    public static void fillBox(ServerWorld world, BlockPos corner1, BlockPos corner2, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlockState(mutable.set(x, y, z), state, SET_FLAGS);
                }
            }
        }
    }

    /**
     * Create hollow box (walls only, no floor/ceiling).
     */
    public static void hollowWalls(ServerWorld world, BlockPos corner1, BlockPos corner2, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isEdge = x == minX || x == maxX || z == minZ || z == maxZ;
                    if (isEdge) {
                        world.setBlockState(mutable.set(x, y, z), state, SET_FLAGS);
                    }
                }
            }
        }
    }

    /**
     * Create a floor at a specific Y level within bounds.
     */
    public static void fillFloor(ServerWorld world, BlockPos corner1, BlockPos corner2, int y, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlockState(mutable.set(x, y, z), state, SET_FLAGS);
            }
        }
    }

    /**
     * Clear the interior of a box (replace with air).
     */
    public static void clearInterior(ServerWorld world, BlockPos corner1, BlockPos corner2) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockState air = Blocks.AIR.getDefaultState();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlockState(mutable.set(x, y, z), air, SET_FLAGS);
                }
            }
        }
    }

    /**
     * Build a cylindrical tower.
     */
    public static void buildCylinder(ServerWorld world, BlockPos center, int radius, int height, BlockState state, boolean hollow) {
        int baseY = center.getY();
        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    boolean inCircle = dist <= radius;
                    boolean onEdge = dist > radius - 1.5;

                    if (inCircle && (!hollow || onEdge)) {
                        world.setBlockState(center.add(x, y, z), state);
                    } else if (hollow && inCircle) {
                        world.setBlockState(center.add(x, y, z), Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    /**
     * Add crenellations (battlements) on top of walls.
     */
    public static void addCrenellations(ServerWorld world, BlockPos corner1, BlockPos corner2, int topY, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        // North and South walls
        for (int x = minX; x <= maxX; x++) {
            if ((x - minX) % 2 == 0) {
                world.setBlockState(new BlockPos(x, topY, minZ), state);
                world.setBlockState(new BlockPos(x, topY, maxZ), state);
            }
        }

        // East and West walls
        for (int z = minZ; z <= maxZ; z++) {
            if ((z - minZ) % 2 == 0) {
                world.setBlockState(new BlockPos(minX, topY, z), state);
                world.setBlockState(new BlockPos(maxX, topY, z), state);
            }
        }
    }

    /**
     * Add crenellations to a circular tower top.
     */
    public static void addCircularCrenellations(ServerWorld world, BlockPos center, int radius, int topY, BlockState state) {
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int x = (int) Math.round(radius * Math.cos(rad));
            int z = (int) Math.round(radius * Math.sin(rad));
            world.setBlockState(center.add(x, topY, z), state);
        }
    }

    /**
     * Create a window (vertical slit or arched).
     */
    public static void createWindow(ServerWorld world, BlockPos pos, Direction facing, int height, BlockState bars) {
        for (int y = 0; y < height; y++) {
            world.setBlockState(pos.up(y), bars);
        }
    }

    /**
     * Create an arched doorway.
     */
    public static void createArchedDoorway(ServerWorld world, BlockPos baseCenter, Direction facing, int width, int height) {
        // Clear the doorway
        int halfWidth = width / 2;
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 0; y < height; y++) {
                BlockPos offset = facing.getAxis() == Direction.Axis.X
                    ? baseCenter.add(0, y, x)
                    : baseCenter.add(x, y, 0);
                world.setBlockState(offset, Blocks.AIR.getDefaultState());
            }
        }

        // Add arch at top (simplified - just corners filled)
        if (width >= 3 && height >= 3) {
            BlockPos leftTop = facing.getAxis() == Direction.Axis.X
                ? baseCenter.add(0, height - 1, -halfWidth)
                : baseCenter.add(-halfWidth, height - 1, 0);
            BlockPos rightTop = facing.getAxis() == Direction.Axis.X
                ? baseCenter.add(0, height - 1, halfWidth)
                : baseCenter.add(halfWidth, height - 1, 0);
            // Arch corners would go here based on existing wall material
        }
    }

    /**
     * Place a chest with loot table.
     */
    public static void placeChest(ServerWorld world, BlockPos pos, Direction facing, RegistryKey<LootTable> lootTable) {
        world.setBlockState(pos, Blocks.CHEST.getDefaultState()
            .with(net.minecraft.block.HorizontalFacingBlock.FACING, facing));
        if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(lootTable, world.getRandom().nextLong());
        }
    }

    /**
     * Create a spiral staircase.
     */
    public static void createSpiralStairs(ServerWorld world, BlockPos center, int radius, int height, BlockState stairBlock, BlockState pillarBlock) {
        // Central pillar
        for (int y = 0; y < height; y++) {
            world.setBlockState(center.up(y), pillarBlock);
        }

        // Spiral stairs
        double stepsPerRotation = 8;
        for (int step = 0; step < height * stepsPerRotation / 4; step++) {
            double angle = (step / stepsPerRotation) * 2 * Math.PI;
            int x = (int) Math.round((radius - 1) * Math.cos(angle));
            int z = (int) Math.round((radius - 1) * Math.sin(angle));
            int y = (int) (step * 4 / stepsPerRotation);

            if (y < height) {
                world.setBlockState(center.add(x, y, z), stairBlock);
            }
        }
    }

    /**
     * Place torches/lanterns at intervals along walls.
     */
    public static void placeLightsAlongWall(ServerWorld world, BlockPos start, BlockPos end, int interval, int heightOffset, BlockState lightBlock) {
        int dx = Integer.signum(end.getX() - start.getX());
        int dz = Integer.signum(end.getZ() - start.getZ());
        int length = Math.max(Math.abs(end.getX() - start.getX()), Math.abs(end.getZ() - start.getZ()));

        for (int i = 0; i <= length; i += interval) {
            BlockPos pos = start.add(dx * i, heightOffset, dz * i);
            world.setBlockState(pos, lightBlock);
        }
    }

    /**
     * Generate a random position within bounds.
     */
    public static BlockPos randomPosInBounds(Random random, BlockPos min, BlockPos max) {
        int x = min.getX() + random.nextInt(max.getX() - min.getX() + 1);
        int y = min.getY() + random.nextInt(max.getY() - min.getY() + 1);
        int z = min.getZ() + random.nextInt(max.getZ() - min.getZ() + 1);
        return new BlockPos(x, y, z);
    }
}
