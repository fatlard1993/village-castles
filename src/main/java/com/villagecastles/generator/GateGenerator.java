package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallTorchBlock;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Generates castle gatehouses with:
 * - Main archway
 * - Portcullis frame
 * - Flanking towers
 * - Murder holes (for defenders)
 * - Drawbridge area
 */
public class GateGenerator {

    private final BiomePalette palette;
    private final Random random;

    // Gatehouse dimensions
    private static final int GATE_WIDTH = 5;      // Opening width
    private static final int GATE_HEIGHT = 6;     // Opening height
    private static final int GATEHOUSE_DEPTH = 8; // Depth of gatehouse
    private static final int TOWER_HEIGHT = 14;   // Height of flanking towers

    public GateGenerator(BiomePalette palette, Random random) {
        this.palette = palette;
        this.random = random;
    }

    /**
     * Generate a full gatehouse facing the specified direction.
     */
    public void generate(ServerWorld world, BlockPos center, Direction facing) {
        // Build flanking towers
        buildFlankingTowers(world, center, facing);

        // Build gatehouse structure
        buildGatehouseBody(world, center, facing);

        // Build archway
        buildArchway(world, center, facing);

        // Add portcullis frame
        addPortcullisFrame(world, center, facing);

        // Add gate
        addGate(world, center, facing);

        // Add murder holes
        addMurderHoles(world, center, facing);

        // Add decorative elements
        addDecorations(world, center, facing);
    }

    private void buildFlankingTowers(ServerWorld world, BlockPos center, Direction facing) {
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        int towerOffset = GATE_WIDTH / 2 + 3;

        // Left tower
        BlockPos leftTowerCenter = center.offset(left, towerOffset);
        buildTower(world, leftTowerCenter);

        // Right tower
        BlockPos rightTowerCenter = center.offset(right, towerOffset);
        buildTower(world, rightTowerCenter);
    }

    private void buildTower(ServerWorld world, BlockPos center) {
        int radius = 3;

        // Foundation
        for (int x = -radius - 1; x <= radius + 1; x++) {
            for (int z = -radius - 1; z <= radius + 1; z++) {
                for (int y = -2; y < 0; y++) {
                    world.setBlockState(center.add(x, y, z), Blocks.COBBLESTONE.getDefaultState());
                }
            }
        }

        // Tower walls (cylindrical)
        for (int y = 0; y < TOWER_HEIGHT; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    double dist = Math.sqrt(x * x + z * z);
                    boolean onEdge = dist <= radius && dist > radius - 1.5;
                    boolean inside = dist < radius - 1.5;

                    if (onEdge) {
                        world.setBlockState(center.add(x, y, z), palette.getRandomWallBlock(random).getDefaultState());
                    } else if (inside) {
                        world.setBlockState(center.add(x, y, z), Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Floors
        for (int floor = 0; floor < TOWER_HEIGHT; floor += 5) {
            for (int x = -radius + 1; x < radius; x++) {
                for (int z = -radius + 1; z < radius; z++) {
                    if (Math.sqrt(x * x + z * z) < radius - 1) {
                        world.setBlockState(center.add(x, floor, z), palette.getFloorState());
                    }
                }
            }
        }

        // Crenellations
        StructureHelper.addCircularCrenellations(world, center, radius, TOWER_HEIGHT, palette.getPrimaryWallState());

        // Conical roof
        for (int y = 1; y <= 3; y++) {
            int roofRadius = radius - y + 1;
            for (int x = -roofRadius; x <= roofRadius; x++) {
                for (int z = -roofRadius; z <= roofRadius; z++) {
                    if (Math.sqrt(x * x + z * z) <= roofRadius) {
                        world.setBlockState(center.add(x, TOWER_HEIGHT + y, z), palette.getRoofState());
                    }
                }
            }
        }
    }

    private void buildGatehouseBody(ServerWorld world, BlockPos center, Direction facing) {
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        int halfWidth = GATE_WIDTH / 2 + 2;
        int halfDepth = GATEHOUSE_DEPTH / 2;

        // Build walls between towers
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int y = 0; y < TOWER_HEIGHT - 4; y++) {
                // Left wall
                BlockPos leftPos = center.offset(facing, d).offset(left, halfWidth).up(y);
                world.setBlockState(leftPos, palette.getRandomWallBlock(random).getDefaultState());

                // Right wall
                BlockPos rightPos = center.offset(facing, d).offset(right, halfWidth).up(y);
                world.setBlockState(rightPos, palette.getRandomWallBlock(random).getDefaultState());
            }
        }

        // Roof over passage
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth + 1; w <= halfWidth - 1; w++) {
                BlockPos roofPos = center.offset(facing, d).offset(left, w).up(GATE_HEIGHT + 1);
                world.setBlockState(roofPos, palette.getPrimaryWallState());
            }
        }

