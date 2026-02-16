package com.villagecastles.generator;

import com.villagecastles.VillageCastles;
import com.villagecastles.util.StructureHelper;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Main orchestrator for castle generation.
 * Assembles keep, walls, towers, and gates into a complete castle.
 *
 * Castle Layout (Large):
 *
 *       [T]----[Wall]----[T]
 *        |                |
 *      [Wall]   [Keep]  [Wall]
 *        |                |
 *       [T]----[Gate]----[T]
 *
 * T = Tower, [Keep] = Central building
 */
public class CastleGenerator {

    public enum CastleSize {
        SMALL(30, 0),   // ~30 blocks diameter, small keep
        MEDIUM(50, 1),  // ~50 blocks diameter, medium keep
        LARGE(70, 2);   // ~70 blocks diameter, large keep

        public final int diameter;
        public final int keepSize;

        CastleSize(int diameter, int keepSize) {
            this.diameter = diameter;
            this.keepSize = keepSize;
        }
    }

    private final BiomePalette palette;
    private final Random random;
    private final CastleSize size;

    // Sub-generators
    private final KeepGenerator keepGenerator;
    private final TowerGenerator towerGenerator;
    private final WallGenerator wallGenerator;
    private final GateGenerator gateGenerator;

    public CastleGenerator(BiomePalette palette, long seed, CastleSize size) {
        this.palette = palette;
        this.random = new Random(seed);
        this.size = size;

        this.keepGenerator = new KeepGenerator(palette, random, size.keepSize);
        this.towerGenerator = new TowerGenerator(palette, random);
        this.wallGenerator = new WallGenerator(palette, random);
        this.gateGenerator = new GateGenerator(palette, random);
    }

    /**
     * Generate a complete castle at the specified position.
     * @param world The server world
     * @param center The center position of the castle courtyard
     * @return CastleBounds containing the generated structure's bounds
     */
    public CastleBounds generate(ServerWorld world, BlockPos center) {
        VillageCastles.LOGGER.info("Generating {} {} castle at {}",
            size.name().toLowerCase(), palette.displayName, center.toShortString());

        int radius = size.diameter / 2;

        // Prepare the ground - flatten and create foundation
        prepareGround(world, center, radius);

        // Calculate key positions
        BlockPos keepPos = center;
        BlockPos northGatePos = center.north(radius - GateGenerator.getDepth() / 2);
        BlockPos southGatePos = center.south(radius - GateGenerator.getDepth() / 2);

        // Corner tower positions
        BlockPos nwTower = center.add(-radius + 5, 0, -radius + 5);
        BlockPos neTower = center.add(radius - 5, 0, -radius + 5);
        BlockPos swTower = center.add(-radius + 5, 0, radius - 5);
        BlockPos seTower = center.add(radius - 5, 0, radius - 5);

        // === Generate in order of importance ===

        // 1. Generate the central keep
        VillageCastles.LOGGER.debug("Generating keep...");
        int keepHeight = keepGenerator.generate(world, keepPos);

        // 2. Generate corner towers
        VillageCastles.LOGGER.debug("Generating corner towers...");
        towerGenerator.generate(world, nwTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, neTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, swTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, seTower, TowerGenerator.TowerType.CORNER);

        // 3. Generate main gatehouse (south facing outward)
        VillageCastles.LOGGER.debug("Generating main gatehouse...");
        gateGenerator.generate(world, southGatePos, Direction.SOUTH);

        // 4. Generate walls connecting towers to gatehouse
        VillageCastles.LOGGER.debug("Generating walls...");
        generateWalls(world, nwTower, neTower, swTower, seTower, northGatePos, southGatePos, radius);

        // 5. Generate courtyard features
        VillageCastles.LOGGER.debug("Generating courtyard...");
        generateCourtyard(world, center, radius);

        // 6. Add secondary features based on size
        if (size == CastleSize.LARGE || size == CastleSize.MEDIUM) {
            // Add wall towers
            addWallTowers(world, center, radius);
        }

        if (size == CastleSize.LARGE) {
            // Add back gate
            VillageCastles.LOGGER.debug("Generating back gate...");
            BlockPos backGatePos = center.north(radius - GateGenerator.getDepth() / 2);
            gateGenerator.generate(world, backGatePos, Direction.NORTH);
        }

        VillageCastles.LOGGER.info("Castle generation complete!");

        return new CastleBounds(
            center.add(-radius, -3, -radius),
            center.add(radius, keepHeight + 10, radius)
        );
    }

