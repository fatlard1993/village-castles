package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.loot.LootTables;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Generates the central keep/tower of a castle.
 * The keep is a multi-story tower with:
 * - Ground floor: Great hall with throne
 * - Upper floors: Living quarters, armory
 * - Top floor: Lord's chamber with balcony
 * - Roof: Crenellated battlements
 */
public class KeepGenerator {

    private final BiomePalette palette;
    private final Random random;

    // Keep dimensions
    private final int width;      // X dimension (odd number for center)
    private final int depth;      // Z dimension
    private final int floorHeight; // Height per floor including ceiling
    private final int numFloors;

    public KeepGenerator(BiomePalette palette, Random random, int size) {
        this.palette = palette;
        this.random = random;

        // Size determines scale
        if (size >= 2) { // Large
            this.width = 17;
            this.depth = 21;
            this.floorHeight = 5;
            this.numFloors = 4;
        } else if (size == 1) { // Medium
            this.width = 13;
            this.depth = 17;
            this.floorHeight = 5;
            this.numFloors = 3;
        } else { // Small
            this.width = 9;
            this.depth = 13;
            this.floorHeight = 4;
            this.numFloors = 2;
        }
    }

    /**
     * Generate the keep at the specified position.
     * @param world The server world
     * @param origin The bottom-center position of the keep
     * @return The total height of the structure
     */
    public int generate(ServerWorld world, BlockPos origin) {
        int totalHeight = numFloors * floorHeight + 2; // +2 for crenellations

        // Calculate corners
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        BlockPos corner1 = origin.add(-halfWidth, 0, -halfDepth);
        BlockPos corner2 = origin.add(halfWidth, totalHeight - 2, halfDepth);

        // Build foundation
        buildFoundation(world, origin, halfWidth, halfDepth);

        // Build main structure walls
        buildExteriorWalls(world, corner1, corner2);

        // Build each floor
        for (int floor = 0; floor < numFloors; floor++) {
            int floorY = origin.getY() + (floor * floorHeight);
            buildFloor(world, origin, floorY, floor);
        }

        // Add roof with crenellations
        int roofY = origin.getY() + (numFloors * floorHeight);
        buildRoof(world, origin, roofY, halfWidth, halfDepth);

        // Add corner turrets
        addCornerTurrets(world, origin, halfWidth, halfDepth, totalHeight);

        // Add main entrance
        buildEntrance(world, origin);

        return totalHeight;
    }

    private void buildFoundation(ServerWorld world, BlockPos origin, int halfWidth, int halfDepth) {
        BlockState stone = Blocks.COBBLESTONE.getDefaultState();
        BlockPos corner1 = origin.add(-halfWidth - 1, -3, -halfDepth - 1);
        BlockPos corner2 = origin.add(halfWidth + 1, -1, halfDepth + 1);
        StructureHelper.fillBox(world, corner1, corner2, stone);
    }

