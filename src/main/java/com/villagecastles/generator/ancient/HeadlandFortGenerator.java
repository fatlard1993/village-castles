package com.villagecastles.generator.ancient;

import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The headland fort: water defends three sides, so the whole art of the place is the fourth - one
 * massive neck wall with a dry ditch before it, drum towers at its ends, and a single gatehouse.
 * Behind the wall, only light parapets trace the sea edges; the keep stands on the seaward high
 * ground, its back to the water.
 *
 * <p>{@code site.facing()} is the dry side - the landward neck the wall must close.
 */
public final class HeadlandFortGenerator {

    private HeadlandFortGenerator() {}

    private static final int NECK_WALL_HEIGHT = 11;
    private static final int SHORE_WALL_HEIGHT = 4;
    private static final int SEA_LEVEL = IslandFortGenerator.SEA_LEVEL;

    public static void draw(Plan plan, Site site, HeightField terrain, AncientPalette palette,
                            Condition condition, RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int radius = site.radius();
        Direction land = site.facing();
        Direction across = land.getClockWise();
        int neckDist = radius - 2;

        // The neck wall: three courses thick, each column footed on its own ground, crenellated
        // on the landward face.
        for (int a = -radius; a <= radius; a++) {
            for (int t = 0; t <= 2; t++) {
                int x = cx + land.getStepX() * (neckDist - t) + across.getStepX() * a;
                int z = cz + land.getStepZ() * (neckDist - t) + across.getStepZ() * a;
                int ground = terrain.surfaceY(x, z);
                Geo.column(plan, x, z, ground - 5, ground, Geo.solid(palette.plinth()));
                int target = ground + NECK_WALL_HEIGHT;
                int reached = Erode.wallColumn(plan, palette, x, z, ground + 1, target);
                if (t == 0 && reached >= target) {
                    plan.set(x, reached + 1, z,
                        a % 2 == 0 ? palette.parapet() : palette.roofSlab());
                }
            }
        }

        // The dry ditch beyond the wall: the neck cut two deep, so the approach reads as earthwork.
        for (int a = -radius + 2; a <= radius - 2; a++) {
            for (int t = 2; t <= 4; t++) {
                int x = cx + land.getStepX() * (neckDist + t) + across.getStepX() * a;
                int z = cz + land.getStepZ() * (neckDist + t) + across.getStepZ() * a;
                int ground = terrain.surfaceY(x, z);
                if (terrain.waterDepth(x, z) > 0) continue;
                plan.set(x, ground, z, Blocks.AIR.defaultBlockState());
                plan.set(x, ground - 1, z, Blocks.AIR.defaultBlockState());
                plan.set(x, ground - 2, z, palette.trimAt(x, ground - 2, z));
            }
        }

        // Drum towers at the wall's ends, and the one gatehouse arch through the middle. The
        // ditch is bridged only there: a causeway of dressed stone.
        for (int end : new int[] {-(radius - 1), radius - 1}) {
            int tx = cx + land.getStepX() * (neckDist - 1) + across.getStepX() * end;
            int tz = cz + land.getStepZ() * (neckDist - 1) + across.getStepZ() * end;
            int ground = terrain.surfaceY(tx, tz);
            AncientRooms.stairTower(plan, new BlockPos(tx, ground + 1, tz), 3,
                NECK_WALL_HEIGHT + 4, land.getOpposite(), palette);
        }
        int gx = cx + land.getStepX() * neckDist;
        int gz = cz + land.getStepZ() * neckDist;
        int gateY = terrain.surfaceY(gx, gz) + 1;
        // The arch cuts all three wall courses (t counts inward from the neck line)...
        for (int t = -1; t <= 2; t++) {
            BlockPos slice = new BlockPos(
                gx - land.getStepX() * t, gateY, gz - land.getStepZ() * t);
            Geo.archway(plan, slice, land, 2, 5, Geo.solid(palette.dressed()));
        }
        // ...and a dressed causeway bridges the ditch outside it.
        for (int t = 2; t <= 4; t++) {
            for (int wSpan = -2; wSpan <= 2; wSpan++) {
                int x = gx + land.getStepX() * t + across.getStepX() * wSpan;
                int z = gz + land.getStepZ() * t + across.getStepZ() * wSpan;
                plan.set(x, gateY - 1, z, palette.dressed());
                plan.set(x, gateY, z, Blocks.AIR.defaultBlockState());
                plan.set(x, gateY + 1, z, Blocks.AIR.defaultBlockState());
            }
        }

        // Light parapets tracing the sea edges: the water is the wall on those sides.
        var shoreRing = Geo.ring(radius);
        for (int i = 0; i < shoreRing.size(); i++) {
            int[] o = shoreRing.get(i);
            // Skip the landward quadrant; the neck wall owns it.
            if (o[0] * land.getStepX() + o[1] * land.getStepZ() > radius / 2) continue;
            int x = cx + o[0];
            int z = cz + o[1];
            if (terrain.waterDepth(x, z) > 0) continue;
            int ground = terrain.surfaceY(x, z);
            Geo.column(plan, x, z, ground - 2, ground, Geo.solid(palette.plinth()));
            Erode.wallColumn(plan, palette, x, z, ground + 1, ground + SHORE_WALL_HEIGHT);
        }

        keep(plan, terrain, palette, condition, cx, cz, radius, land);

        AncientCastleDesigner.scatterDigs(plan, terrain, condition, random, cx, cz, radius - 6,
            Integer.MIN_VALUE);
        AncientCastleDesigner.scatterRubble(plan, terrain, palette, random, cx, cz,
            radius + 2, condition);
    }

    /** The keep on the seaward high ground: square, modest, its vault and garrison below. */
    private static void keep(Plan plan, HeightField terrain, AncientPalette palette,
                             Condition condition, int cx, int cz, int radius, Direction land) {
        Direction sea = land.getOpposite();
        BlockPos high = terrain.highestWithin(
            cx + sea.getStepX() * 6, cz + sea.getStepZ() * 6, radius - 8);
        int kx = high.getX();
        int kz = high.getZ();
        int baseY = Math.max(high.getY(), SEA_LEVEL);
        int half = 4;
        int height = 13;

        // Door faces the neck wall; the treasury and the garrison keep the undercroft.
        AncientRooms.squareKeep(plan, palette, condition, kx, kz, baseY, half, height, land);
        // Hung under the first upper floor; the settling pass removes it if that floor fell.
        plan.set(kx, baseY + 6, kz, palette.lantern());
        AncientRooms.vault(plan, new BlockPos(kx, baseY - 3, kz), palette,
            AncientCastleDesigner.TREASURY);
        Geo.column(plan, kx - 2, kz - 2, baseY - 2, baseY + 1, Geo.AIR);
        AncientRooms.spawnerCell(plan, new BlockPos(kx + half + 4, baseY - 1, kz),
            palette, AncientCastleDesigner.garrison(palette));
    }
}
