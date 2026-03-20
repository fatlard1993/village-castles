package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.JigsawBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.entity.JigsawBlockEntity;
import net.minecraft.block.enums.Orientation;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Identifier;
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

        // Place jigsaw connectors for wall chaining
        placeWallJigsawConnectors(world, origin, facing, segmentType);

        // Post-generation pass: fix connection states for fences, walls, iron bars, etc.
        BlockPos boundsMin = origin.add(-5, -2, -3);
        BlockPos boundsMax = origin.add(5, 12, 3);
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
            palette.light.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
    }

    private void generatePalisadeWall(ServerWorld world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.rotateYClockwise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.offset(perpendicular, i);

            // Foundation
            world.setBlockState(basePos.down(), Blocks.COBBLESTONE.getDefaultState(), StructureHelper.SET_FLAGS);

            // Log posts (alternating heights for visual interest)
            int postHeight = (Math.abs(i) % 2 == 0) ? 4 : 3;
            for (int y = 0; y < postHeight; y++) {
                world.setBlockState(basePos.up(y), palette.getLogState(), StructureHelper.SET_FLAGS);
            }

            // Fence on top
            if (postHeight == 3) {
                world.setBlockState(basePos.up(3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            }
        }

        // Pointed tops on corner posts (fence post tip)
        world.setBlockState(origin.offset(perpendicular, -halfLength).up(4),
            palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlockState(origin.offset(perpendicular, halfLength).up(4),
            palette.getFenceState(), StructureHelper.SET_FLAGS);
    }

    private void generateStoneWall(ServerWorld world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.rotateYClockwise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.offset(perpendicular, i);

            // Foundation
            for (int d = 0; d < 2; d++) {
                world.setBlockState(basePos.offset(facing, d).down(), Blocks.COBBLESTONE.getDefaultState(), StructureHelper.SET_FLAGS);
            }

            // Wall body
            for (int y = 0; y < 4; y++) {
                BlockState wallBlock = palette.getRandomWallBlock(random);
                world.setBlockState(basePos.up(y), wallBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(basePos.offset(facing, 1).up(y), wallBlock, StructureHelper.SET_FLAGS);
            }

            // Crenellations (alternating)
            if (Math.abs(i) % 2 == 0) {
                world.setBlockState(basePos.up(4), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }

            // Walkway
            world.setBlockState(basePos.offset(facing, 1).up(3), palette.getFloorState(), StructureHelper.SET_FLAGS);
        }
    }

    private void generateAdobeWall(ServerWorld world, BlockPos origin, Direction facing, int halfLength) {
        Direction perpendicular = facing.rotateYClockwise();

        for (int i = -halfLength; i <= halfLength; i++) {
            BlockPos basePos = origin.offset(perpendicular, i);

            // Foundation
            world.setBlockState(basePos.down(), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

            // Wall body (tapered - wider at base)
            for (int y = 0; y < 3; y++) {
                world.setBlockState(basePos.up(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                if (y < 2) {
                    world.setBlockState(basePos.offset(facing, 1).up(y), palette.getSecondaryWallState(), StructureHelper.SET_FLAGS);
                }
            }

            // Decorative top edge
            if (Math.abs(i) % 3 == 0) {
                world.setBlockState(basePos.up(3), palette.getAccentWallState(), StructureHelper.SET_FLAGS);
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
                    world.setBlockState(basePos.offset(facing, d).up(y), dirt, StructureHelper.SET_FLAGS);
                }
            }

            // Wooden palisade on top
            world.setBlockState(basePos.up(3), palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlockState(basePos.up(4), palette.getFenceState(), StructureHelper.SET_FLAGS);
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
            world.setBlockState(origin.up(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Extending walls
        for (int i = 1; i <= cornerLength; i++) {
            BlockPos pos1 = origin.offset(facing, i);
            BlockPos pos2 = origin.offset(left, i);

            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(pos1.up(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                world.setBlockState(pos2.up(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }

        // Corner cap lantern (sitting on the wall top)
        world.setBlockState(origin.up(wallType.height + 2),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
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
                world.setBlockState(pos.up(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }

        // Right wall section
        for (int i = gateWidth / 2 + 1; i <= SEGMENT_LENGTH / 2; i++) {
            BlockPos pos = origin.offset(perpendicular, i);
            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(pos.up(y), palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }

        // Gate posts (taller)
        BlockPos leftPost = origin.offset(perpendicular, -gateWidth / 2 - 1);
        BlockPos rightPost = origin.offset(perpendicular, gateWidth / 2 + 1);

        for (int y = 0; y < wallType.height + 2; y++) {
            world.setBlockState(leftPost.up(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlockState(rightPost.up(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Arch over gate
        world.setBlockState(origin.offset(perpendicular, -1).up(wallType.height), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlockState(origin.offset(perpendicular, 1).up(wallType.height), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlockState(origin.up(wallType.height + 1), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

        // Lanterns on posts (sitting on top)
        world.setBlockState(leftPost.up(wallType.height + 2),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        world.setBlockState(rightPost.up(wallType.height + 2),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // Clear gate opening
        for (int i = -gateWidth / 2; i <= gateWidth / 2; i++) {
            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(origin.offset(perpendicular, i).up(y), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Path through gate
        for (int i = -gateWidth / 2; i <= gateWidth / 2; i++) {
            world.setBlockState(origin.offset(perpendicular, i), Blocks.GRAVEL.getDefaultState(), StructureHelper.SET_FLAGS);
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
                        world.setBlockState(pos, palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                    } else if (y == 0 || y == towerHeight - 2) {
                        // Floors
                        world.setBlockState(pos, palette.getFloorState(), StructureHelper.SET_FLAGS);
                    } else {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Crenellations on top
        for (int x = -towerRadius; x <= towerRadius; x++) {
            for (int z = -towerRadius; z <= towerRadius; z++) {
                if (Math.abs(x) == towerRadius || Math.abs(z) == towerRadius) {
                    if ((x + z) % 2 == 0) {
                        world.setBlockState(origin.add(x, towerHeight, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Ladder with proper facing (faces north, back against south wall)
        for (int y = 1; y < towerHeight - 2; y++) {
            world.setBlockState(origin.add(0, y, towerRadius - 1),
                Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }

        // Connecting wall stubs
        Direction perpendicular = facing.rotateYClockwise();
        for (int i = 2; i <= 3; i++) {
            for (int y = 0; y < wallType.height; y++) {
                world.setBlockState(origin.offset(perpendicular, i).up(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlockState(origin.offset(perpendicular, -i).up(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
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
            world.setBlockState(origin.up(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Cap decoration lantern (sitting on wall top)
        world.setBlockState(origin.up(wallType.height + 1),
            palette.light.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // Short wall extension
        for (int i = 1; i <= 2; i++) {
            for (int y = 0; y < wallType.height - 1; y++) {
                world.setBlockState(origin.offset(perpendicular, i).up(y),
                    palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
                world.setBlockState(origin.offset(perpendicular, -i).up(y),
                    palette.getRandomWallBlock(random), StructureHelper.SET_FLAGS);
            }
        }
    }

    public static int getSegmentLength() {
        return SEGMENT_LENGTH;
    }

    /**
     * Place jigsaw blocks at connection points of a wall segment.
     * These allow wall segments to chain together via jigsaw structure generation.
     *
     * All positions assume NORTH facing (perpendicular = EAST, positive X).
     * The facing parameter rotates these positions for other orientations.
     */
    private void placeWallJigsawConnectors(ServerWorld world, BlockPos origin, Direction facing, SegmentType segmentType) {
        Direction perpendicular = facing.rotateYClockwise();

        RegistryKey<StructurePool> wallChainPool = RegistryKey.of(
            RegistryKeys.TEMPLATE_POOL,
            Identifier.of("villagecastles", "village_walls/" + palette.id + "/chain")
        );

        switch (segmentType) {
            case STRAIGHT -> {
                // East end connector
                placeWallJigsaw(world, origin.offset(perpendicular, 5), perpendicular, wallChainPool);
                // West end connector
                placeWallJigsaw(world, origin.offset(perpendicular, -5), perpendicular.getOpposite(), wallChainPool);
            }
            case CORNER -> {
                // West end connector (along perpendicular axis, negative direction)
                placeWallJigsaw(world, origin.offset(perpendicular, -3), perpendicular.getOpposite(), wallChainPool);
                // North end connector (along facing axis)
                placeWallJigsaw(world, origin.offset(facing, 3), facing, wallChainPool);
            }
            case GATE -> {
                // East end connector
                placeWallJigsaw(world, origin.offset(perpendicular, 5), perpendicular, wallChainPool);
                // West end connector
                placeWallJigsaw(world, origin.offset(perpendicular, -5), perpendicular.getOpposite(), wallChainPool);
                // Through-gate street connector (facing into village)
                RegistryKey<StructurePool> streetsPool = RegistryKey.of(
                    RegistryKeys.TEMPLATE_POOL,
                    Identifier.of("minecraft", "village/" + palette.id + "/streets")
                );
                placeStreetJigsaw(world, origin.offset(facing, 1), facing, streetsPool);
            }
            case TOWER -> {
                // East end connector
                placeWallJigsaw(world, origin.offset(perpendicular, 4), perpendicular, wallChainPool);
                // West end connector
                placeWallJigsaw(world, origin.offset(perpendicular, -4), perpendicular.getOpposite(), wallChainPool);
            }
            case TERMINATOR -> {
                // West end only (one-sided, terminates the chain)
                placeWallJigsaw(world, origin.offset(perpendicular, -3), perpendicular.getOpposite(), wallChainPool);
            }
        }
    }

    /**
     * Place a jigsaw block configured for wall-to-wall chaining.
     */
    private void placeWallJigsaw(ServerWorld world, BlockPos pos, Direction facing,
                                  RegistryKey<StructurePool> targetPool) {
        Orientation orientation = orientationFromFacing(facing);

        world.setBlockState(pos, Blocks.JIGSAW.getDefaultState()
            .with(JigsawBlock.ORIENTATION, orientation),
            StructureHelper.SET_FLAGS);

        if (world.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
            jigsaw.setPool(targetPool);
            jigsaw.setName(Identifier.of("villagecastles", "wall_end"));
            jigsaw.setTarget(Identifier.of("villagecastles", "wall_end"));
            jigsaw.setFinalState("minecraft:air");
            jigsaw.setJoint(JigsawBlockEntity.Joint.ALIGNED);
            jigsaw.markDirty();
        }
    }

    /**
     * Place a jigsaw block configured for gate-to-street connection.
     */
    private void placeStreetJigsaw(ServerWorld world, BlockPos pos, Direction facing,
                                    RegistryKey<StructurePool> targetPool) {
        Orientation orientation = orientationFromFacing(facing);

        world.setBlockState(pos, Blocks.JIGSAW.getDefaultState()
            .with(JigsawBlock.ORIENTATION, orientation),
            StructureHelper.SET_FLAGS);

        if (world.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
            jigsaw.setPool(targetPool);
            jigsaw.setName(Identifier.of("minecraft", "bottom"));
            jigsaw.setTarget(Identifier.of("minecraft", "bottom"));
            jigsaw.setFinalState("minecraft:air");
            jigsaw.setJoint(JigsawBlockEntity.Joint.ROLLABLE);
            jigsaw.markDirty();
        }
    }

    private static Orientation orientationFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> Orientation.NORTH_UP;
            case SOUTH -> Orientation.SOUTH_UP;
            case EAST -> Orientation.EAST_UP;
            case WEST -> Orientation.WEST_UP;
            default -> Orientation.NORTH_UP;
        };
    }
}
