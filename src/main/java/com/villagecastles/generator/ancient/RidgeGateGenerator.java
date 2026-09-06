package com.villagecastles.generator.ancient;

import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The ridge gate: an elongated wall-hold spanning the high ground, towers at both ends, one
 * gatehouse through the middle. The same build serves a spine (the wall rides the crest) and a
 * saddle (the wall dips with the gap and climbs both shoulders): every column foots on its own
 * ground, so the terrain writes the silhouette. Nothing else in the set is long and thin.
 *
 * <p>{@code site.facing()} is the crossing direction the gate opens toward (the lower side);
 * the wall runs the perpendicular axis.
 */
public final class RidgeGateGenerator {

    private RidgeGateGenerator() {}

    private static final int WALL_HEIGHT = 9;
    private static final int GATEHOUSE_HALF = 3;
    private static final int END_TOWER_RADIUS = 3;

    public static void draw(Plan plan, Site site, HeightField terrain, AncientPalette palette,
                            Condition condition, RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int radius = site.radius();
        Direction gateWay = site.facing();
        Direction along = gateWay.getClockWise();

        // The wall, two courses thick, running the full span. Terrain-following battlements:
        // this wall's drama is that its top rises and falls with the ground it rides.
        for (int a = -radius; a <= radius; a++) {
            if (Math.abs(a) <= GATEHOUSE_HALF) continue; // the gatehouse owns the middle
            for (int t = 0; t <= 1; t++) {
                int x = cx + along.getStepX() * a + gateWay.getStepX() * t;
                int z = cz + along.getStepZ() * a + gateWay.getStepZ() * t;
                int ground = terrain.surfaceY(x, z);
                Geo.column(plan, x, z, ground - 5, ground, Geo.solid(palette.plinth()));
                int target = ground + WALL_HEIGHT;
                int reached = Erode.wallColumn(plan, palette, x, z, ground + 1, target);
                if (t == 0 && reached >= target) {
                    plan.set(x, reached + 1, z,
                        a % 2 == 0 ? palette.parapet() : palette.roofSlab());
                }
            }
        }

        // Towers anchor both ends of the span.
        for (int end : new int[] {-radius, radius}) {
            int tx = cx + along.getStepX() * end;
            int tz = cz + along.getStepZ() * end;
            int ground = terrain.surfaceY(tx, tz);
            int reached = AncientRooms.stairTower(plan, new BlockPos(tx, ground + 1, tz),
                END_TOWER_RADIUS, WALL_HEIGHT + 6,
                end < 0 ? along : along.getOpposite(), palette);
            if (condition.roofsIntact()) {
                Geo.cone(plan, tx, tz, reached + 1, END_TOWER_RADIUS,
                    Geo.solid(palette.roof()), palette.roofSlab());
            }
        }

        gatehouse(plan, terrain, palette, condition, cx, cz, gateWay, along, random);

        // The crypt runs under the wall along the spine, the garrison at its end, reached by a
        // stair trench cut down from the gatehouse floor.
        int mouthA = GATEHOUSE_HALF + 4;
        int mx = cx + along.getStepX() * mouthA;
        int mz = cz + along.getStepZ() * mouthA;
        int gatehouseFloor = terrain.surfaceY(cx, cz) + 1;
        int cryptY = terrain.surfaceY(mx, mz) - 4;
        for (int a = GATEHOUSE_HALF; a <= mouthA; a++) {
            int x = cx + along.getStepX() * a;
            int z = cz + along.getStepZ() * a;
            int y = gatehouseFloor
                + (cryptY - gatehouseFloor) * (a - GATEHOUSE_HALF) / (mouthA - GATEHOUSE_HALF);
            for (int h = 0; h < 3; h++) plan.set(x, y + h, z, Blocks.CAVE_AIR.defaultBlockState());
            plan.set(x, y - 1, z, palette.floorAt(x, y - 1, z));
        }
        AncientRooms.crypt(plan, new BlockPos(mx, cryptY, mz), along, 8,
            palette, random, AncientCastleDesigner.alcoveTable(condition),
            AncientCastleDesigner.garrison(palette), true);

        AncientCastleDesigner.scatterDigs(plan, terrain, condition, random, cx, cz, radius - 4,
            Integer.MIN_VALUE);
        AncientCastleDesigner.scatterRubble(plan, terrain, palette, random, cx, cz,
            radius + 2, condition);
    }

    /** The gatehouse straddling the wall's middle: a square block, the arch tunneled through. */
    private static void gatehouse(Plan plan, HeightField terrain, AncientPalette palette,
                                  Condition condition, int cx, int cz, Direction gateWay,
                                  Direction along, RandomSource random) {
        int ground = terrain.surfaceY(cx, cz);
        int baseY = ground + 1;
        int height = WALL_HEIGHT + 4;
        Geo.Material face = palette.banded(ground, 6);

        for (int a = -GATEHOUSE_HALF; a <= GATEHOUSE_HALF; a++) {
            for (int t = -GATEHOUSE_HALF; t <= GATEHOUSE_HALF; t++) {
                int x = cx + along.getStepX() * a + gateWay.getStepX() * t;
                int z = cz + along.getStepZ() * a + gateWay.getStepZ() * t;
                boolean shell = Math.abs(a) == GATEHOUSE_HALF || Math.abs(t) == GATEHOUSE_HALF;
                Geo.column(plan, x, z, ground - 4, ground, Geo.solid(palette.plinth()));
                if (shell) {
                    Erode.wallColumn(plan, palette, face, x, z, baseY, ground + height);
                } else {
                    // The guard floor sits above the arch tunnel.
                    plan.set(x, baseY + 5, z, palette.floorAt(x, baseY + 5, z));
                    if (condition.roofsIntact()) {
                        plan.set(x, ground + height, z, palette.roof());
                    }
                }
            }
        }
        // The arch, tunneled through the whole depth after the shell stood.
        for (int t = -GATEHOUSE_HALF - 1; t <= GATEHOUSE_HALF + 1; t++) {
            BlockPos slice = new BlockPos(
                cx + gateWay.getStepX() * t, baseY, cz + gateWay.getStepZ() * t);
            Geo.archway(plan, slice, gateWay, 2, 4, Geo.solid(palette.dressed()));
        }
        // The guard room above the tunnel: a chest, a lantern, and a way up would have rotted
        // away; the room is reached through the eroded shell or not at all.
        plan.chest(new BlockPos(cx + along.getStepX(), baseY + 6, cz + along.getStepZ()),
            gateWay, AncientCastleDesigner.hallTable(condition));
        plan.set(cx, baseY + 8, cz, palette.lantern());
    }
}
