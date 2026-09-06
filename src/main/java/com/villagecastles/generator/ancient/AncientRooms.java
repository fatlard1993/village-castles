package com.villagecastles.generator.ancient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The interior program every landform shares: halls, crypts, wells, stair towers, vaults, and the
 * garrison's cells. All of it draws into the plan through {@link Geo}; the landform generators
 * decide where these rooms go, this class decides what they are.
 */
public final class AncientRooms {

    private AncientRooms() {}

    /**
     * A rectangular great hall: paved floor, eroded masonry walls, a colonnade down each long
     * side, a dais at the far end. The roof is copper when it stands; when it doesn't, fallen
     * beams and open sky.
     *
     * @return the dais chest position, for the caller's loot bookkeeping.
     */
    public static BlockPos greatHall(Plan plan, int minX, int minZ, int maxX, int maxZ, int floorY,
                                     int wallHeight, Direction daisEnd, CastlePalette palette,
                                     RandomSource random) {
        long seed = palette.seed();
        Condition condition = palette.condition();

        Geo.floor(plan, minX, minZ, maxX, maxZ, floorY, palette.paving());
        Geo.box(plan, minX, floorY + 1, minZ, maxX, floorY + wallHeight, maxZ, Geo.AIR);

        // Walls drawn column by column so erosion can shorten them together in patches.
        for (int[] o : wallColumns(minX, minZ, maxX, maxZ)) {
            int top = Erode.columnTop(seed, condition, o[0], o[1], floorY + 1, wallHeight);
            for (int y = floorY + 1; y <= top; y++) {
                if (Erode.bitten(seed, condition, o[0], y, o[1], floorY + 1, wallHeight)) continue;
                plan.set(o[0], y, o[1], palette.masonryAt(o[0], y, o[1]));
            }
        }

        // Colonnade: paired pillars two blocks in from each long wall, every third bay fallen in
        // any state but weathered - its drum laid sideways as rubble beside the stump.
        boolean alongX = (maxX - minX) >= (maxZ - minZ);
        int lowA = alongX ? minZ + 2 : minX + 2;
        int lowB = alongX ? maxZ - 2 : maxX - 2;
        int from = (alongX ? minX : minZ) + 3;
        int to = (alongX ? maxX : maxZ) - 3;
        int bay = 0;
        for (int d = from; d <= to; d += 3, bay++) {
            for (int lane : new int[] {lowA, lowB}) {
                int x = alongX ? d : lane;
                int z = alongX ? lane : d;
                boolean fallen = !condition.intact() && bay % 3 == 2;
                int top = fallen ? floorY + 1 + random.nextInt(2) : floorY + wallHeight - 2;
                Geo.column(plan, x, z, floorY + 1, top, Geo.solid(palette.monolith()));
                if (fallen) {
                    int dx = alongX ? 1 : 0;
                    int dz = alongX ? 0 : 1;
                    plan.set(x + dx, floorY + 1, z + dz, palette.rubble(random));
                    plan.set(x + dx * 2, floorY + 1, z + dz * 2, palette.rubble(random));
                }
            }
        }

        roof(plan, minX, minZ, maxX, maxZ, floorY, floorY + wallHeight, palette, random);

        // Dais: two dressed steps against the chosen end, the hall's chest on top.
        BlockPos centre = new BlockPos((minX + maxX) / 2, floorY, (minZ + maxZ) / 2);
        int daisX = daisEnd.getStepX() == 0 ? centre.getX()
            : (daisEnd.getStepX() > 0 ? maxX - 2 : minX + 2);
        int daisZ = daisEnd.getStepZ() == 0 ? centre.getZ()
            : (daisEnd.getStepZ() > 0 ? maxZ - 2 : minZ + 2);
        Geo.box(plan, daisX - 1, floorY + 1, daisZ - 1, daisX + 1, floorY + 1, daisZ + 1,
            Geo.solid(palette.dressed()));
        return new BlockPos(daisX, floorY + 2, daisZ);
    }

