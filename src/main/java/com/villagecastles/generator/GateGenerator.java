package com.villagecastles.generator;

import com.villagecastles.generator.build.Openings;
import com.villagecastles.util.StructureHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
    private final TowerGenerator towerGenerator;

    // Gatehouse dimensions
    private static final int GATE_WIDTH = 5;      // Opening width
    private static final int GATE_HEIGHT = 6;     // Opening height
    private static final int GATEHOUSE_DEPTH = 8; // Depth of gatehouse
    private static final int TOWER_HEIGHT = TowerGenerator.TowerType.GATEHOUSE.height;

    public GateGenerator(BiomePalette palette, Random random) {
        this.palette = palette;
        this.random = random;
        this.towerGenerator = new TowerGenerator(palette, random);
    }

    /**
     * Generate a full gatehouse facing the specified direction.
     */
    public void generate(ServerLevel world, BlockPos center, Direction facing) {
        buildFlankingTowers(world, center, facing);
        buildGatehouseBody(world, center, facing);
        buildArchway(world, center, facing);
        addPortcullisFrame(world, center, facing);
        addGate(world, center, facing);
        addMurderHoles(world, center, facing);
        addDecorations(world, center, facing);
    }

    private void buildFlankingTowers(ServerLevel world, BlockPos center, Direction facing) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        int towerOffset = GATE_WIDTH / 2 + 3;

        // Delegate to TowerGenerator for flanking towers
        towerGenerator.generate(world, center.relative(left, towerOffset), TowerGenerator.TowerType.GATEHOUSE);
        towerGenerator.generate(world, center.relative(right, towerOffset), TowerGenerator.TowerType.GATEHOUSE);
    }

    private void buildGatehouseBody(ServerLevel world, BlockPos center, Direction facing) {
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        int halfWidth = GATE_WIDTH / 2 + 2;
        int halfDepth = GATEHOUSE_DEPTH / 2;

        // Build walls between towers
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int y = 0; y < TOWER_HEIGHT - 4; y++) {
                // Left wall
                BlockPos leftPos = center.relative(facing, d).relative(left, halfWidth).above(y);
                world.setBlock(leftPos, palette.getWallBlockAt(leftPos), StructureHelper.SET_FLAGS);

                // Right wall
                BlockPos rightPos = center.relative(facing, d).relative(right, halfWidth).above(y);
                world.setBlock(rightPos, palette.getWallBlockAt(rightPos), StructureHelper.SET_FLAGS);
            }
        }

        // Roof over passage
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth + 1; w <= halfWidth - 1; w++) {
                BlockPos roofPos = center.relative(facing, d).relative(left, w).above(GATE_HEIGHT + 1);
                world.setBlock(roofPos, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Walkway on top
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                BlockPos walkwayPos = center.relative(facing, d).relative(left, w).above(GATE_HEIGHT + 2);
                world.setBlock(walkwayPos, palette.getFloorState(), StructureHelper.SET_FLAGS);
            }
        }

        // Crenellations on top
        for (int d = -halfDepth; d <= halfDepth; d += 2) {
            BlockPos leftCrenel = center.relative(facing, d).relative(left, halfWidth).above(GATE_HEIGHT + 3);
            BlockPos rightCrenel = center.relative(facing, d).relative(right, halfWidth).above(GATE_HEIGHT + 3);
            world.setBlock(leftCrenel, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(rightCrenel, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }
    }

    private void buildArchway(ServerLevel world, BlockPos center, Direction facing) {
        Direction left = facing.getCounterClockWise();

        int halfWidth = GATE_WIDTH / 2;
        int halfDepth = GATEHOUSE_DEPTH / 2;

        // Clear the passage
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                for (int y = 0; y < GATE_HEIGHT; y++) {
                    BlockPos pos = center.relative(facing, d).relative(left, w).above(y);
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Build arch at entrance and exit
        for (int side = -1; side <= 1; side += 2) {
            BlockPos archBase = center.relative(facing, side * halfDepth);

            // Arch columns
            for (int y = 0; y < GATE_HEIGHT; y++) {
                world.setBlock(archBase.relative(left, halfWidth + 1).above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                world.setBlock(archBase.relative(left, -halfWidth - 1).above(y), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }

            // Arch top (pointed)
            world.setBlock(archBase.relative(left, halfWidth).above(GATE_HEIGHT), palette.getAccentWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(archBase.relative(left, -halfWidth).above(GATE_HEIGHT), palette.getAccentWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(archBase.relative(left, halfWidth - 1).above(GATE_HEIGHT), palette.getAccentWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(archBase.relative(left, -halfWidth + 1).above(GATE_HEIGHT), palette.getAccentWallState(), StructureHelper.SET_FLAGS);
        }

        // Floor of passage
        for (int d = -halfDepth; d <= halfDepth; d++) {
            for (int w = -halfWidth; w <= halfWidth; w++) {
                BlockPos floorPos = center.relative(facing, d).relative(left, w);
                world.setBlock(floorPos, palette.getFloorState(), StructureHelper.SET_FLAGS);
            }
        }
    }

    private void addPortcullisFrame(ServerLevel world, BlockPos center, Direction facing) {
        Direction left = facing.getCounterClockWise();

        int halfWidth = GATE_WIDTH / 2;

        // Portcullis grooves (iron bars as visual placeholder)
        BlockPos portcullisPos = center.relative(facing, 0);

        for (int w = -halfWidth; w <= halfWidth; w++) {
            // Top bar
            world.setBlock(portcullisPos.relative(left, w).above(GATE_HEIGHT - 1), Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Side grooves
        for (int y = 1; y < GATE_HEIGHT - 1; y++) {
            world.setBlock(portcullisPos.relative(left, halfWidth + 1).above(y), Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(portcullisPos.relative(left, -halfWidth - 1).above(y), Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * The gate proper: masonry jambs narrowing the arch, a hung double door, and the portcullis
     * standing raised above it.
     *
     * <p>This used to stack fence gates three high across the full five-block arch: fifteen fence
     * gates in a grid, which is not a thing anyone builds and was the single most conspicuous of
     * the castles' invented patterns. It also meant the gatehouse had no door, which is how twelve
     * of the fifteen templates came to have none anywhere.
     */
    private void addGate(ServerLevel world, BlockPos center, Direction facing) {
        Direction left = facing.getCounterClockWise();

        int halfWidth = GATE_WIDTH / 2;
        BlockPos gatePos = center.relative(facing, -GATEHOUSE_DEPTH / 2);

        // Jambs: bring the five-block arch down to a two-leaf doorway.
        for (int w : new int[]{-halfWidth, halfWidth}) {
            for (int y = 1; y <= GATE_HEIGHT - 1; y++) {
                world.setBlock(gatePos.relative(left, w).above(y),
                    palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Double door in the middle two columns, opening inward.
        Openings.door(world, gatePos.relative(left, 1).above(1), facing,
            palette.door.defaultBlockState(), null, palette.getFloorState(), true);

        // The third column carries the door head, so the opening reads as one arch.
        world.setBlock(gatePos.relative(left, -1).above(1),
            palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(gatePos.relative(left, -1).above(2),
            palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

        // Portcullis, raised: iron bars filling the arch above the door head.
        for (int w = -halfWidth + 1; w <= halfWidth - 1; w++) {
            for (int y = 3; y <= GATE_HEIGHT - 1; y++) {
                world.setBlock(gatePos.relative(left, w).above(y),
                    Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
    }

    private void addMurderHoles(ServerLevel world, BlockPos center, Direction facing) {
        Direction left = facing.getCounterClockWise();

        // Murder holes in ceiling at regular intervals
        int halfWidth = GATE_WIDTH / 2 - 1;
        int halfDepth = GATEHOUSE_DEPTH / 2 - 2;

        for (int d = -halfDepth; d <= halfDepth; d += 3) {
            for (int w = -halfWidth; w <= halfWidth; w += 2) {
                BlockPos holePos = center.relative(facing, d).relative(left, w).above(GATE_HEIGHT + 1);
                world.setBlock(holePos, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
    }

    private void addDecorations(ServerLevel world, BlockPos center, Direction facing) {
        Direction left = facing.getCounterClockWise();

        int halfWidth = GATE_WIDTH / 2 + 2;

        // Lanterns at entrance - place support block then hanging lantern
        BlockPos entrancePos = center.relative(facing, -GATEHOUSE_DEPTH / 2 - 1);
        // Left side
        world.setBlock(entrancePos.relative(left, halfWidth).above(4), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(entrancePos.relative(left, halfWidth).above(3),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        // Right side
        world.setBlock(entrancePos.relative(left, -halfWidth).above(4), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(entrancePos.relative(left, -halfWidth).above(3),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);

        // Lanterns at exit
        BlockPos exitPos = center.relative(facing, GATEHOUSE_DEPTH / 2 + 1);
        world.setBlock(exitPos.relative(left, halfWidth).above(4), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(exitPos.relative(left, halfWidth).above(3),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlock(exitPos.relative(left, -halfWidth).above(4), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(exitPos.relative(left, -halfWidth).above(3),
            palette.light.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);

        // Banner holders (fence posts)
        world.setBlock(entrancePos.relative(left, halfWidth).above(TOWER_HEIGHT - 2), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlock(entrancePos.relative(left, -halfWidth).above(TOWER_HEIGHT - 2), palette.getFenceState(), StructureHelper.SET_FLAGS);
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
