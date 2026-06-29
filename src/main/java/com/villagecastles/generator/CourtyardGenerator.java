package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
    public void generate(ServerLevel world, BlockPos center, int radius,
                         int keepHalfWidth, int keepHalfDepth,
                         CastleGenerator.CastleSize size) {
        int courtyardInner = Math.max(keepHalfWidth, keepHalfDepth) + 3;
        int courtyardOuter = radius - 10;

        // Village bell — required for villager gathering point and raid detection
        // Placed on a post near the gate approach, visible from the courtyard
        placeBell(world, center.offset(3, 0, courtyardOuter - 4));

        // Path from main gate to keep
        generatePath(world, center.south(keepHalfDepth + 1), center.south(courtyardOuter - 2));

        // Side features based on size
        if (size != CastleGenerator.CastleSize.SMALL) {
            generateWaterFeature(world, center.offset(-courtyardInner - 5, 0, 5));
            generateTrainingArea(world, center.offset(courtyardInner + 5, 0, 5));
        }

        if (size == CastleGenerator.CastleSize.LARGE) {
            generateStables(world, center.offset(-courtyardInner - 8, 0, courtyardOuter - 10), Direction.EAST);
            generateBarracks(world, center.offset(courtyardInner + 8, 0, courtyardOuter - 10), Direction.WEST);
        }
    }

    private void generatePath(ServerLevel world, BlockPos start, BlockPos end) {
        int dz = end.getZ() - start.getZ();
        int length = Math.abs(dz);
        int direction = Integer.signum(dz);

        for (int i = 0; i <= length; i++) {
            BlockPos pos = start.offset(0, 0, i * direction);
            world.setBlock(pos.west(1), palette.getFloorState(), StructureHelper.SET_FLAGS);
            world.setBlock(pos, palette.getFloorState(), StructureHelper.SET_FLAGS);
            world.setBlock(pos.east(1), palette.getFloorState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Biome-specific water/gathering feature.
     * Plains/taiga: traditional well with water
     * Desert: covered cistern with cactus garden
     * Savanna: open-air water trough with shade structure
     * Snowy: campfire hearth with seating
     */
    private void generateWaterFeature(ServerLevel world, BlockPos center) {
        switch (palette) {
            case DESERT -> generateCistern(world, center);
            case SNOWY -> generateHearth(world, center);
            case SAVANNA -> {
                generateWell(world, center);
                // Conical tower monument (Great Zimbabwe inspired) — placed near the well
                generateConicalTower(world, center.offset(6, 0, 0));
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
    private void placeRuneStones(ServerLevel world, BlockPos center) {
        BlockState runeBlock = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();

        // Rune stone 1: northwest of the well
        BlockPos rune1 = center.offset(-3, 0, -3);
        world.setBlock(rune1, runeBlock, StructureHelper.SET_FLAGS);
        world.setBlock(rune1.above(1), runeBlock, StructureHelper.SET_FLAGS);

        // Rune stone 2: east of the well
        BlockPos rune2 = center.offset(4, 0, 1);
        world.setBlock(rune2, runeBlock, StructureHelper.SET_FLAGS);
        world.setBlock(rune2.above(1), runeBlock, StructureHelper.SET_FLAGS);

        // Rune stone 3: south of the well (50% chance for variety)
        if (random.nextBoolean()) {
            BlockPos rune3 = center.offset(-1, 0, 4);
            world.setBlock(rune3, runeBlock, StructureHelper.SET_FLAGS);
            world.setBlock(rune3.above(1), runeBlock, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Conical tower monument — references the famous conical tower of Great Zimbabwe.
     * A solid 3-block-radius, 6-block-tall cone of mud bricks built as concentric rings
     * of decreasing radius.
     */
    private void generateConicalTower(ServerLevel world, BlockPos center) {
        BlockState mudBricks = Blocks.MUD_BRICKS.defaultBlockState();
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
                        world.setBlock(center.offset(x, y, z), mudBricks, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Cap the top with a single block
        world.setBlock(center.above(height), mudBricks, StructureHelper.SET_FLAGS);
    }

    private void generateWell(ServerLevel world, BlockPos center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlock(center.offset(x, 0, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlock(center.offset(x, 1, z), palette.getWallState(), StructureHelper.SET_FLAGS);
            }
        }
        // Water in center
        world.setBlock(center, Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.below(1), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Roof posts
        world.setBlock(center.offset(-1, 2, -1), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(1, 2, -1), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-1, 2, 1), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(1, 2, 1), palette.getFenceState(), StructureHelper.SET_FLAGS);

        // Roof
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlock(center.offset(x, 3, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }
    }

    /**
     * Desert cistern: sunken water with sandstone walls, decorated with cactus pots.
     */
    private void generateCistern(ServerLevel world, BlockPos center) {
        // Sunken basin (2 deep)
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlock(center.offset(x, -1, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlock(center.offset(x, 0, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        // Water pool in center
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlock(center.offset(x, -1, z), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(center.offset(x, 0, z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Corner pillars with shade canopy (3 blocks tall: y=1, y=2, y=3 to meet canopy)
        BlockPos[] corners = {center.offset(-2, 1, -2), center.offset(2, 1, -2), center.offset(-2, 1, 2), center.offset(2, 1, 2)};
        for (BlockPos corner : corners) {
            world.setBlock(corner, palette.getLogState(), StructureHelper.SET_FLAGS);
            world.setBlock(corner.above(1), palette.getLogState(), StructureHelper.SET_FLAGS);
            world.setBlock(corner.above(2), palette.getLogState(), StructureHelper.SET_FLAGS);
        }

        // Shade canopy
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlock(center.offset(x, 3, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }

        // Decorative potted cactus on sandstone pedestals
        world.setBlock(center.offset(-3, 0, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(3, 0, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-3, 1, 0), Blocks.POTTED_CACTUS.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(3, 1, 0), Blocks.POTTED_CACTUS.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Snowy hearth: campfire with log seating, sheltered by partial walls.
     */
    private void generateHearth(ServerLevel world, BlockPos center) {
        // Stone base
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.setBlock(center.offset(x, 0, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Central campfire
        world.setBlock(center.above(1), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Log seating around the fire
        world.setBlock(center.offset(-2, 1, 0), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(2, 1, 0), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 1, -2), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 1, 2), palette.getLogState(), StructureHelper.SET_FLAGS);

        // Partial windbreak walls (two sides)
        for (int z = -1; z <= 1; z++) {
            world.setBlock(center.offset(-2, 2, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(center.offset(-2, 3, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }
        for (int x = -1; x <= 1; x++) {
            world.setBlock(center.offset(x, 2, -2), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(center.offset(x, 3, -2), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Snow layer on top of windbreak walls for snowy biome feel
        world.setBlock(center.offset(-2, 4, 0), Blocks.SNOW.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 4, -2), Blocks.SNOW.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Training area — varies by biome.
     * Plains/savanna: training dummies with hay targets
     * Desert: archery range with terracotta targets
     * Taiga: weapon racks with armor stands
     * Snowy: target range with packed snow walls
     */
    private void generateTrainingArea(ServerLevel world, BlockPos center) {
        switch (palette) {
            case DESERT -> generateArcheryRange(world, center);
            case SNOWY -> generateSnowRange(world, center);
            default -> generateDummyYard(world, center);
        }
    }

    private void generateDummyYard(ServerLevel world, BlockPos center) {
        for (int i = -2; i <= 2; i += 2) {
            BlockPos dummyPos = center.offset(i, 0, 0);
            world.setBlock(dummyPos, palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlock(dummyPos.above(1), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlock(dummyPos.above(2), Blocks.CARVED_PUMPKIN.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        world.setBlock(center.offset(0, 0, 3), Blocks.HAY_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 1, 3), Blocks.HAY_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Desert archery range: terracotta targets backed by sandstone wall.
     */
    private void generateArcheryRange(ServerLevel world, BlockPos center) {
        // Back wall
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 3; y++) {
                world.setBlock(center.offset(x, y, 4), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Terracotta targets on the wall
        world.setBlock(center.offset(-1, 2, 4), Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(1, 2, 4), Blocks.DYED_TERRACOTTA.pick(DyeColor.RED).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 2, 4), Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE).defaultBlockState(), StructureHelper.SET_FLAGS);

        // Shooting positions marked with slabs
        for (int i = -2; i <= 2; i += 2) {
            world.setBlock(center.offset(i, 0, -2), palette.getFloorState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Snowy target range: snow block backstop with wooden frames.
     */
    private void generateSnowRange(ServerLevel world, BlockPos center) {
        // Packed snow backstop
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y <= 3; y++) {
                world.setBlock(center.offset(x, y, 4), Blocks.SNOW_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Wooden target frames
        for (int i = -2; i <= 2; i += 2) {
            world.setBlock(center.offset(i, 1, 3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlock(center.offset(i, 2, 3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlock(center.offset(i, 3, 3), Blocks.CARVED_PUMPKIN.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Warming barrel (campfire near shooting positions)
        world.setBlock(center.offset(0, 0, -2), Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 1, -2), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    private void generateStables(ServerLevel world, BlockPos corner, Direction facing) {
        int width = 8;
        int depth = 5;
        int height = 4;

        // Walls — horizontal logs for X-aligned walls, Z-aligned for side walls
        BlockState logX = palette.log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        BlockState logZ = palette.log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        BlockState logY = palette.getLogState(); // vertical for corners
        for (int x = 0; x <= width; x++) {
            for (int y = 0; y < height; y++) {
                world.setBlock(corner.offset(x, y, 0), logX, StructureHelper.SET_FLAGS);
                world.setBlock(corner.offset(x, y, depth), logX, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = 0; z <= depth; z++) {
            for (int y = 0; y < height; y++) {
                world.setBlock(corner.offset(0, y, z), logZ, StructureHelper.SET_FLAGS);
                world.setBlock(corner.offset(width, y, z), logZ, StructureHelper.SET_FLAGS);
            }
        }

        // Fill interior with air
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                for (int y = 1; y < height; y++) {
                    world.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Floor
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                world.setBlock(corner.offset(x, 0, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }

        // Roof
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                world.setBlock(corner.offset(x, height, z), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            }
        }

        // Hay bales
        world.setBlock(corner.offset(2, 1, 2), Blocks.HAY_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(corner.offset(2, 2, 2), Blocks.HAY_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Supply chest near hay bales
        StructureHelper.placeChest(world, corner.offset(4, 1, 2),
            Direction.NORTH, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);

        // Workstation — composter (farmer, manages feed and crops)
        world.setBlock(corner.offset(6, 1, 2),
            Blocks.COMPOSTER.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Door opening: 2-wide with fence gates and headroom, solid lintel above
        BlockPos doorPos = corner.offset(width / 2, 0, 0);
        BlockPos doorPos2 = doorPos.east(1);
        // Fence gates at Y+1
        world.setBlock(doorPos.above(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        world.setBlock(doorPos2.above(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        // Headroom at Y+2
        world.setBlock(doorPos.above(2), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(doorPos2.above(2), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Solid lintel at Y+3 (height - 1)
        world.setBlock(doorPos.above(3), palette.getLogState(), StructureHelper.SET_FLAGS);
        world.setBlock(doorPos2.above(3), palette.getLogState(), StructureHelper.SET_FLAGS);

        // Interior lighting
        world.setBlock(corner.offset(4, height - 1, 2), palette.getLightState(), StructureHelper.SET_FLAGS);
    }

    private void generateBarracks(ServerLevel world, BlockPos corner, Direction facing) {
        int width = 10;
        int depth = 6;
        int height = 5;

        // Walls
        for (int x = 0; x <= width; x++) {
            for (int y = 0; y < height; y++) {
                world.setBlock(corner.offset(x, y, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlock(corner.offset(x, y, depth), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        for (int z = 0; z <= depth; z++) {
            for (int y = 0; y < height; y++) {
                world.setBlock(corner.offset(0, y, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlock(corner.offset(width, y, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Clear interior
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                for (int y = 1; y < height; y++) {
                    world.setBlock(corner.offset(x, y, z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Floor
        for (int x = 1; x < width; x++) {
            for (int z = 1; z < depth; z++) {
                world.setBlock(corner.offset(x, 0, z), palette.getFloorState(), StructureHelper.SET_FLAGS);
            }
        }

        // Roof
        for (int x = 0; x <= width; x++) {
            for (int z = 0; z <= depth; z++) {
                world.setBlock(corner.offset(x, height, z), palette.getRoofState(), StructureHelper.SET_FLAGS);
            }
        }

        // Beds
        BlockState bedFoot = palette.getBedState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, Direction.SOUTH);
        BlockState bedHead = palette.getBedState()
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(BedBlock.FACING, Direction.SOUTH);
        for (int i = 2; i < width - 1; i += 3) {
            world.setBlock(corner.offset(i, 1, 2), bedFoot, StructureHelper.SET_FLAGS);
            world.setBlock(corner.offset(i, 1, 3), bedHead, StructureHelper.SET_FLAGS);
        }

        // Door opening: 2-wide with fence gates and headroom, solid lintel above
        BlockPos barracksDoor = corner.offset(width / 2, 0, 0);
        BlockPos barracksDoor2 = barracksDoor.east(1);
        // Fence gates at Y+1
        world.setBlock(barracksDoor.above(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        world.setBlock(barracksDoor2.above(1), palette.getFenceGateState(), StructureHelper.SET_FLAGS);
        // Headroom at Y+2
        world.setBlock(barracksDoor.above(2), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(barracksDoor2.above(2), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Solid lintel at Y+3 (height - 1 for barracks)
        world.setBlock(barracksDoor.above(3), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(barracksDoor2.above(3), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

        // Weapon chest against back wall
        StructureHelper.placeChest(world, corner.offset(width - 2, 1, depth - 1),
            Direction.NORTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);

        // Workstations — fletcher (arrows for soldiers), smoker (cooking for troops)
        world.setBlock(corner.offset(2, 1, depth - 1),
            Blocks.FLETCHING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(corner.offset(4, 1, depth - 1),
            Blocks.SMOKER.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Interior lighting
        world.setBlock(corner.offset(3, height - 1, 3), palette.getLightState(), StructureHelper.SET_FLAGS);
        world.setBlock(corner.offset(7, height - 1, 3), palette.getLightState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Place a village bell on a fence post — required for villager gathering and raid detection.
     */
    private void placeBell(ServerLevel world, BlockPos pos) {
        // Fence post base
        world.setBlock(pos, palette.getFenceState(), StructureHelper.SET_FLAGS);
        // Bell on top
        world.setBlock(pos.above(), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);
    }
}
