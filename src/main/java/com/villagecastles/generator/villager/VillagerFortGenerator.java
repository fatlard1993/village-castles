package com.villagecastles.generator.villager;

import com.villagecastles.generator.ancient.AncientRooms;
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

/**
 * MEDIUM - the walled fort: the keep inside a curtain ring with merlon-and-slab coping (the
 * coping rhythm learned from the ancient walls), a gate arch with a hung double door, a well,
 * and a lean-to workshop-barracks against the far wall. Four beds; the fort holds a family.
 */
final class VillagerFortGenerator {

    private VillagerFortGenerator() {}

    private static final int KEEP_HALF = 5;
    private static final int KEEP_HEIGHT = 14;
    private static final int RING_RADIUS = 13;
    private static final int RING_HEIGHT = 6;

    static void draw(Plan plan, Site site, HeightField terrain, VillagerPalette palette,
                     RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int baseY = site.anchor().getY();
        Direction entrance = site.entrance();

        // The curtain ring: every column footed on its own ground, coping when it tops out -
        // which at PRISTINE is always, so the rhythm runs unbroken.
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
                if (i % 8 == 0) VillagerWorks.torch(plan, palette, x, reached + 2, z);
            }
        }

        VillagerWorks.gate(plan, terrain, palette, cx, cz, RING_RADIUS, entrance);
        VillagerWorks.clearCourt(plan, terrain, cx, cz, RING_RADIUS - 1);
        VillagerWorks.keep(plan, palette, random, cx, cz, baseY, KEEP_HALF, KEEP_HEIGHT, entrance);

        // The well, off the keep's shoulder.
        int wx = cx + entrance.getClockWise().getStepX() * (KEEP_HALF + 4);
        int wz = cz + entrance.getClockWise().getStepZ() * (KEEP_HALF + 4);
        AncientRooms.well(plan, new BlockPos(wx, terrain.surfaceY(wx, wz), wz), 5, palette);

        leanTo(plan, terrain, palette, cx, cz, entrance);
        VillagerWorks.flair(plan, terrain, palette, random, cx, cz, RING_RADIUS - 3);
    }

    /** The workshop-barracks: a shed against the inside of the far wall, two beds and the trades. */
    private static void leanTo(Plan plan, HeightField terrain, VillagerPalette palette,
                               int cx, int cz, Direction entrance) {
        Direction back = entrance.getOpposite();
        Direction across = entrance.getClockWise();
        int bx = cx + back.getStepX() * (RING_RADIUS - 3);
        int bz = cz + back.getStepZ() * (RING_RADIUS - 3);
        int ground = terrain.surfaceY(bx, bz);
        int floorY = ground + 1;

        for (int a = -3; a <= 3; a++) {
            for (int d = -1; d <= 1; d++) {
                int x = bx + across.getStepX() * a + back.getStepX() * d;
                int z = bz + across.getStepZ() * a + back.getStepZ() * d;
                plan.set(x, floorY - 1, z, palette.floorAt(x, floorY - 1, z));
                for (int h = 0; h < 3; h++) plan.set(x, floorY + h, z, Blocks.AIR.defaultBlockState());
                // The shed roof: slabs pitched one course, higher toward the wall.
                plan.set(x, floorY + 3 + (d + 1) / 2, z, palette.roofSlab());
            }
        }
        // Log posts on the open corners.
        for (int a : new int[] {-3, 3}) {
            int x = bx + across.getStepX() * a - back.getStepX();
            int z = bz + across.getStepZ() * a - back.getStepZ();
            Geo.column(plan, x, z, floorY, floorY + 2, Geo.solid(palette.log()));
        }

        KitFurnish.bed(plan, new BlockPos(bx + across.getStepX() * 2, floorY - 1,
            bz + across.getStepZ() * 2), back, palette.bed());
        KitFurnish.bed(plan, new BlockPos(bx - across.getStepX() * 2, floorY - 1,
            bz - across.getStepZ() * 2), back, palette.bed());
        KitFurnish.onFloor(plan, new BlockPos(bx, floorY - 1, bz),
            Blocks.FLETCHING_TABLE.defaultBlockState());
        KitFurnish.onFloor(plan, new BlockPos(bx + across.getStepX(), floorY - 1,
            bz + across.getStepZ()), Blocks.BLAST_FURNACE.defaultBlockState(), entrance);
        VillagerWorks.torch(plan, palette, bx, floorY + 2, bz);
    }
}
