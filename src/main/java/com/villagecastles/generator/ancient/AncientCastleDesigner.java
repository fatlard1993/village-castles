package com.villagecastles.generator.ancient;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The entry the worldgen piece and the debug command both call: one site, one condition, one
 * terrain field, one seed in; the finished plan out. Pure - the same arguments always produce the
 * same plan, from whichever chunk asks.
 */
public final class AncientCastleDesigner {

    private AncientCastleDesigner() {}

    /** The keep vault's jackpot, ours to tune (data/village-castles/loot_table/chests/...). */
    public static final ResourceKey<LootTable> TREASURY = ResourceKey.create(Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "chests/ancient_treasury"));

    public static Plan draw(Site site, Condition condition, HeightField terrain, String tintId,
                            long shapeSeed) {
        AncientPalette palette = AncientPalette.forTint(tintId, condition, shapeSeed);
        Plan plan = new Plan();
        RandomSource random = RandomSource.create(shapeSeed);
        switch (site.landform()) {
            case PLATEAU -> PlateauCitadelGenerator.draw(plan, site, terrain, palette, condition, random);
            case HILLTOP -> HillfortGenerator.draw(plan, site, terrain, palette, condition, random);
            case CLIFF_FACE -> CliffHoldGenerator.draw(plan, site, terrain, palette, condition, random);
            case ISLAND -> IslandFortGenerator.draw(plan, site, terrain, palette, condition, random);
            case HEADLAND -> HeadlandFortGenerator.draw(plan, site, terrain, palette, condition, random);
            case RIDGE -> RidgeGateGenerator.draw(plan, site, terrain, palette, condition, random);
            case EYRIE -> EyrieGenerator.draw(plan, site, terrain, palette, condition, random);
        }
        // Clearing before growing: strip the canopy over the works, then let our own moss in.
        PlanPasses.clearCanopy(plan, terrain);
        // Vegetation rolls its own decorrelated stream so tuning a generator never reshuffles it.
        Vegetate.apply(plan, terrain, palette, condition,
            RandomSource.create(shapeSeed ^ 0x9E3779B97F4A7C15L));
        PlanPasses.settleLanterns(plan);
        PlanPasses.settleChests(plan, terrain, palette);
        PlanPasses.connectCross(plan);
        return plan;
    }


    /** Who still holds the castle. The garrison never left; the biome decided what it became. */
    public static EntityType<?> garrison(AncientPalette palette) {
        if (palette.tint == AncientPalette.SNOWY) return EntityTypes.STRAY;
        if (palette.tint == AncientPalette.DESERT) return EntityTypes.HUSK;
        return EntityTypes.SKELETON;
    }

    /** The great hall's chest: richer the more intact the castle that guards it. */
    public static ResourceKey<LootTable> hallTable(Condition condition) {
        return switch (condition) {
            case PRISTINE, WEATHERED -> BuiltInLootTables.STRONGHOLD_LIBRARY;
            case CRUMBLING -> BuiltInLootTables.STRONGHOLD_CORRIDOR;
            case RUINED -> BuiltInLootTables.SIMPLE_DUNGEON;
        };
    }

    /** Crypt alcove chests: the picked-over remainder. */
    public static ResourceKey<LootTable> alcoveTable(Condition condition) {
        return condition == Condition.WEATHERED
            ? BuiltInLootTables.STRONGHOLD_CORRIDOR : BuiltInLootTables.SIMPLE_DUNGEON;
    }

    /**
     * Archaeology sites scattered over a courtyard or bailey: the ruin is a dig. Suspicious
     * gravel with the trail-ruins tables; none in a weathered castle, which was never buried.
     *
     * @param floorY a fixed courtyard level, or {@code Integer.MIN_VALUE} to follow the terrain.
     */
    public static void scatterDigs(Plan plan, HeightField terrain, Condition condition,
                                   RandomSource random, int cx, int cz, int radius, int floorY) {
        int count = condition.minDigs
            + (condition.maxDigs > condition.minDigs
                ? random.nextInt(condition.maxDigs - condition.minDigs + 1) : 0);
        for (int i = 0; i < count; i++) {
            int x = cx + random.nextInt(radius * 2 + 1) - radius;
            int z = cz + random.nextInt(radius * 2 + 1) - radius;
            int y = floorY == Integer.MIN_VALUE ? terrain.surfaceY(x, z) : floorY;
            ResourceKey<LootTable> table = random.nextInt(10) < 3
                ? BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_RARE
                : BuiltInLootTables.TRAIL_RUINS_ARCHAEOLOGY_COMMON;
            plan.dig(new BlockPos(x, y, z), Blocks.SUSPICIOUS_GRAVEL.defaultBlockState(), table);
        }
    }

    /**
     * Spawner cells beyond the crypt's one, buried around a centre. A weathered castle is
     * heavily garrisoned; a ruin is nearly empty.
     */
    public static void extraGarrison(Plan plan, AncientPalette palette, Condition condition,
                                     RandomSource random, int cx, int cz, int y, int spread) {
        int rolled = condition.minSpawners
            + (condition.maxSpawners > condition.minSpawners
                ? random.nextInt(condition.maxSpawners - condition.minSpawners + 1) : 0);
        int extra = Math.max(0, rolled - 1); // the crypt already holds one
        for (int i = 0; i < extra; i++) {
            int x = cx + random.nextInt(spread * 2 + 1) - spread;
            int z = cz + random.nextInt(spread * 2 + 1) - spread;
            AncientRooms.spawnerCell(plan, new BlockPos(x, y, z), palette, garrison(palette));
        }
    }

    /** Fallen mass scattered around the works: more of it the worse the castle fared. */
    public static void scatterRubble(Plan plan, HeightField terrain, AncientPalette palette,
                                     RandomSource random, int cx, int cz, int radius,
                                     Condition condition) {
        int count = switch (condition) {
            case RUINED -> 26;
            case CRUMBLING -> 14;
            case WEATHERED -> 5;
            case PRISTINE -> 0;
        };
        for (int i = 0; i < count; i++) {
            int x = cx + random.nextInt(radius * 2 + 1) - radius;
            int z = cz + random.nextInt(radius * 2 + 1) - radius;
            int y = terrain.surfaceY(x, z) + 1;
            if (terrain.waterDepth(x, z) > 0) continue;
            plan.set(x, y, z, palette.rubble(random));
        }
    }
}
