package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.loot.LootTables;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Generates courtyard features within a castle's walls.
 * Features vary by biome to support architectural diversity beyond palette swaps.
 */
public class CourtyardGenerator {

    private final BiomePalette palette;
    private final Random random;

    public CourtyardGenerator(BiomePalette palette, Random random) {
        this.palette = palette;
        this.random = random;
    }

    /**
     * Generate all courtyard features appropriate for the castle size.
     */
    public void generate(ServerWorld world, BlockPos center, int radius,
                         int keepHalfWidth, int keepHalfDepth,
                         CastleGenerator.CastleSize size) {
        int courtyardInner = Math.max(keepHalfWidth, keepHalfDepth) + 3;
        int courtyardOuter = radius - 10;

        // Village bell — required for villager gathering point and raid detection
        // Placed on a post near the gate approach, visible from the courtyard
        placeBell(world, center.add(3, 0, courtyardOuter - 4));

        // Path from main gate to keep
        generatePath(world, center.south(keepHalfDepth + 1), center.south(courtyardOuter - 2));

        // Side features based on size
        if (size != CastleGenerator.CastleSize.SMALL) {
            generateWaterFeature(world, center.add(-courtyardInner - 5, 0, 5));
            generateTrainingArea(world, center.add(courtyardInner + 5, 0, 5));
        }

        if (size == CastleGenerator.CastleSize.LARGE) {
            generateStables(world, center.add(-courtyardInner - 8, 0, courtyardOuter - 10), Direction.EAST);
            generateBarracks(world, center.add(courtyardInner + 8, 0, courtyardOuter - 10), Direction.WEST);
        }
    }

