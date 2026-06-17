package com.villagecastles.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Utility class for procedurally building castle structures.
 */
public class StructureHelper {

    public static final int SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * Fill a 3D box with a block state.
     */
    public static void fillBox(ServerLevel world, BlockPos corner1, BlockPos corner2, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlock(mutable.set(x, y, z), state, SET_FLAGS);
                }
            }
        }
    }

    /**
     * Create a floor at a specific Y level within bounds.
     */
    public static void fillFloor(ServerLevel world, BlockPos corner1, BlockPos corner2, int y, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.setBlock(mutable.set(x, y, z), state, SET_FLAGS);
            }
        }
    }

    /**
     * Clear the interior of a box (replace with air).
     */
    public static void clearInterior(ServerLevel world, BlockPos corner1, BlockPos corner2) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minY = Math.min(corner1.getY(), corner2.getY());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxY = Math.max(corner1.getY(), corner2.getY());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    world.setBlock(mutable.set(x, y, z), air, SET_FLAGS);
                }
            }
        }
    }

    /**
     * Build a cylindrical tower.
     */
    public static void buildCylinder(ServerLevel world, BlockPos center, int radius, int height, BlockState state, boolean hollow) {
        int radiusSq = radius * radius;
        double innerSq = (radius - 1.5) * (radius - 1.5);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x * x + z * z;
                    boolean inCircle = distSq <= radiusSq;
                    boolean onEdge = distSq > innerSq;

                    mutable.set(cx + x, cy + y, cz + z);
                    if (inCircle && (!hollow || onEdge)) {
                        world.setBlock(mutable, state, SET_FLAGS);
                    } else if (hollow && inCircle) {
                        world.setBlock(mutable, Blocks.AIR.defaultBlockState(), SET_FLAGS);
                    }
                }
            }
        }
    }

    /**
     * Add crenellations (battlements) on top of walls.
     */
    public static void addCrenellations(ServerLevel world, BlockPos corner1, BlockPos corner2, int topY, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());

        // North and South walls
        for (int x = minX; x <= maxX; x++) {
            if ((x - minX) % 2 == 0) {
                world.setBlock(new BlockPos(x, topY, minZ), state, SET_FLAGS);
                world.setBlock(new BlockPos(x, topY, maxZ), state, SET_FLAGS);
            }
        }

        // East and West walls
        for (int z = minZ; z <= maxZ; z++) {
            if ((z - minZ) % 2 == 0) {
                world.setBlock(new BlockPos(minX, topY, z), state, SET_FLAGS);
                world.setBlock(new BlockPos(maxX, topY, z), state, SET_FLAGS);
            }
        }
    }

    /**
     * Add crenellations to a circular tower top.
     */
    public static void addCircularCrenellations(ServerLevel world, BlockPos center, int radius, int topY, BlockState state) {
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int x = (int) Math.round(radius * Math.cos(rad));
            int z = (int) Math.round(radius * Math.sin(rad));
            world.setBlock(center.offset(x, topY, z), state, SET_FLAGS);
        }
    }

    /**
     * Place a chest with loot table.
     */
    public static void placeChest(ServerLevel world, BlockPos pos, Direction facing, ResourceKey<LootTable> lootTable) {
        world.setBlock(pos, Blocks.CHEST.defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, facing), SET_FLAGS);
        if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(lootTable, world.getRandom().nextLong());
        }
    }

    /**
     * Force-load chunks in the generation area.
     * Caller is responsible for calling unforceChunks when done.
     */
    public static void forceLoadChunks(ServerLevel world, BlockPos center, int radius) {
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
    public static void unforceChunks(ServerLevel world, BlockPos center, int radius) {
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
    public static void withForcedChunks(ServerLevel world, BlockPos center, int radius, Runnable action) {
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
    public static void updateConnectionStates(ServerLevel world, BlockPos min, BlockPos max) {
        int minX = Math.min(min.getX(), max.getX());
        int minY = Math.min(min.getY(), max.getY());
        int minZ = Math.min(min.getZ(), max.getZ());
        int maxX = Math.max(min.getX(), max.getX());
        int maxY = Math.max(min.getY(), max.getY());
        int maxZ = Math.max(min.getZ(), max.getZ());

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir()) continue;

                    BlockState updatedState = state;
                    for (Direction direction : Direction.values()) {
                        neighborPos.setWithOffset(pos, direction);
                        BlockState neighborState = world.getBlockState(neighborPos);
                        updatedState = updatedState.updateShape(
                            world, world, pos, direction, neighborPos, neighborState, world.getRandom()
                        );
                    }

                    if (updatedState != state) {
                        // Use SKIP_DROPS (64) to prevent items from spawning when
                        // unsupported blocks (torches, carpets) are removed by neighbor updates
                        world.setBlock(pos, updatedState, SET_FLAGS | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
    }

    /**
     * Create a spiral staircase.
     */
    public static void createSpiralStairs(ServerLevel world, BlockPos center, int radius, int height, BlockState stairBlock, BlockState pillarBlock) {
        // Central pillar
        for (int y = 0; y < height; y++) {
            world.setBlock(center.above(y), pillarBlock, SET_FLAGS);
        }

        // Spiral stairs — one step per block of height, rotating 45° per step.
        // 8 cardinal+diagonal positions per rotation; steps at y=0,1,2... are climbable.
        double stepsPerRotation = 8;
        for (int step = 0; step < height; step++) {
            double angle = (step / stepsPerRotation) * 2 * Math.PI;
            int x = (int) Math.round((radius - 1) * Math.cos(angle));
            int z = (int) Math.round((radius - 1) * Math.sin(angle));
            world.setBlock(center.offset(x, step, z), stairBlock, SET_FLAGS);
        }
    }
}
