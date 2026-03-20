package com.villagecastles.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Utility class for procedurally building castle structures.
 */
public class StructureHelper {

    public static final int SET_FLAGS = net.minecraft.block.Block.NOTIFY_LISTENERS | net.minecraft.block.Block.FORCE_STATE; // Flags 2|16: notify listeners + force state placement

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
        int radiusSq = radius * radius;
        double innerSq = (radius - 1.5) * (radius - 1.5);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x * x + z * z;
                    boolean inCircle = distSq <= radiusSq;
                    boolean onEdge = distSq > innerSq;

                    mutable.set(cx + x, cy + y, cz + z);
                    if (inCircle && (!hollow || onEdge)) {
                        world.setBlockState(mutable, state, SET_FLAGS);
                    } else if (hollow && inCircle) {
                        world.setBlockState(mutable, Blocks.AIR.getDefaultState(), SET_FLAGS);
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
                world.setBlockState(new BlockPos(x, topY, minZ), state, SET_FLAGS);
                world.setBlockState(new BlockPos(x, topY, maxZ), state, SET_FLAGS);
            }
        }

        // East and West walls
        for (int z = minZ; z <= maxZ; z++) {
            if ((z - minZ) % 2 == 0) {
                world.setBlockState(new BlockPos(minX, topY, z), state, SET_FLAGS);
                world.setBlockState(new BlockPos(maxX, topY, z), state, SET_FLAGS);
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
            world.setBlockState(center.add(x, topY, z), state, SET_FLAGS);
        }
    }

    /**
     * Place a chest with loot table.
     */
    public static void placeChest(ServerWorld world, BlockPos pos, Direction facing, RegistryKey<LootTable> lootTable) {
        world.setBlockState(pos, Blocks.CHEST.getDefaultState()
            .with(net.minecraft.block.HorizontalFacingBlock.FACING, facing), SET_FLAGS);
        if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(lootTable, world.getRandom().nextLong());
        }
    }

    /**
     * Force-load chunks in the generation area.
     * Caller is responsible for calling unforcedChunks when done.
     */
    public static void forceLoadChunks(ServerWorld world, BlockPos center, int radius) {
        int chunkRadius = (radius >> 4) + 1;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                world.setChunkForced(x, z, true);
            }
        }
    }

    /**
     * Unforce chunks that were previously force-loaded.
     */
    public static void unforceChunks(ServerWorld world, BlockPos center, int radius) {
        int chunkRadius = (radius >> 4) + 1;
        int cx = center.getX() >> 4;
        int cz = center.getZ() >> 4;
        for (int x = cx - chunkRadius; x <= cx + chunkRadius; x++) {
            for (int z = cz - chunkRadius; z <= cz + chunkRadius; z++) {
                world.setChunkForced(x, z, false);
            }
        }
    }

    /**
     * Force-load chunks in the generation area, run the action, then unforce them.
     * Uses setChunkForced to prevent chunks from unloading during large structure generation.
     */
    public static void withForcedChunks(ServerWorld world, BlockPos center, int radius, Runnable action) {
        forceLoadChunks(world, center, radius);
        try {
            action.run();
        } finally {
            unforceChunks(world, center, radius);
        }
    }

    /**
     * Check if an NBT structure file exists in the mod's resources.
     * Works from any classloader context.
     */
    public static boolean structureNbtExists(String structurePath) {
        String resourcePath = "/data/villagecastles/structure/" + structurePath + ".nbt";
        try (java.io.InputStream is = StructureHelper.class.getResourceAsStream(resourcePath)) {
            return is != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Post-generation pass: recalculate connection states for fences, walls, iron bars,
     * glass panes, and other blocks that derive their visual state from neighbors.
     *
     * Must be called after all blocks in the region have been placed.
     */
    public static void updateConnectionStates(ServerWorld world, BlockPos min, BlockPos max) {
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());

        BlockPos.Mutable pos = new BlockPos.Mutable();
        BlockPos.Mutable neighborPos = new BlockPos.Mutable();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir()) continue;

                    BlockState updatedState = state;
                    for (Direction direction : Direction.values()) {
                        neighborPos.set(pos, direction);
                        BlockState neighborState = world.getBlockState(neighborPos);
                        updatedState = updatedState.getStateForNeighborUpdate(
                            world, world, pos, direction, neighborPos, neighborState, world.getRandom()
                        );
                    }

                    if (updatedState != state) {
                        // Use SKIP_DROPS (64) to prevent items from spawning when
                        // unsupported blocks (torches, carpets) are removed by neighbor updates
                        world.setBlockState(pos, updatedState, SET_FLAGS | net.minecraft.block.Block.SKIP_DROPS);
                    }
                }
            }
        }
    }

    /**
     * Create a spiral staircase.
     */
    public static void createSpiralStairs(ServerWorld world, BlockPos center, int radius, int height, BlockState stairBlock, BlockState pillarBlock) {
        // Central pillar
        for (int y = 0; y < height; y++) {
            world.setBlockState(center.up(y), pillarBlock, SET_FLAGS);
        }

        // Spiral stairs
        double stepsPerRotation = 8;
        for (int step = 0; step < height * stepsPerRotation / 4; step++) {
            double angle = (step / stepsPerRotation) * 2 * Math.PI;
            int x = (int) Math.round((radius - 1) * Math.cos(angle));
            int z = (int) Math.round((radius - 1) * Math.sin(angle));
            int y = (int) (step * 4 / stepsPerRotation);

            if (y < height) {
                world.setBlockState(center.add(x, y, z), stairBlock, SET_FLAGS);
            }
        }
    }
}