    /**
     * Prepare the ground by flattening terrain and creating a foundation.
     */
    private static final int SET_FLAGS = net.minecraft.block.Block.NOTIFY_LISTENERS; // Flag 2: no neighbor updates

    private void prepareGround(ServerWorld world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        net.minecraft.block.BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        net.minecraft.block.BlockState floorState = palette.getFloorState();

        for (int x = -radius - 2; x <= radius + 2; x++) {
            for (int z = -radius - 2; z <= radius + 2; z++) {
                int wx = ox + x;
                int wz = oz + z;

                // Fill foundation from -5 to -1 below surface
                for (int y = -5; y <= -1; y++) {
                    mutable.set(wx, baseY + y, wz);
                    world.setBlockState(mutable, cobble, SET_FLAGS);
                }

                // Ground level floor
                mutable.set(wx, baseY, wz);
                boolean isInner = Math.abs(x) < radius - 8 && Math.abs(z) < radius - 8;
                world.setBlockState(mutable, isInner ? floorState : cobble, SET_FLAGS);

                // Clear air above ground level - skip blocks already air
                for (int y = 1; y <= 40; y++) {
                    mutable.set(wx, baseY + y, wz);
                    if (!world.getBlockState(mutable).isAir()) {
                        world.setBlockState(mutable, air, SET_FLAGS);
                    }
                }
            }
        }

        VillageCastles.LOGGER.debug("Ground prepared: {}x{} area flattened", (radius + 2) * 2, (radius + 2) * 2);
    }

    private void generateWalls(ServerWorld world, BlockPos nwTower, BlockPos neTower,
                               BlockPos swTower, BlockPos seTower,
                               BlockPos northGatePos, BlockPos southGatePos, int radius) {

        int towerRadius = TowerGenerator.TowerType.CORNER.radius;
        int gateHalfWidth = GateGenerator.getFullWidth() / 2;

        // North wall (NW tower to NE tower) - or to back gate if large
        if (size == CastleSize.LARGE) {
            // Wall from NW tower to north gate
            wallGenerator.generateWithLighting(world,
                nwTower.east(towerRadius),
                northGatePos.west(gateHalfWidth),
                8);

            // Wall from north gate to NE tower
            wallGenerator.generateWithLighting(world,
                northGatePos.east(gateHalfWidth),
                neTower.west(towerRadius),
                8);
        } else {
            // Full north wall
            wallGenerator.generateWithLighting(world,
                nwTower.east(towerRadius),
                neTower.west(towerRadius),
                8);
        }

        // South wall (SW tower to main gate to SE tower)
        wallGenerator.generateWithLighting(world,
            swTower.east(towerRadius),
            southGatePos.west(gateHalfWidth),
            8);

        wallGenerator.generateWithLighting(world,
            southGatePos.east(gateHalfWidth),
            seTower.west(towerRadius),
            8);

        // East wall (NE tower to SE tower)
        wallGenerator.generateWithLighting(world,
            neTower.south(towerRadius),
            seTower.north(towerRadius),
            8);

        // West wall (NW tower to SW tower)
        wallGenerator.generateWithLighting(world,
            nwTower.south(towerRadius),
            swTower.north(towerRadius),
            8);
    }

