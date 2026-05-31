package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Random;

/**
 * Generates village perimeter wall segments.
 *
 * Wall Types:
 * - PALISADE: Wooden fence wall (for plains, taiga)
 * - STONE: Low stone wall with crenellations
 * - ADOBE: Mud brick wall (for desert, savanna)
 * - RAMPART: Earthen wall with wooden top
 */
public class VillageWallGenerator {

    public enum WallType {
        PALISADE(4, 3),   // Log fence wall
        STONE(5, 4),      // Stone brick wall
        ADOBE(4, 3),      // Mud/sandstone wall
        RAMPART(4, 5);    // Earth mound with fence

        public final int height;
        public final int thickness;

        WallType(int height, int thickness) {
            this.height = height;
            this.thickness = thickness;
        }
    }

    public enum SegmentType {
        STRAIGHT,      // Regular wall segment
        CORNER,        // 90-degree corner
        GATE,          // Small entrance gate
        TOWER,         // Watch tower along wall
        TERMINATOR     // End cap for village edge
    }

    private final BiomePalette palette;
    private final Random random;

    // Standard segment length (should match village path grid)
    private static final int SEGMENT_LENGTH = 8;

    public VillageWallGenerator(BiomePalette palette, Random random) {
        this.palette = palette;
        this.random = random;
    }

    /**
     * Generate a wall segment at the specified position.
     */
    public void generate(ServerLevel world, BlockPos origin, Direction facing, SegmentType segmentType) {
        WallType wallType = getWallTypeForPalette();

        switch (segmentType) {
            case STRAIGHT -> generateStraightSegment(world, origin, facing, wallType);
            case CORNER -> generateCornerSegment(world, origin, facing, wallType);
            case GATE -> generateGateSegment(world, origin, facing, wallType);
            case TOWER -> generateTowerSegment(world, origin, facing, wallType);
            case TERMINATOR -> generateTerminatorSegment(world, origin, facing, wallType);
        }

        // Post-generation pass: fix connection states for fences, walls, iron bars, etc.
        BlockPos boundsMin = origin.offset(-5, -2, -3);
        BlockPos boundsMax = origin.offset(5, 12, 3);
        StructureHelper.updateConnectionStates(world, boundsMin, boundsMax);
    }

    private WallType getWallTypeForPalette() {
        return switch (palette) {
            case PLAINS -> WallType.PALISADE;
            case DESERT -> WallType.ADOBE;
            case SAVANNA -> WallType.ADOBE;
            case TAIGA -> WallType.PALISADE;
            case SNOWY -> WallType.STONE;
        };
    }

