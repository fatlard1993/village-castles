package com.villagecastles.generator.villager;

import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.generator.villager.VillagerCastleDesigner.Site;
import net.minecraft.util.RandomSource;

/**
 * SMALL - the watch keep: a lone furnished keep in a fenced yard, the villagers' first imitation
 * of the ancient towers. Two storeys, a garrison pair's beds above the day room, torch posts on
 * the fence corners.
 */
final class VillagerKeepGenerator {

    private VillagerKeepGenerator() {}

    private static final int KEEP_HALF = 4;
    private static final int KEEP_HEIGHT = 12;
    private static final int YARD_RADIUS = 7;

    static void draw(Plan plan, Site site, HeightField terrain, VillagerPalette palette,
                     RandomSource random) {
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int baseY = site.anchor().getY();

        VillagerWorks.keep(plan, palette, random, cx, cz, baseY, KEEP_HALF, KEEP_HEIGHT,
            site.entrance());
        VillagerWorks.yard(plan, terrain, palette, cx, cz, YARD_RADIUS, site.entrance());
        VillagerWorks.flair(plan, terrain, palette, random, cx, cz, YARD_RADIUS - 1);
    }
}
