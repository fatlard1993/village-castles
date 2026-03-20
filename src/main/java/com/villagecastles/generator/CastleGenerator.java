package com.villagecastles.generator;

import com.villagecastles.VillageCastles;
import com.villagecastles.util.StructureHelper;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.Orientation;
import net.minecraft.block.entity.JigsawBlockEntity;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.pool.StructurePool;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Random;

/**
 * Main orchestrator for castle generation.
 * Assembles keep, walls, towers, and gates into a complete castle.
 *
 * Castle Layout (Large):
 *
 *       [T]----[Wall]----[T]
 *        |                |
 *      [Wall]   [Keep]  [Wall]
 *        |                |
 *       [T]----[Gate]----[T]
 *
 * T = Tower, [Keep] = Central building
 */
public class CastleGenerator {

    public enum CastleSize {
        SMALL(30, 0),   // ~30 blocks diameter, small keep
        MEDIUM(50, 1),  // ~50 blocks diameter, medium keep
        LARGE(70, 2);   // ~70 blocks diameter, large keep

        public final int diameter;
        public final int keepSize;

        CastleSize(int diameter, int keepSize) {
            this.diameter = diameter;
            this.keepSize = keepSize;
        }
    }

    private final BiomePalette palette;
    private final Random random;
    private final CastleSize size;

    // Sub-generators
    private final KeepGenerator keepGenerator;
    private final TowerGenerator towerGenerator;
    private final CastleWallGenerator wallGenerator;
    private final GateGenerator gateGenerator;
    private final CourtyardGenerator courtyardGenerator;

    public CastleGenerator(BiomePalette palette, long seed, CastleSize size) {
        this.palette = palette;
        this.random = new Random(seed);
        this.size = size;

        this.keepGenerator = new KeepGenerator(palette, random, size.keepSize);
        this.towerGenerator = new TowerGenerator(palette, random);
        this.wallGenerator = new CastleWallGenerator(palette, random);
        this.gateGenerator = new GateGenerator(palette, random);
        this.courtyardGenerator = new CourtyardGenerator(palette, random);
    }

    /**
     * Generate a complete castle at the specified position.
     * Structure type varies fundamentally by size:
     *   SMALL  — Tower Keep: keep + perimeter fence (fortified home)
     *   MEDIUM — Standard Fort: keep + walls + corner towers + gatehouse
     *   LARGE  — Grand Castle: everything medium has + wall towers + back gate + stables/barracks
     *
     * @param world The server world
     * @param center The center position of the castle courtyard
     * @return CastleBounds containing the generated structure's bounds
     */
    public CastleBounds generate(ServerWorld world, BlockPos center) {
        VillageCastles.LOGGER.info("Generating {} {} castle at {}",
            size.name().toLowerCase(), palette.displayName, center.toShortString());

        int radius = size.diameter / 2;
        int keepHalfWidth = keepGenerator.getWidth() / 2;
        int keepHalfDepth = keepGenerator.getDepth() / 2;

        CastleBounds bounds = switch (size) {
            case SMALL -> generateSmall(world, center, radius, keepHalfWidth, keepHalfDepth);
            case MEDIUM -> generateMedium(world, center, radius, keepHalfWidth, keepHalfDepth);
            case LARGE -> generateLarge(world, center, radius, keepHalfWidth, keepHalfDepth);
        };

        // Post-generation pass: fix connection states for fences, walls, iron bars, etc.
        StructureHelper.updateConnectionStates(world, bounds.min, bounds.max);

        return bounds;
    }

    /**
     * SMALL — Biome-specific fortified structure.
     * Each biome gets a distinct small fortification with its own architectural language.
     */
    private CastleBounds generateSmall(ServerWorld world, BlockPos center, int radius,
                                        int keepHalfWidth, int keepHalfDepth) {
        return switch (palette) {
            case PLAINS -> generatePlainsManor(world, center);
            case DESERT -> generateDesertOutpost(world, center);
            case SAVANNA -> generateSavannaEnclosure(world, center);
            case TAIGA -> generateTaigaLonghouse(world, center);
            case SNOWY -> generateIgloo(world, center);
        };
    }