    private void addWallTowers(ServerWorld world, BlockPos center, int radius) {
        // Add a tower at the midpoint of each wall
        int towerY = center.getY();

        // Midpoint of east wall
        BlockPos eastMid = center.add(radius - 5, 0, 0);
        towerGenerator.generate(world, eastMid, TowerGenerator.TowerType.WALL);

        // Midpoint of west wall
        BlockPos westMid = center.add(-radius + 5, 0, 0);
        towerGenerator.generate(world, westMid, TowerGenerator.TowerType.WALL);

        if (size == CastleSize.LARGE) {
            // Extra towers on long walls
            BlockPos eastNorth = center.add(radius - 5, 0, -radius / 3);
            BlockPos eastSouth = center.add(radius - 5, 0, radius / 3);
            towerGenerator.generate(world, eastNorth, TowerGenerator.TowerType.WATCH);
            towerGenerator.generate(world, eastSouth, TowerGenerator.TowerType.WATCH);

            BlockPos westNorth = center.add(-radius + 5, 0, -radius / 3);
            BlockPos westSouth = center.add(-radius + 5, 0, radius / 3);
            towerGenerator.generate(world, westNorth, TowerGenerator.TowerType.WATCH);
            towerGenerator.generate(world, westSouth, TowerGenerator.TowerType.WATCH);
        }
    }

    private void generateCourtyard(ServerWorld world, BlockPos center, int radius) {
        int keepHalfWidth = keepGenerator.getWidth() / 2;
        int keepHalfDepth = keepGenerator.getDepth() / 2;

        // Courtyard is the area between keep and walls
        int courtyardInner = Math.max(keepHalfWidth, keepHalfDepth) + 3;
        int courtyardOuter = radius - 10;

        // Path from main gate to keep
        generatePath(world, center.south(keepHalfDepth + 1), center.south(courtyardOuter - 2));

        // Side features based on size
        if (size != CastleSize.SMALL) {
            // Well in courtyard (west side)
            generateWell(world, center.add(-courtyardInner - 5, 0, 5));

            // Training dummy area (east side)
            generateTrainingArea(world, center.add(courtyardInner + 5, 0, 5));
        }

        if (size == CastleSize.LARGE) {
            // Stables (near gate)
            generateStables(world, center.add(-courtyardInner - 8, 0, courtyardOuter - 10), Direction.EAST);

            // Barracks (opposite side)
            generateBarracks(world, center.add(courtyardInner + 8, 0, courtyardOuter - 10), Direction.WEST);
        }
    }

    private void generatePath(ServerWorld world, BlockPos start, BlockPos end) {
        int dz = end.getZ() - start.getZ();
        int length = Math.abs(dz);
        int direction = Integer.signum(dz);

        for (int i = 0; i <= length; i++) {
            BlockPos pos = start.add(0, 0, i * direction);
            // 3-wide path
            world.setBlockState(pos.west(1), palette.getFloorState());
            world.setBlockState(pos, palette.getFloorState());
            world.setBlockState(pos.east(1), palette.getFloorState());
        }
    }

