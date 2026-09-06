package com.villagecastles.generator.ancient;

import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The island fort: a wall tracing the shoreline, footed below the waterline so nothing hovers
 * over beach, sea-worn to prismarine where the tide has chewed it. The only way in is the sea
 * gate - a water-level arch a boat fits through, opening into a flooded gatehouse pool. A keep on
 * the island's high point, a cistern in its court, a drowned breakwater and jetty off the harbor
 * side.
 */
public final class IslandFortGenerator {

    private IslandFortGenerator() {}

    public static final int SEA_LEVEL = 63;

    private static final int WALL_TOP_ABOVE_SEA = 9;
    private static final int KEEP_HALF = 4;
    private static final int KEEP_HEIGHT = 14;

    public static void draw(Plan plan, Site site, HeightField terrain, AncientPalette palette,
                            Condition condition, RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int radius = site.radius();
        Direction seaward = site.facing();
        int wallTopY = SEA_LEVEL + WALL_TOP_ABOVE_SEA;

        // Shoreline wall: a ring big enough to take in most of the island, every column footed
        // beneath the waterline. Near the tide line the masonry turns sea-worn.
        Geo.Material seaworn = (x, y, z) -> Math.abs(y - SEA_LEVEL) <= 2
            ? palette.seawornAt(x, y, z) : palette.masonryAt(x, y, z);
        var ring = Geo.ring(radius);
        for (int i = 0; i < ring.size(); i++) {
            int[] o = ring.get(i);
            int x = cx + o[0];
            int z = cz + o[1];
            int ground = Math.min(terrain.surfaceY(x, z), SEA_LEVEL - 1);
            Geo.column(plan, x, z, ground - 4, ground, Geo.solid(palette.plinth()));
            int reached = Erode.wallColumn(plan, palette, seaworn, x, z, ground + 1, wallTopY);
            if (reached >= wallTopY) {
                plan.set(x, reached + 1, z,
                    i % 2 == 0 ? palette.parapet() : palette.roofSlab());
            }
        }

        seaGate(plan, palette, cx, cz, radius, seaward);
        keep(plan, terrain, palette, condition, cx, cz, radius);

        // Breakwater: a drowned rubble arc off the harbor quadrant.
        var breakRing = Geo.ring(radius + 8);
        for (int[] o : breakRing) {
            boolean harborSide = o[0] * seaward.getStepX() + o[1] * seaward.getStepZ() > (radius + 8) / 2;
            if (!harborSide) continue;
            if (random.nextInt(10) < 3) continue;
            int x = cx + o[0];
            int z = cz + o[1];
            int ground = terrain.surfaceY(x, z);
            Geo.column(plan, x, z, ground + 1, SEA_LEVEL - 1 + random.nextInt(2),
                Geo.solid(palette.rubble(random)));
        }

        // The drowned jetty: stripped piles and a half-gone deck running out from the sea gate.
        if (!condition.intact()) {
            for (int d = 2; d <= 10; d++) {
                int x = cx + seaward.getStepX() * (radius + d);
                int z = cz + seaward.getStepZ() * (radius + d);
                if (d % 3 == 0) {
                    int ground = terrain.surfaceY(x, z);
                    Geo.column(plan, x, z, ground + 1, SEA_LEVEL, Geo.solid(palette.tint.beam()));
                }
                if (random.nextInt(10) < 5) {
                    plan.set(x, SEA_LEVEL + 1, z, palette.slab());
                }
            }
        }

        AncientCastleDesigner.scatterDigs(plan, terrain, condition, random, cx, cz, radius - 4,
            Integer.MIN_VALUE);
        AncientCastleDesigner.scatterRubble(plan, terrain, palette, random, cx, cz,
            radius + 2, condition);
    }

    /** The sea gate: a boat-sized arch at the waterline, into a flooded gatehouse pool. */
    private static void seaGate(Plan plan, AncientPalette palette, int cx, int cz, int radius,
                                Direction seaward) {
        BlockPos gate = new BlockPos(
            cx + seaward.getStepX() * radius, SEA_LEVEL, cz + seaward.getStepZ() * radius);
        Geo.archway(plan, gate, seaward, 2, 6, Geo.solid(palette.dressed()));
        Geo.archway(plan, gate.relative(seaward.getOpposite(), 1), seaward, 2, 6,
            Geo.solid(palette.dressed()));

        // The pool inside: open water at sea level, walled at the tide line so it stays put.
        BlockPos poolCentre = gate.relative(seaward.getOpposite(), 5);
        for (int[] o : Geo.disc(3)) {
            int x = poolCentre.getX() + o[0];
            int z = poolCentre.getZ() + o[1];
            plan.set(x, SEA_LEVEL - 2, z, palette.plinth());
            plan.set(x, SEA_LEVEL - 1, z, Blocks.WATER.defaultBlockState());
            plan.set(x, SEA_LEVEL, z, Blocks.WATER.defaultBlockState());
        }
        for (int[] o : Geo.ring(4)) {
            int x = poolCentre.getX() + o[0];
            int z = poolCentre.getZ() + o[1];
            // Leave the gate's approach open; wall the rest of the pool rim.
            boolean gateSide = o[0] * seaward.getStepX() + o[1] * seaward.getStepZ() > 2;
            if (gateSide) continue;
            plan.set(x, SEA_LEVEL - 1, z, palette.seawornAt(x, SEA_LEVEL - 1, z));
            plan.set(x, SEA_LEVEL, z, palette.seawornAt(x, SEA_LEVEL, z));
        }
        // The harbor light hangs under the sea gate's arch, where a keeper could reach it.
        plan.set(gate.getX(), SEA_LEVEL + 5, gate.getZ(), palette.lantern());
    }

    /** The keep on the island's high point, the cistern court beside it, the vault below. */
    private static void keep(Plan plan, HeightField terrain, AncientPalette palette,
                             Condition condition, int cx, int cz, int radius) {
        BlockPos high = terrain.highestWithin(cx, cz, radius - 4);
        int kx = high.getX();
        int kz = high.getZ();
        int baseY = Math.max(high.getY(), SEA_LEVEL);

        AncientRooms.squareKeep(plan, palette, condition, kx, kz, baseY, KEEP_HALF,
            KEEP_HEIGHT, Direction.EAST);
        // Hung under the first upper floor; the settling pass removes it if that floor fell.
        plan.set(kx, baseY + 6, kz, palette.lantern());

        // Cistern court: this castle's well drinks rain, not ground water.
        int wx = kx + KEEP_HALF + 4;
        int wz = kz;
        Geo.deck(plan, wx, wz, baseY + 1, 3, palette.paving());
        AncientRooms.well(plan, new BlockPos(wx, baseY + 1, wz), 4, palette);

        AncientRooms.vault(plan, new BlockPos(kx, baseY - 4, kz), palette,
            AncientCastleDesigner.TREASURY);
        Geo.column(plan, kx - 2, kz - 2, baseY - 3, baseY + 1, Geo.AIR);
        AncientRooms.spawnerCell(plan, new BlockPos(kx - KEEP_HALF - 4, baseY - 1, kz),
            palette, AncientCastleDesigner.garrison(palette));
    }
}