    /**
     * Generate a straight wall segment.
     */
    private void generateStraightSegment(ServerLevel world, BlockPos origin, Direction facing, WallType wallType) {
        Direction perpendicular = facing.getClockWise();
        int halfLength = SEGMENT_LENGTH / 2;

        switch (wallType) {
            case PALISADE -> generatePalisadeWall(world, origin, facing, halfLength);
            case STONE -> generateStoneWall(world, origin, facing, halfLength);
            case ADOBE -> generateAdobeWall(world, origin, facing, halfLength);
            case RAMPART -> generateRampartWall(world, origin, facing, halfLength);
        }

        // Add lantern at midpoint (sitting on top of wall)
        world.setBlock(origin.above(wallType.height),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
    }

    private void generatePalisadeWall(ServerLevel world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.getClockWise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.relative(perpendicular, i);

            // Foundation
            world.setBlock(basePos.below(), Blocks.COBBLESTONE.defaultBlockState(), StructureHelper.SET_FLAGS);

            // Log posts (alternating heights for visual interest)
            int postHeight = (Math.abs(i) % 2 == 0) ? 4 : 3;
            for (int y = 0; y < postHeight; y++) {
                world.setBlock(basePos.above(y), palette.getLogState(), StructureHelper.SET_FLAGS);
            }

            // Fence on top
            if (postHeight == 3) {
                world.setBlock(basePos.above(3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            }
        }

        // Pointed tops on corner posts (fence post tip)
        world.setBlock(origin.relative(perpendicular, -halfLength).above(4),
            palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlock(origin.relative(perpendicular, halfLength).above(4),
            palette.getFenceState(), StructureHelper.SET_FLAGS);
    }

    private void generateStoneWall(ServerLevel world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.getClockWise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.relative(perpendicular, i);

            // Foundation
            for (int d = 0; d < 2; d++) {
                world.setBlock(basePos.relative(facing, d).below(), Blocks.COBBLESTONE.defaultBlockState(), StructureHelper.SET_FLAGS);
            }

            // Wall body
            for (int y = 0; y < 4; y++) {
                BlockState wallBlock = palette.getRandomWallBlock(random);
                world.setBlock(basePos.above(y), wallBlock, StructureHelper.SET_FLAGS);
                world.setBlock(basePos.relative(facing, 1).above(y), wallBlock, StructureHelper.SET_FLAGS);
            }

            // Crenellations (alternating)
            if (Math.abs(i) % 2 == 0) {
                world.setBlock(basePos.above(4), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }

            // Walkway
            world.setBlock(basePos.relative(facing, 1).above(3), palette.getFloorState(), StructureHelper.SET_FLAGS);
        }
    }

    private void generateAdobeWall(ServerLevel world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.getClockWise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.relative(perpendicular, i);

            // Foundation
            world.setBlock(basePos.below(), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

            // Wall body (tapered - wider at base)
            for (int y = 0; y < 3; y++) {
                world.setBlock(basePos.above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                if (y < 2) {
                    world.setBlock(basePos.relative(facing, 1).above(y), palette.getSecondaryWallState(), StructureHelper.SET_FLAGS);
                }
            }

            // Decorative top edge
            if (Math.abs(i) % 3 == 0) {
                world.setBlock(basePos.above(3), palette.getAccentWallState(), StructureHelper.SET_FLAGS);
            }
        }
    }

    private void generateRampartWall(ServerLevel world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.getClockWise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.relative(perpendicular, i);

            // Earth mound (coarse dirt/grass)
            for (int d = -1; d <= 1; d++) {
                int moundHeight = (d == 0) ? 3 : 2;
                for (int y = 0; y < moundHeight; y++) {
                    BlockState dirt = (y == moundHeight - 1) ?
                        Blocks.GRASS_BLOCK.defaultBlockState() :
                        Blocks.COARSE_DIRT.defaultBlockState();
                    world.setBlock(basePos.relative(facing, d).above(y), dirt, StructureHelper.SET_FLAGS);
                }
            }

            // Wooden palisade on top
            world.setBlock(basePos.above(3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlock(basePos.above(4), palette.getFenceState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Generate a corner wall segment.
     */
    private void generateCornerSegment(ServerLevel world, BlockPos origin, Direction facing, WallType wallType) {
        // Generate two perpendicular short walls meeting at corner
        Direction left = facing.getCounterClockWise();
        int cornerLength = SEGMENT_LENGTH / 3;

        // Corner tower/post
        for (int y = 0; y < wallType.height + 2; y++) {
            world.setBlock(origin.above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Extending walls
        for (int i = 1; i <= cornerLength; i++) {
            BlockPos pos1 = origin.relative(facing, i);
            BlockPos pos2 = origin.relative(left, i);

            for (int y = 0; y < wallType.height; y++) {
                world.setBlock(pos1.above(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                world.setBlock(pos2.above(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }

        // Corner cap lantern (sitting on the wall top)
        world.setBlock(origin.above(wallType.height + 2),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
    }

    /**
     * Generate a gate segment for village entrance.
     */
    private void generateGateSegment(ServerLevel world, BlockPos origin, Direction facing, WallType wallType) {
        Direction perpendicular = facing.getClockWise();
        int gateWidth = 3;
        int halfWall = (SEGMENT_LENGTH - gateWidth) / 2;

        // Left wall section
        for (int i = -SEGMENT_LENGTH / 2; i < -gateWidth / 2; i++) {
            BlockPos pos = origin.relative(perpendicular, i);
            for (int y = 0; y < wallType.height; y++) {
                world.setBlock(pos.above(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }

        // Right wall section
        for (int i = gateWidth / 2 + 1; i <= SEGMENT_LENGTH / 2; i++) {
            BlockPos pos = origin.relative(perpendicular, i);
            for (int y = 0; y < wallType.height; y++) {
                world.setBlock(pos.above(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }

        // Gate posts (taller)
        BlockPos leftPost = origin.relative(perpendicular, -gateWidth / 2 - 1);
        BlockPos rightPost = origin.relative(perpendicular, gateWidth / 2 + 1);

        for (int y = 0; y < wallType.height + 2; y++) {
            world.setBlock(leftPost.above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(rightPost.above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Arch over gate
        world.setBlock(origin.relative(perpendicular, -1).above(wallType.height), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(origin.relative(perpendicular, 1).above(wallType.height), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(origin.above(wallType.height + 1), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

        // Lanterns on posts (sitting on top)
        world.setBlock(leftPost.above(wallType.height + 2),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        world.setBlock(rightPost.above(wallType.height + 2),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // Clear gate opening
        for (int i = -gateWidth / 2; i <= gateWidth / 2; i++) {
            for (int y = 0; y < wallType.height; y++) {
                world.setBlock(origin.relative(perpendicular, i).above(y), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Path through gate
        for (int i = -gateWidth / 2; i <= gateWidth / 2; i++) {
            world.setBlock(origin.relative(perpendicular, i), Blocks.GRAVEL.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Generate a watch tower segment.
     */
    private void generateTowerSegment(ServerLevel world, BlockPos origin, Direction facing, WallType wallType) {
        // Small 3x3 tower
        int towerRadius = 1;
        int towerHeight = wallType.height + 4;

        // Tower base
        for (int x = -towerRadius; x <= towerRadius; x++) {
            for (int z = -towerRadius; z <= towerRadius; z++) {
                boolean isEdge = Math.abs(x) == towerRadius || Math.abs(z) == towerRadius;

                for (int y = 0; y < towerHeight; y++) {
                    BlockPos pos = origin.offset(x, y, z);
                    if (isEdge) {
                        world.setBlock(pos, palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                    } else if (y == 0 || y == towerHeight - 2) {
                        // Floors
                        world.setBlock(pos, palette.getFloorState(), StructureHelper.SET_FLAGS);
                    } else {
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Crenellations on top
        for (int x = -towerRadius; x <= towerRadius; x++) {
            for (int z = -towerRadius; z <= towerRadius; z++) {
                if (Math.abs(x) == towerRadius || Math.abs(z) == towerRadius) {
                    if ((x + z) % 2 == 0) {
                        world.setBlock(origin.offset(x, towerHeight, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Ladder with proper facing (faces north, back against south wall)
        for (int y = 1; y < towerHeight - 2; y++) {
            world.setBlock(origin.offset(0, y, towerRadius - 1),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }

        // Connecting wall stubs
        Direction perpendicular = facing.getClockWise();
        for (int i = 2; i <= 3; i++) {
            for (int y = 0; y < wallType.height; y++) {
                world.setBlock(origin.relative(perpendicular, i).above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlock(origin.relative(perpendicular, -i).above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
    }

    /**
     * Generate a terminator segment (end cap).
     */
    private void generateTerminatorSegment(ServerLevel world, BlockPos origin, Direction facing, WallType wallType) {
        // Simple end post with short wall stub
        Direction perpendicular = facing.getClockWise();

        // End post
        for (int y = 0; y < wallType.height + 1; y++) {
            world.setBlock(origin.above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Cap decoration lantern (sitting on wall top)
        world.setBlock(origin.above(wallType.height + 1),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // Short wall extension
        for (int i = 1; i <= 2; i++) {
            for (int y = 0; y < wallType.height - 1; y++) {
                world.setBlock(origin.relative(perpendicular, i).above(y),
                    palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                world.setBlock(origin.relative(perpendicular, -i).above(y),
                    palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }
    }

    public static int getSegmentLength() {
        return SEGMENT_LENGTH;
    }

}
