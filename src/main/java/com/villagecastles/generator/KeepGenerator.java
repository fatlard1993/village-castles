package com.villagecastles.generator;

import com.villagecastles.util.StructureHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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
    private final int size;

    // Keep dimensions
    private final int width;      // X dimension (odd number for center)
    private final int depth;      // Z dimension
    private final int floorHeight; // Height per floor including ceiling
    private final int numFloors;

    public KeepGenerator(BiomePalette palette, Random random, int size) {
        this.palette = palette;
        this.random = random;
        this.size = size;

        // Size determines base scale
        int baseWidth, baseDepth, baseFloorHeight;
        if (size >= 2) { // Large
            baseWidth = 17;
            baseDepth = 21;
            baseFloorHeight = 5;
            this.numFloors = 4;
        } else if (size == 1) { // Medium
            baseWidth = 13;
            baseDepth = 17;
            baseFloorHeight = 5;
            this.numFloors = 3;
        } else { // Small — tower keep (taller, narrower)
            baseWidth = 7;
            baseDepth = 9;
            baseFloorHeight = 4;
            this.numFloors = 3;
        }

        // Biome-specific proportions — not just palette swaps
        // Desert/Savanna: wider, squatter (thick-walled, flat-roofed)
        // Taiga: narrower, taller (steep Nordic longhouse proportions)
        // Snowy: taller ceilings (vaulted ice halls)
        switch (palette) {
            case DESERT -> { baseWidth += 3; baseDepth += 2; baseFloorHeight = Math.max(baseFloorHeight - 1, 4); }
            case SAVANNA -> { baseWidth += 2; baseDepth += 1; }
            case TAIGA -> { baseWidth -= 1; baseDepth += 2; baseFloorHeight += 1; }
            case SNOWY -> { baseFloorHeight += 1; }
            default -> {} // PLAINS is the baseline
        }

        // Ensure odd dimensions for centering
        this.width = baseWidth | 1;
        this.depth = baseDepth | 1;
        this.floorHeight = baseFloorHeight;
    }

    /**
     * Generate the keep at the specified position.
     * @param world The server world
     * @param origin The bottom-center position of the keep
     * @return The total height of the structure
     */
    public int generate(ServerLevel world, BlockPos origin) {
        int totalHeight = numFloors * floorHeight + 2; // +2 for crenellations

        // Calculate corners
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        BlockPos corner1 = origin.offset(-halfWidth, 0, -halfDepth);
        BlockPos corner2 = origin.offset(halfWidth, totalHeight - 2, halfDepth);

        // Build foundation
        buildFoundation(world, origin, halfWidth, halfDepth);

        // Build main structure walls
        buildExteriorWalls(world, corner1, corner2);

        // Build each floor
        for (int floor = 0; floor < numFloors; floor++) {
            int floorY = origin.getY() + (floor * floorHeight);
            buildFloor(world, origin, floorY, floor);
        }

        // Second pass: re-open stairwell holes that buildFloor() overwrote.
        // Each stair step needs the floor block above it cleared, plus headroom.
        // The landing (2x2) also needs floor cleared and headroom above.
        for (int floor = 0; floor < numFloors - 1; floor++) {
            int floorY = origin.getY() + (floor * floorHeight);
            int stairOx = origin.getX() + halfWidth - 4;
            int stairOz = origin.getZ() + halfDepth - 2;
            int landingY = floorY + floorHeight;

            // Clear floor block and headroom above EACH stair step
            for (int i = 0; i < floorHeight - 1; i++) {
                int stepZ = stairOz - i;
                // Clear the floor at the next level above this step
                world.setBlock(new BlockPos(stairOx, landingY, stepZ),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(stairOx + 1, landingY, stepZ),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                // Headroom above
                world.setBlock(new BlockPos(stairOx, landingY + 1, stepZ),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(stairOx + 1, landingY + 1, stepZ),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }

            // Clear landing area (2x2) and headroom
            int landingZ = stairOz - floorHeight + 1;
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    // Floor level — air, not planks (the stair landing IS the walkable surface)
                    world.setBlock(new BlockPos(stairOx + dx, landingY, landingZ - dz),
                        Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                    // Headroom
                    for (int dy = 1; dy <= 2; dy++) {
                        world.setBlock(new BlockPos(stairOx + dx, landingY + dy, landingZ - dz),
                            Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
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

    private void buildFoundation(ServerLevel world, BlockPos origin, int halfWidth, int halfDepth) {
        BlockState stone = Blocks.COBBLESTONE.defaultBlockState();
        BlockPos corner1 = origin.offset(-halfWidth - 1, -3, -halfDepth - 1);
        BlockPos corner2 = origin.offset(halfWidth + 1, -1, halfDepth + 1);
        StructureHelper.fillBox(world, corner1, corner2, stone);
    }

    private void buildExteriorWalls(ServerLevel world, BlockPos corner1, BlockPos corner2) {
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
                        BlockState wallBlock = palette.getRandomWallBlock(random);
                        world.setBlock(new BlockPos(x, y, z), wallBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
    }

    private void buildFloor(ServerLevel world, BlockPos origin, int floorY, int floorNum) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Floor surface
        BlockPos floorCorner1 = origin.offset(-halfWidth + 1, 0, -halfDepth + 1);
        BlockPos floorCorner2 = origin.offset(halfWidth - 1, 0, halfDepth - 1);
        StructureHelper.fillFloor(world, floorCorner1, floorCorner2, floorY, palette.getFloorState());

        // Clear interior space
        BlockPos clearCorner1 = origin.offset(-halfWidth + 1, 1, -halfDepth + 1);
        BlockPos clearCorner2 = origin.offset(halfWidth - 1, floorHeight - 1, halfDepth - 1);
        StructureHelper.clearInterior(world,
            clearCorner1.atY(floorY + 1),
            clearCorner2.atY(floorY + floorHeight - 1));

        // Add windows — skip ground floor for defensibility; lookout floor of small keeps gets tighter spacing
        if (floorNum > 0) {
            int windowSpacing = (size == 0 && floorNum == numFloors - 1) ? 3 : 4;
            addWindows(world, origin, floorY + 2, halfWidth, halfDepth, windowSpacing);
        }

        // Floor-specific features — small keeps get their own compact floor plans
        if (size == 0) {
            switch (floorNum) {
                case 0 -> buildLivingRoom(world, origin, floorY);
                case 1 -> buildStoreroom(world, origin, floorY);
                default -> buildLookoutRoom(world, origin, floorY);
            }
        } else {
            switch (floorNum) {
                case 0 -> buildGreatHall(world, origin, floorY);
                case 1 -> buildArmory(world, origin, floorY);
                case 2 -> buildLivingQuarters(world, origin, floorY);
                default -> buildLordsChamber(world, origin, floorY);
            }
        }

        // Add lighting
        addFloorLighting(world, origin, floorY + 2, halfWidth, halfDepth);

        // Add stairs to next floor (except top floor)
        if (floorNum < numFloors - 1) {
            addStaircase(world, origin, floorY);
        }
    }

    // ==================== Small Keep (Size 0) Floor Plans ====================

    /**
     * Ground floor of a small tower keep — a cozy combined living space.
     * Campfire with stone base, bed alcove, small table, chest, carpet.
     */
    private void buildLivingRoom(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        int ox = origin.getX();
        int oz = origin.getZ();

        // Central campfire on a stone base — solid block underneath so nothing floats
        BlockPos fireBase = new BlockPos(ox, floorY + 1, oz);
        world.setBlock(fireBase, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        BlockState campfire = (palette == BiomePalette.SNOWY)
            ? Blocks.SOUL_CAMPFIRE.defaultBlockState()
            : Blocks.CAMPFIRE.defaultBlockState();
        world.setBlock(fireBase.above(), campfire, StructureHelper.SET_FLAGS);

        // Carpet around the fire area
        BlockState carpet = palette.getCarpetState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue; // skip fire position
                BlockPos carpetPos = new BlockPos(ox + dx, floorY + 1, oz + dz);
                world.setBlock(carpetPos, carpet, StructureHelper.SET_FLAGS);
            }
        }

        // Bed alcove against the south wall (back of room)
        BlockPos bedFoot = new BlockPos(ox - halfWidth + 2, floorY + 1, oz + halfDepth - 3);
        BlockPos bedHead = new BlockPos(ox - halfWidth + 2, floorY + 1, oz + halfDepth - 2);
        world.setBlock(bedFoot, palette.getBedState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(bedHead, palette.getBedState()
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Small table (plank block) against east wall with a chair facing it
        BlockPos tablePos = new BlockPos(ox + halfWidth - 2, floorY + 1, oz);
        world.setBlock(tablePos, palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(tablePos.west(), palette.woodStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);

        // Chest for supplies against the north wall
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - 2, floorY + 1, oz - halfDepth + 2),
            Direction.WEST, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
    }

    /**
     * Second floor of a small tower keep — compact storeroom/armory.
     * Barrels, weapon chest, crafting table, furnace, fence-post weapon stands.
     */
    private void buildStoreroom(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        int ox = origin.getX();
        int oz = origin.getZ();

        // Barrels along the north wall
        for (int x = -halfWidth + 2; x <= halfWidth - 2; x += 2) {
            world.setBlock(new BlockPos(ox + x, floorY + 1, oz - halfDepth + 2),
                Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Weapon chest against the south wall
        StructureHelper.placeChest(world, new BlockPos(ox, floorY + 1, oz + halfDepth - 2),
            Direction.NORTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);

        // Crafting table + furnace side by side on the west wall
        world.setBlock(new BlockPos(ox - halfWidth + 2, floorY + 1, oz),
            Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + 2, floorY + 1, oz + 1),
            Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);

        // Fence posts as weapon/armor stands along the east wall
        BlockState fence = palette.getFenceState();
        for (int z = -halfDepth + 2; z <= halfDepth - 2; z += 2) {
            world.setBlock(new BlockPos(ox + halfWidth - 2, floorY + 1, oz + z), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth - 2, floorY + 2, oz + z), fence, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Top floor of a small tower keep — lookout room and lord's personal space.
     * Bed in corner, desk with lantern, bookshelf. More windows (handled by spacing).
     */
    private void buildLookoutRoom(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        int ox = origin.getX();
        int oz = origin.getZ();

        // Bed in the southeast corner
        BlockPos bedFoot = new BlockPos(ox + halfWidth - 3, floorY + 1, oz + halfDepth - 3);
        BlockPos bedHead = new BlockPos(ox + halfWidth - 3, floorY + 1, oz + halfDepth - 2);
        world.setBlock(bedFoot, palette.getBedState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(bedHead, palette.getBedState()
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Desk (planks) against the west wall with a lantern on it
        BlockPos deskPos = new BlockPos(ox - halfWidth + 2, floorY + 1, oz);
        world.setBlock(deskPos, palette.getPlanksState(), StructureHelper.SET_FLAGS);
        // Lantern sitting on the desk
        world.setBlock(deskPos.above(), palette.getLightState(), StructureHelper.SET_FLAGS);

        // Bookshelf against the north wall
        world.setBlock(new BlockPos(ox, floorY + 1, oz - halfDepth + 2),
            Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 1, floorY + 1, oz - halfDepth + 2),
            Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    // ==================== Standard Keep Floor Plans (Size 1+) ====================

    private void buildGreatHall(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        int ox = origin.getX();
        int oz = origin.getZ();

        // Stonecutter near entrance — mason workstation (castle builders)
        world.setBlock(new BlockPos(ox + halfWidth - 2, floorY + 1, oz - halfDepth + 2),
            Blocks.STONECUTTER.defaultBlockState(), StructureHelper.SET_FLAGS);

        switch (palette) {
            case DESERT -> buildGreatHallDesert(world, ox, oz, floorY, halfWidth, halfDepth);
            case TAIGA -> buildGreatHallTaiga(world, ox, oz, floorY, halfWidth, halfDepth);
            case SNOWY -> buildGreatHallSnowy(world, ox, oz, floorY, halfWidth, halfDepth);
            case SAVANNA -> buildGreatHallSavanna(world, ox, oz, floorY, halfWidth, halfDepth);
            default -> buildGreatHallPlains(world, ox, oz, floorY, halfWidth, halfDepth);
        }
    }

    private void buildGreatHallPlains(ServerLevel world, int ox, int oz, int floorY, int halfWidth, int halfDepth) {
        // Throne platform at back
        BlockPos thronePos = new BlockPos(ox, floorY + 1, oz + halfDepth - 3);

        // Platform (2 high)
        for (int x = -2; x <= 2; x++) {
            for (int z = 0; z <= 2; z++) {
                world.setBlock(thronePos.offset(x, 0, z), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Throne (stairs as chair)
        world.setBlock(thronePos.offset(0, 1, 1), palette.woodStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Carpet runner down the middle
        BlockState carpet = palette.getCarpetState();
        for (int z = -halfDepth + 2; z < halfDepth - 3; z++) {
            world.setBlock(new BlockPos(ox, floorY + 1, oz + z), carpet, StructureHelper.SET_FLAGS);
        }

        // Banquet tables
        BlockState table = palette.getPlanksState();
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 2; z <= halfDepth / 3; z += 2) {
                world.setBlock(new BlockPos(ox + side * 3, floorY + 1, oz + z), table, StructureHelper.SET_FLAGS);
            }
        }

        // Pillars
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 2; z <= halfDepth / 2; z += 4) {
                BlockPos pillarBase = new BlockPos(ox + side * (halfWidth - 3), floorY + 1, oz + z);
                for (int y = 0; y < floorHeight - 2; y++) {
                    world.setBlock(pillarBase.above(y), palette.getLogState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Oubliette (secret dungeon pit) - offset from center carpet runner
        int oubX = ox + 2;
        int oubZ = oz - halfDepth / 2;
        int pitDepth = 4;

        // Trapdoor over the pit opening
        world.setBlock(new BlockPos(oubX, floorY + 1, oubZ),
            Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH),
            StructureHelper.SET_FLAGS);

        // Dig the 3x3 pit and line with stone bricks
        for (int dy = 0; dy < pitDepth; dy++) {
            int pitY = floorY - dy;
            for (int px = -1; px <= 1; px++) {
                for (int pz = -1; pz <= 1; pz++) {
                    // Clear interior
                    world.setBlock(new BlockPos(oubX + px, pitY, oubZ + pz),
                        Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
            // Line the walls with stone bricks (ring around the 3x3)
            for (int px = -2; px <= 2; px++) {
                for (int pz = -2; pz <= 2; pz++) {
                    boolean isLining = (px == -2 || px == 2 || pz == -2 || pz == 2)
                        && Math.abs(px) <= 2 && Math.abs(pz) <= 2;
                    if (isLining) {
                        world.setBlock(new BlockPos(oubX + px, pitY, oubZ + pz),
                            Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Stone brick floor at the bottom of the pit
        int bottomY = floorY - pitDepth;
        for (int px = -1; px <= 1; px++) {
            for (int pz = -1; pz <= 1; pz++) {
                world.setBlock(new BlockPos(oubX + px, bottomY, oubZ + pz),
                    Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        // Line the bottom ring too
        for (int px = -2; px <= 2; px++) {
            for (int pz = -2; pz <= 2; pz++) {
                boolean isLining = (px == -2 || px == 2 || pz == -2 || pz == 2)
                    && Math.abs(px) <= 2 && Math.abs(pz) <= 2;
                if (isLining) {
                    world.setBlock(new BlockPos(oubX + px, bottomY, oubZ + pz),
                        Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Skeleton skull and cobwebs at the bottom
        world.setBlock(new BlockPos(oubX, bottomY + 1, oubZ),
            Blocks.SKELETON_SKULL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(oubX - 1, bottomY + 1, oubZ),
            Blocks.COBWEB.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(oubX + 1, bottomY + 1, oubZ),
            Blocks.COBWEB.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(oubX, bottomY + 1, oubZ + 1),
            Blocks.COBWEB.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    private void buildGreatHallDesert(ServerLevel world, int ox, int oz, int floorY, int halfWidth, int halfDepth) {
        // Raised dais with carpet at back
        BlockPos daisPos = new BlockPos(ox, floorY + 1, oz + halfDepth - 3);
        for (int x = -3; x <= 3; x++) {
            for (int z = 0; z <= 2; z++) {
                world.setBlock(daisPos.offset(x, 0, z), Blocks.SMOOTH_SANDSTONE.defaultBlockState(), StructureHelper.SET_FLAGS);
                // Carpet on top of dais
                world.setBlock(daisPos.offset(x, 1, z), palette.getCarpetState(), StructureHelper.SET_FLAGS);
            }
        }

        // Throne on the dais (raised by dais + carpet height means we place on top)
        world.setBlock(daisPos.offset(0, 1, 1), palette.woodStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Terracotta decorative columns instead of log pillars
        BlockState terracottaCol = Blocks.TERRACOTTA.defaultBlockState();
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 2; z <= halfDepth / 2; z += 4) {
                BlockPos pillarBase = new BlockPos(ox + side * (halfWidth - 3), floorY + 1, oz + z);
                for (int y = 0; y < floorHeight - 2; y++) {
                    world.setBlock(pillarBase.above(y), terracottaCol, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Central fountain — 3x3 stone brick frame with water in the center
        int fountainZ = oz;
        // Floor of fountain basin
        for (int fx = -1; fx <= 1; fx++) {
            for (int fz = -1; fz <= 1; fz++) {
                world.setBlock(new BlockPos(ox + fx, floorY + 1, fountainZ + fz),
                    Blocks.SMOOTH_SANDSTONE.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        // Rim walls (1 high around edge)
        for (int fx = -1; fx <= 1; fx++) {
            world.setBlock(new BlockPos(ox + fx, floorY + 2, fountainZ - 1), Blocks.SANDSTONE_WALL.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + fx, floorY + 2, fountainZ + 1), Blocks.SANDSTONE_WALL.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        world.setBlock(new BlockPos(ox - 1, floorY + 2, fountainZ), Blocks.SANDSTONE_WALL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 1, floorY + 2, fountainZ), Blocks.SANDSTONE_WALL.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Water source in center
        world.setBlock(new BlockPos(ox, floorY + 2, fountainZ), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    private void buildGreatHallTaiga(ServerLevel world, int ox, int oz, int floorY, int halfWidth, int halfDepth) {
        // Central fireplace — campfire with stone brick chimney frame
        BlockPos firePos = new BlockPos(ox, floorY + 1, oz);
        // Stone base for the campfire
        world.setBlock(firePos, Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Campfire on top
        world.setBlock(firePos.above(), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Chimney frame corners around the fire (4 pillars)
        for (int cx = -1; cx <= 1; cx += 2) {
            for (int cz = -1; cz <= 1; cz += 2) {
                for (int cy = 0; cy < floorHeight - 2; cy++) {
                    world.setBlock(new BlockPos(ox + cx, floorY + 1 + cy, oz + cz),
                        Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Barrel seats around fire
        for (int side = -1; side <= 1; side += 2) {
            world.setBlock(new BlockPos(ox + side * 3, floorY + 1, oz), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox, floorY + 1, oz + side * 3), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Throne facing the fire (south side, facing north toward fire)
        world.setBlock(new BlockPos(ox, floorY + 1, oz + halfDepth - 3),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);

        // Heavy log pillars with Norse rune stones at the base
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 2; z <= halfDepth / 2; z += 4) {
                BlockPos pillarBase = new BlockPos(ox + side * (halfWidth - 3), floorY + 1, oz + z);
                // Rune stone (chiseled stone bricks) at the base of each pillar
                world.setBlock(pillarBase, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
                for (int y = 1; y < floorHeight - 2; y++) {
                    world.setBlock(pillarBase.above(y), palette.getLogState(), StructureHelper.SET_FLAGS);
                }
            }
        }
    }

    private void buildGreatHallSnowy(ServerLevel world, int ox, int oz, int floorY, int halfWidth, int halfDepth) {
        // Central fire pit — soul campfire on packed_ice platform
        BlockPos pitPos = new BlockPos(ox, floorY + 1, oz);
        world.setBlock(pitPos, Blocks.PACKED_ICE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(pitPos.above(), Blocks.SOUL_CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Blue ice pillars
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 2; z <= halfDepth / 2; z += 4) {
                BlockPos pillarBase = new BlockPos(ox + side * (halfWidth - 3), floorY + 1, oz + z);
                for (int y = 0; y < floorHeight - 2; y++) {
                    world.setBlock(pillarBase.above(y), Blocks.BLUE_ICE.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Carpet everywhere for insulation — fill the floor interior
        BlockState carpet = palette.getCarpetState();
        for (int x = -halfWidth + 2; x <= halfWidth - 2; x++) {
            for (int z = -halfDepth + 2; z <= halfDepth - 2; z++) {
                BlockPos carpetPos = new BlockPos(ox + x, floorY + 1, oz + z);
                // Skip positions occupied by pillars, fire pit, and throne
                if (world.getBlockState(carpetPos).isAir()) {
                    world.setBlock(carpetPos, carpet, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Throne at back, keep it
        world.setBlock(new BlockPos(ox, floorY + 1, oz + halfDepth - 3), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, floorY + 2, oz + halfDepth - 3),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Wool "tapestries" on walls (colored wool blocks)
        BlockState wool = Blocks.LIGHT_BLUE_WOOL.defaultBlockState();
        int tapestrySpacing = 4;
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += tapestrySpacing) {
            // North wall tapestries
            world.setBlock(new BlockPos(ox + x, floorY + 2, oz - halfDepth + 1), wool, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, floorY + 3, oz - halfDepth + 1), wool, StructureHelper.SET_FLAGS);
            // South wall tapestries
            world.setBlock(new BlockPos(ox + x, floorY + 2, oz + halfDepth - 1), wool, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, floorY + 3, oz + halfDepth - 1), wool, StructureHelper.SET_FLAGS);
        }
    }

    private void buildGreatHallSavanna(ServerLevel world, int ox, int oz, int floorY, int halfWidth, int halfDepth) {
        // Open-air feel — no carpets. Floor is already the palette floor (packed_mud for savanna).

        // Potted plants (potted acacia saplings) scattered around
        int plantSpacing = 4;
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += plantSpacing) {
            world.setBlock(new BlockPos(ox + x, floorY + 1, oz - halfDepth + 2),
                Blocks.POTTED_ACACIA_SAPLING.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, floorY + 1, oz + halfDepth - 2),
                Blocks.POTTED_ACACIA_SAPLING.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Central utilitarian workspace — grindstone + crafting table
        world.setBlock(new BlockPos(ox, floorY + 1, oz), Blocks.GRINDSTONE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 1, floorY + 1, oz), Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - 1, floorY + 1, oz), Blocks.SMITHING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Fewer furnishings, minimal pillars — just 2 per side
        for (int side = -1; side <= 1; side += 2) {
            for (int z = -halfDepth / 3; z <= halfDepth / 3; z += halfDepth / 2) {
                BlockPos pillarBase = new BlockPos(ox + side * (halfWidth - 3), floorY + 1, oz + z);
                for (int y = 0; y < floorHeight - 2; y++) {
                    world.setBlock(pillarBase.above(y), palette.getLogState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Ancestor shrine — sacred ancestral veneration spot (Great Zimbabwe inspired)
        // Placed in the southeast corner of the great hall
        int shrineX = ox + halfWidth - 4;
        int shrineZ = oz + halfDepth - 4;
        int shrineY = floorY + 1;

        // 2x2 raised platform of yellow terracotta
        world.setBlock(new BlockPos(shrineX, shrineY, shrineZ),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(shrineX + 1, shrineY, shrineZ),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(shrineX, shrineY, shrineZ + 1),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(shrineX + 1, shrineY, shrineZ + 1),
            Blocks.YELLOW_TERRACOTTA.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Soul lantern on top — ancestral flame
        world.setBlock(new BlockPos(shrineX, shrineY + 1, shrineZ),
            Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // Potted flowers on either side
        world.setBlock(new BlockPos(shrineX + 1, shrineY + 1, shrineZ),
            Blocks.POTTED_ALLIUM.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(shrineX, shrineY + 1, shrineZ + 1),
            Blocks.POTTED_CORNFLOWER.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    private void buildArmory(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Armor stands (represented by fences with banners)
        BlockState fence = palette.getFenceState();
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += 3) {
            world.setBlock(new BlockPos(origin.getX() + x, floorY + 1, origin.getZ() - halfDepth + 2), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(origin.getX() + x, floorY + 2, origin.getZ() - halfDepth + 2), fence, StructureHelper.SET_FLAGS);
        }

        // Weapon racks (item frames on walls would go here in actual NBT)
        // For generation, we'll place fence posts as weapon stands
        for (int z = -halfDepth + 3; z <= halfDepth - 3; z += 4) {
            world.setBlock(new BlockPos(origin.getX() - halfWidth + 2, floorY + 1, origin.getZ() + z), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ() + z), fence, StructureHelper.SET_FLAGS);
        }

        // Workstations — weaponsmith, armorer, toolsmith
        world.setBlock(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ()),
            Blocks.GRINDSTONE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ() + 2),
            Blocks.BLAST_FURNACE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ() - 2),
            Blocks.SMITHING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Chest with loot (armory-appropriate loot)
        StructureHelper.placeChest(world, new BlockPos(origin.getX(), floorY + 1, origin.getZ() + halfDepth - 2),
            Direction.NORTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);
    }

    private void buildLivingQuarters(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Beds along walls - proper bed placement with head and foot
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += 4) {
            BlockPos footPos = new BlockPos(origin.getX() + x, floorY + 1, origin.getZ() - halfDepth + 2);
            BlockPos headPos = new BlockPos(origin.getX() + x, floorY + 1, origin.getZ() - halfDepth + 3);
            // Bed faces south (head toward wall), foot toward room
            world.setBlock(footPos, palette.getBedState()
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlock(headPos, palette.getBedState()
                .setValue(BedBlock.PART, BedPart.HEAD)
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // Bookshelves
        BlockState bookshelf = Blocks.BOOKSHELF.defaultBlockState();
        for (int z = 0; z <= halfDepth - 2; z += 2) {
            world.setBlock(new BlockPos(origin.getX() - halfWidth + 2, floorY + 1, origin.getZ() + z), bookshelf, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(origin.getX() - halfWidth + 2, floorY + 2, origin.getZ() + z), bookshelf, StructureHelper.SET_FLAGS);
        }

        // Crafting area
        world.setBlock(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ()), Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ() + 1),
            Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);

        // Workstations — librarian (near bookshelves), shepherd
        world.setBlock(new BlockPos(origin.getX() - halfWidth + 2, floorY + 1, origin.getZ() + halfDepth - 2),
            Blocks.LECTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(origin.getX() + halfWidth - 2, floorY + 1, origin.getZ() - 2),
            Blocks.LOOM.defaultBlockState(), StructureHelper.SET_FLAGS);
    }

    private void buildLordsChamber(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;
        int ox = origin.getX();
        int oz = origin.getZ();

        // Large bed (proper bed block - center bed only, sides have decorative blocks)
        // Main bed in center facing north (head at wall)
        BlockPos bedFoot = new BlockPos(ox, floorY + 1, oz + halfDepth - 3);
        BlockPos bedHead = new BlockPos(ox, floorY + 1, oz + halfDepth - 2);
        world.setBlock(bedFoot, palette.getBedState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(bedHead, palette.getBedState()
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Decorative carpets on sides of bed
        world.setBlock(new BlockPos(ox - 1, floorY + 1, oz + halfDepth - 2), palette.getCarpetState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 1, floorY + 1, oz + halfDepth - 2), palette.getCarpetState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - 1, floorY + 1, oz + halfDepth - 3), palette.getCarpetState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 1, floorY + 1, oz + halfDepth - 3), palette.getCarpetState(), StructureHelper.SET_FLAGS);

        // Desk — cartography table (war planning) and brewing stand (apothecary)
        world.setBlock(new BlockPos(ox - halfWidth + 3, floorY + 1, oz),
            Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + 4, floorY + 1, oz),
            Blocks.BREWING_STAND.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Treasure chest with loot table
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - 3, floorY + 1, oz + halfDepth - 2),
            Direction.WEST, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);

        // Storage barrel (lord's valuables)
        world.setBlock(new BlockPos(ox + halfWidth - 3, floorY + 1, oz - halfDepth + 2),
            Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Balcony (cut opening in front wall)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 2; y++) {
                world.setBlock(new BlockPos(ox + x, floorY + y, oz - halfDepth), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Balcony platform
        for (int x = -2; x <= 2; x++) {
            world.setBlock(new BlockPos(ox + x, floorY, oz - halfDepth - 1), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, floorY, oz - halfDepth - 2), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Balcony support pillars/brackets — stone brackets at x = -2 and x = +2
        for (int bx : new int[]{-2, 2}) {
            world.setBlock(new BlockPos(ox + bx, floorY - 1, oz - halfDepth - 1), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + bx, floorY - 1, oz - halfDepth - 2), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Balcony railing
        for (int x = -2; x <= 2; x++) {
            world.setBlock(new BlockPos(ox + x, floorY + 1, oz - halfDepth - 2), palette.getWallState(), StructureHelper.SET_FLAGS);
        }
        world.setBlock(new BlockPos(ox - 2, floorY + 1, oz - halfDepth - 1), palette.getWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 2, floorY + 1, oz - halfDepth - 1), palette.getWallState(), StructureHelper.SET_FLAGS);
    }

    private void addWindows(ServerLevel world, BlockPos origin, int windowY, int halfWidth, int halfDepth, int windowSpacing) {
        BlockState bars = palette.getBarsState();
        int ox = origin.getX();
        int oz = origin.getZ();

        // North and South walls
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += windowSpacing) {
            world.setBlock(new BlockPos(ox + x, windowY, oz - halfDepth), bars, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, windowY + 1, oz - halfDepth), bars, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, windowY, oz + halfDepth), bars, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, windowY + 1, oz + halfDepth), bars, StructureHelper.SET_FLAGS);
        }

        // East and West walls
        for (int z = -halfDepth + 3; z <= halfDepth - 3; z += windowSpacing) {
            world.setBlock(new BlockPos(ox - halfWidth, windowY, oz + z), bars, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - halfWidth, windowY + 1, oz + z), bars, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, windowY, oz + z), bars, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, windowY + 1, oz + z), bars, StructureHelper.SET_FLAGS);
        }
    }

    private void addFloorLighting(ServerLevel world, BlockPos origin, int lightY, int halfWidth, int halfDepth) {
        int ox = origin.getX();
        int oz = origin.getZ();
        int torchY = lightY; // lightY is already floorY + 2 (eye level)

        // 1. Wall torches on all 4 walls every 3 blocks, facing away from the wall
        int torchSpacing = 3;

        // North wall torches (face south, away from wall)
        for (int x = -halfWidth + 2; x <= halfWidth - 2; x += torchSpacing) {
            world.setBlock(new BlockPos(ox + x, torchY, oz - halfDepth + 1),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH),
                StructureHelper.SET_FLAGS);
        }

        // South wall torches (face north, away from wall)
        for (int x = -halfWidth + 2; x <= halfWidth - 2; x += torchSpacing) {
            world.setBlock(new BlockPos(ox + x, torchY, oz + halfDepth - 1),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH),
                StructureHelper.SET_FLAGS);
        }

        // East wall torches (face west, away from wall)
        for (int z = -halfDepth + 2; z <= halfDepth - 2; z += torchSpacing) {
            world.setBlock(new BlockPos(ox + halfWidth - 1, torchY, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST),
                StructureHelper.SET_FLAGS);
        }

        // West wall torches (face east, away from wall)
        for (int z = -halfDepth + 2; z <= halfDepth - 2; z += torchSpacing) {
            world.setBlock(new BlockPos(ox - halfWidth + 1, torchY, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST),
                StructureHelper.SET_FLAGS);
        }

        // 2. Hanging lanterns along north and south walls every 3 blocks (reduced from 5)
        int ceilingY = lightY + floorHeight - 3; // Just below ceiling
        int lanternSpacing = 3;

        for (int x = -halfWidth + 2; x <= halfWidth - 2; x += lanternSpacing) {
            // North wall side - place support block then hanging lantern
            BlockPos supportN = new BlockPos(ox + x, ceilingY + 1, oz - halfDepth + 2);
            BlockPos lanternN = new BlockPos(ox + x, ceilingY, oz - halfDepth + 2);
            world.setBlock(supportN, palette.getPlanksState(), StructureHelper.SET_FLAGS);
            world.setBlock(lanternN, palette.light.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);

            // South wall side
            BlockPos supportS = new BlockPos(ox + x, ceilingY + 1, oz + halfDepth - 2);
            BlockPos lanternS = new BlockPos(ox + x, ceilingY, oz + halfDepth - 2);
            world.setBlock(supportS, palette.getPlanksState(), StructureHelper.SET_FLAGS);
            world.setBlock(lanternS, palette.light.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        }

        // 3. Center chandelier for larger rooms (size >= 1): fence post hanging from ceiling with lantern beneath
        if (size >= 1) {
            BlockPos chandelierSupport = new BlockPos(ox, ceilingY + 1, oz);
            BlockPos chandelierFence = new BlockPos(ox, ceilingY, oz);
            BlockPos chandelierLantern = new BlockPos(ox, ceilingY - 1, oz);
            world.setBlock(chandelierSupport, palette.getPlanksState(), StructureHelper.SET_FLAGS);
            world.setBlock(chandelierFence, palette.getFenceState(), StructureHelper.SET_FLAGS);
            world.setBlock(chandelierLantern, palette.light.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        }
    }

    private void addStaircase(ServerLevel world, BlockPos origin, int floorY) {
        int halfWidth = width / 2;
        int halfDepth = depth / 2;

        // Staircase in corner - proper L-shaped staircase with support
        int ox = origin.getX() + halfWidth - 4;
        int oz = origin.getZ() + halfDepth - 2;

        // Stair facing: you face the direction the stair points when climbing
        // Going north (negative Z) means facing north
        BlockState stairNorth = palette.woodStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.NORTH);
        BlockState stairWest = palette.woodStairs.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.WEST);

        // Place stairs — each tread sits one block higher and one block north
        // Only place a support block directly beneath each tread (not a solid triangle)
        for (int i = 0; i < floorHeight - 1; i++) {
            BlockPos stairPos = new BlockPos(ox, floorY + 1 + i, oz - i);
            world.setBlock(stairPos, stairNorth, StructureHelper.SET_FLAGS);

            // Support: fill column below this tread down to the floor
            // but leave the block at walking height (floorY+1) open for passage under the stairs
            for (int below = floorY + 2; below < floorY + 1 + i; below++) {
                world.setBlock(new BlockPos(ox, below, oz - i), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }

        // Landing at top
        BlockPos landingPos = new BlockPos(ox, floorY + floorHeight, oz - floorHeight + 1);
        world.setBlock(landingPos, palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(landingPos.east(), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(landingPos.north(), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlock(landingPos.north().east(), palette.getPlanksState(), StructureHelper.SET_FLAGS);

        // Clear headroom above landing so player can step off the stairs
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                for (int dy = 1; dy <= 2; dy++) {
                    world.setBlock(landingPos.offset(x, dy, -z),
                        Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Add railing around stairwell opening
        world.setBlock(landingPos.west().above(), palette.getFenceState(), StructureHelper.SET_FLAGS);
        world.setBlock(landingPos.south().above(), palette.getFenceState(), StructureHelper.SET_FLAGS);
    }

    private void buildRoof(ServerLevel world, BlockPos origin, int roofY, int halfWidth, int halfDepth) {
        int ox = origin.getX();
        int oz = origin.getZ();

        switch (palette) {
            case DESERT -> buildRoofDesert(world, ox, oz, roofY, halfWidth, halfDepth);
            case TAIGA -> buildRoofTaiga(world, ox, oz, roofY, halfWidth, halfDepth);
            case SNOWY -> buildRoofSnowy(world, ox, oz, roofY, halfWidth, halfDepth);
            case SAVANNA -> buildRoofSavanna(world, ox, oz, roofY, halfWidth, halfDepth);
            default -> buildRoofPlains(world, ox, oz, roofY, halfWidth, halfDepth);
        }
    }

    private void buildRoofPlains(ServerLevel world, int ox, int oz, int roofY, int halfWidth, int halfDepth) {
        // Flat roof + crenellations (classic medieval)
        BlockPos roofCorner1 = new BlockPos(ox - halfWidth, roofY, oz - halfDepth);
        BlockPos roofCorner2 = new BlockPos(ox + halfWidth, roofY, oz + halfDepth);
        StructureHelper.fillFloor(world, roofCorner1, roofCorner2, roofY, palette.getPrimaryWallState());
        StructureHelper.addCrenellations(world, roofCorner1, roofCorner2, roofY + 1, palette.getPrimaryWallState());
    }

    private void buildRoofDesert(ServerLevel world, int ox, int oz, int roofY, int halfWidth, int halfDepth) {
        // Flat roof
        BlockPos roofCorner1 = new BlockPos(ox - halfWidth, roofY, oz - halfDepth);
        BlockPos roofCorner2 = new BlockPos(ox + halfWidth, roofY, oz + halfDepth);
        StructureHelper.fillFloor(world, roofCorner1, roofCorner2, roofY, palette.getPrimaryWallState());

        // Raised parapet edge — solid wall blocks 2 high around perimeter (no crenellation gaps)
        BlockState wallState = palette.getPrimaryWallState();
        for (int y = 1; y <= 2; y++) {
            // North and South walls
            for (int x = -halfWidth; x <= halfWidth; x++) {
                world.setBlock(new BlockPos(ox + x, roofY + y, oz - halfDepth), wallState, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + x, roofY + y, oz + halfDepth), wallState, StructureHelper.SET_FLAGS);
            }
            // East and West walls
            for (int z = -halfDepth; z <= halfDepth; z++) {
                world.setBlock(new BlockPos(ox - halfWidth, roofY + y, oz + z), wallState, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + halfWidth, roofY + y, oz + z), wallState, StructureHelper.SET_FLAGS);
            }
        }

        // Narrow viewing slits in the parapet (every 4 blocks, cut 1-block gap at eye level)
        for (int x = -halfWidth + 2; x <= halfWidth - 2; x += 4) {
            world.setBlock(new BlockPos(ox + x, roofY + 2, oz - halfDepth), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, roofY + 2, oz + halfDepth), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        for (int z = -halfDepth + 2; z <= halfDepth - 2; z += 4) {
            world.setBlock(new BlockPos(ox - halfWidth, roofY + 2, oz + z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, roofY + 2, oz + z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
    }

    private void buildRoofTaiga(ServerLevel world, int ox, int oz, int roofY, int halfWidth, int halfDepth) {
        // Flat roof base first
        BlockPos roofCorner1 = new BlockPos(ox - halfWidth, roofY, oz - halfDepth);
        BlockPos roofCorner2 = new BlockPos(ox + halfWidth, roofY, oz + halfDepth);
        StructureHelper.fillFloor(world, roofCorner1, roofCorner2, roofY, palette.getPrimaryWallState());

        // Peaked A-frame roof — ridge runs along X axis (width)
        // Peak height = halfDepth / 2 above roofY
        int peakHeight = halfDepth / 2;
        BlockState roofBlock = Blocks.DEEPSLATE_TILES.defaultBlockState();

        // Build the A-frame: for each row of z from center outward, place stairs
        // North side: stairs facing north (outer face), rising toward center
        // South side: stairs facing south (outer face), rising toward center
        for (int dz = 0; dz <= halfDepth; dz++) {
            // Height at this z distance from center: linearly interpolate from peak to 0
            int heightAtZ = peakHeight - (dz * peakHeight / halfDepth);
            if (heightAtZ < 0) heightAtZ = 0;

            // North slope (negative z side)
            if (dz > 0) {
                BlockState northStair = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH); // Facing south = stair surface faces north (outward)
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    // Place the stair at the top of this column
                    world.setBlock(new BlockPos(ox + x, roofY + heightAtZ + 1, oz - dz), northStair, StructureHelper.SET_FLAGS);
                    // Fill below with roof block to prevent gaps
                    for (int fy = 1; fy <= heightAtZ; fy++) {
                        world.setBlock(new BlockPos(ox + x, roofY + fy, oz - dz), roofBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }

            // South slope (positive z side)
            if (dz > 0) {
                BlockState southStair = Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH); // Facing north = stair surface faces south (outward)
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    world.setBlock(new BlockPos(ox + x, roofY + heightAtZ + 1, oz + dz), southStair, StructureHelper.SET_FLAGS);
                    for (int fy = 1; fy <= heightAtZ; fy++) {
                        world.setBlock(new BlockPos(ox + x, roofY + fy, oz + dz), roofBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }

            // Ridge row at dz == 0 — solid roof block at peak
            if (dz == 0) {
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    for (int fy = 1; fy <= peakHeight; fy++) {
                        world.setBlock(new BlockPos(ox + x, roofY + fy, oz), roofBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Log beams along the ridge
        for (int x = -halfWidth; x <= halfWidth; x++) {
            world.setBlock(new BlockPos(ox + x, roofY + peakHeight + 1, oz), palette.getLogState(), StructureHelper.SET_FLAGS);
        }

        // Gold roof accent stripe along the ridge peak — Valhalla's golden shields
        for (int x = -halfWidth; x <= halfWidth; x++) {
            world.setBlock(new BlockPos(ox + x, roofY + peakHeight + 2, oz),
                Blocks.YELLOW_TERRACOTTA.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
    }

    private void buildRoofSnowy(ServerLevel world, int ox, int oz, int roofY, int halfWidth, int halfDepth) {
        // Flat roof base first
        BlockPos roofCorner1 = new BlockPos(ox - halfWidth, roofY, oz - halfDepth);
        BlockPos roofCorner2 = new BlockPos(ox + halfWidth, roofY, oz + halfDepth);
        StructureHelper.fillFloor(world, roofCorner1, roofCorner2, roofY, palette.getPrimaryWallState());

        // Same peaked shape as taiga but steeper: peak height = halfDepth * 2/3
        int peakHeight = (halfDepth * 2) / 3;
        BlockState roofBlock = Blocks.STONE_BRICKS.defaultBlockState();

        for (int dz = 0; dz <= halfDepth; dz++) {
            int heightAtZ = peakHeight - (dz * peakHeight / halfDepth);
            if (heightAtZ < 0) heightAtZ = 0;

            // North slope
            if (dz > 0) {
                BlockState northStair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH);
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    world.setBlock(new BlockPos(ox + x, roofY + heightAtZ + 1, oz - dz), northStair, StructureHelper.SET_FLAGS);
                    // Snow layer on top of each stair
                    world.setBlock(new BlockPos(ox + x, roofY + heightAtZ + 2, oz - dz),
                        Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), StructureHelper.SET_FLAGS);
                    // Fill below
                    for (int fy = 1; fy <= heightAtZ; fy++) {
                        world.setBlock(new BlockPos(ox + x, roofY + fy, oz - dz), roofBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }

            // South slope
            if (dz > 0) {
                BlockState southStair = Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.NORTH);
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    world.setBlock(new BlockPos(ox + x, roofY + heightAtZ + 1, oz + dz), southStair, StructureHelper.SET_FLAGS);
                    world.setBlock(new BlockPos(ox + x, roofY + heightAtZ + 2, oz + dz),
                        Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1), StructureHelper.SET_FLAGS);
                    for (int fy = 1; fy <= heightAtZ; fy++) {
                        world.setBlock(new BlockPos(ox + x, roofY + fy, oz + dz), roofBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }

            // Ridge
            if (dz == 0) {
                for (int x = -halfWidth; x <= halfWidth; x++) {
                    for (int fy = 1; fy <= peakHeight; fy++) {
                        world.setBlock(new BlockPos(ox + x, roofY + fy, oz), roofBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Log beams along the ridge
        for (int x = -halfWidth; x <= halfWidth; x++) {
            world.setBlock(new BlockPos(ox + x, roofY + peakHeight + 1, oz), palette.getLogState(), StructureHelper.SET_FLAGS);
        }
    }

    private void buildRoofSavanna(ServerLevel world, int ox, int oz, int roofY, int halfWidth, int halfDepth) {
        // Flat roof
        BlockPos roofCorner1 = new BlockPos(ox - halfWidth, roofY, oz - halfDepth);
        BlockPos roofCorner2 = new BlockPos(ox + halfWidth, roofY, oz + halfDepth);
        StructureHelper.fillFloor(world, roofCorner1, roofCorner2, roofY, palette.getPrimaryWallState());

        // Decorative terracotta rim — 1-high wall of orange_terracotta around edge
        BlockState terracotta = Blocks.ORANGE_TERRACOTTA.defaultBlockState();
        // North and South walls
        for (int x = -halfWidth; x <= halfWidth; x++) {
            world.setBlock(new BlockPos(ox + x, roofY + 1, oz - halfDepth), terracotta, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, roofY + 1, oz + halfDepth), terracotta, StructureHelper.SET_FLAGS);
        }
        // East and West walls
        for (int z = -halfDepth; z <= halfDepth; z++) {
            world.setBlock(new BlockPos(ox - halfWidth, roofY + 1, oz + z), terracotta, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, roofY + 1, oz + z), terracotta, StructureHelper.SET_FLAGS);
        }

        // Ring of MUD_BRICK_SLAB as cap on top of the terracotta rim
        BlockState mudSlab = Blocks.MUD_BRICK_SLAB.defaultBlockState();
        for (int x = -halfWidth; x <= halfWidth; x++) {
            world.setBlock(new BlockPos(ox + x, roofY + 2, oz - halfDepth), mudSlab, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, roofY + 2, oz + halfDepth), mudSlab, StructureHelper.SET_FLAGS);
        }
        for (int z = -halfDepth + 1; z <= halfDepth - 1; z++) {
            world.setBlock(new BlockPos(ox - halfWidth, roofY + 2, oz + z), mudSlab, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, roofY + 2, oz + z), mudSlab, StructureHelper.SET_FLAGS);
        }
    }

    private void addCornerTurrets(ServerLevel world, BlockPos origin, int halfWidth, int halfDepth, int height) {
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
            world.setBlock(corner.above(turretHeight + 1), palette.getRoofState(), StructureHelper.SET_FLAGS);
        }
    }

    private void buildEntrance(ServerLevel world, BlockPos origin) {
        int halfDepth = depth / 2;

        if (size == 0) {
            buildSmallEntrance(world, origin, halfDepth);
            return;
        }

        switch (palette) {
            case DESERT -> buildEntranceDesert(world, origin, halfDepth);
            case TAIGA -> buildEntranceTaiga(world, origin, halfDepth);
            case SNOWY -> buildEntranceSnowy(world, origin, halfDepth);
            case SAVANNA -> buildEntranceSavanna(world, origin, halfDepth);
            default -> buildEntrancePlains(world, origin, halfDepth);
        }
    }

    /**
     * Simple entrance for small (size 0) tower keeps.
     * 2-wide, 3-high doorway with an actual door block.
     */
    private void buildSmallEntrance(ServerLevel world, BlockPos origin, int halfDepth) {
        BlockPos doorBase = origin.offset(0, 0, -halfDepth);

        // Clear a 2-wide, 3-high doorway
        for (int x = 0; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlock(doorBase.offset(x, y, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Place doors on each side (palette-specific)
        world.setBlock(doorBase.offset(0, 1, 0), palette.door.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(0, 2, 0), palette.door.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(1, 1, 0), palette.door.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(1, 2, 0), palette.door.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), StructureHelper.SET_FLAGS);

        // Simple step
        BlockState stair = palette.stoneStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int x = -1; x <= 2; x++) {
            world.setBlock(doorBase.offset(x, 0, -1), stair, StructureHelper.SET_FLAGS);
        }
    }

    private void buildEntrancePlains(ServerLevel world, BlockPos origin, int halfDepth) {
        // Main doorway on front (north) side
        BlockPos doorBase = origin.offset(0, 0, -halfDepth);

        // Clear doorway (3 wide, 4 high)
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 4; y++) {
                world.setBlock(doorBase.offset(x, y, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Arch top
        world.setBlock(doorBase.offset(-1, 4, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(1, 4, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);

        // Steps leading up
        BlockState stair = palette.stoneStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int x = -2; x <= 2; x++) {
            world.setBlock(doorBase.offset(x, 0, -1), stair, StructureHelper.SET_FLAGS);
        }
    }

    private void buildEntranceDesert(ServerLevel world, BlockPos origin, int halfDepth) {
        // Wide entrance (5 wide, 5 high) with pointed arch
        BlockPos doorBase = origin.offset(0, 0, -halfDepth);

        // Clear doorway (5 wide, 5 high)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 5; y++) {
                world.setBlock(doorBase.offset(x, y, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Pointed arch using smooth_sandstone — narrow the top
        BlockState archBlock = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        // Row 5: fill edges to create pointed shape
        world.setBlock(doorBase.offset(-2, 5, 0), archBlock, StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(2, 5, 0), archBlock, StructureHelper.SET_FLAGS);
        // Row 5 middle stays open (already cleared)
        // Row 6 (above): narrow further for the point
        world.setBlock(doorBase.offset(-1, 5, 0), archBlock, StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(1, 5, 0), archBlock, StructureHelper.SET_FLAGS);
        // Apex
        world.setBlock(doorBase.offset(0, 6, 0), archBlock, StructureHelper.SET_FLAGS);

        // Iron bar portcullis frame at the outer face (z-1)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                world.setBlock(doorBase.offset(x, y, -1), Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Steps made of sandstone_stairs
        BlockState stair = Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int x = -3; x <= 3; x++) {
            world.setBlock(doorBase.offset(x, 0, -2), stair, StructureHelper.SET_FLAGS);
        }
    }

    private void buildEntranceTaiga(ServerLevel world, BlockPos origin, int halfDepth) {
        // Narrow fortified entrance (3 wide, 3 high), thick arch frame (2 deep)
        BlockPos doorBase = origin.offset(0, 0, -halfDepth);

        // Clear doorway (3 wide, 3 high) — 2 blocks deep
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlock(doorBase.offset(x, y, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(doorBase.offset(x, y, -1), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Heavy log frame around the doorway — outer face (z=0)
        BlockState logState = palette.getLogState();
        // Left and right columns
        for (int y = 1; y <= 3; y++) {
            world.setBlock(doorBase.offset(-2, y, 0), logState, StructureHelper.SET_FLAGS);
            world.setBlock(doorBase.offset(2, y, 0), logState, StructureHelper.SET_FLAGS);
            world.setBlock(doorBase.offset(-2, y, -1), logState, StructureHelper.SET_FLAGS);
            world.setBlock(doorBase.offset(2, y, -1), logState, StructureHelper.SET_FLAGS);
        }
        // Top beam (lintel)
        for (int x = -2; x <= 2; x++) {
            world.setBlock(doorBase.offset(x, 4, 0), logState, StructureHelper.SET_FLAGS);
            world.setBlock(doorBase.offset(x, 4, -1), logState, StructureHelper.SET_FLAGS);
        }

        // Place door blocks at outer face (palette-specific)
        world.setBlock(doorBase.offset(0, 1, 0), palette.door.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(0, 2, 0), palette.door.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), StructureHelper.SET_FLAGS);
    }

    private void buildEntranceSnowy(ServerLevel world, BlockPos origin, int halfDepth) {
        // Recessed porch — clear a 5-wide, 4-high area, doorway 2 blocks in from wall face
        BlockPos doorBase = origin.offset(0, 0, -halfDepth);

        // Clear the full porch area (5 wide, 4 high, 2 deep into the wall)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                world.setBlock(doorBase.offset(x, y, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(doorBase.offset(x, y, 1), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Porch overhang — roof extends out over entrance
        for (int x = -2; x <= 2; x++) {
            world.setBlock(doorBase.offset(x, 5, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(doorBase.offset(x, 5, -1), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(doorBase.offset(x, 5, -2), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Side walls of the porch recess
        for (int y = 1; y <= 4; y++) {
            world.setBlock(doorBase.offset(-2, y, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlock(doorBase.offset(2, y, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Doorway is 2 blocks recessed (at z+1 — inside the wall)
        // The actual door at z+1 (recessed position)
        world.setBlock(doorBase.offset(0, 1, 1), Blocks.SPRUCE_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(0, 2, 1), Blocks.SPRUCE_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), StructureHelper.SET_FLAGS);

        // Floor of porch
        for (int x = -1; x <= 1; x++) {
            world.setBlock(doorBase.offset(x, 0, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // Steps leading to porch
        BlockState stair = palette.stoneStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int x = -2; x <= 2; x++) {
            world.setBlock(doorBase.offset(x, 0, -1), stair, StructureHelper.SET_FLAGS);
        }
    }

    private void buildEntranceSavanna(ServerLevel world, BlockPos origin, int halfDepth) {
        // Open archway (5 wide, 4 high) with acacia fence gate
        BlockPos doorBase = origin.offset(0, 0, -halfDepth);

        // Clear doorway (5 wide, 4 high)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                world.setBlock(doorBase.offset(x, y, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Mud brick arch with stepped profile — narrowing at top
        BlockState mudBrick = Blocks.MUD_BRICKS.defaultBlockState();
        // Row 4: fill outer edges to start the step
        world.setBlock(doorBase.offset(-2, 4, 0), mudBrick, StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(2, 4, 0), mudBrick, StructureHelper.SET_FLAGS);
        // Row 5 (above doorway): narrow step — bridge the gap
        for (int x = -2; x <= 2; x++) {
            world.setBlock(doorBase.offset(x, 5, 0), mudBrick, StructureHelper.SET_FLAGS);
        }
        // Clear center of row 5 for the stepped look
        world.setBlock(doorBase.offset(-1, 5, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(0, 5, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(doorBase.offset(1, 5, 0), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Acacia fence gate across the base of the entrance
        for (int x = -2; x <= 2; x++) {
            world.setBlock(doorBase.offset(x, 1, 0), Blocks.ACACIA_FENCE_GATE.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }

        // Steps — wider and welcoming
        BlockState stair = Blocks.MUD_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        for (int x = -3; x <= 3; x++) {
            world.setBlock(doorBase.offset(x, 0, -1), stair, StructureHelper.SET_FLAGS);
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