    /**
     * PLAINS SMALL — Manor house.
     * The village leader's home. Stone brick ground floor, oak-framed upper floor,
     * peaked roof with chimney. Bigger than any vanilla house, smaller than a castle.
     * Ground floor: great room with hearth, dining table, workstations.
     * Upper floor: bedchamber with bed, chest, personal effects.
     * Fenced courtyard with bell, stable area.
     */
    private CastleBounds generatePlainsManor(ServerWorld world, BlockPos center) {
        // Building dimensions — wider than tall, L-shaped footprint would be ideal
        // but rectangular is more reliable for procedural gen
        int halfWidth = 7;  // east-west (15 blocks wide)
        int halfDepth = 5;  // north-south (11 blocks deep)
        int groundHeight = 4; // stone brick ground floor
        int upperHeight = 4;  // oak-framed upper floor
        int totalWall = groundHeight + upperHeight;
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // === GROUND FLOOR — Stone brick ===
        StructureHelper.fillBox(world,
            center.add(-halfWidth, 0, -halfDepth),
            center.add(halfWidth, groundHeight, halfDepth),
            palette.getPrimaryWallState());
        // Hollow ground floor interior
        StructureHelper.clearInterior(world,
            center.add(-halfWidth + 1, 1, -halfDepth + 1),
            center.add(halfWidth - 1, groundHeight, halfDepth - 1));
        // Oak plank floor
        StructureHelper.fillFloor(world,
            center.add(-halfWidth + 1, 0, -halfDepth + 1),
            center.add(halfWidth - 1, 0, halfDepth - 1),
            baseY + 1, palette.getPlanksState());

        // === UPPER FLOOR — Oak log timber frame on stone base ===
        // Upper walls: oak log frame with planks infill
        BlockState logBlock = palette.log.getDefaultState();
        BlockState planksBlock = palette.getPlanksState();
        for (int y = groundHeight + 1; y <= totalWall; y++) {
            for (int x = -halfWidth; x <= halfWidth; x++) {
                for (int z = -halfDepth; z <= halfDepth; z++) {
                    // Only wall positions
                    if (x == -halfWidth || x == halfWidth || z == -halfDepth || z == halfDepth) {
                        boolean isCorner = (x == -halfWidth || x == halfWidth) && (z == -halfDepth || z == halfDepth);
                        boolean isFrame = (x % 4 == 0) || (z % 4 == 0) || isCorner;
                        mutable.set(ox + x, baseY + y, oz + z);
                        world.setBlockState(mutable, isFrame ? logBlock : planksBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
        // Clear upper interior
        StructureHelper.clearInterior(world,
            center.add(-halfWidth + 1, groundHeight + 1, -halfDepth + 1),
            center.add(halfWidth - 1, totalWall, halfDepth - 1));
        // Upper floor surface (also ceiling of ground floor)
        StructureHelper.fillFloor(world,
            center.add(-halfWidth + 1, 0, -halfDepth + 1),
            center.add(halfWidth - 1, 0, halfDepth - 1),
            baseY + groundHeight + 1, palette.getPlanksState());

        // === PEAKED ROOF ===
        // Runs east-west (ridge along X axis)
        int roofPeak = 4;
        for (int y = 0; y <= roofPeak; y++) {
            int roofDepth = halfDepth + 1 - y;
            if (roofDepth < 0) break;
            for (int x = -halfWidth - 1; x <= halfWidth + 1; x++) {
                world.setBlockState(new BlockPos(ox + x, baseY + totalWall + y, oz - roofDepth),
                    palette.getRoofState(), StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + totalWall + y, oz + roofDepth),
                    palette.getRoofState(), StructureHelper.SET_FLAGS);
            }
        }
        // Ridge cap
        for (int x = -halfWidth - 1; x <= halfWidth + 1; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY + totalWall + roofPeak, oz),
                palette.getRoofState(), StructureHelper.SET_FLAGS);
        }

        // === CHIMNEY — stone brick, east end ===
        int chimneyX = ox + halfWidth - 2;
        for (int y = 1; y <= totalWall + roofPeak + 2; y++) {
            world.setBlockState(new BlockPos(chimneyX, baseY + y, oz),
                palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(chimneyX + 1, baseY + y, oz),
                palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        }

        // === FRONT ENTRANCE — south, covered porch ===
        // Door opening (2 wide, 3 tall)
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + halfDepth),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Porch roof (oak slab overhang)
        for (int x = -3; x <= 2; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY + 4, oz + halfDepth + 1),
                palette.woodSlab.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Porch pillars
        world.setBlockState(new BlockPos(ox - 3, baseY + 1, oz + halfDepth + 1), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 3, baseY + 2, oz + halfDepth + 1), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 3, baseY + 3, oz + halfDepth + 1), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 2, baseY + 1, oz + halfDepth + 1), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 2, baseY + 2, oz + halfDepth + 1), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 2, baseY + 3, oz + halfDepth + 1), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        // Floor plank at threshold
        world.setBlockState(new BlockPos(ox - 1, baseY + 1, oz + halfDepth), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY + 1, oz + halfDepth), palette.getPlanksState(), StructureHelper.SET_FLAGS);

        // === WINDOWS — glass panes ===
        // Ground floor windows (2 wide each, east and west walls)
        for (int z : new int[]{-2, 2}) {
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + 2, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + 3, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + 2, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + 3, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Upper floor windows
        for (int z : new int[]{-2, 2}) {
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + groundHeight + 2, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + groundHeight + 3, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + groundHeight + 2, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + groundHeight + 3, oz + z), Blocks.GLASS_PANE.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // === STAIRCASE — oak stairs, SW corner, connecting floors ===
        int stairX = ox - halfWidth + 2;
        int stairZ = oz + halfDepth - 2;
        for (int i = 0; i < groundHeight; i++) {
            world.setBlockState(new BlockPos(stairX, baseY + 2 + i, stairZ - i),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH),
                StructureHelper.SET_FLAGS);
            // Clear headroom above each step
            for (int dy = 1; dy <= 3; dy++) {
                world.setBlockState(new BlockPos(stairX, baseY + 2 + i + dy, stairZ - i),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Open the floor above the staircase landing
        for (int sx = -1; sx <= 0; sx++) {
            for (int sz = -1; sz <= 0; sz++) {
                world.setBlockState(new BlockPos(stairX + sx, baseY + groundHeight + 1, stairZ - groundHeight + 1 + sz),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // === GROUND FLOOR FURNISHING ===
        // Hearth — campfire against east wall (next to chimney)
        world.setBlockState(new BlockPos(ox + halfWidth - 3, baseY + 1, oz),
            Blocks.STONE_BRICKS.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 3, baseY + 2, oz),
            Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);

        // Dining table (center of great room) — oak slabs on fences
        for (int x = -2; x <= 1; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY + 1, oz - 1), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + 2, oz - 1), palette.woodSlab.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Chairs at table (stairs facing inward)
        world.setBlockState(new BlockPos(ox - 2, baseY + 2, oz - 2),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 1, baseY + 2, oz - 2),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 2, baseY + 2, oz),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 1, baseY + 2, oz),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);

        // Workstation area (NW corner)
        world.setBlockState(center.add(-halfWidth + 2, 2, -halfDepth + 1), Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-halfWidth + 3, 2, -halfDepth + 1), Blocks.CARTOGRAPHY_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, center.add(-halfWidth + 2, 2, -halfDepth + 2), Direction.SOUTH, LootTables.VILLAGE_PLAINS_CHEST);

        // Carpet runner from door to hearth
        for (int z = -halfDepth + 2; z <= halfDepth - 1; z++) {
            world.setBlockState(new BlockPos(ox, baseY + 2, oz + z), palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Wall torches — ground floor
        world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 3, oz - halfDepth + 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 3, oz + halfDepth - 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 3, oz - halfDepth + 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // === UPPER FLOOR FURNISHING — Bedchamber ===
        int upFloor = baseY + groundHeight + 2; // furniture Y (on upper floor)

        // Master bed (center-north)
        world.setBlockState(new BlockPos(ox + 1, upFloor, oz - halfDepth + 2), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 1, upFloor, oz - halfDepth + 3), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Second bed (guest/family)
        world.setBlockState(new BlockPos(ox + 4, upFloor, oz - halfDepth + 2), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 4, upFloor, oz - halfDepth + 3), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Personal chest
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - 2, upFloor, oz), Direction.WEST, LootTables.VILLAGE_PLAINS_CHEST);

        // Bookshelf
        world.setBlockState(new BlockPos(ox + 3, upFloor, oz - halfDepth + 1), Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 4, upFloor, oz - halfDepth + 1), Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);

        // Lectern with book
        world.setBlockState(new BlockPos(ox + 2, upFloor, oz - halfDepth + 1), Blocks.LECTERN.getDefaultState(), StructureHelper.SET_FLAGS);

        // Flower pot on windowsill (decorative touch)
        world.setBlockState(new BlockPos(ox - halfWidth + 1, upFloor, oz - 2), Blocks.FLOWER_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // Wall torches — upper floor
        world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + groundHeight + 3, oz),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + groundHeight + 3, oz),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);

        // === COURTYARD ===
        int yardRadius = Math.max(halfWidth, halfDepth) + 5;
        generatePerimeterFence(world, center, yardRadius, halfWidth, halfDepth);

        // Bell on post
        world.setBlockState(center.add(-halfWidth - 2, 0, 0), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-halfWidth - 2, 1, 0), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-halfWidth - 2, 2, 0), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Stable area (NW of yard) — fence pen with hay
        int stableX = ox - halfWidth - 3;
        int stableZ = oz - halfDepth;
        for (int x = 0; x <= 4; x++) {
            world.setBlockState(new BlockPos(stableX + x, baseY, stableZ), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(stableX + x, baseY, stableZ + 4), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        for (int z = 0; z <= 4; z++) {
            world.setBlockState(new BlockPos(stableX, baseY, stableZ + z), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(stableX + 4, baseY, stableZ + z), palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        world.setBlockState(new BlockPos(stableX + 2, baseY, stableZ + 4),
            palette.fenceGate.getDefaultState().with(net.minecraft.block.FenceGateBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(stableX + 1, baseY, stableZ + 1), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(stableX + 1, baseY + 1, stableZ + 1), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(stableX + 3, baseY, stableZ + 1), Blocks.CAULDRON.getDefaultState(), StructureHelper.SET_FLAGS);

        // Lantern at front entrance
        world.setBlockState(new BlockPos(ox - 2, baseY + 3, oz + halfDepth),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, yardRadius);

        VillageCastles.LOGGER.info("Plains manor generation complete!");
        return new CastleBounds(
            center.add(-yardRadius - 2, 0, -yardRadius - 2),
            center.add(yardRadius + 2, totalWall + roofPeak + 5, yardRadius + 2)
        );
    }

    /**
     * DESERT SMALL — Walled villa.
     * Rectangular, squat, thick sandstone walls. Flat walkable roof with parapet.
     * Open-air courtyard in the center with a water feature. Rooms around the
     * perimeter — shaded, cool, furnished. From outside: a solid sandstone block
     * with a door. From inside: comfortable and lived-in. A desert lord's home.
     */
    private CastleBounds generateDesertOutpost(ServerWorld world, BlockPos center) {
        int halfWidth = 8;   // east-west
        int halfDepth = 7;   // north-south
        int wallHeight = 5;
        int wallThickness = 2; // thick walls for thermal mass
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // === OUTER WALLS — thick sandstone, the defining visual ===
        StructureHelper.fillBox(world,
            center.add(-halfWidth, 0, -halfDepth),
            center.add(halfWidth, wallHeight, halfDepth),
            palette.getPrimaryWallState());

        // Hollow everything inside the thick walls
        StructureHelper.clearInterior(world,
            center.add(-halfWidth + wallThickness, 1, -halfDepth + wallThickness),
            center.add(halfWidth - wallThickness, wallHeight, halfDepth - wallThickness));

        // Smooth sandstone floor throughout interior
        StructureHelper.fillFloor(world,
            center.add(-halfWidth + wallThickness, 0, -halfDepth + wallThickness),
            center.add(halfWidth - wallThickness, 0, halfDepth - wallThickness),
            baseY + 1, palette.getFloorState());

        // === FLAT WALKABLE ROOF with parapet ===
        StructureHelper.fillFloor(world,
            center.add(-halfWidth, 0, -halfDepth),
            center.add(halfWidth, 0, halfDepth),
            baseY + wallHeight, palette.getFloorState());
        // Parapet (1-block wall around roof edge with gaps for crenellations)
        StructureHelper.addCrenellations(world,
            center.add(-halfWidth, 0, -halfDepth),
            center.add(halfWidth, 0, halfDepth),
            baseY + wallHeight + 1, palette.getPrimaryWallState());

        // === OPEN-AIR COURTYARD — center, 4x4 ===
        int courtHW = 2;
        // Remove the roof over the courtyard (let sky in)
        for (int x = -courtHW; x <= courtHW; x++) {
            for (int z = -courtHW; z <= courtHW; z++) {
                world.setBlockState(new BlockPos(ox + x, baseY + wallHeight, oz + z),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Water feature in courtyard center
        world.setBlockState(center.add(0, 0, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 1, 0), Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);
        // Sandstone rim around water
        for (int[] off : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
            world.setBlockState(center.add(off[0], 1, off[1]),
                palette.stoneStairs.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // === INTERIOR PARTITION — divides rooms ===
        // North-south wall dividing east rooms from west
        int dividerX = ox - 2;
        for (int z = -halfDepth + wallThickness; z <= -courtHW - 1; z++) {
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlockState(new BlockPos(dividerX, baseY + y, oz + z),
                    palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        for (int z = courtHW + 1; z <= halfDepth - wallThickness; z++) {
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlockState(new BlockPos(dividerX, baseY + y, oz + z),
                    palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        // Doorways through divider
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(new BlockPos(dividerX, baseY + y, oz - halfDepth + wallThickness + 2),
                Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(dividerX, baseY + y, oz + halfDepth - wallThickness - 2),
                Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // === FRONT ENTRANCE — south wall, recessed doorway ===
        for (int y = 1; y <= 3; y++) {
            for (int t = 0; t < wallThickness; t++) {
                world.setBlockState(new BlockPos(ox, baseY + y, oz + halfDepth - t),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Threshold plank
        world.setBlockState(new BlockPos(ox, baseY + 1, oz + halfDepth), palette.getFloorState(), StructureHelper.SET_FLAGS);

        // === NARROW WINDOW SLITS ===
        // East and west walls, staggered
        for (int z : new int[]{oz - 3, oz + 3}) {
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + 3, z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + 3, z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // North wall
        world.setBlockState(new BlockPos(ox - 3, baseY + 3, oz - halfDepth), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 3, baseY + 3, oz - halfDepth), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);

        // === BEDCHAMBER — NW room (west of divider, north of courtyard) ===
        int roomFloor = baseY + 2; // furniture sits on the smooth sandstone floor at baseY+1
        // Master bed against north wall
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz - halfDepth + wallThickness),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz - halfDepth + wallThickness + 1),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Second bed
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 3, roomFloor, oz - halfDepth + wallThickness),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 3, roomFloor, oz - halfDepth + wallThickness + 1),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Chest
        StructureHelper.placeChest(world, new BlockPos(dividerX - 1, roomFloor, oz - halfDepth + wallThickness),
            Direction.SOUTH, LootTables.VILLAGE_DESERT_HOUSE_CHEST);
        // Carpet
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 2, roomFloor, oz - courtHW - 1),
            palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);

        // === STUDY — NE room (east of divider, north of courtyard) ===
        world.setBlockState(new BlockPos(dividerX + 2, roomFloor, oz - halfDepth + wallThickness),
            Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(dividerX + 3, roomFloor, oz - halfDepth + wallThickness),
            Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(dividerX + 4, roomFloor, oz - halfDepth + wallThickness),
            Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(dividerX + 2, roomFloor, oz - halfDepth + wallThickness + 1),
            Blocks.LECTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - wallThickness - 1, roomFloor, oz - halfDepth + wallThickness),
            Direction.SOUTH, LootTables.VILLAGE_DESERT_HOUSE_CHEST);
        // Decorated pot
        world.setBlockState(new BlockPos(ox + halfWidth - wallThickness - 1, roomFloor, oz - courtHW - 1),
            Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // === KITCHEN — SW room (west of divider, south of courtyard) ===
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz + halfDepth - wallThickness - 1),
            Blocks.SMOKER.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 2, roomFloor, oz + halfDepth - wallThickness - 1),
            Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz + halfDepth - wallThickness),
            Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 2, roomFloor, oz + halfDepth - wallThickness),
            Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);

        // === AUDIENCE ROOM — SE room (east of divider, south of courtyard) ===
        // Throne/seat facing north (toward the courtyard)
        world.setBlockState(new BlockPos(ox + halfWidth - wallThickness - 2, roomFloor, oz + halfDepth - wallThickness),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        // Terracotta accent behind throne
        world.setBlockState(new BlockPos(ox + halfWidth - wallThickness - 2, roomFloor + 1, oz + halfDepth - wallThickness),
            Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        // Guest seating
        world.setBlockState(new BlockPos(dividerX + 2, roomFloor, oz + courtHW + 2),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Decorated pot
        world.setBlockState(new BlockPos(ox + halfWidth - wallThickness - 1, roomFloor, oz + courtHW + 1),
            Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // === LIGHTING ===
        // Soul lanterns in each room (hanging from ceiling)
        int ceilingY = baseY + wallHeight;
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 2, ceilingY - 1, oz - halfDepth + wallThickness + 2),
            Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(dividerX + 3, ceilingY - 1, oz - halfDepth + wallThickness + 2),
            Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 2, ceilingY - 1, oz + halfDepth - wallThickness - 2),
            Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(dividerX + 3, ceilingY - 1, oz + halfDepth - wallThickness - 2),
            Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);

        // === ROOF ACCESS — ladder in SE corner ===
        for (int y = 2; y <= wallHeight; y++) {
            world.setBlockState(new BlockPos(ox + halfWidth - wallThickness - 1, baseY + y, oz + halfDepth - wallThickness),
                Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, Direction.NORTH),
                StructureHelper.SET_FLAGS);
        }

        // === BELL — on the roof ===
        world.setBlockState(new BlockPos(ox, baseY + wallHeight + 1, oz + halfDepth - 1),
            palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY + wallHeight + 2, oz + halfDepth - 1),
            Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, Math.max(halfWidth, halfDepth));

        VillageCastles.LOGGER.info("Desert villa generation complete!");
        return new CastleBounds(
            center.add(-halfWidth - 2, 0, -halfDepth - 2),
            center.add(halfWidth + 2, wallHeight + 5, halfDepth + 2)
        );
    }

    /**
     * SAVANNA SMALL — Fortified homestead.
     * Cluster of round mud-brick huts with hay thatch roofs behind an acacia
     * palisade with dead-bush thorn barriers. Central fire pit, animal pen,
     * outdoor work area. Compact, lived-in family compound.
     */
    private CastleBounds generateSavannaEnclosure(ServerWorld world, BlockPos center) {
        int yardRadius = 13;
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockState mudBrick = Blocks.MUD_BRICKS.getDefaultState();
        BlockState packedMud = Blocks.PACKED_MUD.getDefaultState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.getDefaultState();
        BlockState fence = palette.fence.getDefaultState();
        BlockState deadBush = Blocks.DEAD_BUSH.getDefaultState();

        // Coarse dirt ground throughout compound — placed at baseY-1 to replace
        // the surface block rather than adding a raised layer on top of terrain
        for (int x = -yardRadius; x <= yardRadius; x++) {
            for (int z = -yardRadius; z <= yardRadius; z++) {
                if (x * x + z * z <= yardRadius * yardRadius) {
                    world.setBlockState(new BlockPos(ox + x, baseY - 1, oz + z), coarseDirt, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Acacia palisade fence with dead bush thorn effect
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int fx = ox + (int)(yardRadius * Math.cos(rad));
            int fz = oz + (int)(yardRadius * Math.sin(rad));
            world.setBlockState(new BlockPos(fx, baseY, fz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(fx, baseY + 1, fz), fence, StructureHelper.SET_FLAGS);
        }
        // Dead bush thorn barriers outside palisade (every ~30 degrees)
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            int bx = ox + (int)((yardRadius + 1) * Math.cos(rad));
            int bz = oz + (int)((yardRadius + 1) * Math.sin(rad));
            // Dead bush needs a valid support block beneath it — place at baseY-1 to replace terrain
            world.setBlockState(new BlockPos(bx, baseY - 1, bz), coarseDirt, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(bx, baseY, bz), deadBush, StructureHelper.SET_FLAGS);
        }

        // Main dwelling hut (north-west, radius 4, wall height 4)
        buildRoundHut(world, center.add(-4, 0, -5), 4, 4);
        // Furnish dwelling — interior floor is packed mud with carpet at y+1
        world.setBlockState(center.add(-4, 1, -5), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        // Bed 1: FACING=SOUTH means HEAD at higher Z (-6), FOOT at lower Z (-7)
        world.setBlockState(center.add(-6, 1, -7), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-6, 1, -6), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Bed 2
        world.setBlockState(center.add(-2, 1, -7), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-2, 1, -6), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Decorated pot inside dwelling
        world.setBlockState(center.add(-5, 1, -3), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // Storage hut (east, radius 4, wall height 3) — radius 4 gives usable 5x5 interior
        buildRoundHut(world, center.add(5, 0, -2), 4, 3);
        StructureHelper.placeChest(world, center.add(5, 1, -2), Direction.SOUTH, LootTables.VILLAGE_SAVANNA_HOUSE_CHEST);
        world.setBlockState(center.add(4, 1, -2), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(6, 1, -2), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Central fire pit with acacia log seating
        world.setBlockState(center, Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        for (int[] off : new int[][]{{-2, -1}, {2, -1}, {-1, 2}, {1, 2}}) {
            world.setBlockState(center.add(off[0], 0, off[1]),
                palette.log.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Terracotta accent blocks around fire pit
        world.setBlockState(center.add(-1, 0, -1), Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(1, 0, -1), Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-1, 0, 1), Blocks.BROWN_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(1, 0, 1), Blocks.BROWN_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);

        // Animal pen (south-west, acacia fence)
        for (int x = -8; x <= -3; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY, oz + 4), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY, oz + 9), fence, StructureHelper.SET_FLAGS);
        }
        for (int z = 4; z <= 9; z++) {
            world.setBlockState(new BlockPos(ox - 8, baseY, oz + z), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - 3, baseY, oz + z), fence, StructureHelper.SET_FLAGS);
        }
        // Fence gate entrance to pen (facing south, at center of south wall)
        world.setBlockState(new BlockPos(ox - 5, baseY, oz + 9),
            palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-5, 0, 7), Blocks.CAULDRON.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-6, 0, 6), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);

        // Outdoor work area (south-east) — loom, smoker, composters
        world.setBlockState(center.add(6, 0, 5), Blocks.LOOM.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(7, 0, 5), Blocks.SMOKER.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(8, 0, 6), Blocks.COMPOSTER.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(6, 0, 7), Blocks.COMPOSTER.getDefaultState(), StructureHelper.SET_FLAGS);

        // Drying rack (fence posts with carpet on top)
        for (int x = 3; x <= 7; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY, oz + 3), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + 1, oz + 3), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + 2, oz + 3), palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Decorated pots scattered around
        world.setBlockState(center.add(-1, 0, -8), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(3, 0, -4), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(8, 0, 0), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // Hay bales (food/animal feed storage)
        world.setBlockState(center.add(-7, 0, 3), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-7, 1, 3), Blocks.HAY_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);

        // Crafting table near dwelling
        world.setBlockState(center.add(-1, 0, -3), Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);

        // Entrance gate south — with 2+ blocks clearance outside
        world.setBlockState(new BlockPos(ox, baseY, oz + yardRadius),
            palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH),
            StructureHelper.SET_FLAGS);

        // Torch posts — fence base so torch has a solid support block
        int[][] torchPosts = {{-2, yardRadius}, {2, yardRadius}, {0, 3}, {-9, 0}, {9, 0}};
        for (int[] tp : torchPosts) {
            world.setBlockState(new BlockPos(ox + tp[0], baseY, oz + tp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + tp[0], baseY + 1, oz + tp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + tp[0], baseY + 2, oz + tp[1]), Blocks.TORCH.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Bell on a fence post
        world.setBlockState(center.add(0, 0, 5), fence, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 1, 5), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, yardRadius);

        VillageCastles.LOGGER.info("Savanna homestead generation complete!");
        return new CastleBounds(
            center.add(-yardRadius - 2, 0, -yardRadius - 2),
            center.add(yardRadius + 2, 12, yardRadius + 2)
        );
    }

    /**
     * Build a round thatched hut — mud brick walls with toron (protruding acacia fence
     * pegs), packed mud floor with orange carpet, conical HAY_BLOCK thatch roof,
     * door opening on south with 2+ block clearance.
     */
    private void buildRoundHut(ServerWorld world, BlockPos center, int radius, int wallHeight) {
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockState mudBrick = Blocks.MUD_BRICKS.getDefaultState();
        BlockState fence = palette.fence.getDefaultState();
        BlockState hayBlock = Blocks.HAY_BLOCK.getDefaultState();
        BlockState packedMud = Blocks.PACKED_MUD.getDefaultState();
        int radiusSq = radius * radius;
        double innerSq = (radius - 1.5) * (radius - 1.5);

        // Mud brick walls (cylinder)
        for (int y = 0; y < wallHeight; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x * x + z * z;
                    if (distSq <= radiusSq && distSq > innerSq) {
                        world.setBlockState(new BlockPos(ox + x, baseY + y, oz + z),
                            mudBrick, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Tapering transition — inverted MUD_BRICK_STAIRS at top of wall
        BlockState invertedStairs = Blocks.MUD_BRICK_STAIRS.getDefaultState()
            .with(StairsBlock.HALF, BlockHalf.TOP);
        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            int sx = ox + (int)(radius * Math.cos(rad));
            int sz = oz + (int)(radius * Math.sin(rad));
            // Determine facing: stairs face outward from center
            Direction facing;
            double dx = sx - ox;
            double dz = sz - oz;
            if (Math.abs(dx) > Math.abs(dz)) {
                facing = dx > 0 ? Direction.EAST : Direction.WEST;
            } else {
                facing = dz > 0 ? Direction.SOUTH : Direction.NORTH;
            }
            world.setBlockState(new BlockPos(sx, baseY + wallHeight, sz),
                invertedStairs.with(StairsBlock.FACING, facing), StructureHelper.SET_FLAGS);
        }

        // Toron — acacia fence pegs protruding 1 block outward from walls
        // Every 3 blocks around circumference, every 2 rows vertically
        for (int y = 1; y < wallHeight; y += 2) {
            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                // Position just outside the wall
                int tx = ox + (int)((radius + 1) * Math.cos(rad));
                int tz = oz + (int)((radius + 1) * Math.sin(rad));
                // Skip the door area (south face, near center-x)
                if (tz >= oz + radius && Math.abs(tx - ox) <= 1) continue;
                world.setBlockState(new BlockPos(tx, baseY + y, tz), fence, StructureHelper.SET_FLAGS);
            }
        }

        // Clear interior (air from y+1 up through wallHeight+radius to ensure 3-block headroom)
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x * x + z * z < (radius - 1) * (radius - 1)) {
                    for (int y = 1; y <= wallHeight + radius; y++) {
                        world.setBlockState(new BlockPos(ox + x, baseY + y, oz + z),
                            Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Packed mud floor with orange carpet
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x * x + z * z < (radius - 1) * (radius - 1)) {
                    world.setBlockState(new BlockPos(ox + x, baseY, oz + z), packedMud, StructureHelper.SET_FLAGS);
                    world.setBlockState(new BlockPos(ox + x, baseY + 1, oz + z),
                        palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Conical HAY_BLOCK thatch roof (concentric rings)
        for (int y = 0; y <= radius + 1; y++) {
            int r = radius + 1 - y;
            if (r < 0) break;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        world.setBlockState(new BlockPos(ox + x, baseY + wallHeight + 1 + y, oz + z),
                            hayBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Re-clear interior under roof to guarantee 3-block headroom above carpet
        for (int x = -radius + 1; x <= radius - 1; x++) {
            for (int z = -radius + 1; z <= radius - 1; z++) {
                if (x * x + z * z < (radius - 1) * (radius - 1)) {
                    for (int y = 1; y <= Math.min(3, wallHeight); y++) {
                        world.setBlockState(new BlockPos(ox + x, baseY + y, oz + z),
                            Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                    }
                    // Re-place carpet on floor after clearing
                    world.setBlockState(new BlockPos(ox + x, baseY + 1, oz + z),
                        palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Door opening on south — clear both radius and radius-1 positions to
        // guarantee we hit the actual wall block (cylinder rounding can vary).
        // Clear 1-wide, 3-tall opening through the wall.
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(new BlockPos(ox, baseY + y, oz + radius), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox, baseY + y, oz + radius - 1), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Ensure 2 blocks clearance outside door
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(new BlockPos(ox, baseY + y, oz + radius + 1), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox, baseY + y, oz + radius + 2), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Remove carpet from doorway (both positions)
        world.setBlockState(new BlockPos(ox, baseY + 1, oz + radius), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY + 1, oz + radius - 1), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);

        // Hanging lantern inside (on solid roof block above)
        world.setBlockState(center.add(0, wallHeight, 0),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
    }

    /**
     * TAIGA SMALL — Fortified spruce longhouse.
     * Elongated rectangle (2:1 ratio), steep A-frame roof, heavy log walls,
     * stone hearth, palisade fence. Norse mead-hall energy.
     */
    private CastleBounds generateTaigaLonghouse(ServerWorld world, BlockPos center) {
        int halfLength = 12; // long axis (north-south)
        int halfWidth = 6;   // short axis (east-west)
        int wallHeight = 5;
        int roofPeak = 4;
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // Stone foundation (1 block high)
        StructureHelper.fillBox(world, center.add(-halfWidth, 0, -halfLength),
            center.add(halfWidth, 0, halfLength), palette.getPrimaryWallState());

        // Spruce log walls
        BlockState logWall = palette.log.getDefaultState();
        for (int z = -halfLength; z <= halfLength; z++) {
            for (int y = 1; y <= wallHeight; y++) {
                world.setBlockState(new BlockPos(ox - halfWidth, baseY + y, oz + z), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + halfWidth, baseY + y, oz + z), logWall, StructureHelper.SET_FLAGS);
            }
        }
        // End walls
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 1; y <= wallHeight; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - halfLength), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + halfLength), logWall, StructureHelper.SET_FLAGS);
            }
        }

        // Clear interior
        StructureHelper.clearInterior(world,
            center.add(-halfWidth + 1, 1, -halfLength + 1),
            center.add(halfWidth - 1, wallHeight, halfLength - 1));

        // Interior floor — spruce planks
        StructureHelper.fillBox(world, center.add(-halfWidth + 1, 1, -halfLength + 1),
            center.add(halfWidth - 1, 1, halfLength - 1), palette.getPlanksState());

        // Floor at doorway thresholds (so player doesn't step down at entrances)
        for (int x = -1; x <= 1; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY + 1, oz + halfLength), palette.getPlanksState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + 1, oz - halfLength), palette.getPlanksState(), StructureHelper.SET_FLAGS);
        }

        // Steep A-frame roof (spruce planks)
        BlockState roofBlock = palette.getRoofState();
        for (int y = 0; y <= roofPeak; y++) {
            int roofWidth = halfWidth - y;
            if (roofWidth < 0) break;
            for (int z = -halfLength - 1; z <= halfLength + 1; z++) {
                world.setBlockState(new BlockPos(ox - roofWidth, baseY + wallHeight + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + roofWidth, baseY + wallHeight + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
        }
        // Ridge beam
        for (int z = -halfLength - 1; z <= halfLength + 1; z++) {
            world.setBlockState(new BlockPos(ox, baseY + wallHeight + roofPeak, oz + z),
                palette.log.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Central stone hearth with chimney
        world.setBlockState(center.add(0, 1, 0), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, wallHeight + roofPeak, 0), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);

        // South entrance with spruce door
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                mutable.set(ox + x, baseY + y, oz + halfLength);
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // North entrance
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                mutable.set(ox + x, baseY + y, oz - halfLength);
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Furnish interior — longhouse style: benches along walls, beds at ends, central hearth
        // Beds at north end (barracks area) — FACING=SOUTH: HEAD at higher Z, FOOT at lower Z
        for (int x : new int[]{-3, -1, 1, 3}) {
            world.setBlockState(new BlockPos(ox + x, baseY + 2, oz - halfLength + 2), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + 2, oz - halfLength + 3), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // Benches (stairs) along east/west walls
        for (int z = -halfLength + 5; z <= halfLength - 5; z += 3) {
            world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 2, oz + z),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 2, oz + z),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // Throne/chief's seat at south end
        world.setBlockState(new BlockPos(ox, baseY + 2, oz + halfLength - 3),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);

        // Chests + storage
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 1, baseY + 2, oz - halfLength + 2),
            Direction.EAST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - 1, baseY + 2, oz + halfLength - 2),
            Direction.WEST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);

        // Barrel storage near entrance
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 2, oz + halfLength - 4), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 2, oz + halfLength - 5), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Windows (narrow slits on long walls)
        for (int z = -halfLength + 3; z <= halfLength - 3; z += 4) {
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + 3, oz + z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + 3, oz + z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Wall torches inside
        for (int z = -halfLength + 2; z <= halfLength - 2; z += 3) {
            world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 3, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 3, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // Palisade fence around the longhouse
        int palisadeRadius = Math.max(halfLength, halfWidth) + 4;
        generatePerimeterFence(world, center, palisadeRadius, halfWidth, halfLength);

        placeJigsawConnectors(world, center, palisadeRadius);

        VillageCastles.LOGGER.info("Taiga longhouse generation complete!");
        return new CastleBounds(
            center.add(-palisadeRadius - 2, 0, -palisadeRadius - 2),
            center.add(palisadeRadius + 2, wallHeight + roofPeak + 5, palisadeRadius + 2)
        );
    }

    /**
     * Generate a fortified igloo for the snowy small castle variant.
     * Replaces the tower keep entirely with a snow/packed-ice dome,
     * furnished interior, and a short entrance tunnel on the south side.
     */
    private CastleBounds generateIgloo(ServerWorld world, BlockPos center) {
        int domeRadius = 11;  // outer dome radius
        int wallThickness = 2;
        int innerRadius = domeRadius - wallThickness;
        int groundRadius = 15;
        int domeHeight = 9;   // peak height above floor
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        BlockState snowBlock = Blocks.SNOW_BLOCK.getDefaultState();
        BlockState packedIce = Blocks.PACKED_ICE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState soulCampfire = Blocks.SOUL_CAMPFIRE.getDefaultState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.getDefaultState();
        BlockState soulLantern = Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        BlockState standingLantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        BlockState spruceFence = Blocks.SPRUCE_FENCE.getDefaultState();
        BlockState spruceDoorLower = Blocks.SPRUCE_DOOR.getDefaultState()
            .with(DoorBlock.FACING, Direction.SOUTH)
            .with(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState spruceDoorUpper = Blocks.SPRUCE_DOOR.getDefaultState()
            .with(DoorBlock.FACING, Direction.SOUTH)
            .with(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState snowLayer = Blocks.SNOW.getDefaultState();

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // ========================================
        // 1. Prepare the ground (circular, radius 15)
        // ========================================
        int groundLimitSq = (groundRadius + 2) * (groundRadius + 2);
        for (int x = -groundRadius - 2; x <= groundRadius + 2; x++) {
            for (int z = -groundRadius - 2; z <= groundRadius + 2; z++) {
                int distSq = x * x + z * z;
                if (distSq > groundLimitSq) continue;

                int wx = ox + x;
                int wz = oz + z;

                // Foundation
                for (int y = -5; y <= -1; y++) {
                    mutable.set(wx, baseY + y, wz);
                    world.setBlockState(mutable, Blocks.COBBLESTONE.getDefaultState(), StructureHelper.SET_FLAGS);
                }

                // Ground level: snow block
                mutable.set(wx, baseY, wz);
                world.setBlockState(mutable, snowBlock, StructureHelper.SET_FLAGS);

                // Clear air above
                for (int y = 1; y <= 25; y++) {
                    mutable.set(wx, baseY + y, wz);
                    if (!world.getBlockState(mutable).isAir()) {
                        world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // ========================================
        // 2. Build the dome shell (hemisphere)
        // ========================================
        // For each Y layer, compute the circle radius from the hemisphere equation:
        //   circleRadius = sqrt(domeRadius^2 - y^2)
        // Outer shell uses snow_block, inner ring uses packed_ice.
        for (int y = 0; y <= domeHeight; y++) {
            double outerCircle = Math.sqrt((double) domeRadius * domeRadius - (double) y * y);
            double innerCircle = Math.sqrt(Math.max(0, (double) innerRadius * innerRadius - (double) y * y));
            double outerThreshSq = (outerCircle + 0.5) * (outerCircle + 0.5);
            double innerThreshSq = (innerCircle + 0.5) * (innerCircle + 0.5);
            double innerIceThreshSq = (innerCircle + 1.5) * (innerCircle + 1.5);

            for (int x = -domeRadius; x <= domeRadius; x++) {
                for (int z = -domeRadius; z <= domeRadius; z++) {
                    int distSq = x * x + z * z;

                    if (distSq <= outerThreshSq) {
                        mutable.set(ox + x, baseY + 1 + y, oz + z);

                        if (distSq > innerThreshSq) {
                            // Shell zone: outer ring is snow, inner ring is packed ice
                            if (distSq > innerIceThreshSq) {
                                world.setBlockState(mutable, snowBlock, StructureHelper.SET_FLAGS);
                            } else {
                                world.setBlockState(mutable, packedIce, StructureHelper.SET_FLAGS);
                            }
                        }
                        // else: interior — will be cleared in step 3
                    }
                }
            }
        }

        // ========================================
        // 3. Clear the interior (hollow the dome)
        // ========================================
        for (int y = 0; y <= domeHeight; y++) {
            double innerCircle = Math.sqrt(Math.max(0, (double) innerRadius * innerRadius - (double) y * y));
            double innerClearSq = (innerCircle + 0.5) * (innerCircle + 0.5);

            for (int x = -innerRadius; x <= innerRadius; x++) {
                for (int z = -innerRadius; z <= innerRadius; z++) {
                    int distSq = x * x + z * z;
                    if (distSq <= innerClearSq) {
                        mutable.set(ox + x, baseY + 1 + y, oz + z);
                        world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // ========================================
        // 4. Place the packed ice floor
        // ========================================
        double innerFloorSq = (innerRadius + 0.5) * (innerRadius + 0.5);
        for (int x = -innerRadius; x <= innerRadius; x++) {
            for (int z = -innerRadius; z <= innerRadius; z++) {
                int distSq = x * x + z * z;
                if (distSq <= innerFloorSq) {
                    mutable.set(ox + x, baseY + 1, oz + z);
                    world.setBlockState(mutable, packedIce, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ========================================
        // 5. Furnish the interior
        // ========================================
        int floorY = baseY + 2; // Items sit on top of the floor layer at baseY+1

        // Central soul campfire on stone brick base
        mutable.set(ox, baseY + 1, oz);
        world.setBlockState(mutable, stoneBricks, StructureHelper.SET_FLAGS);
        mutable.set(ox, floorY, oz);
        world.setBlockState(mutable, soulCampfire, StructureHelper.SET_FLAGS);

        // Beds along the north wall (3 beds, light_blue, facing south)
        // FACING=SOUTH: HEAD at higher Z (south), FOOT at lower Z (north)
        BlockState bedFoot = Blocks.LIGHT_BLUE_BED.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT)
            .with(BedBlock.FACING, Direction.SOUTH);
        BlockState bedHead = Blocks.LIGHT_BLUE_BED.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD)
            .with(BedBlock.FACING, Direction.SOUTH);

        int[] bedXOffsets = { -4, -1, 2 };
        for (int bx : bedXOffsets) {
            // FOOT against the north wall (lower Z), HEAD one block south (higher Z)
            mutable.set(ox + bx, floorY, oz - innerRadius + 2);
            world.setBlockState(mutable, bedFoot, StructureHelper.SET_FLAGS);
            mutable.set(ox + bx, floorY, oz - innerRadius + 3);
            world.setBlockState(mutable, bedHead, StructureHelper.SET_FLAGS);
        }

        // Barrel storage (west side)
        mutable.set(ox - innerRadius + 2, floorY, oz - 2);
        world.setBlockState(mutable, Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ox - innerRadius + 2, floorY, oz - 1);
        world.setBlockState(mutable, Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Crafting table + furnace (east side)
        mutable.set(ox + innerRadius - 2, floorY, oz - 2);
        world.setBlockState(mutable, Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ox + innerRadius - 2, floorY, oz - 1);
        world.setBlockState(mutable, Blocks.FURNACE.getDefaultState(), StructureHelper.SET_FLAGS);

        // Chest with loot (east side, near crafting area)
        StructureHelper.placeChest(world, new BlockPos(ox + innerRadius - 2, floorY, oz),
            Direction.WEST, LootTables.VILLAGE_SNOWY_HOUSE_CHEST);

        // Soul lanterns hanging from the interior ceiling
        // Place at several positions around the interior, attached to solid dome blocks above
        int[][] lanternPositions = { {-4, -3}, {4, -3}, {-4, 3}, {4, 3}, {0, -5} };
        for (int[] lp : lanternPositions) {
            int lx = ox + lp[0];
            int lz = oz + lp[1];
            // Find the lowest solid block above this XZ inside the dome for the lantern to hang from
            for (int ly = domeHeight; ly >= 3; ly--) {
                mutable.set(lx, baseY + 1 + ly, lz);
                if (!world.getBlockState(mutable).isAir()) {
                    // Hang lantern one block below this solid block
                    mutable.set(lx, baseY + ly, lz);
                    if (world.getBlockState(mutable).isAir()) {
                        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);
                    }
                    break;
                }
            }
        }

        // ========================================
        // 6. Build entrance tunnel (south side)
        // ========================================
        int tunnelLength = 5;
        int tunnelStartZ = oz + innerRadius; // where the dome interior ends
        int tunnelEndZ = tunnelStartZ + tunnelLength;

        // Carve through the dome wall where the tunnel meets
        for (int tz = oz + innerRadius - wallThickness; tz <= tunnelEndZ; tz++) {
            for (int tx = -1; tx <= 0; tx++) { // 2-wide tunnel (centered at -0.5)
                // Floor
                mutable.set(ox + tx, baseY + 1, tz);
                world.setBlockState(mutable, packedIce, StructureHelper.SET_FLAGS);

                // Clear tunnel air (3 high)
                for (int ty = 2; ty <= 4; ty++) {
                    mutable.set(ox + tx, baseY + ty, tz);
                    world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                }

                // Tunnel ceiling
                mutable.set(ox + tx, baseY + 5, tz);
                world.setBlockState(mutable, snowBlock, StructureHelper.SET_FLAGS);
            }

            // Tunnel walls (one block to each side of the 2-wide passage)
            for (int ty = 1; ty <= 5; ty++) {
                mutable.set(ox - 2, baseY + ty, tz);
                world.setBlockState(mutable, snowBlock, StructureHelper.SET_FLAGS);
                mutable.set(ox + 1, baseY + ty, tz);
                world.setBlockState(mutable, snowBlock, StructureHelper.SET_FLAGS);
            }
        }

        // Spruce door at the outer end of the tunnel
        mutable.set(ox - 1, baseY + 2, tunnelEndZ);
        world.setBlockState(mutable, spruceDoorLower, StructureHelper.SET_FLAGS);
        mutable.set(ox - 1, baseY + 3, tunnelEndZ);
        world.setBlockState(mutable, spruceDoorUpper, StructureHelper.SET_FLAGS);
        mutable.set(ox, baseY + 2, tunnelEndZ);
        world.setBlockState(mutable, spruceDoorLower, StructureHelper.SET_FLAGS);
        mutable.set(ox, baseY + 3, tunnelEndZ);
        world.setBlockState(mutable, spruceDoorUpper, StructureHelper.SET_FLAGS);

        // ========================================
        // 7. Exterior details
        // ========================================

        // Chimney hole at the top of the dome (1-block opening with soul campfire below visible)
        mutable.set(ox, baseY + 1 + domeHeight, oz);
        world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);

        // Snow layers scattered around the base of the dome
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            int sx = ox + (int) ((domeRadius + 2) * Math.cos(rad));
            int sz = oz + (int) ((domeRadius + 2) * Math.sin(rad));
            mutable.set(sx, baseY + 1, sz);
            if (world.getBlockState(mutable).isAir()) {
                world.setBlockState(mutable, snowLayer, StructureHelper.SET_FLAGS);
            }
        }

        // Spruce fence posts outside as drying racks (west side)
        for (int i = 0; i < 3; i++) {
            BlockPos fencePos = new BlockPos(ox - domeRadius - 2, baseY + 1, oz - 3 + i * 3);
            world.setBlockState(fencePos, spruceFence, StructureHelper.SET_FLAGS);
            world.setBlockState(fencePos.up(), spruceFence, StructureHelper.SET_FLAGS);
        }

        // Lantern on a fence post by the entrance (south side)
        BlockPos entranceFencePos = new BlockPos(ox + 2, baseY + 1, tunnelEndZ + 1);
        world.setBlockState(entranceFencePos, spruceFence, StructureHelper.SET_FLAGS);
        world.setBlockState(entranceFencePos.up(), spruceFence, StructureHelper.SET_FLAGS);
        world.setBlockState(entranceFencePos.up(2), standingLantern, StructureHelper.SET_FLAGS);

        // ========================================
        // 8. Aurora viewing platform (on top of dome)
        // ========================================
        // 3x3 packed_ice platform on top of the dome, with soul lantern fence posts at corners
        int platformY = baseY + 1 + domeHeight + 1; // one block above dome peak
        BlockState standingSoulLantern = Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, false);

        for (int px = -1; px <= 1; px++) {
            for (int pz = -1; pz <= 1; pz++) {
                mutable.set(ox + px, platformY, oz + pz);
                world.setBlockState(mutable, packedIce, StructureHelper.SET_FLAGS);
            }
        }

        // Soul lantern on fence posts at 4 corners — blue glow mimics aurora light
        int[][] auroraCorners = { {-1, -1}, {1, -1}, {-1, 1}, {1, 1} };
        for (int[] corner : auroraCorners) {
            mutable.set(ox + corner[0], platformY + 1, oz + corner[1]);
            world.setBlockState(mutable, spruceFence, StructureHelper.SET_FLAGS);
            mutable.set(ox + corner[0], platformY + 2, oz + corner[1]);
            world.setBlockState(mutable, standingSoulLantern, StructureHelper.SET_FLAGS);
        }

        // Snow layers around the platform edge
        for (int px = -2; px <= 2; px++) {
            for (int pz = -2; pz <= 2; pz++) {
                if (Math.abs(px) <= 1 && Math.abs(pz) <= 1) continue; // skip platform itself
                // Only place snow layer if there is a solid dome block below
                mutable.set(ox + px, platformY - 1, oz + pz);
                if (!world.getBlockState(mutable).isAir()) {
                    mutable.set(ox + px, platformY, oz + pz);
                    world.setBlockState(mutable, snowLayer, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ========================================
        // 9. Hidden ice cellar (below igloo floor)
        // ========================================
        // 3x3x3 chamber of blue_ice, dug 3 blocks below center
        int cellarTopY = baseY - 1;  // just below the floor (baseY+1 is floor, baseY is foundation)
        int cellarBaseY = cellarTopY - 2; // 3 blocks tall

        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                for (int cy = cellarBaseY; cy <= cellarTopY; cy++) {
                    mutable.set(ox + cx, cy, oz + cz);
                    world.setBlockState(mutable, Blocks.BLUE_ICE.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Hollow interior (clear 1x1x2 air space inside the 3x3x3 shell)
        for (int cy = cellarBaseY + 1; cy <= cellarTopY; cy++) {
            mutable.set(ox, cy, oz);
            world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
        }
        // Also clear the blocks adjacent to center for a wider chamber interior
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                if (cx == 0 && cz == 0) continue;
                // Walls stay blue_ice at the outer ring, clear interior on the middle layer
                if (Math.abs(cx) < 1 || Math.abs(cz) < 1) {
                    mutable.set(ox + cx, cellarBaseY + 1, oz + cz);
                    world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                    mutable.set(ox + cx, cellarTopY, oz + cz);
                    world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Spruce trapdoor access directly above the cellar
        // The floor is at baseY+1, trapdoor sits on top facing down into the cellar
        BlockPos trapdoorPos = new BlockPos(ox + 1, baseY + 1, oz);
        world.setBlockState(trapdoorPos, Blocks.SPRUCE_TRAPDOOR.getDefaultState()
            .with(TrapdoorBlock.HALF, BlockHalf.TOP)
            .with(TrapdoorBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        // Clear air below the trapdoor for access shaft down to cellar
        for (int dy = 0; dy >= cellarBaseY - baseY + 1; dy--) {
            mutable.set(trapdoorPos.getX(), baseY + dy, trapdoorPos.getZ());
            if (dy < 0) {
                world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
            }
        }

        // Cellar contents: 2 barrels and a chest with stronghold corridor loot
        mutable.set(ox - 1, cellarBaseY + 1, oz);
        world.setBlockState(mutable, Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ox + 1, cellarBaseY + 1, oz);
        world.setBlockState(mutable, Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, new BlockPos(ox, cellarBaseY + 1, oz),
            Direction.NORTH, LootTables.VILLAGE_SNOWY_HOUSE_CHEST);

        // Village bell near the entrance — required for villager gathering and raids
        world.setBlockState(new BlockPos(ox + 3, baseY + 1, tunnelEndZ - 2),
            Blocks.SPRUCE_FENCE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 3, baseY + 2, tunnelEndZ - 2),
            Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Jigsaw connectors at perimeter for village street integration
        placeJigsawConnectors(world, center, groundRadius);

        VillageCastles.LOGGER.info("Fortified igloo generation complete!");

        // Return bounds encompassing the igloo dome + tunnel + jigsaw connectors
        return new CastleBounds(
            center.add(-groundRadius - 2, 0, -groundRadius - 2),
            center.add(groundRadius + 2, domeHeight + 5, tunnelEndZ - oz + 3)
        );
    }

    /**
     * MEDIUM — Standard Fort.
     * Keep + 4 corner towers + main gatehouse + curtain walls + courtyard.
     * No wall towers, no back gate.
     */
    /**
     * MEDIUM — Biome-specific fortification.
     * Plains: motte-and-bailey. Desert: walled compound. Savanna: multi-compound.
     * Taiga/Snowy: classic fort with biome-specific materials and tower styles.
     */
    private CastleBounds generateMedium(ServerWorld world, BlockPos center, int radius,
                                         int keepHalfWidth, int keepHalfDepth) {
        return switch (palette) {
            case DESERT -> generateDesertCompound(world, center, radius, keepHalfWidth, keepHalfDepth);
            case SAVANNA -> generateSavannaCompound(world, center);
            case SNOWY -> generateWinterCastle(world, center, radius);
            default -> generateMediumFort(world, center, radius, keepHalfWidth, keepHalfDepth);
        };
    }

    /**
     * DESERT MEDIUM — Walled compound.
     * Thick sandstone outer walls with corner bastions, shaded inner courtyard,
     * central keep with flat roof, archery range, cistern. Arid fortress.
     */
    private CastleBounds generateDesertCompound(ServerWorld world, BlockPos center, int radius,
                                                  int keepHalfWidth, int keepHalfDepth) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        int wallHeight = 7;
        int wallThickness = 2;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // Thick rectangular sandstone outer walls
        // North/South walls
        for (int x = -radius; x <= radius; x++) {
            for (int t = 0; t < wallThickness; t++) {
                for (int y = 0; y < wallHeight; y++) {
                    mutable.set(ox + x, baseY + y, oz - radius + t);
                    world.setBlockState(mutable, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                    mutable.set(ox + x, baseY + y, oz + radius - t);
                    world.setBlockState(mutable, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                }
            }
        }
        // East/West walls
        for (int z = -radius; z <= radius; z++) {
            for (int t = 0; t < wallThickness; t++) {
                for (int y = 0; y < wallHeight; y++) {
                    mutable.set(ox - radius + t, baseY + y, oz + z);
                    world.setBlockState(mutable, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                    mutable.set(ox + radius - t, baseY + y, oz + z);
                    world.setBlockState(mutable, palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Crenellations on top
        BlockPos wallMin = center.add(-radius, 0, -radius);
        BlockPos wallMax = center.add(radius, 0, radius);
        StructureHelper.addCrenellations(world, wallMin, wallMax, baseY + wallHeight, palette.getPrimaryWallState());

        // Corner bastions (slightly wider, taller protrusions)
        int bastionSize = 3;
        int bastionHeight = wallHeight + 2;
        int[][] corners = {{-radius, -radius}, {radius, -radius}, {-radius, radius}, {radius, radius}};
        for (int[] c : corners) {
            BlockPos bastionCenter = new BlockPos(ox + c[0], baseY, oz + c[1]);
            StructureHelper.buildCylinder(world, bastionCenter, bastionSize, bastionHeight,
                palette.getSecondaryWallState(), true);
            StructureHelper.addCircularCrenellations(world, bastionCenter, bastionSize,
                baseY + bastionHeight, palette.getSecondaryWallState());
        }

        // Clear interior
        StructureHelper.clearInterior(world,
            center.add(-radius + wallThickness, 0, -radius + wallThickness),
            center.add(radius - wallThickness, wallHeight + 2, radius - wallThickness));

        // Interior floor
        StructureHelper.fillBox(world,
            center.add(-radius + wallThickness, 0, -radius + wallThickness),
            center.add(radius - wallThickness, 0, radius - wallThickness),
            palette.getFloorState());

        // Central keep
        int keepHeight = keepGenerator.generate(world, center);

        // Courtyard features (cistern, archery range)
        courtyardGenerator.generate(world, center, radius - wallThickness,
            keepHalfWidth, keepHalfDepth, size);

        // South gate entrance
        gateGenerator.generate(world, center.south(radius - 2), Direction.SOUTH);

        placeJigsawConnectors(world, center, radius);

        VillageCastles.LOGGER.info("Desert compound generation complete!");
        return new CastleBounds(
            center.add(-radius - 2, 0, -radius - 2),
            center.add(radius + 2, keepHeight + 10, radius + 2)
        );
    }

    /**
     * SAVANNA MEDIUM — Chief's compound.
     * Chief's hut on raised packed-mud platform with toron-decorated mud brick walls.
     * Guard hut, open-air cookhouse on acacia log stilts, granary on stilts,
     * barracks hut. Council circle with log seating. Weaving and smithing areas.
     * Coarse dirt ground, hay thatch roofs, terracotta accents, dead bush thorns.
     */
    private CastleBounds generateSavannaCompound(ServerWorld world, BlockPos center) {
        int compoundRadius = 20;
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockState mudBrick = Blocks.MUD_BRICKS.getDefaultState();
        BlockState packedMud = Blocks.PACKED_MUD.getDefaultState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.getDefaultState();
        BlockState fence = palette.fence.getDefaultState();
        BlockState hayBlock = Blocks.HAY_BLOCK.getDefaultState();
        BlockState deadBush = Blocks.DEAD_BUSH.getDefaultState();
        BlockState logBlock = palette.log.getDefaultState();

        // Coarse dirt ground throughout compound — placed at baseY-1 to replace
        // the surface block rather than adding a raised layer on top of terrain
        for (int x = -compoundRadius; x <= compoundRadius; x++) {
            for (int z = -compoundRadius; z <= compoundRadius; z++) {
                if (x * x + z * z <= compoundRadius * compoundRadius) {
                    world.setBlockState(new BlockPos(ox + x, baseY - 1, oz + z), coarseDirt, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Acacia palisade perimeter (3 high) with dead bush thorns
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int fx = ox + (int)(compoundRadius * Math.cos(rad));
            int fz = oz + (int)(compoundRadius * Math.sin(rad));
            world.setBlockState(new BlockPos(fx, baseY, fz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(fx, baseY + 1, fz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(fx, baseY + 2, fz), fence, StructureHelper.SET_FLAGS);
        }
        // Dead bush thorns outside palisade — support block at baseY-1 to replace terrain
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int bx = ox + (int)((compoundRadius + 1) * Math.cos(rad));
            int bz = oz + (int)((compoundRadius + 1) * Math.sin(rad));
            world.setBlockState(new BlockPos(bx, baseY - 1, bz), coarseDirt, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(bx, baseY, bz), deadBush, StructureHelper.SET_FLAGS);
        }

        // === Chief's hut — north-center on raised packed-mud platform ===
        BlockPos chiefPos = center.add(0, 0, -8);
        // Raised platform (2 blocks high, packed mud)
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (x * x + z * z <= 36) {
                    world.setBlockState(chiefPos.add(x, 0, z), packedMud, StructureHelper.SET_FLAGS);
                    world.setBlockState(chiefPos.add(x, 1, z), packedMud, StructureHelper.SET_FLAGS);
                }
            }
        }
        // MUD_BRICK_STAIRS tapering at platform edge (inverted)
        BlockState invertedMudStairs = Blocks.MUD_BRICK_STAIRS.getDefaultState()
            .with(StairsBlock.HALF, BlockHalf.TOP);
        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            int ex = (int)(6 * Math.cos(rad));
            int ez = (int)(6 * Math.sin(rad));
            if (ex * ex + ez * ez >= 30) {
                Direction facing;
                if (Math.abs(ex) > Math.abs(ez)) {
                    facing = ex > 0 ? Direction.EAST : Direction.WEST;
                } else {
                    facing = ez > 0 ? Direction.SOUTH : Direction.NORTH;
                }
                world.setBlockState(chiefPos.add(ex, 2, ez),
                    invertedMudStairs.with(StairsBlock.FACING, facing), StructureHelper.SET_FLAGS);
            }
        }

        // Build chief's hut on platform (radius 5, wall height 5)
        buildRoundHut(world, chiefPos.up(2), 5, 5);

        // Toron on chief's hut walls (extra prominent — already done by buildRoundHut, but add
        // additional terracotta accents at the base of the platform)
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int ax = (int)(7 * Math.cos(rad));
            int az = (int)(7 * Math.sin(rad));
            world.setBlockState(chiefPos.add(ax, 0, az), Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Throne inside chief's hut: platform is at y+2, floor carpet at y+3, throne ON platform at y+3
        // Throne sits on a solid block (the packed mud floor) at platform+1 = chiefPos.y+3
        world.setBlockState(chiefPos.add(0, 3, -3),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Brown terracotta behind throne
        world.setBlockState(chiefPos.add(0, 4, -4), Blocks.BROWN_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);

        // Chief's bed: FACING=EAST means HEAD at higher X (-2), FOOT at lower X (-3)
        world.setBlockState(chiefPos.add(-3, 3, 0), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(chiefPos.add(-2, 3, 0), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, chiefPos.add(3, 3, 0), Direction.WEST, LootTables.VILLAGE_SAVANNA_HOUSE_CHEST);

        // Decorated pot inside chief's hut
        world.setBlockState(chiefPos.add(2, 3, -2), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // === Guard hut — east (radius 4, wall height 4) — radius 4 gives usable interior ===
        buildRoundHut(world, center.add(10, 0, -3), 4, 4);
        // Bed FACING=SOUTH: HEAD at higher Z (-3), FOOT at lower Z (-4)
        world.setBlockState(center.add(10, 1, -4), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(10, 1, -3), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // === Cookhouse — west (open-air on acacia log stilts, hay thatch roof) ===
        BlockPos cookPos = center.add(-10, 0, -2);
        // Acacia log stilts (4 corners, 3 blocks high)
        for (int[] corner : new int[][]{{-2,-2},{2,-2},{-2,2},{2,2}}) {
            for (int y = 0; y <= 2; y++) {
                world.setBlockState(cookPos.add(corner[0], y, corner[1]), logBlock, StructureHelper.SET_FLAGS);
            }
        }
        // Hay thatch roof at stilt-top+1
        StructureHelper.fillFloor(world, cookPos.add(-3, 0, -3), cookPos.add(3, 0, 3), baseY + 3, hayBlock);
        // Campfire and crafting on ground (3-block headroom under roof)
        world.setBlockState(cookPos, Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(cookPos.add(1, 0, 0), Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(cookPos.add(-1, 0, 0), Blocks.SMOKER.getDefaultState(), StructureHelper.SET_FLAGS);

        // === Granary — south-east (raised hut on acacia log stilts) ===
        BlockPos granaryPos = center.add(8, 0, 8);
        // 4 acacia log stilts (2 blocks high)
        for (int[] corner : new int[][]{{-2,-2},{2,-2},{-2,2},{2,2}}) {
            for (int y = 0; y <= 1; y++) {
                world.setBlockState(granaryPos.add(corner[0], y, corner[1]), logBlock, StructureHelper.SET_FLAGS);
            }
        }
        // Packed mud platform floor at y+2
        StructureHelper.fillBox(world, granaryPos.add(-2, 2, -2), granaryPos.add(2, 2, 2), packedMud);
        // Mud brick walls (y+3 to y+5)
        StructureHelper.fillBox(world, granaryPos.add(-2, 3, -2), granaryPos.add(2, 5, 2), mudBrick);
        // Clear interior (3-block headroom: y+3, y+4, y+5)
        StructureHelper.clearInterior(world, granaryPos.add(-1, 3, -1), granaryPos.add(1, 5, 1));
        // Storage inside
        world.setBlockState(granaryPos.add(0, 3, 0), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(granaryPos.add(1, 3, 0), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(granaryPos.add(-1, 3, 0), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        // Conical hay thatch roof on granary
        for (int y = 0; y <= 3; y++) {
            int r = 3 - y;
            if (r <= 0) break;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        world.setBlockState(new BlockPos(granaryPos.getX() + x, baseY + 6 + y,
                            granaryPos.getZ() + z), hayBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
        // Toron pegs on granary walls
        for (int[] peg : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
            world.setBlockState(granaryPos.add(peg[0], 4, peg[1]), fence, StructureHelper.SET_FLAGS);
        }
        // Door opening on south side of granary (clear 2 wide x 3 high)
        for (int y = 3; y <= 5; y++) {
            world.setBlockState(granaryPos.add(0, y, 2), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // === Barracks hut — south-west (radius 4, wall height 4) ===
        buildRoundHut(world, center.add(-8, 0, 8), 4, 4);
        // Bed 1 FACING=EAST: HEAD at higher X (-9), FOOT at lower X (-10)
        world.setBlockState(center.add(-10, 1, 8), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-9, 1, 8), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        // Bed 2 FACING=WEST: HEAD at lower X (-7), FOOT at higher X (-6)
        world.setBlockState(center.add(-6, 1, 8), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-7, 1, 8), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);

        // === Open-air council circle (center of compound) ===
        // Ring of acacia log seating around a fire
        world.setBlockState(center, Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        // Terracotta ring around fire
        for (int[] off : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            world.setBlockState(center.add(off[0], 0, off[1]),
                Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int sx = (int)(4 * Math.cos(rad));
            int sz = (int)(4 * Math.sin(rad));
            world.setBlockState(center.add(sx, 0, sz), logBlock, StructureHelper.SET_FLAGS);
        }

        // === Weaving area (south-east between huts) ===
        world.setBlockState(center.add(5, 0, 5), Blocks.LOOM.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(6, 0, 5), Blocks.LOOM.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(7, 0, 6), palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(7, 0, 7), palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);

        // === Smithing area (near guard hut) ===
        world.setBlockState(center.add(12, 0, 3), Blocks.SMITHING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(13, 0, 3), Blocks.GRINDSTONE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(11, 0, 3), Blocks.ANVIL.getDefaultState(), StructureHelper.SET_FLAGS);

        // === Decorated pots and terracotta accents ===
        world.setBlockState(center.add(-3, 0, 3), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(3, 0, -3), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-12, 0, 5), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(0, 0, 12), Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
        // Brown terracotta accent blocks
        world.setBlockState(center.add(-14, 0, 0), Blocks.BROWN_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(14, 0, 0), Blocks.BROWN_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);

        // Hay storage near granary
        world.setBlockState(center.add(10, 0, 10), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(10, 1, 10), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(11, 0, 10), hayBlock, StructureHelper.SET_FLAGS);

        // Composters (farming)
        world.setBlockState(center.add(-12, 0, -5), Blocks.COMPOSTER.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-13, 0, -5), Blocks.COMPOSTER.getDefaultState(), StructureHelper.SET_FLAGS);

        // Drying rack near cookhouse (fence posts with carpet)
        for (int z = -5; z <= -2; z++) {
            world.setBlockState(new BlockPos(ox - 14, baseY, oz + z), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - 14, baseY + 1, oz + z), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - 14, baseY + 2, oz + z), palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Torch posts throughout compound — fence base + fence + torch on top (solid support)
        for (int angle = 0; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int tx = ox + (int)(12 * Math.cos(rad));
            int tz = oz + (int)(12 * Math.sin(rad));
            world.setBlockState(new BlockPos(tx, baseY, tz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(tx, baseY + 1, tz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(tx, baseY + 2, tz), Blocks.TORCH.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Council circle torches on fence posts
        for (int[] tp : new int[][]{{0, 5}, {5, 0}, {-5, 0}}) {
            world.setBlockState(center.add(tp[0], 0, tp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(center.add(tp[0], 1, tp[1]), Blocks.TORCH.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Entrance gate south
        world.setBlockState(new BlockPos(ox, baseY, oz + compoundRadius),
            palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH),
            StructureHelper.SET_FLAGS);

        // Bell on a fence post
        world.setBlockState(center.add(3, 0, 3), fence, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(3, 1, 3), fence, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(3, 2, 3), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, compoundRadius);

        VillageCastles.LOGGER.info("Savanna chief's compound generation complete!");
        return new CastleBounds(
            center.add(-compoundRadius - 2, 0, -compoundRadius - 2),
            center.add(compoundRadius + 2, 15, compoundRadius + 2)
        );
    }

    /**
     * Generic medium fort — used by Plains (with motte), Taiga, and Snowy.
     * Keep + 4 corner towers + gatehouse + curtain walls + courtyard.
     * Biome differentiation comes from sub-generators and BiomePalette.
     */
    /**
     * SAVANNA LARGE — Sunken great longhouse (Great Enclosure).
     * Half-buried great hall excavated 5 blocks below ground. Mud brick retaining
     * walls with acacia log reinforcement and toron pegs. HAY_BLOCK thatched roof
     * barely above ground level. Entrance ramp descending from south with
     * MUD_BRICK_STAIRS. Central fire pits with smoke holes. Chief's raised platform
     * with throne at north end. Sleeping alcoves along walls. Storage at south end.
     */
    private CastleBounds generateGreatEnclosure(ServerWorld world, BlockPos center, int radius) {
        int halfLength = 25; // long axis (north-south)
        int halfWidth = 10;  // short axis (east-west)
        int wallHeight = 3;  // walls above ground level (roof barely visible)
        int baseY = center.getY();
        int depth = Math.min(5, baseY + 63); // Clamp to stay above y=-63 (world minimum is -64)
        if (depth < 2) depth = 2; // Minimum viable sunken depth
        int floorY = baseY - depth;
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockState mudBrick = Blocks.MUD_BRICKS.getDefaultState();
        BlockState packedMud = Blocks.PACKED_MUD.getDefaultState();
        BlockState logWall = palette.log.getDefaultState();
        BlockState hayBlock = Blocks.HAY_BLOCK.getDefaultState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.getDefaultState();
        BlockState fence = palette.fence.getDefaultState();
        BlockState deadBush = Blocks.DEAD_BUSH.getDefaultState();
        BlockState mudBrickStairs = Blocks.MUD_BRICK_STAIRS.getDefaultState();

        // Excavate the interior — dig down to create the sunken space
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfLength; z <= halfLength; z++) {
                for (int y = floorY; y <= baseY + wallHeight + 1; y++) {
                    mutable.set(ox + x, y, oz + z);
                    world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Packed mud floor
        StructureHelper.fillBox(world,
            center.add(-halfWidth, -depth, -halfLength),
            center.add(halfWidth, -depth, halfLength),
            packedMud);

        // Mud brick retaining walls below ground, acacia log above ground
        for (int z = -halfLength; z <= halfLength; z++) {
            for (int y = floorY; y <= baseY + wallHeight; y++) {
                BlockState wallBlock = (y < baseY) ? mudBrick : logWall;
                world.setBlockState(new BlockPos(ox - halfWidth, y, oz + z), wallBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + halfWidth, y, oz + z), wallBlock, StructureHelper.SET_FLAGS);
            }
        }
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = floorY; y <= baseY + wallHeight; y++) {
                BlockState wallBlock = (y < baseY) ? mudBrick : logWall;
                world.setBlockState(new BlockPos(ox + x, y, oz - halfLength), wallBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, y, oz + halfLength), wallBlock, StructureHelper.SET_FLAGS);
            }
        }

        // Toron pegs on interior walls (acacia fence protruding inward)
        // Every 3 blocks horizontal, every 2 rows vertical
        for (int z = -halfLength + 3; z <= halfLength - 3; z += 3) {
            for (int y = floorY + 2; y <= baseY + wallHeight - 1; y += 2) {
                // East wall — pegs protrude west (inward)
                world.setBlockState(new BlockPos(ox + halfWidth - 1, y, oz + z), fence, StructureHelper.SET_FLAGS);
                // West wall — pegs protrude east (inward)
                world.setBlockState(new BlockPos(ox - halfWidth + 1, y, oz + z), fence, StructureHelper.SET_FLAGS);
            }
        }
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += 3) {
            for (int y = floorY + 2; y <= baseY + wallHeight - 1; y += 2) {
                // North wall toron
                world.setBlockState(new BlockPos(ox + x, y, oz - halfLength + 1), fence, StructureHelper.SET_FLAGS);
            }
        }

        // HAY_BLOCK thatched roof (barely above ground level)
        int roofY = baseY + wallHeight;
        StructureHelper.fillBox(world,
            center.add(-halfWidth - 1, wallHeight, -halfLength - 1),
            center.add(halfWidth + 1, wallHeight, halfLength + 1),
            hayBlock);
        // Second layer for thickness
        StructureHelper.fillBox(world,
            center.add(-halfWidth, wallHeight + 1, -halfLength),
            center.add(halfWidth, wallHeight + 1, halfLength),
            hayBlock);

        // Ensure 3-block minimum headroom above floor everywhere inside
        StructureHelper.clearInterior(world,
            new BlockPos(ox - halfWidth + 1, floorY + 1, oz - halfLength + 1),
            new BlockPos(ox + halfWidth - 1, floorY + 3, oz + halfLength - 1));

        // Support pillars down the center (acacia logs, floor to roof)
        for (int z = -halfLength + 4; z <= halfLength - 4; z += 5) {
            for (int y = floorY + 1; y <= roofY; y++) {
                world.setBlockState(new BlockPos(ox - halfWidth + 3, y, oz + z), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + halfWidth - 3, y, oz + z), logWall, StructureHelper.SET_FLAGS);
            }
        }

        // === Fire pits down the center ===
        for (int z = -halfLength + 6; z <= halfLength - 6; z += 8) {
            world.setBlockState(new BlockPos(ox, floorY + 1, oz + z), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
            // Orange terracotta border around fire pits
            for (int[] off : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                world.setBlockState(new BlockPos(ox + off[0], floorY + 1, oz + z + off[1]),
                    Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
            }
            // Smoke hole in roof above each fire (clear both roof layers)
            world.setBlockState(new BlockPos(ox, roofY, oz + z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox, roofY + 1, oz + z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            // Also clear adjacent blocks for larger smoke hole
            for (int[] adj : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                world.setBlockState(new BlockPos(ox + adj[0], roofY, oz + z + adj[1]),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + adj[0], roofY + 1, oz + z + adj[1]),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // === Chief's raised platform at north end ===
        // Platform: 2 blocks high packed mud
        StructureHelper.fillBox(world,
            new BlockPos(ox - 4, floorY + 1, oz - halfLength + 2),
            new BlockPos(ox + 4, floorY + 2, oz - halfLength + 6),
            packedMud);
        // MUD_BRICK_STAIRS steps up to platform on south side
        for (int x = -3; x <= 3; x++) {
            world.setBlockState(new BlockPos(ox + x, floorY + 1, oz - halfLength + 7),
                mudBrickStairs.with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }
        // Throne sits ON the platform (platform top is floorY+2, throne at floorY+3)
        world.setBlockState(new BlockPos(ox, floorY + 3, oz - halfLength + 3),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Brown terracotta behind throne
        world.setBlockState(new BlockPos(ox, floorY + 3, oz - halfLength + 2),
            Blocks.BROWN_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, floorY + 4, oz - halfLength + 2),
            Blocks.BROWN_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        // Terracotta accents flanking throne
        world.setBlockState(new BlockPos(ox - 2, floorY + 3, oz - halfLength + 3),
            Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 2, floorY + 3, oz - halfLength + 3),
            Blocks.ORANGE_TERRACOTTA.getDefaultState(), StructureHelper.SET_FLAGS);

        // Orange carpet runner from throne down the hall
        for (int z = -halfLength + 7; z <= 0; z++) {
            world.setBlockState(new BlockPos(ox, floorY + 1, oz + z),
                palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // === Sleeping alcoves along east/west walls ===
        for (int z = -halfLength + 4; z <= halfLength - 8; z += 4) {
            // East side beds FACING=WEST: HEAD at lower X (halfWidth-3), FOOT at higher X (halfWidth-2)
            world.setBlockState(new BlockPos(ox + halfWidth - 2, floorY + 1, oz + z), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 3, floorY + 1, oz + z), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            // West side beds FACING=EAST: HEAD at higher X (-halfWidth+3), FOOT at lower X (-halfWidth+2)
            world.setBlockState(new BlockPos(ox - halfWidth + 2, floorY + 1, oz + z), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - halfWidth + 3, floorY + 1, oz + z), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            // Decorated pot between alcoves
            if (z % 8 == 0) {
                world.setBlockState(new BlockPos(ox + halfWidth - 2, floorY + 1, oz + z + 2),
                    Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // === Storage area at south end ===
        for (int x = -3; x <= 3; x += 2) {
            world.setBlockState(new BlockPos(ox + x, floorY + 1, oz + halfLength - 3),
                Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, floorY + 2, oz + halfLength - 3),
                Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        StructureHelper.placeChest(world, new BlockPos(ox - 5, floorY + 1, oz + halfLength - 2),
            Direction.NORTH, LootTables.VILLAGE_SAVANNA_HOUSE_CHEST);
        StructureHelper.placeChest(world, new BlockPos(ox + 5, floorY + 1, oz + halfLength - 2),
            Direction.NORTH, LootTables.VILLAGE_SAVANNA_HOUSE_CHEST);
        // Hay bales in storage
        world.setBlockState(new BlockPos(ox - 4, floorY + 1, oz + halfLength - 4), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 4, floorY + 2, oz + halfLength - 4), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 4, floorY + 1, oz + halfLength - 4), hayBlock, StructureHelper.SET_FLAGS);

        // === Entrance ramp — south end, MUD_BRICK_STAIRS descending from surface ===
        // Stairs going south (descending as you walk north into the hall) face SOUTH
        for (int i = 0; i <= depth; i++) {
            int stairZ = halfLength + 2 + (depth - i); // furthest south at surface, closest at floor
            int stairY = baseY - i;
            // MUD_BRICK_STAIRS — descent going north, so facing SOUTH (you face south walking up)
            for (int x = -2; x <= 2; x++) {
                world.setBlockState(new BlockPos(ox + x, stairY, oz + stairZ),
                    mudBrickStairs.with(StairsBlock.FACING, Direction.SOUTH),
                    StructureHelper.SET_FLAGS);
            }
            // Clear 3-block headroom above stairs
            for (int x = -2; x <= 2; x++) {
                for (int dy = 1; dy <= 3; dy++) {
                    world.setBlockState(new BlockPos(ox + x, stairY + dy, oz + stairZ),
                        Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
            // Mud brick retaining walls along stairway
            for (int dy = stairY; dy <= baseY + 1; dy++) {
                world.setBlockState(new BlockPos(ox - 3, dy, oz + stairZ), mudBrick, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + 3, dy, oz + stairZ), mudBrick, StructureHelper.SET_FLAGS);
            }
        }
        // Open south wall where stairs meet the interior (clear doorway, 4 blocks high for headroom)
        for (int x = -2; x <= 2; x++) {
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                world.setBlockState(new BlockPos(ox + x, y, oz + halfLength), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Ensure 2+ blocks clearance outside entrance ramp (surface level)
        for (int x = -2; x <= 2; x++) {
            for (int dz = 1; dz <= 2; dz++) {
                world.setBlockState(new BlockPos(ox + x, baseY + 1, oz + halfLength + depth + 2 + dz),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + 2, oz + halfLength + depth + 2 + dz),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Wall torches along the interior — on valid support blocks (mud brick walls)
        for (int z = -halfLength + 2; z <= halfLength - 2; z += 3) {
            // East wall torch: facing EAST means the torch is on the block to its WEST (the wall)
            world.setBlockState(new BlockPos(ox - halfWidth + 1, floorY + 3, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 1, floorY + 3, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // Bell near entrance on fence post
        world.setBlockState(new BlockPos(ox + 4, floorY + 1, oz + halfLength - 5), fence, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 4, floorY + 2, oz + halfLength - 5), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Acacia palisade around the surface footprint with dead bush thorns
        int fenceRadius = Math.max(halfLength, halfWidth) + 5;
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int fx = ox + (int)(fenceRadius * Math.cos(rad));
            int fz = oz + (int)(fenceRadius * Math.sin(rad));
            world.setBlockState(new BlockPos(fx, baseY, fz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(fx, baseY + 1, fz), fence, StructureHelper.SET_FLAGS);
        }
        // Dead bush thorns outside palisade — support block at baseY-1 to replace terrain
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int bx = ox + (int)((fenceRadius + 1) * Math.cos(rad));
            int bz = oz + (int)((fenceRadius + 1) * Math.sin(rad));
            world.setBlockState(new BlockPos(bx, baseY - 1, bz), coarseDirt, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(bx, baseY, bz), deadBush, StructureHelper.SET_FLAGS);
        }

        // Coarse dirt around surface footprint — at baseY-1 to replace terrain, not raise it
        for (int x = -halfWidth - 3; x <= halfWidth + 3; x++) {
            for (int z = -halfLength - 3; z <= halfLength + 3; z++) {
                int ax = ox + x;
                int az = oz + z;
                if (Math.abs(x) > halfWidth || Math.abs(z) > halfLength) {
                    world.setBlockState(new BlockPos(ax, baseY - 1, az), coarseDirt, StructureHelper.SET_FLAGS);
                }
            }
        }

        placeJigsawConnectors(world, center, fenceRadius);

        VillageCastles.LOGGER.info("Savanna sunken longhouse generation complete!");
        return new CastleBounds(
            center.add(-fenceRadius - 2, -depth - 1, -fenceRadius - 2),
            center.add(fenceRadius + 2, wallHeight + 5, fenceRadius + 2)
        );
    }

    /**
     * SNOWY MEDIUM — Winter castle.
     * Stone brick and spruce timber. Built for the cold, not from ice.
     * Steep spruce roof to shed snow, massive hearth, enclosed courtyard with
     * covered wooden walkway. Corner watchtower (one, asymmetric). Iron bar windows.
     * Gray stone, steep pitch, small openings. Scottish highland / Norwegian fortress.
     */
    private CastleBounds generateWinterCastle(ServerWorld world, BlockPos center, int radius) {
        int halfWidth = 12;
        int halfDepth = 10;
        int wallHeight = 7;
        int roofPeak = 5;
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        prepareGround(world, center, Math.max(halfWidth, halfDepth) + 2);

        BlockState stoneBrick = Blocks.STONE_BRICKS.getDefaultState();
        BlockState mossyStoneBrick = Blocks.MOSSY_STONE_BRICKS.getDefaultState();
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState spruceLog = Blocks.SPRUCE_LOG.getDefaultState();
        BlockState spruceSlab = Blocks.SPRUCE_SLAB.getDefaultState();
        BlockState spruceStairs = Blocks.SPRUCE_STAIRS.getDefaultState();
        BlockState spruceFence = Blocks.SPRUCE_FENCE.getDefaultState();

        // === STONE BRICK WALLS — main rectangular keep ===
        StructureHelper.fillBox(world,
            center.add(-halfWidth, 0, -halfDepth),
            center.add(halfWidth, wallHeight, halfDepth),
            stoneBrick);
        // Mossy stone accent at base (damp cold)
        StructureHelper.fillBox(world,
            center.add(-halfWidth, 0, -halfDepth),
            center.add(halfWidth, 1, halfDepth),
            mossyStoneBrick);
        // Hollow interior
        StructureHelper.clearInterior(world,
            center.add(-halfWidth + 1, 1, -halfDepth + 1),
            center.add(halfWidth - 1, wallHeight, halfDepth - 1));

        // Spruce plank floor
        StructureHelper.fillFloor(world,
            center.add(-halfWidth + 1, 0, -halfDepth + 1),
            center.add(halfWidth - 1, 0, halfDepth - 1),
            baseY + 1, sprucePlanks);

        // === INTERNAL DIVISION — great hall (south) + courtyard (north) ===
        int dividerZ = oz - 2; // wall separating great hall from courtyard
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, dividerZ), stoneBrick, StructureHelper.SET_FLAGS);
            }
        }
        // Doorway through divider (2 wide)
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, dividerZ), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // === STEEP SPRUCE ROOF over great hall (south half) ===
        // Sloped from divider to south wall
        int roofStartZ = dividerZ + 1;
        int roofEndZ = oz + halfDepth;
        int roofSpan = roofEndZ - roofStartZ;
        for (int y = 0; y <= roofSpan / 2 + 1; y++) {
            for (int x = -halfWidth - 1; x <= halfWidth + 1; x++) {
                // North slope (from divider going up)
                if (roofStartZ + y <= roofEndZ) {
                    world.setBlockState(new BlockPos(ox + x, baseY + wallHeight + y, roofStartZ + y),
                        sprucePlanks, StructureHelper.SET_FLAGS);
                }
                // South slope (from south wall going up)
                if (roofEndZ - y >= roofStartZ) {
                    world.setBlockState(new BlockPos(ox + x, baseY + wallHeight + y, roofEndZ - y),
                        sprucePlanks, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Ridge cap
        int ridgeZ = (roofStartZ + roofEndZ) / 2;
        int ridgeHeight = roofSpan / 2 + 1;
        for (int x = -halfWidth - 1; x <= halfWidth + 1; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY + wallHeight + ridgeHeight, ridgeZ),
                spruceLog, StructureHelper.SET_FLAGS);
        }

        // Courtyard (north half) stays open-air — no roof
        // But add a covered walkway around the courtyard edges
        int walkwayY = baseY + wallHeight;
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            world.setBlockState(new BlockPos(ox + x, walkwayY, oz - halfDepth + 1), spruceSlab, StructureHelper.SET_FLAGS);
        }
        for (int z = -halfDepth + 1; z <= dividerZ - 1; z++) {
            world.setBlockState(new BlockPos(ox - halfWidth + 1, walkwayY, oz + z), spruceSlab, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 1, walkwayY, oz + z), spruceSlab, StructureHelper.SET_FLAGS);
        }

        // === CORNER WATCHTOWER — NE corner, asymmetric ===
        int towerHW = 3;
        int towerHeight = wallHeight + 6;
        BlockPos towerPos = center.add(halfWidth - towerHW, 0, -halfDepth + towerHW);
        StructureHelper.fillBox(world,
            towerPos.add(-towerHW, 0, -towerHW),
            towerPos.add(towerHW, towerHeight, towerHW),
            stoneBrick);
        StructureHelper.clearInterior(world,
            towerPos.add(-towerHW + 1, 1, -towerHW + 1),
            towerPos.add(towerHW - 1, towerHeight - 1, towerHW - 1));
        // Tower doorway on west wall (connects to courtyard)
        for (int y = 1; y <= 3; y++) {
            world.setBlockState(new BlockPos(towerPos.getX() - towerHW, baseY + y, towerPos.getZ()),
                Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Tower floor
        StructureHelper.fillFloor(world,
            towerPos.add(-towerHW + 1, 0, -towerHW + 1),
            towerPos.add(towerHW - 1, 0, towerHW - 1),
            baseY + 1, sprucePlanks);
        // Upper floor at wall height
        StructureHelper.fillFloor(world,
            towerPos.add(-towerHW + 1, 0, -towerHW + 1),
            towerPos.add(towerHW - 1, 0, towerHW - 1),
            baseY + wallHeight, sprucePlanks);
        // Steep conical roof on tower
        for (int y = 0; y <= towerHW + 2; y++) {
            int r = towerHW + 1 - y;
            if (r < 0) break;
            StructureHelper.fillFloor(world,
                towerPos.add(-r, 0, -r), towerPos.add(r, 0, r),
                baseY + towerHeight + y, sprucePlanks);
        }
        // Ladder inside tower
        for (int y = 2; y <= towerHeight - 1; y++) {
            world.setBlockState(new BlockPos(towerPos.getX() + towerHW - 1, baseY + y, towerPos.getZ()),
                Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, Direction.WEST),
                StructureHelper.SET_FLAGS);
        }
        // Tower lookout windows (iron bars)
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.EAST}) {
            int wx = towerPos.getX() + (dir == Direction.EAST ? towerHW : 0);
            int wz = towerPos.getZ() + (dir == Direction.NORTH ? -towerHW : 0);
            world.setBlockState(new BlockPos(wx, baseY + towerHeight - 2, wz),
                Blocks.IRON_BARS.getDefaultState(), StructureHelper.SET_FLAGS);
        }
        // Bed in tower upper room (guard quarters)
        world.setBlockState(new BlockPos(towerPos.getX() - 1, baseY + wallHeight + 1, towerPos.getZ() - 1),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(towerPos.getX() - 1, baseY + wallHeight + 1, towerPos.getZ()),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // === GREAT HALL FURNISHING (south of divider) ===
        int hallFloor = baseY + 2; // furniture on the spruce plank floor at baseY+1

        // Massive double hearth — 2 campfires on stone brick base, against east wall
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + 1, oz + 2), stoneBrick, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + 1, oz + 3), stoneBrick, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 2, hallFloor, oz + 2), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 2, hallFloor, oz + 3), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        // Chimney above hearth (stone brick column through roof)
        for (int y = wallHeight; y <= wallHeight + ridgeHeight + 2; y++) {
            world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + y, oz + 2), stoneBrick, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + y, oz + 3), stoneBrick, StructureHelper.SET_FLAGS);
        }

        // Long table (fence + slab)
        for (int z = 0; z <= 4; z++) {
            world.setBlockState(new BlockPos(ox, baseY + 1, oz + z), spruceFence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox, hallFloor, oz + z), spruceSlab, StructureHelper.SET_FLAGS);
        }
        // Benches along table
        for (int z = 0; z <= 4; z += 2) {
            world.setBlockState(new BlockPos(ox - 1, hallFloor, oz + z),
                spruceStairs.with(StairsBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 1, hallFloor, oz + z),
                spruceStairs.with(StairsBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // Master's seat at head of table (north end, facing south down the hall)
        world.setBlockState(new BlockPos(ox, hallFloor, dividerZ + 1),
            spruceStairs.with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Master bed — west side of great hall
        world.setBlockState(new BlockPos(ox - halfWidth + 2, hallFloor, oz + halfDepth - 2),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + 2, hallFloor, oz + halfDepth - 3),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        // Second bed
        world.setBlockState(new BlockPos(ox - halfWidth + 4, hallFloor, oz + halfDepth - 2),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + 4, hallFloor, oz + halfDepth - 3),
            palette.bed.getDefaultState().with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);

        // Chests along west wall
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 2, hallFloor, dividerZ + 2),
            Direction.EAST, LootTables.VILLAGE_SNOWY_HOUSE_CHEST);
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 2, hallFloor, oz + 2),
            Direction.EAST, LootTables.VILLAGE_SNOWY_HOUSE_CHEST);

        // Carpet runner
        for (int z = dividerZ + 2; z <= oz + halfDepth - 1; z++) {
            // Skip the table area (z = oz to oz+4)
            if (z >= oz && z <= oz + 4) continue;
            world.setBlockState(new BlockPos(ox, hallFloor, z),
                palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Iron bar windows (small, defensive)
        for (int z : new int[]{oz + 1, oz + halfDepth - 2}) {
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + 3, z), Blocks.IRON_BARS.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + 3, z), Blocks.IRON_BARS.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Wall torches in great hall
        world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 3, oz + 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 3, oz + halfDepth - 2),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 3, oz + 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 3, oz + halfDepth - 2),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 4, baseY + 3, dividerZ + 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 4, baseY + 3, dividerZ + 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Lantern in courtyard
        world.setBlockState(center.add(0, 2, -halfDepth + 3),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // === ENTRANCE — south wall ===
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + halfDepth), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Floor at threshold
        world.setBlockState(new BlockPos(ox - 1, baseY + 1, oz + halfDepth), sprucePlanks, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY + 1, oz + halfDepth), sprucePlanks, StructureHelper.SET_FLAGS);

        // Courtyard entrance — north wall
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - halfDepth), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Bell in courtyard
        world.setBlockState(center.add(-3, 1, -halfDepth + 3), spruceFence, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-3, 2, -halfDepth + 3), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, Math.max(halfWidth, halfDepth));

        VillageCastles.LOGGER.info("Winter castle generation complete!");
        return new CastleBounds(
            center.add(-halfWidth - 2, 0, -halfDepth - 2),
            center.add(halfWidth + 2, towerHeight + towerHW + 5, halfDepth + 2)
        );
    }

    private CastleBounds generateMediumFort(ServerWorld world, BlockPos center, int radius,
                                              int keepHalfWidth, int keepHalfDepth) {
        // Motte-and-bailey: for plains biome, build a dirt hill and raise the castle
        BlockPos originalCenter = center;
        BlockPos buildCenter = center;
        boolean hasMotte = (palette == BiomePalette.PLAINS);
        if (hasMotte) {
            VillageCastles.LOGGER.debug("Plains biome detected — building motte (dirt hill)...");
            buildMotte(world, center, radius);
            buildCenter = center.up(14); // Raise castle by the motte height
            // Motte's flat top IS the prepared ground — only clear air above it
            clearAirAbove(world, buildCenter, radius);
        } else {
            // 1. Prepare the ground normally
            prepareGround(world, buildCenter, radius);
        }

        // 2. Calculate key positions (all relative to buildCenter)
        BlockPos southGatePos = buildCenter.south(radius - GateGenerator.getDepth() / 2);
        int towerOffset = towerGenerator.getAdjustedRadius(TowerGenerator.TowerType.CORNER);
        BlockPos nwTower = buildCenter.add(-radius + towerOffset, 0, -radius + towerOffset);
        BlockPos neTower = buildCenter.add(radius - towerOffset, 0, -radius + towerOffset);
        BlockPos swTower = buildCenter.add(-radius + towerOffset, 0, radius - towerOffset);
        BlockPos seTower = buildCenter.add(radius - towerOffset, 0, radius - towerOffset);

        // 3. Generate the keep
        VillageCastles.LOGGER.debug("Generating keep...");
        int keepHeight = keepGenerator.generate(world, buildCenter);

        // 4. Corner towers
        VillageCastles.LOGGER.debug("Generating corner towers...");
        towerGenerator.generate(world, nwTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, neTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, swTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, seTower, TowerGenerator.TowerType.CORNER);

        // 5. Main gatehouse (south)
        VillageCastles.LOGGER.debug("Generating main gatehouse...");
        gateGenerator.generate(world, southGatePos, Direction.SOUTH);

        // 6. Curtain walls connecting towers to gatehouse
        VillageCastles.LOGGER.debug("Generating walls...");
        BlockPos backGatePos = buildCenter.north(radius - GateGenerator.getDepth() / 2);
        generateWalls(world, nwTower, neTower, swTower, seTower, backGatePos, southGatePos, radius);

        // 7. Courtyard with well + training area
        VillageCastles.LOGGER.debug("Generating courtyard...");
        courtyardGenerator.generate(world, buildCenter, radius,
            keepHalfWidth, keepHalfDepth, size);

        // 8. Jigsaw connectors at perimeter for village street integration
        placeJigsawConnectors(world, buildCenter, radius);

        VillageCastles.LOGGER.info("Standard fort generation complete!");

        // Bounds: include jigsaw connectors (at radius+1) plus 1 block margin
        return new CastleBounds(
            originalCenter.add(-radius - 2, 0, -radius - 2),
            buildCenter.add(radius + 2, keepHeight + 10, radius + 2)
        );
    }

    /**
     * LARGE — Grand Castle.
     * Everything medium has, PLUS wall towers on east/west, back gate on north,
     * and stables + barracks in courtyard.
     */
    /**
     * LARGE — Biome-specific castle complex.
     * The showpiece structures. Each biome's large castle is a landmark.
     */
    private CastleBounds generateLarge(ServerWorld world, BlockPos center, int radius,
                                        int keepHalfWidth, int keepHalfDepth) {
        return switch (palette) {
            case DESERT -> generateDesertPyramid(world, center, radius);
            case SAVANNA -> generateGreatEnclosure(world, center, radius);
            case SNOWY -> generateIcePalace(world, center, radius);
            default -> generateLargeGrandCastle(world, center, radius, keepHalfWidth, keepHalfDepth);
        };
    }

    /**
     * SNOWY LARGE — Ice Palace.
     * Towering blue-ice spires, packed-ice walls, vaulted great hall with massive hearth,
     * ice-crystal towers at corners, snow-covered battlements. The tallest structure
     * in the mod — designed to be visible from far away across the tundra.
     */
    private CastleBounds generateIcePalace(ServerWorld world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        BlockState packedIce = Blocks.PACKED_ICE.getDefaultState();
        BlockState blueIce = Blocks.BLUE_ICE.getDefaultState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.getDefaultState();
        BlockState snowBlock = Blocks.SNOW_BLOCK.getDefaultState();
        BlockState sprucePlanks = palette.getPlanksState();
        BlockState spruceFence = palette.getFenceState();

        int wallRadius = radius - 2;
        int wallHeight = 10;
        int towerHeight = 22; // Very tall towers
        int towerRadius = 4;
        int centralTowerHeight = 28; // Central spire — highest point

        prepareGround(world, center, wallRadius + 2);

        // === Outer walls (packed ice, thick) ===
        for (int x = -wallRadius; x <= wallRadius; x++) {
            for (int y = 0; y < wallHeight; y++) {
                // North/South walls
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - wallRadius), packedIce, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + wallRadius), packedIce, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - wallRadius + 1), packedIce, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + wallRadius - 1), packedIce, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = -wallRadius; z <= wallRadius; z++) {
            for (int y = 0; y < wallHeight; y++) {
                world.setBlockState(new BlockPos(ox - wallRadius, baseY + y, oz + z), packedIce, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + wallRadius, baseY + y, oz + z), packedIce, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox - wallRadius + 1, baseY + y, oz + z), packedIce, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + wallRadius - 1, baseY + y, oz + z), packedIce, StructureHelper.SET_FLAGS);
            }
        }

        // Snow-topped battlements
        StructureHelper.addCrenellations(world,
            center.add(-wallRadius, 0, -wallRadius), center.add(wallRadius, 0, wallRadius),
            baseY + wallHeight, snowBlock);

        // Clear interior
        StructureHelper.clearInterior(world,
            center.add(-wallRadius + 2, 0, -wallRadius + 2),
            center.add(wallRadius - 2, wallHeight + 5, wallRadius - 2));

        // Ice floor
        StructureHelper.fillFloor(world,
            center.add(-wallRadius + 2, 0, -wallRadius + 2),
            center.add(wallRadius - 2, 0, wallRadius - 2),
            baseY, packedIce);

        // === Four corner towers (blue ice, very tall, pointed) ===
        int[][] cornerOffsets = {
            {-wallRadius, -wallRadius}, {wallRadius, -wallRadius},
            {-wallRadius, wallRadius}, {wallRadius, wallRadius}
        };
        for (int[] corner : cornerOffsets) {
            BlockPos towerBase = new BlockPos(ox + corner[0], baseY, oz + corner[1]);
            // Blue ice cylinder
            StructureHelper.buildCylinder(world, towerBase, towerRadius, towerHeight, blueIce, true);
            // Pointed top (cone)
            for (int y = 0; y <= towerRadius + 2; y++) {
                int r = towerRadius - y;
                if (r < 0) r = 0;
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (x * x + z * z <= r * r) {
                            mutable.set(towerBase.getX() + x, baseY + towerHeight + y, towerBase.getZ() + z);
                            world.setBlockState(mutable, blueIce, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Lanterns at top
            world.setBlockState(towerBase.up(towerHeight - 1),
                Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        }

        // === Central tower/spire (the showpiece) ===
        int centralRadius = 6;
        StructureHelper.buildCylinder(world, center, centralRadius, centralTowerHeight, blueIce, true);
        // Spiraling blue ice crown
        for (int y = 0; y <= centralRadius + 4; y++) {
            int r = centralRadius - (y * centralRadius / (centralRadius + 4));
            if (r < 1) r = 1;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        mutable.set(ox + x, baseY + centralTowerHeight + y, oz + z);
                        world.setBlockState(mutable, blueIce, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
        // Beacon-like light at very top
        world.setBlockState(center.up(centralTowerHeight + centralRadius + 4),
            Blocks.SEA_LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);

        // Central tower doorways (south and north entrances)
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + centralRadius),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - centralRadius),
                    Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // === Great hall (stone brick interior structure inside the walls) ===
        int hallHW = wallRadius - 4;
        int hallHeight = 8;
        // Stone brick pillars along the hall
        for (int x = -hallHW + 2; x <= hallHW - 2; x += 4) {
            for (int y = 1; y <= hallHeight; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - hallHW + 2), stoneBricks, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + hallHW - 2), stoneBricks, StructureHelper.SET_FLAGS);
            }
        }

        // Massive central hearth (3x3 campfires)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                world.setBlockState(center.add(x, 0, z), stoneBricks, StructureHelper.SET_FLAGS);
                world.setBlockState(center.add(x, 1, z), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Beds along walls (8 beds)
        // FACING=SOUTH: HEAD at higher Z, FOOT at lower Z
        // FACING=NORTH: HEAD at lower Z, FOOT at higher Z
        for (int i = 0; i < 4; i++) {
            int bedX = -hallHW + 3 + i * 4;
            world.setBlockState(new BlockPos(ox + bedX, baseY + 1, oz - hallHW + 3), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + bedX, baseY + 1, oz - hallHW + 4), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + bedX, baseY + 1, oz + hallHW - 3), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + bedX, baseY + 1, oz + hallHW - 4), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }

        // Throne at north end
        world.setBlockState(center.add(0, 1, -hallHW + 3),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Carpet runner to throne
        for (int z = -hallHW + 4; z <= 0; z++) {
            world.setBlockState(center.add(0, 1, z), palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Soul lanterns hanging from pillars
        for (int x = -hallHW + 2; x <= hallHW - 2; x += 4) {
            world.setBlockState(new BlockPos(ox + x, baseY + hallHeight - 1, oz - hallHW + 2),
                Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + hallHeight - 1, oz + hallHW - 2),
                Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        }

        // Chests
        StructureHelper.placeChest(world, center.add(-hallHW + 3, 1, 0),
            Direction.EAST, LootTables.VILLAGE_SNOWY_HOUSE_CHEST);
        StructureHelper.placeChest(world, center.add(hallHW - 3, 1, 0),
            Direction.WEST, LootTables.VILLAGE_SNOWY_HOUSE_CHEST);

        // Gate entrance south
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y < 5; y++) {
                mutable.set(ox + x, baseY + y, oz + wallRadius);
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                mutable.set(ox + x, baseY + y, oz + wallRadius - 1);
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Bell
        world.setBlockState(center.add(hallHW - 3, 1, hallHW - 3), spruceFence, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(hallHW - 3, 2, hallHW - 3), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, wallRadius);

        VillageCastles.LOGGER.info("Ice Palace generation complete!");
        return new CastleBounds(
            center.add(-wallRadius - 2, 0, -wallRadius - 2),
            center.add(wallRadius + 2, centralTowerHeight + centralRadius + 10, wallRadius + 2)
        );
    }

    /**
     * Generic large grand castle — used by Plains and Taiga.
     */
    private CastleBounds generateLargeGrandCastle(ServerWorld world, BlockPos center, int radius,
                                                    int keepHalfWidth, int keepHalfDepth) {

        // 1. Prepare the ground
        prepareGround(world, center, radius);

        // Plains large: moat and drawbridge
        if (palette == BiomePalette.PLAINS) {
            VillageCastles.LOGGER.debug("Generating moat and drawbridge...");
            buildMoat(world, center, radius);
        }

        // 2. Calculate key positions
        BlockPos southGatePos = center.south(radius - GateGenerator.getDepth() / 2);
        BlockPos backGatePos = center.north(radius - GateGenerator.getDepth() / 2);
        int towerOffset = towerGenerator.getAdjustedRadius(TowerGenerator.TowerType.CORNER);
        BlockPos nwTower = center.add(-radius + towerOffset, 0, -radius + towerOffset);
        BlockPos neTower = center.add(radius - towerOffset, 0, -radius + towerOffset);
        BlockPos swTower = center.add(-radius + towerOffset, 0, radius - towerOffset);
        BlockPos seTower = center.add(radius - towerOffset, 0, radius - towerOffset);

        // 3. Generate the keep
        VillageCastles.LOGGER.debug("Generating keep...");
        int keepHeight = keepGenerator.generate(world, center);

        // 4. Corner towers
        VillageCastles.LOGGER.debug("Generating corner towers...");
        towerGenerator.generate(world, nwTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, neTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, swTower, TowerGenerator.TowerType.CORNER);
        towerGenerator.generate(world, seTower, TowerGenerator.TowerType.CORNER);

        // 5. Main gatehouse (south)
        VillageCastles.LOGGER.debug("Generating main gatehouse...");
        gateGenerator.generate(world, southGatePos, Direction.SOUTH);

        // 6. Curtain walls connecting towers to gatehouse
        VillageCastles.LOGGER.debug("Generating walls...");
        generateWalls(world, nwTower, neTower, swTower, seTower, backGatePos, southGatePos, radius);

        // 7. Wall towers on east/west walls
        VillageCastles.LOGGER.debug("Generating wall towers...");
        addWallTowers(world, center, radius);

        // 8. Back gate on north wall
        VillageCastles.LOGGER.debug("Generating back gate...");
        gateGenerator.generate(world, backGatePos, Direction.NORTH);

        // 9. Courtyard with all features (well, training, stables, barracks)
        VillageCastles.LOGGER.debug("Generating courtyard...");
        courtyardGenerator.generate(world, center, radius,
            keepHalfWidth, keepHalfDepth, size);

        // Jigsaw connectors at perimeter for village street integration
        placeJigsawConnectors(world, center, radius);

        VillageCastles.LOGGER.info("Grand castle generation complete!");

        int boundsExtra = (palette == BiomePalette.PLAINS) ? 6 : 2; // Moat for plains, jigsaw margin for others
        return new CastleBounds(
            center.add(-radius - boundsExtra, 0, -radius - boundsExtra),
            center.add(radius + boundsExtra, keepHeight + 10, radius + boundsExtra)
        );
    }

    // ========================================================================
    // Desert Stepped Pyramid (Large only)
    // ========================================================================

    /**
     * Generate a massive Mesoamerican/Egyptian-inspired stepped pyramid.
     * 4 tiers with battered (inward-sloping) walls, decorative bands, recessed niches,
     * a grand 8-wide south-face staircase, open-air summit temple with columns,
     * interior ring corridors, treasure room, and sandstone pillar guardians.
     */
    private CastleBounds generateDesertPyramid(ServerWorld world, BlockPos center, int radius) {
        VillageCastles.LOGGER.info("Generating desert stepped pyramid at {}", center.toShortString());

        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        prepareGround(world, center, radius);

        // Block palette
        BlockState sandstone = palette.getPrimaryWallState();
        BlockState cutSandstone = palette.getSecondaryWallState();
        BlockState smoothSandstone = palette.getAccentWallState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState soulLantern = Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        BlockState floorLantern = Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        BlockState orangeTerracotta = Blocks.ORANGE_TERRACOTTA.getDefaultState();
        BlockState redTerracotta = Blocks.RED_TERRACOTTA.getDefaultState();
        BlockState chiseledSandstone = Blocks.CHISELED_SANDSTONE.getDefaultState();
        BlockState carpet = palette.getCarpetState();

        BlockPos.Mutable pyramidMutable = new BlockPos.Mutable();

        // Tier parameters
        int tierCount = 4;
        int tierHeight = 9;
        int tierInset = 7;
        int baseHalf = radius;

        // ================================================================
        // Step 1: Build solid tier masses with battered (sloped) exterior
        // ================================================================
        for (int tier = 0; tier < tierCount; tier++) {
            int half = baseHalf - (tier * tierInset);
            int yStart = baseY + 1 + (tier * tierHeight);

            if (half < 4) break;

            // Build each row of the tier, narrowing by 1 block every 3 rows for batter
            for (int row = 0; row < tierHeight; row++) {
                int y = yStart + row;
                int batter = row / 3; // Inward slope: 1 block inset per 3 vertical blocks
                int rowHalf = half - batter;
                if (rowHalf < 3) break;

                // Fill the solid shell for this row (hollow interior done later)
                StructureHelper.fillBox(world,
                    new BlockPos(ox - rowHalf, y, oz - rowHalf),
                    new BlockPos(ox + rowHalf, y, oz + rowHalf),
                    sandstone);

                // Exterior stair batter on each face -- place sandstone stairs on the
                // outermost ring when the wall steps inward
                if (batter > 0 && row % 3 == 0) {
                    int prevHalf = rowHalf + 1;
                    // North face stairs (facing SOUTH, outward)
                    for (int sx = -prevHalf; sx <= prevHalf; sx++) {
                        pyramidMutable.set(ox + sx, y, oz - prevHalf);
                        world.setBlockState(pyramidMutable,
                            Blocks.SANDSTONE_STAIRS.getDefaultState()
                                .with(StairsBlock.FACING, Direction.SOUTH)
                                .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                            StructureHelper.SET_FLAGS);
                    }
                    // South face stairs (facing NORTH, outward)
                    for (int sx = -prevHalf; sx <= prevHalf; sx++) {
                        pyramidMutable.set(ox + sx, y, oz + prevHalf);
                        world.setBlockState(pyramidMutable,
                            Blocks.SANDSTONE_STAIRS.getDefaultState()
                                .with(StairsBlock.FACING, Direction.NORTH)
                                .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                            StructureHelper.SET_FLAGS);
                    }
                    // East face stairs (facing WEST, outward)
                    for (int sz = -prevHalf; sz <= prevHalf; sz++) {
                        pyramidMutable.set(ox + prevHalf, y, oz + sz);
                        world.setBlockState(pyramidMutable,
                            Blocks.SANDSTONE_STAIRS.getDefaultState()
                                .with(StairsBlock.FACING, Direction.WEST)
                                .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                            StructureHelper.SET_FLAGS);
                    }
                    // West face stairs (facing EAST, outward)
                    for (int sz = -prevHalf; sz <= prevHalf; sz++) {
                        pyramidMutable.set(ox - prevHalf, y, oz + sz);
                        world.setBlockState(pyramidMutable,
                            Blocks.SANDSTONE_STAIRS.getDefaultState()
                                .with(StairsBlock.FACING, Direction.EAST)
                                .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                            StructureHelper.SET_FLAGS);
                    }
                }
            }

            // Base trim: smooth sandstone at the bottom row of each tier
            int trimHalf = half;
            StructureHelper.fillBox(world,
                new BlockPos(ox - trimHalf, yStart, oz - trimHalf),
                new BlockPos(ox + trimHalf, yStart, oz + trimHalf),
                smoothSandstone);

            VillageCastles.LOGGER.debug("Pyramid tier {} built: half={}, y={}-{}",
                tier, half, yStart, yStart + tierHeight - 1);
        }

        // ================================================================
        // Step 2: Decorative bands on each tier exterior
        // ================================================================
        for (int tier = 0; tier < tierCount; tier++) {
            int half = baseHalf - (tier * tierInset);
            int yStart = baseY + 1 + (tier * tierHeight);
            if (half < 4) break;

            // Band at mid-height of each tier (row 4)
            int bandRow = 4;
            int bandBatter = bandRow / 3;
            int bandHalf = half - bandBatter;
            int bandY = yStart + bandRow;

            // Alternate: even tiers get chiseled sandstone, odd tiers get terracotta
            BlockState bandBlock = (tier % 2 == 0) ? chiseledSandstone : orangeTerracotta;
            BlockState accentBlock = (tier % 2 == 0) ? redTerracotta : chiseledSandstone;

            // North and south faces
            for (int bx = -bandHalf; bx <= bandHalf; bx++) {
                BlockState b = (Math.abs(bx) % 3 == 0) ? accentBlock : bandBlock;
                pyramidMutable.set(ox + bx, bandY, oz - bandHalf);
                world.setBlockState(pyramidMutable, b, StructureHelper.SET_FLAGS);
                pyramidMutable.set(ox + bx, bandY, oz + bandHalf);
                world.setBlockState(pyramidMutable, b, StructureHelper.SET_FLAGS);
            }
            // East and west faces
            for (int bz = -bandHalf + 1; bz <= bandHalf - 1; bz++) {
                BlockState b = (Math.abs(bz) % 3 == 0) ? accentBlock : bandBlock;
                pyramidMutable.set(ox + bandHalf, bandY, oz + bz);
                world.setBlockState(pyramidMutable, b, StructureHelper.SET_FLAGS);
                pyramidMutable.set(ox - bandHalf, bandY, oz + bz);
                world.setBlockState(pyramidMutable, b, StructureHelper.SET_FLAGS);
            }

            // Top cap: cut sandstone at the topmost row of each tier
            int topRow = tierHeight - 1;
            int topBatter = topRow / 3;
            int topHalf = half - topBatter;
            int topY = yStart + topRow;
            for (int tx = -topHalf; tx <= topHalf; tx++) {
                pyramidMutable.set(ox + tx, topY, oz - topHalf);
                world.setBlockState(pyramidMutable, cutSandstone, StructureHelper.SET_FLAGS);
                pyramidMutable.set(ox + tx, topY, oz + topHalf);
                world.setBlockState(pyramidMutable, cutSandstone, StructureHelper.SET_FLAGS);
            }
            for (int tz = -topHalf + 1; tz <= topHalf - 1; tz++) {
                pyramidMutable.set(ox + topHalf, topY, oz + tz);
                world.setBlockState(pyramidMutable, cutSandstone, StructureHelper.SET_FLAGS);
                pyramidMutable.set(ox - topHalf, topY, oz + tz);
                world.setBlockState(pyramidMutable, cutSandstone, StructureHelper.SET_FLAGS);
            }
        }

        // ================================================================
        // Step 3: Recessed niches with soul lanterns on exterior faces
        // ================================================================
        for (int tier = 0; tier < tierCount - 1; tier++) {
            int half = baseHalf - (tier * tierInset);
            int yStart = baseY + 1 + (tier * tierHeight);
            if (half < 6) break;

            // Niches at row 2 and row 6, every 5 blocks along each face
            int[] nicheRows = {2, 6};
            for (int nicheRow : nicheRows) {
                int nicheBatter = nicheRow / 3;
                int nicheHalf = half - nicheBatter;
                int nicheY = yStart + nicheRow;

                // North face niches
                for (int nx = -nicheHalf + 3; nx <= nicheHalf - 3; nx += 5) {
                    // Carve 1-deep niche (1 wide, 2 tall)
                    pyramidMutable.set(ox + nx, nicheY, oz - nicheHalf);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox + nx, nicheY + 1, oz - nicheHalf);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    // Chiseled sandstone frame above niche
                    pyramidMutable.set(ox + nx, nicheY + 2, oz - nicheHalf);
                    world.setBlockState(pyramidMutable, chiseledSandstone, StructureHelper.SET_FLAGS);
                    // Soul lantern inside niche (every other niche)
                    if (Math.abs(nx) % 10 < 5) {
                        pyramidMutable.set(ox + nx, nicheY, oz - nicheHalf + 1);
                        world.setBlockState(pyramidMutable, floorLantern, StructureHelper.SET_FLAGS);
                    }
                }
                // South face niches
                for (int nx = -nicheHalf + 3; nx <= nicheHalf - 3; nx += 5) {
                    pyramidMutable.set(ox + nx, nicheY, oz + nicheHalf);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox + nx, nicheY + 1, oz + nicheHalf);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox + nx, nicheY + 2, oz + nicheHalf);
                    world.setBlockState(pyramidMutable, chiseledSandstone, StructureHelper.SET_FLAGS);
                    if (Math.abs(nx) % 10 < 5) {
                        pyramidMutable.set(ox + nx, nicheY, oz + nicheHalf - 1);
                        world.setBlockState(pyramidMutable, floorLantern, StructureHelper.SET_FLAGS);
                    }
                }
                // East face niches
                for (int nz = -nicheHalf + 3; nz <= nicheHalf - 3; nz += 5) {
                    pyramidMutable.set(ox + nicheHalf, nicheY, oz + nz);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox + nicheHalf, nicheY + 1, oz + nz);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox + nicheHalf, nicheY + 2, oz + nz);
                    world.setBlockState(pyramidMutable, chiseledSandstone, StructureHelper.SET_FLAGS);
                    if (Math.abs(nz) % 10 < 5) {
                        pyramidMutable.set(ox + nicheHalf - 1, nicheY, oz + nz);
                        world.setBlockState(pyramidMutable, floorLantern, StructureHelper.SET_FLAGS);
                    }
                }
                // West face niches
                for (int nz = -nicheHalf + 3; nz <= nicheHalf - 3; nz += 5) {
                    pyramidMutable.set(ox - nicheHalf, nicheY, oz + nz);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox - nicheHalf, nicheY + 1, oz + nz);
                    world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox - nicheHalf, nicheY + 2, oz + nz);
                    world.setBlockState(pyramidMutable, chiseledSandstone, StructureHelper.SET_FLAGS);
                    if (Math.abs(nz) % 10 < 5) {
                        pyramidMutable.set(ox - nicheHalf + 1, nicheY, oz + nz);
                        world.setBlockState(pyramidMutable, floorLantern, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // ================================================================
        // Step 4: Interior ring corridors (3-wide) with rooms, per tier
        // ================================================================
        for (int tier = 0; tier < tierCount - 1; tier++) {
            int half = baseHalf - (tier * tierInset);
            int yStart = baseY + 1 + (tier * tierHeight);
            int corridorFloorY = yStart + 1;
            int corridorCeilY = corridorFloorY + 3; // 3 blocks headroom

            int wallThickness = 5;
            int innerHalf = half - wallThickness;

            if (innerHalf < 3) continue;

            int corridorWidth = 3; // 3-wide corridors

            // North corridor
            StructureHelper.clearInterior(world,
                new BlockPos(ox - innerHalf, corridorFloorY, oz - innerHalf),
                new BlockPos(ox + innerHalf, corridorCeilY, oz - innerHalf + corridorWidth - 1));
            // South corridor
            StructureHelper.clearInterior(world,
                new BlockPos(ox - innerHalf, corridorFloorY, oz + innerHalf - corridorWidth + 1),
                new BlockPos(ox + innerHalf, corridorCeilY, oz + innerHalf));
            // East corridor
            StructureHelper.clearInterior(world,
                new BlockPos(ox + innerHalf - corridorWidth + 1, corridorFloorY, oz - innerHalf),
                new BlockPos(ox + innerHalf, corridorCeilY, oz + innerHalf));
            // West corridor
            StructureHelper.clearInterior(world,
                new BlockPos(ox - innerHalf, corridorFloorY, oz - innerHalf),
                new BlockPos(ox - innerHalf + corridorWidth - 1, corridorCeilY, oz + innerHalf));

            // Smooth sandstone floors in corridors
            StructureHelper.fillFloor(world,
                new BlockPos(ox - innerHalf, 0, oz - innerHalf),
                new BlockPos(ox + innerHalf, 0, oz + innerHalf),
                corridorFloorY - 1, smoothSandstone);

            // Central room on each tier
            int roomHalf = Math.min(innerHalf - 4, 6);
            if (roomHalf >= 3) {
                StructureHelper.clearInterior(world,
                    new BlockPos(ox - roomHalf, corridorFloorY, oz - roomHalf),
                    new BlockPos(ox + roomHalf, corridorCeilY, oz + roomHalf));
                StructureHelper.fillFloor(world,
                    new BlockPos(ox - roomHalf, 0, oz - roomHalf),
                    new BlockPos(ox + roomHalf, 0, oz + roomHalf),
                    corridorFloorY - 1, smoothSandstone);
            }

            // Soul lanterns in corridors every 5 blocks
            for (int lx = -innerHalf + 2; lx <= innerHalf - 2; lx += 5) {
                pyramidMutable.set(ox + lx, corridorCeilY, oz - innerHalf + 1);
                world.setBlockState(pyramidMutable, soulLantern, StructureHelper.SET_FLAGS);
                pyramidMutable.set(ox + lx, corridorCeilY, oz + innerHalf - 1);
                world.setBlockState(pyramidMutable, soulLantern, StructureHelper.SET_FLAGS);
            }
            for (int lz = -innerHalf + 2; lz <= innerHalf - 2; lz += 5) {
                pyramidMutable.set(ox + innerHalf - 1, corridorCeilY, oz + lz);
                world.setBlockState(pyramidMutable, soulLantern, StructureHelper.SET_FLAGS);
                pyramidMutable.set(ox - innerHalf + 1, corridorCeilY, oz + lz);
                world.setBlockState(pyramidMutable, soulLantern, StructureHelper.SET_FLAGS);
            }

            // Internal spiral staircase connecting to next tier (NE corner)
            if (tier < tierCount - 2) {
                int stairX = ox + innerHalf - 2;
                int stairZ = oz - innerHalf + 2;
                pyramidBuildSpiralStairs(world, stairX, corridorFloorY, stairZ,
                    tierHeight, sandstone, smoothSandstone);
            }
        }

        // ================================================================
        // Step 5: Treasure room in base tier
        // ================================================================
        {
            int half = baseHalf;
            int yFloor = baseY + 2;
            int innerHalf = half - 5;
            int treasureX = ox - innerHalf + 2;
            int treasureZ = oz - innerHalf + 2;

            // Carve treasure chamber (NW corner of tier 0)
            StructureHelper.clearInterior(world,
                new BlockPos(treasureX, yFloor, treasureZ),
                new BlockPos(treasureX + 6, yFloor + 3, treasureZ + 6));

            // Smooth sandstone floor
            for (int tx = treasureX; tx <= treasureX + 6; tx++) {
                for (int tz = treasureZ; tz <= treasureZ + 6; tz++) {
                    pyramidMutable.set(tx, yFloor - 1, tz);
                    world.setBlockState(pyramidMutable, smoothSandstone, StructureHelper.SET_FLAGS);
                }
            }

            // Floor accent pattern -- orange terracotta cross
            world.setBlockState(new BlockPos(treasureX + 3, yFloor - 1, treasureZ + 3),
                orangeTerracotta, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(treasureX + 2, yFloor - 1, treasureZ + 3),
                redTerracotta, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(treasureX + 4, yFloor - 1, treasureZ + 3),
                redTerracotta, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(treasureX + 3, yFloor - 1, treasureZ + 2),
                redTerracotta, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(treasureX + 3, yFloor - 1, treasureZ + 4),
                redTerracotta, StructureHelper.SET_FLAGS);

            // Treasure chests
            StructureHelper.placeChest(world, new BlockPos(treasureX + 1, yFloor, treasureZ + 1),
                Direction.SOUTH, LootTables.DESERT_PYRAMID_CHEST);
            StructureHelper.placeChest(world, new BlockPos(treasureX + 5, yFloor, treasureZ + 1),
                Direction.SOUTH, LootTables.DESERT_PYRAMID_CHEST);
            StructureHelper.placeChest(world, new BlockPos(treasureX + 3, yFloor, treasureZ),
                Direction.SOUTH, LootTables.DESERT_PYRAMID_CHEST);

            // Lanterns in treasure room
            world.setBlockState(new BlockPos(treasureX + 2, yFloor + 3, treasureZ + 3),
                soulLantern, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(treasureX + 4, yFloor + 3, treasureZ + 3),
                soulLantern, StructureHelper.SET_FLAGS);

            // No traps in village castles — these are inhabited structures.
            // TNT traps belong in ruins variants only (DecayEngine/RuinsGenerator).

            VillageCastles.LOGGER.debug("Treasure room placed at tier 0");
        }

        // ================================================================
        // Step 6: Grand staircase on the south face (8 blocks wide)
        // ================================================================
        {
            int stairHalfWidth = 4; // 8 blocks total (from -4 to +3)
            int wallWidth = 2; // Flanking solid walls

            for (int tier = 0; tier < tierCount; tier++) {
                int half = baseHalf - (tier * tierInset);
                int tierBaseY = baseY + 1 + (tier * tierHeight);

                if (half < 4) break;

                // Build stairs ascending the south face
                for (int step = 0; step < tierHeight; step++) {
                    int stepY = tierBaseY + step;
                    int stepZ = oz + half - step;

                    // Stair blocks (facing NORTH)
                    for (int sx = -stairHalfWidth; sx <= stairHalfWidth - 1; sx++) {
                        pyramidMutable.set(ox + sx, stepY, stepZ);
                        world.setBlockState(pyramidMutable,
                            Blocks.SANDSTONE_STAIRS.getDefaultState()
                                .with(StairsBlock.FACING, Direction.NORTH)
                                .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                            StructureHelper.SET_FLAGS);

                        // Clear headroom above steps
                        for (int clearY = 1; clearY <= 4; clearY++) {
                            pyramidMutable.set(ox + sx, stepY + clearY, stepZ);
                            world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                        }
                    }

                    // Solid flanking walls (not just railings)
                    for (int wy = 0; wy <= 2; wy++) {
                        for (int ww = 0; ww < wallWidth; ww++) {
                            pyramidMutable.set(ox - stairHalfWidth - 1 - ww, stepY + wy, stepZ);
                            world.setBlockState(pyramidMutable, sandstone, StructureHelper.SET_FLAGS);
                            pyramidMutable.set(ox + stairHalfWidth + ww, stepY + wy, stepZ);
                            world.setBlockState(pyramidMutable, sandstone, StructureHelper.SET_FLAGS);
                        }
                    }
                    // Cut sandstone cap on flanking walls
                    world.setBlockState(
                        new BlockPos(ox - stairHalfWidth - 1, stepY + 2, stepZ),
                        cutSandstone, StructureHelper.SET_FLAGS);
                    world.setBlockState(
                        new BlockPos(ox + stairHalfWidth, stepY + 2, stepZ),
                        cutSandstone, StructureHelper.SET_FLAGS);

                    // Soul lanterns every 4 steps on the wall tops
                    if (step % 4 == 0) {
                        world.setBlockState(
                            new BlockPos(ox - stairHalfWidth - 1, stepY + 3, stepZ),
                            floorLantern, StructureHelper.SET_FLAGS);
                        world.setBlockState(
                            new BlockPos(ox + stairHalfWidth, stepY + 3, stepZ),
                            floorLantern, StructureHelper.SET_FLAGS);
                    }
                }

                // Landing platform between tiers
                int landingY = tierBaseY + tierHeight;
                int landingZ = oz + half - tierHeight;
                for (int sx = -stairHalfWidth - wallWidth; sx <= stairHalfWidth + wallWidth - 1; sx++) {
                    for (int sz = 0; sz < 3; sz++) {
                        pyramidMutable.set(ox + sx, landingY, landingZ - sz);
                        world.setBlockState(pyramidMutable, smoothSandstone, StructureHelper.SET_FLAGS);
                        for (int clearY = 1; clearY <= 5; clearY++) {
                            pyramidMutable.set(ox + sx, landingY + clearY, landingZ - sz);
                            world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
        }

        // ================================================================
        // Step 7: Entrance archway at base of south staircase
        // ================================================================
        {
            int stairHalfWidth = 4;
            int entranceY = baseY + 1;
            int entranceZ = oz + baseHalf;

            // Carve entrance passage (4 wide, 4 tall, 3 deep into base tier)
            for (int ex = -2; ex <= 1; ex++) {
                for (int ey = 0; ey <= 3; ey++) {
                    for (int ez = -3; ez <= 0; ez++) {
                        pyramidMutable.set(ox + ex, entranceY + ey, entranceZ + ez);
                        world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                    }
                }
            }

            // Archway frame: chiseled sandstone arch over the entrance
            for (int ex = -3; ex <= 2; ex++) {
                world.setBlockState(new BlockPos(ox + ex, entranceY + 4, entranceZ),
                    chiseledSandstone, StructureHelper.SET_FLAGS);
            }
            // Arch sides
            for (int ey = 0; ey <= 4; ey++) {
                world.setBlockState(new BlockPos(ox - 3, entranceY + ey, entranceZ),
                    cutSandstone, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + 2, entranceY + ey, entranceZ),
                    cutSandstone, StructureHelper.SET_FLAGS);
            }

            // Sandstone pillar guardians flanking the entrance
            // Left guardian
            int guardX1 = ox - stairHalfWidth - 3;
            int guardX2 = ox + stairHalfWidth + 2;
            for (int side : new int[]{guardX1, guardX2}) {
                // 2x2 base
                for (int gx = 0; gx <= 1; gx++) {
                    for (int gz = 0; gz <= 1; gz++) {
                        pyramidMutable.set(side + gx, entranceY, entranceZ + gz);
                        world.setBlockState(pyramidMutable, sandstone, StructureHelper.SET_FLAGS);
                        pyramidMutable.set(side + gx, entranceY + 1, entranceZ + gz);
                        world.setBlockState(pyramidMutable, sandstone, StructureHelper.SET_FLAGS);
                        pyramidMutable.set(side + gx, entranceY + 2, entranceZ + gz);
                        world.setBlockState(pyramidMutable, chiseledSandstone, StructureHelper.SET_FLAGS);
                    }
                }
                // Soul lantern on top
                world.setBlockState(new BlockPos(side, entranceY + 3, entranceZ),
                    floorLantern, StructureHelper.SET_FLAGS);
            }

            VillageCastles.LOGGER.debug("Entrance archway and pillar guardians placed");
        }

        // ================================================================
        // Step 8: Summit temple (open-air, columned, with overhanging roof)
        // ================================================================
        {
            int topTier = tierCount - 1;
            int topHalf = baseHalf - (topTier * tierInset);
            int topY = baseY + 1 + (topTier * tierHeight);

            if (topHalf >= 4) {
                // Smooth sandstone floor
                StructureHelper.fillFloor(world,
                    new BlockPos(ox - topHalf, 0, oz - topHalf),
                    new BlockPos(ox + topHalf, 0, oz + topHalf),
                    topY, smoothSandstone);

                // Clear temple interior
                StructureHelper.clearInterior(world,
                    new BlockPos(ox - topHalf + 1, topY + 1, oz - topHalf + 1),
                    new BlockPos(ox + topHalf - 1, topY + 7, oz + topHalf - 1));

                // 2x2 columns, 6 blocks tall, at corners and midpoints
                int colInset = 2;
                int[][] colPositions = {
                    {-topHalf + colInset, -topHalf + colInset},
                    { topHalf - colInset - 1, -topHalf + colInset},
                    {-topHalf + colInset,  topHalf - colInset - 1},
                    { topHalf - colInset - 1,  topHalf - colInset - 1},
                };
                // Add midpoint columns if temple is large enough
                if (topHalf > 8) {
                    colPositions = new int[][]{
                        {-topHalf + colInset, -topHalf + colInset},
                        { topHalf - colInset - 1, -topHalf + colInset},
                        {-topHalf + colInset,  topHalf - colInset - 1},
                        { topHalf - colInset - 1,  topHalf - colInset - 1},
                        {0, -topHalf + colInset},
                        {0,  topHalf - colInset - 1},
                        {-topHalf + colInset, 0},
                        { topHalf - colInset - 1, 0},
                    };
                }

                for (int[] col : colPositions) {
                    for (int cy = 1; cy <= 6; cy++) {
                        // 2x2 column base
                        for (int cdx = 0; cdx <= 1; cdx++) {
                            for (int cdz = 0; cdz <= 1; cdz++) {
                                BlockState colBlock = (cy == 1 || cy == 6) ? chiseledSandstone : cutSandstone;
                                pyramidMutable.set(ox + col[0] + cdx, topY + cy, oz + col[1] + cdz);
                                world.setBlockState(pyramidMutable, colBlock, StructureHelper.SET_FLAGS);
                            }
                        }
                    }
                }

                // Overhanging roof: cut sandstone, extends 1 block beyond the tier
                int roofY = topY + 7;
                int roofOverhang = 1;
                StructureHelper.fillBox(world,
                    new BlockPos(ox - topHalf - roofOverhang, roofY, oz - topHalf - roofOverhang),
                    new BlockPos(ox + topHalf + roofOverhang, roofY, oz + topHalf + roofOverhang),
                    cutSandstone);
                // Second roof layer (inner, raised) for depth
                StructureHelper.fillBox(world,
                    new BlockPos(ox - topHalf + 2, roofY + 1, oz - topHalf + 2),
                    new BlockPos(ox + topHalf - 2, roofY + 1, oz + topHalf - 2),
                    sandstone);

                // Roof edge trim: terracotta border
                for (int rx = -topHalf - roofOverhang; rx <= topHalf + roofOverhang; rx++) {
                    pyramidMutable.set(ox + rx, roofY, oz - topHalf - roofOverhang);
                    world.setBlockState(pyramidMutable, orangeTerracotta, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox + rx, roofY, oz + topHalf + roofOverhang);
                    world.setBlockState(pyramidMutable, orangeTerracotta, StructureHelper.SET_FLAGS);
                }
                for (int rz = -topHalf - roofOverhang + 1; rz <= topHalf + roofOverhang - 1; rz++) {
                    pyramidMutable.set(ox - topHalf - roofOverhang, roofY, oz + rz);
                    world.setBlockState(pyramidMutable, orangeTerracotta, StructureHelper.SET_FLAGS);
                    pyramidMutable.set(ox + topHalf + roofOverhang, roofY, oz + rz);
                    world.setBlockState(pyramidMutable, orangeTerracotta, StructureHelper.SET_FLAGS);
                }

                // Altar/throne area in the center
                // Raised platform (2x3, 1 block high)
                for (int ax = -1; ax <= 1; ax++) {
                    for (int az = -1; az <= 0; az++) {
                        pyramidMutable.set(ox + ax, topY + 1, oz + az);
                        world.setBlockState(pyramidMutable, smoothSandstone, StructureHelper.SET_FLAGS);
                    }
                }
                // Throne (sandstone stairs facing south)
                world.setBlockState(new BlockPos(ox, topY + 2, oz),
                    Blocks.SANDSTONE_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, Direction.SOUTH)
                        .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                    StructureHelper.SET_FLAGS);
                // Throne back
                world.setBlockState(new BlockPos(ox, topY + 2, oz - 1),
                    chiseledSandstone, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox, topY + 3, oz - 1),
                    chiseledSandstone, StructureHelper.SET_FLAGS);
                // Arm rests
                world.setBlockState(new BlockPos(ox - 1, topY + 2, oz),
                    Blocks.SANDSTONE_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, Direction.EAST)
                        .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                    StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + 1, topY + 2, oz),
                    Blocks.SANDSTONE_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, Direction.WEST)
                        .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                    StructureHelper.SET_FLAGS);

                // Carpet leading to throne from south entrance
                for (int cz = 2; cz <= topHalf - 2; cz++) {
                    pyramidMutable.set(ox, topY + 1, oz + cz);
                    world.setBlockState(pyramidMutable, carpet, StructureHelper.SET_FLAGS);
                }

                // Hanging soul lanterns from roof
                world.setBlockState(new BlockPos(ox - 3, roofY - 1, oz), soulLantern, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + 3, roofY - 1, oz), soulLantern, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox, roofY - 1, oz - 3), soulLantern, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox, roofY - 1, oz + 3), soulLantern, StructureHelper.SET_FLAGS);
                // Additional lanterns at column positions
                for (int[] col : colPositions) {
                    world.setBlockState(
                        new BlockPos(ox + col[0], roofY - 1, oz + col[1] + 1),
                        soulLantern, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ================================================================
        // Step 9: False corridors (dead-end tomb deterrents) in lower tiers
        // ================================================================
        for (int tier = 0; tier < Math.min(2, tierCount - 1); tier++) {
            int half = baseHalf - (tier * tierInset);
            int yStart = baseY + 1 + (tier * tierHeight);
            int corridorFloorY = yStart + 1;
            int innerHalf = half - 5;

            if (innerHalf < 3) continue;

            int tunnelDepth = 5;
            int tunnelHeight = 3;
            int tunnelCount = 2 + (tier == 0 ? 1 : 0);
            int spacing = Math.max(4, (innerHalf * 2) / (tunnelCount + 1));

            int eastWallX = ox + innerHalf;
            int westWallX = ox - innerHalf;

            for (int t = 0; t < tunnelCount; t++) {
                int tunnelZ = oz - innerHalf + spacing * (t + 1);

                // East false tunnel
                for (int d = 1; d <= tunnelDepth; d++) {
                    for (int w = 0; w < 2; w++) {
                        for (int h = 0; h < tunnelHeight; h++) {
                            pyramidMutable.set(eastWallX + d, corridorFloorY + h, tunnelZ + w);
                            world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                        }
                        pyramidMutable.set(eastWallX + d, corridorFloorY - 1, tunnelZ + w);
                        world.setBlockState(pyramidMutable, smoothSandstone, StructureHelper.SET_FLAGS);
                    }
                }
                // West false tunnel
                for (int d = 1; d <= tunnelDepth; d++) {
                    for (int w = 0; w < 2; w++) {
                        for (int h = 0; h < tunnelHeight; h++) {
                            pyramidMutable.set(westWallX - d, corridorFloorY + h, tunnelZ + w);
                            world.setBlockState(pyramidMutable, air, StructureHelper.SET_FLAGS);
                        }
                        pyramidMutable.set(westWallX - d, corridorFloorY - 1, tunnelZ + w);
                        world.setBlockState(pyramidMutable, smoothSandstone, StructureHelper.SET_FLAGS);
                    }
                }
            }

            VillageCastles.LOGGER.debug("False corridors placed in tier {} ({} per side)", tier, tunnelCount);
        }

        // ================================================================
        // Step 10: Village bell + Jigsaw connectors and return bounds
        // ================================================================
        // Bell near the base of the grand staircase — required for villager gathering and raids
        world.setBlockState(new BlockPos(ox + 7, baseY + 2, oz + baseHalf),
            Blocks.ACACIA_FENCE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 7, baseY + 3, oz + baseHalf),
            Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, radius);

        int totalHeight = (tierCount * tierHeight) + 9; // +9 for temple columns, roof, and raised cap

        VillageCastles.LOGGER.info("Desert stepped pyramid generation complete! {} tiers, height={}",
            tierCount, totalHeight);

        return new CastleBounds(
            center.add(-baseHalf - 4, 0, -baseHalf - 4),
            center.add(baseHalf + 4, totalHeight + 5, baseHalf + 4)
        );
    }

    /**
     * Build a spiral staircase connecting one pyramid tier to the next.
     * Rotates through 4 directions, placing steps with cleared headroom.
     */
    private void pyramidBuildSpiralStairs(ServerWorld world, int startX, int startY,
                                           int startZ, int height,
                                           BlockState wallBlock, BlockState stepBlock) {
        BlockState pyramidAir = Blocks.AIR.getDefaultState();
        BlockPos.Mutable spiralMutable = new BlockPos.Mutable();

        // Carve the stairwell shaft (3x3)
        StructureHelper.clearInterior(world,
            new BlockPos(startX - 1, startY, startZ - 1),
            new BlockPos(startX + 2, startY + height + 2, startZ + 2));

        // Spiral pattern: N, E, S, W offsets for each quarter turn
        // Facing direction: the stair faces the direction the player walks up toward
        int[][] spiral = {
            {0, -1},  // North offset -> player walks south, stair faces SOUTH
            {1, 0},   // East offset  -> player walks west,  stair faces WEST
            {0, 1},   // South offset -> player walks north, stair faces NORTH
            {-1, 0},  // West offset  -> player walks east,  stair faces EAST
        };
        Direction[] spiralFacing = {
            Direction.SOUTH,
            Direction.WEST,
            Direction.NORTH,
            Direction.EAST,
        };
        BlockState sandstoneStairs = Blocks.SANDSTONE_STAIRS.getDefaultState();

        for (int step = 0; step < height; step++) {
            int stepY = startY + step;
            int dir = step % 4;
            int stepX = startX + spiral[dir][0];
            int stepZ = startZ + spiral[dir][1];

            // Place stair block with correct facing instead of full block
            spiralMutable.set(stepX, stepY, stepZ);
            world.setBlockState(spiralMutable, sandstoneStairs.with(StairsBlock.FACING, spiralFacing[dir]), StructureHelper.SET_FLAGS);
            // Support underneath
            spiralMutable.set(stepX, stepY - 1, stepZ);
            world.setBlockState(spiralMutable, wallBlock, StructureHelper.SET_FLAGS);
            // Clear headroom
            for (int clearY = 1; clearY <= 3; clearY++) {
                spiralMutable.set(stepX, stepY + clearY, stepZ);
                world.setBlockState(spiralMutable, pyramidAir, StructureHelper.SET_FLAGS);
            }
        }

        // Central pillar for the spiral
        for (int py = startY; py < startY + height; py++) {
            spiralMutable.set(startX, py, startZ);
            world.setBlockState(spiralMutable, wallBlock, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Build a water moat around the castle perimeter with a drawbridge at the south gate.
     * The moat is a 4-wide, 3-deep trench of water just outside the castle walls.
     * A drawbridge (oak planks) spans the moat at the south gatehouse.
     */
    private void buildMoat(ServerWorld world, BlockPos center, int radius) {
        int moatOffset = radius + 1;   // Just outside the wall line
        int moatWidth = 4;
        int moatDepth = 3;
        int ox = center.getX();
        int oz = center.getZ();
        int baseY = center.getY();
        int gateHalfWidth = GateGenerator.getFullWidth() / 2 + 1;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockState water = Blocks.WATER.getDefaultState();
        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState stoneBrick = Blocks.STONE_BRICKS.getDefaultState();

        // Dig the moat as a rectangular ring
        for (int x = -moatOffset - moatWidth; x <= moatOffset + moatWidth; x++) {
            for (int z = -moatOffset - moatWidth; z <= moatOffset + moatWidth; z++) {
                // Only place in the moat band (between moatOffset and moatOffset+moatWidth)
                boolean inXBand = Math.abs(x) >= moatOffset && Math.abs(x) <= moatOffset + moatWidth;
                boolean inZBand = Math.abs(z) >= moatOffset && Math.abs(z) <= moatOffset + moatWidth;
                boolean inXRange = Math.abs(x) <= moatOffset + moatWidth;
                boolean inZRange = Math.abs(z) <= moatOffset + moatWidth;

                boolean inMoat = (inXBand && inZRange) || (inZBand && inXRange);
                if (!inMoat) continue;

                // Skip the drawbridge zone (south gate approach)
                if (z > 0 && Math.abs(x) <= gateHalfWidth) continue;
                // Skip the back gate approach (north)
                if (z < 0 && Math.abs(x) <= gateHalfWidth) continue;

                // Stone lining on the edges (where moat meets ground)
                boolean isEdge = Math.abs(x) == moatOffset || Math.abs(x) == moatOffset + moatWidth
                    || Math.abs(z) == moatOffset || Math.abs(z) == moatOffset + moatWidth;

                for (int y = -moatDepth; y <= 0; y++) {
                    mutable.set(ox + x, baseY + y, oz + z);
                    if (y == -moatDepth) {
                        // Bottom: stone
                        world.setBlockState(mutable, stone, StructureHelper.SET_FLAGS);
                    } else if (y == 0 && isEdge) {
                        // Top edge: stone brick lip
                        world.setBlockState(mutable, stoneBrick, StructureHelper.SET_FLAGS);
                    } else if (y < 0) {
                        // Water fill
                        world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
                    } else {
                        // Surface level non-edge: water
                        world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Drawbridge at south gate: oak planks spanning the moat
        BlockState planks = palette.getPlanksState();
        int bridgeZ = moatOffset;
        for (int bz = bridgeZ; bz <= bridgeZ + moatWidth; bz++) {
            for (int bx = -gateHalfWidth; bx <= gateHalfWidth; bx++) {
                mutable.set(ox + bx, baseY, oz + bz);
                world.setBlockState(mutable, planks, StructureHelper.SET_FLAGS);
            }
        }

        // Drawbridge at north gate
        for (int bz = -bridgeZ - moatWidth; bz <= -bridgeZ; bz++) {
            for (int bx = -gateHalfWidth; bx <= gateHalfWidth; bx++) {
                mutable.set(ox + bx, baseY, oz + bz);
                world.setBlockState(mutable, planks, StructureHelper.SET_FLAGS);
            }
        }

        // Fence railings on the drawbridge edges
        BlockState fence = palette.getFenceState();
        for (int bz = bridgeZ; bz <= bridgeZ + moatWidth; bz++) {
            mutable.set(ox - gateHalfWidth - 1, baseY + 1, oz + bz);
            world.setBlockState(mutable, fence, StructureHelper.SET_FLAGS);
            mutable.set(ox + gateHalfWidth + 1, baseY + 1, oz + bz);
            world.setBlockState(mutable, fence, StructureHelper.SET_FLAGS);
        }
        for (int bz = -bridgeZ - moatWidth; bz <= -bridgeZ; bz++) {
            mutable.set(ox - gateHalfWidth - 1, baseY + 1, oz + bz);
            world.setBlockState(mutable, fence, StructureHelper.SET_FLAGS);
            mutable.set(ox + gateHalfWidth + 1, baseY + 1, oz + bz);
            world.setBlockState(mutable, fence, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Build a natural-looking rounded earthwork motte for the plains medium castle.
     * Uses a cosine-based profile for smooth slopes, grass/dirt/stone layering,
     * a zigzag gravel path up the south face, retaining walls at the top edge,
     * and scattered vegetation on the slopes.
     */
    private void buildMotte(ServerWorld world, BlockPos center, int radius) {
        int motteHeight = 14;
        int topRadius = radius + 4;   // Wider than castle footprint so towers are grounded
        int baseRadius = topRadius + motteHeight + 4; // Gradual slope needs width
        int stairWidth = 3;           // Width of the entrance staircase

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int ox = center.getX();
        int oz = center.getZ();
        int baseY = center.getY();

        // --- Pass 1: Build the hill with smooth cosine profile ---
        int topRadiusSq = topRadius * topRadius;
        int baseRadiusSq = baseRadius * baseRadius;
        for (int x = -baseRadius - 2; x <= baseRadius + 2; x++) {
            for (int z = -baseRadius - 2; z <= baseRadius + 2; z++) {
                int distSq = x * x + z * z;

                int columnHeight;
                if (distSq <= topRadiusSq) {
                    // Flat plateau — full height
                    columnHeight = motteHeight;
                } else if (distSq <= baseRadiusSq) {
                    // Smooth cosine falloff from plateau to ground
                    double dist = Math.sqrt(distSq);
                    double t = (dist - topRadius) / (baseRadius - topRadius);
                    columnHeight = (int) Math.round(motteHeight * (1.0 + Math.cos(t * Math.PI)) / 2.0);
                } else {
                    columnHeight = 0;
                }

                if (columnHeight <= 0) continue;

                // Place blocks for this column
                for (int y = 0; y <= columnHeight; y++) {
                    mutable.set(ox + x, baseY + y, oz + z);
                    int depthFromSurface = columnHeight - y;

                    if (depthFromSurface == 0) {
                        world.setBlockState(mutable, Blocks.GRASS_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
                    } else if (depthFromSurface <= 3) {
                        world.setBlockState(mutable, Blocks.DIRT.getDefaultState(), StructureHelper.SET_FLAGS);
                    } else {
                        BlockState fill = random.nextInt(5) == 0
                            ? Blocks.COBBLESTONE.getDefaultState()
                            : Blocks.COARSE_DIRT.getDefaultState();
                        world.setBlockState(mutable, fill, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // --- Pass 2: Stone staircase up the south face ---
        // Straight staircase from ground level to the plateau top, with stone walls on each side.
        // Stairs are placed stepping up one block per Z as we walk north (toward center).
        BlockState stairState = Blocks.STONE_BRICK_STAIRS.getDefaultState()
            .with(StairsBlock.FACING, Direction.NORTH); // Ascending northward toward the castle
        BlockState wallState = palette.getPrimaryWallState();

        // Start Z at the base of the south slope, end at plateau edge
        int stairStartZ = oz + baseRadius - 1;
        int stairEndZ = oz + topRadius;

        for (int y = 0; y <= motteHeight; y++) {
            // Each step is one block north and one block up
            int stepZ = stairStartZ - (int)((double) y / motteHeight * (stairStartZ - stairEndZ));

            for (int sx = -stairWidth / 2; sx <= stairWidth / 2; sx++) {
                int wx = ox + sx;

                // Stair tread
                mutable.set(wx, baseY + y, stepZ);
                world.setBlockState(mutable, stairState, StructureHelper.SET_FLAGS);

                // Fill solid below the stair tread (so stairs aren't floating)
                for (int fillY = baseY; fillY < baseY + y; fillY++) {
                    mutable.set(wx, fillY, stepZ);
                    BlockState existing = world.getBlockState(mutable);
                    if (existing.isAir() || existing.equals(Blocks.GRASS_BLOCK.getDefaultState())) {
                        world.setBlockState(mutable, Blocks.COBBLESTONE.getDefaultState(), StructureHelper.SET_FLAGS);
                    }
                }

                // Clear 3 blocks of headroom above each step
                for (int clearY = 1; clearY <= 3; clearY++) {
                    mutable.set(wx, baseY + y + clearY, stepZ);
                    world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }

            // Stone brick walls flanking the staircase
            int wallLeftX = ox - stairWidth / 2 - 1;
            int wallRightX = ox + stairWidth / 2 + 1;

            for (int wallY = 0; wallY <= 1; wallY++) {
                mutable.set(wallLeftX, baseY + y + wallY, stepZ);
                world.setBlockState(mutable, wallState, StructureHelper.SET_FLAGS);
                mutable.set(wallRightX, baseY + y + wallY, stepZ);
                world.setBlockState(mutable, wallState, StructureHelper.SET_FLAGS);
            }
        }

        // --- Pass 3: Retaining wall around the plateau top edge ---
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int wx = (int) Math.round((topRadius + 1) * Math.cos(rad));
            int wz = (int) Math.round((topRadius + 1) * Math.sin(rad));

            // Skip the south gate approach
            if (wz > 0 && Math.abs(wx) <= stairWidth) continue;

            mutable.set(ox + wx, baseY + motteHeight + 1, oz + wz);
            world.setBlockState(mutable, wallState, StructureHelper.SET_FLAGS);
        }

        // --- Pass 4: Sparse vegetation on slopes ---
        for (int x = -baseRadius; x <= baseRadius; x++) {
            for (int z = -baseRadius; z <= baseRadius; z++) {
                int distSq = x * x + z * z;
                if (distSq <= topRadiusSq || distSq >= baseRadiusSq) continue;

                double dist = Math.sqrt(distSq);
                double t = (dist - topRadius) / (baseRadius - topRadius);
                int colHeight = (int) Math.round(motteHeight * (1.0 + Math.cos(t * Math.PI)) / 2.0);
                if (colHeight <= 2 || colHeight >= motteHeight) continue;

                // ~8% chance — sparse, not noisy
                if (random.nextInt(100) < 8) {
                    mutable.set(ox + x, baseY + colHeight + 1, oz + z);
                    if (world.getBlockState(mutable).isAir()) {
                        world.setBlockState(mutable, Blocks.SHORT_GRASS.getDefaultState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        VillageCastles.LOGGER.debug("Motte built: height={}, baseRadius={}, topRadius={}", motteHeight, baseRadius, topRadius);
    }

    /**
     * Generate a perimeter fence around a small tower keep.
     * Places fence posts in a rectangle around the keep with a fence gate entrance
     * on the south side and torch-topped fence posts at the corners.
     */
    private void generatePerimeterFence(ServerWorld world, BlockPos center, int radius,
                                         int keepHalfWidth, int keepHalfDepth) {
        int fenceHalfX = keepHalfWidth + 5;
        int fenceHalfZ = keepHalfDepth + 5;
        int baseY = center.getY();

        BlockState fenceState = palette.getFenceState();
        BlockState lanternState = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        BlockState gateState = palette.getFenceGateState().with(FenceGateBlock.FACING, Direction.SOUTH);

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // North and south fence lines
        for (int x = -fenceHalfX; x <= fenceHalfX; x++) {
            // North side
            mutable.set(center.getX() + x, baseY + 1, center.getZ() - fenceHalfZ);
            world.setBlockState(mutable, fenceState, StructureHelper.SET_FLAGS);

            // South side — leave a 2-wide gap in the center for the fence gate
            int absX = Math.abs(x);
            if (absX > 1) {
                mutable.set(center.getX() + x, baseY + 1, center.getZ() + fenceHalfZ);
                world.setBlockState(mutable, fenceState, StructureHelper.SET_FLAGS);
            }
        }

        // South fence gate (2 blocks wide, centered)
        mutable.set(center.getX(), baseY + 1, center.getZ() + fenceHalfZ);
        world.setBlockState(mutable, gateState, StructureHelper.SET_FLAGS);
        mutable.set(center.getX() - 1, baseY + 1, center.getZ() + fenceHalfZ);
        world.setBlockState(mutable, gateState, StructureHelper.SET_FLAGS);

        // East and west fence lines
        for (int z = -fenceHalfZ + 1; z <= fenceHalfZ - 1; z++) {
            // West side
            mutable.set(center.getX() - fenceHalfX, baseY + 1, center.getZ() + z);
            world.setBlockState(mutable, fenceState, StructureHelper.SET_FLAGS);

            // East side
            mutable.set(center.getX() + fenceHalfX, baseY + 1, center.getZ() + z);
            world.setBlockState(mutable, fenceState, StructureHelper.SET_FLAGS);
        }

        // Corner torch posts: fence + lantern on top at the 4 corners
        BlockPos[] corners = {
            new BlockPos(center.getX() - fenceHalfX, baseY + 1, center.getZ() - fenceHalfZ),
            new BlockPos(center.getX() + fenceHalfX, baseY + 1, center.getZ() - fenceHalfZ),
            new BlockPos(center.getX() - fenceHalfX, baseY + 1, center.getZ() + fenceHalfZ),
            new BlockPos(center.getX() + fenceHalfX, baseY + 1, center.getZ() + fenceHalfZ)
        };
        for (BlockPos corner : corners) {
            // Corner fence already placed by the loops above; add a second fence post
            // on top and crown it with a lantern
            world.setBlockState(corner.up(1), fenceState, StructureHelper.SET_FLAGS);
            world.setBlockState(corner.up(2), lanternState, StructureHelper.SET_FLAGS);
        }
    }

    /**
     * Place jigsaw connector blocks at the perimeter of a castle structure.
     * These allow village streets to radiate outward from the castle when it's
     * used as a village town_center piece.
     *
     * Connectors are placed on 4 cardinal sides + 4 diagonal positions,
     * pointing outward to the village streets pool for this biome.
     */
    /**
     * Place a single jigsaw connector at the castle entrance (south side).
     * This connects the castle to a village street when placed as a houses-pool element.
     * The connector is at the outermost edge of the export bounds so connecting
     * pieces don't overlap the castle's bounding box.
     *
     * Uses the vanilla house connector convention:
     *   name: minecraft:bottom, target: minecraft:bottom, pool: minecraft:empty
     * Terminal — no further pieces chain from the castle.
     */
    private void placeJigsawConnectors(ServerWorld world, BlockPos center, int perimeterRadius) {
        RegistryKey<StructurePool> emptyPool = RegistryKey.of(
            RegistryKeys.TEMPLATE_POOL,
            Identifier.of("minecraft", "empty")
        );

        // Single entrance connector at the south edge of the export bounds
        int r = perimeterRadius + 2; // matches export bounds edge
        placeJigsaw(world, center.add(0, 0, r), Direction.SOUTH, emptyPool);
    }

    /**
     * Place and configure a jigsaw block for village street connections.
     */
    private void placeJigsaw(ServerWorld world, BlockPos pos, Direction facing,
                              RegistryKey<StructurePool> targetPool) {
        Orientation orientation = orientationFromFacing(facing);

        world.setBlockState(pos, Blocks.JIGSAW.getDefaultState()
            .with(net.minecraft.block.JigsawBlock.ORIENTATION, orientation),
            StructureHelper.SET_FLAGS);

        if (world.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
            jigsaw.setPool(targetPool);
            jigsaw.setName(Identifier.of("minecraft", "bottom"));
            jigsaw.setTarget(Identifier.of("minecraft", "bottom"));
            jigsaw.setFinalState("minecraft:dirt_path");
            jigsaw.setJoint(JigsawBlockEntity.Joint.ROLLABLE);
            jigsaw.markDirty();
        }
    }

    /**
     * Place and configure a jigsaw block for wall chain connections.
     */
    private void placeWallJigsaw(ServerWorld world, BlockPos pos, Direction facing,
                                  RegistryKey<StructurePool> targetPool) {
        Orientation orientation = orientationFromFacing(facing);

        world.setBlockState(pos, Blocks.JIGSAW.getDefaultState()
            .with(net.minecraft.block.JigsawBlock.ORIENTATION, orientation),
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

    private static Orientation orientationFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> Orientation.NORTH_UP;
            case SOUTH -> Orientation.SOUTH_UP;
            case EAST -> Orientation.EAST_UP;
            case WEST -> Orientation.WEST_UP;
            default -> Orientation.NORTH_UP;
        };
    }

    private void prepareGround(ServerWorld world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        net.minecraft.block.BlockState cobble = Blocks.COBBLESTONE.getDefaultState();
        net.minecraft.block.BlockState air = Blocks.AIR.getDefaultState();
        net.minecraft.block.BlockState floorState = palette.getFloorState();

        for (int x = -radius - 2; x <= radius + 2; x++) {
            for (int z = -radius - 2; z <= radius + 2; z++) {
                int wx = ox + x;
                int wz = oz + z;

                // Fill foundation from -5 to -1 below surface
                for (int y = -5; y <= -1; y++) {
                    mutable.set(wx, baseY + y, wz);
                    world.setBlockState(mutable, cobble, StructureHelper.SET_FLAGS);
                }

                // Ground level floor
                mutable.set(wx, baseY, wz);
                boolean isInner = Math.abs(x) < radius - 8 && Math.abs(z) < radius - 8;
                world.setBlockState(mutable, isInner ? floorState : cobble, StructureHelper.SET_FLAGS);

                // Clear air above ground level - skip blocks already air
                for (int y = 1; y <= 40; y++) {
                    mutable.set(wx, baseY + y, wz);
                    if (!world.getBlockState(mutable).isAir()) {
                        world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        VillageCastles.LOGGER.debug("Ground prepared: {}x{} area flattened", (radius + 2) * 2, (radius + 2) * 2);
    }

    /**
     * For motte castles: only clear air above the motte's flat top so structures can be placed.
     * Does NOT flatten or fill — the motte itself provides the ground surface.
     */
    private void clearAirAbove(ServerWorld world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockState air = Blocks.AIR.getDefaultState();

        for (int x = -radius - 2; x <= radius + 2; x++) {
            for (int z = -radius - 2; z <= radius + 2; z++) {
                for (int y = 1; y <= 40; y++) {
                    mutable.set(ox + x, baseY + y, oz + z);
                    if (!world.getBlockState(mutable).isAir()) {
                        world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
    }

    private void generateWalls(ServerWorld world, BlockPos nwTower, BlockPos neTower,
                               BlockPos swTower, BlockPos seTower,
                               BlockPos backGatePos, BlockPos southGatePos, int radius) {

        int towerRadius = towerGenerator.getAdjustedRadius(TowerGenerator.TowerType.CORNER);
        int gateHalfWidth = GateGenerator.getFullWidth() / 2;

        // North wall (NW tower to NE tower) - or to back gate if large
        if (size == CastleSize.LARGE) {
            // Wall from NW tower to north gate
            wallGenerator.generateWithLighting(world,
                nwTower.east(towerRadius),
                backGatePos.west(gateHalfWidth),
                8);

            // Wall from north gate to NE tower
            wallGenerator.generateWithLighting(world,
                backGatePos.east(gateHalfWidth),
                neTower.west(towerRadius),
                8);
        } else {
            // Full north wall
            wallGenerator.generateWithLighting(world,
                nwTower.east(towerRadius),
                neTower.west(towerRadius),
                8);
        }

        // South wall (SW tower to main gate to SE tower)
        wallGenerator.generateWithLighting(world,
            swTower.east(towerRadius),
            southGatePos.west(gateHalfWidth),
            8);

        wallGenerator.generateWithLighting(world,
            southGatePos.east(gateHalfWidth),
            seTower.west(towerRadius),
            8);

        // East wall (NE tower to SE tower)
        wallGenerator.generateWithLighting(world,
            neTower.south(towerRadius),
            seTower.north(towerRadius),
            8);

        // West wall (NW tower to SW tower)
        wallGenerator.generateWithLighting(world,
            nwTower.south(towerRadius),
            swTower.north(towerRadius),
            8);
    }

    private void addWallTowers(ServerWorld world, BlockPos center, int radius) {
        int towerOffset = towerGenerator.getAdjustedRadius(TowerGenerator.TowerType.CORNER);

        // Midpoint of east wall
        BlockPos eastMid = center.add(radius - towerOffset, 0, 0);
        towerGenerator.generate(world, eastMid, TowerGenerator.TowerType.WALL);

        // Midpoint of west wall
        BlockPos westMid = center.add(-radius + towerOffset, 0, 0);
        towerGenerator.generate(world, westMid, TowerGenerator.TowerType.WALL);

        if (size == CastleSize.LARGE) {
            // Extra towers on long walls
            BlockPos eastNorth = center.add(radius - towerOffset, 0, -radius / 3);
            BlockPos eastSouth = center.add(radius - towerOffset, 0, radius / 3);
            towerGenerator.generate(world, eastNorth, TowerGenerator.TowerType.WATCH);
            towerGenerator.generate(world, eastSouth, TowerGenerator.TowerType.WATCH);

            BlockPos westNorth = center.add(-radius + towerOffset, 0, -radius / 3);
            BlockPos westSouth = center.add(-radius + towerOffset, 0, radius / 3);
            towerGenerator.generate(world, westNorth, TowerGenerator.TowerType.WATCH);
            towerGenerator.generate(world, westSouth, TowerGenerator.TowerType.WATCH);
        }
    }

    /**
     * Represents the bounding box of a generated castle.
     */
    public static class CastleBounds {
        public final BlockPos min;
        public final BlockPos max;

        public CastleBounds(BlockPos min, BlockPos max) {
            this.min = min;
            this.max = max;
        }

        public int getWidth() {
            return max.getX() - min.getX();
        }

        public int getHeight() {
            return max.getY() - min.getY();
        }

        public int getDepth() {
            return max.getZ() - min.getZ();
        }
    }
}
