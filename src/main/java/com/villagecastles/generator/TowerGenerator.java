package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Random;

/**
 * Generates defensive towers for castle walls.
 * Towers can be:
 * - Corner towers (larger, cylindrical)
 * - Wall towers (medium, square)
 * - Watch towers (small, for walls)
 *
 * Tower tops vary by biome:
 * - PLAINS: Classic conical roof
 * - DESERT: Flat top with parapet lip and small dome
 * - TAIGA: Tall steep spire with finial
 * - SNOWY: Steep cone with snow accumulation
 * - SAVANNA: Low flat cap with terracotta edge
 */
public class TowerGenerator {

    public enum TowerType {
        CORNER(5, 20, true),      // Large cylindrical corner towers
        WALL(4, 16, false),       // Medium square wall towers
        WATCH(3, 12, false),      // Small square watch towers
        GATEHOUSE(3, 14, true);   // Cylindrical flanking towers for gatehouses

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
     * Tower dimensions are adjusted by biome for architectural variety.
     */
    public void generate(ServerLevel world, BlockPos center, TowerType type) {
        if (type.cylindrical) {
            generateCylindricalTower(world, center, type);
        } else {
            generateSquareTower(world, center, type);
        }
    }

    /**
     * Get biome-adjusted tower height.
     * Taiga/Snowy towers are taller; Desert towers are shorter and wider at base.
     */
    private int adjustedHeight(TowerType type) {
        return switch (palette) {
            case TAIGA -> type.height + 3;
            case SNOWY -> type.height + 2;
            case DESERT -> type.height - 2;
            default -> type.height;
        };
    }

    /**
     * Get the biome-adjusted radius for a tower type. Public for wall/tower positioning.
     */
    public int getAdjustedRadius(TowerType type) {
        return adjustedRadius(type);
    }

    private int adjustedRadius(TowerType type) {
        return switch (palette) {
            case DESERT -> type.radius + 1;  // Wider base for desert towers
            case SAVANNA -> type.radius + 1;
            default -> type.radius;
        };
    }