    /**
     * The hall's roof: copper hips stepping in from the eaves when the condition keeps roofs,
     * scattered fallen beams inside when it doesn't.
     */
    private static void roof(Plan plan, int minX, int minZ, int maxX, int maxZ, int floorY,
                             int eaveY, CastlePalette palette, RandomSource random) {
        if (palette.condition().roofsIntact()) {
            int inset = 0;
            int y = eaveY;
            while (minX + inset <= maxX - inset && minZ + inset <= maxZ - inset) {
                for (int x = minX + inset; x <= maxX - inset; x++) {
                    for (int z = minZ + inset; z <= maxZ - inset; z++) {
                        boolean rim = x == minX + inset || x == maxX - inset
                            || z == minZ + inset || z == maxZ - inset;
                        if (rim) plan.set(x, y, z, palette.roof());
                    }
                }
                inset += 2;
                y++;
            }
            return;
        }
        // Fallen beams: whole trunks lying across the floor where the roof came down.
        int beams = 2 + random.nextInt(3);
        for (int i = 0; i < beams; i++) {
            int bx = minX + 2 + random.nextInt(Math.max(1, maxX - minX - 3));
            int bz = minZ + 2 + random.nextInt(Math.max(1, maxZ - minZ - 3));
            int len = 3 + random.nextInt(3);
            boolean alongX = random.nextBoolean();
            for (int d = 0; d < len; d++) {
                int x = alongX ? Math.min(bx + d, maxX - 1) : bx;
                int z = alongX ? bz : Math.min(bz + d, maxZ - 1);
                plan.set(x, floorY + 1, z, palette.beam());
            }
        }
    }

    /**
     * A corridor crypt driven horizontally from a mouth: cave-aired bore, paved floor, chest and
     * dig alcoves, and the garrison's spawner cell sealed at the far end.
     */
    public static void crypt(Plan plan, BlockPos mouth, Direction into, int length,
                             CastlePalette palette, RandomSource random,
                             ResourceKey<LootTable> alcoveTable, EntityType<?> garrison,
                             boolean withSpawner) {
        Direction across = into.getClockWise();
        for (int d = 0; d < length; d++) {
            int cx = mouth.getX() + into.getStepX() * d;
            int cz = mouth.getZ() + into.getStepZ() * d;
            for (int w = -1; w <= 1; w++) {
                int x = cx + across.getStepX() * w;
                int z = cz + across.getStepZ() * w;
                plan.set(x, mouth.getY() - 1, z, palette.floorAt(x, mouth.getY() - 1, z));
                for (int h = 0; h < 3; h++) {
                    plan.set(x, mouth.getY() + h, z, Blocks.CAVE_AIR.defaultBlockState());
                }
                plan.set(x, mouth.getY() + 3, z, palette.trimAt(x, mouth.getY() + 3, z));
            }
            // Alcoves every fourth stride, alternating sides.
            if (d > 1 && d % 4 == 0 && d < length - 2) {
                Direction side = (d / 4) % 2 == 0 ? across : across.getOpposite();
                int ax = cx + side.getStepX() * 2;
                int az = cz + side.getStepZ() * 2;
                plan.set(ax, mouth.getY(), az, Blocks.CAVE_AIR.defaultBlockState());
                plan.set(ax, mouth.getY() + 1, az, Blocks.CAVE_AIR.defaultBlockState());
                if (random.nextBoolean()) {
                    plan.chest(new BlockPos(ax, mouth.getY(), az), side.getOpposite(), alcoveTable);
                } else {
                    plan.dig(new BlockPos(ax, mouth.getY() - 1, az),
                        Blocks.SUSPICIOUS_GRAVEL.defaultBlockState(),
                        net.minecraft.world.level.storage.loot.BuiltInLootTables
                            .TRAIL_RUINS_ARCHAEOLOGY_RARE);
                }
            }
        }
        // Trilithon mouth: monolith jambs and a lintel framing the entrance.
        int mx = mouth.getX(), mz = mouth.getZ();
        for (int w : new int[] {-2, 2}) {
            Geo.column(plan, mx + across.getStepX() * w, mz + across.getStepZ() * w,
                mouth.getY(), mouth.getY() + 2, Geo.solid(palette.monolith()));
        }
        for (int w = -2; w <= 2; w++) {
            plan.set(mx + across.getStepX() * w, mouth.getY() + 3, mz + across.getStepZ() * w,
                palette.dressed());
        }

        if (withSpawner) {
            BlockPos end = new BlockPos(
                mouth.getX() + into.getStepX() * (length + 2),
                mouth.getY(),
                mouth.getZ() + into.getStepZ() * (length + 2));
            spawnerCell(plan, end, palette, garrison);
        }
    }

