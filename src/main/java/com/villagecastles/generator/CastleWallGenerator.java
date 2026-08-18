package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.Random;

/**
 * Generates castle curtain walls with walkways, crenellations, and defensive features.
 * These are the fortress-scale walls connecting towers in a full castle.
 * Not to be confused with VillageWallGenerator, which produces village perimeter walls.
 */
public class CastleWallGenerator {

    private final BiomePalette palette;
    private final Random random;

    // Wall dimensions
    private static final int WALL_HALF_WIDTH = 1; // Wall extends -1 to +1 from center (3 blocks total)
    private static final int DEFAULT_WALL_HEIGHT = 8;
    private static final int WALKWAY_HEIGHT = 6;

    // Wall extends from -WALL_HALF_WIDTH to +WALL_HALF_WIDTH perpendicular to facing.
    // Total thickness = WALL_HALF_WIDTH * 2 + 1 = 3 blocks.

    public CastleWallGenerator(BiomePalette palette, Random random) {
        this.palette = palette;
        this.random = random;
    }

    /**
     * Generate a wall segment between two points.
     */
    public void generate(ServerLevel world, BlockPos start, BlockPos end) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();

        // Guard: wall interpolation only works for axis-aligned segments.
        // Diagonal walls would produce gaps from integer truncation.
        if (dx != 0 && dz != 0) {
            throw new IllegalArgumentException(
                "Wall must be axis-aligned (diagonal from " + start.toShortString() + " to " + end.toShortString() + ")");
        }

        int length = Math.max(Math.abs(dx), Math.abs(dz));

        if (length == 0) return;

        // Determine wall direction
        Direction facing;
        if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        // Build wall block by block
        for (int i = 0; i <= length; i++) {
            double t = (double) i / length;
            int x = start.getX() + (int) (dx * t);
            int z = start.getZ() + (int) (dz * t);

            BlockPos basePos = new BlockPos(x, start.getY(), z);
            buildWallSection(world, basePos, facing, i);
        }
    }

    /**
     * Build a single wall section (vertical slice).
     */
    private void buildWallSection(ServerLevel world, BlockPos base, Direction facing, int sectionIndex) {
        Direction perpendicular = facing.getClockWise();

        // No foundation: CastleGroundsPiece underfills the footprint at placement time.

        // Main wall body
        for (int y = 0; y < getWallHeight(); y++) {
            for (int p = -WALL_HALF_WIDTH; p <= WALL_HALF_WIDTH; p++) {
                BlockPos wallPos = base.relative(perpendicular, p).above(y);
                BlockState wallBlock;
                if (palette == BiomePalette.SAVANNA) {
                    // Chevron/zigzag pattern: alternate primary and secondary wall blocks
                    // using position hash + y offset to create diagonal stripes
                    int positionAlongWall = base.getX() + base.getZ(); // works for axis-aligned walls
                    int chevronPhase = (positionAlongWall + y) % 4;
                    wallBlock = (chevronPhase < 2)
                        ? palette.getPrimaryWallState()
                        : palette.getSecondaryWallState();
                } else {
                    wallBlock = palette.getWallBlockAt(wallPos);
                }
                world.setBlock(wallPos, wallBlock, StructureHelper.SET_FLAGS);
            }
        }

        // The wall-walk is the top of the wall itself. An earlier revision carved a covered
        // passage into the wall at WALKWAY_HEIGHT and left the courses above it solid, which
        // gave the defenders a one-block-tall crawlspace and no way onto the parapet.
        int wallTop = getWallHeight() - 1;

        // Merlons on the outer edge, alternating. Parity comes from the section index, not from
        // the section's world coordinates: summing absolute X and Z made the pattern depend on
        // where in the world the castle generated, and Java's % leaves a negative sum negative,
        // so the two halves of a wall that straddled an axis crenellated out of phase.
        if (sectionIndex % 2 == 0) {
            world.setBlock(base.relative(perpendicular, -WALL_HALF_WIDTH).above(wallTop + 1),
                palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Continuous railing on the inner edge so the walk is not a fall hazard.
        world.setBlock(base.relative(perpendicular, WALL_HALF_WIDTH).above(wallTop + 1),
            palette.getFenceState(), StructureHelper.SET_FLAGS);
    }

    /**
     * Generate a wall with arrow slits at intervals.
     */
    public void generateWithArrowSlits(ServerLevel world, BlockPos start, BlockPos end, int slitInterval) {
        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int length = Math.max(Math.abs(dx), Math.abs(dz));

        if (length == 0) return;

        Direction facing;
        if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        for (int i = 0; i <= length; i++) {
            double t = (double) i / length;
            int x = start.getX() + (int) (dx * t);
            int z = start.getZ() + (int) (dz * t);

            BlockPos basePos = new BlockPos(x, start.getY(), z);
            buildWallSection(world, basePos, facing, i);

            // Add arrow slit
            if (i > 0 && i < length && i % slitInterval == 0) {
                addArrowSlit(world, basePos, facing);
            }
        }
    }

    /**
     * Add an arrow slit (narrow window) to the wall.
     */
    private void addArrowSlit(ServerLevel world, BlockPos base, Direction facing) {
        Direction perpendicular = facing.getClockWise();
        // Arrow slits are 1 wide, 3 tall, at eye level, on the OUTER face of the wall.
        // Outer face is at -WALL_HALF_WIDTH perpendicular offset.
        int slitHeight = 3;
        int slitStart = 3;

        for (int y = slitStart; y < slitStart + slitHeight; y++) {
            BlockPos outerPos = base.relative(perpendicular, -WALL_HALF_WIDTH).above(y);
            // Place bars on the outer face -- visible from outside,
            // wall remains solid on the inside
            world.setBlock(outerPos, palette.getBarsState(), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Generate wall with torch/lantern placement.
     */
    public void generateWithLighting(ServerLevel world, BlockPos start, BlockPos end, int lightInterval) {
        generateWithArrowSlits(world, start, end, 6);

        int dx = end.getX() - start.getX();
        int dz = end.getZ() - start.getZ();
        int length = Math.max(Math.abs(dx), Math.abs(dz));

        Direction facing;
        if (Math.abs(dx) > Math.abs(dz)) {
            facing = dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        Direction perpendicular = facing.getClockWise();

        // Add lights on walkway (sitting on floor)
        for (int i = lightInterval / 2; i <= length; i += lightInterval) {
            double t = (double) i / length;
            int x = start.getX() + (int) (dx * t);
            int z = start.getZ() + (int) (dz * t);

            BlockPos lightPos = new BlockPos(x, start.getY() + WALKWAY_HEIGHT, z)
                .relative(perpendicular, WALL_HALF_WIDTH);
            // Lantern sitting on the walkway floor
            world.setBlock(lightPos, palette.light.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Returns wall height adjusted for biome.
     * Desert: 7 (lower, wider walls -- desert heat makes tall walls impractical)
     * Taiga/Snowy: 9 (taller, more defensive fortification)
     * Plains/Savanna: 8 (default)
     */
    private int getWallHeight() {
        return getWallHeight(palette);
    }

    public static int getWallHeight(BiomePalette palette) {
        return switch (palette) {
            case DESERT -> 7;
            case TAIGA, SNOWY -> 9;
            default -> DEFAULT_WALL_HEIGHT;
        };
    }

    public static int getWalkwayHeight() {
        return WALKWAY_HEIGHT;
    }
}
