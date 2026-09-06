package com.villagecastles.generator.ancient;

import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

/**
 * The eyrie: peaks too jagged for a ringfort get a single monumental watchtower instead - a
 * plinth terrace levelled over the crag, one tall drum with a spiral stair, a beacon platform,
 * and rock-cut steps winding down the slope. Deliberately not a full castle: the most vertical
 * terrain gets the most vertical answer, and stays cheap doing it.
 */
public final class EyrieGenerator {

    private EyrieGenerator() {}

    private static final int TOWER_RADIUS = 5;
    private static final int TOWER_HEIGHT = 24;
    private static final int TERRACE_RADIUS = 7;

    public static void draw(Plan plan, Site site, HeightField terrain, AncientPalette palette,
                            Condition condition, RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int baseY = site.anchor().getY();
        Direction door = site.facing();

        // The terrace: every column under it built up from its own ground to one level, so the
        // tower stands on masonry, not on the crag's teeth.
        for (int[] o : Geo.disc(TERRACE_RADIUS)) {
            int x = cx + o[0];
            int z = cz + o[1];
            int ground = Math.min(terrain.surfaceY(x, z), baseY);
            Geo.column(plan, x, z, ground - 2, baseY - 1, Geo.solid(palette.plinth()));
            plan.set(x, baseY, z, palette.floorAt(x, baseY, z));
        }

        // The drum, uncapped: this tower crowns itself with the beacon instead of a cone.
        AncientRooms.roundKeep(plan, palette, condition, cx, cz, baseY, TOWER_RADIUS,
            TOWER_HEIGHT, door, false);
        Geo.spiralStairs(plan, cx, cz, baseY + 1, baseY + TOWER_HEIGHT - 2, TOWER_RADIUS - 2,
            palette.stairs(), Geo.solid(palette.plinth()));

        // The beacon: a slate platform and a cluster of soul lanterns, the light this tower
        // existed to show. A ruined eyrie keeps one lantern on its broken lip.
        if (condition.roofsIntact()) {
            Geo.deck(plan, cx, cz, baseY + TOWER_HEIGHT + 1, TOWER_RADIUS,
                Geo.solid(palette.roof()));
            plan.set(cx, baseY + TOWER_HEIGHT + 2, cz, palette.monolith());
            plan.set(cx, baseY + TOWER_HEIGHT + 3, cz, palette.lantern());
            for (int[] o : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                plan.set(cx + o[0], baseY + TOWER_HEIGHT + 2, cz + o[1], palette.lantern());
            }
        } else {
            // A ruined eyrie keeps one lantern on the broken lip of its tallest surviving arc.
            int lipY = Erode.columnTop(palette.seed(), condition, cx + TOWER_RADIUS, cz,
                baseY + 1, TOWER_HEIGHT);
            plan.set(cx + TOWER_RADIUS, lipY + 1, cz, palette.lantern());
        }

        // Rock-cut steps winding down from the door: each tread follows the slope, its headroom
        // carved from the crag.
        int stepX = cx + door.getStepX() * (TERRACE_RADIUS + 1);
        int stepZ = cz + door.getStepZ() * (TERRACE_RADIUS + 1);
        Direction walk = door;
        for (int i = 0; i < 14; i++) {
            int ground = terrain.surfaceY(stepX, stepZ);
            for (int h = 1; h <= 3; h++) {
                plan.set(stepX, ground + h, stepZ, Blocks.CAVE_AIR.defaultBlockState());
            }
            plan.set(stepX, ground + 1, stepZ,
                palette.stairs().setValue(StairBlock.FACING, walk.getOpposite()));
            plan.set(stepX, ground, stepZ, palette.plinth());
            // Wind a third of the way around as it descends.
            if (i % 5 == 4) walk = walk.getClockWise();
            stepX += walk.getStepX();
            stepZ += walk.getStepZ();
        }

        // The vault under the terrace, its stair shaft, and the keeper who never left.
        AncientRooms.vault(plan, new BlockPos(cx, baseY - 5, cz), palette,
            AncientCastleDesigner.TREASURY);
        Geo.column(plan, cx + 2, cz + 2, baseY - 4, baseY, Geo.AIR);
        AncientRooms.spawnerCell(plan,
            new BlockPos(cx - TERRACE_RADIUS - 2, baseY - 2, cz), palette,
            AncientCastleDesigner.garrison(palette));

        AncientCastleDesigner.scatterDigs(plan, terrain, condition, random, cx, cz,
            TERRACE_RADIUS, Integer.MIN_VALUE);
        AncientCastleDesigner.scatterRubble(plan, terrain, palette, random, cx, cz,
            site.radius(), condition);
    }
}
