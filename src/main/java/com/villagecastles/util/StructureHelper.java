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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class for procedurally building castle structures.
 */
public class StructureHelper {

    /**
     * Flags for every block a generator places.
     *
     * <p>{@code UPDATE_SUPPRESS_DROPS} is not optional here. Generators stamp over their own work
     * constantly, and removing a block that holds items makes the world spit those items out as
     * entities: overwriting a loot chest resolves its table on the way out, so the roll lands on
     * the floor and gets baked straight into the exported template. That is where the shipped
     * castles' saddles, horse armour and iron gear came from. A generator has no business dropping
     * anything, so the primitive refuses to.
     */
    public static final int SET_FLAGS =
        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

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
     * Every offset on the outline of a circle of this radius, walked in angular order.
     *
     * <p>Membership is a distance test, not an angular sweep: a sweep at a fixed angular step
     * either duplicates blocks (small radius) or leaves holes in the outline (large radius),
     * and truncating its cosine biases the whole ring one block toward the origin. Testing
     * every cell in the bounding square against {@code (r-1)^2 < d^2 <= r^2} yields a closed,
     * symmetric, one-block-thick ring at any radius. The angular sort exists so callers can
     * walk the ring in order (alternating merlons, spiral steps) without re-deriving it.
     */
    public static List<int[]> ringOffsets(int radius) {
        if (radius < 1) return List.of(new int[]{0, 0});
        int outerSq = radius * radius;
        int innerSq = (radius - 1) * (radius - 1);
        List<int[]> ring = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d = x * x + z * z;
                if (d <= outerSq && d > innerSq) ring.add(new int[]{x, z});
            }
        }
        ring.sort(Comparator.comparingDouble(o -> Math.atan2(o[1], o[0])));
        return ring;
    }

    /**
     * A band of the given thickness inside a circle of this radius: every offset with
     * {@code (radius - thickness)^2 < d^2 <= radius^2}.
     *
     * <p>Use this, not {@link #ringOffsets}, whenever successive courses shrink: a cone, a dome, a
     * spire. A one-block ring is only 8-connected, so at its four cardinal tips a cell's orthogonal
     * neighbours are all outside the ring. In a cylinder that does not matter, because the identical
     * ring repeats above and below and holds every cell in place. In anything tapering, the course
     * above is a different ring and the tips are left touching nothing: stacking one-block rings for
     * the savanna thatch left 44 hay blocks hanging in mid-air over a single hut. A band of two
     * overlaps its neighbouring courses and stays connected in every direction.
     */
    public static List<int[]> bandOffsets(int radius, int thickness) {
        int outerSq = radius * radius;
        int inner = Math.max(0, radius - thickness);
        int innerSq = inner * inner;
        List<int[]> band = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int d = x * x + z * z;
                if (d <= outerSq && d > innerSq) band.add(new int[]{x, z});
            }
        }
        return band;
    }

    /** Every offset inside a circle of this radius, outline included. */
    public static List<int[]> discOffsets(int radius) {
        int outerSq = radius * radius;
        List<int[]> disc = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= outerSq) disc.add(new int[]{x, z});
            }
        }
        return disc;
    }

    /**
     * Build a solid (filled) cylindrical column.
     */
    public static void buildSolidCylinder(ServerLevel world, BlockPos center, int radius, int height, BlockState state) {
        List<int[]> disc = discOffsets(radius);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            for (int[] o : disc) {
                world.setBlock(mutable.set(center.getX() + o[0], center.getY() + y, center.getZ() + o[1]), state, SET_FLAGS);
            }
        }
    }

    /**
     * Build a hollow cylindrical tower: a one-block-thick shell with the interior cleared.
     */
    public static void buildHollowCylinder(ServerLevel world, BlockPos center, int radius, int height, BlockState state) {
        List<int[]> ring = ringOffsets(radius);
        List<int[]> disc = discOffsets(radius - 1);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = 0; y < height; y++) {
            for (int[] o : disc) {
                world.setBlock(mutable.set(center.getX() + o[0], center.getY() + y, center.getZ() + o[1]), air, SET_FLAGS);
            }
            for (int[] o : ring) {
                world.setBlock(mutable.set(center.getX() + o[0], center.getY() + y, center.getZ() + o[1]), state, SET_FLAGS);
            }
        }
    }

    /**
     * Crenellate the top of a rectangular wall.
     *
     * @param wallTopY Y of the wall's topmost course. Merlons land on {@code wallTopY + 1}, so
     *                 the caller passes the wall, not the gap above it: an earlier signature took
     *                 the merlon height directly and every square-tower caller was two blocks out,
     *                 leaving 80 merlons floating over open air in plains/castle_large alone.
     */
    public static void addCrenellations(ServerLevel world, BlockPos corner1, BlockPos corner2, int wallTopY, BlockState state) {
        int minX = Math.min(corner1.getX(), corner2.getX());
        int minZ = Math.min(corner1.getZ(), corner2.getZ());
        int maxX = Math.max(corner1.getX(), corner2.getX());
        int maxZ = Math.max(corner1.getZ(), corner2.getZ());
        int y = wallTopY + 1;

        // Alternate inward from both ends of each run, so every corner carries a merlon and an
        // even-length run puts its one doubled pair at the midpoint rather than at a corner.
        for (int x = minX; x <= maxX; x++) {
            if (Math.min(x - minX, maxX - x) % 2 != 0) continue;
            world.setBlock(new BlockPos(x, y, minZ), state, SET_FLAGS);
            world.setBlock(new BlockPos(x, y, maxZ), state, SET_FLAGS);
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (Math.min(z - minZ, maxZ - z) % 2 != 0) continue;
            world.setBlock(new BlockPos(minX, y, z), state, SET_FLAGS);
            world.setBlock(new BlockPos(maxX, y, z), state, SET_FLAGS);
        }
    }

    /**
     * Crenellate a circular tower top.
     *
     * @param wallTopY Y of the tower wall's topmost course; merlons land one above it.
     */
    public static void addCircularCrenellations(ServerLevel world, BlockPos center, int radius, int wallTopY, BlockState state) {
        List<int[]> ring = ringOffsets(radius);
        for (int i = 0; i < ring.size(); i++) {
            if (i % 2 != 0) continue;
            int[] o = ring.get(i);
            world.setBlock(new BlockPos(center.getX() + o[0], wallTopY + 1, center.getZ() + o[1]), state, SET_FLAGS);
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
        String resourcePath = "/data/village-castles/structure/" + structurePath + ".nbt";
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

    /** The horizontal direction of a one-block step, defaulting to NORTH for a zero delta. */
    private static Direction horizontalStep(int dx, int dz) {
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        if (dz > 0) return Direction.SOUTH;
        if (dz < 0) return Direction.NORTH;
        return Direction.NORTH;
    }

    /**
     * The perimeter of a square ring, walked so that consecutive entries are orthogonally
     * adjacent. That adjacency is the whole point: it is what makes a step per cell climbable.
     */
    public static List<int[]> squareRingOffsets(int radius) {
        List<int[]> ring = new ArrayList<>();
        for (int x = -radius; x < radius; x++) ring.add(new int[]{x, -radius});
        for (int z = -radius; z < radius; z++) ring.add(new int[]{radius, z});
        for (int x = radius; x > -radius; x--) ring.add(new int[]{x, radius});
        for (int z = radius; z > -radius; z--) ring.add(new int[]{-radius, z});
        return ring;
    }

    /**
     * A climbable spiral staircase winding around a central pillar.
     *
     * <p>One step per cell of a square ring, one block of rise per step, each stair oriented to
     * the direction of travel and given two blocks of cleared headroom. The ring is square rather
     * than circular so consecutive steps are orthogonal neighbours: the previous implementation
     * sampled a circle every 45 degrees, which produced diagonal hops, duplicate cells at small
     * radii, and stairs left at their default facing.
     *
     * @param radius ring radius around the pillar; clamped to at least 1.
     */
    public static void createSpiralStairs(ServerLevel world, BlockPos center, int radius, int height, BlockState stairBlock, BlockState pillarBlock) {
        List<int[]> ring = squareRingOffsets(Math.max(1, radius));
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int y = 0; y < height; y++) {
            world.setBlock(center.above(y), pillarBlock, SET_FLAGS);
        }

        for (int step = 0; step < height; step++) {
            int[] here = ring.get(step % ring.size());
            int[] next = ring.get((step + 1) % ring.size());
            Direction travel = horizontalStep(next[0] - here[0], next[1] - here[1]);

            BlockPos stepPos = center.offset(here[0], step, here[1]);
            world.setBlock(stepPos, stairBlock.setValue(HorizontalDirectionalBlock.FACING, travel), SET_FLAGS);
            for (int clear = 1; clear <= 2; clear++) {
                world.setBlock(stepPos.above(clear), air, SET_FLAGS);
            }
        }
    }
}