    private void generateCylindricalTower(ServerLevel world, BlockPos center, TowerType type) {
        int radius = adjustedRadius(type);
        int height = adjustedHeight(type);
        int radiusSq = radius * radius;
        double innerSq = (radius - 1.5) * (radius - 1.5);
        int floorThreshSq = (radius - 1) * (radius - 1);

        // Foundation
        StructureHelper.buildCylinder(world, center.below(2), radius + 1, 3, Blocks.COBBLESTONE.defaultBlockState(), false);

        // Main tower walls
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int cx = center.getX(), cy = center.getY(), cz = center.getZ();

        for (int y = 0; y < height; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x * x + z * z;
                    boolean onEdge = distSq <= radiusSq && distSq > innerSq;
                    boolean inside = distSq < innerSq;

                    mutable.set(cx + x, cy + y, cz + z);

                    if (onEdge) {
                        world.setBlock(mutable, palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                    } else if (inside) {
                        world.setBlock(mutable, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Floors every 5 blocks
        for (int floor = 0; floor < height; floor += 5) {
            for (int x = -radius + 1; x < radius; x++) {
                for (int z = -radius + 1; z < radius; z++) {
                    if (x * x + z * z < floorThreshSq) {
                        world.setBlock(center.offset(x, floor, z), palette.getFloorState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Spiral staircase
        StructureHelper.createSpiralStairs(world, center, radius - 1, height,
            palette.woodStairs.defaultBlockState(), palette.getLogState());

        // Windows on each floor
        for (int floor = 2; floor < height; floor += 5) {
            addCircularWindows(world, center, radius, floor);
        }

        // Guard's supply chest on top floor, against the north wall
        int topFloor = (height / 5) * 5; // highest floor divisible by 5
        StructureHelper.placeChest(world, center.offset(0, topFloor + 1, -(radius - 2)),
            Direction.SOUTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);

        // Crenellated top
        StructureHelper.addCircularCrenellations(world, center, radius, height, palette.getPrimaryWallState());

        // Biome-specific roof and flag pole
        int roofPeakY = buildCylindricalRoof(world, center, radius, height);

        // Flag pole sits on top of the roof peak
        world.setBlock(center.above(roofPeakY), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.above(roofPeakY + 1), palette.getFenceState(), StructureHelper.SET_FLAGS);

        // Gargoyle waterspouts on Plains corner towers
        if (palette == BiomePalette.PLAINS && type == TowerType.CORNER) {
            addGargoyleWaterspouts(world, center, radius, height);
        }
    }

    /**
     * Add gargoyle waterspouts projecting from the tower wall at cardinal directions,
     * just below the crenellations. Each gargoyle is a stone brick "neck" block
     * projecting outward with a skull on top.
     */
    private void addGargoyleWaterspouts(ServerLevel world, BlockPos center, int radius, int height) {
        int gargoyleY = height - 2;

        // Cardinal direction offsets: N, S, E, W
        int[][] directions = {
            {0, -(radius + 1)},  // North
            {0, radius + 1},     // South
            {radius + 1, 0},     // East
            {-(radius + 1), 0}   // West
        };

        // Alternate between skeleton skull and zombie head for variety
        BlockState[] skulls = {
            Blocks.SKELETON_SKULL.defaultBlockState(),
            Blocks.ZOMBIE_HEAD.defaultBlockState(),
            Blocks.SKELETON_SKULL.defaultBlockState(),
            Blocks.ZOMBIE_HEAD.defaultBlockState()
        };

        for (int i = 0; i < directions.length; i++) {
            int dx = directions[i][0];
            int dz = directions[i][1];

            // Stone brick "neck" projecting from the wall
            BlockPos neckPos = center.offset(dx, gargoyleY, dz);
            world.setBlock(neckPos, Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);

            // Skull on top of the neck
            BlockPos skullPos = neckPos.above();
            world.setBlock(skullPos, skulls[i], StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Build a biome-specific roof on a cylindrical tower.
     * Returns the Y offset from center where the flag pole should start.
     */
    private int buildCylindricalRoof(ServerLevel world, BlockPos center, int radius, int height) {
        return switch (palette) {
            case PLAINS -> buildConicalRoof(world, center, radius, height, palette.getRoofState(), false);
            case DESERT -> buildDesertDome(world, center, radius, height);
            case TAIGA -> buildTaigaSpire(world, center, radius, height);
            case SNOWY -> buildConicalRoof(world, center, radius, height, palette.getRoofState(), true);
            case SAVANNA -> buildSavannaFlatCap(world, center, radius, height);
        };
    }

    /**
     * Classic conical roof that properly scales with radius.
     * Layers taper from radius-1 down to 1 (or 0 center cap).
     * Returns the Y offset above center for flag pole placement.
     */
    private int buildConicalRoof(ServerLevel world, BlockPos center, int radius, int height, BlockState roofState, boolean addSnow) {
        int layers = radius - 1; // Number of tapering layers
        for (int layer = 0; layer < layers; layer++) {
            int roofRadius = radius - 1 - layer;
            int roofRadiusSq = roofRadius * roofRadius;
            int y = height + 1 + layer;
            for (int x = -roofRadius; x <= roofRadius; x++) {
                for (int z = -roofRadius; z <= roofRadius; z++) {
                    if (x * x + z * z <= roofRadiusSq) {
                        world.setBlock(center.offset(x, y, z), roofState, StructureHelper.SET_FLAGS);
                        if (addSnow) {
                            world.setBlock(center.offset(x, y + 1, z),
                                Blocks.SNOW.defaultBlockState(), StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
        }
        // Cap block at the very top center
        int peakY = height + 1 + layers;
        world.setBlock(center.above(peakY), roofState, StructureHelper.SET_FLAGS);
        if (addSnow) {
            world.setBlock(center.above(peakY + 1), Blocks.SNOW.defaultBlockState(), StructureHelper.SET_FLAGS);
            return peakY + 2; // Flag pole above snow
        }
        return peakY + 1; // Flag pole above cap
    }

    /**
     * Desert: flat top with parapet lip and small dome center.
     * No conical roof. Wall ring as parapet at height+1, dome center block at height+1
     * with roofBlock ring around it at height.
     */
    private int buildDesertDome(ServerLevel world, BlockPos center, int radius, int height) {
        int radiusSq = radius * radius;
        double innerSq = (radius - 1.5) * (radius - 1.5);

        // Fill the top surface as a solid platform first (support for dome and parapet)
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radiusSq) {
                    world.setBlock(center.offset(x, height, z), palette.getRoofState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Parapet lip: ring of wall blocks at height+1 on the outer edge
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int distSq = x * x + z * z;
                if (distSq <= radiusSq && distSq > innerSq) {
                    world.setBlock(center.offset(x, height + 1, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Small dome: ring of roofBlock at height+1 in the center area (radius 2)
        int domeRadius = Math.min(2, radius - 2);
        if (domeRadius > 0) {
            int domeRadiusSq = domeRadius * domeRadius;
            for (int x = -domeRadius; x <= domeRadius; x++) {
                for (int z = -domeRadius; z <= domeRadius; z++) {
                    if (x * x + z * z <= domeRadiusSq) {
                        world.setBlock(center.offset(x, height + 1, z), palette.getRoofState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
        // Dome peak
        world.setBlock(center.above(height + 2), palette.getRoofState(), StructureHelper.SET_FLAGS);

        return height + 3; // Flag pole above dome peak
    }

    /**
     * Taiga: tall steep spire using deepslate_tiles (roofBlock).
     * Extra height layers compared to plains, topped with a fence as finial.
     */
    private int buildTaigaSpire(ServerLevel world, BlockPos center, int radius, int height) {
        // Spire is steeper: more layers than a standard cone
        // We go from radius-1 down to 0 but also add extra intermediate layers
        int currentRadius = radius - 1;
        int y = height + 1;
        BlockState spireBlock = palette.getRoofState(); // deepslate_tiles for TAIGA

        while (currentRadius > 0) {
            int currentRadiusSq = currentRadius * currentRadius;
            // Place ring at current radius
            for (int x = -currentRadius; x <= currentRadius; x++) {
                for (int z = -currentRadius; z <= currentRadius; z++) {
                    if (x * x + z * z <= currentRadiusSq) {
                        world.setBlock(center.offset(x, y, z), spireBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
            y++;

            // For steep spire, add an extra layer at the same radius for larger radii
            if (currentRadius >= 3) {
                for (int x = -currentRadius; x <= currentRadius; x++) {
                    for (int z = -currentRadius; z <= currentRadius; z++) {
                        if (x * x + z * z <= currentRadiusSq) {
                            world.setBlock(center.offset(x, y, z), spireBlock, StructureHelper.SET_FLAGS);
                        }
                    }
                }
                y++;
            }

            currentRadius--;
        }

        // Pointed tip: single block at center
        world.setBlock(center.above(y), spireBlock, StructureHelper.SET_FLAGS);
        y++;

        // Norse dragon prow: fence post topped with a dragon head facing outward
        world.setBlock(center.above(y), palette.getFenceState(), StructureHelper.SET_FLAGS);
        y++;
        world.setBlock(center.above(y),
            Blocks.SKELETON_SKULL.defaultBlockState(), StructureHelper.SET_FLAGS);

        return y + 1; // Flag pole above dragon head
    }

    /**
     * Savanna: very low flat cap, just 1-2 layers, almost flat.
     * Terracotta edge ring (roofBlock = ORANGE_TERRACOTTA for savanna).
     */
    private int buildSavannaFlatCap(ServerLevel world, BlockPos center, int radius, int height) {
        int radiusSq = radius * radius;
        double innerSq = (radius - 1.5) * (radius - 1.5);

        // Flat roof surface
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radiusSq) {
                    world.setBlock(center.offset(x, height, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Terracotta edge ring at height+1 (only the outer rim)
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int distSq = x * x + z * z;
                if (distSq <= radiusSq && distSq > innerSq) {
                    world.setBlock(center.offset(x, height + 1, z), palette.getRoofState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        return height + 2; // Flag pole above edge ring
    }

    private void generateSquareTower(ServerLevel world, BlockPos center, TowerType type) {
        int halfSize = adjustedRadius(type);
        int height = adjustedHeight(type);

        BlockPos corner1 = center.offset(-halfSize, 0, -halfSize);
        BlockPos corner2 = center.offset(halfSize, height, halfSize);

        // Foundation
        BlockPos foundCorner1 = corner1.offset(-1, -2, -1);
        BlockPos foundCorner2 = corner2.offset(1, -height + 1, 1);
        StructureHelper.fillBox(world, foundCorner1, foundCorner2.atY(center.getY() - 1), Blocks.COBBLESTONE.defaultBlockState());

        // Walls
        for (int y = 0; y < height; y++) {
            for (int x = -halfSize; x <= halfSize; x++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    boolean isEdge = x == -halfSize || x == halfSize || z == -halfSize || z == halfSize;
                    BlockPos pos = center.offset(x, y, z);

                    if (isEdge) {
                        world.setBlock(pos, palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                    } else {
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Floors
        for (int floor = 0; floor < height; floor += 5) {
            for (int x = -halfSize + 1; x < halfSize; x++) {
                for (int z = -halfSize + 1; z < halfSize; z++) {
                    world.setBlock(center.offset(x, floor, z), palette.getFloorState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Ladder with backing block - ladder faces west (away from east wall)
        // Ensure the wall block behind it is solid
        for (int y = 1; y < height; y++) {
            // Backing block on the wall (already placed as part of walls, but ensure it's solid)
            BlockPos backingPos = center.offset(halfSize, y, 0);
            if (world.getBlockState(backingPos).isAir()) {
                world.setBlock(backingPos, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
            // Ladder facing west (back against east wall)
            world.setBlock(center.offset(halfSize - 1, y, 0),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // Windows
        for (int floor = 2; floor < height; floor += 5) {
            addSquareWindows(world, center, halfSize, floor);
        }

        // Guard's supply chest on top floor
        int topFloor = (height / 5) * 5;
        StructureHelper.placeChest(world, center.above(topFloor + 1).relative(Direction.NORTH, halfSize - 2),
            Direction.EAST, BuiltInLootTables.VILLAGE_WEAPONSMITH);

        // Crenellations
        StructureHelper.addCrenellations(world, corner1.atY(center.getY() + height),
            corner2.atY(center.getY() + height), center.getY() + height + 1, palette.getPrimaryWallState());

        // Biome-specific square tower top
        buildSquareTowerTop(world, center, halfSize, height);

        // Lighting - place lanterns on floor (not hanging)
        // Floor lanterns sit on the floor block
        for (int floor = 0; floor < height; floor += 5) {
            BlockPos lanternPos = center.above(floor + 1);
            // Make sure there's a floor block below
            if (!world.getBlockState(lanternPos.below()).isAir()) {
                world.setBlock(lanternPos,
                    palette.light.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
            }
        }
    }

    /**
     * Build biome-specific caps on square towers.
     * - DESERT/SAVANNA: Keep flat with crenellations (already placed)
     * - TAIGA: Small peaked roof (4 stair blocks as hip roof)
     * - SNOWY: Peaked roof + snow layers
     * - PLAINS: Keep flat with crenellations (already placed)
     */
    private void buildSquareTowerTop(ServerLevel world, BlockPos center, int halfSize, int height) {
        switch (palette) {
            case TAIGA -> buildSquarePeakedRoof(world, center, halfSize, height, false);
            case SNOWY -> buildSquarePeakedRoof(world, center, halfSize, height, true);
            // DESERT, SAVANNA, PLAINS: flat with crenellations is already placed above
            default -> {}
        }
    }

    /**
     * Build a small peaked hip roof on a square tower using stair blocks.
     * Layers taper inward from the edges. Each layer is a ring of stairs facing inward
     * with a fill of roof blocks in the interior, topped with a ridge.
     */
    private void buildSquarePeakedRoof(ServerLevel world, BlockPos center, int halfSize, int height, boolean addSnow) {
        BlockState roofBlock = palette.getRoofState();
        int roofY = height + 1; // Start just above the crenellation row

        // Build a solid floor at roofY as support for the roof structure
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                world.setBlock(center.offset(x, roofY, z), roofBlock, StructureHelper.SET_FLAGS);
            }
        }
        if (addSnow) {
            addSnowLayer(world, center, halfSize, halfSize, roofY);
        }

        // Tapering layers
        int layer = 1;
        int currentHalf = halfSize - 1;
        while (currentHalf >= 0) {
            int y = roofY + layer;
            // Fill this layer as a solid platform
            for (int x = -currentHalf; x <= currentHalf; x++) {
                for (int z = -currentHalf; z <= currentHalf; z++) {
                    world.setBlock(center.offset(x, y, z), roofBlock, StructureHelper.SET_FLAGS);
                }
            }
            if (addSnow) {
                addSnowLayer(world, center, currentHalf, currentHalf, y);
            }
            currentHalf--;
            layer++;
        }

        // Place stair blocks on the outer ring of the first roof layer facing inward
        // for a nicer visual (4 cardinal directions)
        int stairY = roofY;
        BlockState northStairs = palette.stoneStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState southStairs = palette.stoneStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState eastStairs = palette.stoneStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.BOTTOM);
        BlockState westStairs = palette.stoneStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.BOTTOM);

        // North edge stairs (facing south/inward)
        for (int x = -halfSize; x <= halfSize; x++) {
            world.setBlock(center.offset(x, stairY, -halfSize), northStairs, StructureHelper.SET_FLAGS);
        }
        // South edge stairs (facing north/inward)
        for (int x = -halfSize; x <= halfSize; x++) {
            world.setBlock(center.offset(x, stairY, halfSize), southStairs, StructureHelper.SET_FLAGS);
        }
        // East edge stairs (facing west/inward)
        for (int z = -halfSize + 1; z < halfSize; z++) {
            world.setBlock(center.offset(halfSize, stairY, z), eastStairs, StructureHelper.SET_FLAGS);
        }
        // West edge stairs (facing east/inward)
        for (int z = -halfSize + 1; z < halfSize; z++) {
            world.setBlock(center.offset(-halfSize, stairY, z), westStairs, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Place snow layer blocks on top of a rectangular area.
     * Only places snow where there is a solid block directly below.
     */
    private void addSnowLayer(ServerLevel world, BlockPos center, int halfX, int halfZ, int y) {
        for (int x = -halfX; x <= halfX; x++) {
            for (int z = -halfZ; z <= halfZ; z++) {
                BlockPos snowPos = center.offset(x, y + 1, z);
                if (!world.getBlockState(snowPos.below()).isAir()) {
                    world.setBlock(snowPos, Blocks.SNOW.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }
    }

    private void addCircularWindows(ServerLevel world, BlockPos center, int radius, int y) {
        BlockState bars = palette.getBarsState();

        // 4 windows, one each direction
        world.setBlock(center.offset(radius, y, 0), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(radius, y + 1, 0), bars, StructureHelper.SET_FLAGS);

        world.setBlock(center.offset(-radius, y, 0), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-radius, y + 1, 0), bars, StructureHelper.SET_FLAGS);

        world.setBlock(center.offset(0, y, radius), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, y + 1, radius), bars, StructureHelper.SET_FLAGS);

        world.setBlock(center.offset(0, y, -radius), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, y + 1, -radius), bars, StructureHelper.SET_FLAGS);
    }

    private void addSquareWindows(ServerLevel world, BlockPos center, int halfSize, int y) {
        BlockState bars = palette.getBarsState();

        // Window on each wall
        world.setBlock(center.offset(halfSize, y, 0), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(halfSize, y + 1, 0), bars, StructureHelper.SET_FLAGS);

        world.setBlock(center.offset(-halfSize, y, 0), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-halfSize, y + 1, 0), bars, StructureHelper.SET_FLAGS);

        world.setBlock(center.offset(0, y, halfSize), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, y + 1, halfSize), bars, StructureHelper.SET_FLAGS);

        world.setBlock(center.offset(0, y, -halfSize), bars, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, y + 1, -halfSize), bars, StructureHelper.SET_FLAGS);
    }

}
