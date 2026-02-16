package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Generates village perimeter wall segments.
 * These are smaller, village-scale defensive structures that connect via jigsaw.
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
    public void generate(ServerWorld world, BlockPos origin, Direction facing, SegmentType segmentType) {
        WallType wallType = getWallTypeForPalette();

        switch (segmentType) {
            case STRAIGHT -> generateStraightSegment(world, origin, facing, wallType);
            case CORNER -> generateCornerSegment(world, origin, facing, wallType);
            case GATE -> generateGateSegment(world, origin, facing, wallType);
            case TOWER -> generateTowerSegment(world, origin, facing, wallType);
            case TERMINATOR -> generateTerminatorSegment(world, origin, facing, wallType);
        }
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
    private void generateStraightSegment(ServerWorld world, BlockPos origin, Direction facing, WallType wallType) {
        Direction perpendicular = facing.rotateYClockwise();
        int halfLength = SEGMENT_LENGTH / 2;

        switch (wallType) {
            case PALISADE -> generatePalisadeWall(world, origin, facing, halfLength);
            case STONE -> generateStoneWall(world, origin, facing, halfLength);
            case ADOBE -> generateAdobeWall(world, origin, facing, halfLength);
            case RAMPART -> generateRampartWall(world, origin, facing, halfLength);
        }

        // Add lantern at midpoint (sitting on top of wall)
        world.setBlockState(origin.up(wallType.height),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false));
    }

    private void generatePalisadeWall(ServerWorld world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.rotateYClockwise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.offset(perpendicular, i);

            // Foundation
            world.setBlockState(basePos.down(), Blocks.COBBLESTONE.getDefaultState());

            // Log posts (alternating heights for visual interest)
            int postHeight = (Math.abs(i) % 2 == 0) ? 4 : 3;
            for (int y = 0; y < postHeight; y++) {
                world.setBlockState(basePos.up(y), palette.getLogState());
            }

            // Fence on top
            if (postHeight == 3) {
                world.setBlockState(basePos.up(3), palette.getFenceState());
            }
        }

        // Pointed tops on corner posts
        world.setBlockState(origin.offset(perpendicular, -halfLength).up(4),
            Blocks.POINTED_DRIPSTONE.getDefaultState());
        world.setBlockState(origin.offset(perpendicular, halfLength).up(4),
            Blocks.POINTED_DRIPSTONE.getDefaultState());
    }

    private void generateStoneWall(ServerWorld world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.rotateYClockwise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.offset(perpendicular, i);

            // Foundation
            for (int d = 0; d < 2; d++) {
                world.setBlockState(basePos.offset(facing, d).down(), Blocks.COBBLESTONE.getDefaultState());
            }

            // Wall body
            for (int y = 0; y < 4; y++) {
                BlockState wallBlock = palette.getRandomWallBlock(random).getDefaultState();
                world.setBlockState(basePos.up(y), wallBlock);
                world.setBlockState(basePos.offset(facing, 1).up(y), wallBlock);
            }

            // Crenellations (alternating)
            if (Math.abs(i) % 2 == 0) {
                world.setBlockState(basePos.up(4), palette.getPrimaryWallState());
            }

            // Walkway
            world.setBlockState(basePos.offset(facing, 1).up(3), palette.getFloorState());
        }
    }

    private void generateAdobeWall(ServerWorld world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.rotateYClockwise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.offset(perpendicular, i);

            // Foundation
            world.setBlockState(basePos.down(), palette.getPrimaryWallState());

            // Wall body (tapered - wider at base)
            for (int y = 0; y < 3; y++) {
                world.setBlockState(basePos.up(y), palette.getPrimaryWallState());
                if (y < 2) {
                    world.setBlockState(basePos.offset(facing, 1).up(y), palette.getSecondaryWallState());
                }
            }

            // Decorative top edge
            if (Math.abs(i) % 3 == 0) {
                world.setBlockState(basePos.up(3), palette.getAccentWallState());
            }
        }
    }

    private void generateRampartWall(ServerWorld world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.rotateYClockwise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.offset(perpendicular, i);

            // Earth mound (coarse dirt/grass)
            for (int d = -1; d <= 1; d++) {
                int moundHeight = (d == 0) ? 3 : 2;
                for (int y = 0; y < moundHeight; y++) {
                    BlockState dirt = (y == moundHeight - 1) ?
                        Blocks.GRASS_BLOCK.getDefaultState() :
                        Blocks.COARSE_DIRT.getDefaultState();
                    world.setBlockState(basePos.offset(facing, d).up(y), dirt);
                }
            }

            // Wooden palisade on top
            world.setBlockState(basePos.up(3), palette.getFenceState());
            world.setBlockState(basePos.up(4), palette.getFenceState());
        }
    }

    /**
     * Generate a corner wall segment.
     */
    private void generateCornerSegment(ServerWorld world, BlockPos origin, Direction facing, WallType wallType) {
        // Generate two perpendicular short walls meeting at corner
        Direction left = facing.rotateYCounterclockwise();
        int cornerLength = SEGMENT_LENGTH / 3;

        // Corner tower/post
        for (int y = 0; y < wallType.height + 2; y++) {
            world.setBlockState(origin.up(y), palette.getPrimaryWallState());
        }

        // Extending walls
        for (int i = 1; i <= cornerLength; i++) {
            BlockPos pos1 = origin.offset(facing, i);
            BlockPos pos2 = origin.offset(left, i);

            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(pos1.up(y), palette.getRandomWallBlock(random).getDefaultState());
                world.setBlockState(pos2.up(y), palette.getRandomWallBlock(random).getDefaultState());
            }
        }

        // Corner cap lantern (sitting on the wall top)
        world.setBlockState(origin.up(wallType.height + 2),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false));
    }

    /**
     * Generate a gate segment for village entrance.
     */
    private void generateGateSegment(ServerWorld world, BlockPos origin, Direction facing, WallType wallType) {
        Direction perpendicular = facing.rotateYClockwise();
        int gateWidth = 3;
        int halfWall = (SEGMENT_LENGTH - gateWidth) / 2;

        // Left wall section
        for (int i = -SEGMENT_LENGTH / 2; i < -gateWidth / 2; i++) {
            BlockPos pos = origin.offset(perpendicular, i);
            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(pos.up(y), palette.getRandomWallBlock(random).getDefaultState());
            }
        }

        // Right wall section
        for (int i = gateWidth / 2 + 1; i <= SEGMENT_LENGTH / 2; i++) {
            BlockPos pos = origin.offset(perpendicular, i);
            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(pos.up(y), palette.getRandomWallBlock(random).getDefaultState());
            }
        }

        // Gate posts (taller)
        BlockPos leftPost = origin.offset(perpendicular, -gateWidth / 2 - 1);
        BlockPos rightPost = origin.offset(perpendicular, gateWidth / 2 + 1);

        for (int y = 0; y < wallType.height + 2; y++) {
            world.setBlockState(leftPost.up(y), palette.getPrimaryWallState());
            world.setBlockState(rightPost.up(y), palette.getPrimaryWallState());
        }

        // Arch over gate
        world.setBlockState(origin.offset(perpendicular, -1).up(wallType.height), palette.getPrimaryWallState());
        world.setBlockState(origin.offset(perpendicular, 1).up(wallType.height), palette.getPrimaryWallState());
        world.setBlockState(origin.up(wallType.height + 1), palette.getPrimaryWallState());

        // Lanterns on posts (sitting on top)
        world.setBlockState(leftPost.up(wallType.height + 2),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false));
        world.setBlockState(rightPost.up(wallType.height + 2),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false));

        // Clear gate opening
        for (int i = -gateWidth / 2; i <= gateWidth / 2; i++) {
            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(origin.offset(perpendicular, i).up(y), Blocks.AIR.getDefaultState());
            }
        }

        // Path through gate
        for (int i = -gateWidth / 2; i <= gateWidth / 2; i++) {
            world.setBlockState(origin.offset(perpendicular, i), Blocks.GRAVEL.getDefaultState());
        }
    }

    /**
     * Generate a watch tower segment.
     */
    private void generateTowerSegment(ServerWorld world, BlockPos origin, Direction facing, WallType wallType) {
        // Small 3x3 tower
        int towerRadius = 1;
        int towerHeight = wallType.height + 4;

        // Tower base
        for (int x = -towerRadius; x <= towerRadius; x++) {
            for (int z = -towerRadius; z <= towerRadius; z++) {
                boolean isEdge = Math.abs(x) == towerRadius || Math.abs(z) == towerRadius;

                for (int y = 0; y < towerHeight; y++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (isEdge) {
                        world.setBlockState(pos, palette.getRandomWallBlock(random).getDefaultState());
                    } else if (y == 0 || y == towerHeight - 2) {
                        // Floors
                        world.setBlockState(pos, palette.getFloorState());
                    } else {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Crenellations on top
        for (int x = -towerRadius; x <= towerRadius; x++) {
            for (int z = -towerRadius; z <= towerRadius; z++) {
                if (Math.abs(x) == towerRadius || Math.abs(z) == towerRadius) {
                    if ((x + z) % 2 == 0) {
                        world.setBlockState(origin.add(x, towerHeight, z), palette.getPrimaryWallState());
                    }
                }
            }
        }

        // Ladder with proper facing (faces north, back against south wall)
        for (int y = 1; y < towerHeight - 2; y++) {
            world.setBlockState(origin.add(0, y, towerRadius - 1),
                Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH));
        }

        // Connecting wall stubs
        Direction perpendicular = facing.rotateYClockwise();
        for (int i = 2; i <= 3; i++) {
            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(origin.offset(perpendicular, i).up(y), palette.getPrimaryWallState());
                world.setBlockState(origin.offset(perpendicular, -i).up(y), palette.getPrimaryWallState());
            }
        }
    }

    /**
     * Generate a terminator segment (end cap).
     */
    private void generateTerminatorSegment(ServerWorld world, BlockPos origin, Direction facing, WallType wallType) {
        // Simple end post with short wall stub
        Direction perpendicular = facing.rotateYClockwise();

        // End post
        for (int y = 0; y < wallType.height + 1; y++) {
            world.setBlockState(origin.up(y), palette.getPrimaryWallState());
        }

        // Cap decoration lantern (sitting on wall top)
        world.setBlockState(origin.up(wallType.height + 1),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false));

        // Short wall extension
        for (int i = 1; i <= 2; i++) {
            for (int y = 0; y < wallType.height - 1; y++) {
                world.setBlockState(origin.offset(perpendicular, i).up(y),
                    palette.getRandomWallBlock(random).getDefaultState());
                world.setBlockState(origin.offset(perpendicular, -i).up(y),
                    palette.getRandomWallBlock(random).getDefaultState());
            }
        }
    }

    public static int getSegmentLength() {
        return SEGMENT_LENGTH;
    }
}