        // Walkway on top
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                BlockPos walkwayPos = center.offset(facing, d).offset(left, w).up(GATE_HEIGHT + 2);
                world.setBlockState(walkwayPos, palette.getFloorState());
            }
        }

        // Crenellations on top
        for (int d = -halfDepth; d <= halfDepth; d += 2) {
            BlockPos leftCrenel = center.offset(facing, d).offset(left, halfWidth).up(GATE_HEIGHT + 3);
            BlockPos rightCrenel = center.offset(facing, d).offset(right, halfWidth).up(GATE_HEIGHT + 3);
            world.setBlockState(leftCrenel, palette.getPrimaryWallState());
            world.setBlockState(rightCrenel, palette.getPrimaryWallState());
        }
    }

    private void buildArchway(ServerWorld world, BlockPos center, Direction facing) {
        Direction left = facing.rotateYCounterclockwise();

        int halfWidth = GATE_WIDTH / 2;
        int halfDepth = GATEHOUSE_DEPTH / 2;

        // Clear the passage
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                for (int y = 0; y < GATE_HEIGHT; y++) {
                    BlockPos pos = center.offset(facing, d).offset(left, w).up(y);
                    world.setBlockState(pos, Blocks.AIR.getDefaultState());
                }
            }
        }

        // Build arch at entrance and exit
        for (int side = -1; side <= 1; side += 2) {
            BlockPos archBase = center.offset(facing, side * halfDepth);

            // Arch columns
            for (int y = 0; y < GATE_HEIGHT; y++) {
                world.setBlockState(archBase.offset(left, halfWidth + 1).up(y), palette.getPrimaryWallState());
                world.setBlockState(archBase.offset(left, -halfWidth - 1).up(y), palette.getPrimaryWallState());
            }

            // Arch top (pointed)
            world.setBlockState(archBase.offset(left, halfWidth).up(GATE_HEIGHT), palette.getAccentWallState());
            world.setBlockState(archBase.offset(left, -halfWidth).up(GATE_HEIGHT), palette.getAccentWallState());
            world.setBlockState(archBase.offset(left, halfWidth - 1).up(GATE_HEIGHT), palette.getAccentWallState());
            world.setBlockState(archBase.offset(left, -halfWidth + 1).up(GATE_HEIGHT), palette.getAccentWallState());
        }

        // Floor of passage
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                BlockPos floorPos = center.offset(facing, d).offset(left, w);
                world.setBlockState(floorPos, palette.getFloorState());
            }
        }
    }

    private void addPortcullisFrame(ServerWorld world, BlockPos center, Direction facing) {
        Direction left = facing.rotateYCounterclockwise();

        int halfWidth = GATE_WIDTH / 2;

        // Portcullis grooves (iron bars as visual placeholder)
        BlockPos portcullisPos = center.offset(facing, 0);

        for (int w = -halfWidth; w <= halfWidth; w++) {
            // Top bar
            world.setBlockState(portcullisPos.offset(left, w).up(GATE_HEIGHT - 1), Blocks.IRON_BARS.getDefaultState());
        }

        // Side grooves
        for (int y = 1; y < GATE_HEIGHT - 1; y++) {
            world.setBlockState(portcullisPos.offset(left, halfWidth + 1).up(y), Blocks.IRON_BARS.getDefaultState());
            world.setBlockState(portcullisPos.offset(left, -halfWidth - 1).up(y), Blocks.IRON_BARS.getDefaultState());
        }
    }

    private void addGate(ServerWorld world, BlockPos center, Direction facing) {
        Direction left = facing.rotateYCounterclockwise();

        int halfWidth = GATE_WIDTH / 2;

        // Fence gates at entrance
        BlockPos gatePos = center.offset(facing, -GATEHOUSE_DEPTH / 2);
        BlockState gate = Blocks.OAK_FENCE_GATE.getDefaultState().with(FenceGateBlock.FACING, facing);

        for (int w = -halfWidth; w <= halfWidth; w++) {
            world.setBlockState(gatePos.offset(left, w).up(1), gate);
        }
    }

    private void addMurderHoles(ServerWorld world, BlockPos center, Direction facing) {
        Direction left = facing.rotateYCounterclockwise();

        // Murder holes in ceiling at regular intervals
        int halfWidth = GATE_WIDTH / 2 - 1;
        int halfDepth = GATEHOUSE_DEPTH / 2 - 2;

        for (int d = -halfDepth; d <= halfDepth; d += 3) {
            for (int w = -halfWidth; w <= halfWidth; w += 2) {
                BlockPos holePos = center.offset(facing, d).offset(left, w).up(GATE_HEIGHT + 1);
                world.setBlockState(holePos, Blocks.AIR.getDefaultState());
            }
        }
    }

    private void addDecorations(ServerWorld world, BlockPos center, Direction facing) {
        Direction left = facing.rotateYCounterclockwise();

        int halfWidth = GATE_WIDTH / 2 + 2;

        // Lanterns at entrance - place support block then hanging lantern
        BlockPos entrancePos = center.offset(facing, -GATEHOUSE_DEPTH / 2 - 1);
        // Left side
        world.setBlockState(entrancePos.offset(left, halfWidth).up(4), palette.getPlanksState());
        world.setBlockState(entrancePos.offset(left, halfWidth).up(3),
            palette.light.getDefaultState().with(LanternBlock.HANGING, true));
        // Right side
        world.setBlockState(entrancePos.offset(left, -halfWidth).up(4), palette.getPlanksState());
        world.setBlockState(entrancePos.offset(left, -halfWidth).up(3),
            palette.light.getDefaultState().with(LanternBlock.HANGING, true));

        // Lanterns at exit
        BlockPos exitPos = center.offset(facing, GATEHOUSE_DEPTH / 2 + 1);
        world.setBlockState(exitPos.offset(left, halfWidth).up(4), palette.getPlanksState());
        world.setBlockState(exitPos.offset(left, halfWidth).up(3),
            palette.light.getDefaultState().with(LanternBlock.HANGING, true));
        world.setBlockState(exitPos.offset(left, -halfWidth).up(4), palette.getPlanksState());
        world.setBlockState(exitPos.offset(left, -halfWidth).up(3),
            palette.light.getDefaultState().with(LanternBlock.HANGING, true));

        // Banner holders (fence posts)
        world.setBlockState(entrancePos.offset(left, halfWidth).up(TOWER_HEIGHT - 2), palette.getFenceState());
        world.setBlockState(entrancePos.offset(left, -halfWidth).up(TOWER_HEIGHT - 2), palette.getFenceState());
    }

    /**
     * Get the width of passage for wall connections.
     */
    public static int getGateWidth() {
        return GATE_WIDTH;
    }

    /**
     * Get full gatehouse width including towers.
     */
    public static int getFullWidth() {
        return GATE_WIDTH + 12; // Gate + 2 towers
    }

    /**
     * Get gatehouse depth.
     */
    public static int getDepth() {
        return GATEHOUSE_DEPTH;
    }
}
