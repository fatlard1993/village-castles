package com.villagecastles.generator.ancient;

import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/**
 * The plateau citadel: a curtain wall following the rim of the high ground, footed on the uneven
 * edge but topped at one level battlement line - level battlements over irregular footings is the
 * ancient-engineering read, and the height field makes it free. Inside: a paved court around a
 * square keep, a colonnaded great hall against the wall, a crypt carved down into the plateau
 * itself, and a trilithon gate on the approach side.
 */
public final class PlateauCitadelGenerator {

    private PlateauCitadelGenerator() {}

    private static final int WALL_ABOVE_TOP = 10;
    private static final int COURT_RADIUS = 13;

    public static void draw(Plan plan, Site site, HeightField terrain, AncientPalette palette,
                            Condition condition, RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int topY = site.anchor().getY();
        int radius = site.radius();
        int battlementY = topY + WALL_ABOVE_TOP;

        // Curtain wall, two courses thick, each column footed on its own ground.
        for (int ringRadius : new int[] {radius, radius - 1}) {
            boolean outer = ringRadius == radius;
            var ring = Geo.ring(ringRadius);
            for (int i = 0; i < ring.size(); i++) {
                int[] o = ring.get(i);
                int x = cx + o[0];
                int z = cz + o[1];
                int ground = terrain.surfaceY(x, z);
                Geo.column(plan, x, z, ground - 5, ground, Geo.solid(palette.plinth()));
                int reached = Erode.wallColumn(plan, palette, x, z, ground + 1, battlementY);
                if (outer && reached >= battlementY) {
                    plan.set(x, reached + 1, z,
                        i % 2 == 0 ? palette.parapet() : palette.roofSlab());
                }
            }
        }

        // The gate pierces both courses on the approach side: monolith jambs, blackstone lintel.
        Direction out = site.facing();
        Direction across = out.getClockWise();
        BlockPos gate = new BlockPos(
            cx + out.getStepX() * radius, topY + 1, cz + out.getStepZ() * radius);
        for (int depth = -2; depth <= 1; depth++) {
            BlockPos slice = gate.relative(out.getOpposite(), depth);
            Geo.archway(plan, slice, out, 2, 5, Geo.solid(palette.dressed()));
        }
        for (int w : new int[] {-3, 3}) {
            for (int d : new int[] {0, -1}) {
                BlockPos jamb = gate.relative(across, w).relative(out.getOpposite(), d);
                Geo.column(plan, jamb.getX(), jamb.getZ(), topY - 2, topY + 5,
                    Geo.solid(palette.monolith()));
            }
        }
        for (int w = -3; w <= 3; w++) {
            BlockPos lintel = gate.relative(across, w);
            plan.set(lintel.getX(), topY + 6, lintel.getZ(), palette.monolith());
        }

        // Paved court around the keep, cleared a few blocks up so the plateau's own bumps and
        // surface scatter never poke through the yard.
        for (int[] o : Geo.disc(COURT_RADIUS)) {
            int x = cx + o[0];
            int z = cz + o[1];
            // Two courses of bedding under the paving: the plateau is flat to within a few
            // blocks, not to one, and a court floating over a dip reads as a bug, not a ruin.
            plan.set(x, topY - 2, z, palette.trimAt(x, topY - 2, z));
            plan.set(x, topY - 1, z, palette.trimAt(x, topY - 1, z));
            plan.set(x, topY, z, palette.floorAt(x, topY, z));
            for (int y = 1; y <= 4; y++) plan.set(x, topY + y, z, Blocks.AIR.defaultBlockState());
        }

        // Ruined colonnade ringing the court: pillar pairs, every third fallen with its drum
        // lying beside the stump.
        var courtRing = Geo.ring(COURT_RADIUS - 1);
        for (int i = 0; i < courtRing.size(); i += 4) {
            int[] o = courtRing.get(i);
            int x = cx + o[0];
            int z = cz + o[1];
            boolean fallen = !condition.intact() && (i / 4) % 3 == 2;
            if (fallen) {
                plan.set(x, topY + 1, z, palette.monolith());
                int towardX = Integer.signum(-o[0]);
                int towardZ = Integer.signum(-o[1]);
                plan.set(x + towardX, topY + 1, z + towardZ, palette.rubble(random));
                plan.set(x + towardX * 2, topY + 1, z + towardZ * 2, palette.monolith());
            } else {
                Geo.column(plan, x, z, topY + 1, topY + 5, Geo.solid(palette.monolith()));
                plan.set(x, topY + 6, z, palette.dressed());
            }
        }

        keep(plan, cx, cz, topY, palette, condition);

        // Great hall against the wall, off to the gate's right hand.
        Direction hallSide = across;
        int hx = cx + hallSide.getStepX() * (radius - 10);
        int hz = cz + hallSide.getStepZ() * (radius - 10);
        BlockPos dais = AncientRooms.greatHall(plan,
            Math.min(hx - 4, hx + 4), Math.min(hz - 7, hz + 7),
            Math.max(hx - 4, hx + 4), Math.max(hz - 7, hz + 7),
            topY, 8, hallSide, palette, random);
        plan.chest(dais, hallSide.getOpposite(), AncientCastleDesigner.hallTable(condition));

        // The crypt: carved into the plateau off the keep's undercroft, the garrison at its end.
        // The mouth sits one stride past the undercroft's edge so the corridor opens into it.
        Direction cryptWay = out.getOpposite();
        BlockPos mouth = new BlockPos(
            cx + cryptWay.getStepX() * 4, topY - 5, cz + cryptWay.getStepZ() * 4);
        AncientRooms.crypt(plan, mouth, cryptWay, 10, palette, random,
            AncientCastleDesigner.alcoveTable(condition),
            AncientCastleDesigner.garrison(palette), true);

        AncientCastleDesigner.scatterDigs(plan, terrain, condition, random, cx, cz,
            COURT_RADIUS - 2, topY);
        AncientCastleDesigner.extraGarrison(plan, palette, condition, random, cx, cz, topY - 3, 9);
        AncientCastleDesigner.scatterRubble(plan, terrain, palette, random, cx, cz,
            radius + 4, condition);
    }

