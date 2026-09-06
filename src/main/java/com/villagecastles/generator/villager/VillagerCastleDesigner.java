package com.villagecastles.generator.villager;

import com.villagecastles.generator.BiomePalette;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.generator.ancient.PlanPasses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

/**
 * The entry for villager castles: new-built strongholds raised in-the-style-of the great ancient
 * ones the villagers found in their world - the same grammar (keeps, coping, string courses,
 * gatehouse arches) spoken in the biome's own timber and stone, furnished and lit, because these
 * ones are lived in. Pure like the ancient designer: same arguments, same plan, from any chunk.
 */
public final class VillagerCastleDesigner {

    private VillagerCastleDesigner() {}

    public enum Size {
        SMALL(15),
        MEDIUM(30),
        LARGE(44);

        /** The square clearance the site survey and Village Builder plan around. */
        public final int clearance;

        Size(int clearance) {
            this.clearance = clearance;
        }

        public String id() {
            return name().toLowerCase();
        }

        public static Size byId(String id) {
            for (Size size : values()) {
                if (size.id().equalsIgnoreCase(id)) return size;
            }
            return SMALL;
        }
    }

    /** One villager castle's commission: where, how grand, and which way the door faces. */
    public record Site(BlockPos anchor, Size size, Direction entrance) {}

    public static Plan draw(Site site, HeightField terrain, String biomeId, long seed) {
        VillagerPalette palette = VillagerPalette.of(BiomePalette.fromId(biomeId), seed);
        Plan plan = new Plan();
        RandomSource random = RandomSource.create(seed);
        switch (site.size()) {
            case SMALL -> VillagerKeepGenerator.draw(plan, site, terrain, palette, random);
            case MEDIUM -> VillagerFortGenerator.draw(plan, site, terrain, palette, random);
            case LARGE -> VillagerSeatGenerator.draw(plan, site, terrain, palette, random);
        }
        PlanPasses.clearCanopy(plan, terrain);
        PlanPasses.settleLanterns(plan);
        PlanPasses.settleChests(plan, terrain, palette);
        PlanPasses.connectCross(plan);
        return plan;
    }
}
