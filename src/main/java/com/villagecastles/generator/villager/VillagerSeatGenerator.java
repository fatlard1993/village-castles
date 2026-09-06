package com.villagecastles.generator.villager;

import com.villagecastles.generator.ancient.AncientRooms;
import com.villagecastles.generator.ancient.Condition;
import com.villagecastles.generator.ancient.Erode;
import com.villagecastles.generator.ancient.Geo;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.generator.kit.KitFurnish;
import com.villagecastles.generator.villager.VillagerCastleDesigner.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

/**
 * LARGE - the seat: a full walled complex. The keep stands off-centre, a great hall in villager
 * timber holds a throne and a long table, corner towers watch the ring, a well serves the court,
 * and the dormitory sleeps six. This is the fortified village grown into its ancestors' shape.
 */
final class VillagerSeatGenerator {

    private VillagerSeatGenerator() {}

    private static final int KEEP_HALF = 6;
    private static final int KEEP_HEIGHT = 18;
    private static final int RING_RADIUS = 19;
    private static final int RING_HEIGHT = 7;

    static void draw(Plan plan, Site site, HeightField terrain, VillagerPalette palette,
                     RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int baseY = site.anchor().getY();
        Direction entrance = site.entrance();
        Direction back = entrance.getOpposite();
        Direction across = entrance.getClockWise();

        // Curtain ring with coping and wall torches.
        var ring = Geo.ring(RING_RADIUS);
        for (int i = 0; i < ring.size(); i++) {
            int[] o = ring.get(i);
            int x = cx + o[0];
            int z = cz + o[1];
            int ground = terrain.surfaceY(x, z);
            Geo.column(plan, x, z, ground - 3, ground, Geo.solid(palette.plinth()));
            int target = ground + RING_HEIGHT;
            int reached = Erode.wallColumn(plan, palette, x, z, ground + 1, target);
            if (reached >= target) {
                plan.set(x, reached + 1, z,
                    i % 2 == 0 ? palette.parapet() : palette.roofSlab());
                if (i % 10 == 0) VillagerWorks.torch(plan, palette, x, reached + 2, z);
            }
        }

        // Corner towers on the ring's diagonals, capped in the biome's roof.
        for (int[] diag : new int[][] {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            int tx = cx + diag[0] * (int) (RING_RADIUS * 0.7);
            int tz = cz + diag[1] * (int) (RING_RADIUS * 0.7);
            int ground = terrain.surfaceY(tx, tz);
            int reached = AncientRooms.stairTower(plan, new BlockPos(tx, ground + 1, tz), 3,
                RING_HEIGHT + 6, Direction.NORTH, palette);
            Geo.cone(plan, tx, tz, reached + 1, 3, Geo.solid(palette.roof()),
                palette.roofSlab());
            VillagerWorks.torch(plan, palette, tx, reached + 1 + 3 + 1, tz);
        }

        VillagerWorks.gate(plan, terrain, palette, cx, cz, RING_RADIUS, entrance);
        VillagerWorks.clearCourt(plan, terrain, cx, cz, RING_RADIUS - 1);

        // The keep, off toward the gate's left hand.
        int kx = cx - across.getStepX() * 8 + back.getStepX() * 4;
        int kz = cz - across.getStepZ() * 8 + back.getStepZ() * 4;
        VillagerWorks.keep(plan, palette, random, kx, kz, baseY, KEEP_HALF, KEEP_HEIGHT, entrance);

        hall(plan, terrain, palette, random, cx, cz, baseY, entrance);

        // The well in the open court between keep and hall.
        int wx = cx + entrance.getStepX() * 6;
        int wz = cz + entrance.getStepZ() * 6;
        AncientRooms.well(plan, new BlockPos(wx, terrain.surfaceY(wx, wz), wz), 6, palette);

        dormitory(plan, terrain, palette, cx, cz, back, across);
        VillagerWorks.flair(plan, terrain, palette, random, cx, cz, RING_RADIUS - 5);
    }

    /** The great hall: the ancient form in villager timber, throne on the dais, table down the middle. */
    private static void hall(Plan plan, HeightField terrain, VillagerPalette palette,
                             RandomSource random, int cx, int cz, int baseY, Direction entrance) {
        Direction across = entrance.getClockWise();
        int hx = cx + across.getStepX() * 9;
        int hz = cz + across.getStepZ() * 9;
        BlockPos dais = AncientRooms.greatHall(plan,
            Math.min(hx - 4, hx + 4), Math.min(hz - 6, hz + 6),
            Math.max(hx - 4, hx + 4), Math.max(hz - 6, hz + 6),
            baseY, 7, across, palette, random);
        // The throne: a stair on the dais under a carpet runner, facing back down the hall.
        plan.set(dais, palette.biome().woodStairs.defaultBlockState()
            .setValue(StairBlock.FACING, across.getOpposite()));
        plan.set(dais.relative(across.getOpposite()).below(), palette.dressed());
        plan.set(dais.relative(across.getOpposite()), palette.carpet());
        plan.chest(dais.relative(across.getClockWise(), 2), across.getOpposite(),
            VillagerWorks.KEEP_CHEST);
        // The long table with chairs both sides.
        boolean alongX = across.getStepX() != 0;
        Direction tableRun = alongX ? Direction.SOUTH : Direction.EAST;
        KitFurnish.table(plan, new BlockPos(hx, baseY, hz - (alongX ? 3 : 0)).offset(
            alongX ? 0 : -3, 0, 0), tableRun, 6, palette.fence(), palette.slab());
        VillagerWorks.torch(plan, palette, hx, baseY + 2, hz);
    }

    /** Six beds against the back wall: the seat's garrison is a household. */
    private static void dormitory(Plan plan, HeightField terrain, VillagerPalette palette,
                                  int cx, int cz, Direction back, Direction across) {
        int bx = cx + back.getStepX() * (RING_RADIUS - 4);
        int bz = cz + back.getStepZ() * (RING_RADIUS - 4);
        int ground = terrain.surfaceY(bx, bz);
        int floorY = ground + 1;

        for (int a = -5; a <= 5; a++) {
            for (int d = -1; d <= 1; d++) {
                int x = bx + across.getStepX() * a + back.getStepX() * d;
                int z = bz + across.getStepZ() * a + back.getStepZ() * d;
                plan.set(x, floorY - 1, z, palette.floorAt(x, floorY - 1, z));
                for (int h = 0; h < 3; h++) plan.set(x, floorY + h, z, Blocks.AIR.defaultBlockState());
                plan.set(x, floorY + 3 + (d + 1) / 2, z, palette.roofSlab());
            }
        }
        for (int a : new int[] {-5, 5}) {
            int x = bx + across.getStepX() * a - back.getStepX();
            int z = bz + across.getStepZ() * a - back.getStepZ();
            Geo.column(plan, x, z, floorY, floorY + 2, Geo.solid(palette.log()));
        }
        for (int i = -2; i <= 2; i += 2) {
            KitFurnish.bed(plan, new BlockPos(bx + across.getStepX() * i, floorY - 1,
                bz + across.getStepZ() * i), back, palette.bed());
            KitFurnish.bed(plan, new BlockPos(bx + across.getStepX() * (i + 1), floorY - 1,
                bz + across.getStepZ() * (i + 1)), back, palette.bed());
        }
        VillagerWorks.torch(plan, palette, bx, floorY + 2, bz);
    }
}