    /** The square keep: eroded shell, corner turrets, three floors, vault in the undercroft. */
    private static void keep(Plan plan, int cx, int cz, int topY, AncientPalette palette,
                             Condition condition) {
        int half = 5;
        int height = 18;

        AncientRooms.squareKeep(plan, palette, condition, cx, cz, topY, half, height,
            Direction.EAST);
        // Undercroft, carved after the keep so it opens through the plinth pad; the vault and
        // the stair shaft follow for the same reason.
        Geo.box(plan, cx - 3, topY - 4, cz - 3, cx + 3, topY - 1, cz + 3, Geo.CAVE_AIR);
        AncientRooms.vault(plan,
            new BlockPos(cx, topY - 4, cz), palette, AncientCastleDesigner.TREASURY);
        Geo.column(plan, cx - 2, cz - 2, topY - 3, topY + 1, Geo.AIR);
        Geo.column(plan, cx - 2, cz - 1, topY - 3, topY, Geo.AIR);

        // Corner turrets, one soul lantern each while they stand.
        for (int[] corner : new int[][] {{-half, -half}, {-half, half}, {half, -half}, {half, half}}) {
            int tx = cx + corner[0];
            int tz = cz + corner[1];
            int reached = AncientRooms.stairTower(plan, new BlockPos(tx, topY + 1, tz), 2,
                height + 4, Direction.NORTH, palette);
            if (condition.roofsIntact()) {
                Geo.cone(plan, tx, tz, reached + 1, 2, Geo.solid(palette.roof()),
                    palette.roofSlab());
            }
        }
    }
}
