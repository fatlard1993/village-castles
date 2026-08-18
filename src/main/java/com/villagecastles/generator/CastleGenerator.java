package com.villagecastles.generator;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.build.Facade;
import com.villagecastles.generator.build.Furnish;
import com.villagecastles.generator.build.Openings;
import com.villagecastles.generator.build.Roofs;
import com.villagecastles.util.StructureHelper;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.core.FrontAndTop;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

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

    /** Final-state string used when a jigsaw block transitions to a path surface. */
    private static final String JIGSAW_FINAL_DIRT_PATH = "minecraft:dirt_path";
    /** Final-state string used when a jigsaw block transitions to empty space. */
    private static final String JIGSAW_FINAL_AIR = "minecraft:air";

    public enum CastleSize {
        // Plan budget, in blocks across. Vanilla's largest single hand-built pieces are the
        // woodland mansion's at 21 and the ancient city's at 41; a village house is 9 to 17.
        // The previous 30/50/70 put every medium and large castle beyond anything vanilla
        // places in one piece, which is what made them so hard to site next to a village.
        SMALL(20, 0),   // a large house: a watch post, a hall, a homestead
        MEDIUM(32, 1),  // a walled fort
        LARGE(44, 2);   // a castle complex; the moat and outworks carry it to about 50

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
     *   SMALL:  tower keep with perimeter fence (fortified home)
     *   MEDIUM: standard fort (keep, walls, corner towers, gatehouse)
     *   LARGE:  grand castle (medium plus wall towers, back gate, stables, barracks)
     *
     * @param world The server world
     * @param center The center position of the castle courtyard
     * @return CastleBounds containing the generated structure's bounds
     */
    public CastleBounds generate(ServerLevel world, BlockPos center) {
        VillageCastles.LOGGER.debug("Generating {} {} castle at {}",
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
     * SMALL: Biome-specific fortified structure.
     * Each biome gets a distinct small fortification with its own architectural language.
     */
    private CastleBounds generateSmall(ServerLevel world, BlockPos center, int radius,
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
     * PLAINS SMALL: the reeve's manor.
     *
     * <p>The village leader's house: stone below, timber above, a steep gable, a working chimney.
     * Built to the vanilla grammar read off {@code plains_big_house_1} rather than as a hollow box
     * with holes punched in it, so it reads as the largest house in the village instead of as a
     * generated shape. Cobble plinth, stone ground storey with log posts, a log band at the storey
     * line, timber upper storey, an oversailing gable with a stair fascia and filled gable ends.
     *
     * <p>Ground floor: hall with hearth, dining table, and the reeve's workbench.
     * Upper floor: two bedchambers reached by a stair that arrives on the landing.
     */
    private CastleBounds generatePlainsManor(ServerLevel world, BlockPos center) {
        int halfWidth = 7;   // 15 blocks east-west
        int halfDepth = 5;   // 11 blocks north-south
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();

        BlockPos min = new BlockPos(ox - halfWidth, baseY, oz - halfDepth);
        BlockPos max = new BlockPos(ox + halfWidth, baseY, oz + halfDepth);

        BlockState stone = palette.getPrimaryWallState();
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState planks = palette.getPlanksState();
        BlockState log = palette.log.defaultBlockState();
        BlockState woodStairs = palette.woodStairs.defaultBlockState();
        BlockState woodSlab = palette.woodSlab.defaultBlockState();
        BlockState fence = palette.fence.defaultBlockState();

        // === SHELL ===
        // Plinth, stone storey, band, timber storey. Facade closes every ring, so the four walls
        // meet at the corners without the caller resolving them.
        int groundH = 4;
        int upperH = 3;
        int wallTop = Facade.walls(world, min, max, cobble, 1,
            java.util.List.of(
                new Facade.Storey(groundH, stone, log, log),
                new Facade.Storey(upperH, planks, log, null)),
            4);

        int groundFloorY = baseY;                 // floor course of the hall
        int upperFloorY = baseY + 1 + groundH;    // the band course doubles as the upper floor

        Facade.room(world, min, max, groundFloorY, upperFloorY - 1, planks);
        Facade.room(world, min, max, upperFloorY, wallTop, planks);

        // === ROOF ===
        // Ridge east-west, oversailing a block on every side. Walked from both eaves inward, so
        // there is no course at which a row can be missed.
        int eaveY = wallTop + 1;
        Roofs.eaveTrim(world, min, max, eaveY, 1, woodStairs);
        int ridgeY = Roofs.gable(world, min, max, eaveY, Roofs.Ridge.X, 1, planks, log, planks);

        // === CHIMNEY ===
        // A real flue: it starts at the hearth and leaves the roof, rather than standing beside it.
        int chimneyX = ox + halfWidth - 2;
        for (int y = baseY; y <= ridgeY + 2; y++) {
            world.setBlock(new BlockPos(chimneyX, y, oz - halfDepth), cobble, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(chimneyX + 1, y, oz - halfDepth), cobble, StructureHelper.SET_FLAGS);
        }
        world.setBlock(new BlockPos(chimneyX, ridgeY + 3, oz - halfDepth),
            Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === OPENINGS ===
        // Front door, south wall, centred, with jambs and a lintel.
        Openings.door(world, new BlockPos(ox, baseY + 1, oz + halfDepth), Direction.NORTH,
            palette.door.defaultBlockState(), log, cobble, false);

        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        // Long walls, both storeys. Sill and lintel in log so each window reads as framed.
        for (int wx : new int[]{ox - 4, ox + 3}) {
            Openings.window(world, new BlockPos(wx, baseY + 2, oz - halfDepth), Direction.EAST, 2, 2, pane, log);
            Openings.window(world, new BlockPos(wx, baseY + 2, oz + halfDepth), Direction.EAST, 2, 2, pane, log);
            Openings.window(world, new BlockPos(wx, upperFloorY + 1, oz - halfDepth), Direction.EAST, 2, 2, pane, log);
            Openings.window(world, new BlockPos(wx, upperFloorY + 1, oz + halfDepth), Direction.EAST, 2, 2, pane, log);
        }
        // Gable ends.
        Openings.window(world, new BlockPos(ox - halfWidth, baseY + 2, oz - 1), Direction.SOUTH, 2, 2, pane, log);
        Openings.window(world, new BlockPos(ox + halfWidth, baseY + 2, oz - 1), Direction.SOUTH, 2, 2, pane, log);

        // === PORCH ===
        // Two posts and a slab canopy over the door.
        for (int px : new int[]{ox - 2, ox + 2}) {
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                world.setBlock(new BlockPos(px, y, oz + halfDepth + 1), fence, StructureHelper.SET_FLAGS);
            }
        }
        for (int px = ox - 2; px <= ox + 2; px++) {
            world.setBlock(new BlockPos(px, baseY + 4, oz + halfDepth + 1), woodSlab, StructureHelper.SET_FLAGS);
        }
        world.setBlock(new BlockPos(ox - 2, baseY + 4, oz + halfDepth + 1),
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);

        // === STAIR TO THE UPPER FLOOR ===
        // Opened first, hung second. The previous revision placed its top step and then cleared the
        // landing on top of it, deleting the step and stranding the upper floor.
        int stairX = ox - halfWidth + 2;
        for (int sx = -1; sx <= 0; sx++) {
            for (int sz = 0; sz <= 1; sz++) {
                world.setBlock(new BlockPos(stairX + sx, upperFloorY, oz - halfDepth + 2 + sz),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        // Five risers from the hall floor to the landing, running north, each with headroom.
        for (int i = 0; i < groundH + 1; i++) {
            BlockPos step = new BlockPos(stairX, baseY + 1 + i, oz + halfDepth - 2 - i);
            world.setBlock(step, woodStairs.setValue(StairBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            for (int dy = 1; dy <= 2; dy++) {
                world.setBlock(step.above(dy), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // === GROUND FLOOR ===
        Furnish.hearth(world, new BlockPos(chimneyX, groundFloorY, oz - halfDepth + 1), groundFloorY,
            cobble, Blocks.CAMPFIRE.defaultBlockState());

        Furnish.table(world, new BlockPos(ox - 1, groundFloorY, oz), Direction.EAST, 3, fence, woodSlab);
        Furnish.chair(world, new BlockPos(ox - 1, groundFloorY, oz - 1), Direction.SOUTH, woodStairs);
        Furnish.chair(world, new BlockPos(ox + 1, groundFloorY, oz - 1), Direction.SOUTH, woodStairs);
        Furnish.chair(world, new BlockPos(ox - 1, groundFloorY, oz + 1), Direction.NORTH, woodStairs);
        Furnish.chair(world, new BlockPos(ox + 1, groundFloorY, oz + 1), Direction.NORTH, woodStairs);

        Furnish.onFloor(world, new BlockPos(ox - halfWidth + 1, groundFloorY, oz - halfDepth + 1),
            Blocks.CRAFTING_TABLE.defaultBlockState());
        Furnish.onFloor(world, new BlockPos(ox - halfWidth + 2, groundFloorY, oz - halfDepth + 1),
            Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 1, groundFloorY + 1, oz - halfDepth + 2),
            Direction.EAST, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
        Furnish.onFloor(world, new BlockPos(ox + halfWidth - 1, groundFloorY, oz + halfDepth - 1),
            Blocks.BARREL.defaultBlockState());

        Furnish.hangingLantern(world, new BlockPos(ox, upperFloorY - 1, oz + 2), palette.getLightState());
        Furnish.hangingLantern(world, new BlockPos(ox, upperFloorY - 1, oz - 3), palette.getLightState());

        // === UPPER FLOOR ===
        Furnish.bed(world, new BlockPos(ox + 2, upperFloorY, oz - halfDepth + 1), Direction.SOUTH, palette.bed.defaultBlockState());
        Furnish.bed(world, new BlockPos(ox + 5, upperFloorY, oz - halfDepth + 1), Direction.SOUTH, palette.bed.defaultBlockState());
        Furnish.bed(world, new BlockPos(ox + 2, upperFloorY, oz + halfDepth - 2), Direction.NORTH, palette.bed.defaultBlockState());

        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - 1, upperFloorY + 1, oz),
            Direction.WEST, BuiltInLootTables.VILLAGE_PLAINS_HOUSE);
        Furnish.onFloor(world, new BlockPos(ox + 1, upperFloorY, oz), Blocks.BOOKSHELF.defaultBlockState());
        Furnish.onFloor(world, new BlockPos(ox, upperFloorY, oz), Blocks.LECTERN.defaultBlockState());
        Furnish.onFloor(world, new BlockPos(ox - 3, upperFloorY, oz - halfDepth + 1),
            Blocks.FLOWER_POT.defaultBlockState());
        Furnish.hangingLantern(world, new BlockPos(ox + 3, wallTop, oz), palette.getLightState());

        VillageCastles.LOGGER.debug("Plains manor generation complete!");
        return new CastleBounds(
            new BlockPos(ox - halfWidth - 2, baseY, oz - halfDepth - 2),
            new BlockPos(ox + halfWidth + 2, ridgeY + 4, oz + halfDepth + 2)
        );
    }

    /**
     * DESERT SMALL: Walled villa.
     * Rectangular, squat, thick sandstone walls. Flat walkable roof with parapet.
     * Open-air courtyard in the center with a water feature. Rooms around the
     * perimeter are shaded, cool, and furnished. From outside, a solid sandstone block
     * with a door. From inside, comfortable and lived-in. A desert lord's home.
     */
    private CastleBounds generateDesertOutpost(ServerLevel world, BlockPos center) {
        int halfWidth = 8;   // east-west
        int halfDepth = 7;   // north-south
        int wallHeight = 5;
        int wallThickness = 2; // thick walls for thermal mass
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // === OUTER WALLS: thick sandstone, the defining visual ===
        StructureHelper.fillBox(world,
            center.offset(-halfWidth, 0, -halfDepth),
            center.offset(halfWidth, wallHeight, halfDepth),
            palette.getPrimaryWallState());

        // Hollow everything inside the thick walls
        StructureHelper.clearInterior(world,
            center.offset(-halfWidth + wallThickness, 1, -halfDepth + wallThickness),
            center.offset(halfWidth - wallThickness, wallHeight, halfDepth - wallThickness));

        // Smooth sandstone floor throughout interior
        StructureHelper.fillFloor(world,
            center.offset(-halfWidth + wallThickness, 0, -halfDepth + wallThickness),
            center.offset(halfWidth - wallThickness, 0, halfDepth - wallThickness),
            baseY + 1, palette.getFloorState());

        // === FLAT WALKABLE ROOF with parapet ===
        StructureHelper.fillFloor(world,
            center.offset(-halfWidth, 0, -halfDepth),
            center.offset(halfWidth, 0, halfDepth),
            baseY + wallHeight, palette.getFloorState());
        // Parapet (1-block wall around roof edge with gaps for crenellations)
        StructureHelper.addCrenellations(world,
            center.offset(-halfWidth, 0, -halfDepth),
            center.offset(halfWidth, 0, halfDepth),
            baseY + wallHeight, palette.getPrimaryWallState());

        // === OPEN-AIR COURTYARD: center, 4x4 ===
        int courtHW = 2;
        // Remove the roof over the courtyard (let sky in)
        for (int x = -courtHW; x <= courtHW; x++) {
            for (int z = -courtHW; z <= courtHW; z++) {
                world.setBlock(new BlockPos(ox + x, baseY + wallHeight, oz + z),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        // Water feature in courtyard center
        world.setBlock(center.offset(0, 0, 0), palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 1, 0), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Sandstone rim around water. Each stair turns to face the basin: leaving them at their
        // default state pointed all four north, so three sides of the fountain had their backs
        // to the water.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            world.setBlock(center.relative(side).above(),
                palette.stoneStairs.defaultBlockState().setValue(StairBlock.FACING, side),
                StructureHelper.SET_FLAGS);
        }

        // === INTERIOR PARTITION: divides rooms ===
        // North-south wall dividing east rooms from west
        int dividerX = ox - 2;
        for (int z = -halfDepth + wallThickness; z <= -courtHW - 1; z++) {
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlock(new BlockPos(dividerX, baseY + y, oz + z),
                    palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        for (int z = courtHW + 1; z <= halfDepth - wallThickness; z++) {
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlock(new BlockPos(dividerX, baseY + y, oz + z),
                    palette.getPrimaryWallState(), StructureHelper.SET_FLAGS);
            }
        }
        // Doorways through divider
        for (int y = 1; y <= 3; y++) {
            world.setBlock(new BlockPos(dividerX, baseY + y, oz - halfDepth + wallThickness + 2),
                Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(dividerX, baseY + y, oz + halfDepth - wallThickness - 2),
                Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // === FRONT ENTRANCE: south wall, recessed doorway ===
        for (int y = 1; y <= 3; y++) {
            for (int t = 0; t < wallThickness; t++) {
                world.setBlock(new BlockPos(ox, baseY + y, oz + halfDepth - t),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        // Hang a door in the recess rather than leaving the wall simply open.
        Openings.door(world, new BlockPos(ox, baseY + 1, oz + halfDepth - 1), Direction.NORTH,
            palette.door.defaultBlockState(), null, palette.getFloorState(), false);
        world.setBlock(new BlockPos(ox, baseY, oz + halfDepth), palette.getFloorState(), StructureHelper.SET_FLAGS);

        // === NARROW WINDOW SLITS ===
        // East and west walls, staggered
        for (int z : new int[]{oz - 3, oz + 3}) {
            world.setBlock(new BlockPos(ox - halfWidth, baseY + 3, z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, baseY + 3, z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        // North wall
        world.setBlock(new BlockPos(ox - 3, baseY + 3, oz - halfDepth), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 3, baseY + 3, oz - halfDepth), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === BEDCHAMBER: NW room (west of divider, north of courtyard) ===
        int roomFloor = baseY + 2; // furniture sits on the smooth sandstone floor at baseY+1
        // Master bed against north wall
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz - halfDepth + wallThickness),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz - halfDepth + wallThickness + 1),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Second bed
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 3, roomFloor, oz - halfDepth + wallThickness),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 3, roomFloor, oz - halfDepth + wallThickness + 1),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Chest
        StructureHelper.placeChest(world, new BlockPos(dividerX - 1, roomFloor, oz - halfDepth + wallThickness),
            Direction.SOUTH, BuiltInLootTables.VILLAGE_DESERT_HOUSE);
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 2, roomFloor, oz - courtHW - 1),
            palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === STUDY: NE room (east of divider, north of courtyard) ===
        world.setBlock(new BlockPos(dividerX + 2, roomFloor, oz - halfDepth + wallThickness),
            Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(dividerX + 3, roomFloor, oz - halfDepth + wallThickness),
            Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(dividerX + 4, roomFloor, oz - halfDepth + wallThickness),
            Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(dividerX + 2, roomFloor, oz - halfDepth + wallThickness + 1),
            Blocks.LECTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, new BlockPos(ox + halfWidth - wallThickness - 1, roomFloor, oz - halfDepth + wallThickness),
            Direction.SOUTH, BuiltInLootTables.VILLAGE_DESERT_HOUSE);
        world.setBlock(new BlockPos(ox + halfWidth - wallThickness - 1, roomFloor, oz - courtHW - 1),
            Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === KITCHEN: SW room (west of divider, south of courtyard) ===
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz + halfDepth - wallThickness - 1),
            Blocks.SMOKER.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 2, roomFloor, oz + halfDepth - wallThickness - 1),
            Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 1, roomFloor, oz + halfDepth - wallThickness),
            Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 2, roomFloor, oz + halfDepth - wallThickness),
            Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === AUDIENCE ROOM: SE room (east of divider, south of courtyard) ===
        // Throne/seat facing north (toward the courtyard)
        world.setBlock(new BlockPos(ox + halfWidth - wallThickness - 2, roomFloor, oz + halfDepth - wallThickness),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        // Terracotta accent behind throne
        world.setBlock(new BlockPos(ox + halfWidth - wallThickness - 2, roomFloor + 1, oz + halfDepth - wallThickness),
            Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);
        // Guest seating
        world.setBlock(new BlockPos(dividerX + 2, roomFloor, oz + courtHW + 2),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + halfWidth - wallThickness - 1, roomFloor, oz + courtHW + 1),
            Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === LIGHTING ===
        // Lanterns in each room (hanging from ceiling)
        int ceilingY = baseY + wallHeight;
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 2, ceilingY - 1, oz - halfDepth + wallThickness + 2),
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(dividerX + 3, ceilingY - 1, oz - halfDepth + wallThickness + 2),
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + wallThickness + 2, ceilingY - 1, oz + halfDepth - wallThickness - 2),
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(dividerX + 3, ceilingY - 1, oz + halfDepth - wallThickness - 2),
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);

        // === ROOF ACCESS: ladder in SE corner ===
        for (int y = 2; y <= wallHeight; y++) {
            world.setBlock(new BlockPos(ox + halfWidth - wallThickness - 1, baseY + y, oz + halfDepth - wallThickness),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH),
                StructureHelper.SET_FLAGS);
        }

        // === BELL: on the roof ===
        world.setBlock(new BlockPos(ox, baseY + wallHeight + 1, oz + halfDepth - 1),
            palette.fence.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, baseY + wallHeight + 2, oz + halfDepth - 1),
            Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        if (size != CastleSize.SMALL) {
            placeJigsawConnectors(world, center, Math.max(halfWidth, halfDepth));
        }

        VillageCastles.LOGGER.debug("Desert villa generation complete!");
        return new CastleBounds(
            center.offset(-halfWidth - 2, 0, -halfDepth - 2),
            center.offset(halfWidth + 2, wallHeight + 5, halfDepth + 2)
        );
    }

    /**
     * SAVANNA SMALL: Fortified homestead.
     * Cluster of round mud-brick huts with hay thatch roofs behind an acacia
     * palisade with dead-bush thorn barriers. Central fire pit, animal pen,
     * outdoor work area. Compact, lived-in family compound.
     */
    private CastleBounds generateSavannaEnclosure(ServerLevel world, BlockPos center) {
        int yardRadius = 9;
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockState mudBrick = Blocks.MUD_BRICKS.defaultBlockState();
        BlockState packedMud = Blocks.PACKED_MUD.defaultBlockState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState fence = palette.fence.defaultBlockState();
        BlockState deadBush = Blocks.DEAD_BUSH.defaultBlockState();

        // Coarse dirt ground throughout compound: placed at baseY-1 to replace
        // the surface block rather than adding a raised layer on top of terrain
        for (int x = -yardRadius; x <= yardRadius; x++) {
            for (int z = -yardRadius; z <= yardRadius; z++) {
                if (x * x + z * z <= yardRadius * yardRadius) {
                    world.setBlock(new BlockPos(ox + x, baseY - 1, oz + z), coarseDirt, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Acacia palisade fence with dead bush thorn effect: skipped for SMALL (standalone worldgen structure)
        if (size != CastleSize.SMALL) {
            for (int angle = 0; angle < 360; angle += 2) {
                double rad = Math.toRadians(angle);
                int fx = ox + (int) Math.round(yardRadius * Math.cos(rad));
                int fz = oz + (int) Math.round(yardRadius * Math.sin(rad));
                world.setBlock(new BlockPos(fx, baseY, fz), fence, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(fx, baseY + 1, fz), fence, StructureHelper.SET_FLAGS);
            }
            // Dead bush thorn barriers outside palisade (every ~30 degrees)
            for (int angle = 0; angle < 360; angle += 30) {
                double rad = Math.toRadians(angle);
                int bx = ox + (int) Math.round((yardRadius + 1) * Math.cos(rad));
                int bz = oz + (int) Math.round((yardRadius + 1) * Math.sin(rad));
                // Dead bush needs a valid support block beneath it: place at baseY-1 to replace terrain
                world.setBlock(new BlockPos(bx, baseY - 1, bz), coarseDirt, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(bx, baseY, bz), deadBush, StructureHelper.SET_FLAGS);
            }
        }

        // Main dwelling hut (north-west, radius 4, wall height 4)
        buildRoundHut(world, center.offset(-4, 0, -5), 4, 4);
        // Furnish dwelling: interior floor is packed mud with carpet at y+1
        world.setBlock(center.offset(-4, 1, -5), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Bed 1
        world.setBlock(center.offset(-6, 1, -7), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-6, 1, -6), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Bed 2
        world.setBlock(center.offset(-2, 1, -7), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-2, 1, -6), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Decorated pot inside dwelling
        world.setBlock(center.offset(-5, 1, -3), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Storage hut (east, radius 4, wall height 3): radius 4 gives usable 5x5 interior
        buildRoundHut(world, center.offset(5, 0, -2), 4, 3);
        StructureHelper.placeChest(world, center.offset(5, 1, -2), Direction.SOUTH, BuiltInLootTables.VILLAGE_SAVANNA_HOUSE);
        world.setBlock(center.offset(4, 1, -2), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(6, 1, -2), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Central fire pit with acacia log seating
        world.setBlock(center, Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        for (int[] off : new int[][]{{-2, -1}, {2, -1}, {-1, 2}, {1, 2}}) {
            world.setBlock(center.offset(off[0], 0, off[1]),
                palette.log.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        // Terracotta accent blocks around fire pit
        world.setBlock(center.offset(-1, 0, -1), Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(1, 0, -1), Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-1, 0, 1), Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(1, 0, 1), Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(), StructureHelper.SET_FLAGS);

        // Animal pen (south-west, acacia fence)
        for (int x = -8; x <= -3; x++) {
            world.setBlock(new BlockPos(ox + x, baseY, oz + 4), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, baseY, oz + 9), fence, StructureHelper.SET_FLAGS);
        }
        for (int z = 4; z <= 9; z++) {
            world.setBlock(new BlockPos(ox - 8, baseY, oz + z), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - 3, baseY, oz + z), fence, StructureHelper.SET_FLAGS);
        }
        // Fence gate entrance to pen (facing south, at center of south wall)
        world.setBlock(new BlockPos(ox - 5, baseY, oz + 9),
            palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-5, 0, 7), Blocks.CAULDRON.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-6, 0, 6), Blocks.HAY_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Outdoor work area (south-east): loom, smoker, composters
        world.setBlock(center.offset(6, 0, 5), Blocks.LOOM.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(7, 0, 5), Blocks.SMOKER.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(8, 0, 6), Blocks.COMPOSTER.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(6, 0, 7), Blocks.COMPOSTER.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Drying rack (fence posts with carpet on top)
        for (int x = 3; x <= 7; x++) {
            world.setBlock(new BlockPos(ox + x, baseY, oz + 3), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, baseY + 1, oz + 3), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, baseY + 2, oz + 3), palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Decorated pots scattered around
        world.setBlock(center.offset(-1, 0, -8), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(3, 0, -4), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(8, 0, 0), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Hay bales (food/animal feed storage)
        world.setBlock(center.offset(-7, 0, 3), Blocks.HAY_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-7, 1, 3), Blocks.HAY_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Crafting table near dwelling
        world.setBlock(center.offset(-1, 0, -3), Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Entrance gate, torch posts, bell, and jigsaw: skipped for SMALL (standalone worldgen structure)
        if (size != CastleSize.SMALL) {
            // Entrance gate south, with 2+ blocks clearance outside
            world.setBlock(new BlockPos(ox, baseY, oz + yardRadius),
                palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH),
                StructureHelper.SET_FLAGS);

            // Torch posts: fence base so torch has a solid support block
            int[][] torchPosts = {{-2, yardRadius}, {2, yardRadius}, {0, 3}, {-9, 0}, {9, 0}};
            for (int[] tp : torchPosts) {
                world.setBlock(new BlockPos(ox + tp[0], baseY, oz + tp[1]), fence, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + tp[0], baseY + 1, oz + tp[1]), fence, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + tp[0], baseY + 2, oz + tp[1]), Blocks.TORCH.defaultBlockState(), StructureHelper.SET_FLAGS);
            }

            // Bell on a fence post
            world.setBlock(center.offset(0, 0, 5), fence, StructureHelper.SET_FLAGS);
            world.setBlock(center.offset(0, 1, 5), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

            placeJigsawConnectors(world, center, yardRadius);
        }

        VillageCastles.LOGGER.debug("Savanna homestead generation complete!");
        return new CastleBounds(
            center.offset(-yardRadius - 2, 0, -yardRadius - 2),
            center.offset(yardRadius + 2, 12, yardRadius + 2)
        );
    }

    /**
     * A round thatched hut: mud brick wall, packed mud floor, conical thatch, one hung door.
     *
     * <p>The thatch is a stack of rings, not a stack of discs. Filling the whole disc at every
     * course made the roof a solid cone: about 170 hay blocks of dead mass over a three-block-tall
     * room, and hay ended up 26-48% of every savanna castle by block count. Consecutive rings whose
     * radius drops by one tile the plan exactly, so a shell is as weathertight as the solid cone
     * was and leaves the loft as space.
     *
     * <p>The toron pegs are placed outward from a wall block that exists, rather than sampled off a
     * second circle of radius+1. Sampling made them land adjacent only by luck: once the ring maths
     * stopped truncating toward the origin, thirteen of them in savanna/castle_small were floating
     * free of the wall they are supposed to be driven into.
     */
    private void buildRoundHut(ServerLevel world, BlockPos center, int radius, int wallHeight) {
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockState mudBrick = Blocks.MUD_BRICKS.defaultBlockState();
        BlockState fence = palette.fence.defaultBlockState();
        BlockState hayBlock = Blocks.HAY_BLOCK.defaultBlockState();
        BlockState packedMud = Blocks.PACKED_MUD.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        java.util.List<int[]> wallRing = StructureHelper.ringOffsets(radius);
        java.util.List<int[]> inner = StructureHelper.discOffsets(radius - 1);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Floor, then the wall, then the space between them.
        for (int[] o : inner) {
            world.setBlock(cursor.set(ox + o[0], baseY, oz + o[1]), packedMud, StructureHelper.SET_FLAGS);
        }
        for (int y = 0; y < wallHeight; y++) {
            for (int[] o : wallRing) {
                world.setBlock(cursor.set(ox + o[0], baseY + y, oz + o[1]), mudBrick, StructureHelper.SET_FLAGS);
            }
        }
        for (int[] o : inner) {
            for (int y = 1; y <= wallHeight + radius + 1; y++) {
                world.setBlock(cursor.set(ox + o[0], baseY + y, oz + o[1]), air, StructureHelper.SET_FLAGS);
            }
        }

        // Conical thatch: a two-thick band per course, so successive courses overlap and no cell is
        // left touching nothing. Apex closed with a single block.
        for (int y = 0; y <= radius + 1; y++) {
            int r = radius + 1 - y;
            int ty = baseY + wallHeight + y;
            if (r <= 1) {
                for (int[] o : StructureHelper.discOffsets(Math.max(r, 0))) {
                    world.setBlock(cursor.set(ox + o[0], ty, oz + o[1]), hayBlock, StructureHelper.SET_FLAGS);
                }
                break;
            }
            for (int[] o : StructureHelper.bandOffsets(r, 2)) {
                world.setBlock(cursor.set(ox + o[0], ty, oz + o[1]), hayBlock, StructureHelper.SET_FLAGS);
            }
        }

        // Toron: pegs driven outward from wall blocks that exist, every fourth cell, alternate rows.
        // The step is along the dominant axis only. Stepping diagonally (signum on both axes at
        // once) lands the peg corner-to-corner with its wall block, which is not attachment.
        for (int y = 1; y < wallHeight; y += 2) {
            for (int i = 0; i < wallRing.size(); i += 4) {
                int[] o = wallRing.get(i);
                if (o[1] >= radius - 1 && Math.abs(o[0]) <= 1) continue; // keep the doorway clear
                int px = ox + o[0];
                int pz = oz + o[1];
                if (Math.abs(o[0]) >= Math.abs(o[1])) px += Integer.signum(o[0]);
                else pz += Integer.signum(o[1]);
                world.setBlock(cursor.set(px, baseY + y, pz), fence, StructureHelper.SET_FLAGS);
            }
        }

        // Carpet the floor, then clear the threshold again below.
        for (int[] o : inner) {
            world.setBlock(cursor.set(ox + o[0], baseY + 1, oz + o[1]),
                palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // South doorway, hung rather than punched, with the approach cleared.
        for (int y = 1; y <= 3; y++) {
            world.setBlock(cursor.set(ox, baseY + y, oz + radius), air, StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(ox, baseY + y, oz + radius - 1), air, StructureHelper.SET_FLAGS);
            world.setBlock(cursor.set(ox, baseY + y, oz + radius + 1), air, StructureHelper.SET_FLAGS);
        }
        world.setBlock(cursor.set(ox, baseY, oz + radius), packedMud, StructureHelper.SET_FLAGS);
        world.setBlock(cursor.set(ox, baseY, oz + radius - 1), packedMud, StructureHelper.SET_FLAGS);
        Openings.door(world, new BlockPos(ox, baseY + 1, oz + radius), Direction.NORTH,
            palette.door.defaultBlockState(), null, packedMud, false);

        Furnish.hangingLantern(world, new BlockPos(ox, baseY + wallHeight - 1, oz), palette.getLightState());
    }

    /**
     * TAIGA SMALL: the chieftain's mead hall.
     *
     * <p>A log longhouse on a stone footing: heavy timber walls, a steep gable, a longfire down the
     * middle, sleeping benches along both long walls, and the high seat on the east wall where a
     * Norse hall actually put it. Doors hung at both gable ends, a palisade drawn in tight.
     *
     * <p>Three things this replaces. The roof's smoke hole was cut by clearing the roof position at
     * every Y at once, which removed both roof planes from ridge to eave and left the hall roofless
     * along its centre line. The sleeping platforms were bottom slabs with beds placed a full block
     * above them, so four beds floated half a block clear of the boards. And the palisade's bounding
     * radius, not the building's, set the export box: a 13x25 hall shipped inside 37x37.
     */
    private CastleBounds generateTaigaLonghouse(ServerLevel world, BlockPos center) {
        int halfWidth = 5;    // 11 blocks east-west
        int halfLength = 9;   // 19 blocks north-south
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();

        BlockPos min = new BlockPos(ox - halfWidth, baseY, oz - halfLength);
        BlockPos max = new BlockPos(ox + halfWidth, baseY, oz + halfLength);

        BlockState cobble = palette.getPrimaryWallState();
        BlockState mossy = palette.getSecondaryWallState();
        BlockState log = palette.log.defaultBlockState();
        BlockState planks = palette.getPlanksState();
        BlockState roofBlock = palette.getRoofState();
        BlockState woodStairs = palette.woodStairs.defaultBlockState();
        BlockState woodSlab = palette.woodSlab.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // === SHELL: two courses of stone footing, then log walls ===
        int wallH = 5;
        int wallTop = Facade.walls(world, min, max, cobble, 1,
            java.util.List.of(new Facade.Storey(wallH, log, null, null)), 0);
        Facade.ring(world, ox - halfWidth, ox + halfWidth, oz - halfLength, oz + halfLength, baseY, mossy);

        int floorY = baseY;
        Facade.room(world, min, max, floorY, wallTop, planks);

        // === ROOF: steep gable down the long axis, gable ends closed ===
        int eaveY = wallTop + 1;
        Roofs.eaveTrim(world, min, max, eaveY, 1, woodStairs);
        int ridgeY = Roofs.gable(world, min, max, eaveY, Roofs.Ridge.Z, 1, roofBlock, log, log);

        // Smoke hole: a bounded opening over the hearth, not a slice through the whole roof.
        Roofs.openHole(world, ox - 1, ox + 1, eaveY, ridgeY, oz - 1, oz + 1);

        // === DOORS at both gable ends ===
        Openings.door(world, new BlockPos(ox, baseY + 1, oz + halfLength), Direction.NORTH,
            palette.door.defaultBlockState(), log, cobble, false);
        Openings.door(world, new BlockPos(ox, baseY + 1, oz - halfLength), Direction.SOUTH,
            palette.door.defaultBlockState(), log, cobble, false);

        // === ARROW SLITS along the long walls ===
        for (int z = -halfLength + 3; z <= halfLength - 3; z += 4) {
            world.setBlock(new BlockPos(ox - halfWidth, baseY + 3, oz + z), palette.getBarsState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, baseY + 3, oz + z), palette.getBarsState(), StructureHelper.SET_FLAGS);
        }

        // === LONGFIRE down the centre ===
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlock(new BlockPos(ox, floorY, oz + dz), cobble, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox, floorY + 1, oz + dz), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - 1, floorY, oz + dz), cobble, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 1, floorY, oz + dz), cobble, StructureHelper.SET_FLAGS);
        }

        // === SLEEPING BENCHES: top slabs, so what sits on them sits flush ===
        Furnish.platform(world, new BlockPos(ox - halfWidth + 1, 0, oz - halfLength + 1),
            new BlockPos(ox - halfWidth + 2, 0, oz + halfLength - 1), floorY, woodSlab);
        Furnish.platform(world, new BlockPos(ox + halfWidth - 2, 0, oz - halfLength + 1),
            new BlockPos(ox + halfWidth - 1, 0, oz + halfLength - 1), floorY, woodSlab);

        int benchY = Furnish.standing(floorY);
        for (int bz : new int[]{-halfLength + 2, -halfLength + 5}) {
            Furnish.bed(world, new BlockPos(ox - halfWidth + 1, benchY, oz + bz), Direction.SOUTH, palette.bed.defaultBlockState());
            Furnish.bed(world, new BlockPos(ox + halfWidth - 2, benchY, oz + bz), Direction.SOUTH, palette.bed.defaultBlockState());
        }

        // === HIGH SEAT on the east long wall, facing across the hall ===
        Furnish.chair(world, new BlockPos(ox + halfWidth - 2, benchY, oz), Direction.WEST, woodStairs);
        world.setBlock(new BlockPos(ox + halfWidth - 2, benchY + 2, oz - 1), palette.fence.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + halfWidth - 2, benchY + 2, oz + 1), palette.fence.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === STORES ===
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 1, benchY + 1, oz + halfLength - 2),
            Direction.EAST, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);
        Furnish.onFloor(world, new BlockPos(ox + halfWidth - 1, benchY, oz + halfLength - 2), Blocks.BARREL.defaultBlockState());
        Furnish.onFloor(world, new BlockPos(ox + halfWidth - 1, benchY, oz + halfLength - 3), Blocks.SMOKER.defaultBlockState());
        Furnish.onFloor(world, new BlockPos(ox - halfWidth + 1, benchY, oz - halfLength + 8), Blocks.FLETCHING_TABLE.defaultBlockState());

        for (int z = -halfLength + 3; z <= halfLength - 3; z += 5) {
            world.setBlock(new BlockPos(ox - halfWidth + 1, baseY + 4, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth - 1, baseY + 4, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // === PALISADE: drawn in tight, gates aligned with the hall doors ===
        int palX = halfWidth + 3;
        int palZ = halfLength + 3;
        for (int x = -palX; x <= palX; x++) {
            for (int z = -palZ; z <= palZ; z++) {
                boolean edge = Math.abs(x) == palX || Math.abs(z) == palZ;
                if (!edge) continue;
                if (Math.abs(x) <= 1 && Math.abs(z) == palZ) continue; // gateways
                for (int y = 1; y <= 3; y++) {
                    world.setBlock(new BlockPos(ox + x, baseY + y, oz + z), log, StructureHelper.SET_FLAGS);
                }
                world.setBlock(new BlockPos(ox + x, baseY, oz + z), cobble, StructureHelper.SET_FLAGS);
            }
        }
        for (int gz : new int[]{-palZ, palZ}) {
            for (int gx = -1; gx <= 1; gx++) {
                world.setBlock(new BlockPos(ox + gx, baseY, oz + gz), cobble, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + gx, baseY + 1, oz + gz),
                    palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING,
                        gz > 0 ? Direction.SOUTH : Direction.NORTH), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + gx, baseY + 2, oz + gz), air, StructureHelper.SET_FLAGS);
            }
            world.setBlock(new BlockPos(ox - 2, baseY + 4, oz + gz), Blocks.LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 2, baseY + 4, oz + gz), Blocks.LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        VillageCastles.LOGGER.debug("Taiga longhouse (mead hall) generation complete!");
        return new CastleBounds(
            new BlockPos(ox - palX - 1, baseY, oz - palZ - 1),
            new BlockPos(ox + palX + 1, ridgeY + 2, oz + palZ + 1)
        );
    }

    /**
     * Generate a fortified igloo for the snowy small castle variant.
     * Replaces the tower keep entirely with a snow/packed-ice dome,
     * furnished interior, and a short entrance tunnel on the south side.
     */
    private CastleBounds generateIgloo(ServerLevel world, BlockPos center) {
        int domeRadius = 9;   // outer dome radius
        int wallThickness = 2;
        int innerRadius = domeRadius - wallThickness;
        int groundRadius = 15;
        int domeHeight = 9;   // peak height above floor
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState packedIce = Blocks.PACKED_ICE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState campfireState = Blocks.CAMPFIRE.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState hangingLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState standingLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
        BlockState spruceFence = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState spruceDoorLower = Blocks.SPRUCE_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockState spruceDoorUpper = Blocks.SPRUCE_DOOR.defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        BlockState snowLayer = Blocks.SNOW.defaultBlockState();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // ========================================
        // 1. Prepare the ground (circular, radius 15)
        // ========================================
        // Snow apron under the dome and its approach, and nothing below it. This used to lay five
        // courses of cobblestone across a disc of radius 17 before doing anything else, which is
        // roughly 4,500 blocks of buried plinth inside a template whose building is 22 across.
        // CastleGroundsPiece does the footing at placement, against the terrain that is actually
        // there.
        int apronRadius = domeRadius + 2;
        int apronLimitSq = apronRadius * apronRadius;
        for (int x = -apronRadius; x <= apronRadius; x++) {
            for (int z = -apronRadius; z <= apronRadius; z++) {
                if (x * x + z * z > apronLimitSq) continue;

                int wx = ox + x;
                int wz = oz + z;

                mutable.set(wx, baseY, wz);
                world.setBlock(mutable, snowBlock, StructureHelper.SET_FLAGS);

                for (int y = 1; y <= domeHeight + 6; y++) {
                    mutable.set(wx, baseY + y, wz);
                    if (!world.getBlockState(mutable).isAir()) {
                        world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
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
                                world.setBlock(mutable, snowBlock, StructureHelper.SET_FLAGS);
                            } else {
                                world.setBlock(mutable, packedIce, StructureHelper.SET_FLAGS);
                            }
                        }
                        // else: interior, will be cleared in step 3
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
                        world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
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
                    world.setBlock(mutable, packedIce, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ========================================
        // 5. Furnish the interior
        // ========================================
        int floorY = baseY + 2; // Items sit on top of the floor layer at baseY+1

        // Central soul campfire on stone brick base
        mutable.set(ox, baseY + 1, oz);
        world.setBlock(mutable, stoneBricks, StructureHelper.SET_FLAGS);
        mutable.set(ox, floorY, oz);
        world.setBlock(mutable, campfireState, StructureHelper.SET_FLAGS);

        // Beds along the north wall (3 beds, light_blue, facing south)
        BlockState bedFoot = Blocks.BED.pick(DyeColor.LIGHT_BLUE).defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT)
            .setValue(BedBlock.FACING, Direction.SOUTH);
        BlockState bedHead = Blocks.BED.pick(DyeColor.LIGHT_BLUE).defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD)
            .setValue(BedBlock.FACING, Direction.SOUTH);

        int[] bedXOffsets = { -4, -1, 2 };
        for (int bx : bedXOffsets) {
            // FOOT against the north wall (lower Z), HEAD one block south (higher Z)
            mutable.set(ox + bx, floorY, oz - innerRadius + 2);
            world.setBlock(mutable, bedFoot, StructureHelper.SET_FLAGS);
            mutable.set(ox + bx, floorY, oz - innerRadius + 3);
            world.setBlock(mutable, bedHead, StructureHelper.SET_FLAGS);
        }

        // Barrel storage (west side)
        mutable.set(ox - innerRadius + 2, floorY, oz - 2);
        world.setBlock(mutable, Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ox - innerRadius + 2, floorY, oz - 1);
        world.setBlock(mutable, Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Crafting table + furnace (east side)
        mutable.set(ox + innerRadius - 2, floorY, oz - 2);
        world.setBlock(mutable, Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ox + innerRadius - 2, floorY, oz - 1);
        world.setBlock(mutable, Blocks.FURNACE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Chest with loot (east side, near crafting area)
        StructureHelper.placeChest(world, new BlockPos(ox + innerRadius - 2, floorY, oz),
            Direction.WEST, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);

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
                        world.setBlock(mutable, hangingLantern, StructureHelper.SET_FLAGS);
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
                world.setBlock(mutable, packedIce, StructureHelper.SET_FLAGS);

                // Clear tunnel air (3 high)
                for (int ty = 2; ty <= 4; ty++) {
                    mutable.set(ox + tx, baseY + ty, tz);
                    world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
                }

                // Tunnel ceiling
                mutable.set(ox + tx, baseY + 5, tz);
                world.setBlock(mutable, snowBlock, StructureHelper.SET_FLAGS);
            }

            // Tunnel walls (one block to each side of the 2-wide passage)
            for (int ty = 1; ty <= 5; ty++) {
                mutable.set(ox - 2, baseY + ty, tz);
                world.setBlock(mutable, snowBlock, StructureHelper.SET_FLAGS);
                mutable.set(ox + 1, baseY + ty, tz);
                world.setBlock(mutable, snowBlock, StructureHelper.SET_FLAGS);
            }
        }

        // Spruce door at the outer end of the tunnel
        mutable.set(ox - 1, baseY + 2, tunnelEndZ);
        world.setBlock(mutable, spruceDoorLower, StructureHelper.SET_FLAGS);
        mutable.set(ox - 1, baseY + 3, tunnelEndZ);
        world.setBlock(mutable, spruceDoorUpper, StructureHelper.SET_FLAGS);
        mutable.set(ox, baseY + 2, tunnelEndZ);
        world.setBlock(mutable, spruceDoorLower, StructureHelper.SET_FLAGS);
        mutable.set(ox, baseY + 3, tunnelEndZ);
        world.setBlock(mutable, spruceDoorUpper, StructureHelper.SET_FLAGS);

        // ========================================
        // 7. Exterior details
        // ========================================

        // Chimney hole at the top of the dome (1-block opening with soul campfire below visible)
        mutable.set(ox, baseY + 1 + domeHeight, oz);
        world.setBlock(mutable, air, StructureHelper.SET_FLAGS);

        // Snow layers scattered around the base of the dome
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            int sx = ox + (int) Math.round((domeRadius + 2) * Math.cos(rad));
            int sz = oz + (int) Math.round((domeRadius + 2) * Math.sin(rad));
            mutable.set(sx, baseY + 1, sz);
            if (world.getBlockState(mutable).isAir()) {
                world.setBlock(mutable, snowLayer, StructureHelper.SET_FLAGS);
            }
        }

        // Spruce fence posts outside as drying racks (west side)
        for (int i = 0; i < 3; i++) {
            BlockPos fencePos = new BlockPos(ox - domeRadius - 2, baseY + 1, oz - 3 + i * 3);
            world.setBlock(fencePos, spruceFence, StructureHelper.SET_FLAGS);
            world.setBlock(fencePos.above(), spruceFence, StructureHelper.SET_FLAGS);
        }

        // Lantern on a fence post by the entrance (south side)
        BlockPos entranceFencePos = new BlockPos(ox + 2, baseY + 1, tunnelEndZ + 1);
        world.setBlock(entranceFencePos, spruceFence, StructureHelper.SET_FLAGS);
        world.setBlock(entranceFencePos.above(), spruceFence, StructureHelper.SET_FLAGS);
        world.setBlock(entranceFencePos.above(2), standingLantern, StructureHelper.SET_FLAGS);

        // ========================================
        // 8. Aurora viewing platform (on top of dome)
        // ========================================
        // 3x3 packed_ice platform on top of the dome, with soul lantern fence posts at corners
        int platformY = baseY + 1 + domeHeight + 1; // one block above dome peak
        BlockState standingSoulLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);

        for (int px = -1; px <= 1; px++) {
            for (int pz = -1; pz <= 1; pz++) {
                mutable.set(ox + px, platformY, oz + pz);
                world.setBlock(mutable, packedIce, StructureHelper.SET_FLAGS);
            }
        }

        // Soul lantern on fence posts at 4 corners: blue glow mimics aurora light
        int[][] auroraCorners = { {-1, -1}, {1, -1}, {-1, 1}, {1, 1} };
        for (int[] corner : auroraCorners) {
            mutable.set(ox + corner[0], platformY + 1, oz + corner[1]);
            world.setBlock(mutable, spruceFence, StructureHelper.SET_FLAGS);
            mutable.set(ox + corner[0], platformY + 2, oz + corner[1]);
            world.setBlock(mutable, standingSoulLantern, StructureHelper.SET_FLAGS);
        }

        // Snow layers around the platform edge
        for (int px = -2; px <= 2; px++) {
            for (int pz = -2; pz <= 2; pz++) {
                if (Math.abs(px) <= 1 && Math.abs(pz) <= 1) continue; // skip platform itself
                // Only place snow layer if there is a solid dome block below
                mutable.set(ox + px, platformY - 1, oz + pz);
                if (!world.getBlockState(mutable).isAir()) {
                    mutable.set(ox + px, platformY, oz + pz);
                    world.setBlock(mutable, snowLayer, StructureHelper.SET_FLAGS);
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
                    world.setBlock(mutable, Blocks.BLUE_ICE.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Hollow interior (clear 1x1x2 air space inside the 3x3x3 shell)
        for (int cy = cellarBaseY + 1; cy <= cellarTopY; cy++) {
            mutable.set(ox, cy, oz);
            world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
        }
        // Also clear the blocks adjacent to center for a wider chamber interior
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                if (cx == 0 && cz == 0) continue;
                // Walls stay blue_ice at the outer ring, clear interior on the middle layer
                if (Math.abs(cx) < 1 || Math.abs(cz) < 1) {
                    mutable.set(ox + cx, cellarBaseY + 1, oz + cz);
                    world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
                    mutable.set(ox + cx, cellarTopY, oz + cz);
                    world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Spruce trapdoor access directly above the cellar
        // The floor is at baseY+1, trapdoor sits on top facing down into the cellar
        BlockPos trapdoorPos = new BlockPos(ox + 1, baseY + 1, oz);
        world.setBlock(trapdoorPos, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
            .setValue(TrapDoorBlock.HALF, Half.TOP)
            .setValue(TrapDoorBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        // Clear air below the trapdoor for access shaft down to cellar
        for (int dy = 0; dy >= cellarBaseY - baseY + 1; dy--) {
            mutable.set(trapdoorPos.getX(), baseY + dy, trapdoorPos.getZ());
            if (dy < 0) {
                world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
            }
        }

        // Cellar contents: 2 barrels and a chest with stronghold corridor loot
        mutable.set(ox - 1, cellarBaseY + 1, oz);
        world.setBlock(mutable, Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ox + 1, cellarBaseY + 1, oz);
        world.setBlock(mutable, Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, new BlockPos(ox, cellarBaseY + 1, oz),
            Direction.NORTH, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);

        // Village bell near the entrance: required for villager gathering and raids
        world.setBlock(new BlockPos(ox + 3, baseY + 1, tunnelEndZ - 2),
            Blocks.SPRUCE_FENCE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 3, baseY + 2, tunnelEndZ - 2),
            Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Jigsaw connectors at perimeter for village street integration
        placeJigsawConnectors(world, center, groundRadius);

        VillageCastles.LOGGER.debug("Fortified igloo generation complete!");

        // Return bounds encompassing the igloo dome + tunnel + jigsaw connectors
        return new CastleBounds(
            center.offset(-groundRadius - 2, 0, -groundRadius - 2),
            center.offset(groundRadius + 2, domeHeight + 5, tunnelEndZ - oz + 3)
        );
    }

    /**
     * MEDIUM: Standard Fort.
     * Keep + 4 corner towers + main gatehouse + curtain walls + courtyard.
     * No wall towers, no back gate.
     */
    /**
     * MEDIUM: Biome-specific fortification.
     * Plains: motte-and-bailey. Desert: walled compound. Savanna: multi-compound.
     * Taiga/Snowy: classic fort with biome-specific materials and tower styles.
     */
    private CastleBounds generateMedium(ServerLevel world, BlockPos center, int radius,
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
     * DESERT MEDIUM: Walled compound.
     * Thick sandstone outer walls with corner bastions, shaded inner courtyard,
     * central keep with flat roof, archery range, cistern. Arid fortress.
     */
    private CastleBounds generateDesertCompound(ServerLevel world, BlockPos center, int radius,
                                                  int keepHalfWidth, int keepHalfDepth) {
        VillageCastles.LOGGER.debug("Generating fortified caravanserai palace at {}", center.toShortString());

        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        int wallHeight = 7;
        int wallThickness = 2;
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // Block palette
        BlockState sandstone = palette.getPrimaryWallState();
        BlockState cutSandstone = Blocks.CUT_SANDSTONE.defaultBlockState();
        BlockState smoothSandstone = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        BlockState chiseledSandstone = Blocks.CHISELED_SANDSTONE.defaultBlockState();
        BlockState orangeTerracotta = Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState();
        BlockState soulLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState floorLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
        BlockState wallBlock = palette.wall.defaultBlockState();
        BlockState ironBars = Blocks.IRON_BARS.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState acaciaSlab = palette.woodSlab.defaultBlockState();
        BlockState carpet = palette.getCarpetState();
        BlockState bedFoot = palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD);

        // Compound dimensions - rectangular, longer north-south
        int halfW = radius;          // east-west half width
        int halfD = radius + 2;      // north-south half depth (longer)

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
                    world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
                    mutable.set(ox + x, baseY + y, oz + halfD - t);
                    world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
                }
            }
        }
        // East/West walls
        for (int z = -halfD; z <= halfD; z++) {
            for (int t = 0; t < wallThickness; t++) {
                for (int y = 0; y < wallHeight; y++) {
                    mutable.set(ox - halfW + t, baseY + y, oz + z);
                    world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
                    mutable.set(ox + halfW - t, baseY + y, oz + z);
                    world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Flat walkable roof on outer walls
        for (int x = -halfW; x <= halfW; x++) {
            for (int t = 0; t < wallThickness; t++) {
                mutable.set(ox + x, baseY + wallHeight, oz - halfD + t);
                world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
                mutable.set(ox + x, baseY + wallHeight, oz + halfD - t);
                world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = -halfD; z <= halfD; z++) {
            for (int t = 0; t < wallThickness; t++) {
                mutable.set(ox - halfW + t, baseY + wallHeight, oz + z);
                world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
                mutable.set(ox + halfW - t, baseY + wallHeight, oz + z);
                world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            }
        }

        // SANDSTONE_WALL parapet on roof edges
        for (int x = -halfW; x <= halfW; x++) {
            mutable.set(ox + x, baseY + wallHeight + 1, oz - halfD);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(ox + x, baseY + wallHeight + 1, oz + halfD);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
        }
        for (int z = -halfD; z <= halfD; z++) {
            mutable.set(ox - halfW, baseY + wallHeight + 1, oz + z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(ox + halfW, baseY + wallHeight + 1, oz + z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
        }

        // Iron bar mashrabiya windows on exterior walls (every 4 blocks, at y+3 and y+4)
        // Punch through both outer and inner wall layers
        for (int x = -halfW + 4; x <= halfW - 4; x += 4) {
            for (int wy = 3; wy <= 4; wy++) {
                // Outer layer (north/south)
                mutable.set(ox + x, baseY + wy, oz - halfD);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + x, baseY + wy, oz + halfD);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
                // Inner layer (north/south)
                mutable.set(ox + x, baseY + wy, oz - halfD + 1);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + x, baseY + wy, oz + halfD - 1);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = -halfD + 4; z <= halfD - 4; z += 4) {
            for (int wy = 3; wy <= 4; wy++) {
                // Outer layer (east/west)
                mutable.set(ox - halfW, baseY + wy, oz + z);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + halfW, baseY + wy, oz + z);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
                // Inner layer (east/west)
                mutable.set(ox - halfW + 1, baseY + wy, oz + z);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
                mutable.set(ox + halfW - 1, baseY + wy, oz + z);
                world.setBlock(mutable, ironBars, StructureHelper.SET_FLAGS);
            }
        }

        StructureHelper.clearInterior(world,
            center.offset(-halfW + wallThickness, 0, -halfD + wallThickness),
            center.offset(halfW - wallThickness, wallHeight + 2, halfD - wallThickness));

        // ================================================================
        // Step 2: Interior floor - base smooth sandstone
        // ================================================================
        StructureHelper.fillBox(world,
            center.offset(-halfW + wallThickness, 0, -halfD + wallThickness),
            center.offset(halfW - wallThickness, 0, halfD - wallThickness),
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
                world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Room partition walls along east side (guard quarters) - Z from divideZ to south wall
        // NOTE: eastRoomX is ABSOLUTE (includes ox)
        int eastRoomX = ox + innerW - 7; // East room is 7 blocks wide
        for (int z = divideZ + 1; z <= oz + innerD; z++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(eastRoomX, baseY + y, z);
                world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Room partition walls along west side (kitchen) - Z from divideZ to south wall
        // NOTE: westRoomX is ABSOLUTE (includes ox)
        int westRoomX = ox - innerW + 7; // West room is 7 blocks wide
        for (int z = divideZ + 1; z <= oz + innerD; z++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(westRoomX, baseY + y, z);
                world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // South audience hall partition (separates audience hall from main courtyard)
        // NOTE: audienceZ is ABSOLUTE (includes oz)
        int audienceZ = oz + innerD - 6; // Audience hall is 6 blocks deep at south
        for (int x = westRoomX; x <= eastRoomX; x++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(x, baseY + y, audienceZ);
                world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Lord's chambers partition (north side, between private courtyard and north wall)
        // NOTE: lordZ is ABSOLUTE (includes oz)
        int lordZ = oz - innerD + 6; // Lord's chambers 6 blocks deep at north
        for (int x = -innerW; x <= innerW; x++) {
            for (int y = 1; y <= roomWallHeight; y++) {
                mutable.set(ox + x, baseY + y, lordZ);
                world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Doorways in partition walls (3-wide, 3-tall openings)
        // Divide wall: central passage between courtyards
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                mutable.set(ox + dx, baseY + dy, divideZ);
                world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
            }
        }
        // East room doorway
        for (int dy = 1; dy <= 3; dy++) {
            mutable.set(eastRoomX, baseY + dy, divideZ + 4);
            world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
            mutable.set(eastRoomX, baseY + dy, divideZ + 5);
            world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
        }
        // West room doorway
        for (int dy = 1; dy <= 3; dy++) {
            mutable.set(westRoomX, baseY + dy, divideZ + 4);
            world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
            mutable.set(westRoomX, baseY + dy, divideZ + 5);
            world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
        }
        // Audience hall doorway (from courtyard)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                mutable.set(ox + dx, baseY + dy, audienceZ);
                world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
            }
        }
        // Lord's chambers doorway (from private courtyard)
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                mutable.set(ox + dx, baseY + dy, lordZ);
                world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
            }
        }

        // ================================================================
        // Step 4: Main courtyard (larger, south-center area)
        // ================================================================
        // Courtyard bounds: ALL ABSOLUTE coordinates
        int cyMinX = westRoomX + 1;
        int cyMaxX = eastRoomX - 1;
        int cyMinZ = divideZ + 1;
        int cyMaxZ = audienceZ - 1;

        // Checkerboard floor: CUT_SANDSTONE + ORANGE_TERRACOTTA
        for (int x = cyMinX; x <= cyMaxX; x++) {
            for (int z = cyMinZ; z <= cyMaxZ; z++) {
                BlockState floorBlock = ((x + z) % 2 == 0) ? cutSandstone : orangeTerracotta;
                mutable.set(x, baseY, z);
                world.setBlock(mutable, floorBlock, StructureHelper.SET_FLAGS);
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
                    world.setBlock(mutable, cutSandstone, StructureHelper.SET_FLAGS);
                    mutable.set(fountainX + fx, baseY + 1, fountainZ + fz);
                    world.setBlock(mutable, cutSandstone, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Water pool (3x3, sunken 1 block)
        for (int fx = -1; fx <= 1; fx++) {
            for (int fz = -1; fz <= 1; fz++) {
                mutable.set(fountainX + fx, baseY, fountainZ + fz);
                world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
            }
        }
        // Central column (no cascade water at top: too messy)
        mutable.set(fountainX, baseY + 1, fountainZ);
        world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
        mutable.set(fountainX, baseY + 2, fountainZ);
        world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);

        // Colonnade walkways around main courtyard (pillars + shade roof every 2 blocks)
        for (int x = cyMinX; x <= cyMaxX; x += 2) {
            // North colonnade (along divideZ+1)
            mutable.set(x, baseY + 1, cyMinZ);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 2, cyMinZ);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 3, cyMinZ);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 4, cyMinZ);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            // Shade roof extends one block into courtyard
            mutable.set(x, baseY + 4, cyMinZ + 1);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);

            // South colonnade (along audienceZ-1)
            mutable.set(x, baseY + 1, cyMaxZ);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 2, cyMaxZ);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 3, cyMaxZ);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 4, cyMaxZ);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 4, cyMaxZ - 1);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
        }
        for (int z = cyMinZ; z <= cyMaxZ; z += 2) {
            // East colonnade
            mutable.set(cyMaxX, baseY + 1, z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX, baseY + 2, z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX, baseY + 3, z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX, baseY + 4, z);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            mutable.set(cyMaxX - 1, baseY + 4, z);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);

            // West colonnade
            mutable.set(cyMinX, baseY + 1, z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX, baseY + 2, z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX, baseY + 3, z);
            world.setBlock(mutable, wallBlock, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX, baseY + 4, z);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
            mutable.set(cyMinX + 1, baseY + 4, z);
            world.setBlock(mutable, acaciaSlab, StructureHelper.SET_FLAGS);
        }

        // Water channels (chahar bagh layout) - from fountain to courtyard corners
        // North channel
        for (int z = cyMinZ + 1; z < fountainZ - 2; z++) {
            mutable.set(fountainX, baseY, z);
            world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
            mutable.set(fountainX - 1, baseY, z);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(fountainX + 1, baseY, z);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }
        // South channel
        for (int z = fountainZ + 3; z <= cyMaxZ - 1; z++) {
            mutable.set(fountainX, baseY, z);
            world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
            mutable.set(fountainX - 1, baseY, z);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(fountainX + 1, baseY, z);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }
        // East channel: water at baseY, borders at baseY to match north/south channels
        for (int x = fountainX + 3; x <= cyMaxX - 1; x++) {
            mutable.set(x, baseY, fountainZ);
            world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
            // Border blocks on sides at baseY (same level as north/south channel borders)
            mutable.set(x, baseY, fountainZ - 1);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY, fountainZ + 1);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }
        // West channel: water at baseY, borders at baseY to match north/south channels
        for (int x = cyMinX + 1; x < fountainX - 2; x++) {
            mutable.set(x, baseY, fountainZ);
            world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
            // Border blocks on sides at baseY
            mutable.set(x, baseY, fountainZ - 1);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY, fountainZ + 1);
            world.setBlock(mutable, smoothSandstone, StructureHelper.SET_FLAGS);
        }

        // Bell in main courtyard
        mutable.set(fountainX + 3, baseY, fountainZ + 3);
        world.setBlock(mutable, palette.fence.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(fountainX + 3, baseY + 1, fountainZ + 3);
        world.setBlock(mutable, Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Soul lanterns throughout main courtyard (hanging from colonnade slabs)
        for (int x = cyMinX + 1; x <= cyMaxX - 1; x += 3) {
            mutable.set(x, baseY + 3, cyMinZ + 1);
            world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);
            mutable.set(x, baseY + 3, cyMaxZ - 1);
            world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);
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
                world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
            }
        }
        // Pool border
        for (int fx = -1; fx <= 2; fx++) {
            mutable.set(pcCenterX + fx, baseY, pcCenterZ - 1);
            world.setBlock(mutable, cutSandstone, StructureHelper.SET_FLAGS);
            mutable.set(pcCenterX + fx, baseY, pcCenterZ + 2);
            world.setBlock(mutable, cutSandstone, StructureHelper.SET_FLAGS);
        }
        for (int fz = 0; fz <= 1; fz++) {
            mutable.set(pcCenterX - 1, baseY, pcCenterZ + fz);
            world.setBlock(mutable, cutSandstone, StructureHelper.SET_FLAGS);
            mutable.set(pcCenterX + 2, baseY, pcCenterZ + fz);
            world.setBlock(mutable, cutSandstone, StructureHelper.SET_FLAGS);
        }

        // Decorated pots in private courtyard corners (pcMinX/pcMaxX are relative, pcMinZ is absolute)
        mutable.set(ox + pcMinX + 1, baseY + 1, pcMinZ + 1);
        world.setBlock(mutable, Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMaxX - 1, baseY + 1, pcMinZ + 1);
        world.setBlock(mutable, Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Soul lanterns in private courtyard, with solid block above for hanging
        mutable.set(ox + pcMinX + 1, baseY + 5, pcMinZ + 1);
        world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMinX + 1, baseY + 4, pcMinZ + 1);
        world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMaxX - 1, baseY + 5, pcMaxZ - 1);
        world.setBlock(mutable, sandstone, StructureHelper.SET_FLAGS);
        mutable.set(ox + pcMaxX - 1, baseY + 4, pcMaxZ - 1);
        world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 6: Audience hall (south room, between audienceZ and south wall)
        // ================================================================
        // NOTE: ahFloorZ and ahBackZ are ABSOLUTE
        int ahFloorZ = audienceZ + 1;
        int ahBackZ = oz + innerD;

        // Throne (SANDSTONE_STAIRS facing north - seated player faces north toward courtyard)
        world.setBlock(new BlockPos(ox, baseY + 1, ahBackZ - 1),
            Blocks.SANDSTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH)
                .setValue(StairBlock.HALF, Half.BOTTOM),
            StructureHelper.SET_FLAGS);
        // Throne back
        world.setBlock(new BlockPos(ox, baseY + 1, ahBackZ),
            chiseledSandstone, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, baseY + 2, ahBackZ),
            chiseledSandstone, StructureHelper.SET_FLAGS);

        // Yellow carpet runner from door to throne
        for (int z = ahFloorZ; z <= ahBackZ - 2; z++) {
            mutable.set(ox, baseY + 1, z);
            world.setBlock(mutable, carpet, StructureHelper.SET_FLAGS);
        }

        // Chiseled sandstone pillar accents (2 on each side of carpet)
        for (int pz = ahFloorZ + 1; pz <= ahBackZ - 2; pz += 3) {
            for (int py = 1; py <= roomWallHeight; py++) {
                mutable.set(ox - 2, baseY + py, pz);
                world.setBlock(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
                mutable.set(ox + 2, baseY + py, pz);
                world.setBlock(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Soul lanterns in audience hall
        mutable.set(ox - 2, baseY + roomWallHeight, ahFloorZ + 2);
        world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);
        mutable.set(ox + 2, baseY + roomWallHeight, ahFloorZ + 2);
        world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);

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
            world.setBlock(new BlockPos(bedX, baseY + 1, bedZ),
                bedFoot.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(bedX, baseY + 1, bedZ + 1),
                bedHead.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // Chests for guards
        StructureHelper.placeChest(world, new BlockPos(gqMaxX - 1, baseY + 1, gqMinZ + 4),
            Direction.WEST, BuiltInLootTables.VILLAGE_DESERT_HOUSE);
        StructureHelper.placeChest(world, new BlockPos(gqMaxX - 1, baseY + 1, gqMinZ + 6),
            Direction.WEST, BuiltInLootTables.VILLAGE_DESERT_HOUSE);

        mutable.set(gqCenterX, baseY + roomWallHeight, gqMinZ + 3);
        world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 8: Kitchen/stores (west room)
        // ================================================================
        // NOTE: ktMinX is ABSOLUTE, ktMaxX is ABSOLUTE, ktMinZ is ABSOLUTE
        int ktMinX = ox - innerW;
        int ktMaxX = westRoomX - 1;
        int ktMinZ = divideZ + 1;

        mutable.set(ktMinX + 1, baseY + 1, ktMinZ + 1);
        world.setBlock(mutable, Blocks.SMOKER.defaultBlockState()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        mutable.set(ktMinX + 3, baseY + 1, ktMinZ + 1);
        world.setBlock(mutable, Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);

        mutable.set(ktMinX + 1, baseY + 1, ktMinZ + 3);
        world.setBlock(mutable, Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ktMinX + 1, baseY + 2, ktMinZ + 3);
        world.setBlock(mutable, Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ktMinX + 2, baseY + 1, ktMinZ + 3);
        world.setBlock(mutable, Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);

        mutable.set(ktMinX + 4, baseY + 1, ktMinZ + 1);
        world.setBlock(mutable, Blocks.CAULDRON.defaultBlockState(), StructureHelper.SET_FLAGS);

        mutable.set(ktMinX + 3, baseY + roomWallHeight, ktMinZ + 2);
        world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 9: Lord's chambers (north, between north wall and lordZ)
        // ================================================================
        // NOTE: lcMinZ, lcMaxZ, lcCenterZ are ALL ABSOLUTE
        int lcMinZ = oz - innerD;
        int lcMaxZ = lordZ - 1;
        int lcCenterZ = (lcMinZ + lcMaxZ) / 2;

        // 2 beds (facing SOUTH)
        world.setBlock(new BlockPos(ox - 3, baseY + 1, lcCenterZ),
            bedFoot.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - 3, baseY + 1, lcCenterZ + 1),
            bedHead.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 3, baseY + 1, lcCenterZ),
            bedFoot.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 3, baseY + 1, lcCenterZ + 1),
            bedHead.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        mutable.set(ox - 2, baseY + 1, lcMinZ + 1);
        world.setBlock(mutable, Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ox - 1, baseY + 1, lcMinZ + 1);
        world.setBlock(mutable, Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        mutable.set(ox - 2, baseY + 2, lcMinZ + 1);
        world.setBlock(mutable, Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Personal chest
        StructureHelper.placeChest(world, new BlockPos(ox + 2, baseY + 1, lcMinZ + 1),
            Direction.SOUTH, BuiltInLootTables.VILLAGE_DESERT_HOUSE);

        mutable.set(ox + 4, baseY + 1, lcMinZ + 1);
        world.setBlock(mutable, Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        mutable.set(ox, baseY + roomWallHeight, lcCenterZ);
        world.setBlock(mutable, soulLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // Step 10: Entrance - recessed doorway on south wall
        // ================================================================
        // Carve entrance (3 wide, 4 tall) through south wall
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 1; dy <= 4; dy++) {
                mutable.set(ox + dx, baseY + dy, oz + halfD);
                world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
                mutable.set(ox + dx, baseY + dy, oz + halfD - 1);
                world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
            }
        }
        // Chiseled sandstone door frame
        for (int dy = 1; dy <= 4; dy++) {
            mutable.set(ox - 2, baseY + dy, oz + halfD);
            world.setBlock(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
            mutable.set(ox + 2, baseY + dy, oz + halfD);
            world.setBlock(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
        }
        // Hang the gate. The frame and the recess were built but nothing was ever hung in them.
        Openings.door(world, new BlockPos(ox - 1, baseY + 1, oz + halfD - 1), Direction.NORTH,
            palette.door.defaultBlockState(), null, palette.getFloorState(), true);
        // Arch top
        for (int dx = -2; dx <= 2; dx++) {
            mutable.set(ox + dx, baseY + 5, oz + halfD);
            world.setBlock(mutable, chiseledSandstone, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 11: Ladder access to roof (interior, east side near entrance)
        // ================================================================
        int ladderX = ox + innerW;
        int ladderZ = oz + innerD - 1;
        for (int ly = 1; ly <= wallHeight; ly++) {
            mutable.set(ladderX, baseY + ly, ladderZ);
            world.setBlock(mutable, Blocks.LADDER.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // Step 12: Room ceilings (smooth sandstone slabs over rooms)
        // ================================================================
        // Ceiling over audience hall (all vars are absolute: no ox/oz prefix needed)
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

        VillageCastles.LOGGER.debug("Fortified caravanserai palace generation complete!");
        return new CastleBounds(
            center.offset(-halfW - 2, 0, -halfD - 2),
            center.offset(halfW + 2, wallHeight + 4, halfD + 2)
        );
    }

    /**
     * SAVANNA MEDIUM: Chief's compound.
     * Chief's hut on raised packed-mud platform with toron-decorated mud brick walls.
     * Guard hut, open-air cookhouse on acacia log stilts, granary on stilts,
     * barracks hut. Council circle with log seating. Weaving and smithing areas.
     * Coarse dirt ground, hay thatch roofs, terracotta accents, dead bush thorns.
     */
    private CastleBounds generateSavannaCompound(ServerLevel world, BlockPos center) {
        int compoundRadius = 15;
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockState mudBrick = Blocks.MUD_BRICKS.defaultBlockState();
        BlockState packedMud = Blocks.PACKED_MUD.defaultBlockState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState fence = palette.fence.defaultBlockState();
        BlockState hayBlock = Blocks.HAY_BLOCK.defaultBlockState();
        BlockState deadBush = Blocks.DEAD_BUSH.defaultBlockState();
        BlockState logBlock = palette.log.defaultBlockState();

        // Coarse dirt ground throughout compound: placed at baseY-1 to replace
        // the surface block rather than adding a raised layer on top of terrain
        for (int x = -compoundRadius; x <= compoundRadius; x++) {
            for (int z = -compoundRadius; z <= compoundRadius; z++) {
                if (x * x + z * z <= compoundRadius * compoundRadius) {
                    world.setBlock(new BlockPos(ox + x, baseY - 1, oz + z), coarseDirt, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Acacia palisade perimeter (3 high) with dead bush thorns
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int fx = ox + (int) Math.round(compoundRadius * Math.cos(rad));
            int fz = oz + (int) Math.round(compoundRadius * Math.sin(rad));
            world.setBlock(new BlockPos(fx, baseY, fz), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(fx, baseY + 1, fz), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(fx, baseY + 2, fz), fence, StructureHelper.SET_FLAGS);
        }
        // Dead bush thorns outside palisade: support block at baseY-1 to replace terrain
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int bx = ox + (int) Math.round((compoundRadius + 1) * Math.cos(rad));
            int bz = oz + (int) Math.round((compoundRadius + 1) * Math.sin(rad));
            world.setBlock(new BlockPos(bx, baseY - 1, bz), coarseDirt, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(bx, baseY, bz), deadBush, StructureHelper.SET_FLAGS);
        }

        // === Chief's hut: north-center on raised packed-mud platform ===
        BlockPos chiefPos = center.offset(0, 0, -8);
        // Raised platform (2 blocks high, packed mud)
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (x * x + z * z <= 36) {
                    world.setBlock(chiefPos.offset(x, 0, z), packedMud, StructureHelper.SET_FLAGS);
                    world.setBlock(chiefPos.offset(x, 1, z), packedMud, StructureHelper.SET_FLAGS);
                }
            }
        }
        // MUD_BRICK_STAIRS tapering at platform edge (inverted)
        BlockState invertedMudStairs = Blocks.MUD_BRICK_STAIRS.defaultBlockState()
            .setValue(StairBlock.HALF, Half.TOP);
        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            int ex = (int) Math.round(6 * Math.cos(rad));
            int ez = (int) Math.round(6 * Math.sin(rad));
            if (ex * ex + ez * ez >= 30) {
                Direction facing;
                if (Math.abs(ex) > Math.abs(ez)) {
                    facing = ex > 0 ? Direction.EAST : Direction.WEST;
                } else {
                    facing = ez > 0 ? Direction.SOUTH : Direction.NORTH;
                }
                world.setBlock(chiefPos.offset(ex, 2, ez),
                    invertedMudStairs.setValue(StairBlock.FACING, facing), StructureHelper.SET_FLAGS);
            }
        }

        // Build chief's hut on platform (radius 5, wall height 5)
        buildRoundHut(world, chiefPos.above(2), 5, 5);

        // Toron on chief's hut walls (extra prominent: already done by buildRoundHut, but add
        // additional terracotta accents at the base of the platform)
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int ax = (int) Math.round(7 * Math.cos(rad));
            int az = (int) Math.round(7 * Math.sin(rad));
            world.setBlock(chiefPos.offset(ax, 0, az), Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Throne inside chief's hut: platform is at y+2, floor carpet at y+3, throne ON platform at y+3
        // Throne sits on a solid block (the packed mud floor) at platform+1 = chiefPos.y+3
        world.setBlock(chiefPos.offset(0, 3, -3),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Brown terracotta behind throne
        world.setBlock(chiefPos.offset(0, 4, -4), Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(), StructureHelper.SET_FLAGS);

        // Chief's bed
        world.setBlock(chiefPos.offset(-3, 3, 0), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlock(chiefPos.offset(-2, 3, 0), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, chiefPos.offset(3, 3, 0), Direction.WEST, BuiltInLootTables.VILLAGE_SAVANNA_HOUSE);

        // Decorated pot inside chief's hut
        world.setBlock(chiefPos.offset(2, 3, -2), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === Guard hut: east (radius 4, wall height 4), radius 4 gives usable interior ===
        buildRoundHut(world, center.offset(10, 0, -3), 4, 4);
        // Bed
        world.setBlock(center.offset(10, 1, -4), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(10, 1, -3), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // === Cookhouse: west (open-air on acacia log stilts, hay thatch roof) ===
        BlockPos cookPos = center.offset(-10, 0, -2);
        // Acacia log stilts (4 corners, 3 blocks high)
        for (int[] corner : new int[][]{{-2,-2},{2,-2},{-2,2},{2,2}}) {
            for (int y = 0; y <= 2; y++) {
                world.setBlock(cookPos.offset(corner[0], y, corner[1]), logBlock, StructureHelper.SET_FLAGS);
            }
        }
        // Hay thatch roof at stilt-top+1
        StructureHelper.fillFloor(world, cookPos.offset(-3, 0, -3), cookPos.offset(3, 0, 3), baseY + 3, hayBlock);
        // Campfire and crafting on ground (3-block headroom under roof)
        world.setBlock(cookPos, Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(cookPos.offset(1, 0, 0), Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(cookPos.offset(-1, 0, 0), Blocks.SMOKER.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === Granary: south-east (raised hut on acacia log stilts) ===
        BlockPos granaryPos = center.offset(8, 0, 8);
        // 4 acacia log stilts (2 blocks high)
        for (int[] corner : new int[][]{{-2,-2},{2,-2},{-2,2},{2,2}}) {
            for (int y = 0; y <= 1; y++) {
                world.setBlock(granaryPos.offset(corner[0], y, corner[1]), logBlock, StructureHelper.SET_FLAGS);
            }
        }
        // Packed mud platform floor at y+2
        StructureHelper.fillBox(world, granaryPos.offset(-2, 2, -2), granaryPos.offset(2, 2, 2), packedMud);
        // Mud brick walls (y+3 to y+5)
        StructureHelper.fillBox(world, granaryPos.offset(-2, 3, -2), granaryPos.offset(2, 5, 2), mudBrick);
        // Clear interior (3-block headroom: y+3, y+4, y+5)
        StructureHelper.clearInterior(world, granaryPos.offset(-1, 3, -1), granaryPos.offset(1, 5, 1));
        // Storage inside
        world.setBlock(granaryPos.offset(0, 3, 0), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(granaryPos.offset(1, 3, 0), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(granaryPos.offset(-1, 3, 0), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Conical hay thatch roof on granary
        for (int y = 0; y <= 3; y++) {
            int r = 3 - y;
            if (r <= 0) break;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + z * z <= r * r) {
                        world.setBlock(new BlockPos(granaryPos.getX() + x, baseY + 6 + y,
                            granaryPos.getZ() + z), hayBlock, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }
        // Toron pegs on granary walls
        for (int[] peg : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
            world.setBlock(granaryPos.offset(peg[0], 4, peg[1]), fence, StructureHelper.SET_FLAGS);
        }
        // Door opening on south side of granary (clear 2 wide x 3 high)
        for (int y = 3; y <= 5; y++) {
            world.setBlock(granaryPos.offset(0, y, 2), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // === Barracks hut: south-west (radius 4, wall height 4) ===
        buildRoundHut(world, center.offset(-8, 0, 8), 4, 4);
        // Bed 1
        world.setBlock(center.offset(-10, 1, 8), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-9, 1, 8), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        // Bed 2
        world.setBlock(center.offset(-6, 1, 8), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-7, 1, 8), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);

        // === Open-air council circle (center of compound) ===
        // Ring of acacia log seating around a fire
        world.setBlock(center, Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Terracotta ring around fire
        for (int[] off : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            world.setBlock(center.offset(off[0], 0, off[1]),
                Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int sx = (int) Math.round(4 * Math.cos(rad));
            int sz = (int) Math.round(4 * Math.sin(rad));
            world.setBlock(center.offset(sx, 0, sz), logBlock, StructureHelper.SET_FLAGS);
        }

        // === Weaving area (south-east between huts) ===
        world.setBlock(center.offset(5, 0, 5), Blocks.LOOM.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(6, 0, 5), Blocks.LOOM.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(7, 0, 6), palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(7, 0, 7), palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === Smithing area (near guard hut) ===
        world.setBlock(center.offset(12, 0, 3), Blocks.SMITHING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(13, 0, 3), Blocks.GRINDSTONE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(11, 0, 3), Blocks.ANVIL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === Decorated pots and terracotta accents ===
        world.setBlock(center.offset(-3, 0, 3), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(3, 0, -3), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-12, 0, 5), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(0, 0, 12), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Brown terracotta accent blocks
        world.setBlock(center.offset(-14, 0, 0), Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(14, 0, 0), Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(), StructureHelper.SET_FLAGS);

        // Hay storage near granary
        world.setBlock(center.offset(10, 0, 10), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(10, 1, 10), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(11, 0, 10), hayBlock, StructureHelper.SET_FLAGS);

        // Composters (farming)
        world.setBlock(center.offset(-12, 0, -5), Blocks.COMPOSTER.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-13, 0, -5), Blocks.COMPOSTER.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Drying rack near cookhouse (fence posts with carpet)
        for (int z = -5; z <= -2; z++) {
            world.setBlock(new BlockPos(ox - 14, baseY, oz + z), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - 14, baseY + 1, oz + z), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - 14, baseY + 2, oz + z), palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Torch posts throughout compound: fence base + fence + torch on top (solid support)
        for (int angle = 0; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int tx = ox + (int) Math.round(12 * Math.cos(rad));
            int tz = oz + (int) Math.round(12 * Math.sin(rad));
            world.setBlock(new BlockPos(tx, baseY, tz), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(tx, baseY + 1, tz), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(tx, baseY + 2, tz), Blocks.TORCH.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        // Council circle torches on fence posts
        for (int[] tp : new int[][]{{0, 5}, {5, 0}, {-5, 0}}) {
            world.setBlock(center.offset(tp[0], 0, tp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlock(center.offset(tp[0], 1, tp[1]), Blocks.TORCH.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Entrance gate south
        world.setBlock(new BlockPos(ox, baseY, oz + compoundRadius),
            palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH),
            StructureHelper.SET_FLAGS);

        // Bell on a fence post
        world.setBlock(center.offset(3, 0, 3), fence, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(3, 1, 3), fence, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(3, 2, 3), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, compoundRadius);

        VillageCastles.LOGGER.debug("Savanna chief's compound generation complete!");
        return new CastleBounds(
            center.offset(-compoundRadius - 2, 0, -compoundRadius - 2),
            center.offset(compoundRadius + 2, 15, compoundRadius + 2)
        );
    }

    /**
     * Generic medium fort: used by Plains (with motte), Taiga, and Snowy.
     * Keep + 4 corner towers + gatehouse + curtain walls + courtyard.
     * Biome differentiation comes from sub-generators and BiomePalette.
     */
    /**
     * SAVANNA LARGE: Sunken great longhouse (Great Enclosure).
     * Half-buried great hall excavated 5 blocks below ground. Mud brick retaining
     * walls with acacia log reinforcement and toron pegs. HAY_BLOCK thatched roof
     * barely above ground level. Entrance ramp descending from south with
     * MUD_BRICK_STAIRS. Central fire pits with smoke holes. Chief's raised platform
     * with throne at north end. Sleeping alcoves along walls. Storage at south end.
     */
    private CastleBounds generateGreatEnclosure(ServerLevel world, BlockPos center, int radius) {
        int halfLength = 20; // long axis (north-south)
        int halfWidth = 13;  // short axis (east-west)
        int wallHeight = 3;  // walls above ground level (roof barely visible)
        int baseY = center.getY();
        int depth = Math.min(5, baseY + 63); // Clamp to stay above y=-63 (world minimum is -64)
        if (depth < 2) depth = 2; // Minimum viable sunken depth
        int floorY = baseY - depth;
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockState mudBrick = Blocks.MUD_BRICKS.defaultBlockState();
        BlockState packedMud = Blocks.PACKED_MUD.defaultBlockState();
        BlockState logWall = palette.log.defaultBlockState();
        BlockState hayBlock = Blocks.HAY_BLOCK.defaultBlockState();
        BlockState coarseDirt = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState fence = palette.fence.defaultBlockState();
        BlockState deadBush = Blocks.DEAD_BUSH.defaultBlockState();
        BlockState mudBrickStairs = Blocks.MUD_BRICK_STAIRS.defaultBlockState();

        // Excavate the interior: dig down to create the sunken space
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = -halfLength; z <= halfLength; z++) {
                for (int y = floorY; y <= baseY + wallHeight + 1; y++) {
                    mutable.set(ox + x, y, oz + z);
                    world.setBlock(mutable, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }

        // Packed mud floor
        StructureHelper.fillBox(world,
            center.offset(-halfWidth, -depth, -halfLength),
            center.offset(halfWidth, -depth, halfLength),
            packedMud);

        // Mud brick retaining walls below ground, acacia log above ground
        for (int z = -halfLength; z <= halfLength; z++) {
            for (int y = floorY; y <= baseY + wallHeight; y++) {
                BlockState wallBlock = (y < baseY) ? mudBrick : logWall;
                world.setBlock(new BlockPos(ox - halfWidth, y, oz + z), wallBlock, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + halfWidth, y, oz + z), wallBlock, StructureHelper.SET_FLAGS);
            }
        }
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = floorY; y <= baseY + wallHeight; y++) {
                BlockState wallBlock = (y < baseY) ? mudBrick : logWall;
                world.setBlock(new BlockPos(ox + x, y, oz - halfLength), wallBlock, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + x, y, oz + halfLength), wallBlock, StructureHelper.SET_FLAGS);
            }
        }

        // Toron pegs on interior walls (acacia fence protruding inward)
        // Every 3 blocks horizontal, every 2 rows vertical
        for (int z = -halfLength + 3; z <= halfLength - 3; z += 3) {
            for (int y = floorY + 2; y <= baseY + wallHeight - 1; y += 2) {
                // East wall: pegs protrude west (inward)
                world.setBlock(new BlockPos(ox + halfWidth - 1, y, oz + z), fence, StructureHelper.SET_FLAGS);
                // West wall: pegs protrude east (inward)
                world.setBlock(new BlockPos(ox - halfWidth + 1, y, oz + z), fence, StructureHelper.SET_FLAGS);
            }
        }
        for (int x = -halfWidth + 3; x <= halfWidth - 3; x += 3) {
            for (int y = floorY + 2; y <= baseY + wallHeight - 1; y += 2) {
                // North wall toron
                world.setBlock(new BlockPos(ox + x, y, oz - halfLength + 1), fence, StructureHelper.SET_FLAGS);
            }
        }

        // HAY_BLOCK thatched roof (barely above ground level): single layer
        int roofY = baseY + wallHeight;
        StructureHelper.fillBox(world,
            center.offset(-halfWidth - 1, wallHeight, -halfLength - 1),
            center.offset(halfWidth + 1, wallHeight, halfLength + 1),
            hayBlock);

        // Ensure 3-block minimum headroom above floor everywhere inside
        StructureHelper.clearInterior(world,
            new BlockPos(ox - halfWidth + 1, floorY + 1, oz - halfLength + 1),
            new BlockPos(ox + halfWidth - 1, floorY + 3, oz + halfLength - 1));

        // Support pillars down the center (acacia logs, floor to roof)
        // Offset by 2 from bed positions to avoid placing beams on top of beds
        for (int z = -halfLength + 6; z <= halfLength - 4; z += 5) {
            for (int y = floorY + 1; y <= roofY; y++) {
                world.setBlock(new BlockPos(ox - halfWidth + 3, y, oz + z), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + halfWidth - 3, y, oz + z), logWall, StructureHelper.SET_FLAGS);
            }
        }

        // === Fire pits down the center ===
        for (int z = -halfLength + 6; z <= halfLength - 6; z += 8) {
            world.setBlock(new BlockPos(ox, floorY + 1, oz + z), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
            // Orange terracotta border around fire pits
            for (int[] off : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                world.setBlock(new BlockPos(ox + off[0], floorY + 1, oz + z + off[1]),
                    Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);
            }
            // Smoke hole in roof above each fire
            world.setBlock(new BlockPos(ox, roofY, oz + z), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            // Also clear adjacent blocks for larger smoke hole
            for (int[] adj : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                world.setBlock(new BlockPos(ox + adj[0], roofY, oz + z + adj[1]),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
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
            world.setBlock(new BlockPos(ox + x, floorY + 1, oz - halfLength + 7),
                mudBrickStairs.setValue(StairBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }
        // Throne sits ON the platform (platform top is floorY+2, throne at floorY+3)
        world.setBlock(new BlockPos(ox, floorY + 3, oz - halfLength + 3),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Brown terracotta behind throne
        world.setBlock(new BlockPos(ox, floorY + 3, oz - halfLength + 2),
            Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, floorY + 4, oz - halfLength + 2),
            Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN).defaultBlockState(), StructureHelper.SET_FLAGS);
        // Terracotta accents flanking throne
        world.setBlock(new BlockPos(ox - 2, floorY + 3, oz - halfLength + 3),
            Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 2, floorY + 3, oz - halfLength + 3),
            Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState(), StructureHelper.SET_FLAGS);

        // Orange carpet runner from throne down the hall
        for (int z = -halfLength + 7; z <= 0; z++) {
            world.setBlock(new BlockPos(ox, floorY + 1, oz + z),
                palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // === Sleeping alcoves along east/west walls ===
        for (int z = -halfLength + 4; z <= halfLength - 8; z += 4) {
            // East side beds
            world.setBlock(new BlockPos(ox + halfWidth - 2, floorY + 1, oz + z), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth - 3, floorY + 1, oz + z), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            // West side beds
            world.setBlock(new BlockPos(ox - halfWidth + 2, floorY + 1, oz + z), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - halfWidth + 3, floorY + 1, oz + z), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            // Decorated pot between alcoves
            if (z % 8 == 0) {
                world.setBlock(new BlockPos(ox + halfWidth - 2, floorY + 1, oz + z + 2),
                    Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // === Storage area at south end ===
        for (int x = -3; x <= 3; x += 2) {
            world.setBlock(new BlockPos(ox + x, floorY + 1, oz + halfLength - 3),
                Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, floorY + 2, oz + halfLength - 3),
                Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        StructureHelper.placeChest(world, new BlockPos(ox - 5, floorY + 1, oz + halfLength - 2),
            Direction.NORTH, BuiltInLootTables.VILLAGE_SAVANNA_HOUSE);
        StructureHelper.placeChest(world, new BlockPos(ox + 5, floorY + 1, oz + halfLength - 2),
            Direction.NORTH, BuiltInLootTables.VILLAGE_SAVANNA_HOUSE);
        // Hay bales in storage
        world.setBlock(new BlockPos(ox - 4, floorY + 1, oz + halfLength - 4), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - 4, floorY + 2, oz + halfLength - 4), hayBlock, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 4, floorY + 1, oz + halfLength - 4), hayBlock, StructureHelper.SET_FLAGS);

        // === Entrance ramp: south end, MUD_BRICK_STAIRS descending from surface ===
        // Stairs going south (descending as you walk north into the hall) face SOUTH
        for (int i = 0; i <= depth; i++) {
            int stairZ = halfLength + 2 + (depth - i); // furthest south at surface, closest at floor
            int stairY = baseY - i;
            // MUD_BRICK_STAIRS: descent going north, so facing SOUTH (you face south walking up)
            for (int x = -2; x <= 2; x++) {
                world.setBlock(new BlockPos(ox + x, stairY, oz + stairZ),
                    mudBrickStairs.setValue(StairBlock.FACING, Direction.SOUTH),
                    StructureHelper.SET_FLAGS);
            }
            // Clear 3-block headroom above stairs
            for (int x = -2; x <= 2; x++) {
                for (int dy = 1; dy <= 3; dy++) {
                    world.setBlock(new BlockPos(ox + x, stairY + dy, oz + stairZ),
                        Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
            // Mud brick retaining walls along stairway
            for (int dy = stairY; dy <= baseY + 1; dy++) {
                world.setBlock(new BlockPos(ox - 3, dy, oz + stairZ), mudBrick, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + 3, dy, oz + stairZ), mudBrick, StructureHelper.SET_FLAGS);
            }
        }
        // Open south wall where stairs meet the interior (clear doorway, 4 blocks high for headroom)
        for (int x = -2; x <= 2; x++) {
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                world.setBlock(new BlockPos(ox + x, y, oz + halfLength), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        Openings.door(world, new BlockPos(ox - 1, floorY + 1, oz + halfLength), Direction.NORTH,
            palette.door.defaultBlockState(), null, palette.getFloorState(), true);
        // Ensure 2+ blocks clearance outside entrance ramp (surface level)
        for (int x = -2; x <= 2; x++) {
            for (int dz = 1; dz <= 2; dz++) {
                world.setBlock(new BlockPos(ox + x, baseY + 1, oz + halfLength + depth + 2 + dz),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + x, baseY + 2, oz + halfLength + depth + 2 + dz),
                    Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Wall torches along the interior: on valid support blocks (mud brick walls)
        for (int z = -halfLength + 2; z <= halfLength - 2; z += 3) {
            // East wall torch: facing EAST means the torch is on the block to its WEST (the wall)
            world.setBlock(new BlockPos(ox - halfWidth + 1, floorY + 3, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth - 1, floorY + 3, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // Bell near entrance on fence post
        world.setBlock(new BlockPos(ox + 4, floorY + 1, oz + halfLength - 5), fence, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 4, floorY + 2, oz + halfLength - 5), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Acacia palisade around the surface footprint with dead bush thorns
        int fenceRadius = Math.max(halfLength, halfWidth) + 2;
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int fx = ox + (int) Math.round(fenceRadius * Math.cos(rad));
            int fz = oz + (int) Math.round(fenceRadius * Math.sin(rad));
            world.setBlock(new BlockPos(fx, baseY, fz), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(fx, baseY + 1, fz), fence, StructureHelper.SET_FLAGS);
        }
        // Dead bush thorns outside palisade: support block at baseY-1 to replace terrain
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int bx = ox + (int) Math.round((fenceRadius + 1) * Math.cos(rad));
            int bz = oz + (int) Math.round((fenceRadius + 1) * Math.sin(rad));
            world.setBlock(new BlockPos(bx, baseY - 1, bz), coarseDirt, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(bx, baseY, bz), deadBush, StructureHelper.SET_FLAGS);
        }

        // Coarse dirt around surface footprint: at baseY-1 to replace terrain, not raise it
        for (int x = -halfWidth - 3; x <= halfWidth + 3; x++) {
            for (int z = -halfLength - 3; z <= halfLength + 3; z++) {
                int ax = ox + x;
                int az = oz + z;
                if (Math.abs(x) > halfWidth || Math.abs(z) > halfLength) {
                    world.setBlock(new BlockPos(ax, baseY - 1, az), coarseDirt, StructureHelper.SET_FLAGS);
                }
            }
        }

        placeJigsawConnectors(world, center, fenceRadius);

        VillageCastles.LOGGER.debug("Savanna sunken longhouse generation complete!");
        return new CastleBounds(
            center.offset(-fenceRadius - 2, -depth - 1, -fenceRadius - 2),
            center.offset(fenceRadius + 2, wallHeight + 5, fenceRadius + 2)
        );
    }

    /**
     * SNOWY MEDIUM: Winter castle.
     * Stone brick and spruce timber. Built for the cold, not from ice.
     * Steep spruce roof to shed snow, massive hearth, enclosed courtyard with
     * covered wooden walkway. Corner watchtower (one, asymmetric). Iron bar windows.
     * Gray stone, steep pitch, small openings. Scottish highland / Norwegian fortress.
     */
    private CastleBounds generateWinterCastle(ServerLevel world, BlockPos center, int radius) {
        int halfWidth = 12;
        int halfDepth = 10;
        int wallHeight = 7;
        int roofPeak = 5;
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        prepareGround(world, center, Math.max(halfWidth, halfDepth) + 2);

        BlockState stoneBrick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossyStoneBrick = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState spruceLog = Blocks.SPRUCE_LOG.defaultBlockState();
        BlockState spruceSlab = Blocks.SPRUCE_SLAB.defaultBlockState();
        BlockState spruceStairs = Blocks.SPRUCE_STAIRS.defaultBlockState();
        BlockState spruceFence = Blocks.SPRUCE_FENCE.defaultBlockState();

        // === STONE BRICK WALLS: main rectangular keep ===
        StructureHelper.fillBox(world,
            center.offset(-halfWidth, 0, -halfDepth),
            center.offset(halfWidth, wallHeight, halfDepth),
            stoneBrick);
        // Mossy stone accent at base (damp cold)
        StructureHelper.fillBox(world,
            center.offset(-halfWidth, 0, -halfDepth),
            center.offset(halfWidth, 1, halfDepth),
            mossyStoneBrick);
        // Hollow interior
        StructureHelper.clearInterior(world,
            center.offset(-halfWidth + 1, 1, -halfDepth + 1),
            center.offset(halfWidth - 1, wallHeight, halfDepth - 1));

        // Spruce plank floor
        StructureHelper.fillFloor(world,
            center.offset(-halfWidth + 1, 0, -halfDepth + 1),
            center.offset(halfWidth - 1, 0, halfDepth - 1),
            baseY, sprucePlanks);

        // === INTERNAL DIVISION: great hall (south) + courtyard (north) ===
        int dividerZ = oz - 2; // wall separating great hall from courtyard
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            for (int y = 1; y <= wallHeight - 1; y++) {
                world.setBlock(new BlockPos(ox + x, baseY + y, dividerZ), stoneBrick, StructureHelper.SET_FLAGS);
            }
        }
        // Doorway through divider (2 wide)
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlock(new BlockPos(ox + x, baseY + y, dividerZ), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
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
                    world.setBlock(new BlockPos(ox + x, baseY + wallHeight + y, roofStartZ + y),
                        sprucePlanks, StructureHelper.SET_FLAGS);
                }
                // South slope (from south wall going up)
                if (roofEndZ - y >= roofStartZ) {
                    world.setBlock(new BlockPos(ox + x, baseY + wallHeight + y, roofEndZ - y),
                        sprucePlanks, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Ridge cap
        int ridgeZ = (roofStartZ + roofEndZ) / 2;
        int ridgeHeight = roofSpan / 2 + 1;
        for (int x = -halfWidth - 1; x <= halfWidth + 1; x++) {
            world.setBlock(new BlockPos(ox + x, baseY + wallHeight + ridgeHeight, ridgeZ),
                spruceLog, StructureHelper.SET_FLAGS);
        }

        // Courtyard (north half) stays open-air: no roof
        // But add a covered walkway around the courtyard edges
        int walkwayY = baseY + wallHeight;
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            world.setBlock(new BlockPos(ox + x, walkwayY, oz - halfDepth + 1), spruceSlab, StructureHelper.SET_FLAGS);
        }
        for (int z = oz - halfDepth + 1; z <= dividerZ - 1; z++) {
            world.setBlock(new BlockPos(ox - halfWidth + 1, walkwayY, z), spruceSlab, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth - 1, walkwayY, z), spruceSlab, StructureHelper.SET_FLAGS);
        }

        // === CORNER WATCHTOWER: NE corner, asymmetric ===
        int towerHW = 3;
        int towerHeight = wallHeight + 6;
        BlockPos towerPos = center.offset(halfWidth - towerHW, 0, -halfDepth + towerHW);
        StructureHelper.fillBox(world,
            towerPos.offset(-towerHW, 0, -towerHW),
            towerPos.offset(towerHW, towerHeight, towerHW),
            stoneBrick);
        StructureHelper.clearInterior(world,
            towerPos.offset(-towerHW + 1, 1, -towerHW + 1),
            towerPos.offset(towerHW - 1, towerHeight - 1, towerHW - 1));
        // Tower doorway on west wall (connects to courtyard)
        for (int y = 1; y <= 3; y++) {
            world.setBlock(new BlockPos(towerPos.getX() - towerHW, baseY + y, towerPos.getZ()),
                Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        // Tower floor
        StructureHelper.fillFloor(world,
            towerPos.offset(-towerHW + 1, 0, -towerHW + 1),
            towerPos.offset(towerHW - 1, 0, towerHW - 1),
            baseY, sprucePlanks);
        // Upper floor at wall height
        StructureHelper.fillFloor(world,
            towerPos.offset(-towerHW + 1, 0, -towerHW + 1),
            towerPos.offset(towerHW - 1, 0, towerHW - 1),
            baseY + wallHeight, sprucePlanks);
        // Steep conical roof on tower
        for (int y = 0; y <= towerHW + 2; y++) {
            int r = towerHW + 1 - y;
            if (r < 0) break;
            StructureHelper.fillFloor(world,
                towerPos.offset(-r, 0, -r), towerPos.offset(r, 0, r),
                baseY + towerHeight + y, sprucePlanks);
        }
        // Ladder inside tower
        for (int y = 2; y <= towerHeight - 1; y++) {
            world.setBlock(new BlockPos(towerPos.getX() + towerHW - 1, baseY + y, towerPos.getZ()),
                Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST),
                StructureHelper.SET_FLAGS);
        }
        // Tower lookout windows (iron bars)
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.EAST}) {
            int wx = towerPos.getX() + (dir == Direction.EAST ? towerHW : 0);
            int wz = towerPos.getZ() + (dir == Direction.NORTH ? -towerHW : 0);
            world.setBlock(new BlockPos(wx, baseY + towerHeight - 2, wz),
                Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        // Bed in tower upper room (guard quarters)
        world.setBlock(new BlockPos(towerPos.getX() - 1, baseY + wallHeight + 1, towerPos.getZ() - 1),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(towerPos.getX() - 1, baseY + wallHeight + 1, towerPos.getZ()),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // === GREAT HALL FURNISHING (south of divider) ===
        int hallFloor = baseY + 1; // furniture on the spruce plank floor at baseY

        // Massive double hearth: 2 campfires on stone brick base, against east wall
        world.setBlock(new BlockPos(ox + halfWidth - 2, baseY, oz + 2), stoneBrick, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + halfWidth - 2, baseY, oz + 3), stoneBrick, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + halfWidth - 2, hallFloor, oz + 2), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + halfWidth - 2, hallFloor, oz + 3), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Chimney above hearth (stone brick column through roof)
        for (int y = wallHeight; y <= wallHeight + ridgeHeight + 2; y++) {
            world.setBlock(new BlockPos(ox + halfWidth - 2, baseY + y, oz + 2), stoneBrick, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth - 2, baseY + y, oz + 3), stoneBrick, StructureHelper.SET_FLAGS);
        }

        // Long table (fence + slab)
        for (int z = 0; z <= 4; z++) {
            world.setBlock(new BlockPos(ox, baseY, oz + z), spruceFence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox, hallFloor, oz + z), spruceSlab, StructureHelper.SET_FLAGS);
        }
        // Benches along table
        for (int z = 0; z <= 4; z += 2) {
            world.setBlock(new BlockPos(ox - 1, hallFloor, oz + z),
                spruceStairs.setValue(StairBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 1, hallFloor, oz + z),
                spruceStairs.setValue(StairBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // Master's seat at head of table (north end, facing south down the hall)
        world.setBlock(new BlockPos(ox, hallFloor, dividerZ + 1),
            spruceStairs.setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Master bed: west side of great hall
        world.setBlock(new BlockPos(ox - halfWidth + 2, hallFloor, oz + halfDepth - 2),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + 2, hallFloor, oz + halfDepth - 3),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        // Second bed
        world.setBlock(new BlockPos(ox - halfWidth + 4, hallFloor, oz + halfDepth - 2),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + 4, hallFloor, oz + halfDepth - 3),
            palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);

        // Chests along west wall
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 2, hallFloor, dividerZ + 2),
            Direction.EAST, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);
        StructureHelper.placeChest(world, new BlockPos(ox - halfWidth + 2, hallFloor, oz + 2),
            Direction.EAST, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);

        // Carpet runner
        for (int z = dividerZ + 2; z <= oz + halfDepth - 1; z++) {
            // Skip the table area (z = oz to oz+4)
            if (z >= oz && z <= oz + 4) continue;
            world.setBlock(new BlockPos(ox, hallFloor, z),
                palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Iron bar windows (small, defensive)
        for (int z : new int[]{oz + 1, oz + halfDepth - 2}) {
            world.setBlock(new BlockPos(ox - halfWidth, baseY + 3, z), Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + halfWidth, baseY + 3, z), Blocks.IRON_BARS.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Wall torches in great hall
        world.setBlock(new BlockPos(ox - halfWidth + 1, baseY + 3, oz + 1),
            Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - halfWidth + 1, baseY + 3, oz + halfDepth - 2),
            Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + halfWidth - 1, baseY + 3, oz + 1),
            Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + halfWidth - 1, baseY + 3, oz + halfDepth - 2),
            Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - 4, baseY + 3, dividerZ + 1),
            Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 4, baseY + 3, dividerZ + 1),
            Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Lantern in courtyard
        world.setBlock(center.offset(0, 1, -halfDepth + 3),
            Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);

        // === ENTRANCE: south wall ===
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlock(new BlockPos(ox + x, baseY + y, oz + halfDepth), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        Openings.door(world, new BlockPos(ox - 1, baseY + 1, oz + halfDepth), Direction.NORTH,
            palette.door.defaultBlockState(), null, sprucePlanks, true);

        // Courtyard entrance: north wall
        for (int x = -1; x <= 0; x++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlock(new BlockPos(ox + x, baseY + y, oz - halfDepth), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        Openings.door(world, new BlockPos(ox - 1, baseY + 1, oz - halfDepth), Direction.SOUTH,
            palette.door.defaultBlockState(), null, sprucePlanks, true);

        // Bell in courtyard
        world.setBlock(center.offset(-3, 0, -halfDepth + 3), spruceFence, StructureHelper.SET_FLAGS);
        world.setBlock(center.offset(-3, 1, -halfDepth + 3), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        placeJigsawConnectors(world, center, Math.max(halfWidth, halfDepth));

        VillageCastles.LOGGER.debug("Winter castle generation complete!");
        return new CastleBounds(
            center.offset(-halfWidth - 2, 0, -halfDepth - 2),
            center.offset(halfWidth + 2, towerHeight + towerHW + 5, halfDepth + 2)
        );
    }

    private CastleBounds generateMediumFort(ServerLevel world, BlockPos center, int radius,
                                              int keepHalfWidth, int keepHalfDepth) {
        // The bailey sits at grade. Plains used to get a motte: a 14-block earthwork whose slope
        // ran out to radius+22, with the whole castle raised on top of it. Baked into a template
        // that is a landform, not a building, and it showed: plains/castle_medium exported at
        // 99x99 and 71% dirt, coarse dirt and grass, and stamped that hill into whatever terrain
        // it landed on. A castle template carries the castle; CastleGroundsPiece meets the ground.
        BlockPos originalCenter = center;
        BlockPos buildCenter = center;
        prepareGround(world, buildCenter, radius);

        // 2. Calculate key positions (all relative to buildCenter)
        BlockPos southGatePos = buildCenter.south(radius - GateGenerator.getDepth() / 2);
        int towerOffset = towerGenerator.getAdjustedRadius(TowerGenerator.TowerType.CORNER);
        BlockPos nwTower = buildCenter.offset(-radius + towerOffset, 0, -radius + towerOffset);
        BlockPos neTower = buildCenter.offset(radius - towerOffset, 0, -radius + towerOffset);
        BlockPos swTower = buildCenter.offset(-radius + towerOffset, 0, radius - towerOffset);
        BlockPos seTower = buildCenter.offset(radius - towerOffset, 0, radius - towerOffset);

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

        VillageCastles.LOGGER.debug("Standard fort generation complete!");

        int xzCapture = radius + 2;
        return new CastleBounds(
            originalCenter.offset(-xzCapture, 0, -xzCapture),
            buildCenter.offset(xzCapture, keepHeight + 10, xzCapture)
        );
    }

    /**
     * LARGE: Grand Castle.
     * Everything medium has, PLUS wall towers on east/west, back gate on north,
     * and stables + barracks in courtyard.
     */
    /**
     * LARGE: Biome-specific castle complex.
     * The showpiece structures. Each biome's large castle is a landmark.
     */
    private CastleBounds generateLarge(ServerLevel world, BlockPos center, int radius,
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
     * SNOWY LARGE: Ancient Ice Citadel.
     *
     * A hexagonal fortress of six spires connected by thick textured ice walls.
     * The north spire is the largest: hollow, with a wide spiral staircase
     * going up and down from a mid-level entrance reached by a grand bridge
     * from the courtyard center. Built for beings twice human size.
     *
     * The villagers adapted it: cobblestone stairs bridging giant 2-block rises,
     * spruce scaffolding climbing the spire exteriors, wooden platforms near the
     * tips, campfire camps huddled in corners of the vast courtyard.
     *
     * Texture: blue ice (structural), packed ice (variation), snow block (accents),
     * regular ice (translucent windows). Not monochrome: intentionally textured.
     *
     * Below the big spire: a secret shrine chamber with a diamond block.
     */
    private CastleBounds generateIcePalace(ServerLevel world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX(), oz = center.getZ();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        // Block palette: textured, not monochrome
        BlockState blueIce = Blocks.BLUE_ICE.defaultBlockState();
        BlockState packedIce = Blocks.PACKED_ICE.defaultBlockState();
        BlockState snowBlock = Blocks.SNOW_BLOCK.defaultBlockState();
        // No regular ICE: it melts near light sources
        BlockState seaLantern = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState spruce = palette.getPlanksState();
        BlockState fence = palette.getFenceState();
        BlockState log = palette.log.defaultBlockState();
        BlockState cobbleStairs = Blocks.COBBLESTONE_STAIRS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // Dimensions. The citadel still reads as the tallest thing for miles, but it is a
        // building rather than a landform: the previous 34-radius hexagon around an 85-block
        // spire exported as 79x102x79, taller than the world is deep in a lot of terrain and
        // impossible to site beside a village. Kept: the hex plan, the dominant central spire,
        // the flanking spires, the bridge. Changed: everything is roughly 40% of its old scale.
        int hexR = 22;             // hexagon vertex radius from center
        int wallH = 10;            // wall height between spires
        int wallThick = 3;         // wall thickness
        int smallSpireR = 4;       // regular spire base radius
        int smallSpireH = 18;      // regular spire height
        int bigSpireR = 12;        // dominates the interior
        int bigSpireH = 34;        // the defining silhouette
        int bigSpireShell = 2;     // shell walls
        // Big spire TOUCHING the back (north) wall
        int bigSX = ox;
        int bigSZ = oz - hexR + bigSpireR; // north edge meets the north wall
        int bridgeY = baseY + bigSpireH / 3; // entrance 1/3 up (~22 blocks)
        int dungeonDepth = Math.min(10, baseY + 63);

        // ICE-APPROPRIATE GROUND PREP: no cobblestone foundation
        // Floor at baseY-1 so structures start flush at baseY (ground level)
        baseY = baseY - 1; // shift down 1 so the structure sits AT ground level
        {
            int gr = hexR + 5;
            BlockPos.MutableBlockPos gm = new BlockPos.MutableBlockPos();
            for (int gx = -gr; gx <= gr; gx++) {
                for (int gz = -gr; gz <= gr; gz++) {
                    // Fill below with packed ice (natural-looking)
                    for (int gy = -3; gy <= -1; gy++) {
                        gm.set(ox + gx, baseY + gy, oz + gz);
                        world.setBlock(gm, packedIce, StructureHelper.SET_FLAGS);
                    }
                    // Snow floor at baseY
                    gm.set(ox + gx, baseY, oz + gz);
                    world.setBlock(gm, snowBlock, StructureHelper.SET_FLAGS);
                    // Clear air above (must clear higher than tallest spire)
                    for (int gy = 1; gy <= 100; gy++) {
                        gm.set(ox + gx, baseY + gy, oz + gz);
                        if (!world.getBlockState(gm).isAir()) {
                            world.setBlock(gm, air, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
        }

        // Hexagon vertex positions: rotated 30° so NO spire is dead south
        // Entrance passes between two spires, not through one
        // Vertex 0 is still north (big spire position, though spire is offset)
        int[][] verts = new int[6][2];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(90 + i * 60 + 30); // 30° rotation
            verts[i][0] = (int) Math.round(hexR * Math.cos(angle));
            verts[i][1] = (int) Math.round(-hexR * Math.sin(angle)); // -sin because MC north = -Z
        }

        // Deterministic texture function: mixed ice based on position
        // 60% blue ice, 30% packed ice, 10% snow block
        // NO regular ice: it melts near light sources and turns to water
        java.util.function.Function<BlockPos, BlockState> iceTexture = (BlockPos pos) -> {
            int hash = ((pos.getX() * 31 + pos.getY() * 17 + pos.getZ() * 7) & 0x1F);
            if (hash < 19) return blueIce;
            if (hash < 29) return packedIce;
            return snowBlock;
        };

        // ============================================================
        // SECTION 1: SIX SPIRES
        // ============================================================

        // === 5 SPIRES at hex vertices 1-5 ===
        // Vertex 0 (NW) skipped: overlaps with big spire
        // Vertices 1 (W) and 5 (NE) flank the big spire: taller, larger, no crow's nests
        for (int si = 1; si < 6; si++) {
            int sx = ox + verts[si][0];
            int sz = oz + verts[si][1];

            // Back spires (1=W, 5=NE) flank the big spire: 50% larger and taller
            boolean isBackSpire = (si == 1 || si == 5);
            int thisSpireR = isBackSpire ? (int)(smallSpireR * 1.5) : smallSpireR; // 7 vs 5
            int thisSpireH = isBackSpire ? (int)(smallSpireH * 1.5) : smallSpireH; // 48 vs 32

            // Tapered solid spire
            for (int y = 0; y < thisSpireH; y++) {
                double taper = 1.0 - ((double) y / (thisSpireH + 8));
                int r = Math.max(1, (int)(thisSpireR * taper));
                for (int bx = -r; bx <= r; bx++) {
                    for (int bz = -r; bz <= r; bz++) {
                        if (bx * bx + bz * bz <= r * r) {
                            BlockPos bp = new BlockPos(sx + bx, baseY + y, sz + bz);
                            world.setBlock(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Tip
            for (int y = 0; y <= (isBackSpire ? 6 : 4); y++) {
                world.setBlock(new BlockPos(sx, baseY + thisSpireH + y, sz), blueIce, StructureHelper.SET_FLAGS);
            }

            // Sea lantern windows
            for (int y = 6; y < thisSpireH - 5; y += 6) {
                for (int angle = 0; angle < 360; angle += 90) {
                    double rad = Math.toRadians(angle);
                    int r = Math.max(1, (int)(thisSpireR * (1.0 - (double) y / (thisSpireH + 8))));
                    int wx = (int) Math.round(r * Math.cos(rad));
                    int wz = (int) Math.round(r * Math.sin(rad));
                    world.setBlock(new BlockPos(sx + wx, baseY + y, sz + wz), seaLantern, StructureHelper.SET_FLAGS);
                }
            }

            // Crow's nests + ladders ONLY on front/side spires (not back ones)
            if (!isBackSpire) {
                int nestHeight = (int)(thisSpireH * 0.75);
                int nestY = baseY + nestHeight;
                int spireRAtNest = Math.max(1, (int)(thisSpireR * (1.0 - (double) nestHeight / (thisSpireH + 8))));
                int nestR = spireRAtNest + 2;
                // Circular platform
                for (int px = -nestR; px <= nestR; px++) {
                    for (int pz = -nestR; pz <= nestR; pz++) {
                        int distSq = px * px + pz * pz;
                        if (distSq <= nestR * nestR && distSq > (nestR - 2) * (nestR - 2)) {
                            world.setBlock(new BlockPos(sx + px, nestY, sz + pz), spruce, StructureHelper.SET_FLAGS);
                            if (distSq > (nestR - 1) * (nestR - 1)) {
                                world.setBlock(new BlockPos(sx + px, nestY + 1, sz + pz), fence, StructureHelper.SET_FLAGS);
                            }
                        }
                    }
                }
                // Ladder on courtyard-facing side
                double towardCenterX = -verts[si][0];
                double towardCenterZ = -verts[si][1];
                double tcLen = Math.sqrt(towardCenterX * towardCenterX + towardCenterZ * towardCenterZ);
                int ladderDX = (int) Math.round(towardCenterX / tcLen * thisSpireR);
                int ladderDZ = (int) Math.round(towardCenterZ / tcLen * thisSpireR);
                Direction ladderFacing;
                if (Math.abs(towardCenterX) > Math.abs(towardCenterZ)) {
                    ladderFacing = towardCenterX > 0 ? Direction.EAST : Direction.WEST;
                } else {
                    ladderFacing = towardCenterZ > 0 ? Direction.SOUTH : Direction.NORTH;
                }
                for (int y = 1; y <= nestHeight; y++) {
                    world.setBlock(new BlockPos(sx + ladderDX, baseY + y, sz + ladderDZ),
                        Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, ladderFacing),
                        StructureHelper.SET_FLAGS);
                }
                world.setBlock(new BlockPos(sx + nestR - 1, nestY + 1, sz),
                    Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
            }
        }

        // === MASSIVE BIG SPIRE: hollow, with solid exterior spiral bands ===
        // THREE spiral ledges wrap the exterior like the interior staircase but outside
        // Each ledge is 2 blocks wide, 1 block thick shelf protruding from the surface
        // They spiral at the same rate as the interior stairs for visual coherence
        for (int y = 0; y < bigSpireH; y++) {
            double taper = 1.0 - ((double) y / (bigSpireH + 10));
            int baseR = Math.max(2, (int)(bigSpireR * taper));
            int ir = baseR - bigSpireShell;

            for (int bx = -baseR; bx <= baseR; bx++) {
                for (int bz = -baseR; bz <= baseR; bz++) {
                    int distSq = bx * bx + bz * bz;
                    if (distSq > baseR * baseR) continue;
                    // Hollow: skip interior
                    if (y > 0 && ir > 2 && distSq < ir * ir) continue;
                    BlockPos bp = new BlockPos(bigSX + bx, baseY + y, bigSZ + bz);
                    // ALL body blocks use the mixed iceTexture
                    world.setBlock(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                }
            }

            // EXTERIOR SPIRAL BANDS: 3 bands, 120° apart, wax on from base
            double heightRatio = (double) y / bigSpireH;
            double waxOn = heightRatio < 0.1 ? heightRatio / 0.1 : 1.0; // ramp up over first 10%
            if (waxOn > 0.05) { // skip the very bottom
                double twist = Math.toRadians(y * 10); // same rate as interior stairs
                double[] bandAngles = {twist, twist + 2.094, twist + 4.189}; // 120° apart

                for (double bandAngle : bandAngles) {
                    // Walk the arc in block-sized steps rather than sampling three fixed angles.
                    // At a 24-block radius those three samples sat two or more blocks apart, so
                    // the band came out as a dotted line of detached snow: 127 of the ice palace's
                    // blocks had air on all six sides. The step is sized from the radius, so the
                    // run stays contiguous however wide the spire is at this course.
                    for (int depth = 0; depth <= 1; depth++) {
                        int shelfR = baseR + 1 + depth;
                        double step = 1.0 / Math.max(1, shelfR);
                        for (double a = -0.15; a <= 0.15 + 1e-9; a += step) {
                            double offsetAngle = bandAngle + a;
                            int sx2 = bigSX + (int) Math.round(shelfR * Math.cos(offsetAngle));
                            int sz2 = bigSZ + (int) Math.round(shelfR * Math.sin(offsetAngle));
                            world.setBlock(new BlockPos(sx2, baseY + y, sz2), snowBlock, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
        }
        // Tip + end rod
        for (int y = 0; y <= 5; y++) {
            world.setBlock(new BlockPos(bigSX, baseY + bigSpireH + y, bigSZ), blueIce, StructureHelper.SET_FLAGS);
        }
        // Tip is sea lantern: visible beacon, no end rods
        world.setBlock(new BlockPos(bigSX, baseY + bigSpireH + 6, bigSZ), seaLantern, StructureHelper.SET_FLAGS);

        // Sea lanterns spiraling inside the big spire (visible looking up)
        for (int y = 5; y < bigSpireH - 5; y += 3) {
            double angle = Math.toRadians(y * 25);
            int r = Math.max(2, (int)(bigSpireR * (1.0 - (double) y / (bigSpireH + 10)))) - bigSpireShell;
            if (r < 2) continue;
            int lx = (int) Math.round((r - 1) * Math.cos(angle));
            int lz = (int) Math.round((r - 1) * Math.sin(angle));
            world.setBlock(new BlockPos(bigSX + lx, baseY + y, bigSZ + lz), seaLantern, StructureHelper.SET_FLAGS);
        }
        // Sea lantern at peak
        world.setBlock(new BlockPos(bigSX, baseY + bigSpireH - 2, bigSZ), seaLantern, StructureHelper.SET_FLAGS);

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
                        world.setBlock(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                    }
                    // Snow on top
                    world.setBlock(new BlockPos(bx, baseY + localH, bz), snowBlock, StructureHelper.SET_FLAGS);
                }
                // No wood in the ancient walls: pure ice, varied and organic
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
                    world.setBlock(m, packedIce, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ============================================================
        // SECTION 4: GRAND BRIDGE + ICE SCULPTURES
        // ============================================================

        // Bridge from south courtyard up to the spire entrance at 1/3 height
        // The spire tapers: calculate where the south face actually IS at bridgeY
        double taperAtBridge = 1.0 - ((double)(bridgeY - baseY) / (bigSpireH + 10));
        int spireRAtBridge = Math.max(2, (int)(bigSpireR * taperAtBridge));
        int spireFaceAtBridge = bigSZ + spireRAtBridge; // actual south face at entrance height

        int bridgeHalfW = 3; // 7 blocks wide (giant scale)
        int bridgeStartZ = oz + hexR; // start at the south hex extent (ground level)
        int bridgeEndZ = spireFaceAtBridge + 1; // just outside the tapered spire face
        int bridgeLen = bridgeStartZ - bridgeEndZ;
        if (bridgeLen <= 0) bridgeLen = 1;

        VillageCastles.LOGGER.debug("Bridge: startZ={} endZ={} len={} bridgeY={}", bridgeStartZ, bridgeEndZ, bridgeLen, bridgeY);

        for (int bz = bridgeEndZ; bz <= bridgeStartZ; bz++) {
            // Y rises linearly from baseY (at south/center) to bridgeY (at north/spire)
            double progress = (double)(bridgeStartZ - bz) / bridgeLen;
            int by = baseY + (int)(progress * (bridgeY - baseY));

            for (int bx = -bridgeHalfW; bx <= bridgeHalfW; bx++) {
                BlockPos bp = new BlockPos(ox + bx, by, bz);
                world.setBlock(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                // Giant-scale railings (2 blocks tall, packed ice)
                if (Math.abs(bx) == bridgeHalfW) {
                    world.setBlock(new BlockPos(ox + bx, by + 1, bz), packedIce, StructureHelper.SET_FLAGS);
                    world.setBlock(new BlockPos(ox + bx, by + 2, bz), packedIce, StructureHelper.SET_FLAGS);
                }
            }
            // Clear headroom above bridge surface
            for (int bx = -bridgeHalfW + 1; bx < bridgeHalfW; bx++) {
                for (int h = 1; h <= 5; h++) {
                    world.setBlock(new BlockPos(ox + bx, by + h, bz), air, StructureHelper.SET_FLAGS);
                }
            }

            // Clean ice bridge: no cobble adaptations on this ancient structure
        }

        // Open the big spire wall at bridge entrance height: grand doorway
        // Use the TAPERED south face position, not ground-level
        int doorZ = spireFaceAtBridge;
        for (int bx = -3; bx <= 3; bx++) { // 7 wide (giant scale)
            for (int y = bridgeY - 1; y <= bridgeY + 5; y++) { // 7 tall
                for (int dz = 0; dz < bigSpireShell + 1; dz++) {
                    world.setBlock(new BlockPos(bigSX + bx, y, doorZ - dz), air, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Also open a ground-level entrance (villagers walk in from the courtyard)
        int groundDoorZ = bigSZ + bigSpireR; // ground-level south face (full radius)
        for (int bx = -2; bx <= 2; bx++) {
            for (int y = 1; y <= 4; y++) {
                for (int dz = 0; dz < bigSpireShell + 1; dz++) {
                    world.setBlock(new BlockPos(bigSX + bx, baseY + y, groundDoorZ - dz), air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Hang the citadel's ground door. The spire had two openings and no door in either, which
        // is also a cold hall: an open five-block arch is not a way into an ice keep.
        Openings.door(world, new BlockPos(bigSX - 1, baseY + 1, groundDoorZ), Direction.NORTH,
            palette.door.defaultBlockState(), null, packedIce, true);
        for (int bx : new int[]{-2, 2}) {
            for (int y = 1; y <= 4; y++) {
                world.setBlock(new BlockPos(bigSX + bx, baseY + y, groundDoorZ), packedIce, StructureHelper.SET_FLAGS);
            }
        }
        for (int y = 1; y <= 2; y++) {
            world.setBlock(new BlockPos(bigSX + 1, baseY + y, groundDoorZ), packedIce, StructureHelper.SET_FLAGS);
        }

        // Mini sentinel spires flanking the bridge
        // Tapered ice spires matching the citadel's architectural language
        for (int side : new int[]{-1, 1}) {
            int sculX = ox + side * (bridgeHalfW + 4);
            int sculZ = (bridgeStartZ + bridgeEndZ) / 2;
            int miniR = 3;
            int miniH = 18;
            for (int y = 0; y < miniH; y++) {
                double taper = 1.0 - ((double) y / (miniH + 4));
                int r = Math.max(1, (int)(miniR * taper));
                for (int bx = -r; bx <= r; bx++) {
                    for (int bz = -r; bz <= r; bz++) {
                        if (bx * bx + bz * bz <= r * r) {
                            BlockPos bp = new BlockPos(sculX + bx, baseY + y, sculZ + bz);
                            world.setBlock(bp, iceTexture.apply(bp), StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Tip
            for (int y = 0; y <= 2; y++) {
                world.setBlock(new BlockPos(sculX, baseY + miniH + y, sculZ), blueIce, StructureHelper.SET_FLAGS);
            }
            // Sea lantern window at mid-height
            world.setBlock(new BlockPos(sculX, baseY + miniH / 2, sculZ + miniR), seaLantern, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(sculX, baseY + miniH / 2, sculZ - miniR), seaLantern, StructureHelper.SET_FLAGS);
        }

        // ============================================================
        // SECTION 5: BIG SPIRE INTERIOR, spiral staircase
        // ============================================================

        // Clear the hollow interior (bigSX/bigSZ already defined above)
        for (int y = 1; y < bigSpireH - 3; y++) {
            int r = Math.max(1, (int)(bigSpireR * (1.0 - (double) y / (bigSpireH + 8))));
            int ir = r - 2;
            if (ir < 2) continue;
            for (int bx = -ir; bx <= ir; bx++) {
                for (int bz = -ir; bz <= ir; bz++) {
                    if (bx * bx + bz * bz < ir * ir) {
                        world.setBlock(new BlockPos(bigSX + bx, baseY + y, bigSZ + bz),
                            air, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // SOLID interior spiral staircase: wide, flush with inner wall, lit
        for (int y = 1; y < bigSpireH - 4; y++) {
            double taperHere = 1.0 - ((double) y / (bigSpireH + 10));
            int rHere = Math.max(3, (int)(bigSpireR * taperHere)) - bigSpireShell;
            if (rHere < 3) continue;

            double stairAngle = Math.toRadians(y * 10);
            // Stair touches the inner wall (rHere) and extends inward
            // Fill a wide arc at multiple radii so it's thick and walkable
            for (int tw = -5; tw <= 5; tw++) {
                double offsetAngle = stairAngle + tw * 0.18;
                // Place at wall radius AND one block inward for 2-deep steps
                for (int rd = 0; rd <= 1; rd++) {
                    int sr = rHere - rd; // rHere = flush with wall, rHere-1 = one inward
                    if (sr < 2) continue;
                    int stx = bigSX + (int) Math.round(sr * Math.cos(offsetAngle));
                    int stz = bigSZ + (int) Math.round(sr * Math.sin(offsetAngle));
                    world.setBlock(new BlockPos(stx, baseY + y, stz), packedIce, StructureHelper.SET_FLAGS);
                    world.setBlock(new BlockPos(stx, baseY + y - 1, stz), packedIce, StructureHelper.SET_FLAGS);
                    for (int h = 1; h <= 3; h++) {
                        world.setBlock(new BlockPos(stx, baseY + y + h, stz), air, StructureHelper.SET_FLAGS);
                    }
                }
            }

            // Sea lantern built into the stair every 6 blocks: embedded in the step
            if (y % 6 == 0) {
                int lx = bigSX + (int) Math.round(rHere * Math.cos(stairAngle));
                int lz = bigSZ + (int) Math.round(rHere * Math.sin(stairAngle));
                world.setBlock(new BlockPos(lx, baseY + y, lz), seaLantern, StructureHelper.SET_FLAGS);
            }
        }

        // Bridge-level platform inside the spire (where bridge delivers you)
        for (int bx = -5; bx <= 5; bx++) {
            for (int bz = -5; bz <= 5; bz++) {
                if (bx * bx + bz * bz <= 25) { // circular platform
                    world.setBlock(new BlockPos(bigSX + bx, bridgeY, bigSZ + bz), packedIce, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Clear headroom above platform
        for (int bx = -3; bx <= 3; bx++) {
            for (int bz = -3; bz <= 3; bz++) {
                for (int h = 1; h <= 5; h++) {
                    world.setBlock(new BlockPos(bigSX + bx, bridgeY + h, bigSZ + bz),
                        air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Sea lantern at spire peak (visible from inside looking up)
        world.setBlock(new BlockPos(bigSX, baseY + bigSpireH - 2, bigSZ), seaLantern, StructureHelper.SET_FLAGS);

        // ============================================================
        // SECTION 6: VILLAGER HABITATION, 3 LEVELS inside big spire
        // Ground: ice floor (workshop), Platform 1: bridge level (common area),
        // Platform 2: halfway up (sleeping quarters)
        // ============================================================

        int groundFloorR = bigSpireR - bigSpireShell - 1;
        int gfy = baseY + 1; // ground furniture Y (on ice floor)

        // --- GROUND FLOOR (ICE): Workshop on the packed-ice courtyard floor ---
        // The courtyard floor section already placed packed ice at baseY inside the hex.
        // This area is the base of the spire: add workshop stations directly on ice.

        // Central hearth (2x2 campfires on stone brick: warmth on ice)
        for (int hx = 0; hx <= 1; hx++) {
            for (int hz = 0; hz <= 1; hz++) {
                world.setBlock(new BlockPos(bigSX + hx, baseY, bigSZ + hz), Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(bigSX + hx, gfy, bigSZ + hz), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Workshop stations around the ice walls
        world.setBlock(new BlockPos(bigSX + groundFloorR - 3, gfy, bigSZ + 4), Blocks.BLAST_FURNACE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + groundFloorR - 3, gfy, bigSZ + 5), Blocks.ANVIL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + groundFloorR - 4, gfy, bigSZ + 4), Blocks.CAULDRON.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX - groundFloorR + 3, gfy, bigSZ + 4), Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX - groundFloorR + 3, gfy, bigSZ + 5), Blocks.LECTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + groundFloorR - 3, gfy, bigSZ - 4), Blocks.BREWING_STAND.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + groundFloorR - 3, gfy, bigSZ - 5), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX - groundFloorR + 3, gfy, bigSZ - 4), Blocks.FLETCHING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX - groundFloorR + 3, gfy, bigSZ - 5), Blocks.TARGET.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Food stores
        world.setBlock(new BlockPos(bigSX + 4, gfy, bigSZ), Blocks.SMOKER.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + 5, gfy, bigSZ), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + 5, gfy, bigSZ + 1), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + 5, gfy, bigSZ - 1), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX - 4, gfy, bigSZ), Blocks.CRAFTING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX - 5, gfy, bigSZ), Blocks.LOOM.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX, gfy + 3, bigSZ + 3), log, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX, gfy + 4, bigSZ + 3), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Ground floor loot chest
        StructureHelper.placeChest(world, new BlockPos(bigSX - groundFloorR + 4, gfy, bigSZ),
            Direction.EAST, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);
        // Ground floor lanterns (on fence posts)
        for (int angle = 30; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int lx = bigSX + (int) Math.round((groundFloorR - 3) * Math.cos(rad));
            int lz = bigSZ + (int) Math.round((groundFloorR - 3) * Math.sin(rad));
            world.setBlock(new BlockPos(lx, gfy, lz), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(lx, gfy + 1, lz), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(lx, gfy + 2, lz),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        // --- SPRUCE LOG COLUMNS running from ground to platform 2 ---
        // These support both floating platforms and give the interior vertical structure
        int plat2Y = baseY + bigSpireH / 2; // platform 2 at 50% height
        for (int angle = 0; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int lx = bigSX + (int) Math.round((groundFloorR - 2) * Math.cos(rad));
            int lz = bigSZ + (int) Math.round((groundFloorR - 2) * Math.sin(rad));
            for (int y = 0; y <= plat2Y - baseY + 1; y++) {
                world.setBlock(new BlockPos(lx, baseY + y, lz), log, StructureHelper.SET_FLAGS);
            }
        }

        // --- PLATFORM 1 (FLOATING WOOD): Common area at bridge level ---
        int plat1Y = bridgeY;
        double taperAtPlat1 = 1.0 - ((double)(plat1Y - baseY) / (bigSpireH + 10));
        int plat1R = Math.max(4, (int)(bigSpireR * taperAtPlat1) - bigSpireShell - 1);

        // Spruce plank platform
        for (int px = -plat1R; px <= plat1R; px++) {
            for (int pz = -plat1R; pz <= plat1R; pz++) {
                if (px * px + pz * pz < plat1R * plat1R) {
                    world.setBlock(new BlockPos(bigSX + px, plat1Y, bigSZ + pz), spruce, StructureHelper.SET_FLAGS);
                }
            }
        }
        int p1fy = plat1Y + 1;

        // Campfire hearth
        world.setBlock(new BlockPos(bigSX, plat1Y, bigSZ), Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX, p1fy, bigSZ), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Carpet warmth ring
        BlockState carpet = palette.getCarpetState();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                if (cx * cx + cz * cz <= 4 && !(cx == 0 && cz == 0)) {
                    world.setBlock(new BlockPos(bigSX + cx, p1fy, bigSZ + cz), carpet, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Storage and supplies
        world.setBlock(new BlockPos(bigSX + plat1R - 2, p1fy, bigSZ), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + plat1R - 2, p1fy, bigSZ + 1), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX + plat1R - 2, p1fy, bigSZ - 1), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, new BlockPos(bigSX + plat1R - 3, p1fy, bigSZ),
            Direction.EAST, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);

        // Fence railing around the platform edge
        for (int px = -plat1R; px <= plat1R; px++) {
            for (int pz = -plat1R; pz <= plat1R; pz++) {
                int dSq = px * px + pz * pz;
                if (dSq < plat1R * plat1R && dSq >= (plat1R - 1) * (plat1R - 1)) {
                    world.setBlock(new BlockPos(bigSX + px, p1fy, bigSZ + pz), fence, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Hanging lanterns under platform 2 (illuminate the common area)
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int lx = bigSX + (int) Math.round((plat1R - 3) * Math.cos(rad));
            int lz = bigSZ + (int) Math.round((plat1R - 3) * Math.sin(rad));
            world.setBlock(new BlockPos(lx, p1fy + 3, lz),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        }

        // Sentinels at bridge entrance
        for (int guardSide : new int[]{-3, 3}) {
            int gx2 = bigSX + guardSide;
            int gz2 = bigSZ + plat1R - 2;
            world.setBlock(new BlockPos(gx2, p1fy, gz2), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(gx2, p1fy + 1, gz2), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(gx2, p1fy + 2, gz2),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        // --- PLATFORM 2 (FLOATING WOOD): Sleeping quarters at 50% height ---
        double taperAtPlat2 = 1.0 - ((double)(plat2Y - baseY) / (bigSpireH + 10));
        int plat2R = Math.max(4, (int)(bigSpireR * taperAtPlat2) - bigSpireShell - 1);

        // Spruce plank platform
        for (int px = -plat2R; px <= plat2R; px++) {
            for (int pz = -plat2R; pz <= plat2R; pz++) {
                if (px * px + pz * pz < plat2R * plat2R) {
                    world.setBlock(new BlockPos(bigSX + px, plat2Y, bigSZ + pz), spruce, StructureHelper.SET_FLAGS);
                }
            }
        }
        int p2fy = plat2Y + 1;

        // Campfire hearth
        world.setBlock(new BlockPos(bigSX, plat2Y, bigSZ), Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX, p2fy, bigSZ), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // 8 beds around the hearth
        int bedR = plat2R - 3;
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int bx = bigSX + (int) Math.round(bedR * Math.cos(rad));
            int bz = bigSZ + (int) Math.round(bedR * Math.sin(rad));
            Direction bedFacing = (Math.abs(Math.cos(rad)) > Math.abs(Math.sin(rad)))
                ? (Math.cos(rad) > 0 ? Direction.WEST : Direction.EAST)
                : (Math.sin(rad) > 0 ? Direction.NORTH : Direction.SOUTH);
            int dx = bedFacing == Direction.EAST ? 1 : bedFacing == Direction.WEST ? -1 : 0;
            int dz = bedFacing == Direction.SOUTH ? 1 : bedFacing == Direction.NORTH ? -1 : 0;
            world.setBlock(new BlockPos(bx, p2fy, bz), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, bedFacing), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(bx + dx, p2fy, bz + dz), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, bedFacing), StructureHelper.SET_FLAGS);
        }

        // Storage
        world.setBlock(new BlockPos(bigSX + plat2R - 2, p2fy, bigSZ), Blocks.BARREL.defaultBlockState(), StructureHelper.SET_FLAGS);
        StructureHelper.placeChest(world, new BlockPos(bigSX - plat2R + 3, p2fy, bigSZ),
            Direction.WEST, BuiltInLootTables.VILLAGE_SNOWY_HOUSE);

        // Fence railing
        for (int px = -plat2R; px <= plat2R; px++) {
            for (int pz = -plat2R; pz <= plat2R; pz++) {
                int dSq = px * px + pz * pz;
                if (dSq < plat2R * plat2R && dSq >= (plat2R - 1) * (plat2R - 1)) {
                    world.setBlock(new BlockPos(bigSX + px, p2fy, bigSZ + pz), fence, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Hanging lanterns
        for (int angle = 0; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int lx = bigSX + (int) Math.round((plat2R - 2) * Math.cos(rad));
            int lz = bigSZ + (int) Math.round((plat2R - 2) * Math.sin(rad));
            world.setBlock(new BlockPos(lx, p2fy + 3, lz),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), StructureHelper.SET_FLAGS);
        }

        // Courtyard lanterns (passage between spire and walls)
        for (int[] lp : new int[][]{{ox, oz + hexR - 5}, {ox - 15, oz + 5}, {ox + 15, oz + 5},
                {ox - 10, oz - 10}, {ox + 10, oz - 10}, {ox, oz + hexR / 2}}) {
            world.setBlock(new BlockPos(lp[0], baseY, lp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(lp[0], baseY + 1, lp[1]), fence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(lp[0], baseY + 2, lp[1]),
                Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        // PURPOSEFUL ARCH ENTRANCE through the south hex wall
        // Clean pointed arch: wider at base, narrows at top
        // Clear along the full bridge path through any walls it crosses
        for (int bz = bridgeEndZ; bz <= bridgeStartZ; bz++) {
            double progress = (double)(bridgeStartZ - bz) / Math.max(1, bridgeLen);
            int by = baseY + (int)(progress * (bridgeY - baseY));

            // Only clear if this Z is within a wall (check if world block is ice)
            boolean inWall = false;
            BlockState probe = world.getBlockState(new BlockPos(ox, by + 1, bz));
            if (probe.equals(blueIce) || probe.equals(packedIce) || probe.equals(snowBlock)) {
                inWall = true;
            }
            if (!inWall) continue;

            // Pointed arch: width narrows from bridge width at base to 3 at top
            int archWidth = bridgeHalfW + 1;
            int archHeight = 8; // tall enough for giant-scale
            for (int bx = -archWidth; bx <= archWidth; bx++) {
                int maxH = archHeight - Math.abs(bx) / 2; // narrows at edges
                for (int h = 0; h <= maxH; h++) {
                    for (int t = -wallThick; t <= wallThick; t++) {
                        BlockPos clearPos = new BlockPos(ox + bx, by + h, bz + t);
                        // Don't carve into the big spire
                        int dxS = ox + bx - bigSX;
                        int dzS = bz + t - bigSZ;
                        double distS = Math.sqrt(dxS * dxS + dzS * dzS);
                        double spireRH = bigSpireR * (1.0 - (double)Math.max(0, by + h - baseY) / (bigSpireH + 10));
                        if (distS > spireRH + 1) {
                            world.setBlock(clearPos, air, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
        }

        // ============================================================
        // SECTION 7: SECRET DUNGEON
        // ============================================================

        int dungeonFloorY = baseY - dungeonDepth;
        int chamberR = 5;
        int chamberCZ = bigSZ - 6; // beneath the big spire, offset north

        // Carve the chamber below the big spire (the spiral staircase descends here)
        for (int x = -chamberR; x <= chamberR; x++) {
            for (int z = -chamberR; z <= chamberR; z++) {
                if (x * x + z * z <= chamberR * chamberR) {
                    // Floor
                    world.setBlock(new BlockPos(bigSX + x, dungeonFloorY, chamberCZ + z), blueIce, StructureHelper.SET_FLAGS);
                    // Walls + hollow
                    for (int y = 1; y <= 6; y++) {
                        boolean edge = x * x + z * z > (chamberR - 1) * (chamberR - 1);
                        world.setBlock(new BlockPos(bigSX + x, dungeonFloorY + y, chamberCZ + z),
                            edge ? blueIce : air, StructureHelper.SET_FLAGS);
                    }
                    // Dome ceiling
                    world.setBlock(new BlockPos(bigSX + x, dungeonFloorY + 7, chamberCZ + z), blueIce, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Diamond shrine
        world.setBlock(new BlockPos(bigSX, dungeonFloorY + 1, chamberCZ), packedIce, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX, dungeonFloorY + 2, chamberCZ), packedIce, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX, dungeonFloorY + 3, chamberCZ), Blocks.DIAMOND_BLOCK.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(bigSX, dungeonFloorY + 4, chamberCZ), Blocks.END_ROD.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Shrine pillars
        for (int angle = 0; angle < 360; angle += 60) {
            double rad = Math.toRadians(angle);
            int px = (int) Math.round(3 * Math.cos(rad));
            int pz = (int) Math.round(3 * Math.sin(rad));
            for (int y = 1; y <= 5; y++) {
                world.setBlock(new BlockPos(bigSX + px, dungeonFloorY + y, chamberCZ + pz), blueIce, StructureHelper.SET_FLAGS);
            }
            world.setBlock(new BlockPos(bigSX + px, dungeonFloorY + 6, chamberCZ + pz),
                Blocks.END_ROD.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Soul lanterns (the only light: ancient)
        for (int[] sl : new int[][]{{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
            world.setBlock(new BlockPos(bigSX + sl[0], dungeonFloorY + 3, chamberCZ + sl[1]),
                Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false), StructureHelper.SET_FLAGS);
        }

        placeJigsawConnectors(world, center, hexR);

        VillageCastles.LOGGER.debug("Ancient Ice Citadel generation complete!");
        return new CastleBounds(
            center.offset(-hexR - 5, -dungeonDepth - chamberR - 2, -hexR - 5),
            center.offset(hexR + 5, bigSpireH + 10, hexR + 5)
        );
    }

    /**
     * Generic large grand castle: used by Plains and Taiga.
     */
    private CastleBounds generateLargeGrandCastle(ServerLevel world, BlockPos center, int radius,
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
        BlockPos nwTower = center.offset(-radius + towerOffset, 0, -radius + towerOffset);
        BlockPos neTower = center.offset(radius - towerOffset, 0, -radius + towerOffset);
        BlockPos swTower = center.offset(-radius + towerOffset, 0, radius - towerOffset);
        BlockPos seTower = center.offset(radius - towerOffset, 0, radius - towerOffset);

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

        VillageCastles.LOGGER.debug("Grand castle generation complete!");

        int boundsExtra = (palette == BiomePalette.PLAINS) ? 6 : 2; // Moat for plains, jigsaw margin for others
        return new CastleBounds(
            center.offset(-radius - boundsExtra, 0, -radius - boundsExtra),
            center.offset(radius + boundsExtra, keepHeight + 10, radius + boundsExtra)
        );
    }

    // ========================================================================
    // Desert Alcazar (Large only)
    // ========================================================================

    /**
     * DESERT LARGE: Alcazar Palace Compound.
     * A walled palace compound inspired by Alhambra/Moroccan riad architecture.
     * Thick sandstone curtain wall with 4 domed corner bastions, grand pointed-arch gatehouse,
     * central courtyard with fountain pool and acacia shade, palace building against north wall
     * (2 stories: throne hall + lord's quarters with balcony), east barracks wing, west library wing,
     * underground cistern with treasure.
     */
    private CastleBounds generateDesertPyramid(ServerLevel world, BlockPos center, int radius) {
        VillageCastles.LOGGER.debug("Generating desert alcazar at {}", center.toShortString());

        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        prepareGround(world, center, radius);

        // Block palette
        BlockState sandstone = palette.getPrimaryWallState();
        BlockState cutSandstone = Blocks.CUT_SANDSTONE.defaultBlockState();
        BlockState smoothSandstone = Blocks.SMOOTH_SANDSTONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState hangLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        BlockState floorLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
        BlockState orangeTerracotta = Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE).defaultBlockState();
        BlockState chiseledSandstone = Blocks.CHISELED_SANDSTONE.defaultBlockState();
        BlockState carpet = palette.getCarpetState();
        BlockState wallBlock = palette.wall.defaultBlockState();
        BlockState prismarine = Blocks.PRISMARINE.defaultBlockState();
        BlockState acaciaLog = palette.log.defaultBlockState();
        BlockState acaciaSlab = palette.woodSlab.defaultBlockState();
        BlockState acaciaFence = palette.fence.defaultBlockState();
        BlockState bedFoot = palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.FOOT);
        BlockState bedHead = palette.bed.defaultBlockState().setValue(BedBlock.PART, BedPart.HEAD);

        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        int half = radius;       // half-width of the compound
        int wallH = 10;          // curtain wall height
        int wallThick = 3;       // curtain wall thickness
        int bastionR = 5;        // corner bastion radius
        int bastionH = 14;       // bastion height (taller than walls)
        int palaceDepth = 14;    // palace building depth (north-south)
        int palaceH1 = 6;        // ground floor height
        int palaceH2 = 5;        // upper floor height
        int wingWidth = 10;      // east/west wing width
        int wingDepth = 12;      // wing depth
        int fy = baseY + 1;      // furniture Y

        // ================================================================
        // 1: CURTAIN WALLS, thick sandstone perimeter
        // ================================================================
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean onWall = false;
                if (Math.abs(x) >= half - wallThick + 1) onWall = true;
                if (Math.abs(z) >= half - wallThick + 1) onWall = true;
                if (!onWall) continue;
                if (Math.abs(x) > half || Math.abs(z) > half) continue;

                for (int y = 0; y < wallH; y++) {
                    m.set(ox + x, baseY + y, oz + z);
                    world.setBlock(m, sandstone, StructureHelper.SET_FLAGS);
                }
                // Crenellations (alternating merlons on top)
                if ((x + z) % 2 == 0) {
                    m.set(ox + x, baseY + wallH, oz + z);
                    world.setBlock(m, sandstone, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Wall-top walkway (smooth sandstone)
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean onWalk = false;
                if (Math.abs(x) >= half - wallThick + 1 && Math.abs(x) <= half) onWalk = true;
                if (Math.abs(z) >= half - wallThick + 1 && Math.abs(z) <= half) onWalk = true;
                if (onWalk && Math.abs(x) <= half && Math.abs(z) <= half) {
                    m.set(ox + x, baseY + wallH - 1, oz + z);
                    world.setBlock(m, smoothSandstone, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ================================================================
        // 2: CORNER BASTIONS, cylindrical with domed tops
        // ================================================================
        int[][] bastionCenters = {
            {-half + bastionR, -half + bastionR},  // NW
            { half - bastionR, -half + bastionR},  // NE
            {-half + bastionR,  half - bastionR},  // SW
            { half - bastionR,  half - bastionR},  // SE
        };
        for (int[] bc : bastionCenters) {
            int bx = ox + bc[0], bz = oz + bc[1];
            // Cylinder body
            for (int y = 0; y < bastionH; y++) {
                for (int dx = -bastionR; dx <= bastionR; dx++) {
                    for (int dz = -bastionR; dz <= bastionR; dz++) {
                        if (dx * dx + dz * dz <= bastionR * bastionR) {
                            m.set(bx + dx, baseY + y, bz + dz);
                            world.setBlock(m, sandstone, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Hollow interior (rooms inside bastions)
            int ir = bastionR - 2;
            for (int y = 1; y < bastionH - 1; y++) {
                for (int dx = -ir; dx <= ir; dx++) {
                    for (int dz = -ir; dz <= ir; dz++) {
                        if (dx * dx + dz * dz < ir * ir) {
                            m.set(bx + dx, baseY + y, bz + dz);
                            world.setBlock(m, air, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Dome top (hemisphere)
            for (int dy = 0; dy <= bastionR; dy++) {
                int domeR = (int) Math.sqrt(bastionR * bastionR - dy * dy);
                for (int dx = -domeR; dx <= domeR; dx++) {
                    for (int dz = -domeR; dz <= domeR; dz++) {
                        if (dx * dx + dz * dz <= domeR * domeR) {
                            m.set(bx + dx, baseY + bastionH + dy, bz + dz);
                            world.setBlock(m, cutSandstone, StructureHelper.SET_FLAGS);
                        }
                    }
                }
            }
            // Lantern inside bastion
            world.setBlock(new BlockPos(bx, baseY + bastionH - 2, bz), hangLantern, StructureHelper.SET_FLAGS);
            // Sea lantern at dome peak
            world.setBlock(new BlockPos(bx, baseY + bastionH + bastionR, bz),
                Blocks.SEA_LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
            // Door into courtyard (toward center)
            int doorDX = bc[0] > 0 ? -bastionR : bastionR;
            int doorDZ = bc[1] > 0 ? -bastionR : bastionR;
            for (int dy = 1; dy <= 3; dy++) {
                world.setBlock(new BlockPos(bx + doorDX, baseY + dy, bz), air, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(bx, baseY + dy, bz + doorDZ), air, StructureHelper.SET_FLAGS);
            }
        }

        // ================================================================
        // 3: GRAND GATEHOUSE, pointed arch on south wall
        // ================================================================
        int gateHW = 3; // 7-wide opening
        // Carve the gate through the south wall
        for (int gx = -gateHW; gx <= gateHW; gx++) {
            int archH = 6 - Math.abs(gx) / 2; // pointed arch shape
            for (int gy = 1; gy <= archH; gy++) {
                for (int gz = 0; gz < wallThick; gz++) {
                    m.set(ox + gx, baseY + gy, oz + half - gz);
                    world.setBlock(m, air, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Chiseled sandstone arch frame
        for (int gy = 1; gy <= 7; gy++) {
            world.setBlock(new BlockPos(ox - gateHW - 1, baseY + gy, oz + half), chiseledSandstone, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + gateHW + 1, baseY + gy, oz + half), chiseledSandstone, StructureHelper.SET_FLAGS);
        }
        // Arch keystone
        world.setBlock(new BlockPos(ox, baseY + 7, oz + half), chiseledSandstone, StructureHelper.SET_FLAGS);
        // Hang the citadel gate in the arch that was already framed for it.
        Openings.door(world, new BlockPos(ox - 1, baseY + 1, oz + half), Direction.NORTH,
            palette.door.defaultBlockState(), null, palette.getFloorState(), true);
        // Flanking towers above gate (2 small turrets)
        for (int side : new int[]{-1, 1}) {
            int tx = ox + side * (gateHW + 3);
            for (int y = 0; y <= wallH + 4; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        m.set(tx + dx, baseY + y, oz + half - 1 + dz);
                        world.setBlock(m, sandstone, StructureHelper.SET_FLAGS);
                    }
                }
            }
            // Lantern on turret
            world.setBlock(new BlockPos(tx, baseY + wallH + 5, oz + half - 1), floorLantern, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // 4: COURTYARD FLOOR, smooth sandstone with terracotta patterns
        // ================================================================
        int innerHalf = half - wallThick;
        for (int x = -innerHalf; x <= innerHalf; x++) {
            for (int z = -innerHalf; z <= innerHalf; z++) {
                m.set(ox + x, baseY, oz + z);
                // Terracotta accent cross pattern
                if (x == 0 || z == 0 || Math.abs(x) == Math.abs(z)) {
                    world.setBlock(m, orangeTerracotta, StructureHelper.SET_FLAGS);
                } else {
                    world.setBlock(m, smoothSandstone, StructureHelper.SET_FLAGS);
                }
            }
        }

        // ================================================================
        // 5: CENTRAL FOUNTAIN, octagonal pool with water column
        // ================================================================
        int fountainR = 4;
        // Dig the pool 1 block deep with prismarine bottom
        for (int fx = -fountainR; fx <= fountainR; fx++) {
            for (int fz = -fountainR; fz <= fountainR; fz++) {
                if (Math.abs(fx) + Math.abs(fz) <= fountainR + 1) { // octagonal
                    m.set(ox + fx, baseY - 1, oz + fz);
                    world.setBlock(m, prismarine, StructureHelper.SET_FLAGS);
                    m.set(ox + fx, baseY, oz + fz);
                    world.setBlock(m, Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }
        // Prismarine border at water level
        for (int fx = -fountainR - 1; fx <= fountainR + 1; fx++) {
            for (int fz = -fountainR - 1; fz <= fountainR + 1; fz++) {
                if (Math.abs(fx) + Math.abs(fz) == fountainR + 2) {
                    m.set(ox + fx, baseY, oz + fz);
                    world.setBlock(m, prismarine, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Central column with water flowing down
        for (int y = 1; y <= 4; y++) {
            world.setBlock(new BlockPos(ox, baseY + y, oz), chiseledSandstone, StructureHelper.SET_FLAGS);
        }
        world.setBlock(new BlockPos(ox, baseY + 5, oz), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Lanterns on fountain corners
        for (int[] fp : new int[][]{{-fountainR, 0}, {fountainR, 0}, {0, -fountainR}, {0, fountainR}}) {
            world.setBlock(new BlockPos(ox + fp[0], baseY + 1, oz + fp[1]), acaciaFence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + fp[0], baseY + 2, oz + fp[1]), floorLantern, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // 6: PALACE BUILDING, against north wall, 2 stories
        // ================================================================
        int palN = -innerHalf;                 // north edge (against wall)
        int palS = palN + palaceDepth;         // south edge
        int palW = -innerHalf;                 // full width
        int palE = innerHalf;

        // Solid shell
        StructureHelper.fillBox(world,
            new BlockPos(ox + palW, baseY, oz + palN),
            new BlockPos(ox + palE, baseY + palaceH1 + palaceH2, oz + palS),
            sandstone);

        // --- Ground floor: Throne Hall ---
        int g1Floor = baseY + 1;
        int g1Ceil = baseY + palaceH1 - 1;
        // Carve interior
        StructureHelper.clearInterior(world,
            new BlockPos(ox + palW + 2, g1Floor, oz + palN + 2),
            new BlockPos(ox + palE - 2, g1Ceil, oz + palS - 2));
        // Smooth sandstone floor
        StructureHelper.fillBox(world,
            new BlockPos(ox + palW + 2, g1Floor - 1, oz + palN + 2),
            new BlockPos(ox + palE - 2, g1Floor - 1, oz + palS - 2),
            smoothSandstone);

        // Colonnade: chiseled sandstone pillars down the hall
        for (int px = palW + 5; px <= palE - 5; px += 5) {
            for (int py = g1Floor; py <= g1Ceil; py++) {
                world.setBlock(new BlockPos(ox + px, py, oz + palN + 4), chiseledSandstone, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + px, py, oz + palS - 4), chiseledSandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Throne at center of north wall
        world.setBlock(new BlockPos(ox, g1Floor, oz + palN + 2),
            Blocks.SANDSTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM),
            StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, g1Floor, oz + palN + 1), chiseledSandstone, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, g1Floor + 1, oz + palN + 1), chiseledSandstone, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - 1, g1Floor, oz + palN + 2), chiseledSandstone, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 1, g1Floor, oz + palN + 2), chiseledSandstone, StructureHelper.SET_FLAGS);

        // Carpet runner from entrance to throne
        for (int cz = palN + 3; cz <= palS - 2; cz++) {
            world.setBlock(new BlockPos(ox, g1Floor, oz + cz), carpet, StructureHelper.SET_FLAGS);
        }

        // Grand entrance (south face of palace, pointed arch into courtyard)
        for (int gx = -2; gx <= 2; gx++) {
            int archH = 5 - Math.abs(gx) / 2;
            for (int gy = 1; gy <= archH; gy++) {
                for (int gz = 0; gz < 2; gz++) {
                    world.setBlock(new BlockPos(ox + gx, baseY + gy, oz + palS - gz), air, StructureHelper.SET_FLAGS);
                }
            }
        }
        // Arch frame
        for (int gy = 1; gy <= 6; gy++) {
            world.setBlock(new BlockPos(ox - 3, baseY + gy, oz + palS), chiseledSandstone, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 3, baseY + gy, oz + palS), chiseledSandstone, StructureHelper.SET_FLAGS);
        }

        // Hanging lanterns in throne hall
        for (int lx = palW + 4; lx <= palE - 4; lx += 4) {
            world.setBlock(new BlockPos(ox + lx, g1Ceil, oz + palN + 6), hangLantern, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + lx, g1Ceil, oz + palS - 4), hangLantern, StructureHelper.SET_FLAGS);
        }
        world.setBlock(new BlockPos(ox + 5, g1Floor, oz + palS - 3), acaciaFence, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 5, g1Floor + 1, oz + palS - 3), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // --- Upper floor: Lord's quarters ---
        int u1Floor = baseY + palaceH1 + 1;
        int u1Ceil = baseY + palaceH1 + palaceH2 - 1;
        // Carve interior
        StructureHelper.clearInterior(world,
            new BlockPos(ox + palW + 2, u1Floor, oz + palN + 2),
            new BlockPos(ox + palE - 2, u1Ceil, oz + palS - 2));
        StructureHelper.fillBox(world,
            new BlockPos(ox + palW + 2, u1Floor - 1, oz + palN + 2),
            new BlockPos(ox + palE - 2, u1Floor - 1, oz + palS - 2),
            smoothSandstone);

        // Internal staircase (east side, ground to upper)
        BlockState stairN = Blocks.SANDSTONE_STAIRS.defaultBlockState()
            .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
        int stairX = ox + palE - 4;
        for (int step = 0; step < palaceH1; step++) {
            int sz = oz + palS - 3 - step;
            m.set(stairX, baseY + 1 + step, sz);
            world.setBlock(m, stairN, StructureHelper.SET_FLAGS);
            m.set(stairX - 1, baseY + 1 + step, sz);
            world.setBlock(m, stairN, StructureHelper.SET_FLAGS);
            for (int h = 1; h <= 3; h++) {
                world.setBlock(new BlockPos(stairX, baseY + 1 + step + h, sz), air, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(stairX - 1, baseY + 1 + step + h, sz), air, StructureHelper.SET_FLAGS);
            }
        }

        // Lord's bed (center of upper floor)
        world.setBlock(new BlockPos(ox, u1Floor, oz + palN + 4),
            bedFoot.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, u1Floor, oz + palN + 5),
            bedHead.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 2, u1Floor, oz + palN + 4),
            bedFoot.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 2, u1Floor, oz + palN + 5),
            bedHead.setValue(BedBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);

        // Bookshelves against north wall
        for (int bx = -5; bx <= -2; bx++) {
            for (int by = u1Floor; by <= u1Floor + 1; by++) {
                world.setBlock(new BlockPos(ox + bx, by, oz + palN + 2), Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Lord's chest
        StructureHelper.placeChest(world, new BlockPos(ox - palE + 4, u1Floor, oz + palN + 3),
            Direction.SOUTH, BuiltInLootTables.VILLAGE_DESERT_HOUSE);

        // Balcony overlooking courtyard (south side of upper floor)
        // Open the south wall
        for (int bx = -4; bx <= 4; bx++) {
            for (int by = u1Floor; by <= u1Floor + 2; by++) {
                world.setBlock(new BlockPos(ox + bx, by, oz + palS), air, StructureHelper.SET_FLAGS);
            }
        }
        // Balcony platform extending into courtyard
        for (int bx = -5; bx <= 5; bx++) {
            for (int bz = 1; bz <= 3; bz++) {
                world.setBlock(new BlockPos(ox + bx, u1Floor - 1, oz + palS + bz), smoothSandstone, StructureHelper.SET_FLAGS);
            }
        }
        // Fence railing on balcony
        for (int bx = -5; bx <= 5; bx++) {
            world.setBlock(new BlockPos(ox + bx, u1Floor, oz + palS + 3), acaciaFence, StructureHelper.SET_FLAGS);
        }
        for (int bz = 1; bz <= 3; bz++) {
            world.setBlock(new BlockPos(ox - 5, u1Floor, oz + palS + bz), acaciaFence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 5, u1Floor, oz + palS + bz), acaciaFence, StructureHelper.SET_FLAGS);
        }

        // Upper floor lanterns
        for (int lx = palW + 5; lx <= palE - 5; lx += 5) {
            world.setBlock(new BlockPos(ox + lx, u1Ceil, oz + palN + 5), hangLantern, StructureHelper.SET_FLAGS);
        }

        // Flat roof with crenellations
        StructureHelper.fillBox(world,
            new BlockPos(ox + palW, baseY + palaceH1 + palaceH2, oz + palN),
            new BlockPos(ox + palE, baseY + palaceH1 + palaceH2, oz + palS),
            smoothSandstone);
        for (int rx = palW; rx <= palE; rx += 2) {
            world.setBlock(new BlockPos(ox + rx, baseY + palaceH1 + palaceH2 + 1, oz + palN), sandstone, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + rx, baseY + palaceH1 + palaceH2 + 1, oz + palS), sandstone, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // 7: COURTYARD MARKETPLACE, open-air stalls with shade canopies
        // Each stall: 4 acacia log posts, acacia slab roof, profession blocks beneath
        // Arranged in two rows flanking the fountain path
        // ================================================================

        // --- Market stall helper: place a 5x5 pergola stall with profession items ---
        // Stall positions: [centerX offset, centerZ offset, facing direction for items]
        int[][] stallPositions = {
            // East row (3 stalls)
            {innerHalf - 6, palS + 4},
            {innerHalf - 6, palS + 10},
            {innerHalf - 6, innerHalf - 6},
            // West row (3 stalls)
            {-innerHalf + 6, palS + 4},
            {-innerHalf + 6, palS + 10},
            {-innerHalf + 6, innerHalf - 6},
        };

        // Profession blocks for each stall: a different trade per stall
        BlockState[][] stallContents = {
            // East stalls: blacksmith, butcher, fletcher
            {Blocks.BLAST_FURNACE.defaultBlockState(), Blocks.ANVIL.defaultBlockState(), Blocks.CAULDRON.defaultBlockState()},
            {Blocks.SMOKER.defaultBlockState(), Blocks.BARREL.defaultBlockState(), Blocks.CAMPFIRE.defaultBlockState()},
            {Blocks.FLETCHING_TABLE.defaultBlockState(), Blocks.TARGET.defaultBlockState(), Blocks.BARREL.defaultBlockState()},
            // West stalls: cartographer, cleric, leatherworker
            {Blocks.CARTOGRAPHY_TABLE.defaultBlockState(), Blocks.LECTERN.defaultBlockState(), Blocks.BOOKSHELF.defaultBlockState()},
            {Blocks.BREWING_STAND.defaultBlockState(), Blocks.CAULDRON.defaultBlockState(), Blocks.BARREL.defaultBlockState()},
            {Blocks.LOOM.defaultBlockState(), Blocks.CRAFTING_TABLE.defaultBlockState(), Blocks.BARREL.defaultBlockState()},
        };

        for (int si = 0; si < stallPositions.length; si++) {
            int sx = ox + stallPositions[si][0];
            int sz = oz + stallPositions[si][1];
            int stallHW = 2; // 5x5 stall

            // 4 acacia log posts
            for (int[] corner : new int[][]{{-stallHW, -stallHW}, {stallHW, -stallHW}, {-stallHW, stallHW}, {stallHW, stallHW}}) {
                for (int py = 1; py <= 3; py++) {
                    world.setBlock(new BlockPos(sx + corner[0], baseY + py, sz + corner[1]), acaciaLog, StructureHelper.SET_FLAGS);
                }
            }
            // Acacia slab canopy
            for (int cx = -stallHW; cx <= stallHW; cx++) {
                for (int cz = -stallHW; cz <= stallHW; cz++) {
                    world.setBlock(new BlockPos(sx + cx, baseY + 4, sz + cz), acaciaSlab, StructureHelper.SET_FLAGS);
                }
            }
            // Orange terracotta stall counter (U-shape)
            for (int cx = -stallHW + 1; cx <= stallHW - 1; cx++) {
                world.setBlock(new BlockPos(sx + cx, fy, sz - stallHW + 1), orangeTerracotta, StructureHelper.SET_FLAGS);
            }
            world.setBlock(new BlockPos(sx - stallHW + 1, fy, sz), orangeTerracotta, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(sx + stallHW - 1, fy, sz), orangeTerracotta, StructureHelper.SET_FLAGS);

            // Profession blocks on the counter
            BlockState[] items = stallContents[si];
            world.setBlock(new BlockPos(sx - 1, fy + 1, sz - stallHW + 1), items[0], StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(sx, fy + 1, sz - stallHW + 1), items[1], StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(sx + 1, fy + 1, sz - stallHW + 1), items[2], StructureHelper.SET_FLAGS);

            // Hanging lantern from canopy
            world.setBlock(new BlockPos(sx, baseY + 3, sz), hangLantern, StructureHelper.SET_FLAGS);

            // Decorated pot beside each stall
            world.setBlock(new BlockPos(sx + stallHW, fy, sz + stallHW),
                Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // --- Guard barracks: a simple row of beds against the east inner wall ---
        // No box: just beds, chests, and fence posts along the wall
        for (int b = 0; b < 6; b++) {
            int bedZ = oz + palS + 3 + b * 3;
            if (bedZ >= oz + innerHalf - 2) break;
            // Beds against the east wall, facing the courtyard
            world.setBlock(new BlockPos(ox + innerHalf - 1, fy, bedZ),
                bedFoot.setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + innerHalf - 2, fy, bedZ),
                bedHead.setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }
        // Guard chests at ends
        StructureHelper.placeChest(world, new BlockPos(ox + innerHalf - 1, fy, oz + palS + 2),
            Direction.SOUTH, BuiltInLootTables.VILLAGE_DESERT_HOUSE);
        StructureHelper.placeChest(world, new BlockPos(ox + innerHalf - 1, fy, oz + innerHalf - 2),
            Direction.NORTH, BuiltInLootTables.VILLAGE_DESERT_HOUSE);

        // ================================================================
        // 8: ACACIA SHADE TREES in courtyard
        // ================================================================
        int[][] treePositions = {
            {-innerHalf + 12, palS + 7}, {innerHalf - 12, palS + 7},
            {0, innerHalf - 5},
            {-innerHalf + 12, innerHalf - 5}, {innerHalf - 12, innerHalf - 5},
        };
        for (int[] tp : treePositions) {
            int tx = ox + tp[0], tz = oz + tp[1];
            // Trunk
            for (int y = 1; y <= 5; y++) {
                world.setBlock(new BlockPos(tx, baseY + y, tz), acaciaLog, StructureHelper.SET_FLAGS);
            }
            // Wide flat canopy (acacia leaves)
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    if (Math.abs(cx) + Math.abs(cz) <= 4) {
                        world.setBlock(new BlockPos(tx + cx, baseY + 6, tz + cz),
                            Blocks.ACACIA_LEAVES.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                    if (Math.abs(cx) + Math.abs(cz) <= 3) {
                        world.setBlock(new BlockPos(tx + cx, baseY + 7, tz + cz),
                            Blocks.ACACIA_LEAVES.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // ================================================================
        // 9: GRANDEUR DETAILS, water channels, banners, decorated pots, wall accents
        // ================================================================

        // Water channels from fountain to east/west walls (shallow 1-wide canals)
        for (int wx = fountainR + 2; wx < innerHalf - 2; wx++) {
            world.setBlock(new BlockPos(ox + wx, baseY - 1, oz), prismarine, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + wx, baseY, oz), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - wx, baseY - 1, oz), prismarine, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - wx, baseY, oz), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Chiseled sandstone accent pillars on inner face of curtain walls (every 8 blocks)
        for (int wx = -innerHalf + 4; wx <= innerHalf - 4; wx += 8) {
            for (int wy = 1; wy <= wallH - 2; wy++) {
                // North inner wall
                world.setBlock(new BlockPos(ox + wx, baseY + wy, oz - innerHalf), chiseledSandstone, StructureHelper.SET_FLAGS);
                // South inner wall
                world.setBlock(new BlockPos(ox + wx, baseY + wy, oz + innerHalf), chiseledSandstone, StructureHelper.SET_FLAGS);
            }
        }
        for (int wz = -innerHalf + 4; wz <= innerHalf - 4; wz += 8) {
            for (int wy = 1; wy <= wallH - 2; wy++) {
                world.setBlock(new BlockPos(ox - innerHalf, baseY + wy, oz + wz), chiseledSandstone, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + innerHalf, baseY + wy, oz + wz), chiseledSandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Decorated pots flanking the palace entrance
        world.setBlock(new BlockPos(ox - 4, fy, oz + palS + 1), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 4, fy, oz + palS + 1), Blocks.DECORATED_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Carpet path from gate to fountain (south approach)
        for (int cz = innerHalf - 1; cz > fountainR + 2; cz--) {
            world.setBlock(new BlockPos(ox, fy, oz + cz), carpet, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - 1, fy, oz + cz), carpet, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 1, fy, oz + cz), carpet, StructureHelper.SET_FLAGS);
        }

        // Sandstone wall railings along the carpet path (low walls)
        for (int cz = innerHalf - 1; cz > fountainR + 2; cz -= 3) {
            world.setBlock(new BlockPos(ox - 2, fy, oz + cz), wallBlock, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 2, fy, oz + cz), wallBlock, StructureHelper.SET_FLAGS);
        }

        // Flower pots on wall railings
        world.setBlock(new BlockPos(ox - 2, fy + 1, oz + innerHalf - 1), Blocks.FLOWER_POT.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 2, fy + 1, oz + innerHalf - 1), Blocks.FLOWER_POT.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Sea lanterns embedded in curtain wall at regular intervals (exterior glow)
        for (int wx = -half + 6; wx <= half - 6; wx += 8) {
            world.setBlock(new BlockPos(ox + wx, baseY + wallH / 2, oz + half), Blocks.SEA_LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + wx, baseY + wallH / 2, oz - half), Blocks.SEA_LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        }
        for (int wz = -half + 6; wz <= half - 6; wz += 8) {
            world.setBlock(new BlockPos(ox + half, baseY + wallH / 2, oz + wz), Blocks.SEA_LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - half, baseY + wallH / 2, oz + wz), Blocks.SEA_LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // Campfire cooking area near the butcher stall
        world.setBlock(new BlockPos(ox + innerHalf - 8, baseY, oz + palS + 11), Blocks.STONE_BRICKS.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + innerHalf - 8, fy, oz + palS + 11), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);

        // Well near the south gate (travelers' water)
        int wellX = ox, wellZ = oz + innerHalf - 3;
        for (int wx2 = -1; wx2 <= 1; wx2++) {
            for (int wz2 = -1; wz2 <= 1; wz2++) {
                if (Math.abs(wx2) == 1 || Math.abs(wz2) == 1) {
                    world.setBlock(new BlockPos(wellX + wx2, fy, wellZ + wz2), wallBlock, StructureHelper.SET_FLAGS);
                } else {
                    world.setBlock(new BlockPos(wellX, baseY - 1, wellZ), prismarine, StructureHelper.SET_FLAGS);
                    world.setBlock(new BlockPos(wellX, baseY, wellZ), Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
        }
        // Well posts and roof
        for (int wy = 1; wy <= 3; wy++) {
            world.setBlock(new BlockPos(wellX - 1, fy + wy, wellZ - 1), acaciaFence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(wellX + 1, fy + wy, wellZ - 1), acaciaFence, StructureHelper.SET_FLAGS);
        }
        for (int wx2 = -1; wx2 <= 1; wx2++) {
            world.setBlock(new BlockPos(wellX + wx2, fy + 4, wellZ - 1), acaciaSlab, StructureHelper.SET_FLAGS);
        }

        // ================================================================
        // 10: UNDERGROUND CISTERN (trapdoor access in courtyard)
        // ================================================================
        int cisternDepth = Math.min(6, Math.max(2, baseY + 64));
        int cisternFloorY = baseY - cisternDepth;
        int cisternHalf = 6;
        int cisternX = ox + 10; // east side of courtyard

        // Carve chamber
        StructureHelper.clearInterior(world,
            new BlockPos(cisternX - cisternHalf, cisternFloorY + 1, oz - cisternHalf),
            new BlockPos(cisternX + cisternHalf, baseY - 1, oz + cisternHalf));
        StructureHelper.fillBox(world,
            new BlockPos(cisternX - cisternHalf, cisternFloorY, oz - cisternHalf),
            new BlockPos(cisternX + cisternHalf, cisternFloorY, oz + cisternHalf),
            smoothSandstone);
        // Walls
        for (int wx = -cisternHalf; wx <= cisternHalf; wx++) {
            for (int wy = cisternFloorY; wy <= baseY; wy++) {
                world.setBlock(new BlockPos(cisternX + wx, wy, oz - cisternHalf), sandstone, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(cisternX + wx, wy, oz + cisternHalf), sandstone, StructureHelper.SET_FLAGS);
            }
        }
        for (int wz = -cisternHalf + 1; wz <= cisternHalf - 1; wz++) {
            for (int wy = cisternFloorY; wy <= baseY; wy++) {
                world.setBlock(new BlockPos(cisternX - cisternHalf, wy, oz + wz), sandstone, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(cisternX + cisternHalf, wy, oz + wz), sandstone, StructureHelper.SET_FLAGS);
            }
        }

        // Water pool in center of cistern
        for (int px = -2; px <= 2; px++) {
            for (int pz = -2; pz <= 2; pz++) {
                world.setBlock(new BlockPos(cisternX + px, cisternFloorY, oz + pz), prismarine, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(cisternX + px, cisternFloorY + 1, oz + pz),
                    Blocks.WATER.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        // Treasure chest
        StructureHelper.placeChest(world, new BlockPos(cisternX - cisternHalf + 1, cisternFloorY + 1, oz),
            Direction.EAST, BuiltInLootTables.VILLAGE_DESERT_HOUSE);

        // Staircase down from courtyard
        for (int step = 0; step < cisternDepth; step++) {
            int sy = baseY - step;
            int sz = oz - cisternHalf + 1 + step;
            world.setBlock(new BlockPos(cisternX, sy, sz),
                Blocks.SANDSTONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM),
                StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(cisternX + 1, sy, sz),
                Blocks.SANDSTONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM),
                StructureHelper.SET_FLAGS);
            for (int h = 1; h <= 3; h++) {
                world.setBlock(new BlockPos(cisternX, sy + h, sz), air, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(cisternX + 1, sy + h, sz), air, StructureHelper.SET_FLAGS);
            }
        }

        // Cistern lanterns
        world.setBlock(new BlockPos(cisternX, cisternFloorY + 3, oz - cisternHalf + 1), hangLantern, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(cisternX, cisternFloorY + 3, oz + cisternHalf - 1), hangLantern, StructureHelper.SET_FLAGS);

        // ================================================================
        // 11: COURTYARD LANTERNS + finishing touches
        // ================================================================
        // Lantern posts around the courtyard
        int[][] lanternPosts = {
            {-12, 0}, {12, 0}, {0, 10}, {0, innerHalf - 3},
            {-innerHalf + 3, innerHalf - 3}, {innerHalf - 3, innerHalf - 3}
        };
        for (int[] lp : lanternPosts) {
            world.setBlock(new BlockPos(ox + lp[0], fy, oz + lp[1]), acaciaFence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + lp[0], fy + 1, oz + lp[1]), acaciaFence, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + lp[0], fy + 2, oz + lp[1]), floorLantern, StructureHelper.SET_FLAGS);
        }

        placeJigsawConnectors(world, center, radius);

        int totalHeight = palaceH1 + palaceH2 + 2;

        VillageCastles.LOGGER.debug("Desert alcazar generation complete!");

        return new CastleBounds(
            center.offset(-half - 4, -cisternDepth - 2, -half - 4),
            center.offset(half + 4, bastionH + bastionR + 2, half + 4)
        );
    }

    /**
     * Build a water moat around the castle perimeter with a drawbridge at the south gate.
     * The moat is a 4-wide, 3-deep trench of water just outside the castle walls.
     * A drawbridge (oak planks) spans the moat at the south gatehouse.
     */
    private void buildMoat(ServerLevel world, BlockPos center, int radius) {
        int moatOffset = radius + 1;   // Just outside the wall line
        int moatWidth = 3;
        int moatDepth = 3;
        int ox = center.getX();
        int oz = center.getZ();
        int baseY = center.getY();
        int gateHalfWidth = GateGenerator.getFullWidth() / 2 + 1;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState stoneBrick = Blocks.STONE_BRICKS.defaultBlockState();

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
                        world.setBlock(mutable, stone, StructureHelper.SET_FLAGS);
                    } else if (y == 0 && isEdge) {
                        // Top edge: stone brick lip
                        world.setBlock(mutable, stoneBrick, StructureHelper.SET_FLAGS);
                    } else if (y < 0) {
                        // Water fill
                        world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
                    } else {
                        // Surface level non-edge: water
                        world.setBlock(mutable, water, StructureHelper.SET_FLAGS);
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
                world.setBlock(mutable, planks, StructureHelper.SET_FLAGS);
            }
        }

        // Drawbridge at north gate
        for (int bz = -bridgeZ - moatWidth; bz <= -bridgeZ; bz++) {
            for (int bx = -gateHalfWidth; bx <= gateHalfWidth; bx++) {
                mutable.set(ox + bx, baseY, oz + bz);
                world.setBlock(mutable, planks, StructureHelper.SET_FLAGS);
            }
        }

        // Fence railings on the drawbridge edges
        BlockState fence = palette.getFenceState();
        for (int bz = bridgeZ; bz <= bridgeZ + moatWidth; bz++) {
            mutable.set(ox - gateHalfWidth - 1, baseY + 1, oz + bz);
            world.setBlock(mutable, fence, StructureHelper.SET_FLAGS);
            mutable.set(ox + gateHalfWidth + 1, baseY + 1, oz + bz);
            world.setBlock(mutable, fence, StructureHelper.SET_FLAGS);
        }
        for (int bz = -bridgeZ - moatWidth; bz <= -bridgeZ; bz++) {
            mutable.set(ox - gateHalfWidth - 1, baseY + 1, oz + bz);
            world.setBlock(mutable, fence, StructureHelper.SET_FLAGS);
            mutable.set(ox + gateHalfWidth + 1, baseY + 1, oz + bz);
            world.setBlock(mutable, fence, StructureHelper.SET_FLAGS);
        }
    }


    // ==========================================================================
    // TAIGA MEDIUM: Scandinavian Ring Fort
    // ==========================================================================

    /**
     * TAIGA MEDIUM: Scandinavian ring fort garrison.
     * Circular rampart wall (mossy cobblestone base + spruce log palisade),
     * 4 cardinal gates, cross-road, central longhouse, corner watchtower
     * platforms, fighting walkway, bell at crossroads.
     */
    private CastleBounds generateTaigaRingFort(ServerLevel world, BlockPos center, int radius) {
        int fortRadius = 15;
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        BlockState logWall = palette.log.defaultBlockState();
        BlockState mossyCobble = palette.getSecondaryWallState();
        BlockState cobble = palette.getPrimaryWallState();
        BlockState planks = palette.getPlanksState();
        BlockState slabBlock = palette.woodSlab.defaultBlockState();
        BlockState fenceBlock = palette.fence.defaultBlockState();
        BlockState roofBlock = palette.getRoofState();
        BlockState cobblePath = Blocks.COBBLESTONE.defaultBlockState();

        // === GROUND PREPARATION ===
        prepareGround(world, center, fortRadius + 2);

        // === CIRCULAR RAMPART WALL ===
        // 2 blocks mossy cobblestone base + 3 blocks spruce log palisade = 5 total
        int wallThickness = 2;
        for (int angle = 0; angle < 360; angle += 1) {
            double rad = Math.toRadians(angle);
            int wx = ox + (int) Math.round(fortRadius * Math.cos(rad));
            int wz = oz + (int) Math.round(fortRadius * Math.sin(rad));

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
                int iwx = ox + (int) Math.round(innerRad * Math.cos(rad));
                int iwz = oz + (int) Math.round(innerRad * Math.sin(rad));

                // 2 blocks mossy cobblestone base
                for (int y = 0; y <= 1; y++) {
                    world.setBlock(new BlockPos(iwx, baseY + y, iwz), mossyCobble, StructureHelper.SET_FLAGS);
                }
                // 3 blocks spruce log palisade on top
                for (int y = 2; y <= 4; y++) {
                    world.setBlock(new BlockPos(iwx, baseY + y, iwz), logWall, StructureHelper.SET_FLAGS);
                }
            }
        }

        // === FIGHTING WALKWAY: spruce slab on interior wall at 3 blocks up ===
        for (int angle = 0; angle < 360; angle += 2) {
            double rad = Math.toRadians(angle);
            int walkX = ox + (int) Math.round((fortRadius - 2) * Math.cos(rad));
            int walkZ = oz + (int) Math.round((fortRadius - 2) * Math.sin(rad));
            world.setBlock(new BlockPos(walkX, baseY + 3, walkZ), slabBlock, StructureHelper.SET_FLAGS);
        }

        // === 4 CARDINAL GATES: 3-wide tunnels through rampart ===
        // South gate
        for (int dz = -wallThickness; dz <= 0; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlock(new BlockPos(ox + dx, baseY + y, oz + fortRadius + dz), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
            // Spruce log frame on sides
            for (int y = 0; y <= 4; y++) {
                world.setBlock(new BlockPos(ox - 2, baseY + y, oz + fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + 2, baseY + y, oz + fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
            }
        }
        // A hung double door in the gate tunnel, with the third column closed off. A single course
        // of fence gates across a three-block opening leaves the way open above and below it.
        Openings.door(world, new BlockPos(ox - 1, baseY + 1, oz + fortRadius), Direction.NORTH,
            palette.door.defaultBlockState(), null, palette.getPrimaryWallState(), true);
        for (int y = 1; y <= 2; y++) {
            world.setBlock(new BlockPos(ox + 1, baseY + y, oz + fortRadius), logWall, StructureHelper.SET_FLAGS);
        }

        // North gate
        for (int dz = 0; dz <= wallThickness; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlock(new BlockPos(ox + dx, baseY + y, oz - fortRadius + dz), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
            for (int y = 0; y <= 4; y++) {
                world.setBlock(new BlockPos(ox - 2, baseY + y, oz - fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + 2, baseY + y, oz - fortRadius + dz), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            world.setBlock(new BlockPos(ox + dx, baseY + 1, oz - fortRadius),
                palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }

        // East gate
        for (int ddx = -wallThickness; ddx <= 0; ddx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlock(new BlockPos(ox + fortRadius + ddx, baseY + y, oz + dz), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
            for (int y = 0; y <= 4; y++) {
                world.setBlock(new BlockPos(ox + fortRadius + ddx, baseY + y, oz - 2), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + fortRadius + ddx, baseY + y, oz + 2), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlock(new BlockPos(ox + fortRadius, baseY + 1, oz + dz),
                palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        }

        // West gate
        for (int ddx = 0; ddx <= wallThickness; ddx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 1; y <= 3; y++) {
                    world.setBlock(new BlockPos(ox - fortRadius + ddx, baseY + y, oz + dz), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                }
            }
            for (int y = 0; y <= 4; y++) {
                world.setBlock(new BlockPos(ox - fortRadius + ddx, baseY + y, oz - 2), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox - fortRadius + ddx, baseY + y, oz + 2), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlock(new BlockPos(ox - fortRadius, baseY + 1, oz + dz),
                palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // === CROSS-ROAD: cobblestone paths N-S and E-W ===
        for (int i = -fortRadius + 2; i <= fortRadius - 2; i++) {
            // N-S path (x = center, vary z)
            world.setBlock(new BlockPos(ox, baseY, oz + i), cobblePath, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox - 1, baseY, oz + i), cobblePath, StructureHelper.SET_FLAGS);
            // E-W path (z = center, vary x)
            world.setBlock(new BlockPos(ox + i, baseY, oz), cobblePath, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + i, baseY, oz - 1), cobblePath, StructureHelper.SET_FLAGS);
        }

        // === BELL at crossroads intersection ===
        // Bell needs a support block above it
        world.setBlock(new BlockPos(ox, baseY + 1, oz), logWall, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, baseY, oz), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

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
                world.setBlock(new BlockPos(ox + x, baseY + y, lhOz - lhHalfD), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + x, baseY + y, lhOz + lhHalfD), logWall, StructureHelper.SET_FLAGS);
            }
        }
        for (int z = -lhHalfD; z <= lhHalfD; z++) {
            for (int y = 1; y <= lhWallH; y++) {
                world.setBlock(new BlockPos(ox - lhHalfW, baseY + y, lhOz + z), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + lhHalfW, baseY + y, lhOz + z), logWall, StructureHelper.SET_FLAGS);
            }
        }
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
                world.setBlock(new BlockPos(ox - rw, baseY + lhWallH + y, lhOz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + rw, baseY + lhWallH + y, lhOz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
        }
        // Ridge beam
        for (int z = -lhHalfD - 1; z <= lhHalfD + 1; z++) {
            world.setBlock(new BlockPos(ox, baseY + lhWallH + lhRoofPeak, lhOz + z), logWall, StructureHelper.SET_FLAGS);
        }
        // Longhouse entrance (south side, facing crossroads)
        for (int dx = -1; dx <= 0; dx++) {
            for (int y = 2; y <= 3; y++) {
                world.setBlock(new BlockPos(ox + dx, baseY + y, lhOz + lhHalfD), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        // Campfire hearth inside longhouse
        world.setBlock(new BlockPos(ox, baseY + 1, lhOz), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Chief's seat (facing south toward door)
        world.setBlock(new BlockPos(ox, baseY + 1, lhOz - lhHalfD + 1),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // 2 beds
        world.setBlock(new BlockPos(ox - lhHalfW + 1, baseY + 1, lhOz - 1), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - lhHalfW + 2, baseY + 1, lhOz - 1), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - lhHalfW + 1, baseY + 1, lhOz + 1), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - lhHalfW + 2, baseY + 1, lhOz + 1), palette.bed.defaultBlockState()
            .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        // Chests in longhouse
        StructureHelper.placeChest(world, new BlockPos(ox + lhHalfW - 1, baseY + 1, lhOz - 1),
            Direction.WEST, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);
        StructureHelper.placeChest(world, new BlockPos(ox + lhHalfW - 1, baseY + 1, lhOz + 1),
            Direction.WEST, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);

        // === CORNER WATCHTOWER PLATFORMS ===
        // 4 diagonal positions on the palisade
        int[][] corners = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        double diagDist = fortRadius * 0.707; // cos(45) ~ 0.707
        for (int[] c : corners) {
            int tx = ox + (int)(diagDist * c[0]);
            int tz = oz + (int)(diagDist * c[1]);

            // 4 spruce log stilts (3x3 base)
            for (int y = 0; y <= 4; y++) {
                world.setBlock(new BlockPos(tx - 1, baseY + y, tz - 1), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(tx + 1, baseY + y, tz - 1), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(tx - 1, baseY + y, tz + 1), logWall, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(tx + 1, baseY + y, tz + 1), logWall, StructureHelper.SET_FLAGS);
            }
            // 3x3 platform at palisade-top height (baseY+5)
            for (int px = -1; px <= 1; px++) {
                for (int pz = -1; pz <= 1; pz++) {
                    world.setBlock(new BlockPos(tx + px, baseY + 5, tz + pz), planks, StructureHelper.SET_FLAGS);
                }
            }
            // Fence railing around platform edges
            for (int px = -1; px <= 1; px++) {
                world.setBlock(new BlockPos(tx + px, baseY + 6, tz - 1), fenceBlock, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(tx + px, baseY + 6, tz + 1), fenceBlock, StructureHelper.SET_FLAGS);
            }
            world.setBlock(new BlockPos(tx - 1, baseY + 6, tz), fenceBlock, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(tx + 1, baseY + 6, tz), fenceBlock, StructureHelper.SET_FLAGS);
            // Lantern on platform
            world.setBlock(new BlockPos(tx, baseY + 6, tz),
                Blocks.LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        }

        // === WALL TORCHES on interior palisade ===
        for (int angle = 0; angle < 360; angle += 20) {
            double rad = Math.toRadians(angle);
            int torchX = ox + (int) Math.round((fortRadius - 2) * Math.cos(rad));
            int torchZ = oz + (int) Math.round((fortRadius - 2) * Math.sin(rad));

            // Determine which direction the torch should face (inward toward center)
            Direction torchFacing;
            double dx = torchX - ox;
            double dz = torchZ - oz;
            if (Math.abs(dx) > Math.abs(dz)) {
                torchFacing = dx > 0 ? Direction.WEST : Direction.EAST;
            } else {
                torchFacing = dz > 0 ? Direction.NORTH : Direction.SOUTH;
            }
            world.setBlock(new BlockPos(torchX, baseY + 3, torchZ),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, torchFacing), StructureHelper.SET_FLAGS);
        }

        placeJigsawConnectors(world, center, fortRadius);

        VillageCastles.LOGGER.debug("Taiga ring fort generation complete!");
        return new CastleBounds(
            center.offset(-fortRadius - 2, 0, -fortRadius - 2),
            center.offset(fortRadius + 2, lhWallH + lhRoofPeak + 5, fortRadius + 2)
        );
    }

    // ==========================================================================
    // TAIGA LARGE: Jarl's Tower-House Fortress
    // ==========================================================================

    /**
     * TAIGA LARGE: Jarl's tower-house fortress.
     * Scottish highland tower-house + stave church influence.
     * Narrow, tall main tower (9x7, 18 blocks to wall top), battered stone base,
     * 4 floors (great hall, armory, bedchamber, battlement), multi-tiered steep roof,
     * crow-stepped gables, tight courtyard palisade, furnished interiors.
     */
    private CastleBounds generateTaigaTowerHouse(ServerLevel world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        BlockState logWall = palette.log.defaultBlockState();
        BlockState strippedLog = Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState();
        BlockState mossyCobble = palette.getSecondaryWallState();
        BlockState cobble = palette.getPrimaryWallState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState planks = palette.getPlanksState();
        BlockState slabBlock = palette.woodSlab.defaultBlockState();
        BlockState fenceBlock = palette.fence.defaultBlockState();
        BlockState roofBlock = palette.getRoofState();
        BlockState ironBars = Blocks.IRON_BARS.defaultBlockState();
        BlockState cobbleWall = Blocks.COBBLESTONE_WALL.defaultBlockState();
        BlockState cobbleStairs = Blocks.COBBLESTONE_STAIRS.defaultBlockState();

        // Tower dimensions
        int tHalfW = 4;  // east-west half (9 blocks wide)
        int tHalfD = 3;  // north-south half (7 blocks deep)
        int wallTop = 18;
        int floorHeight = 4; // each floor is 4 blocks (3 air + 1 floor)

        // Courtyard palisade
        int courtRadius = tHalfW + 6; // 5-6 blocks clearance

        prepareGround(world, center, courtRadius + 2);

        // ============================================
        // MAIN TOWER: Stone brick, 4 floors
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
                // North face stairs: FACING=SOUTH (back faces south = you walk up going south)
                for (int x = -tHalfW - expand; x <= tHalfW + expand; x++) {
                    world.setBlock(new BlockPos(ox + x, y, oz - tHalfD - expand),
                        cobbleStairs.setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.TOP),
                        StructureHelper.SET_FLAGS);
                }
                // South face stairs
                for (int x = -tHalfW - expand; x <= tHalfW + expand; x++) {
                    world.setBlock(new BlockPos(ox + x, y, oz + tHalfD + expand),
                        cobbleStairs.setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.TOP),
                        StructureHelper.SET_FLAGS);
                }
                // West face stairs
                for (int z = -tHalfD - expand + 1; z <= tHalfD + expand - 1; z++) {
                    world.setBlock(new BlockPos(ox - tHalfW - expand, y, oz + z),
                        cobbleStairs.setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP),
                        StructureHelper.SET_FLAGS);
                }
                // East face stairs
                for (int z = -tHalfD - expand + 1; z <= tHalfD + expand - 1; z++) {
                    world.setBlock(new BlockPos(ox + tHalfW + expand, y, oz + z),
                        cobbleStairs.setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP),
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
                        world.setBlock(mutable, stoneBricks, StructureHelper.SET_FLAGS);
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
                world.setBlock(new BlockPos(stairX, nextFloor, stairZ), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(stairX + 1, nextFloor, stairZ), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
            // 4 stairs going up (along z then x)
            // Step 1: at floorBase+1, facing NORTH (walking north = up)
            world.setBlock(new BlockPos(stairX, floorBase + 1, stairZ + 1),
                palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            // Step 2: at floorBase+2
            world.setBlock(new BlockPos(stairX, floorBase + 2, stairZ),
                palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            // Step 3: at floorBase+3, landing (slab)
            world.setBlock(new BlockPos(stairX, floorBase + 3, stairZ), slabBlock, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(stairX + 1, floorBase + 3, stairZ), slabBlock, StructureHelper.SET_FLAGS);
            // Step 4: at floorBase+4, final step onto next floor
            world.setBlock(new BlockPos(stairX + 1, floorBase + 4, stairZ),
                palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }

        // --- Arrow slits (IRON_BARS) on lower floors (floors 1-2), shuttered windows (TRAPDOOR) on upper ---
        // Floor 1 (great hall): y = baseY+3 (midway)
        // Floor 2 (armory): y = baseY+7
        for (int floorY : new int[]{baseY + 3, baseY + 7}) {
            // North and south walls
            for (int x = -tHalfW + 2; x <= tHalfW - 2; x += 3) {
                world.setBlock(new BlockPos(ox + x, floorY, oz - tHalfD), ironBars, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + x, floorY, oz + tHalfD), ironBars, StructureHelper.SET_FLAGS);
            }
            // East and west walls
            for (int z = -tHalfD + 1; z <= tHalfD - 1; z += 2) {
                world.setBlock(new BlockPos(ox - tHalfW, floorY, oz + z), ironBars, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + tHalfW, floorY, oz + z), ironBars, StructureHelper.SET_FLAGS);
            }
        }

        // Shuttered windows on upper floors (floors 3-4)
        BlockState trapdoor = palette.trapdoor.defaultBlockState();
        for (int floorY : new int[]{baseY + 11, baseY + 15}) {
            // North and south walls
            for (int x = -tHalfW + 2; x <= tHalfW - 2; x += 3) {
                world.setBlock(new BlockPos(ox + x, floorY, oz - tHalfD),
                    trapdoor.setValue(TrapDoorBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + x, floorY, oz + tHalfD),
                    trapdoor.setValue(TrapDoorBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
            }
            // East and west walls
            for (int z = -tHalfD + 1; z <= tHalfD - 1; z += 2) {
                world.setBlock(new BlockPos(ox - tHalfW, floorY, oz + z),
                    trapdoor.setValue(TrapDoorBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + tHalfW, floorY, oz + z),
                    trapdoor.setValue(TrapDoorBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            }
        }

        // --- Tower entrance (south side, ground floor) ---
        for (int dx = -1; dx <= 0; dx++) {
            for (int y = 2; y <= 4; y++) {
                world.setBlock(new BlockPos(ox + dx, baseY + y, oz + tHalfD), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }

        Openings.door(world, new BlockPos(ox - 1, baseY + 2, oz + tHalfD), Direction.NORTH,
            palette.door.defaultBlockState(), null, palette.getPlanksState(), true);

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
                world.setBlock(new BlockPos(ox - rw, roofBase + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + rw, roofBase + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
            // Clear interior under roof
            if (rw > 0) {
                for (int z = -tHalfD; z <= tHalfD; z++) {
                    for (int rx = -rw + 1; rx <= rw - 1; rx++) {
                        mutable.set(ox + rx, roofBase + y, oz + z);
                        world.setBlock(mutable, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        // --- 1-block gap of spruce fence (visible wall between tiers) ---
        int gapY = roofBase + tier1Peak + 1;
        for (int z = -tHalfD; z <= tHalfD; z++) {
            world.setBlock(new BlockPos(ox - 1, gapY, oz + z), fenceBlock, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 1, gapY, oz + z), fenceBlock, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox, gapY, oz + z), fenceBlock, StructureHelper.SET_FLAGS);
        }

        // --- Second tier: smaller roof on top ---
        int tier2Base = gapY + 1;
        int tier2HalfW = 2;
        int tier2Peak = tier2HalfW;
        for (int y = 0; y <= tier2Peak; y++) {
            int rw = tier2HalfW - y;
            if (rw < 0) break;
            for (int z = -tHalfD; z <= tHalfD; z++) {
                world.setBlock(new BlockPos(ox - rw, tier2Base + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(ox + rw, tier2Base + y, oz + z), roofBlock, StructureHelper.SET_FLAGS);
            }
        }

        // --- Spruce log spire at peak ---
        int spireBase = tier2Base + tier2Peak;
        for (int y = 0; y <= 3; y++) {
            world.setBlock(new BlockPos(ox, spireBase + y, oz), logWall, StructureHelper.SET_FLAGS);
        }

        // --- Ridge beams (first tier) ---
        for (int z = -tHalfD - 1; z <= tHalfD + 1; z++) {
            world.setBlock(new BlockPos(ox, roofBase + tier1Peak, oz + z), logWall, StructureHelper.SET_FLAGS);
        }

        // --- Crow-stepped gables on north and south ends ---
        // Cobblestone blocks stepping up with the roof in 1-block increments
        for (int step = 0; step <= tier1Peak; step++) {
            int gableX = tier1HalfW - step;
            if (gableX < 0) break;
            // North gable
            world.setBlock(new BlockPos(ox - gableX, roofBase + step, oz - tHalfD - 1), cobble, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + gableX, roofBase + step, oz - tHalfD - 1), cobble, StructureHelper.SET_FLAGS);
            // South gable
            world.setBlock(new BlockPos(ox - gableX, roofBase + step, oz + tHalfD + 1), cobble, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + gableX, roofBase + step, oz + tHalfD + 1), cobble, StructureHelper.SET_FLAGS);
        }

        // ============================================
        // 4TH FLOOR: BATTLEMENT LOOKOUT (open roof)
        // ============================================
        int battleY = baseY + 13; // furniture level on 4th floor (baseY+12 floor + 1)
        // Cobblestone wall parapet around tower top
        for (int x = -tHalfW; x <= tHalfW; x++) {
            world.setBlock(new BlockPos(ox + x, baseY + wallTop + 1, oz - tHalfD), cobbleWall, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + x, baseY + wallTop + 1, oz + tHalfD), cobbleWall, StructureHelper.SET_FLAGS);
        }
        for (int z = -tHalfD; z <= tHalfD; z++) {
            world.setBlock(new BlockPos(ox - tHalfW, baseY + wallTop + 1, oz + z), cobbleWall, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + tHalfW, baseY + wallTop + 1, oz + z), cobbleWall, StructureHelper.SET_FLAGS);
        }
        // Lanterns at battlement corners
        world.setBlock(new BlockPos(ox - tHalfW, baseY + wallTop + 2, oz - tHalfD),
            Blocks.LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + tHalfW, baseY + wallTop + 2, oz - tHalfD),
            Blocks.LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - tHalfW, baseY + wallTop + 2, oz + tHalfD),
            Blocks.LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + tHalfW, baseY + wallTop + 2, oz + tHalfD),
            Blocks.LANTERN.defaultBlockState(), StructureHelper.SET_FLAGS);

        // ============================================
        // FLOOR 1: GREAT HALL (baseY floor, baseY+1 furniture)
        // ============================================
        int ghFurn = baseY + 1;
        // Central hearth (campfire with cobble border)
        world.setBlock(new BlockPos(ox, ghFurn, oz), Blocks.CAMPFIRE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - 1, ghFurn, oz), Blocks.COBBLESTONE_SLAB.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + 1, ghFurn, oz), Blocks.COBBLESTONE_SLAB.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Long table (spruce slabs)
        for (int z = -1; z <= 1; z++) {
            world.setBlock(new BlockPos(ox + 2, ghFurn, oz + z), slabBlock, StructureHelper.SET_FLAGS);
        }
        // Chief's seat (east side of table, facing west across hall)
        world.setBlock(new BlockPos(ox + 3, ghFurn, oz),
            palette.woodStairs.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        // Tapestry (banner on north wall): use a wall torch as placeholder since banners are complex
        world.setBlock(new BlockPos(ox, ghFurn + 1, oz - tHalfD + 1),
            Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        // Chests flanking entrance
        StructureHelper.placeChest(world, new BlockPos(ox + tHalfW - 1, ghFurn, oz + tHalfD - 1),
            Direction.WEST, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);
        StructureHelper.placeChest(world, new BlockPos(ox - tHalfW + 1, ghFurn, oz + tHalfD - 1),
            Direction.EAST, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);

        // ============================================
        // FLOOR 2: ARMORY (baseY+4 floor, baseY+5 furniture)
        // ============================================
        int armFurn = baseY + 5;
        world.setBlock(new BlockPos(ox + tHalfW - 1, armFurn, oz - tHalfD + 1),
            Blocks.GRINDSTONE.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + tHalfW - 1, armFurn, oz),
            Blocks.ANVIL.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox + tHalfW - 1, armFurn, oz + tHalfD - 1),
            Blocks.SMITHING_TABLE.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Armor stands (fence + banner approximation)
        world.setBlock(new BlockPos(ox - tHalfW + 1, armFurn, oz), fenceBlock, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - tHalfW + 1, armFurn + 1, oz), fenceBlock, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - tHalfW + 1, armFurn, oz + 2), fenceBlock, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - tHalfW + 1, armFurn + 1, oz + 2), fenceBlock, StructureHelper.SET_FLAGS);
        // Armory chest
        StructureHelper.placeChest(world, new BlockPos(ox, armFurn, oz + tHalfD - 1),
            Direction.NORTH, BuiltInLootTables.VILLAGE_WEAPONSMITH);

        // ============================================
        // FLOOR 3: BEDCHAMBER (baseY+8 floor, baseY+9 furniture)
        // ============================================
        int bedFurn = baseY + 9;
        // 3 beds along east wall
        for (int dz = -1; dz <= 1; dz++) {
            world.setBlock(new BlockPos(ox + tHalfW - 1, bedFurn, oz + dz), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.FOOT).setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + tHalfW - 2, bedFurn, oz + dz), palette.bed.defaultBlockState()
                .setValue(BedBlock.PART, BedPart.HEAD).setValue(BedBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }
        world.setBlock(new BlockPos(ox - tHalfW + 1, bedFurn, oz - tHalfD + 1), Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox - tHalfW + 1, bedFurn + 1, oz - tHalfD + 1), Blocks.BOOKSHELF.defaultBlockState(), StructureHelper.SET_FLAGS);
        // Personal effects chest
        StructureHelper.placeChest(world, new BlockPos(ox - tHalfW + 1, bedFurn, oz + tHalfD - 1),
            Direction.EAST, BuiltInLootTables.VILLAGE_TAIGA_HOUSE);
        world.setBlock(new BlockPos(ox, bedFurn, oz), palette.carpet.defaultBlockState(), StructureHelper.SET_FLAGS);

        // ============================================
        // COURTYARD PALISADE: tight around tower
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
                world.setBlock(new BlockPos(wx, baseY, wz), mossyCobble, StructureHelper.SET_FLAGS);
                world.setBlock(new BlockPos(wx, baseY + 1, wz), mossyCobble, StructureHelper.SET_FLAGS);
                // Spruce log palisade
                for (int y = 2; y <= 4; y++) {
                    world.setBlock(new BlockPos(wx, baseY + y, wz), logWall, StructureHelper.SET_FLAGS);
                }
            }
        }

        // === SOUTH GATE: timber-framed tunnel ===
        // Gate posts
        for (int y = 0; y <= 5; y++) {
            world.setBlock(new BlockPos(ox - 2, baseY + y, oz + courtRadius), logWall, StructureHelper.SET_FLAGS);
            world.setBlock(new BlockPos(ox + 2, baseY + y, oz + courtRadius), logWall, StructureHelper.SET_FLAGS);
        }
        // Lintel
        for (int dx = -1; dx <= 1; dx++) {
            world.setBlock(new BlockPos(ox + dx, baseY + 4, oz + courtRadius), logWall, StructureHelper.SET_FLAGS);
        }
        // Clear gate passage
        for (int dx = -1; dx <= 1; dx++) {
            for (int y = 1; y <= 3; y++) {
                world.setBlock(new BlockPos(ox + dx, baseY + y, oz + courtRadius), Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
        }
        // Fence gates
        for (int dx = -1; dx <= 1; dx++) {
            world.setBlock(new BlockPos(ox + dx, baseY + 1, oz + courtRadius),
                palette.fenceGate.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }

        // === BELL in courtyard (south of tower) ===
        world.setBlock(new BlockPos(ox, baseY + 1, oz + tHalfD + 2), logWall, StructureHelper.SET_FLAGS);
        world.setBlock(new BlockPos(ox, baseY, oz + tHalfD + 2), Blocks.BELL.defaultBlockState(), StructureHelper.SET_FLAGS);

        // === COURTYARD TORCHES ===
        // Wall torches on interior of palisade walls
        // North wall interior (facing SOUTH)
        for (int x = -courtRadius + 3; x <= courtRadius - 3; x += 4) {
            world.setBlock(new BlockPos(ox + x, baseY + 3, oz - courtRadius + 1),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH), StructureHelper.SET_FLAGS);
        }
        // South wall interior (facing NORTH)
        for (int x = -courtRadius + 3; x <= courtRadius - 3; x += 4) {
            world.setBlock(new BlockPos(ox + x, baseY + 3, oz + courtRadius - 1),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), StructureHelper.SET_FLAGS);
        }
        // East wall interior (facing WEST)
        for (int z = -courtRadius + 3; z <= courtRadius - 3; z += 4) {
            world.setBlock(new BlockPos(ox + courtRadius - 1, baseY + 3, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST), StructureHelper.SET_FLAGS);
        }
        // West wall interior (facing EAST)
        for (int z = -courtRadius + 3; z <= courtRadius - 3; z += 4) {
            world.setBlock(new BlockPos(ox - courtRadius + 1, baseY + 3, oz + z),
                Blocks.WALL_TORCH.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST), StructureHelper.SET_FLAGS);
        }

        int boundsRadius = courtRadius + 2;
        placeJigsawConnectors(world, center, boundsRadius);

        VillageCastles.LOGGER.debug("Taiga tower-house fortress generation complete!");
        return new CastleBounds(
            center.offset(-boundsRadius, -1, -boundsRadius),
            center.offset(boundsRadius, spireBase + 3 + 2, boundsRadius)
        );
    }

    /**
     * Generate a perimeter fence around a small tower keep.
     * Places fence posts in a rectangle around the keep with a fence gate entrance
     * on the south side and torch-topped fence posts at the corners.
     */
    private void generatePerimeterFence(ServerLevel world, BlockPos center, int radius,
                                         int keepHalfWidth, int keepHalfDepth) {
        int fenceHalfX = keepHalfWidth + 5;
        int fenceHalfZ = keepHalfDepth + 5;
        int baseY = center.getY();

        BlockState fenceState = palette.getFenceState();
        BlockState lanternState = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);
        BlockState gateState = palette.getFenceGateState().setValue(FenceGateBlock.FACING, Direction.SOUTH);

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

        // North and south fence lines
        for (int x = -fenceHalfX; x <= fenceHalfX; x++) {
            // North side
            mutable.set(center.getX() + x, baseY + 1, center.getZ() - fenceHalfZ);
            world.setBlock(mutable, fenceState, StructureHelper.SET_FLAGS);

            // South side: leave a 2-wide gap in the center for the fence gate
            int absX = Math.abs(x);
            if (absX > 1) {
                mutable.set(center.getX() + x, baseY + 1, center.getZ() + fenceHalfZ);
                world.setBlock(mutable, fenceState, StructureHelper.SET_FLAGS);
            }
        }

        // South fence gate (2 blocks wide, centered)
        mutable.set(center.getX(), baseY + 1, center.getZ() + fenceHalfZ);
        world.setBlock(mutable, gateState, StructureHelper.SET_FLAGS);
        mutable.set(center.getX() - 1, baseY + 1, center.getZ() + fenceHalfZ);
        world.setBlock(mutable, gateState, StructureHelper.SET_FLAGS);

        // East and west fence lines
        for (int z = -fenceHalfZ + 1; z <= fenceHalfZ - 1; z++) {
            // West side
            mutable.set(center.getX() - fenceHalfX, baseY + 1, center.getZ() + z);
            world.setBlock(mutable, fenceState, StructureHelper.SET_FLAGS);

            // East side
            mutable.set(center.getX() + fenceHalfX, baseY + 1, center.getZ() + z);
            world.setBlock(mutable, fenceState, StructureHelper.SET_FLAGS);
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
            world.setBlock(corner.above(1), fenceState, StructureHelper.SET_FLAGS);
            world.setBlock(corner.above(2), lanternState, StructureHelper.SET_FLAGS);
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
     * Terminal: no further pieces chain from the castle.
     */
    private void placeJigsawConnectors(ServerLevel world, BlockPos center, int perimeterRadius) {
        ResourceKey<StructureTemplatePool> emptyPool = ResourceKey.create(
            Registries.TEMPLATE_POOL,
            Identifier.fromNamespaceAndPath("minecraft", "empty")
        );

        // Single entrance connector at the south edge of the export bounds
        int r = perimeterRadius + 2; // matches export bounds edge
        placeJigsaw(world, center.offset(0, 0, r), Direction.SOUTH, emptyPool);
    }

    /**
     * Place and configure a jigsaw block for village street connections.
     */
    private void placeJigsaw(ServerLevel world, BlockPos pos, Direction facing,
                              ResourceKey<StructureTemplatePool> targetPool) {
        FrontAndTop orientation = orientationFromFacing(facing);

        world.setBlock(pos, Blocks.JIGSAW.defaultBlockState()
            .setValue(JigsawBlock.ORIENTATION, orientation),
            StructureHelper.SET_FLAGS);

        if (world.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
            jigsaw.setPool(targetPool);
            jigsaw.setName(Identifier.fromNamespaceAndPath("minecraft", "bottom"));
            jigsaw.setTarget(Identifier.fromNamespaceAndPath("minecraft", "bottom"));
            jigsaw.setFinalState(JIGSAW_FINAL_DIRT_PATH);
            jigsaw.setJoint(JigsawBlockEntity.JointType.ROLLABLE);
            jigsaw.setChanged();
        }
    }

    /**
     * Place and configure a jigsaw block for wall chain connections.
     */
    private void placeWallJigsaw(ServerLevel world, BlockPos pos, Direction facing,
                                  ResourceKey<StructureTemplatePool> targetPool) {
        FrontAndTop orientation = orientationFromFacing(facing);

        world.setBlock(pos, Blocks.JIGSAW.defaultBlockState()
            .setValue(JigsawBlock.ORIENTATION, orientation),
            StructureHelper.SET_FLAGS);

        if (world.getBlockEntity(pos) instanceof JigsawBlockEntity jigsaw) {
            jigsaw.setPool(targetPool);
            jigsaw.setName(Identifier.fromNamespaceAndPath("villagecastles", "wall_end"));
            jigsaw.setTarget(Identifier.fromNamespaceAndPath("villagecastles", "wall_end"));
            jigsaw.setFinalState(JIGSAW_FINAL_AIR);
            jigsaw.setJoint(JigsawBlockEntity.JointType.ALIGNED);
            jigsaw.setChanged();
        }
    }

    private static FrontAndTop orientationFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> FrontAndTop.NORTH_UP;
            case SOUTH -> FrontAndTop.SOUTH_UP;
            case EAST -> FrontAndTop.EAST_UP;
            case WEST -> FrontAndTop.WEST_UP;
            default -> FrontAndTop.NORTH_UP;
        };
    }

    /**
     * Clear the build space and lay the single course the castle stands on.
     *
     * <p>No foundation. A castle template carries the building from its own floor course up and
     * nothing below it: {@code CastleGroundsPiece} underfills each footprint column down to real
     * ground at placement time, using the block the castle itself put down. Baking a plinth into
     * the template instead made every castle stamp a flat pad into whatever terrain it landed on
     * (this method used to lay five courses of cobblestone across {@code (radius+2)^2}, which is
     * why desert/castle_large shipped 56% cobblestone and coarse dirt was 16% of every block the
     * mod placed) and is exactly what VISION.md rules out.
     *
     * <p>The one remaining course is the castle's floor, not a footing, and it is scoped to the
     * footprint rather than overhanging it. Generators that lay their own floors do not need this
     * call at all.
     */
    private void prepareGround(ServerLevel world, BlockPos center, int radius) {
        int baseY = center.getY();
        int ox = center.getX();
        int oz = center.getZ();

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState floorState = palette.getFloorState();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int wx = ox + x;
                int wz = oz + z;

                mutable.set(wx, baseY, wz);
                world.setBlock(mutable, floorState, StructureHelper.SET_FLAGS);

                for (int y = 1; y <= 40; y++) {
                    mutable.set(wx, baseY + y, wz);
                    if (!world.getBlockState(mutable).isAir()) {
                        world.setBlock(mutable, air, StructureHelper.SET_FLAGS);
                    }
                }
            }
        }

        VillageCastles.LOGGER.debug("Ground prepared: {}x{} area levelled", radius * 2 + 1, radius * 2 + 1);
    }


    private void generateWalls(ServerLevel world, BlockPos nwTower, BlockPos neTower,
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

    private void addWallTowers(ServerLevel world, BlockPos center, int radius) {
        int towerOffset = towerGenerator.getAdjustedRadius(TowerGenerator.TowerType.CORNER);

        // Midpoint of east wall
        BlockPos eastMid = center.offset(radius - towerOffset, 0, 0);
        towerGenerator.generate(world, eastMid, TowerGenerator.TowerType.WALL);

        // Midpoint of west wall
        BlockPos westMid = center.offset(-radius + towerOffset, 0, 0);
        towerGenerator.generate(world, westMid, TowerGenerator.TowerType.WALL);

        if (size == CastleSize.LARGE) {
            // Extra towers on long walls
            BlockPos eastNorth = center.offset(radius - towerOffset, 0, -radius / 3);
            BlockPos eastSouth = center.offset(radius - towerOffset, 0, radius / 3);
            towerGenerator.generate(world, eastNorth, TowerGenerator.TowerType.WATCH);
            towerGenerator.generate(world, eastSouth, TowerGenerator.TowerType.WATCH);

            BlockPos westNorth = center.offset(-radius + towerOffset, 0, -radius / 3);
            BlockPos westSouth = center.offset(-radius + towerOffset, 0, radius / 3);
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