    private void generateWell(ServerWorld world, BlockPos center) {
        // Simple well structure
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(center.add(x, 0, z), palette.getPrimaryWallState());
                world.setBlockState(center.add(x, 1, z), palette.getWallState());
            }
        }
        // Water in center
        world.setBlockState(center, net.minecraft.block.Blocks.WATER.getDefaultState());
        world.setBlockState(center.down(1), net.minecraft.block.Blocks.WATER.getDefaultState());

        // Roof posts
        world.setBlockState(center.add(-1, 2, -1), palette.getFenceState());
        world.setBlockState(center.add(1, 2, -1), palette.getFenceState());
        world.setBlockState(center.add(-1, 2, 1), palette.getFenceState());
        world.setBlockState(center.add(1, 2, 1), palette.getFenceState());

        // Roof
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(center.add(x, 3, z), palette.getPlanksState());
            }
        }
    }

    private void generateTrainingArea(ServerWorld world, BlockPos center) {
        // Training dummies (fence posts with pumpkin heads)
        for (int i = -2; i <= 2; i += 2) {
            BlockPos dummyPos = center.add(i, 0, 0);
            world.setBlockState(dummyPos, palette.getFenceState());
            world.setBlockState(dummyPos.up(1), palette.getFenceState());
            world.setBlockState(dummyPos.up(2), net.minecraft.block.Blocks.CARVED_PUMPKIN.getDefaultState());
        }

        // Target (hay bale)
        world.setBlockState(center.add(0, 0, 3), net.minecraft.block.Blocks.HAY_BLOCK.getDefaultState());
        world.setBlockState(center.add(0, 1, 3), net.minecraft.block.Blocks.HAY_BLOCK.getDefaultState());
    }

    private void generateStables(ServerWorld world, BlockPos corner, Direction facing) {
        int width = 8;
        int depth = 5;
        int height = 4;

        // Simple barn structure
        BlockPos corner2 = corner.add(
            facing == Direction.EAST || facing == Direction.WEST ? depth : width,
            height,
            facing == Direction.NORTH || facing == Direction.SOUTH ? depth : width
        );

        // Walls
        for (int x = 0; x <= width; x++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(x, y, 0), palette.getLogState());
                world.setBlockState(corner.add(x, y, depth), palette.getLogState());
            }
        }
        for (int z = 0; z <= depth; z++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(0, y, z), palette.getLogState());
                world.setBlockState(corner.add(width, y, z), palette.getLogState());
            }
        }

        // Fill interior with air
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                for (int y = 1; y < height; y++) {
                    world.setBlockState(corner.add(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState());
                }
            }
        }

        // Floor
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                world.setBlockState(corner.add(x, 0, z), palette.getPlanksState());
            }
        }

        // Roof
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                world.setBlockState(corner.add(x, height, z), palette.getPlanksState());
            }
        }

        // Hay bales
        world.setBlockState(corner.add(2, 1, 2), net.minecraft.block.Blocks.HAY_BLOCK.getDefaultState());
        world.setBlockState(corner.add(2, 2, 2), net.minecraft.block.Blocks.HAY_BLOCK.getDefaultState());
    }

    private void generateBarracks(ServerWorld world, BlockPos corner, Direction facing) {
        int width = 10;
        int depth = 6;
        int height = 5;

        // Similar to stables but with beds
        // Walls
        for (int x = 0; x <= width; x++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(x, y, 0), palette.getPrimaryWallState());
                world.setBlockState(corner.add(x, y, depth), palette.getPrimaryWallState());
            }
        }
        for (int z = 0; z <= depth; z++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(0, y, z), palette.getPrimaryWallState());
                world.setBlockState(corner.add(width, y, z), palette.getPrimaryWallState());
            }
        }

        // Clear interior
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                for (int y = 1; y < height; y++) {
                    world.setBlockState(corner.add(x, y, z), net.minecraft.block.Blocks.AIR.getDefaultState());
                }
            }
        }

        // Floor
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                world.setBlockState(corner.add(x, 0, z), palette.getFloorState());
            }
        }

        // Roof
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                world.setBlockState(corner.add(x, height, z), palette.getRoofState());
            }
        }

        // Beds (wool blocks as placeholder)
        for (int i = 2; i < width - 1; i += 3) {
            world.setBlockState(corner.add(i, 1, 2), net.minecraft.block.Blocks.RED_WOOL.getDefaultState());
            world.setBlockState(corner.add(i, 1, 3), net.minecraft.block.Blocks.RED_WOOL.getDefaultState());
        }

        // Door
        world.setBlockState(corner.add(width / 2, 1, 0), net.minecraft.block.Blocks.AIR.getDefaultState());
        world.setBlockState(corner.add(width / 2, 2, 0), net.minecraft.block.Blocks.AIR.getDefaultState());
    }

    /**
     * Represents the bounding box of a generated castle.
     */
    public static class CastleBounds {
        public final BlockPos min;
        public final BlockPos max;

        public CastleBounds(BlockPos min, BlockPos max) {
            this.min = min;
            this.max = max;
        }

        public int getWidth() {
            return max.getX() - min.getX();
        }

        public int getHeight() {
            return max.getY() - min.getY();
        }

        public int getDepth() {
            return max.getZ() - min.getZ();
        }
    }
}