    private void buildExteriorWalls(ServerWorld world, BlockPos corner1, BlockPos corner2) {
        // Build walls with varied blocks
        int minX = corner1.getX();
        int minZ = corner1.getZ();
        int maxX = corner2.getX();
        int maxZ = corner2.getZ();
        int minY = corner1.getY();
        int maxY = corner2.getY();

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    boolean isEdge = x == minX || x == maxX || z == minZ || z == maxZ;
                    if (isEdge) {
                        BlockState wallBlock = palette.getRandomWallBlock(random).getDefaultState();
                        world.setBlockState(new BlockPos(x, y, z), wallBlock);
                    }
                }
            }
        }
    }

    private void buildFloor(ServerWorld world, BlockPos origin, int floorY, int floorNum) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Floor surface
        BlockPos floorCorner1 = origin.add(-halfWidth + 1, 0, -halfDepth + 1);
        BlockPos floorCorner2 = origin.add(halfWidth - 1, 0, halfDepth - 1);
        StructureHelper.fillFloor(world, floorCorner1, floorCorner2, floorY, palette.getFloorState());

        // Clear interior space
        BlockPos clearCorner1 = origin.add(-halfWidth + 1, 1, -halfDepth + 1);
        BlockPos clearCorner2 = origin.add(halfWidth - 1, floorHeight - 1, halfDepth - 1);
        StructureHelper.clearInterior(world,
            clearCorner1.withY(floorY + 1),
            clearCorner2.withY(floorY + floorHeight - 1));

        // Add windows
        addWindows(world, origin, floorY + 2, halfWidth, halfDepth);

        // Floor-specific features
        switch (floorNum) {
            case 0 -> buildGreatHall(world, origin, floorY);
            case 1 -> buildArmory(world, origin, floorY);
            case 2 -> buildLivingQuarters(world, origin, floorY);
            default -> buildLordsChamber(world, origin, floorY);
        }

        // Add lighting
        addFloorLighting(world, origin, floorY + 2, halfWidth, halfDepth);

        // Add stairs to next floor (except top floor)
        if (floorNum < numFloors - 1) {
            addStaircase(world, origin, floorY);
        }
    }

    private void buildGreatHall(ServerWorld world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Throne platform at back (use absolute Y, not relative to origin.y)
        BlockPos thronePos = new BlockPos(origin.getX(), floorY + 1, origin.getZ() + halfDepth - 3);

        // Platform (2 high)
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                world.setBlockState(thronePos.add(x, 0, z), palette.getPrimaryWallState());
            }
        }

        // Throne (stairs as chair)
        world.setBlockState(thronePos.add(0, 1, 1), palette.woodStairs.getDefaultState()
            .with(StairsBlock.FACING, Direction.SOUTH));

        // Carpet runner down the middle
        BlockState carpet = Blocks.RED_CARPET.getDefaultState();
        for (int z = -halfDepth + 2; z < halfDepth - 3; z++) {
            world.setBlockState(new BlockPos(origin.getX(), floorY + 1, origin.getZ() + z), carpet);
        }

        // Banquet tables
        BlockState table = palette.getPlanksState();
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 2; z <= halfDepth / 3; z += 2) {
                world.setBlockState(new BlockPos(origin.getX() + side * 3, floorY + 1, origin.getZ() + z), table);
            }
        }

        // Pillars
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 2; z <= halfDepth / 2; z += 4) {
                BlockPos pillarBase = new BlockPos(origin.getX() + side * (halfWidth - 3), floorY + 1, origin.getZ() + z);
                for (int y = 0; y < floorHeight - 2; y++) {
                    world.setBlockState(pillarBase.up(y), palette.getLogState());
                }
            }
        }
    }

    private void buildArmory(ServerWorld world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Armor stands (represented by fences with banners)
        BlockState fence = palette.getFenceState();
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += 3) {
            world.setBlockState(new BlockPos(origin.getX() + x, floorY + 1, origin.getZ() - halfDepth + 2), fence);
            world.setBlockState(new BlockPos(origin.getX() + x, floorY + 2, origin.getZ() - halfDepth + 2), fence);
        }

        // Weapon racks (item frames on walls would go here in actual NBT)
        // For generation, we'll place fence posts as weapon stands
        for (int z = -halfDepth + 3; z <= halfDepth - 3; z += 4) {
            world.setBlockState(new BlockPos(origin.getX() - halfWidth + 2, floorY + 1, origin.getZ() + z), fence);
            world.setBlockState(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ() + z), fence);
        }

        // Chest with loot (armory-appropriate loot)
        StructureHelper.placeChest(world, new BlockPos(origin.getX(), floorY + 1, origin.getZ() + halfDepth - 2),
            Direction.NORTH, LootTables.VILLAGE_WEAPONSMITH_CHEST);
    }

    private void buildLivingQuarters(ServerWorld world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Beds along walls - proper bed placement with head and foot
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += 4) {
            BlockPos footPos = new BlockPos(origin.getX() + x, floorY + 1, origin.getZ() - halfDepth + 2);
            BlockPos headPos = new BlockPos(origin.getX() + x, floorY + 1, origin.getZ() - halfDepth + 3);
            // Bed faces south (head toward wall), foot toward room
            world.setBlockState(footPos, Blocks.RED_BED.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT)
                .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
            world.setBlockState(headPos, Blocks.RED_BED.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD)
                .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        }

        // Bookshelves
        BlockState bookshelf = Blocks.BOOKSHELF.getDefaultState();
        for (int z = 0; z <= halfDepth - 2; z += 2) {
            world.setBlockState(new BlockPos(origin.getX() - halfWidth + 2, floorY + 1, origin.getZ() + z), bookshelf);
            world.setBlockState(new BlockPos(origin.getX() - halfWidth + 2, floorY + 2, origin.getZ() + z), bookshelf);
        }

        // Crafting area
        world.setBlockState(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ()), Blocks.CRAFTING_TABLE.getDefaultState());
        world.setBlockState(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ() + 1),
            Blocks.FURNACE.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));
    }

    private void buildLordsChamber(ServerWorld world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        int ox = origin.getX();
        int oz = origin.getZ();

        // Large bed (proper bed block - center bed only, sides have decorative blocks)
        // Main bed in center facing north (head at wall)
        BlockPos bedFoot = new BlockPos(ox, floorY + 1, oz + halfDepth - 3);
        BlockPos bedHead = new BlockPos(ox, floorY + 1, oz + halfDepth - 2);
        world.setBlockState(bedFoot, Blocks.RED_BED.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT)
            .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        world.setBlockState(bedHead, Blocks.RED_BED.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD)
            .with(HorizontalFacingBlock.FACING, Direction.SOUTH));
        // Decorative carpets on sides of bed
        world.setBlockState(new BlockPos(ox - 1, floorY + 1, oz + halfDepth - 2), Blocks.RED_CARPET.getDefaultState());
        world.setBlockState(new BlockPos(ox + 1, floorY + 1, oz + halfDepth - 2), Blocks.RED_CARPET.getDefaultState());
        world.setBlockState(new BlockPos(ox - 1, floorY + 1, oz + halfDepth - 3), Blocks.RED_CARPET.getDefaultState());
        world.setBlockState(new BlockPos(ox + 1, floorY + 1, oz + halfDepth - 3), Blocks.RED_CARPET.getDefaultState());

        // Desk
        world.setBlockState(new BlockPos(ox - halfWidth + 3, floorY + 1, oz), palette.getPlanksState());
        world.setBlockState(new BlockPos(ox - halfWidth + 4, floorY + 1, oz), palette.getPlanksState());

        // Treasure chest with loot table
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - 3, floorY + 1, oz + halfDepth - 2),
            Direction.WEST, LootTables.STRONGHOLD_CORRIDOR_CHEST);

        // Ender chest (lord's valuables) - with facing
        world.setBlockState(new BlockPos(ox + halfWidth - 3, floorY + 1, oz - halfDepth + 2),
            Blocks.ENDER_CHEST.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST));

        // Balcony (cut opening in front wall)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlockState(new BlockPos(ox + x, floorY + y, oz - halfDepth), Blocks.AIR.getDefaultState());
            }
        }

        // Balcony platform
        for (int x = -2; x <= 2; x++) {
            world.setBlockState(new BlockPos(ox + x, floorY, oz - halfDepth - 1), palette.getPrimaryWallState());
            world.setBlockState(new BlockPos(ox + x, floorY, oz - halfDepth - 2), palette.getPrimaryWallState());
        }

        // Balcony railing
        for (int x = -2; x <= 2; x++) {
            world.setBlockState(new BlockPos(ox + x, floorY + 1, oz - halfDepth - 2), palette.getWallState());
        }
        world.setBlockState(new BlockPos(ox - 2, floorY + 1, oz - halfDepth - 1), palette.getWallState());
        world.setBlockState(new BlockPos(ox + 2, floorY + 1, oz - halfDepth - 1), palette.getWallState());
    }

    private void addWindows(ServerWorld world, BlockPos origin, int windowY, int halfWidth, int halfDepth) {
        BlockState bars = palette.getBarsState();
        int ox = origin.getX();
        int oz = origin.getZ();

        // Windows on each wall
        int windowSpacing = 4;

        // North and South walls
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += windowSpacing) {
            world.setBlockState(new BlockPos(ox + x, windowY, oz - halfDepth), bars);
            world.setBlockState(new BlockPos(ox + x, windowY + 1, oz - halfDepth), bars);
            world.setBlockState(new BlockPos(ox + x, windowY, oz + halfDepth), bars);
            world.setBlockState(new BlockPos(ox + x, windowY + 1, oz + halfDepth), bars);
        }

        // East and West walls
        for (int z = -halfDepth + 3; z <= halfDepth - 3; z += windowSpacing) {
            world.setBlockState(new BlockPos(ox - halfWidth, windowY, oz + z), bars);
            world.setBlockState(new BlockPos(ox - halfWidth, windowY + 1, oz + z), bars);
            world.setBlockState(new BlockPos(ox + halfWidth, windowY, oz + z), bars);
            world.setBlockState(new BlockPos(ox + halfWidth, windowY + 1, oz + z), bars);
        }
    }

    private void addFloorLighting(ServerWorld world, BlockPos origin, int lightY, int halfWidth, int halfDepth) {
        int ox = origin.getX();
        int oz = origin.getZ();

        // Place lanterns hanging from ceiling (need solid block above)
        // First ensure ceiling blocks exist, then place hanging lanterns
        int ceilingY = lightY + floorHeight - 3; // Just below ceiling
        int spacing = 5;

        for (int x = -halfWidth + 2; x <= halfWidth - 2; x += spacing) {
            // North wall side - place support block then hanging lantern
            BlockPos supportN = new BlockPos(ox + x, ceilingY + 1, oz - halfDepth + 2);
            BlockPos lanternN = new BlockPos(ox + x, ceilingY, oz - halfDepth + 2);
            world.setBlockState(supportN, palette.getPlanksState());
            world.setBlockState(lanternN, palette.light.getDefaultState().with(LanternBlock.HANGING, true));

            // South wall side
            BlockPos supportS = new BlockPos(ox + x, ceilingY + 1, oz + halfDepth - 2);
            BlockPos lanternS = new BlockPos(ox + x, ceilingY, oz + halfDepth - 2);
            world.setBlockState(supportS, palette.getPlanksState());
            world.setBlockState(lanternS, palette.light.getDefaultState().with(LanternBlock.HANGING, true));
        }
    }

    private void addStaircase(ServerWorld world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Staircase in corner - proper L-shaped staircase with support
        int ox = origin.getX() + halfWidth - 4;
        int oz = origin.getZ() + halfDepth - 2;

        // Stair facing: you face the direction the stair points when climbing
        // Going north (negative Z) means facing north
        BlockState stairNorth = palette.woodStairs.getDefaultState()
            .with(StairsBlock.FACING, Direction.NORTH);
        BlockState stairWest = palette.woodStairs.getDefaultState()
            .with(StairsBlock.FACING, Direction.WEST);

        // Build support structure under stairs (filled so stairs aren't floating)
        for (int i = 0; i < floorHeight - 1; i++) {
            // Support blocks under the diagonal
            for (int j = 0; j <= i; j++) {
                world.setBlockState(new BlockPos(ox, floorY + 1 + j, oz - i), palette.getPrimaryWallState());
            }
        }

        // Place stairs on top of support
        for (int i = 0; i < floorHeight - 1; i++) {
            BlockPos stairPos = new BlockPos(ox, floorY + 1 + i, oz - i);
            world.setBlockState(stairPos, stairNorth);
        }

        // Landing at top
        BlockPos landingPos = new BlockPos(ox, floorY + floorHeight, oz - floorHeight + 1);
        world.setBlockState(landingPos, palette.getPlanksState());
        world.setBlockState(landingPos.east(), palette.getPlanksState());
        world.setBlockState(landingPos.north(), palette.getPlanksState());
        world.setBlockState(landingPos.north().east(), palette.getPlanksState());

        // Clear opening in ceiling for access
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                world.setBlockState(landingPos.add(x, 0, -z), palette.getPlanksState());
            }
        }

        // Add railing around stairwell opening
        world.setBlockState(landingPos.west().up(), palette.getFenceState());
        world.setBlockState(landingPos.south().up(), palette.getFenceState());
    }

    private void buildRoof(ServerWorld world, BlockPos origin, int roofY, int halfWidth, int halfDepth) {
        int ox = origin.getX();
        int oz = origin.getZ();

        // Flat roof (use absolute Y)
        BlockPos roofCorner1 = new BlockPos(ox - halfWidth, roofY, oz - halfDepth);
        BlockPos roofCorner2 = new BlockPos(ox + halfWidth, roofY, oz + halfDepth);
        StructureHelper.fillFloor(world, roofCorner1, roofCorner2, roofY, palette.getPrimaryWallState());

        // Crenellations
        StructureHelper.addCrenellations(world, roofCorner1, roofCorner2, roofY + 1, palette.getPrimaryWallState());
    }

    private void addCornerTurrets(ServerWorld world, BlockPos origin, int halfWidth, int halfDepth, int height) {
        int turretRadius = 3;
        int turretHeight = height + 3;
        int baseY = origin.getY();

        BlockPos[] corners = {
            new BlockPos(origin.getX() - halfWidth, baseY, origin.getZ() - halfDepth),
            new BlockPos(origin.getX() + halfWidth, baseY, origin.getZ() - halfDepth),
            new BlockPos(origin.getX() - halfWidth, baseY, origin.getZ() + halfDepth),
            new BlockPos(origin.getX() + halfWidth, baseY, origin.getZ() + halfDepth)
        };

        for (BlockPos corner : corners) {
            StructureHelper.buildCylinder(world, corner, turretRadius, turretHeight, palette.getPrimaryWallState(), true);
            StructureHelper.addCircularCrenellations(world, corner, turretRadius, turretHeight, palette.getPrimaryWallState());

            // Conical roof hint (just the top layer sloped)
            world.setBlockState(corner.up(turretHeight + 1), palette.getRoofState());
        }
    }

    private void buildEntrance(ServerWorld world, BlockPos origin) {
        int halfDepth = depth / 2;

        // Main doorway on front (north) side
        BlockPos doorBase = origin.add(0, 0, -halfDepth);

        // Clear doorway (3 wide, 4 high)
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 4; y++) {
                world.setBlockState(doorBase.add(x, y, 0), Blocks.AIR.getDefaultState());
            }
        }

        // Arch top
        world.setBlockState(doorBase.add(-1, 4, 0), palette.getPrimaryWallState());
        world.setBlockState(doorBase.add(1, 4, 0), palette.getPrimaryWallState());

        // Steps leading up
        BlockState stair = palette.stoneStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH);
        for (int x = -2; x <= 2; x++) {
            world.setBlockState(doorBase.add(x, 0, -1), stair);
        }
    }

    public int getWidth() {
        return width;
    }

    public int getDepth() {
        return depth;
    }

    public int getTotalHeight() {
        return numFloors * floorHeight + 2;
    }
}
