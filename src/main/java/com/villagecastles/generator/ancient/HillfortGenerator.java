package com.villagecastles.generator.ancient;

import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * The hillfort: concentric terrace rings stepping the hill instead of flattening it, each ring
 * footed on its own ground and retained to its own median height, gates rotated ring to ring so
 * the approach spirals the slope. A round keep crowns the summit; a standing-stone circle holds
 * the outer bailey; a barrow crypt runs into the hillside below the walls.
 */
public final class HillfortGenerator {

    private HillfortGenerator() {}

    private static final int RING_WALL_HEIGHT = 5;
    private static final int KEEP_RADIUS = 6;
    private static final int KEEP_HEIGHT = 17;

    public static void draw(Plan plan, Site site, HeightField terrain, AncientPalette palette,
                            Condition condition, RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int radius = site.radius();

        BlockPos summit = terrain.highestWithin(cx, cz, radius / 2);

        int[] ringRadii = {radius, radius * 2 / 3, radius / 3 + 1};
        Direction gateWay = site.facing();
        for (int ringIndex = 0; ringIndex < ringRadii.length; ringIndex++) {
            int ringRadius = ringRadii[ringIndex];
            List<int[]> ring = Geo.ring(ringRadius);

            // The ring retains to its own median ground: the terraces step the hill.
            List<Integer> grounds = new ArrayList<>(ring.size());
            for (int[] o : ring) grounds.add(terrain.surfaceY(cx + o[0], cz + o[1]));
            List<Integer> sorted = new ArrayList<>(grounds);
            sorted.sort(null);
            int median = sorted.get(sorted.size() / 2);
            int topY = median + RING_WALL_HEIGHT;

            for (int i = 0; i < ring.size(); i++) {
                int[] o = ring.get(i);
                int x = cx + o[0];
                int z = cz + o[1];
                int ground = grounds.get(i);
                Geo.column(plan, x, z, ground - 4, ground, Geo.solid(palette.plinth()));
                if (ground + 1 >= topY) continue; // uphill side already stands above this terrace
                int reached = Erode.wallColumn(plan, palette, x, z, ground + 1, topY);
                if (reached >= topY) {
                    plan.set(x, reached + 1, z,
                        i % 2 == 0 ? palette.parapet() : palette.roofSlab());
                }
            }

            // Gates rotate a quarter turn per ring so the approach spirals the hill.
            BlockPos gate = new BlockPos(
                cx + gateWay.getStepX() * ringRadius,
                terrain.surfaceY(cx + gateWay.getStepX() * ringRadius,
                    cz + gateWay.getStepZ() * ringRadius) + 1,
                cz + gateWay.getStepZ() * ringRadius);
            Geo.archway(plan, gate, gateWay, 1, 4, Geo.solid(palette.dressed()));
            Geo.archway(plan, gate.relative(gateWay.getOpposite(), 1), gateWay, 1, 4,
                Geo.solid(palette.dressed()));
            gateWay = gateWay.getClockWise();
        }

        keep(plan, summit, palette, condition);

        // Standing-stone circle in the outer bailey, some stones toppled where the fort fell.
        List<int[]> stoneRing = Geo.ring(radius - 4);
        int stones = 8;
        for (int s = 0; s < stones; s++) {
            int[] o = stoneRing.get(s * stoneRing.size() / stones);
            int x = cx + o[0];
            int z = cz + o[1];
            int ground = terrain.surfaceY(x, z);
            boolean toppled = !condition.intact()
                && Erode.patchRoll(palette.seed(), x, 0, z, 10) < 4;
            if (toppled) {
                plan.set(x, ground + 1, z, palette.monolith());
                int towardX = Integer.signum(-o[0]);
                int towardZ = Integer.signum(-o[1]);
                plan.set(x + towardX, ground + 1, z + towardZ, palette.monolith());
            } else {
                Geo.column(plan, x, z, ground + 1, ground + 3, Geo.solid(palette.monolith()));
            }
        }

        // The barrow: a corridor crypt driven into the hillside under the second ring, its
        // trilithon mouth opening downhill.
        Direction barrowWay = site.facing().getOpposite();
        int mouthDist = ringRadii[1] + 3;
        int mx = cx + barrowWay.getStepX() * mouthDist;
        int mz = cz + barrowWay.getStepZ() * mouthDist;
        BlockPos mouth = new BlockPos(mx, terrain.surfaceY(mx, mz) + 1, mz);
        AncientRooms.crypt(plan, mouth, barrowWay.getOpposite(), 9, palette, random,
            AncientCastleDesigner.alcoveTable(condition),
            AncientCastleDesigner.garrison(palette), true);

        // The inner bailey's well, sunk from the terrace between keep and second ring.
        int wx = cx - ringRadii[2] + 2;
        int wz = cz + 2;
        AncientRooms.well(plan, new BlockPos(wx, terrain.surfaceY(wx, wz), wz), 6, palette);

        AncientCastleDesigner.scatterDigs(plan, terrain, condition, random, cx, cz,
            ringRadii[1] - 2, Integer.MIN_VALUE);
        AncientCastleDesigner.extraGarrison(plan, palette, condition, random,
            summit.getX(), summit.getZ(), summit.getY() - 4, 7);
        AncientCastleDesigner.scatterRubble(plan, terrain, palette, random, cx, cz,
            radius + 3, condition);
    }

    /** The round summit keep: eroded drum, spiral stair, slate cone while it stands. */
    private static void keep(Plan plan, BlockPos summit, AncientPalette palette,
                             Condition condition) {
        int cx = summit.getX();
        int cz = summit.getZ();
        int baseY = summit.getY();

        AncientRooms.roundKeep(plan, palette, condition, cx, cz, baseY, KEEP_RADIUS,
            KEEP_HEIGHT, Direction.EAST);
        Geo.spiralStairs(plan, cx, cz, baseY + 1, baseY + KEEP_HEIGHT - 2, KEEP_RADIUS - 2,
            palette.stairs(), Geo.solid(palette.plinth()));

        // A lantern hung in the door arch, and the treasury under the floor.
        plan.set(cx + KEEP_RADIUS, baseY + 3, cz, palette.lantern());
        AncientRooms.vault(plan, new BlockPos(cx, baseY - 8, cz), palette,
            AncientCastleDesigner.TREASURY);
        Geo.column(plan, cx + 2, cz, baseY - 7, baseY + 1, Geo.AIR);
    }
}
