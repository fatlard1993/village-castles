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
        // Lanterns in each room (hanging from ceiling)
        int ceilingY = baseY + wallHeight;
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 2, ceilingY - 1, oz - halfDepth + wallThickness + 2),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(dividerX + 3, ceilingY - 1, oz - halfDepth + wallThickness + 2),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - halfWidth + wallThickness + 2, ceilingY - 1, oz + halfDepth - wallThickness - 2),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(dividerX + 3, ceilingY - 1, oz + halfDepth - wallThickness - 2),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);

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
     * TAIGA SMALL — Viking chieftain's mead hall.
     * Elongated longhouse with steep 1:1 roof, 2-course stone foundation,
     * spruce log walls with exterior buttresses, central longfire (3 campfires),
     * raised side platforms, iron bar arrow slits, chief's high seat on the
     * east long wall (historically accurate), and a rough oval spruce log palisade.
     */
    private CastleBounds generateTaigaLonghouse(ServerWorld world, BlockPos center) {
        int halfLength = 12; // long axis (north-south)
        int halfWidth = 6;   // short axis (east-west)
        int wallHeight = 5;
        int roofPeak = halfWidth; // steep 1:1 pitch
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        BlockState logWall = palette.log.getDefaultState();
        BlockState cobble = palette.getPrimaryWallState();
        BlockState mossyCobble = palette.getSecondaryWallState();
        BlockState planks = palette.getPlanksState();
        BlockState roofBlock = palette.getRoofState();
        BlockState slabBlock = palette.woodSlab.getDefaultState();
        BlockState ironBars = Blocks.IRON_BARS.getDefaultState();

        // === 2-COURSE STONE FOUNDATION (cobblestone + mossy cobblestone) ===
        // Course 1 at baseY: cobblestone
        StructureHelper.fillBox(world, center.add(-halfWidth, 0, -halfLength),
            center.add(halfWidth, 0, halfLength), cobble);
        // Course 2 at baseY-1: mossy cobblestone (exposed below ground line)
        StructureHelper.fillBox(world, center.add(-halfWidth, -1, -halfLength),
            center.add(halfWidth, -1, halfLength), mossyCobble);

        // === SPRUCE LOG WALLS ===
        // Long walls (east/west)
        for (int z = -halfLength; z <= halfLength; z++) {
            for (int y = 1; y <= wallHeight; y++) {
                world.setBlockState(new BlockPos(ox - halfWidth, baseY + y, oz + z), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + halfWidth, baseY + y, oz + z), logWall, StructureHelper.SET_FLAGS);
            }
        }
        // End walls (north/south)
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = 1; y <= wallHeight; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - halfLength), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + halfLength), logWall, StructureHelper.SET_FLAGS);
            }
        }

        // === EXTERIOR BUTTRESS POSTS every 4 blocks along long walls ===
        // Buttress: spruce log extending 1 block outward from wall, with a base block 1 lower
        for (int z = -halfLength + 2; z <= halfLength - 2; z += 4) {
            // West wall buttresses
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlockState(new BlockPos(ox - halfWidth - 1, baseY + y, oz + z), logWall, StructureHelper.SET_FLAGS);
            }
            world.setBlockState(new BlockPos(ox - halfWidth - 1, baseY, oz + z), cobble, StructureHelper.SET_FLAGS);
            // East wall buttresses
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlockState(new BlockPos(ox + halfWidth + 1, baseY + y, oz + z), logWall, StructureHelper.SET_FLAGS);
            }
            world.setBlockState(new BlockPos(ox + halfWidth + 1, baseY, oz + z), cobble, StructureHelper.SET_FLAGS);
        }

        // === CLEAR INTERIOR ===
        StructureHelper.clearInterior(world,
            center.add(-halfWidth + 1, 1, -halfLength + 1),
            center.add(halfWidth - 1, wallHeight, halfLength - 1));

        // === INTERIOR FLOOR — spruce planks at baseY+1 ===
        StructureHelper.fillBox(world, center.add(-halfWidth + 1, 1, -halfLength + 1),
            center.add(halfWidth - 1, 1, halfLength - 1), planks);

        // Floor at doorway thresholds
        for (int x = -1; x <= 1; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY + 1, oz + halfLength), planks, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + 1, oz - halfLength), planks, StructureHelper.SET_FLAGS);
        }

        // === STEEP 1:1 A-FRAME ROOF (DEEPSLATE_TILES) ===
        for (int y = 0; y <= roofPeak; y++) {
            int roofWidth = halfWidth - y;
            if (roofWidth < 0) break;
            for (int z = -halfLength - 1; z <= halfLength + 1; z++) {
                world.setBlockState(new BlockPos(ox - roofWidth, baseY + wallHeight + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + roofWidth, baseY + wallHeight + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
            // Clear interior under roof (above walls, below the tile line)
            if (y > 0 && roofWidth > 0) {
                for (int z = -halfLength; z <= halfLength; z++) {
                    for (int rx = -roofWidth + 1; rx <= roofWidth - 1; rx++) {
                        mutable.set(ox + rx, baseY + wallHeight + y, oz + z);
                        world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
        // Ridge beam (spruce log at peak)
        for (int z = -halfLength - 1; z <= halfLength + 1; z++) {
            world.setBlockState(new BlockPos(ox, baseY + wallHeight + roofPeak, oz + z), logWall, StructureHelper.SET_FLAGS);
        }

        // === SMOKE HOLE in roof above hearth (2 blocks wide) ===
        for (int y = 0; y <= roofPeak; y++) {
            int roofWidth = halfWidth - y;
            if (roofWidth < 0) break;
            for (int dz = -1; dz <= 0; dz++) {
                // Clear left and right roof tiles above hearth center
                if (roofWidth > 0) {
                    world.setBlockState(new BlockPos(ox - roofWidth, baseY + wallHeight + y, oz + dz), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                    world.setBlockState(new BlockPos(ox + roofWidth, baseY + wallHeight + y, oz + dz), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
        }
        // Also clear ridge beam above hearth for the smoke hole
        world.setBlockState(new BlockPos(ox, baseY + wallHeight + roofPeak, oz), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY + wallHeight + roofPeak, oz - 1), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);

        // === SOUTH ENTRANCE (3 wide, 3 tall) ===
        for (int x = -1; x <= 1; x++) {
            for (int y = 2; y <= 4; y++) {
                mutable.set(ox + x, baseY + y, oz + halfLength);
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // === NORTH ENTRANCE ===
        for (int x = -1; x <= 1; x++) {
            for (int y = 2; y <= 4; y++) {
                mutable.set(ox + x, baseY + y, oz - halfLength);
                world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // === CENTRAL LONGFIRE: 3 campfires in a row with cobblestone slab borders ===
        BlockState cobbleSlab = Blocks.COBBLESTONE_SLAB.getDefaultState();
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(ox, baseY + 2, oz + dz), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
            // Cobblestone slab borders on east and west sides
            world.setBlockState(new BlockPos(ox - 1, baseY + 2, oz + dz), cobbleSlab, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 1, baseY + 2, oz + dz), cobbleSlab, StructureHelper.SET_FLAGS);
        }
        // Slab caps at north and south ends of the hearth
        world.setBlockState(new BlockPos(ox, baseY + 2, oz - 2), cobbleSlab, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY + 2, oz + 2), cobbleSlab, StructureHelper.SET_FLAGS);

        // === RAISED SIDE PLATFORMS (spruce slab at baseY+2 along each long wall) ===
        for (int z = -halfLength + 1; z <= halfLength - 1; z++) {
            // West platform (1 block from wall)
            world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 2, oz + z), slabBlock, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - halfWidth + 2, baseY + 2, oz + z), slabBlock, StructureHelper.SET_FLAGS);
            // East platform (1 block from wall)
            world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 2, oz + z), slabBlock, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + 2, oz + z), slabBlock, StructureHelper.SET_FLAGS);
        }

        // === IRON BAR WINDOWS (narrow slits) on long walls ===
        for (int z = -halfLength + 3; z <= halfLength - 3; z += 4) {
            world.setBlockState(new BlockPos(ox - halfWidth, baseY + 3, oz + z), ironBars, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth, baseY + 3, oz + z), ironBars, StructureHelper.SET_FLAGS);
        }

        // === CHIEF'S HIGH SEAT — center of east wall, facing west across the hall ===
        // Historically accurate: chief sits on the long wall, not at the end
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + 3, oz),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        // Clear platform under the seat for it to sit properly
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + 2, oz), planks, StructureHelper.SET_FLAGS);
        // Armrests (fences on either side)
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + 3, oz - 1),
            palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + 3, oz + 1),
            palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);

        // === BEDS on raised platforms at north end (4 beds) ===
        // FACING=SOUTH: FOOT at lower Z, HEAD at higher Z
        for (int x : new int[]{-halfWidth + 1, -halfWidth + 2, halfWidth - 2, halfWidth - 1}) {
            world.setBlockState(new BlockPos(ox + x, baseY + 3, oz - halfLength + 2), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + 3, oz - halfLength + 3), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // === BENCHES (stairs) along walls between hearth area ===
        for (int z = -halfLength + 5; z <= halfLength - 5; z += 3) {
            world.setBlockState(new BlockPos(ox - halfWidth + 3, baseY + 2, oz + z),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 3, baseY + 2, oz + z),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // === CHESTS + STORAGE ===
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 1, baseY + 3, oz - halfLength + 1),
            Direction.EAST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - 1, baseY + 3, oz + halfLength - 2),
            Direction.WEST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);
        // Barrels near south entrance
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 3, oz + halfLength - 4), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 3, oz + halfLength - 5), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);

        // === WALL TORCHES INSIDE ===
        // FACING = direction torch points (away from wall)
        for (int z = -halfLength + 2; z <= halfLength - 2; z += 4) {
            world.setBlockState(new BlockPos(ox - halfWidth + 1, baseY + 4, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 1, baseY + 4, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // === SPRUCE LOG PALISADE — rough oval (3 blocks tall) ===
        int palisadeRadiusX = halfWidth + 5;  // wider on short axis
        int palisadeRadiusZ = halfLength + 4; // tighter on long axis
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int px = ox + (int)(palisadeRadiusX * Math.cos(rad));
            int pz = oz + (int)(palisadeRadiusZ * Math.sin(rad));

            // Skip gate openings at north and south
            int relZ = pz - oz;
            int relX = px - ox;
            boolean isSouthGate = (relZ > palisadeRadiusZ - 2) && Math.abs(relX) <= 1;
            boolean isNorthGate = (relZ < -palisadeRadiusZ + 2) && Math.abs(relX) <= 1;
            if (isSouthGate || isNorthGate) continue;

            // 3-tall spruce log palisade
            for (int y = 0; y <= 2; y++) {
                world.setBlockState(new BlockPos(px, baseY + y, pz), logWall, StructureHelper.SET_FLAGS);
            }
            // Foundation under palisade
            world.setBlockState(new BlockPos(px, baseY - 1, pz), cobble, StructureHelper.SET_FLAGS);
        }

        // Palisade gate posts and fence gates at south entrance
        for (int y = 0; y <= 2; y++) {
            world.setBlockState(new BlockPos(ox - 2, baseY + y, oz + palisadeRadiusZ), logWall, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 2, baseY + y, oz + palisadeRadiusZ), logWall, StructureHelper.SET_FLAGS);
        }
        for (int gx = -1; gx <= 1; gx++) {
            world.setBlockState(new BlockPos(ox + gx, baseY + 1, oz + palisadeRadiusZ),
                palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // Palisade gate posts and fence gates at north entrance
        for (int y = 0; y <= 2; y++) {
            world.setBlockState(new BlockPos(ox - 2, baseY + y, oz - palisadeRadiusZ), logWall, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 2, baseY + y, oz - palisadeRadiusZ), logWall, StructureHelper.SET_FLAGS);
        }
        for (int gx = -1; gx <= 1; gx++) {
            world.setBlockState(new BlockPos(ox + gx, baseY + 1, oz - palisadeRadiusZ),
                palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }

        // Lanterns on palisade gate posts
        world.setBlockState(new BlockPos(ox - 2, baseY + 3, oz + palisadeRadiusZ),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 2, baseY + 3, oz + palisadeRadiusZ),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 2, baseY + 3, oz - palisadeRadiusZ),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 2, baseY + 3, oz - palisadeRadiusZ),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);

        int boundsRadius = Math.max(palisadeRadiusX, palisadeRadiusZ) + 2;
        placeJigsawConnectors(world, center, boundsRadius);

        VillageCastles.LOGGER.info("Taiga longhouse (mead hall) generation complete!");
        return new CastleBounds(
            center.add(-boundsRadius, -1, -boundsRadius),
            center.add(boundsRadius, wallHeight + roofPeak + 2, boundsRadius)
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
        BlockState campfireState = Blocks.CAMPFIRE.getDefaultState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.getDefaultState();
        BlockState hangingLantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
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
        world.setBlockState(mutable, campfireState, StructureHelper.SET_FLAGS);

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
                        world.setBlockState(mutable, hangingLantern, StructureHelper.SET_FLAGS);
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
        BlockState standingSoulLantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);

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
            case TAIGA -> generateTaigaRingFort(world, center, radius);
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
        VillageCastles.LOGGER.info("Generating fortified caravanserai palace at {}", center.toShortString());

        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        int wallHeight = 7;
        int wallThickness = 2;
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        // Block palette
        BlockState sandstone = palette.getPrimaryWallState();
        BlockState cutSandstone = Blocks.CUT_SANDSTONE.getDefaultState();
        BlockState smoothSandstone = Blocks.SMOOTH_SANDSTONE.getDefaultState();
        BlockState chiseledSandstone = Blocks.CHISELED_SANDSTONE.getDefaultState();
        BlockState orangeTerracotta = Blocks.ORANGE_TERRACOTTA.getDefaultState();
        BlockState soulLantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        BlockState floorLantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        BlockState wallBlock = palette.wall.getDefaultState();
        BlockState ironBars = Blocks.IRON_BARS.getDefaultState();
        BlockState water = Blocks.WATER.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState acaciaSlab = palette.woodSlab.getDefaultState();
        BlockState carpet = palette.getCarpetState();
        BlockState bedFoot = palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD);

        // Compound dimensions - rectangular, longer north-south
        int halfW = radius;          // east-west half width
        int halfD = radius + 5;      // north-south half depth (longer)

        // Prepare ground before building
        prepareGround(world, center, Math.max(halfW, halfD));

        // ================================================================
        // Step 1: Thick outer walls (2-block thick SANDSTONE), flat roof
        // ================================================================
        // North/South walls
        for (int x = -halfW; x <= halfW; x++) {
            for (int t = 0; t < wallThickness; t++) {
                for (int y = 0; y < wallHeight; y++) {
                    mutable.set(ox + x, baseY + y, oz - halfD + t);
                    world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
                    mutable.set(ox + x, baseY + y, oz + halfD - t);
                    world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
                }
            }
        }
        // East/West walls
        for (int z = -halfD; z <= halfD; z++) {
            for (int t = 0; t < wallThickness; t++) {
                for (int y = 0; y < wallHeight; y++) {
                    mutable.set(ox - halfW + t, baseY + y, oz + z);
                    world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
                    mutable.set(ox + halfW - t, baseY + y, oz + z);
                    world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Flat walkable roof on outer walls
        for (int x = -halfW; x <= halfW; x++) {
            for (int t = 0; t < wallThickness; t++) {
                mutable.set(ox + x, baseY + wallHeight, oz - halfD + t);
                world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
                mutable.set(ox + x, baseY + wallHeight, oz + halfD - t);
                world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = -halfD; z <= halfD; z++) {
            for (int t = 0; t < wallThickness; t++) {
                mutable.set(ox - halfW + t, baseY + wallHeight, oz + z);
                world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
                mutable.set(ox + halfW - t, baseY + wallHeight, oz + z);
                world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            }
        }

        // SANDSTONE_WALL parapet on roof edges
        for (int x = -halfW; x <= halfW; x++) {
            mutable.set(ox + x, baseY + wallHeight + 1, oz - halfD);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(ox + x, baseY + wallHeight + 1, oz + halfD);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
        }
        for (int z = -halfD; z <= halfD; z++) {
            mutable.set(ox - halfW, baseY + wallHeight + 1, oz + z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(ox + halfW, baseY + wallHeight + 1, oz + z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
        }

        // Iron bar mashrabiya windows on exterior walls (every 4 blocks, at y+3 and y+4)
        // Punch through both outer and inner wall layers
        for (int x = -halfW + 4; x <= halfW - 4; x += 4) {
            for (int wy = 3; wy <= 4; wy++) {
                // Outer layer (north/south)
                mutable.set(ox + x, baseY + wy, oz - halfD);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + x, baseY + wy, oz + halfD);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
                // Inner layer (north/south)
                mutable.set(ox + x, baseY + wy, oz - halfD + 1);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + x, baseY + wy, oz + halfD - 1);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = -halfD + 4; z <= halfD - 4; z += 4) {
            for (int wy = 3; wy <= 4; wy++) {
                // Outer layer (east/west)
                mutable.set(ox - halfW, baseY + wy, oz + z);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + halfW, baseY + wy, oz + z);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
                // Inner layer (east/west)
                mutable.set(ox - halfW + 1, baseY + wy, oz + z);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + halfW - 1, baseY + wy, oz + z);
                world.setBlockState(mutable, ironBars, StructureHelper.SET_FLAGS);
            }
        }

        // Clear interior
        StructureHelper.clearInterior(world,
            center.add(-halfW + wallThickness, 0, -halfD + wallThickness),
            center.add(halfW - wallThickness, wallHeight + 2, halfD - wallThickness));

        // ================================================================
        // Step 2: Interior floor - base smooth sandstone
        // ================================================================
        StructureHelper.fillBox(world,
            center.add(-halfW + wallThickness, 0, -halfD + wallThickness),
            center.add(halfW - wallThickness, 0, halfD - wallThickness),
            smoothSandstone);

        // ================================================================
        // Step 3: Internal partition walls dividing rooms around courtyards
        // ================================================================
        // Layout (south = +Z):
        //   North edge rooms: Lord's chambers (center), connecting passage
        //   Main courtyard: center-south area
        //   Private courtyard: center-north area (smaller)
        //   East rooms: Guard quarters
        //   West rooms: Kitchen/stores
        //   South rooms: Audience hall

        int innerW = halfW - wallThickness;   // Inner usable half-width (RELATIVE)
        int innerD = halfD - wallThickness;   // Inner usable half-depth (RELATIVE)

        // Dividing wall between private courtyard (north) and main courtyard (south)
        // This wall runs east-west at oz - 4
        // NOTE: divideZ is ABSOLUTE (includes oz)
        int divideZ = oz - 4;
        int roomWallHeight = 5; // Internal walls shorter than outer walls
        for (int x = -innerW; x <= innerW; x++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(ox + x, baseY + y, divideZ);
                world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Room partition walls along east side (guard quarters) - Z from divideZ to south wall
        // NOTE: eastRoomX is ABSOLUTE (includes ox)
        int eastRoomX = ox + innerW - 7; // East room is 7 blocks wide
        for (int z = divideZ + 1; z <= oz + innerD; z++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(eastRoomX, baseY + y, z);
                world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Room partition walls along west side (kitchen) - Z from divideZ to south wall
        // NOTE: westRoomX is ABSOLUTE (includes ox)
        int westRoomX = ox - innerW + 7; // West room is 7 blocks wide
        for (int z = divideZ + 1; z <= oz + innerD; z++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(westRoomX, baseY + y, z);
                world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // South audience hall partition (separates audience hall from main courtyard)
        // NOTE: audienceZ is ABSOLUTE (includes oz)
        int audienceZ = oz + innerD - 6; // Audience hall is 6 blocks deep at south
        for (int x = westRoomX; x <= eastRoomX; x++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(x, baseY + y, audienceZ);
                world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Lord's chambers partition (north side, between private courtyard and north wall)
        // NOTE: lordZ is ABSOLUTE (includes oz)
        int lordZ = oz - innerD + 6; // Lord's chambers 6 blocks deep at north
        for (int x = -innerW; x <= innerW; x++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(ox + x, baseY + y, lordZ);
                world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Doorways in partition walls (3-wide, 3-tall openings)
        // Divide wall: central passage between courtyards
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                mutable.set(ox + dx, baseY + dy, divideZ);
                world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
            }
        }
        // East room doorway
        for (int dy = 1; dy <= 3; dy++) {
            mutable.set(eastRoomX, baseY + dy, divideZ + 4);
            world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
            mutable.set(eastRoomX, baseY + dy, divideZ + 5);
            world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
        }
        // West room doorway
        for (int dy = 1; dy <= 3; dy++) {
            mutable.set(westRoomX, baseY + dy, divideZ + 4);
            world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
            mutable.set(westRoomX, baseY + dy, divideZ + 5);
            world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
        }
        // Audience hall doorway (from courtyard)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                mutable.set(ox + dx, baseY + dy, audienceZ);
                world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
            }
        }
        // Lord's chambers doorway (from private courtyard)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                mutable.set(ox + dx, baseY + dy, lordZ);
                world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
            }
        }

        // ================================================================
        // Step 4: Main courtyard (larger, south-center area)
        // ================================================================
        // Courtyard bounds — ALL ABSOLUTE coordinates
        int cyMinX = westRoomX + 1;
        int cyMaxX = eastRoomX - 1;
        int cyMinZ = divideZ + 1;
        int cyMaxZ = audienceZ - 1;

        // Checkerboard floor: CUT_SANDSTONE + ORANGE_TERRACOTTA
        for (int x = cyMinX; x <= cyMaxX; x++) {
            for (int z = cyMinZ; z <= cyMaxZ; z++) {
                BlockState floorBlock = ((x + z) % 2 == 0) ? cutSandstone : orangeTerracotta;
                mutable.set(x, baseY, z);
                world.setBlockState(mutable, floorBlock, StructureHelper.SET_FLAGS);
            }
        }

        // Central fountain pool (3x3 water, bordered with CUT_SANDSTONE)
        // NOTE: fountainX and fountainZ are ABSOLUTE
        int fountainX = ox;
        int fountainZ = (cyMinZ + cyMaxZ) / 2;
        // Border (5x5 cut sandstone frame, 1 block high)
        for (int fx = -2; fx <= 2; fx++) {
            for (int fz = -2; fz <= 2; fz++) {
                if (Math.abs(fx) == 2 || Math.abs(fz) == 2) {
                    mutable.set(fountainX + fx, baseY, fountainZ + fz);
                    world.setBlockState(mutable, cutSandstone, StructureHelper.SET_FLAGS);
                    mutable.set(fountainX + fx, baseY + 1, fountainZ + fz);
                    world.setBlockState(mutable, cutSandstone, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Water pool (3x3, sunken 1 block)
        for (int fx = -1; fx <= 1; fx++) {
            for (int fz = -1; fz <= 1; fz++) {
                mutable.set(fountainX + fx, baseY, fountainZ + fz);
                world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
            }
        }
        // Central column (no cascade water at top — too messy)
        mutable.set(fountainX, baseY + 1, fountainZ);
        world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
        mutable.set(fountainX, baseY + 2, fountainZ);
        world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);

        // Colonnade walkways around main courtyard (pillars + shade roof every 2 blocks)
        for (int x = cyMinX; x <= cyMaxX; x += 2) {
            // North colonnade (along divideZ+1)
            mutable.set(x, baseY + 1, cyMinZ);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 2, cyMinZ);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 3, cyMinZ);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 4, cyMinZ);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            // Shade roof extends one block into courtyard
            mutable.set(x, baseY + 4, cyMinZ + 1);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);

            // South colonnade (along audienceZ-1)
            mutable.set(x, baseY + 1, cyMaxZ);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 2, cyMaxZ);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 3, cyMaxZ);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 4, cyMaxZ);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 4, cyMaxZ - 1);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
        }
        for (int z = cyMinZ; z <= cyMaxZ; z += 2) {
            // East colonnade
            mutable.set(cyMaxX, baseY + 1, z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX, baseY + 2, z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX, baseY + 3, z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX, baseY + 4, z);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX - 1, baseY + 4, z);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);

            // West colonnade
            mutable.set(cyMinX, baseY + 1, z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX, baseY + 2, z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX, baseY + 3, z);
            world.setBlockState(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX, baseY + 4, z);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX + 1, baseY + 4, z);
            world.setBlockState(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
        }

        // Water channels (chahar bagh layout) - from fountain to courtyard corners
        // North channel
        for (int z = cyMinZ + 1; z < fountainZ - 2; z++) {
            mutable.set(fountainX, baseY, z);
            world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
            mutable.set(fountainX - 1, baseY, z);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(fountainX + 1, baseY, z);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }
        // South channel
        for (int z = fountainZ + 3; z <= cyMaxZ - 1; z++) {
            mutable.set(fountainX, baseY, z);
            world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
            mutable.set(fountainX - 1, baseY, z);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(fountainX + 1, baseY, z);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }
        // East channel — water at baseY, borders at baseY to match north/south channels
        for (int x = fountainX + 3; x <= cyMaxX - 1; x++) {
            mutable.set(x, baseY, fountainZ);
            world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
            // Border blocks on sides at baseY (same level as north/south channel borders)
            mutable.set(x, baseY, fountainZ - 1);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY, fountainZ + 1);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }
        // West channel — water at baseY, borders at baseY to match north/south channels
        for (int x = cyMinX + 1; x < fountainX - 2; x++) {
            mutable.set(x, baseY, fountainZ);
            world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
            // Border blocks on sides at baseY
            mutable.set(x, baseY, fountainZ - 1);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY, fountainZ + 1);
            world.setBlockState(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }

        // Bell in main courtyard
        mutable.set(fountainX + 3, baseY, fountainZ + 3);
        world.setBlockState(mutable, palette.fence.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(fountainX + 3, baseY + 1, fountainZ + 3);
        world.setBlockState(mutable, Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Soul lanterns throughout main courtyard (hanging from colonnade slabs)
        for (int x = cyMinX + 1; x <= cyMaxX - 1; x += 3) {
            mutable.set(x, baseY + 3, cyMinZ + 1);
            world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 3, cyMaxZ - 1);
            world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 5: Private courtyard (smaller, north area between lordZ and divideZ)
        // ================================================================
        // NOTE: pcMinZ, pcMaxZ are ABSOLUTE; pcMinX, pcMaxX are RELATIVE
        int pcMinZ = lordZ + 1;
        int pcMaxZ = divideZ - 1;
        int pcMinX = -innerW + 2;
        int pcMaxX = innerW - 2;

        // Small pool in private courtyard (2x2)
        int pcCenterX = ox;
        int pcCenterZ = (pcMinZ + pcMaxZ) / 2;
        for (int fx = 0; fx <= 1; fx++) {
            for (int fz = 0; fz <= 1; fz++) {
                mutable.set(pcCenterX + fx, baseY, pcCenterZ + fz);
                world.setBlockState(mutable, water, StructureHelper.SET_FLAGS);
            }
        }
        // Pool border
        for (int fx = -1; fx <= 2; fx++) {
            mutable.set(pcCenterX + fx, baseY, pcCenterZ - 1);
            world.setBlockState(mutable, cutSandstone, StructureHelper.SET_FLAGS);
            mutable.set(pcCenterX + fx, baseY, pcCenterZ + 2);
            world.setBlockState(mutable, cutSandstone, StructureHelper.SET_FLAGS);
        }
        for (int fz = 0; fz <= 1; fz++) {
            mutable.set(pcCenterX - 1, baseY, pcCenterZ + fz);
            world.setBlockState(mutable, cutSandstone, StructureHelper.SET_FLAGS);
            mutable.set(pcCenterX + 2, baseY, pcCenterZ + fz);
            world.setBlockState(mutable, cutSandstone, StructureHelper.SET_FLAGS);
        }

        // Decorated pots in private courtyard corners (pcMinX/pcMaxX are relative, pcMinZ is absolute)
        mutable.set(ox + pcMinX + 1, baseY + 1, pcMinZ + 1);
        world.setBlockState(mutable, Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMaxX - 1, baseY + 1, pcMinZ + 1);
        world.setBlockState(mutable, Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // Soul lanterns in private courtyard — with solid block above for hanging
        mutable.set(ox + pcMinX + 1, baseY + 5, pcMinZ + 1);
        world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMinX + 1, baseY + 4, pcMinZ + 1);
        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMaxX - 1, baseY + 5, pcMaxZ - 1);
        world.setBlockState(mutable, sandstone, StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMaxX - 1, baseY + 4, pcMaxZ - 1);
        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 6: Audience hall (south room, between audienceZ and south wall)
        // ================================================================
        // NOTE: ahFloorZ and ahBackZ are ABSOLUTE
        int ahFloorZ = audienceZ + 1;
        int ahBackZ = oz + innerD;

        // Throne (SANDSTONE_STAIRS facing north - seated player faces north toward courtyard)
        world.setBlockState(new BlockPos(ox, baseY + 1, ahBackZ - 1),
            Blocks.SANDSTONE_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.NORTH)
                .with(StairsBlock.HALF, BlockHalf.BOTTOM),
            StructureHelper.SET_FLAGS);
        // Throne back
        world.setBlockState(new BlockPos(ox, baseY + 1, ahBackZ),
            chiseledSandstone, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY + 2, ahBackZ),
            chiseledSandstone, StructureHelper.SET_FLAGS);

        // Yellow carpet runner from door to throne
        for (int z = ahFloorZ; z <= ahBackZ - 2; z++) {
            mutable.set(ox, baseY + 1, z);
            world.setBlockState(mutable, carpet, StructureHelper.SET_FLAGS);
        }

        // Chiseled sandstone pillar accents (2 on each side of carpet)
        for (int pz = ahFloorZ + 1; pz <= ahBackZ - 2; pz += 3) {
            for (int py = 1; py <= roomWallHeight; py++) {
                mutable.set(ox - 2, baseY + py, pz);
                world.setBlockState(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
                mutable.set(ox + 2, baseY + py, pz);
                world.setBlockState(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Soul lanterns in audience hall
        mutable.set(ox - 2, baseY + roomWallHeight, ahFloorZ + 2);
        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);
        mutable.set(ox + 2, baseY + roomWallHeight, ahFloorZ + 2);
        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 7: Guard quarters (east room)
        // ================================================================
        // NOTE: gqMinX, gqMaxX, gqMinZ, gqCenterX are ALL ABSOLUTE
        int gqMinX = eastRoomX + 1;
        int gqMaxX = ox + innerW;
        int gqMinZ = divideZ + 1;
        int gqCenterX = (gqMinX + gqMaxX) / 2;

        // 3 beds along east wall (facing SOUTH: foot at z, head at z+1)
        for (int b = 0; b < 3; b++) {
            int bedX = gqMinX + 1 + b * 2;
            if (bedX >= gqMaxX) break;
            int bedZ = gqMinZ + 1;
            world.setBlockState(new BlockPos(bedX, baseY + 1, bedZ),
                bedFoot.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(bedX, baseY + 1, bedZ + 1),
                bedHead.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // Chests for guards
        StructureHelper.placeChest(world, new BlockPos(gqMaxX - 1, baseY + 1, gqMinZ + 4),
            Direction.WEST, LootTables.VILLAGE_DESERT_HOUSE_CHEST);
        StructureHelper.placeChest(world, new BlockPos(gqMaxX - 1, baseY + 1, gqMinZ + 6),
            Direction.WEST, LootTables.VILLAGE_DESERT_HOUSE_CHEST);

        // Soul lantern
        mutable.set(gqCenterX, baseY + roomWallHeight, gqMinZ + 3);
        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 8: Kitchen/stores (west room)
        // ================================================================
        // NOTE: ktMinX is ABSOLUTE, ktMaxX is ABSOLUTE, ktMinZ is ABSOLUTE
        int ktMinX = ox - innerW;
        int ktMaxX = westRoomX - 1;
        int ktMinZ = divideZ + 1;

        // Smoker
        mutable.set(ktMinX + 1, baseY + 1, ktMinZ + 1);
        world.setBlockState(mutable, Blocks.SMOKER.getDefaultState()
            .with(HorizontalFacingBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Crafting table
        mutable.set(ktMinX + 3, baseY + 1, ktMinZ + 1);
        world.setBlockState(mutable, Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);

        // Barrels
        mutable.set(ktMinX + 1, baseY + 1, ktMinZ + 3);
        world.setBlockState(mutable, Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ktMinX + 1, baseY + 2, ktMinZ + 3);
        world.setBlockState(mutable, Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ktMinX + 2, baseY + 1, ktMinZ + 3);
        world.setBlockState(mutable, Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Cauldron
        mutable.set(ktMinX + 4, baseY + 1, ktMinZ + 1);
        world.setBlockState(mutable, Blocks.CAULDRON.getDefaultState(), StructureHelper.SET_FLAGS);

        // Soul lantern
        mutable.set(ktMinX + 3, baseY + roomWallHeight, ktMinZ + 2);
        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 9: Lord's chambers (north, between north wall and lordZ)
        // ================================================================
        // NOTE: lcMinZ, lcMaxZ, lcCenterZ are ALL ABSOLUTE
        int lcMinZ = oz - innerD;
        int lcMaxZ = lordZ - 1;
        int lcCenterZ = (lcMinZ + lcMaxZ) / 2;

        // 2 beds (facing SOUTH)
        world.setBlockState(new BlockPos(ox - 3, baseY + 1, lcCenterZ),
            bedFoot.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 3, baseY + 1, lcCenterZ + 1),
            bedHead.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 3, baseY + 1, lcCenterZ),
            bedFoot.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 3, baseY + 1, lcCenterZ + 1),
            bedHead.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Bookshelf
        mutable.set(ox - 2, baseY + 1, lcMinZ + 1);
        world.setBlockState(mutable, Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ox - 1, baseY + 1, lcMinZ + 1);
        world.setBlockState(mutable, Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        mutable.set(ox - 2, baseY + 2, lcMinZ + 1);
        world.setBlockState(mutable, Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);

        // Personal chest
        StructureHelper.placeChest(world, new BlockPos(ox + 2, baseY + 1, lcMinZ + 1),
            Direction.SOUTH, LootTables.VILLAGE_DESERT_HOUSE_CHEST);

        // Decorated pot
        mutable.set(ox + 4, baseY + 1, lcMinZ + 1);
        world.setBlockState(mutable, Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);

        // Soul lanterns
        mutable.set(ox, baseY + roomWallHeight, lcCenterZ);
        world.setBlockState(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 10: Entrance - recessed doorway on south wall
        // ================================================================
        // Carve entrance (3 wide, 4 tall) through south wall
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                mutable.set(ox + dx, baseY + dy, oz + halfD);
                world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
                mutable.set(ox + dx, baseY + dy, oz + halfD - 1);
                world.setBlockState(mutable, air, StructureHelper.SET_FLAGS);
            }
        }
        // Chiseled sandstone door frame
        for (int dy = 1; dy <= 4; dy++) {
            mutable.set(ox - 2, baseY + dy, oz + halfD);
            world.setBlockState(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
            mutable.set(ox + 2, baseY + dy, oz + halfD);
            world.setBlockState(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
        }
        // Arch top
        for (int dx = -2; dx <= 2; dx++) {
            mutable.set(ox + dx, baseY + 5, oz + halfD);
            world.setBlockState(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 11: Ladder access to roof (interior, east side near entrance)
        // ================================================================
        int ladderX = ox + innerW;
        int ladderZ = oz + innerD - 1;
        for (int ly = 1; ly <= wallHeight; ly++) {
            mutable.set(ladderX, baseY + ly, ladderZ);
            world.setBlockState(mutable, Blocks.LADDER.getDefaultState()
                .with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 12: Room ceilings (smooth sandstone slabs over rooms)
        // ================================================================
        // Ceiling over audience hall (all vars are absolute — no ox/oz prefix needed)
        StructureHelper.fillBox(world,
            new BlockPos(westRoomX + 1, baseY + roomWallHeight + 1, ahFloorZ),
            new BlockPos(eastRoomX - 1, baseY + roomWallHeight + 1, ahBackZ),
            smoothSandstone);
        // Ceiling over guard quarters
        StructureHelper.fillBox(world,
            new BlockPos(gqMinX, baseY + roomWallHeight + 1, gqMinZ),
            new BlockPos(gqMaxX, baseY + roomWallHeight + 1, audienceZ - 1),
            smoothSandstone);
        // Ceiling over kitchen
        StructureHelper.fillBox(world,
            new BlockPos(ktMinX, baseY + roomWallHeight + 1, ktMinZ),
            new BlockPos(ktMaxX, baseY + roomWallHeight + 1, audienceZ - 1),
            smoothSandstone);
        // Ceiling over lord's chambers
        StructureHelper.fillBox(world,
            new BlockPos(ox - innerW, baseY + roomWallHeight + 1, lcMinZ),
            new BlockPos(ox + innerW, baseY + roomWallHeight + 1, lcMaxZ),
            smoothSandstone);

        placeJigsawConnectors(world, center, Math.max(halfW, halfD));

        VillageCastles.LOGGER.info("Fortified caravanserai palace generation complete!");
        return new CastleBounds(
            center.add(-halfW - 2, 0, -halfD - 2),
            center.add(halfW + 2, wallHeight + 4, halfD + 2)
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

        // HAY_BLOCK thatched roof (barely above ground level) — single layer
        int roofY = baseY + wallHeight;
        StructureHelper.fillBox(world,
            center.add(-halfWidth - 1, wallHeight, -halfLength - 1),
            center.add(halfWidth + 1, wallHeight, halfLength + 1),
            hayBlock);

        // Ensure 3-block minimum headroom above floor everywhere inside
        StructureHelper.clearInterior(world,
            new BlockPos(ox - halfWidth + 1, floorY + 1, oz - halfLength + 1),
            new BlockPos(ox + halfWidth - 1, floorY + 3, oz + halfLength - 1));

        // Support pillars down the center (acacia logs, floor to roof)
        // Offset by 2 from bed positions to avoid placing beams on top of beds
        for (int z = -halfLength + 6; z <= halfLength - 4; z += 5) {
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
            // Smoke hole in roof above each fire
            world.setBlockState(new BlockPos(ox, roofY, oz + z), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            // Also clear adjacent blocks for larger smoke hole
            for (int[] adj : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                world.setBlockState(new BlockPos(ox + adj[0], roofY, oz + z + adj[1]),
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
            baseY, sprucePlanks);

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
        for (int z = oz - halfDepth + 1; z <= dividerZ - 1; z++) {
            world.setBlockState(new BlockPos(ox - halfWidth + 1, walkwayY, z), spruceSlab, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 1, walkwayY, z), spruceSlab, StructureHelper.SET_FLAGS);
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
            baseY, sprucePlanks);
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
        int hallFloor = baseY + 1; // furniture on the spruce plank floor at baseY

        // Massive double hearth — 2 campfires on stone brick base, against east wall
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY, oz + 2), stoneBrick, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY, oz + 3), stoneBrick, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 2, hallFloor, oz + 2), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + halfWidth - 2, hallFloor, oz + 3), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        // Chimney above hearth (stone brick column through roof)
        for (int y = wallHeight; y <= wallHeight + ridgeHeight + 2; y++) {
            world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + y, oz + 2), stoneBrick, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + halfWidth - 2, baseY + y, oz + 3), stoneBrick, StructureHelper.SET_FLAGS);
        }

        // Long table (fence + slab)
        for (int z = 0; z <= 4; z++) {
            world.setBlockState(new BlockPos(ox, baseY, oz + z), spruceFence, StructureHelper.SET_FLAGS);
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
        world.setBlockState(center.add(0, 1, -halfDepth + 3),
            Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // === ENTRANCE — south wall ===
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz + halfDepth), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Floor at threshold
        world.setBlockState(new BlockPos(ox - 1, baseY, oz + halfDepth), sprucePlanks, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY, oz + halfDepth), sprucePlanks, StructureHelper.SET_FLAGS);

        // Courtyard entrance — north wall
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, oz - halfDepth), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // Bell in courtyard
        world.setBlockState(center.add(-3, 0, -halfDepth + 3), spruceFence, StructureHelper.SET_FLAGS);
        world.setBlockState(center.add(-3, 1, -halfDepth + 3), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

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
            case TAIGA -> generateTaigaTowerHouse(world, center, radius);
            default -> generateLargeGrandCastle(world, center, radius, keepHalfWidth, keepHalfDepth);
        };
    }

    /**
     * SNOWY LARGE — Ancient Ice Citadel.
     *
     * A hexagonal fortress of six spires connected by thick textured ice walls.
     * The north spire is the largest — hollow, with a wide spiral staircase
     * going up and down from a mid-level entrance reached by a grand bridge
     * from the courtyard center. Built for beings twice human size.
     *
     * The villagers adapted it: cobblestone stairs bridging giant 2-block rises,
     * spruce scaffolding climbing the spire exteriors, wooden platforms near the
     * tips, campfire camps huddled in corners of the vast courtyard.
     *
     * Texture: blue ice (structural), packed ice (variation), snow block (accents),
     * regular ice (translucent windows). Not monochrome — intentionally textured.
     *
     * Below the big spire: a secret shrine chamber with a diamond block.
     */
    private CastleBounds generateIcePalace(ServerWorld world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockPos.Mutable m = new BlockPos.Mutable();

        // Block palette — textured, not monochrome
        BlockState blueIce = Blocks.BLUE_ICE.getDefaultState();
        BlockState packedIce = Blocks.PACKED_ICE.getDefaultState();
        BlockState snowBlock = Blocks.SNOW_BLOCK.getDefaultState();
        BlockState ice = Blocks.ICE.getDefaultState(); // translucent
        BlockState seaLantern = Blocks.SEA_LANTERN.getDefaultState();
        BlockState spruce = palette.getPlanksState();
        BlockState fence = palette.getFenceState();
        BlockState log = palette.log.getDefaultState();
        BlockState cobbleStairs = Blocks.COBBLESTONE_STAIRS.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();

        // Dimensions — built for giants (2x human scale)
        int hexR = 34;             // hexagon vertex radius from center
        int wallH = 14;            // wall height between spires
        int wallThick = 3;         // wall thickness
        int smallSpireR = 5;       // regular spire base radius
        int smallSpireH = 32;      // regular spire height
        int bigSpireR = 20;        // MASSIVE — eats 2/3 of the courtyard
        int bigSpireH = 55;        // taller to match the wider base
        int bigSpireShell = 3;     // thick shell walls
        // Big spire centered north of hex center, not at vertex
        int bigSX = ox;
        int bigSZ = oz - hexR / 3; // shifted north, connected to back walls
        int bridgeY = baseY + 12;  // bridge entrance mid-level on spire
        int dungeonDepth = Math.min(10, baseY + 63);

        prepareGround(world, center, hexR + 5);

        // Hexagon vertex positions (MC coords: +X=east, -Z=north)
        int[][] verts = {
            {0, -hexR},                                          // 0: NORTH — big spire
            {(int)(hexR * 0.866), -(hexR / 2)},                 // 1: NE
            {(int)(hexR * 0.866),  (hexR / 2)},                 // 2: SE
            {0,  hexR},                                          // 3: SOUTH — entrance
            {-(int)(hexR * 0.866),  (hexR / 2)},                // 4: SW
            {-(int)(hexR * 0.866), -(hexR / 2)}                 // 5: NW
        };

        // Deterministic texture function — mixed ice based on position
        // 60% blue ice, 25% packed ice, 10% snow block, 5% regular ice
        java.util.function.Function<BlockPos, BlockState> iceTexture = (BlockPos pos) -> {
            int hash = ((pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 7) & 0x1F);
            if (hash < 19) return blueIce;
            if (hash < 27) return packedIce;
            if (hash < 30) return snowBlock;
            return ice;
        };

        // ============================================================
        // SECTION 1: SIX SPIRES
        // ============================================================

        // === 5 SMALL SPIRES at hex vertices 1-5 (skip 0=north, big spire is separate) ===
        for (int si = 1; si < 6; si++) {
            int sx = ox + verts[si][0];
            int sz = oz + verts[si][1];

            // Tapered solid spire
            for (int y = 0; y < smallSpireH; y++) {
                double taper = 1.0 - ((double) y / (smallSpireH + 8));
                int r = Math.max(1, (int)(smallSpireR * taper));
                for (int bx = -r; bx <= r; bx++) {
                    for (int bz = -r; bz <= r; bz++) {
                        if (bx * bx + bz * bz <= r * r) {
                            BlockPos bp = new BlockPos(sx + bx, baseY + y, sz + bz);
                            world.setBlockState(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Tip + end rod
            for (int y = 0; y <= 4; y++) {
                world.setBlockState(new BlockPos(sx, baseY + smallSpireH + y, sz), blueIce, StructureHelper.SET_FLAGS);
            }
            world.setBlockState(new BlockPos(sx, baseY + smallSpireH + 5, sz),
                Blocks.END_ROD.getDefaultState(), StructureHelper.SET_FLAGS);

            // Sea lantern windows
            for (int y = 6; y < smallSpireH - 5; y += 6) {
                for (int angle = 0; angle < 360; angle += 90) {
                    double rad = Math.toRadians(angle);
                    int r = Math.max(1, (int)(smallSpireR * (1.0 - (double) y / (smallSpireH + 8))));
                    int wx = (int)(r * Math.cos(rad));
                    int wz = (int)(r * Math.sin(rad));
                    world.setBlockState(new BlockPos(sx + wx, baseY + y, sz + wz), seaLantern, StructureHelper.SET_FLAGS);
                }
            }

            // Villager crow's nest — wraps AROUND the spire where it's still wide
            // Place at ~60% height where spire radius is still substantial
            int nestHeight = (int)(smallSpireH * 0.55);
            int nestY = baseY + nestHeight;
            // Calculate spire radius at this height and wrap just outside it
            int spireRAtNest = Math.max(2, (int)(smallSpireR * (1.0 - (double) nestHeight / (smallSpireH + 8))));
            int nestR = spireRAtNest + 2; // wraps outside the spire wall at this height
            // Circular platform
            for (int px = -nestR; px <= nestR; px++) {
                for (int pz = -nestR; pz <= nestR; pz++) {
                    int distSq = px * px + pz * pz;
                    if (distSq <= nestR * nestR && distSq > (nestR - 2) * (nestR - 2)) {
                        world.setBlockState(new BlockPos(sx + px, nestY, sz + pz), spruce, StructureHelper.SET_FLAGS);
                        // Fence railing on outer edge
                        if (distSq > (nestR - 1) * (nestR - 1)) {
                            world.setBlockState(new BlockPos(sx + px, nestY + 1, sz + pz), fence, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Ladder up the south face of spire to the nest
            for (int y = 1; y <= nestHeight; y++) {
                world.setBlockState(new BlockPos(sx, baseY + y, sz + smallSpireR),
                    Blocks.LADDER.getDefaultState().with(net.minecraft.block.LadderBlock.FACING, Direction.SOUTH),
                    StructureHelper.SET_FLAGS);
            }
            // Lantern on nest
            world.setBlockState(new BlockPos(sx + nestR - 1, nestY + 1, sz),
                Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        // === MASSIVE BIG SPIRE — hollow, centered at bigSX/bigSZ ===
        for (int y = 0; y < bigSpireH; y++) {
            double taper = 1.0 - ((double) y / (bigSpireH + 10));
            int r = Math.max(2, (int)(bigSpireR * taper));
            int ir = r - bigSpireShell;
            for (int bx = -r; bx <= r; bx++) {
                for (int bz = -r; bz <= r; bz++) {
                    int distSq = bx * bx + bz * bz;
                    if (distSq <= r * r) {
                        // Hollow: skip interior above ground level
                        if (y > 0 && ir > 2 && distSq < ir * ir) continue;
                        BlockPos bp = new BlockPos(bigSX + bx, baseY + y, bigSZ + bz);
                        world.setBlockState(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
        // Tip + end rod
        for (int y = 0; y <= 5; y++) {
            world.setBlockState(new BlockPos(bigSX, baseY + bigSpireH + y, bigSZ), blueIce, StructureHelper.SET_FLAGS);
        }
        world.setBlockState(new BlockPos(bigSX, baseY + bigSpireH + 6, bigSZ),
            Blocks.END_ROD.getDefaultState(), StructureHelper.SET_FLAGS);

        // Sea lanterns spiraling inside the big spire (visible looking up)
        for (int y = 5; y < bigSpireH - 5; y += 3) {
            double angle = Math.toRadians(y * 25);
            int r = Math.max(2, (int)(bigSpireR * (1.0 - (double) y / (bigSpireH + 10)))) - bigSpireShell;
            if (r < 2) continue;
            int lx = (int)((r - 1) * Math.cos(angle));
            int lz = (int)((r - 1) * Math.sin(angle));
            world.setBlockState(new BlockPos(bigSX + lx, baseY + y, bigSZ + lz), seaLantern, StructureHelper.SET_FLAGS);
        }
        // Sea lantern at peak
        world.setBlockState(new BlockPos(bigSX, baseY + bigSpireH - 2, bigSZ), seaLantern, StructureHelper.SET_FLAGS);

        // ============================================================
        // SECTION 2: WALLS CONNECTING SPIRES
        // ============================================================

        for (int wi = 0; wi < 6; wi++) {
            int wj = (wi + 1) % 6;
            int x1 = verts[wi][0], z1 = verts[wi][1];
            int x2 = verts[wj][0], z2 = verts[wj][1];
            int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
            if (steps == 0) continue;

            for (int s = 0; s <= steps; s++) {
                int wx = ox + x1 + (x2 - x1) * s / steps;
                int wz = oz + z1 + (z2 - z1) * s / steps;

                // Wall perpendicular direction for thickness
                double dx = (double)(x2 - x1) / steps;
                double dz = (double)(z2 - z1) / steps;
                // Perpendicular: (-dz, dx) normalized
                double len = Math.sqrt(dx * dx + dz * dz);
                double px = -dz / len;
                double pz = dx / len;

                // Vary wall height by position for organic look
                int localH = wallH + ((wx * 7 + wz * 13) % 5) - 2; // -2 to +2 variation

                for (int t = -wallThick / 2; t <= wallThick / 2; t++) {
                    int bx = wx + (int)(px * t);
                    int bz = wz + (int)(pz * t);
                    for (int y = 0; y < localH; y++) {
                        BlockPos bp = new BlockPos(bx, baseY + y, bz);
                        world.setBlockState(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                    }
                    // Snow on top
                    world.setBlockState(new BlockPos(bx, baseY + localH, bz), snowBlock, StructureHelper.SET_FLAGS);
                }
                // Villager plank spanning gaps where wall dips low
                if (localH < wallH) {
                    int midX = wx;
                    int midZ = wz;
                    for (int y = localH; y <= wallH; y++) {
                        world.setBlockState(new BlockPos(midX, baseY + y, midZ), spruce, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // ============================================================
        // SECTION 3: COURTYARD FLOOR
        // ============================================================

        // Fill the interior of the hexagon with packed ice floor
        for (int x = -hexR; x <= hexR; x++) {
            for (int z = -hexR; z <= hexR; z++) {
                // Point-in-hexagon test (simplified: if within all 6 half-planes)
                boolean inside = true;
                for (int i = 0; i < 6 && inside; i++) {
                    int j = (i + 1) % 6;
                    int ex = verts[j][0] - verts[i][0];
                    int ez = verts[j][1] - verts[i][1];
                    int px2 = x - verts[i][0];
                    int pz2 = z - verts[i][1];
                    if (ex * pz2 - ez * px2 > 0) inside = false;
                }
                if (inside) {
                    m.set(ox + x, baseY, oz + z);
                    world.setBlockState(m, packedIce, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ============================================================
        // SECTION 4: GRAND BRIDGE + ICE SCULPTURES
        // ============================================================

        // Bridge from courtyard south to big spire entrance
        // Rises from baseY to bridgeY over the gap
        int bridgeHalfW = 3; // 7 blocks wide (giant scale)
        int bridgeStartZ = oz + hexR / 3; // south part of courtyard
        int bridgeEndZ = bigSZ + bigSpireR + 1; // just outside big spire south face
        int bridgeLen = bridgeStartZ - bridgeEndZ;
        if (bridgeLen <= 0) bridgeLen = 1; // safety

        for (int bz = bridgeEndZ; bz <= bridgeStartZ; bz++) {
            // Y rises linearly from baseY (at south/center) to bridgeY (at north/spire)
            double progress = (double)(bridgeStartZ - bz) / bridgeLen;
            int by = baseY + (int)(progress * (bridgeY - baseY));

            for (int bx = -bridgeHalfW; bx <= bridgeHalfW; bx++) {
                BlockPos bp = new BlockPos(ox + bx, by, bz);
                world.setBlockState(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                // Giant-scale railings (2 blocks tall, packed ice)
                if (Math.abs(bx) == bridgeHalfW) {
                    world.setBlockState(new BlockPos(ox + bx, by + 1, bz), packedIce, StructureHelper.SET_FLAGS);
                    world.setBlockState(new BlockPos(ox + bx, by + 2, bz), packedIce, StructureHelper.SET_FLAGS);
                }
            }
            // Clear headroom above bridge surface
            for (int bx = -bridgeHalfW + 1; bx < bridgeHalfW; bx++) {
                for (int h = 1; h <= 5; h++) {
                    world.setBlockState(new BlockPos(ox + bx, by + h, bz), air, StructureHelper.SET_FLAGS);
                }
            }

            // Villager adaptation: cobblestone stairs every 2 blocks of rise
            // The bridge rises 1 block per ~3 Z, so every 3 blocks place a cobble step
            if (bz % 3 == 0 && by > baseY) {
                for (int bx = -bridgeHalfW + 1; bx < bridgeHalfW; bx++) {
                    world.setBlockState(new BlockPos(ox + bx, by + 1, bz),
                        cobbleStairs.with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Open the big spire wall at bridge entrance height — grand doorway
        int doorZ = bigSZ + bigSpireR; // south face of the spire
        for (int bx = -3; bx <= 3; bx++) { // 7 wide (giant scale)
            for (int y = bridgeY - 1; y <= bridgeY + 5; y++) { // 7 tall
                for (int dz = 0; dz < bigSpireShell + 1; dz++) {
                    world.setBlockState(new BlockPos(bigSX + bx, y, doorZ - dz), air, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Also open a ground-level entrance (villagers walk in from the courtyard)
        for (int bx = -2; bx <= 2; bx++) {
            for (int y = 1; y <= 4; y++) {
                for (int dz = 0; dz < bigSpireShell + 1; dz++) {
                    world.setBlockState(new BlockPos(bigSX + bx, baseY + y, doorZ - dz), air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Ice sculptures flanking the bridge start
        for (int side : new int[]{-1, 1}) {
            int sculX = ox + side * (bridgeHalfW + 3);
            int sculZ = (bridgeStartZ + bridgeEndZ) / 2; // midpoint of bridge
            // Pedestal
            StructureHelper.fillBox(world, new BlockPos(sculX - 1, baseY, sculZ - 1),
                new BlockPos(sculX + 1, baseY + 2, sculZ + 1), packedIce);
            // Figure (abstract: column with end rod)
            for (int y = 3; y <= 7; y++) {
                world.setBlockState(new BlockPos(sculX, baseY + y, sculZ), blueIce, StructureHelper.SET_FLAGS);
            }
            world.setBlockState(new BlockPos(sculX, baseY + 8, sculZ),
                Blocks.END_ROD.getDefaultState(), StructureHelper.SET_FLAGS);
            // Arms (horizontal blue ice)
            world.setBlockState(new BlockPos(sculX - 1, baseY + 6, sculZ), blueIce, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(sculX + 1, baseY + 6, sculZ), blueIce, StructureHelper.SET_FLAGS);
        }

        // ============================================================
        // SECTION 5: BIG SPIRE INTERIOR — spiral staircase
        // ============================================================

        // Clear the hollow interior (bigSX/bigSZ already defined above)
        for (int y = 1; y < bigSpireH - 3; y++) {
            int r = Math.max(1, (int)(bigSpireR * (1.0 - (double) y / (bigSpireH + 8))));
            int ir = r - 2;
            if (ir < 2) continue;
            for (int bx = -ir; bx <= ir; bx++) {
                for (int bz = -ir; bz <= ir; bz++) {
                    if (bx * bx + bz * bz < ir * ir) {
                        world.setBlockState(new BlockPos(bigSX + bx, baseY + y, bigSZ + bz),
                            air, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // Spiral staircase — giant scale (2-block rise, 3-block tread depth)
        // Spirals around the interior wall, going both up and down from bridgeY
        int stairR = bigSpireR - 3; // stair radius (inside the shell)
        int stairWidth = 3; // tread width

        // Upward spiral from bridge level
        double spiralAngle = Math.PI; // start facing south (toward bridge)
        int currentY = bridgeY;
        for (int step = 0; step < 60 && currentY < baseY + bigSpireH - 8; step++) {
            spiralAngle += 0.3; // ~20 degrees per step
            int stepX = bigSX + (int)(stairR * Math.cos(spiralAngle));
            int stepZ = bigSZ + (int)(stairR * Math.sin(spiralAngle));

            // Place giant tread (3 wide, 2 blocks deep along arc)
            for (int tw = -1; tw <= 1; tw++) {
                int twX = stepX + (int)(tw * Math.sin(spiralAngle));
                int twZ = stepZ - (int)(tw * Math.cos(spiralAngle));
                m.set(twX, currentY, twZ);
                world.setBlockState(m, iceTexture.apply(m.toImmutable()), StructureHelper.SET_FLAGS);
                // Clear headroom
                for (int h = 1; h <= 4; h++) {
                    world.setBlockState(new BlockPos(twX, currentY + h, twZ), air, StructureHelper.SET_FLAGS);
                }
                // Villager cobble stairs on each giant step
                world.setBlockState(new BlockPos(twX, currentY + 1, twZ),
                    cobbleStairs.with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            }
            // Rise every other step (giant 2-block rise)
            if (step % 2 == 1) currentY += 2;
        }

        // Downward spiral to dungeon
        spiralAngle = Math.PI;
        currentY = bridgeY - 2;
        for (int step = 0; step < 20 && currentY > baseY - dungeonDepth + 2; step++) {
            spiralAngle -= 0.3;
            int stepX = bigSX + (int)(stairR * Math.cos(spiralAngle));
            int stepZ = bigSZ + (int)(stairR * Math.sin(spiralAngle));

            for (int tw = -1; tw <= 1; tw++) {
                int twX = stepX + (int)(tw * Math.sin(spiralAngle));
                int twZ = stepZ - (int)(tw * Math.cos(spiralAngle));
                m.set(twX, currentY, twZ);
                world.setBlockState(m, iceTexture.apply(m.toImmutable()), StructureHelper.SET_FLAGS);
                for (int h = 1; h <= 4; h++) {
                    world.setBlockState(new BlockPos(twX, currentY + h, twZ), air, StructureHelper.SET_FLAGS);
                }
            }
            if (step % 2 == 1) currentY -= 2;
        }

        // Entrance platform inside the spire at bridge level
        for (int bx = -3; bx <= 3; bx++) {
            for (int bz = -3; bz <= 3; bz++) {
                world.setBlockState(new BlockPos(bigSX + bx, bridgeY, bigSZ + bz),
                    packedIce, StructureHelper.SET_FLAGS);
            }
        }
        // Clear headroom above platform
        for (int bx = -3; bx <= 3; bx++) {
            for (int bz = -3; bz <= 3; bz++) {
                for (int h = 1; h <= 5; h++) {
                    world.setBlockState(new BlockPos(bigSX + bx, bridgeY + h, bigSZ + bz),
                        air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Sea lantern at spire peak (visible from inside looking up)
        world.setBlockState(new BlockPos(bigSX, baseY + bigSpireH - 2, bigSZ), seaLantern, StructureHelper.SET_FLAGS);

        // ============================================================
        // SECTION 6: VILLAGER HABITATION — INSIDE the big spire
        // ============================================================

        // Ground floor platform inside the spire
        int innerFloorR = bigSpireR - bigSpireShell - 1; // usable interior
        // Spruce plank platform covering the ground floor interior
        for (int px = -innerFloorR; px <= innerFloorR; px++) {
            for (int pz = -innerFloorR; pz <= innerFloorR; pz++) {
                if (px * px + pz * pz < innerFloorR * innerFloorR) {
                    world.setBlockState(new BlockPos(bigSX + px, baseY + 1, bigSZ + pz), spruce, StructureHelper.SET_FLAGS);
                }
            }
        }
        int fy = baseY + 2; // furniture Y

        // Central campfire hearth (the warmth everything orbits)
        world.setBlockState(new BlockPos(bigSX, baseY + 1, bigSZ), Blocks.STONE_BRICKS.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX, fy, bigSZ), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX + 1, baseY + 1, bigSZ), Blocks.STONE_BRICKS.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX + 1, fy, bigSZ), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX, baseY + 1, bigSZ + 1), Blocks.STONE_BRICKS.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX, fy, bigSZ + 1), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);

        // Beds arranged in a circle around the hearth
        int bedR = innerFloorR - 3;
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int bx = bigSX + (int)(bedR * Math.cos(rad));
            int bz = bigSZ + (int)(bedR * Math.sin(rad));
            // Face inward toward the fire
            Direction bedFacing = (Math.abs(Math.cos(rad)) > Math.abs(Math.sin(rad)))
                ? (Math.cos(rad) > 0 ? Direction.WEST : Direction.EAST)
                : (Math.sin(rad) > 0 ? Direction.NORTH : Direction.SOUTH);
            int dx = bedFacing == Direction.EAST ? 1 : bedFacing == Direction.WEST ? -1 : 0;
            int dz = bedFacing == Direction.SOUTH ? 1 : bedFacing == Direction.NORTH ? -1 : 0;
            world.setBlockState(new BlockPos(bx, fy, bz), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, bedFacing), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(bx + dx, fy, bz + dz), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, bedFacing), StructureHelper.SET_FLAGS);
        }

        // Storage cluster (east side of interior)
        world.setBlockState(new BlockPos(bigSX + innerFloorR - 2, fy, bigSZ), Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX + innerFloorR - 2, fy, bigSZ + 1), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX + innerFloorR - 2, fy, bigSZ - 1), Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, new BlockPos(bigSX + innerFloorR - 3, fy, bigSZ),
            Direction.EAST, LootTables.VILLAGE_SNOWY_HOUSE_CHEST);

        // Spruce log columns supporting upper platforms (villager construction)
        for (int angle = 0; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int lx = bigSX + (int)((innerFloorR - 1) * Math.cos(rad));
            int lz = bigSZ + (int)((innerFloorR - 1) * Math.sin(rad));
            for (int y = 0; y <= 8; y++) {
                world.setBlockState(new BlockPos(lx, baseY + y, lz), log, StructureHelper.SET_FLAGS);
            }
        }

        // Lanterns inside the spire (regular — warm human light)
        for (int angle = 30; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int lx = bigSX + (int)((innerFloorR - 2) * Math.cos(rad));
            int lz = bigSZ + (int)((innerFloorR - 2) * Math.sin(rad));
            world.setBlockState(new BlockPos(lx, baseY, lz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(lx, baseY + 1, lz), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(lx, baseY + 2, lz),
                Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        // Bell inside the spire
        world.setBlockState(new BlockPos(bigSX - innerFloorR + 2, baseY, bigSZ), fence, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX - innerFloorR + 2, baseY + 1, bigSZ), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        // Courtyard lanterns (a few in the narrow passage between spire and walls)
        for (int[] lp : new int[][]{{ox, oz + hexR - 5}, {ox - 15, oz + 5}, {ox + 15, oz + 5}}) {
            world.setBlockState(new BlockPos(lp[0], baseY, lp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(lp[0], baseY + 1, lp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(lp[0], baseY + 2, lp[1]),
                Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        // South entrance gap (in the south wall segment)
        for (int bx = -3; bx <= 3; bx++) {
            for (int y = 1; y <= 5; y++) {
                for (int t = -wallThick / 2; t <= wallThick / 2; t++) {
                    world.setBlockState(new BlockPos(ox + bx, baseY + y, oz + hexR + t), air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ============================================================
        // SECTION 7: SECRET DUNGEON
        // ============================================================

        int dungeonFloorY = baseY - dungeonDepth;
        int chamberR = 5;
        int chamberCZ = bigSZ - 15; // beneath the big spire, offset north

        // Carve the chamber below the big spire (the spiral staircase descends here)
        for (int x = -chamberR; x <= chamberR; x++) {
            for (int z = -chamberR; z <= chamberR; z++) {
                if (x * x + z * z <= chamberR * chamberR) {
                    // Floor
                    world.setBlockState(new BlockPos(bigSX + x, dungeonFloorY, chamberCZ + z), blueIce, StructureHelper.SET_FLAGS);
                    // Walls + hollow
                    for (int y = 1; y <= 6; y++) {
                        boolean edge = x * x + z * z > (chamberR - 1) * (chamberR - 1);
                        world.setBlockState(new BlockPos(bigSX + x, dungeonFloorY + y, chamberCZ + z),
                            edge ? blueIce : air, StructureHelper.SET_FLAGS);
                    }
                    // Dome ceiling
                    world.setBlockState(new BlockPos(bigSX + x, dungeonFloorY + 7, chamberCZ + z), blueIce, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Diamond shrine
        world.setBlockState(new BlockPos(bigSX, dungeonFloorY + 1, chamberCZ), packedIce, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX, dungeonFloorY + 2, chamberCZ), packedIce, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX, dungeonFloorY + 3, chamberCZ), Blocks.DIAMOND_BLOCK.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(bigSX, dungeonFloorY + 4, chamberCZ), Blocks.END_ROD.getDefaultState(), StructureHelper.SET_FLAGS);

        // Shrine pillars
        for (int angle = 0; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int px = (int)(3 * Math.cos(rad));
            int pz = (int)(3 * Math.sin(rad));
            for (int y = 1; y <= 5; y++) {
                world.setBlockState(new BlockPos(bigSX + px, dungeonFloorY + y, chamberCZ + pz), blueIce, StructureHelper.SET_FLAGS);
            }
            world.setBlockState(new BlockPos(bigSX + px, dungeonFloorY + 6, chamberCZ + pz),
                Blocks.END_ROD.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // Soul lanterns (the only light — ancient)
        for (int[] sl : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
            world.setBlockState(new BlockPos(bigSX + sl[0], dungeonFloorY + 3, chamberCZ + sl[1]),
                Blocks.SOUL_LANTERN.getDefaultState().with(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        placeJigsawConnectors(world, center, hexR);

        VillageCastles.LOGGER.info("Ancient Ice Citadel generation complete!");
        return new CastleBounds(
            center.add(-hexR - 5, -dungeonDepth - chamberR - 2, -hexR - 5),
            center.add(hexR + 5, bigSpireH + 10, hexR + 5)
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
     * DESERT LARGE — Citadel Palace with Ziggurat Tower and Underground Pool.
     * An inhabited stepped citadel: 4 tiers each inset 3 blocks, grand staircase on south face,
     * tiered rooms (ground: throne hall + barracks + kitchen; tier 2: lord's quarters + pergola;
     * tier 3: garden terrace; tier 4: open-air throne pavilion), plus underground pool chamber.
     */
    private CastleBounds generateDesertPyramid(ServerWorld world, BlockPos center, int radius) {
        VillageCastles.LOGGER.info("Generating desert citadel palace at {}", center.toShortString());

        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        prepareGround(world, center, radius);

        // Block palette
        BlockState sandstone = palette.getPrimaryWallState();
        BlockState cutSandstone = Blocks.CUT_SANDSTONE.getDefaultState();
        BlockState smoothSandstone = Blocks.SMOOTH_SANDSTONE.getDefaultState();
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState soulLantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        BlockState floorLantern = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, false);
        BlockState orangeTerracotta = Blocks.ORANGE_TERRACOTTA.getDefaultState();
        BlockState chiseledSandstone = Blocks.CHISELED_SANDSTONE.getDefaultState();
        BlockState carpet = palette.getCarpetState();
        BlockState wallBlock = palette.wall.getDefaultState();
        BlockState prismarine = Blocks.PRISMARINE.getDefaultState();
        BlockState acaciaLog = palette.log.getDefaultState();
        BlockState acaciaSlab = palette.woodSlab.getDefaultState();
        BlockState acaciaFence = palette.fence.getDefaultState();
        BlockState bedFoot = palette.bed.getDefaultState().with(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = palette.bed.getDefaultState().with(BedBlock.PART, BedPart.HEAD);

        BlockPos.Mutable m = new BlockPos.Mutable();

        // Tier parameters
        int tierCount = 4;
        int tierInset = 3;
        int baseHalf = radius;

        // Tier heights: each tier is 6 blocks tall (5 usable + 1 floor/ceiling)
        int tierHeight = 6;

        // ================================================================
        // Step 1: Build solid tier masses
        // ================================================================
        for (int tier = 0; tier < tierCount; tier++) {
            int half = baseHalf - (tier * tierInset);
            int yStart = baseY + (tier * tierHeight);

            if (half < 4) break;

            // Solid fill for each tier
            StructureHelper.fillBox(world,
                new BlockPos(ox - half, yStart, oz - half),
                new BlockPos(ox + half, yStart + tierHeight, oz + half),
                sandstone);

            // Smooth sandstone floor on terrace
            StructureHelper.fillBox(world,
                new BlockPos(ox - half, yStart + tierHeight, oz - half),
                new BlockPos(ox + half, yStart + tierHeight, oz + half),
                smoothSandstone);

            // Sandstone wall railings on terrace edges
            if (tier > 0) {
                for (int ex = -half; ex <= half; ex++) {
                    m.set(ox + ex, yStart + tierHeight + 1, oz - half);
                    world.setBlockState(m, wallBlock, StructureHelper.SET_FLAGS);
                    m.set(ox + ex, yStart + tierHeight + 1, oz + half);
                    world.setBlockState(m, wallBlock, StructureHelper.SET_FLAGS);
                }
                for (int ez = -half + 1; ez <= half - 1; ez++) {
                    m.set(ox - half, yStart + tierHeight + 1, oz + ez);
                    world.setBlockState(m, wallBlock, StructureHelper.SET_FLAGS);
                    m.set(ox + half, yStart + tierHeight + 1, oz + ez);
                    world.setBlockState(m, wallBlock, StructureHelper.SET_FLAGS);
                }
            }

            VillageCastles.LOGGER.debug("Citadel tier {} built: half={}, y={}-{}",
                tier, half, yStart, yStart + tierHeight);
        }

        // ================================================================
        // Step 2: Grand staircase on south face (5-wide)
        // ================================================================
        {
            int stairHalfWidth = 2; // 5 blocks total (-2 to +2)
            BlockState stairBlock = Blocks.SANDSTONE_STAIRS.getDefaultState()
                .with(StairsBlock.FACING, Direction.NORTH)
                .with(StairsBlock.HALF, BlockHalf.BOTTOM);

            for (int tier = 0; tier < tierCount; tier++) {
                int half = baseHalf - (tier * tierInset);
                int tierBaseY = baseY + (tier * tierHeight);

                if (half < 4) break;

                // Build stairs ascending the south face of this tier
                for (int step = 0; step < tierHeight; step++) {
                    int stepY = tierBaseY + step;
                    int stepZ = oz + half - step;

                    // Stair blocks (facing NORTH = ascending northward)
                    for (int sx = -stairHalfWidth; sx <= stairHalfWidth; sx++) {
                        m.set(ox + sx, stepY, stepZ);
                        world.setBlockState(m, stairBlock, StructureHelper.SET_FLAGS);

                        // Clear headroom above steps
                        for (int clearY = 1; clearY <= 4; clearY++) {
                            m.set(ox + sx, stepY + clearY, stepZ);
                            world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                        }
                    }

                    // SANDSTONE_WALL balustrades on both sides
                    m.set(ox - stairHalfWidth - 1, stepY + 1, stepZ);
                    world.setBlockState(m, wallBlock, StructureHelper.SET_FLAGS);
                    m.set(ox + stairHalfWidth + 1, stepY + 1, stepZ);
                    world.setBlockState(m, wallBlock, StructureHelper.SET_FLAGS);
                    // Solid support under balustrades
                    m.set(ox - stairHalfWidth - 1, stepY, stepZ);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                    m.set(ox + stairHalfWidth + 1, stepY, stepZ);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);

                    // Soul lanterns every 4 steps on balustrade
                    if (step % 4 == 0) {
                        m.set(ox - stairHalfWidth - 1, stepY + 2, stepZ);
                        world.setBlockState(m, floorLantern, StructureHelper.SET_FLAGS);
                        m.set(ox + stairHalfWidth + 1, stepY + 2, stepZ);
                        world.setBlockState(m, floorLantern, StructureHelper.SET_FLAGS);
                    }
                }

                // ACACIA_LOG columns at each landing (top of tier staircase)
                int landingY = tierBaseY + tierHeight;
                int landingZ = oz + half - tierHeight + 1;
                for (int ly = 0; ly <= 3; ly++) {
                    m.set(ox - stairHalfWidth - 1, landingY + ly, landingZ);
                    world.setBlockState(m, acaciaLog, StructureHelper.SET_FLAGS);
                    m.set(ox + stairHalfWidth + 1, landingY + ly, landingZ);
                    world.setBlockState(m, acaciaLog, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ================================================================
        // Step 3: Tier 1 (ground) — Carve interior rooms
        // ================================================================
        {
            int t1Half = baseHalf;
            int t1Floor = baseY + 1;           // Floor surface
            int t1Ceil = baseY + tierHeight - 1; // Ceiling
            int wallThick = 3;                  // Wall thickness for rooms
            int innerHalf = t1Half - wallThick;

            // Central hall — large open space
            StructureHelper.clearInterior(world,
                new BlockPos(ox - innerHalf, t1Floor, oz - innerHalf),
                new BlockPos(ox + innerHalf, t1Ceil, oz + innerHalf));
            StructureHelper.fillBox(world,
                new BlockPos(ox - innerHalf, t1Floor - 1, oz - innerHalf),
                new BlockPos(ox + innerHalf, t1Floor - 1, oz + innerHalf),
                smoothSandstone);

            // CHISELED_SANDSTONE pillars in throne hall (every 4 blocks along center)
            for (int pz = -innerHalf + 2; pz <= innerHalf - 2; pz += 4) {
                for (int py = t1Floor; py <= t1Ceil; py++) {
                    m.set(ox - 4, py, oz + pz);
                    world.setBlockState(m, chiseledSandstone, StructureHelper.SET_FLAGS);
                    m.set(ox + 4, py, oz + pz);
                    world.setBlockState(m, chiseledSandstone, StructureHelper.SET_FLAGS);
                }
            }

            // Throne at north wall (facing SOUTH — seated player faces south toward entrance)
            world.setBlockState(new BlockPos(ox, t1Floor, oz - innerHalf + 1),
                Blocks.SANDSTONE_STAIRS.getDefaultState()
                    .with(StairsBlock.FACING, Direction.SOUTH)
                    .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                StructureHelper.SET_FLAGS);
            // Throne back
            world.setBlockState(new BlockPos(ox, t1Floor, oz - innerHalf),
                chiseledSandstone, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox, t1Floor + 1, oz - innerHalf),
                chiseledSandstone, StructureHelper.SET_FLAGS);

            // YELLOW_CARPET runner to throne
            for (int cz = -innerHalf + 2; cz <= innerHalf; cz++) {
                m.set(ox, t1Floor, oz + cz);
                world.setBlockState(m, carpet, StructureHelper.SET_FLAGS);
            }

            // East wing: guard barracks (partitioned off east side)
            int barrackX = ox + innerHalf - 6;
            // Partition wall
            for (int bz = -innerHalf; bz <= innerHalf; bz++) {
                for (int by = t1Floor; by <= t1Ceil; by++) {
                    m.set(barrackX, by, oz + bz);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                }
            }
            // Doorway in partition
            for (int dy = t1Floor; dy <= t1Floor + 2; dy++) {
                m.set(barrackX, dy, oz);
                world.setBlockState(m, air, StructureHelper.SET_FLAGS);
            }
            // 4 beds (facing SOUTH: foot at z, head at z+1)
            for (int b = 0; b < 4; b++) {
                int bedX = barrackX + 2 + (b % 2) * 3;
                int bedZ = oz - innerHalf + 2 + (b / 2) * 4;
                if (bedX <= ox + innerHalf - 1) {
                    world.setBlockState(new BlockPos(bedX, t1Floor, bedZ),
                        bedFoot.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
                    world.setBlockState(new BlockPos(bedX, t1Floor, bedZ + 1),
                        bedHead.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
                }
            }
            // Guard chests
            StructureHelper.placeChest(world, new BlockPos(ox + innerHalf - 1, t1Floor, oz - innerHalf + 1),
                Direction.SOUTH, LootTables.VILLAGE_DESERT_HOUSE_CHEST);
            StructureHelper.placeChest(world, new BlockPos(ox + innerHalf - 1, t1Floor, oz + innerHalf - 1),
                Direction.NORTH, LootTables.VILLAGE_DESERT_HOUSE_CHEST);

            // West wing: kitchen + stores (partitioned off west side)
            int kitchenX = ox - innerHalf + 6;
            for (int bz = -innerHalf; bz <= innerHalf; bz++) {
                for (int by = t1Floor; by <= t1Ceil; by++) {
                    m.set(kitchenX, by, oz + bz);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                }
            }
            // Doorway
            for (int dy = t1Floor; dy <= t1Floor + 2; dy++) {
                m.set(kitchenX, dy, oz);
                world.setBlockState(m, air, StructureHelper.SET_FLAGS);
            }
            // Smokers
            world.setBlockState(new BlockPos(ox - innerHalf + 1, t1Floor, oz - innerHalf + 1),
                Blocks.SMOKER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH),
                StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - innerHalf + 2, t1Floor, oz - innerHalf + 1),
                Blocks.SMOKER.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH),
                StructureHelper.SET_FLAGS);
            // Barrels
            world.setBlockState(new BlockPos(ox - innerHalf + 1, t1Floor, oz - innerHalf + 3),
                Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - innerHalf + 1, t1Floor + 1, oz - innerHalf + 3),
                Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - innerHalf + 2, t1Floor, oz - innerHalf + 3),
                Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
            // Crafting table
            world.setBlockState(new BlockPos(ox - innerHalf + 4, t1Floor, oz - innerHalf + 1),
                Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);

            // South: main entrance hall — carve entrance through south wall
            for (int ex = -2; ex <= 2; ex++) {
                for (int ey = t1Floor; ey <= t1Floor + 3; ey++) {
                    for (int ez = 0; ez < wallThick; ez++) {
                        m.set(ox + ex, ey, oz + t1Half - ez);
                        world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                    }
                }
            }
            // Chiseled sandstone entrance frame
            for (int ey = t1Floor; ey <= t1Floor + 4; ey++) {
                m.set(ox - 3, ey, oz + t1Half);
                world.setBlockState(m, chiseledSandstone, StructureHelper.SET_FLAGS);
                m.set(ox + 3, ey, oz + t1Half);
                world.setBlockState(m, chiseledSandstone, StructureHelper.SET_FLAGS);
            }
            for (int ex = -3; ex <= 3; ex++) {
                m.set(ox + ex, t1Floor + 4, oz + t1Half);
                world.setBlockState(m, chiseledSandstone, StructureHelper.SET_FLAGS);
            }

            // Lanterns throughout tier 1 — more than before for proper lighting
            for (int lx = -innerHalf + 2; lx <= innerHalf - 2; lx += 4) {
                m.set(ox + lx, t1Ceil, oz);
                world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
                // Additional lanterns offset from center line
                m.set(ox + lx, t1Ceil, oz + 3);
                world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
                m.set(ox + lx, t1Ceil, oz - 3);
                world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
            }
            for (int lz = -innerHalf + 2; lz <= innerHalf - 2; lz += 4) {
                m.set(ox, t1Ceil, oz + lz);
                world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
            }

            // Additional furniture on tier 1 for inhabited feel
            // Carpets around throne area
            for (int cx = -1; cx <= 1; cx++) {
                m.set(ox + cx, t1Floor, oz - innerHalf + 2);
                world.setBlockState(m, carpet, StructureHelper.SET_FLAGS);
            }
            // Additional beds in barracks
            for (int b = 0; b < 2; b++) {
                int bedX = barrackX + 2 + (b % 2) * 3;
                int bedZ = oz + 2 + b * 4;
                if (bedX <= ox + innerHalf - 1) {
                    world.setBlockState(new BlockPos(bedX, t1Floor, bedZ),
                        bedFoot.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
                    world.setBlockState(new BlockPos(bedX, t1Floor, bedZ + 1),
                        bedHead.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
                }
            }
            // More barrels in kitchen
            world.setBlockState(new BlockPos(ox - innerHalf + 3, t1Floor, oz - innerHalf + 3),
                Blocks.BARREL.getDefaultState(), StructureHelper.SET_FLAGS);
            // Crafting table near throne
            world.setBlockState(new BlockPos(ox + 3, t1Floor, oz - innerHalf + 2),
                Blocks.CRAFTING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);

            // Stairs descending to underground pool (NE corner of tier 1)
            // Diagonal staircase: each step offsets 1 block in Z and 1 block in Y
            int poolStairX = ox + innerHalf - 2;
            int poolStairZ = oz - innerHalf + 2;
            int poolDepth = Math.min(6, Math.max(2, baseY + 64));
            for (int step = 0; step < poolDepth; step++) {
                int stepZ = poolStairZ - step;
                int stepY = t1Floor - 1 - step;
                m.set(poolStairX, stepY, stepZ);
                world.setBlockState(m,
                    Blocks.SANDSTONE_STAIRS.getDefaultState()
                        .with(StairsBlock.FACING, Direction.SOUTH)
                        .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                    StructureHelper.SET_FLAGS);
                // Clear headroom above
                for (int ch = 1; ch <= 3; ch++) {
                    m.set(poolStairX, stepY + ch, stepZ);
                    world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                }
                // Side walls
                m.set(poolStairX - 1, stepY, stepZ);
                world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                m.set(poolStairX + 1, stepY, stepZ);
                world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
            }

            // Bell on tier 1 (near entrance)
            world.setBlockState(new BlockPos(ox + 5, t1Floor, oz + innerHalf - 1),
                acaciaFence, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 5, t1Floor + 1, oz + innerHalf - 1),
                Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 4: Tier 2 — Residential terrace
        // ================================================================
        {
            int t2Half = baseHalf - tierInset;
            int t2Floor = baseY + tierHeight + 1;
            int t2Ceil = baseY + (2 * tierHeight) - 1;
            int wallThick = 2;
            int innerHalf = t2Half - wallThick;

            // Carve interior for lord's bedchamber (enclosed room on north side)
            int chamberHalf = Math.min(innerHalf, 6);
            StructureHelper.clearInterior(world,
                new BlockPos(ox - chamberHalf, t2Floor, oz - innerHalf),
                new BlockPos(ox + chamberHalf, t2Ceil, oz - innerHalf + 5));
            StructureHelper.fillBox(world,
                new BlockPos(ox - chamberHalf, t2Floor - 1, oz - innerHalf),
                new BlockPos(ox + chamberHalf, t2Floor - 1, oz - innerHalf + 5),
                smoothSandstone);

            // Lord's beds (2, facing SOUTH)
            world.setBlockState(new BlockPos(ox - 2, t2Floor, oz - innerHalf + 1),
                bedFoot.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - 2, t2Floor, oz - innerHalf + 2),
                bedHead.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 2, t2Floor, oz - innerHalf + 1),
                bedFoot.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 2, t2Floor, oz - innerHalf + 2),
                bedHead.with(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

            // Bookshelves against north wall
            for (int bx = -chamberHalf + 1; bx <= -chamberHalf + 3; bx++) {
                for (int by = t2Floor; by <= t2Floor + 1; by++) {
                    m.set(ox + bx, by, oz - innerHalf);
                    world.setBlockState(m, Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }

            // Personal chest
            StructureHelper.placeChest(world, new BlockPos(ox + chamberHalf - 1, t2Floor, oz - innerHalf + 1),
                Direction.WEST, LootTables.VILLAGE_DESERT_HOUSE_CHEST);

            // Doorway from chamber to terrace
            for (int dy = t2Floor; dy <= t2Floor + 2; dy++) {
                m.set(ox, dy, oz - innerHalf + 5);
                world.setBlockState(m, air, StructureHelper.SET_FLAGS);
            }

            // Open terrace with shade pergola (south side of tier 2)
            // Pergola: ACACIA_LOG posts + ACACIA_SLAB roof + ACACIA_FENCE railing
            int pergolaMinZ = oz - innerHalf + 6;
            int pergolaMaxZ = oz + innerHalf;
            int pergolaMinX = ox - chamberHalf;
            int pergolaMaxX = ox + chamberHalf;

            // Posts at corners and midpoints
            for (int px = pergolaMinX; px <= pergolaMaxX; px += 4) {
                for (int pz = pergolaMinZ; pz <= pergolaMaxZ; pz += 4) {
                    for (int py = t2Floor; py <= t2Floor + 3; py++) {
                        m.set(px, py, pz);
                        world.setBlockState(m, acaciaLog, StructureHelper.SET_FLAGS);
                    }
                }
            }
            // Slab roof over pergola
            for (int rx = pergolaMinX; rx <= pergolaMaxX; rx++) {
                for (int rz = pergolaMinZ; rz <= pergolaMaxZ; rz++) {
                    m.set(rx, t2Floor + 4, rz);
                    world.setBlockState(m, acaciaSlab, StructureHelper.SET_FLAGS);
                }
            }
            // Fence railing around terrace edge
            for (int rx = pergolaMinX; rx <= pergolaMaxX; rx++) {
                m.set(rx, t2Floor + 1, pergolaMaxZ);
                world.setBlockState(m, acaciaFence, StructureHelper.SET_FLAGS);
            }
            for (int rz = pergolaMinZ; rz <= pergolaMaxZ; rz++) {
                m.set(pergolaMinX, t2Floor + 1, rz);
                world.setBlockState(m, acaciaFence, StructureHelper.SET_FLAGS);
                m.set(pergolaMaxX, t2Floor + 1, rz);
                world.setBlockState(m, acaciaFence, StructureHelper.SET_FLAGS);
            }

            // Water channel along terrace edge
            for (int wx = pergolaMinX + 1; wx <= pergolaMaxX - 1; wx++) {
                m.set(wx, t2Floor - 1, pergolaMaxZ - 1);
                world.setBlockState(m, Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);
            }

            // Soul lanterns
            m.set(ox - 2, t2Ceil, oz - innerHalf + 3);
            world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
            m.set(ox + 2, t2Ceil, oz - innerHalf + 3);
            world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
            m.set(ox, t2Floor + 3, oz + innerHalf - 2);
            world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 5: Tier 3 — Garden terrace
        // ================================================================
        {
            int t3Half = baseHalf - (2 * tierInset);
            int t3Floor = baseY + (2 * tierHeight);

            // Open air — just railing, pavilion, pool, pots
            // ACACIA_FENCE railings around the edge (on top of the terrace floor)
            for (int rx = -t3Half; rx <= t3Half; rx++) {
                m.set(ox + rx, t3Floor + 1, oz - t3Half);
                world.setBlockState(m, acaciaFence, StructureHelper.SET_FLAGS);
                m.set(ox + rx, t3Floor + 1, oz + t3Half);
                world.setBlockState(m, acaciaFence, StructureHelper.SET_FLAGS);
            }
            for (int rz = -t3Half + 1; rz <= t3Half - 1; rz++) {
                m.set(ox - t3Half, t3Floor + 1, oz + rz);
                world.setBlockState(m, acaciaFence, StructureHelper.SET_FLAGS);
                m.set(ox + t3Half, t3Floor + 1, oz + rz);
                world.setBlockState(m, acaciaFence, StructureHelper.SET_FLAGS);
            }

            // Small shade pavilion (center of tier 3)
            // 4 posts, slab roof
            int pavHalf = 2;
            for (int[] corner : new int[][]{{-pavHalf, -pavHalf}, {pavHalf, -pavHalf}, {-pavHalf, pavHalf}, {pavHalf, pavHalf}}) {
                for (int py = t3Floor + 1; py <= t3Floor + 3; py++) {
                    m.set(ox + corner[0], py, oz + corner[1]);
                    world.setBlockState(m, acaciaLog, StructureHelper.SET_FLAGS);
                }
            }
            for (int sx = -pavHalf; sx <= pavHalf; sx++) {
                for (int sz = -pavHalf; sz <= pavHalf; sz++) {
                    m.set(ox + sx, t3Floor + 4, oz + sz);
                    world.setBlockState(m, acaciaSlab, StructureHelper.SET_FLAGS);
                }
            }

            // Water pool feature (off to one side)
            for (int wx = -1; wx <= 1; wx++) {
                for (int wz = -1; wz <= 1; wz++) {
                    m.set(ox + t3Half - 3 + wx, t3Floor, oz + wz);
                    world.setBlockState(m, Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }

            // Decorated pots and flower pots
            world.setBlockState(new BlockPos(ox - t3Half + 2, t3Floor + 1, oz - t3Half + 2),
                Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + t3Half - 2, t3Floor + 1, oz - t3Half + 2),
                Blocks.DECORATED_POT.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - t3Half + 2, t3Floor + 1, oz + t3Half - 2),
                Blocks.FLOWER_POT.getDefaultState(), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + t3Half - 2, t3Floor + 1, oz + t3Half - 2),
                Blocks.FLOWER_POT.getDefaultState(), StructureHelper.SET_FLAGS);

            // Soul lanterns on pavilion
            m.set(ox, t3Floor + 3, oz);
            world.setBlockState(m, soulLantern, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 6: Tier 4 (summit) — Open-air throne pavilion
        // ================================================================
        {
            int t4Half = baseHalf - (3 * tierInset);
            int t4Floor = baseY + (3 * tierHeight);

            // Ensure at least 7x7 platform
            int platHalf = Math.max(t4Half, 3);

            // 7x7 SMOOTH_SANDSTONE platform (re-lay on top of existing tier)
            StructureHelper.fillBox(world,
                new BlockPos(ox - platHalf, t4Floor, oz - platHalf),
                new BlockPos(ox + platHalf, t4Floor, oz + platHalf),
                smoothSandstone);

            // ORANGE_TERRACOTTA accent floor border
            for (int bx = -platHalf; bx <= platHalf; bx++) {
                m.set(ox + bx, t4Floor, oz - platHalf);
                world.setBlockState(m, orangeTerracotta, StructureHelper.SET_FLAGS);
                m.set(ox + bx, t4Floor, oz + platHalf);
                world.setBlockState(m, orangeTerracotta, StructureHelper.SET_FLAGS);
            }
            for (int bz = -platHalf + 1; bz <= platHalf - 1; bz++) {
                m.set(ox - platHalf, t4Floor, oz + bz);
                world.setBlockState(m, orangeTerracotta, StructureHelper.SET_FLAGS);
                m.set(ox + platHalf, t4Floor, oz + bz);
                world.setBlockState(m, orangeTerracotta, StructureHelper.SET_FLAGS);
            }

            // Clear air above platform
            StructureHelper.clearInterior(world,
                new BlockPos(ox - platHalf, t4Floor + 1, oz - platHalf),
                new BlockPos(ox + platHalf, t4Floor + 5, oz + platHalf));

            // CHISELED_SANDSTONE columns at corners
            int[][] summitCorners = {
                {-platHalf, -platHalf}, {platHalf, -platHalf},
                {-platHalf, platHalf}, {platHalf, platHalf}
            };
            for (int[] c : summitCorners) {
                for (int cy = 1; cy <= 4; cy++) {
                    m.set(ox + c[0], t4Floor + cy, oz + c[1]);
                    world.setBlockState(m, chiseledSandstone, StructureHelper.SET_FLAGS);
                }
                // SOUL_LANTERN on each column
                m.set(ox + c[0], t4Floor + 5, oz + c[1]);
                world.setBlockState(m, floorLantern, StructureHelper.SET_FLAGS);
            }

            // Throne (SANDSTONE_STAIRS facing SOUTH — overlooking everything)
            world.setBlockState(new BlockPos(ox, t4Floor + 1, oz - platHalf + 1),
                Blocks.SANDSTONE_STAIRS.getDefaultState()
                    .with(StairsBlock.FACING, Direction.SOUTH)
                    .with(StairsBlock.HALF, BlockHalf.BOTTOM),
                StructureHelper.SET_FLAGS);
            // Throne back
            world.setBlockState(new BlockPos(ox, t4Floor + 1, oz - platHalf),
                chiseledSandstone, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox, t4Floor + 2, oz - platHalf),
                chiseledSandstone, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 7: Underground pool (beneath tier 1)
        // ================================================================
        {
            int poolDepth = Math.min(6, Math.max(2, baseY + 64));
            int poolFloorY = baseY - poolDepth;
            int poolChamberHalf = 5; // 11x11 chamber
            int poolHalf = 2;        // 5x5 pool
            int poolCeilY = baseY - 1;

            // Carve the chamber
            StructureHelper.clearInterior(world,
                new BlockPos(ox - poolChamberHalf, poolFloorY, oz - poolChamberHalf),
                new BlockPos(ox + poolChamberHalf, poolCeilY, oz + poolChamberHalf));

            // SMOOTH_SANDSTONE walkway floor
            StructureHelper.fillBox(world,
                new BlockPos(ox - poolChamberHalf, poolFloorY, oz - poolChamberHalf),
                new BlockPos(ox + poolChamberHalf, poolFloorY, oz + poolChamberHalf),
                smoothSandstone);

            // Sandstone walls around chamber
            for (int wx = -poolChamberHalf; wx <= poolChamberHalf; wx++) {
                for (int wy = poolFloorY; wy <= poolCeilY; wy++) {
                    m.set(ox + wx, wy, oz - poolChamberHalf);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                    m.set(ox + wx, wy, oz + poolChamberHalf);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                }
            }
            for (int wz = -poolChamberHalf + 1; wz <= poolChamberHalf - 1; wz++) {
                for (int wy = poolFloorY; wy <= poolCeilY; wy++) {
                    m.set(ox - poolChamberHalf, wy, oz + wz);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                    m.set(ox + poolChamberHalf, wy, oz + wz);
                    world.setBlockState(m, sandstone, StructureHelper.SET_FLAGS);
                }
            }
            // Ceiling
            StructureHelper.fillBox(world,
                new BlockPos(ox - poolChamberHalf, poolCeilY + 1, oz - poolChamberHalf),
                new BlockPos(ox + poolChamberHalf, poolCeilY + 1, oz + poolChamberHalf),
                sandstone);

            // PRISMARINE accent border around pool on floor
            for (int px = -poolHalf - 1; px <= poolHalf + 1; px++) {
                m.set(ox + px, poolFloorY, oz - poolHalf - 1);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
                m.set(ox + px, poolFloorY, oz + poolHalf + 1);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
            }
            for (int pz = -poolHalf; pz <= poolHalf; pz++) {
                m.set(ox - poolHalf - 1, poolFloorY, oz + pz);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
                m.set(ox + poolHalf + 1, poolFloorY, oz + pz);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
            }

            // Pool: 5x5, 2 blocks deep, PRISMARINE at bottom
            for (int px = -poolHalf; px <= poolHalf; px++) {
                for (int pz = -poolHalf; pz <= poolHalf; pz++) {
                    // Dig pool 2 deep
                    m.set(ox + px, poolFloorY - 1, oz + pz);
                    world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
                    m.set(ox + px, poolFloorY, oz + pz);
                    world.setBlockState(m, Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);
                    m.set(ox + px, poolFloorY + 1, oz + pz);
                    world.setBlockState(m, Blocks.WATER.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }

            // Raised PRISMARINE border at poolFloorY+2 around pool perimeter
            // Prevents upper water layer from flowing out
            for (int px = -poolHalf - 1; px <= poolHalf + 1; px++) {
                m.set(ox + px, poolFloorY + 2, oz - poolHalf - 1);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
                m.set(ox + px, poolFloorY + 2, oz + poolHalf + 1);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
            }
            for (int pz = -poolHalf; pz <= poolHalf; pz++) {
                m.set(ox - poolHalf - 1, poolFloorY + 2, oz + pz);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
                m.set(ox + poolHalf + 1, poolFloorY + 2, oz + pz);
                world.setBlockState(m, prismarine, StructureHelper.SET_FLAGS);
            }

            // SANDSTONE_WALL column ring around pool
            int colDist = poolHalf + 2;
            int[][] poolCols = {
                {-colDist, -colDist}, {colDist, -colDist},
                {-colDist, colDist}, {colDist, colDist},
                {0, -colDist}, {0, colDist},
                {-colDist, 0}, {colDist, 0}
            };
            for (int[] col : poolCols) {
                for (int cy = poolFloorY + 1; cy <= poolCeilY; cy++) {
                    m.set(ox + col[0], cy, oz + col[1]);
                    world.setBlockState(m, wallBlock, StructureHelper.SET_FLAGS);
                }
            }

            // SOUL_LANTERN in wall niches (recessed 1 block into walls)
            for (int nx = -poolChamberHalf + 3; nx <= poolChamberHalf - 3; nx += 3) {
                // North wall niches
                m.set(ox + nx, poolFloorY + 2, oz - poolChamberHalf + 1);
                world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                m.set(ox + nx, poolFloorY + 2, oz - poolChamberHalf);
                world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                m.set(ox + nx, poolFloorY + 1, oz - poolChamberHalf + 1);
                world.setBlockState(m, floorLantern, StructureHelper.SET_FLAGS);
                // South wall niches
                m.set(ox + nx, poolFloorY + 2, oz + poolChamberHalf - 1);
                world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                m.set(ox + nx, poolFloorY + 2, oz + poolChamberHalf);
                world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                m.set(ox + nx, poolFloorY + 1, oz + poolChamberHalf - 1);
                world.setBlockState(m, floorLantern, StructureHelper.SET_FLAGS);
            }

            // 1x1 light shafts in ceiling — air through shaft, GLASS at floor level
            // so light passes but players cannot fall through
            BlockState glass = Blocks.GLASS.getDefaultState();
            for (int lx = -2; lx <= 2; lx += 2) {
                for (int lz = -2; lz <= 2; lz += 2) {
                    for (int ly = poolCeilY + 1; ly <= baseY + 1; ly++) {
                        m.set(ox + lx, ly, oz + lz);
                        if (ly == baseY + 1) {
                            // Floor level (t1Floor) — glass so players don't fall in
                            world.setBlockState(m, glass, StructureHelper.SET_FLAGS);
                        } else {
                            world.setBlockState(m, air, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }

            VillageCastles.LOGGER.debug("Underground pool chamber placed at y={}", poolFloorY);
        }

        // ================================================================
        // Step 8: Chests throughout + Jigsaw connectors + Return bounds
        // ================================================================
        // Additional chests in tier 1 throne hall
        StructureHelper.placeChest(world, new BlockPos(ox - 3, baseY + 1, oz - baseHalf + 4),
            Direction.SOUTH, LootTables.VILLAGE_DESERT_HOUSE_CHEST);

        placeJigsawConnectors(world, center, radius);

        int totalHeight = (tierCount * tierHeight) + 6;

        VillageCastles.LOGGER.info("Desert citadel palace generation complete! {} tiers, height={}",
            tierCount, totalHeight);

        return new CastleBounds(
            center.add(-baseHalf - 4, -8, -baseHalf - 4),
            center.add(baseHalf + 4, totalHeight + 5, baseHalf + 4)
        );
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

    // ==========================================================================
    // TAIGA MEDIUM — Scandinavian Ring Fort
    // ==========================================================================

    /**
     * TAIGA MEDIUM — Scandinavian ring fort garrison.
     * Circular rampart wall (mossy cobblestone base + spruce log palisade),
     * 4 cardinal gates, cross-road, central longhouse, corner watchtower
     * platforms, fighting walkway, bell at crossroads.
     */
    private CastleBounds generateTaigaRingFort(ServerWorld world, BlockPos center, int radius) {
        int fortRadius = 22;
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        BlockState logWall = palette.log.getDefaultState();
        BlockState mossyCobble = palette.getSecondaryWallState();
        BlockState cobble = palette.getPrimaryWallState();
        BlockState planks = palette.getPlanksState();
        BlockState slabBlock = palette.woodSlab.getDefaultState();
        BlockState fenceBlock = palette.fence.getDefaultState();
        BlockState roofBlock = palette.getRoofState();
        BlockState cobblePath = Blocks.COBBLESTONE.getDefaultState();

        // === GROUND PREPARATION ===
        prepareGround(world, center, fortRadius + 2);

        // === CIRCULAR RAMPART WALL ===
        // 2 blocks mossy cobblestone base + 3 blocks spruce log palisade = 5 total
        int wallThickness = 2;
        for (int angle = 0; angle < 360; angle += 1) {
            double rad = Math.toRadians(angle);
            int wx = ox + (int)(fortRadius * Math.cos(rad));
            int wz = oz + (int)(fortRadius * Math.sin(rad));

            // Check if this is a gate position (3-wide gap at each cardinal)
            int relX = wx - ox;
            int relZ = wz - oz;
            boolean isNorthGate = (relZ < -fortRadius + 3) && Math.abs(relX) <= 1;
            boolean isSouthGate = (relZ > fortRadius - 3) && Math.abs(relX) <= 1;
            boolean isEastGate = (relX > fortRadius - 3) && Math.abs(relZ) <= 1;
            boolean isWestGate = (relX < -fortRadius + 3) && Math.abs(relZ) <= 1;
            if (isNorthGate || isSouthGate || isEastGate || isWestGate) continue;

            // Inner and outer ring for wall thickness
            for (int t = 0; t < wallThickness; t++) {
                double innerRad = fortRadius - t;
                int iwx = ox + (int)(innerRad * Math.cos(rad));
                int iwz = oz + (int)(innerRad * Math.sin(rad));

                // 2 blocks mossy cobblestone base
                for (int y = 0; y <= 1; y++) {
                    world.setBlockState(new BlockPos(iwx, baseY + y, iwz), mossyCobble, StructureHelper.SET_FLAGS);
                }
                // 3 blocks spruce log palisade on top
                for (int y = 2; y <= 4; y++) {
                    world.setBlockState(new BlockPos(iwx, baseY + y, iwz), logWall, StructureHelper.SET_FLAGS);
                }
            }
        }

        // === FIGHTING WALKWAY — spruce slab on interior wall at 3 blocks up ===
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int walkX = ox + (int)((fortRadius - 2) * Math.cos(rad));
            int walkZ = oz + (int)((fortRadius - 2) * Math.sin(rad));
            world.setBlockState(new BlockPos(walkX, baseY + 3, walkZ), slabBlock, StructureHelper.SET_FLAGS);
        }

        // === 4 CARDINAL GATES — 3-wide tunnels through rampart ===
        // South gate
        for (int dz = -wallThickness; dz <= 0; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlockState(new BlockPos(ox + dx, baseY + y, oz + fortRadius + dz), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
            // Spruce log frame on sides
            for (int y = 0; y <= 4; y++) {
                world.setBlockState(new BlockPos(ox - 2, baseY + y, oz + fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + 2, baseY + y, oz + fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
            }
        }
        // Fence gates at south entrance
        for (int dx = -1; dx <= 1; dx++) {
            world.setBlockState(new BlockPos(ox + dx, baseY + 1, oz + fortRadius),
                palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // North gate
        for (int dz = 0; dz <= wallThickness; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlockState(new BlockPos(ox + dx, baseY + y, oz - fortRadius + dz), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
            for (int y = 0; y <= 4; y++) {
                world.setBlockState(new BlockPos(ox - 2, baseY + y, oz - fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + 2, baseY + y, oz - fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            world.setBlockState(new BlockPos(ox + dx, baseY + 1, oz - fortRadius),
                palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }

        // East gate
        for (int ddx = -wallThickness; ddx <= 0; ddx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlockState(new BlockPos(ox + fortRadius + ddx, baseY + y, oz + dz), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
            for (int y = 0; y <= 4; y++) {
                world.setBlockState(new BlockPos(ox + fortRadius + ddx, baseY + y, oz - 2), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + fortRadius + ddx, baseY + y, oz + 2), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(ox + fortRadius, baseY + 1, oz + dz),
                palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        }

        // West gate
        for (int ddx = 0; ddx <= wallThickness; ddx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlockState(new BlockPos(ox - fortRadius + ddx, baseY + y, oz + dz), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                }
            }
            for (int y = 0; y <= 4; y++) {
                world.setBlockState(new BlockPos(ox - fortRadius + ddx, baseY + y, oz - 2), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox - fortRadius + ddx, baseY + y, oz + 2), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(ox - fortRadius, baseY + 1, oz + dz),
                palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // === CROSS-ROAD — cobblestone paths N-S and E-W ===
        for (int i = -fortRadius + 2; i <= fortRadius - 2; i++) {
            // N-S path (x = center, vary z)
            world.setBlockState(new BlockPos(ox, baseY, oz + i), cobblePath, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox - 1, baseY, oz + i), cobblePath, StructureHelper.SET_FLAGS);
            // E-W path (z = center, vary x)
            world.setBlockState(new BlockPos(ox + i, baseY, oz), cobblePath, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + i, baseY, oz - 1), cobblePath, StructureHelper.SET_FLAGS);
        }

        // === BELL at crossroads intersection ===
        // Bell needs a support block above it
        world.setBlockState(new BlockPos(ox, baseY + 1, oz), logWall, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY, oz), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        // === CENTRAL LONGHOUSE (smaller: 8x5 footprint) ===
        int lhHalfW = 4; // east-west (8 wide)
        int lhHalfD = 2; // north-south (5 deep)
        int lhWallH = 4;
        int lhRoofPeak = lhHalfW; // steep 1:1

        // Offset longhouse slightly north so it doesn't sit on the crossroads
        int lhOz = oz - 6;

        // Stone foundation
        StructureHelper.fillBox(world, new BlockPos(ox - lhHalfW, baseY, lhOz - lhHalfD),
            new BlockPos(ox + lhHalfW, baseY, lhOz + lhHalfD), cobble);
        // Log walls
        for (int x = -lhHalfW; x <= lhHalfW; x++) {
            for (int y = 1; y <= lhWallH; y++) {
                world.setBlockState(new BlockPos(ox + x, baseY + y, lhOz - lhHalfD), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, baseY + y, lhOz + lhHalfD), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = -lhHalfD; z <= lhHalfD; z++) {
            for (int y = 1; y <= lhWallH; y++) {
                world.setBlockState(new BlockPos(ox - lhHalfW, baseY + y, lhOz + z), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + lhHalfW, baseY + y, lhOz + z), logWall, StructureHelper.SET_FLAGS);
            }
        }
        // Clear interior
        StructureHelper.clearInterior(world,
            new BlockPos(ox - lhHalfW + 1, baseY + 1, lhOz - lhHalfD + 1),
            new BlockPos(ox + lhHalfW - 1, baseY + lhWallH, lhOz + lhHalfD - 1));
        // Plank floor
        StructureHelper.fillBox(world,
            new BlockPos(ox - lhHalfW + 1, baseY, lhOz - lhHalfD + 1),
            new BlockPos(ox + lhHalfW - 1, baseY, lhOz + lhHalfD - 1), planks);
        // Steep roof
        for (int y = 0; y <= lhRoofPeak; y++) {
            int rw = lhHalfW - y;
            if (rw < 0) break;
            for (int z = -lhHalfD - 1; z <= lhHalfD + 1; z++) {
                world.setBlockState(new BlockPos(ox - rw, baseY + lhWallH + y, lhOz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + rw, baseY + lhWallH + y, lhOz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
        }
        // Ridge beam
        for (int z = -lhHalfD - 1; z <= lhHalfD + 1; z++) {
            world.setBlockState(new BlockPos(ox, baseY + lhWallH + lhRoofPeak, lhOz + z), logWall, StructureHelper.SET_FLAGS);
        }
        // Longhouse entrance (south side, facing crossroads)
        for (int dx = -1; dx <= 0; dx++) {
            for (int y = 2; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + dx, baseY + y, lhOz + lhHalfD), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Campfire hearth inside longhouse
        world.setBlockState(new BlockPos(ox, baseY + 1, lhOz), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        // Chief's seat (facing south toward door)
        world.setBlockState(new BlockPos(ox, baseY + 1, lhOz - lhHalfD + 1),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // 2 beds — FACING=WEST: FOOT at lower X, HEAD at higher X
        world.setBlockState(new BlockPos(ox - lhHalfW + 1, baseY + 1, lhOz - 1), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - lhHalfW + 2, baseY + 1, lhOz - 1), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - lhHalfW + 1, baseY + 1, lhOz + 1), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - lhHalfW + 2, baseY + 1, lhOz + 1), palette.bed.getDefaultState()
            .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        // Chests in longhouse
        StructureHelper.placeChest(world, new BlockPos(ox + lhHalfW - 1, baseY + 1, lhOz - 1),
            Direction.WEST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);
        StructureHelper.placeChest(world, new BlockPos(ox + lhHalfW - 1, baseY + 1, lhOz + 1),
            Direction.WEST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);

        // === CORNER WATCHTOWER PLATFORMS ===
        // 4 diagonal positions on the palisade
        int[][] corners = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        double diagDist = fortRadius * 0.707; // cos(45) ~ 0.707
        for (int[] c : corners) {
            int tx = ox + (int)(diagDist * c[0]);
            int tz = oz + (int)(diagDist * c[1]);

            // 4 spruce log stilts (3x3 base)
            for (int y = 0; y <= 4; y++) {
                world.setBlockState(new BlockPos(tx - 1, baseY + y, tz - 1), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(tx + 1, baseY + y, tz - 1), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(tx - 1, baseY + y, tz + 1), logWall, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(tx + 1, baseY + y, tz + 1), logWall, StructureHelper.SET_FLAGS);
            }
            // 3x3 platform at palisade-top height (baseY+5)
            for (int px = -1; px <= 1; px++) {
                for (int pz = -1; pz <= 1; pz++) {
                    world.setBlockState(new BlockPos(tx + px, baseY + 5, tz + pz), planks, StructureHelper.SET_FLAGS);
                }
            }
            // Fence railing around platform edges
            for (int px = -1; px <= 1; px++) {
                world.setBlockState(new BlockPos(tx + px, baseY + 6, tz - 1), fenceBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(tx + px, baseY + 6, tz + 1), fenceBlock, StructureHelper.SET_FLAGS);
            }
            world.setBlockState(new BlockPos(tx - 1, baseY + 6, tz), fenceBlock, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(tx + 1, baseY + 6, tz), fenceBlock, StructureHelper.SET_FLAGS);
            // Lantern on platform
            world.setBlockState(new BlockPos(tx, baseY + 6, tz),
                Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        }

        // === WALL TORCHES on interior palisade ===
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int torchX = ox + (int)((fortRadius - 2) * Math.cos(rad));
            int torchZ = oz + (int)((fortRadius - 2) * Math.sin(rad));

            // Determine which direction the torch should face (inward toward center)
            Direction torchFacing;
            double dx = torchX - ox;
            double dz = torchZ - oz;
            if (Math.abs(dx) > Math.abs(dz)) {
                torchFacing = dx > 0 ? Direction.WEST : Direction.EAST;
            } else {
                torchFacing = dz > 0 ? Direction.NORTH : Direction.SOUTH;
            }
            world.setBlockState(new BlockPos(torchX, baseY + 3, torchZ),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, torchFacing), StructureHelper.SET_FLAGS);
        }

        placeJigsawConnectors(world, center, fortRadius);

        VillageCastles.LOGGER.info("Taiga ring fort generation complete!");
        return new CastleBounds(
            center.add(-fortRadius - 2, 0, -fortRadius - 2),
            center.add(fortRadius + 2, lhWallH + lhRoofPeak + 5, fortRadius + 2)
        );
    }

    // ==========================================================================
    // TAIGA LARGE — Jarl's Tower-House Fortress
    // ==========================================================================

    /**
     * TAIGA LARGE — Jarl's tower-house fortress.
     * Scottish highland tower-house + stave church influence.
     * Narrow, tall main tower (9x7, 18 blocks to wall top), battered stone base,
     * 4 floors (great hall, armory, bedchamber, battlement), multi-tiered steep roof,
     * crow-stepped gables, tight courtyard palisade, furnished interiors.
     */
    private CastleBounds generateTaigaTowerHouse(ServerWorld world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        BlockState logWall = palette.log.getDefaultState();
        BlockState strippedLog = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
        BlockState mossyCobble = palette.getSecondaryWallState();
        BlockState cobble = palette.getPrimaryWallState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.getDefaultState();
        BlockState planks = palette.getPlanksState();
        BlockState slabBlock = palette.woodSlab.getDefaultState();
        BlockState fenceBlock = palette.fence.getDefaultState();
        BlockState roofBlock = palette.getRoofState();
        BlockState ironBars = Blocks.IRON_BARS.getDefaultState();
        BlockState cobbleWall = Blocks.COBBLESTONE_WALL.getDefaultState();
        BlockState cobbleStairs = Blocks.COBBLESTONE_STAIRS.getDefaultState();

        // Tower dimensions
        int tHalfW = 4;  // east-west half (9 blocks wide)
        int tHalfD = 3;  // north-south half (7 blocks deep)
        int wallTop = 18;
        int floorHeight = 4; // each floor is 4 blocks (3 air + 1 floor)

        // Courtyard palisade
        int courtRadius = tHalfW + 6; // 5-6 blocks clearance

        prepareGround(world, center, courtRadius + 2);

        // ============================================
        // MAIN TOWER — Stone brick, 4 floors
        // ============================================

        // --- Battered base: bottom 3 courses use inverted cobblestone stairs stepping outward ---
        for (int course = 0; course < 3; course++) {
            int expand = 2 - course; // course 0 = 2 out, course 1 = 1 out, course 2 = 0 out
            int y = baseY + course;
            // Fill solid base at this level
            StructureHelper.fillBox(world,
                new BlockPos(ox - tHalfW - expand, y, oz - tHalfD - expand),
                new BlockPos(ox + tHalfW + expand, y, oz + tHalfD + expand), cobble);

            // Place inverted stairs on the outer edge for the battered look
            if (expand > 0) {
                // North face stairs — FACING=SOUTH (back faces south = you walk up going south)
                for (int x = -tHalfW - expand; x <= tHalfW + expand; x++) {
                    world.setBlockState(new BlockPos(ox + x, y, oz - tHalfD - expand),
                        cobbleStairs.with(StairsBlock.FACING, Direction.SOUTH).with(StairsBlock.HALF, BlockHalf.TOP),
                        StructureHelper.SET_FLAGS);
                }
                // South face stairs — FACING=NORTH
                for (int x = -tHalfW - expand; x <= tHalfW + expand; x++) {
                    world.setBlockState(new BlockPos(ox + x, y, oz + tHalfD + expand),
                        cobbleStairs.with(StairsBlock.FACING, Direction.NORTH).with(StairsBlock.HALF, BlockHalf.TOP),
                        StructureHelper.SET_FLAGS);
                }
                // West face stairs — FACING=EAST
                for (int z = -tHalfD - expand + 1; z <= tHalfD + expand - 1; z++) {
                    world.setBlockState(new BlockPos(ox - tHalfW - expand, y, oz + z),
                        cobbleStairs.with(StairsBlock.FACING, Direction.EAST).with(StairsBlock.HALF, BlockHalf.TOP),
                        StructureHelper.SET_FLAGS);
                }
                // East face stairs — FACING=WEST
                for (int z = -tHalfD - expand + 1; z <= tHalfD + expand - 1; z++) {
                    world.setBlockState(new BlockPos(ox + tHalfW + expand, y, oz + z),
                        cobbleStairs.with(StairsBlock.FACING, Direction.WEST).with(StairsBlock.HALF, BlockHalf.TOP),
                        StructureHelper.SET_FLAGS);
                }
            }
        }

        // --- Main tower walls: stone brick shell from course 3 to wallTop ---
        for (int y = 3; y <= wallTop; y++) {
            for (int x = -tHalfW; x <= tHalfW; x++) {
                for (int z = -tHalfD; z <= tHalfD; z++) {
                    boolean isWall = x == -tHalfW || x == tHalfW || z == -tHalfD || z == tHalfD;
                    if (isWall) {
                        mutable.set(ox + x, baseY + y, oz + z);
                        world.setBlockState(mutable, stoneBricks, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // --- Clear interior (all 4 floors) ---
        StructureHelper.clearInterior(world,
            new BlockPos(ox - tHalfW + 1, baseY + 1, oz - tHalfD + 1),
            new BlockPos(ox + tHalfW - 1, baseY + wallTop, oz + tHalfD - 1));

        // --- Floor surfaces ---
        // Ground floor (baseY): spruce planks (great hall)
        StructureHelper.fillBox(world,
            new BlockPos(ox - tHalfW + 1, baseY, oz - tHalfD + 1),
            new BlockPos(ox + tHalfW - 1, baseY, oz + tHalfD - 1), planks);
        // 2nd floor (baseY+4): armory
        StructureHelper.fillBox(world,
            new BlockPos(ox - tHalfW + 1, baseY + 4, oz - tHalfD + 1),
            new BlockPos(ox + tHalfW - 1, baseY + 4, oz + tHalfD - 1), planks);
        // 3rd floor (baseY+8): bedchamber
        StructureHelper.fillBox(world,
            new BlockPos(ox - tHalfW + 1, baseY + 8, oz - tHalfD + 1),
            new BlockPos(ox + tHalfW - 1, baseY + 8, oz + tHalfD - 1), planks);
        // 4th floor (baseY+12): battlement lookout
        StructureHelper.fillBox(world,
            new BlockPos(ox - tHalfW + 1, baseY + 12, oz - tHalfD + 1),
            new BlockPos(ox + tHalfW - 1, baseY + 12, oz + tHalfD - 1), planks);

        // --- Corner staircase connecting all floors (NW corner: x=-tHalfW+1, z=-tHalfD+1) ---
        int stairX = ox - tHalfW + 1;
        int stairZ = oz - tHalfD + 1;
        // Stairs spiral up in NW corner: each flight is 4 steps (one per floor height)
        for (int floor = 0; floor < 4; floor++) {
            int floorBase = baseY + (floor * floorHeight); // floor surface
            // Clear stair column through the floor above (if not top floor)
            if (floor < 3) {
                int nextFloor = floorBase + floorHeight;
                world.setBlockState(new BlockPos(stairX, nextFloor, stairZ), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(stairX + 1, nextFloor, stairZ), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
            // 4 stairs going up (along z then x)
            // Step 1: at floorBase+1 — facing NORTH (walking north = up)
            world.setBlockState(new BlockPos(stairX, floorBase + 1, stairZ + 1),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            // Step 2: at floorBase+2
            world.setBlockState(new BlockPos(stairX, floorBase + 2, stairZ),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            // Step 3: at floorBase+3 — landing (slab)
            world.setBlockState(new BlockPos(stairX, floorBase + 3, stairZ), slabBlock, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(stairX + 1, floorBase + 3, stairZ), slabBlock, StructureHelper.SET_FLAGS);
            // Step 4: at floorBase+4 — final step onto next floor
            world.setBlockState(new BlockPos(stairX + 1, floorBase + 4, stairZ),
                palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // --- Arrow slits (IRON_BARS) on lower floors (floors 1-2), shuttered windows (TRAPDOOR) on upper ---
        // Floor 1 (great hall): y = baseY+3 (midway)
        // Floor 2 (armory): y = baseY+7
        for (int floorY : new int[]{baseY + 3, baseY + 7}) {
            // North and south walls
            for (int x = -tHalfW + 2; x <= tHalfW - 2; x += 3) {
                world.setBlockState(new BlockPos(ox + x, floorY, oz - tHalfD), ironBars, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, floorY, oz + tHalfD), ironBars, StructureHelper.SET_FLAGS);
            }
            // East and west walls
            for (int z = -tHalfD + 1; z <= tHalfD - 1; z += 2) {
                world.setBlockState(new BlockPos(ox - tHalfW, floorY, oz + z), ironBars, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + tHalfW, floorY, oz + z), ironBars, StructureHelper.SET_FLAGS);
            }
        }

        // Shuttered windows on upper floors (floors 3-4)
        BlockState trapdoor = palette.trapdoor.getDefaultState();
        for (int floorY : new int[]{baseY + 11, baseY + 15}) {
            // North and south walls
            for (int x = -tHalfW + 2; x <= tHalfW - 2; x += 3) {
                world.setBlockState(new BlockPos(ox + x, floorY, oz - tHalfD),
                    trapdoor.with(TrapdoorBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + x, floorY, oz + tHalfD),
                    trapdoor.with(TrapdoorBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            }
            // East and west walls
            for (int z = -tHalfD + 1; z <= tHalfD - 1; z += 2) {
                world.setBlockState(new BlockPos(ox - tHalfW, floorY, oz + z),
                    trapdoor.with(TrapdoorBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + tHalfW, floorY, oz + z),
                    trapdoor.with(TrapdoorBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            }
        }

        // --- Tower entrance (south side, ground floor) ---
        for (int dx = -1; dx <= 0; dx++) {
            for (int y = 2; y <= 4; y++) {
                world.setBlockState(new BlockPos(ox + dx, baseY + y, oz + tHalfD), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }

        // ============================================
        // MULTI-TIERED STEEP ROOF (Stave Church Influence)
        // ============================================

        int roofBase = baseY + wallTop + 1;

        // --- First tier: steep 1:1 pitch, extends 1 block past walls ---
        int tier1HalfW = tHalfW + 1;
        int tier1Peak = tier1HalfW;
        for (int y = 0; y <= tier1Peak; y++) {
            int rw = tier1HalfW - y;
            if (rw < 0) break;
            for (int z = -tHalfD - 1; z <= tHalfD + 1; z++) {
                world.setBlockState(new BlockPos(ox - rw, roofBase + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + rw, roofBase + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
            // Clear interior under roof
            if (rw > 0) {
                for (int z = -tHalfD; z <= tHalfD; z++) {
                    for (int rx = -rw + 1; rx <= rw - 1; rx++) {
                        mutable.set(ox + rx, roofBase + y, oz + z);
                        world.setBlockState(mutable, Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // --- 1-block gap of spruce fence (visible wall between tiers) ---
        int gapY = roofBase + tier1Peak + 1;
        for (int z = -tHalfD; z <= tHalfD; z++) {
            world.setBlockState(new BlockPos(ox - 1, gapY, oz + z), fenceBlock, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 1, gapY, oz + z), fenceBlock, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox, gapY, oz + z), fenceBlock, StructureHelper.SET_FLAGS);
        }

        // --- Second tier: smaller roof on top ---
        int tier2Base = gapY + 1;
        int tier2HalfW = 2;
        int tier2Peak = tier2HalfW;
        for (int y = 0; y <= tier2Peak; y++) {
            int rw = tier2HalfW - y;
            if (rw < 0) break;
            for (int z = -tHalfD; z <= tHalfD; z++) {
                world.setBlockState(new BlockPos(ox - rw, tier2Base + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(ox + rw, tier2Base + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
        }

        // --- Spruce log spire at peak ---
        int spireBase = tier2Base + tier2Peak;
        for (int y = 0; y <= 3; y++) {
            world.setBlockState(new BlockPos(ox, spireBase + y, oz), logWall, StructureHelper.SET_FLAGS);
        }

        // --- Ridge beams (first tier) ---
        for (int z = -tHalfD - 1; z <= tHalfD + 1; z++) {
            world.setBlockState(new BlockPos(ox, roofBase + tier1Peak, oz + z), logWall, StructureHelper.SET_FLAGS);
        }

        // --- Crow-stepped gables on north and south ends ---
        // Cobblestone blocks stepping up with the roof in 1-block increments
        for (int step = 0; step <= tier1Peak; step++) {
            int gableX = tier1HalfW - step;
            if (gableX < 0) break;
            // North gable
            world.setBlockState(new BlockPos(ox - gableX, roofBase + step, oz - tHalfD - 1), cobble, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + gableX, roofBase + step, oz - tHalfD - 1), cobble, StructureHelper.SET_FLAGS);
            // South gable
            world.setBlockState(new BlockPos(ox - gableX, roofBase + step, oz + tHalfD + 1), cobble, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + gableX, roofBase + step, oz + tHalfD + 1), cobble, StructureHelper.SET_FLAGS);
        }

        // ============================================
        // 4TH FLOOR — BATTLEMENT LOOKOUT (open roof)
        // ============================================
        int battleY = baseY + 13; // furniture level on 4th floor (baseY+12 floor + 1)
        // Cobblestone wall parapet around tower top
        for (int x = -tHalfW; x <= tHalfW; x++) {
            world.setBlockState(new BlockPos(ox + x, baseY + wallTop + 1, oz - tHalfD), cobbleWall, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + x, baseY + wallTop + 1, oz + tHalfD), cobbleWall, StructureHelper.SET_FLAGS);
        }
        for (int z = -tHalfD; z <= tHalfD; z++) {
            world.setBlockState(new BlockPos(ox - tHalfW, baseY + wallTop + 1, oz + z), cobbleWall, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + tHalfW, baseY + wallTop + 1, oz + z), cobbleWall, StructureHelper.SET_FLAGS);
        }
        // Lanterns at battlement corners
        world.setBlockState(new BlockPos(ox - tHalfW, baseY + wallTop + 2, oz - tHalfD),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + tHalfW, baseY + wallTop + 2, oz - tHalfD),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - tHalfW, baseY + wallTop + 2, oz + tHalfD),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + tHalfW, baseY + wallTop + 2, oz + tHalfD),
            Blocks.LANTERN.getDefaultState(), StructureHelper.SET_FLAGS);

        // ============================================
        // FLOOR 1 — GREAT HALL (baseY floor, baseY+1 furniture)
        // ============================================
        int ghFurn = baseY + 1;
        // Central hearth (campfire with cobble border)
        world.setBlockState(new BlockPos(ox, ghFurn, oz), Blocks.CAMPFIRE.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - 1, ghFurn, oz), Blocks.COBBLESTONE_SLAB.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox + 1, ghFurn, oz), Blocks.COBBLESTONE_SLAB.getDefaultState(), StructureHelper.SET_FLAGS);
        // Long table (spruce slabs)
        for (int z = -1; z <= 1; z++) {
            world.setBlockState(new BlockPos(ox + 2, ghFurn, oz + z), slabBlock, StructureHelper.SET_FLAGS);
        }
        // Chief's seat (east side of table, facing west across hall)
        world.setBlockState(new BlockPos(ox + 3, ghFurn, oz),
            palette.woodStairs.getDefaultState().with(StairsBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        // Tapestry (banner on north wall) — use a wall torch as placeholder since banners are complex
        world.setBlockState(new BlockPos(ox, ghFurn + 1, oz - tHalfD + 1),
            Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Chests flanking entrance
        StructureHelper.placeChest(world, new BlockPos(ox + tHalfW - 1, ghFurn, oz + tHalfD - 1),
            Direction.WEST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);
        StructureHelper.placeChest(world, new BlockPos(ox - tHalfW + 1, ghFurn, oz + tHalfD - 1),
            Direction.EAST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);

        // ============================================
        // FLOOR 2 — ARMORY (baseY+4 floor, baseY+5 furniture)
        // ============================================
        int armFurn = baseY + 5;
        // Grindstone
        world.setBlockState(new BlockPos(ox + tHalfW - 1, armFurn, oz - tHalfD + 1),
            Blocks.GRINDSTONE.getDefaultState(), StructureHelper.SET_FLAGS);
        // Anvil
        world.setBlockState(new BlockPos(ox + tHalfW - 1, armFurn, oz),
            Blocks.ANVIL.getDefaultState(), StructureHelper.SET_FLAGS);
        // Smithing table
        world.setBlockState(new BlockPos(ox + tHalfW - 1, armFurn, oz + tHalfD - 1),
            Blocks.SMITHING_TABLE.getDefaultState(), StructureHelper.SET_FLAGS);
        // Armor stands (fence + banner approximation)
        world.setBlockState(new BlockPos(ox - tHalfW + 1, armFurn, oz), fenceBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - tHalfW + 1, armFurn + 1, oz), fenceBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - tHalfW + 1, armFurn, oz + 2), fenceBlock, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - tHalfW + 1, armFurn + 1, oz + 2), fenceBlock, StructureHelper.SET_FLAGS);
        // Armory chest
        StructureHelper.placeChest(world, new BlockPos(ox, armFurn, oz + tHalfD - 1),
            Direction.NORTH, LootTables.VILLAGE_WEAPONSMITH_CHEST);

        // ============================================
        // FLOOR 3 — BEDCHAMBER (baseY+8 floor, baseY+9 furniture)
        // ============================================
        int bedFurn = baseY + 9;
        // 3 beds along east wall — FACING=WEST: FOOT at higher X, HEAD at lower X
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlockState(new BlockPos(ox + tHalfW - 1, bedFurn, oz + dz), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.FOOT).with(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + tHalfW - 2, bedFurn, oz + dz), palette.bed.getDefaultState()
                .with(BedBlock.PART, BedPart.HEAD).with(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }
        // Bookshelf
        world.setBlockState(new BlockPos(ox - tHalfW + 1, bedFurn, oz - tHalfD + 1), Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox - tHalfW + 1, bedFurn + 1, oz - tHalfD + 1), Blocks.BOOKSHELF.getDefaultState(), StructureHelper.SET_FLAGS);
        // Personal effects chest
        StructureHelper.placeChest(world, new BlockPos(ox - tHalfW + 1, bedFurn, oz + tHalfD - 1),
            Direction.EAST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST);
        // Carpet
        world.setBlockState(new BlockPos(ox, bedFurn, oz), palette.carpet.getDefaultState(), StructureHelper.SET_FLAGS);

        // ============================================
        // COURTYARD PALISADE — tight around tower
        // ============================================
        // Mossy cobblestone base (2 blocks) + spruce log palisade (3 blocks) = 5 total
        for (int x = -courtRadius; x <= courtRadius; x++) {
            for (int z = -courtRadius; z <= courtRadius; z++) {
                // Rectangular palisade
                boolean isWall = (x == -courtRadius || x == courtRadius) && (z >= -courtRadius && z <= courtRadius);
                isWall = isWall || ((z == -courtRadius || z == courtRadius) && (x >= -courtRadius && x <= courtRadius));
                if (!isWall) continue;

                // Skip south gate (3-wide opening centered)
                if (z == courtRadius && Math.abs(x) <= 1) continue;

                int wx = ox + x;
                int wz = oz + z;
                // Mossy cobblestone base
                world.setBlockState(new BlockPos(wx, baseY, wz), mossyCobble, StructureHelper.SET_FLAGS);
                world.setBlockState(new BlockPos(wx, baseY + 1, wz), mossyCobble, StructureHelper.SET_FLAGS);
                // Spruce log palisade
                for (int y = 2; y <= 4; y++) {
                    world.setBlockState(new BlockPos(wx, baseY + y, wz), logWall, StructureHelper.SET_FLAGS);
                }
            }
        }

        // === SOUTH GATE — timber-framed tunnel ===
        // Gate posts
        for (int y = 0; y <= 5; y++) {
            world.setBlockState(new BlockPos(ox - 2, baseY + y, oz + courtRadius), logWall, StructureHelper.SET_FLAGS);
            world.setBlockState(new BlockPos(ox + 2, baseY + y, oz + courtRadius), logWall, StructureHelper.SET_FLAGS);
        }
        // Lintel
        for (int dx = -1; dx <= 1; dx++) {
            world.setBlockState(new BlockPos(ox + dx, baseY + 4, oz + courtRadius), logWall, StructureHelper.SET_FLAGS);
        }
        // Clear gate passage
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlockState(new BlockPos(ox + dx, baseY + y, oz + courtRadius), Blocks.AIR.getDefaultState(), StructureHelper.SET_FLAGS);
            }
        }
        // Fence gates
        for (int dx = -1; dx <= 1; dx++) {
            world.setBlockState(new BlockPos(ox + dx, baseY + 1, oz + courtRadius),
                palette.fenceGate.getDefaultState().with(FenceGateBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // === BELL in courtyard (south of tower) ===
        world.setBlockState(new BlockPos(ox, baseY + 1, oz + tHalfD + 2), logWall, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(ox, baseY, oz + tHalfD + 2), Blocks.BELL.getDefaultState(), StructureHelper.SET_FLAGS);

        // === COURTYARD TORCHES ===
        // Wall torches on interior of palisade walls
        // North wall interior (facing SOUTH)
        for (int x = -courtRadius + 3; x <= courtRadius - 3; x += 4) {
            world.setBlockState(new BlockPos(ox + x, baseY + 3, oz - courtRadius + 1),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }
        // South wall interior (facing NORTH)
        for (int x = -courtRadius + 3; x <= courtRadius - 3; x += 4) {
            world.setBlockState(new BlockPos(ox + x, baseY + 3, oz + courtRadius - 1),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }
        // East wall interior (facing WEST)
        for (int z = -courtRadius + 3; z <= courtRadius - 3; z += 4) {
            world.setBlockState(new BlockPos(ox + courtRadius - 1, baseY + 3, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }
        // West wall interior (facing EAST)
        for (int z = -courtRadius + 3; z <= courtRadius - 3; z += 4) {
            world.setBlockState(new BlockPos(ox - courtRadius + 1, baseY + 3, oz + z),
                Blocks.WALL_TORCH.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        }

        int boundsRadius = courtRadius + 2;
        placeJigsawConnectors(world, center, boundsRadius);

        VillageCastles.LOGGER.info("Taiga tower-house fortress generation complete!");
        return new CastleBounds(
            center.add(-boundsRadius, -1, -boundsRadius),
            center.add(boundsRadius, spireBase + 3 + 2, boundsRadius)
        );
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