    private void generatePath(ServerWorld world, BlockPos start, BlockPos end) {
        int dz = end.getZ() - start.getZ();
        int length = Math.abs(dz);
        int direction = Integer.signum(dz);

        for (int i = 0; i <= length; i++) {
            BlockPos pos = start.add(0, 0, i * direction);
            world.setBlockState(pos.west(1), palette.getFloorState(), StructureHelper.SET_FLAGS);
            world.setBlockState(pos, palette.getFloorState(), StructureHelper.SET_FLAGS);
            world.setBlockState(pos.east(1), palette.getFloorState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Biome-specific water/gathering feature.
     * Plains/taiga: traditional well with water
     * Desert: covered cistern with cactus garden
     * Savanna: open-air water trough with shade structure
     * Snowy: campfire hearth with seating
     */
    private void generateWaterFeature(ServerWorld world, BlockPos center) {
        switch (palette) {
            case DESERT -> generateCistern(world, center);
            case SNOWY -> generateHearth(world, center);
            case SAVANNA -> {
                generateWell(world, center);
                // Conical tower monument (Great Zimbabwe inspired) — placed near the well
                generateConicalTower(world, center.add(6, 0, 0));
            }
            default -> generateWell(world, center);
        }

        // Norse standing rune stones near the well for Taiga biome
        if (palette == BiomePalette.TAIGA) {
            placeRuneStones(world, center);
        }
    }

    /**
     * Place 2-3 standing rune stones (2-high chiseled stone bricks) near the water feature.
     * These represent Norse rune markers found in Viking settlements.
     */
    private void placeRuneStones(ServerWorld world, BlockPos center) {
        BlockState runeBlock = Blocks.CHISELED_STONE_BRICKS.getDefaultState();

        // Rune stone 1: northwest of the well
        BlockPos rune1 = center.add(-3, 0, -3);
        world.setBlockState(rune1, runeBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(rune1.up(1), runeBlock, StructureHelper.SET_FLAGS);

        // Rune stone 2: east of the well
        BlockPos rune2 = center.add(4, 0, 1);
        world.setBlockState(rune2, runeBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(rune2.up(1), runeBlock, StructureHelper.SET_FLAGS);

        // Rune stone 3: south of the well (50% chance for variety)
        if (random.nextBoolean()) {
            BlockPos rune3 = center.add(-1, 0, 4);
            world.setBlockState(rune3, runeBlock, StructureHelper.SET_FLAGS);
            world.setBlockState(rune3.up(1), runeBlock, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Conical tower monument — references the famous conical tower of Great Zimbabwe.
     * A solid 3-block-radius, 6-block-tall cone of mud bricks built as concentric rings
     * of decreasing radius.
     */
    private void generateConicalTower(ServerWorld world, BlockPos center) {
        BlockState mudBricks = Blocks.MUD_BRICKS.getDefaultState();
        int maxRadius = 3;
        int height = 6;

        for (int y = 0; y < height; y++) {
            // Radius decreases as we go up: 3 at bottom, narrowing to 0 at top
            // Linear interpolation: radius = maxRadius * (1 - y / height)
            double radiusAtY = maxRadius * (1.0 - (double) y / height);

            for (int x = -maxRadius; x <= maxRadius; x++) {
                for (int z = -maxRadius; z <= maxRadius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    if (dist <= radiusAtY + 0.5) {
                        world.setBlockState(center.add(x, y, z), mudBricks, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Cap the top with a single block
        world.setBlockState(center.up(height), mudBricks, StructureHelper.SET_FLAGS);
    }

    private void generateWell(ServerWorld world, BlockPos center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(center.add(x, 0, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlockState(center.add(x, 1, z), palette.getWallState(), StructureHelper.SET_FLAGS);
            }
        }
        // Water in center
        world.setBlockState(center, Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.down(1), Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);

        // Roof posts
        world.setBlockState(center.add(-1, 2, -1), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(1, 2, -1), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-1, 2, 1), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(1, 2, 1), palette.getFenceState(), StructureHelper.SET_FLAGS);

        // Roof
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(center.add(x, 3, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }
    }

    /**
     * Desert cistern: sunken water with sandstone walls, decorated with cactus pots.
     */
    private void generateCistern(ServerWorld world, BlockPos center) {
        // Sunken basin (2 deep)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlockState(center.add(x, -1, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlockState(center.add(x, 0, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        // Water pool in center
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(center.add(x, -1, z), Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);
                world.setBlockState(center.add(x, 0, z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Corner pillars with shade canopy (3 blocks tall: y=1, y=2, y=3 to meet canopy)
        BlockPos[] corners = {center.add(-2, 1, -2), center.add(2, 1, -2), center.add(-2, 1, 2), center.add(2, 1, 2)};
        for (BlockPos corner : corners) {
            world.setBlockState(corner, palette.getLogState(), StructureHelper.SET_FLAGS);
            world.setBlockState(corner.up(1), palette.getLogState(), StructureHelper.SET_FLAGS);
            world.setBlockState(corner.up(2), palette.getLogState(), StructureHelper.SET_FLAGS);
        }

        // Shade canopy
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlockState(center.add(x, 3, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }

        // Decorative potted cactus on sandstone pedestals
        world.setBlockState(center.add(-3, 0, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(3, 0, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-3, 1, 0), Blocks.POTTED_CACTUS.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(3, 1, 0), Blocks.POTTED_CACTUS.getDefaultState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Snowy hearth: campfire with log seating, sheltered by partial walls.
     */
    private void generateHearth(ServerWorld world, BlockPos center) {
        // Stone base
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlockState(center.add(x, 0, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Central campfire
        world.setBlockState(center.up(1), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);

        // Log seating around the fire
        world.setBlockState(center.add(-2, 1, 0), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(2, 1, 0), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 1, -2), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 1, 2), palette.getLogState(), StructureHelper.SET_FLAGS);

        // Partial windbreak walls (two sides)
        for (int z = -1; z <= 1; z++) {
            world.setBlockState(center.add(-2, 2, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlockState(center.add(-2, 3, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }
        for (int x = -1; x <= 1; x++) {
            world.setBlockState(center.add(x, 2, -2), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlockState(center.add(x, 3, -2), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Snow layer on top of windbreak walls for snowy biome feel
        world.setBlockState(center.add(-2, 4, 0), Blocks.SNOW.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 4, -2), Blocks.SNOW.getDefaultState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Training area — varies by biome.
     * Plains/savanna: training dummies with hay targets
     * Desert: archery range with terracotta targets
     * Taiga: weapon racks with armor stands
     * Snowy: target range with packed snow walls
     */
    private void generateTrainingArea(ServerWorld world, BlockPos center) {
        switch (palette) {
            case DESERT -> generateArcheryRange(world, center);
            case SNOWY -> generateSnowRange(world, center);
            default -> generateDummyYard(world, center);
        }
    }

    private void generateDummyYard(ServerWorld world, BlockPos center) {
        for (int i = -2; i <= 2; i += 2) {
            BlockPos dummyPos = center.add(i, 0, 0);
            world.setBlockState(dummyPos, palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlockState(dummyPos.up(1), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlockState(dummyPos.up(2), Blocks.CARVED_PUMPKIN.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        world.setBlockState(center.add(0, 0, 3), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 1, 3), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Desert archery range: terracotta targets backed by sandstone wall.
     */
    private void generateArcheryRange(ServerWorld world, BlockPos center) {
        // Back wall
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 3; y++) {
                world.setBlockState(center.add(x, y, 4), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Terracotta targets on the wall
        world.setBlockState(center.add(-1, 2, 4), Blocks.RED_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(1, 2, 4), Blocks.RED_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 2, 4), Blocks.WHITE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);

        // Shooting positions marked with slabs
        for (int i = -2; i <= 2; i += 2) {
            world.setBlockState(center.add(i, 0, -2), palette.getFloorState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Snowy target range: snow block backstop with wooden frames.
     */
    private void generateSnowRange(ServerWorld world, BlockPos center) {
        // Packed snow backstop
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 3; y++) {
                world.setBlockState(center.add(x, y, 4), Blocks.SNOW_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Wooden target frames
        for (int i = -2; i <= 2; i += 2) {
            world.setBlockState(center.add(i, 1, 3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlockState(center.add(i, 2, 3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlockState(center.add(i, 3, 3), Blocks.CARVED_PUMPKIN.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Warming barrel (campfire near shooting positions)
        world.setBlockState(center.add(0, 0, -2), Blocks.STONE_BRICKS.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 1, -2), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
    }

    private void generateStables(ServerWorld world, BlockPos corner, Direction facing) {
        int width = 8;
        int depth = 5;
        int height = 4;

        // Walls — horizontal logs for X-aligned walls, Z-aligned for side walls
        BlockState logX = palette.log.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.X);
        BlockState logZ = palette.log.getDefaultState().with(PillarBlock.AXIS, Direction.Axis.Z);
        BlockState logY = palette.getLogState(); // vertical for corners
        for (int x = 0; x <= width; x++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(x, y, 0), logX, StructureHelper.SET_FLAGS);
                world.setBlockState(corner.add(x, y, depth), logX, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = 0; z <= depth; z++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(0, y, z), logZ, StructureHelper.SET_FLAGS);
                world.setBlockState(corner.add(width, y, z), logZ, StructureHelper.SET_FLAGS);
            }
        }

        // Fill interior with air
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                for (int y = 1; y < height; y++) {
                    world.setBlockState(corner.add(x, y, z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Floor
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                world.setBlockState(corner.add(x, 0, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }

        // Roof
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                world.setBlockState(corner.add(x, height, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }

        // Hay bales
        world.setBlockState(corner.add(2, 1, 2), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(corner.add(2, 2, 2), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);

        // Supply chest near hay bales
        StructureHelper.placeChest(world, corner.add(4, 1, 2),
            Direction.NORTH, LootTables.VILLAGE_PLAINS_CHEST);

        // Workstation — composter (farmer, manages feed and crops)
        world.setBlockState(corner.add(6, 1, 2),
            Blocks.COMPOSTER.getDefaultState(), StructureHelper.SET_FLAGS);

        // Door opening: 2-wide with fence gates and headroom, solid lintel above
        BlockPos doorPos = corner.add(width / 2, 0, 0);
        BlockPos doorPos2 = doorPos.east(1);
        // Fence gates at Y+1
        world.setBlockState(doorPos.up(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        world.setBlockState(doorPos2.up(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        // Headroom at Y+2
        world.setBlockState(doorPos.up(2), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(doorPos2.up(2), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        // Solid lintel at Y+3 (height - 1)
        world.setBlockState(doorPos.up(3), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlockState(doorPos2.up(3), palette.getLogState(), StructureHelper.SET_FLAGS);

        // Interior lighting
        world.setBlockState(corner.add(4, height - 1, 2), palette.getLightState(), StructureHelper.SET_FLAGS);
    }

    private void generateBarracks(ServerWorld world, BlockPos corner, Direction facing) {
        int width = 10;
        int depth = 6;
        int height = 5;

        // Walls
        for (int x = 0; x <= width; x++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(x, y, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlockState(corner.add(x, y, depth), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        for (int z = 0; z <= depth; z++) {
            for (int y = 0; y < height; y++) {
                world.setBlockState(corner.add(0, y, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlockState(corner.add(width, y, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Clear interior
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                for (int y = 1; y < height; y++) {
                    world.setBlockState(corner.add(x, y, z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Floor
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                world.setBlockState(corner.add(x, 0, z), palette.getFloorState(), StructureHelper.SET_FLAGS);
            }
        }

        // Roof
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                world.setBlockState(corner.add(x, height, z), palette.getRoofState(), StructureHelper.SET_FLAGS);
            }
        }

        // Beds
        BlockState bedFoot = palette.getBedState()
            .with(BedBlock.PART, BedPart.FOOT)
            .with(BedBlock.FACING, Direction.SOUTH);
        BlockState bedHead = palette.getBedState()
            .with(BedBlock.PART, BedPart.HEAD)
            .with(BedBlock.FACING, Direction.SOUTH);
        for (int i = 2; i < width - 1; i += 3) {
            world.setBlockState(corner.add(i, 1, 2), bedFoot, StructureHelper.SET_FLAGS);
            world.setBlockState(corner.add(i, 1, 3), bedHead, StructureHelper.SET_FLAGS);
        }

        // Door opening: 2-wide with fence gates and headroom, solid lintel above
        BlockPos barracksDoor = corner.add(width / 2, 0, 0);
        BlockPos barracksDoor2 = barracksDoor.east(1);
        // Fence gates at Y+1
        world.setBlockState(barracksDoor.up(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        world.setBlockState(barracksDoor2.up(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        // Headroom at Y+2
        world.setBlockState(barracksDoor.up(2), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(barracksDoor2.up(2), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        // Solid lintel at Y+3 (height - 1 for barracks)
        world.setBlockState(barracksDoor.up(3), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlockState(barracksDoor2.up(3), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

        // Weapon chest against back wall
        StructureHelper.placeChest(world, corner.add(width - 2, 1, depth - 1),
            Direction.NORTH, LootTables.VILLAGE_WEAPONSMITH_CHEST);

        // Workstations — fletcher (arrows for soldiers), smoker (cooking for troops)
        world.setBlockState(corner.add(2, 1, depth - 1),
            Blocks.FLETCHING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(corner.add(4, 1, depth - 1),
            Blocks.SMOKER.getDefaultState(), StructureHelper.SET_FLAGS);

        // Interior lighting
        world.setBlockState(corner.add(3, height - 1, 3), palette.getLightState(), StructureHelper.SET_FLAGS);
        world.setBlockState(corner.add(7, height - 1, 3), palette.getLightState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Place a village bell on a fence post — required for villager gathering and raid detection.
     */
    private void placeBell(ServerWorld world, BlockPos pos) {
        // Fence post base
        world.setBlockState(pos, palette.getFenceState(), StructureHelper.SET_FLAGS);
        // Bell on top
        world.setBlockState(pos.up(), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);
    }
}