    /**
     * A sealed 5x5x5 shell with the spawner at its heart. Written into the plan rather than placed
     * directly, so a cell straddling a chunk boundary is clipped by the same code that clips
     * everything else.
     */
    public static void spawnerCell(Plan plan, BlockPos centre, CastlePalette palette,
                                   EntityType<?> garrison) {
        Geo.box(plan, centre.getX() - 2, centre.getY() - 2, centre.getZ() - 2,
            centre.getX() + 2, centre.getY() + 2, centre.getZ() + 2, palette.masonry());
        Geo.box(plan, centre.getX() - 1, centre.getY() - 1, centre.getZ() - 1,
            centre.getX() + 1, centre.getY() + 1, centre.getZ() + 1, Geo.CAVE_AIR);
        plan.spawner(centre, garrison);
    }

    private static final int STOREY = 6;

    /**
     * A square keep with actual architecture: a plinth pad one course proud of the walls,
     * blackstone corner pilasters that never erode, a tuff string course at every storey line,
     * arrow slits per face per storey, and - where the walls still reach full height - a
     * corbelled crown: an oversailing top-slab table with merlon-and-slab coping above it. The
     * crown follows surviving columns, so a shortened wall never carries floating stonework.
     */
    public static void squareKeep(Plan plan, CastlePalette palette, Condition condition,
                                  int cx, int cz, int baseY, int half, int height,
                                  Direction door) {
        int topY = baseY + height;
        Geo.Material face = palette.banded(baseY, STOREY);

        // Plinth pad, one block proud on every side.
        for (int x = cx - half - 1; x <= cx + half + 1; x++) {
            for (int z = cz - half - 1; z <= cz + half + 1; z++) {
                Geo.column(plan, x, z, baseY - 4, baseY, Geo.solid(palette.plinth()));
            }
        }

        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                int dx = x - cx, dz = z - cz;
                boolean shell = Math.abs(dx) == half || Math.abs(dz) == half;
                if (shell) {
                    int reached = Erode.wallColumn(plan, palette, face, x, z, baseY + 1, topY);
                    if (reached >= topY && condition != Condition.RUINED) {
                        crown(plan, palette, x, z, topY,
                            Math.abs(dx) == half ? Integer.signum(dx) : 0,
                            Math.abs(dz) == half ? Integer.signum(dz) : 0);
                    }
                } else {
                    Geo.column(plan, x, z, baseY + 1, topY - 1, Geo.AIR);
                    for (int fy = baseY + 1; fy < topY - 1; fy += STOREY) {
                        if (condition == Condition.RUINED && fy > baseY + 1) break;
                        plan.set(x, fy, z, palette.floorAt(x, fy, z));
                    }
                }
            }
        }

        // Corner pilasters, proud of the shell and immune to erosion: the bones always read.
        for (int sx : new int[] {-1, 1}) {
            for (int sz : new int[] {-1, 1}) {
                int px = cx + sx * half, pz = cz + sz * half;
                Geo.column(plan, px + sx, pz, baseY + 1, topY - 1, Geo.solid(palette.plinth()));
                Geo.column(plan, px, pz + sz, baseY + 1, topY - 1, Geo.solid(palette.plinth()));
            }
        }

        slits(plan, cx, cz, baseY, half, height);
        Geo.archway(plan, new BlockPos(cx + door.getStepX() * half, baseY + 1,
            cz + door.getStepZ() * half), door, 1, 3, Geo.solid(palette.dressed()));

        if (condition.roofsIntact()) {
            Geo.pyramid(plan, cx, cz, topY + 1, half - 1, Geo.solid(palette.roof()),
                palette.roofSlab());
        }
    }

    /**
     * A round keep drum with the same vocabulary: banded eroded shell, cardinal arrow slits per
     * storey, storey floors, and the corbelled crown on surviving full-height columns.
     */
    public static void roundKeep(Plan plan, CastlePalette palette, Condition condition,
                                 int cx, int cz, int baseY, int radius, int height,
                                 Direction door) {
        roundKeep(plan, palette, condition, cx, cz, baseY, radius, height, door, true);
    }

    /** {@link #roundKeep} with the cone roof optional, for drums that crown themselves (the
     *  eyrie's beacon platform). */
    public static void roundKeep(Plan plan, CastlePalette palette, Condition condition,
                                 int cx, int cz, int baseY, int radius, int height,
                                 Direction door, boolean cappedRoof) {
        int topY = baseY + height;
        Geo.Material face = palette.banded(baseY, STOREY);

        Geo.deck(plan, cx, cz, baseY, radius + 1, Geo.solid(palette.plinth()));
        for (int[] o : Geo.disc(radius)) {
            Geo.column(plan, cx + o[0], cz + o[1], baseY - 4, baseY - 1,
                Geo.solid(palette.plinth()));
        }

        for (int[] o : Geo.ring(radius)) {
            int x = cx + o[0], z = cz + o[1];
            int reached = Erode.wallColumn(plan, palette, face, x, z, baseY + 1, topY);
            if (reached >= topY && condition != Condition.RUINED) {
                crown(plan, palette, x, z, topY, Integer.signum(o[0]), Integer.signum(o[1]));
            }
        }
        for (int[] o : Geo.disc(radius - 1)) {
            int x = cx + o[0], z = cz + o[1];
            Geo.column(plan, x, z, baseY + 1, topY - 1, Geo.AIR);
            for (int fy = baseY + 1; fy < topY - 1; fy += STOREY) {
                if (condition == Condition.RUINED && fy > baseY + 1) break;
                plan.set(x, fy, z, palette.floorAt(x, fy, z));
            }
        }

        // Cardinal arrow slits, one pair of courses per storey.
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int fy = baseY + 1 + STOREY; fy < topY - 2; fy += STOREY) {
                int x = cx + side.getStepX() * radius;
                int z = cz + side.getStepZ() * radius;
                plan.set(x, fy - 3, z, Blocks.AIR.defaultBlockState());
                plan.set(x, fy - 2, z, Blocks.AIR.defaultBlockState());
            }
        }

        Geo.archway(plan, new BlockPos(cx + door.getStepX() * radius, baseY + 1,
            cz + door.getStepZ() * radius), door, 1, 3, Geo.solid(palette.dressed()));

        if (cappedRoof && condition.roofsIntact()) {
            Geo.cone(plan, cx, cz, topY + 1, radius, Geo.solid(palette.roof()),
                palette.roofSlab());
        }
    }

    /** One column's share of the corbelled crown: an oversailing top slab and the coping above. */
    private static void crown(Plan plan, CastlePalette palette, int x, int z, int topY,
                              int outX, int outZ) {
        if (outX == 0 && outZ == 0) return;
        int ox = x + outX, oz = z + outZ;
        plan.set(ox, topY, oz,
            palette.roofSlab().setValue(SlabBlock.TYPE, SlabType.TOP));
        plan.set(ox, topY + 1, oz,
            Math.floorMod(x + z, 2) == 0 ? palette.parapet() : palette.roofSlab());
    }

    /** Arrow slits on every face of a square shell: two per face per upper storey. */
    private static void slits(Plan plan, int cx, int cz, int baseY, int half, int height) {
        int spread = Math.max(1, half / 2);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Direction across = side.getClockWise();
            for (int fy = baseY + 1 + STOREY; fy < baseY + height - 2; fy += STOREY) {
                for (int w : new int[] {-spread, spread}) {
                    int x = cx + side.getStepX() * half + across.getStepX() * w;
                    int z = cz + side.getStepZ() * half + across.getStepZ() * w;
                    plan.set(x, fy - 3, z, Blocks.AIR.defaultBlockState());
                    plan.set(x, fy - 2, z, Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** The treasury: a dressed-stone strongroom with the castle's best chest on a plinth. */
    public static void vault(Plan plan, BlockPos centre, CastlePalette palette,
                             ResourceKey<LootTable> table) {
        Geo.box(plan, centre.getX() - 2, centre.getY() - 1, centre.getZ() - 2,
            centre.getX() + 2, centre.getY() + 3, centre.getZ() + 2, Geo.solid(palette.dressed()));
        Geo.box(plan, centre.getX() - 1, centre.getY(), centre.getZ() - 1,
            centre.getX() + 1, centre.getY() + 2, centre.getZ() + 1, Geo.CAVE_AIR);
        plan.set(centre.getX(), centre.getY(), centre.getZ(), palette.monolith());
        plan.chest(centre.above(), Direction.NORTH, table);
        plan.set(centre.getX() - 1, centre.getY() + 2, centre.getZ() - 1, palette.lantern());
    }

    /** A well or cistern eye: dressed rim, water column sunk into the ground. */
    public static void well(Plan plan, BlockPos rim, int depth, CastlePalette palette) {
        for (int[] o : Geo.squareRing(1)) {
            plan.set(rim.getX() + o[0], rim.getY(), rim.getZ() + o[1], palette.dressed());
        }
        for (int y = 0; y < depth; y++) {
            plan.set(rim.getX(), rim.getY() - y, rim.getZ(), Blocks.WATER.defaultBlockState());
        }
        plan.set(rim.getX(), rim.getY() - depth, rim.getZ(), palette.plinth());
    }

    /**
     * A round stair tower: eroded shell, spiral stair, doorway at the base facing out.
     *
     * @return the Y of the tower's top course after erosion had its say.
     */
    public static int stairTower(Plan plan, BlockPos base, int radius, int height,
                                 Direction door, CastlePalette palette) {
        long seed = palette.seed();
        Condition condition = palette.condition();
        int top = base.getY() + height;
        for (int[] o : Geo.ring(radius)) {
            int x = base.getX() + o[0];
            int z = base.getZ() + o[1];
            int columnTop = Erode.columnTop(seed, condition, x, z, base.getY(), height);
            for (int y = base.getY(); y <= columnTop; y++) {
                if (Erode.bitten(seed, condition, x, y, z, base.getY(), height)) continue;
                plan.set(x, y, z, palette.masonryAt(x, y, z));
            }
        }
        Geo.spiralStairs(plan, base.getX(), base.getZ(), base.getY(), top - 2, radius - 1,
            palette.stairs(), Geo.solid(palette.plinth()));
        BlockPos doorAt = base.relative(door, radius);
        plan.set(doorAt, Blocks.AIR.defaultBlockState());
        plan.set(doorAt.above(), Blocks.AIR.defaultBlockState());
        return top;
    }

    private static java.util.List<int[]> wallColumns(int minX, int minZ, int maxX, int maxZ) {
        java.util.List<int[]> columns = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            columns.add(new int[] {x, minZ});
            columns.add(new int[] {x, maxZ});
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++) {
            columns.add(new int[] {minX, z});
            columns.add(new int[] {maxX, z});
        }
        return columns;
    }

    /** Battlements: merlons alternating with slab coping along a wall top. */
    public static void crenellate(Plan plan, java.util.List<int[]> wallLine, int y,
                                  CastlePalette palette) {
        for (int i = 0; i < wallLine.size(); i++) {
            int[] o = wallLine.get(i);
            plan.set(o[0], y, o[1], i % 2 == 0 ? palette.parapet() : palette.roofSlab());
        }
    }

}
