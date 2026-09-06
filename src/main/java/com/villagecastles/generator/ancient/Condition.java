package com.villagecastles.generator.ancient;

import net.minecraft.util.RandomSource;

/**
 * How much of an ancient castle is still standing, rolled once per site.
 *
 * <p>Rarer is more intact, and loot rises with intactness by design: the standing castle was never
 * looted and its garrison still holds it, while the ruin was picked over centuries ago and offers
 * archaeology instead. Risk stays proportional to reward; the rare find is the jackpot.
 */
public enum Condition {
    RUINED(30, 50, 0.80f, 1, 2, 0, 1, 4, 6),
    CRUMBLING(12, 28, 0.45f, 3, 4, 1, 3, 2, 3),
    WEATHERED(2, 6, 0.15f, 5, 6, 3, 4, 0, 0),
    /** New-built: zero decay, zero growth. Never rolled - the villager castles' condition. */
    PRISTINE(0, 0, 0f, 0, 0, 0, 0, 0, 0);

    /** Erosion percentage at a wall's footing. */
    public final int erosionBase;
    /** Erosion percentage added across a wall's full height (worst at the top). */
    public final int erosionRise;
    /** Scales every vegetation probability. */
    public final float vegetationScale;
    public final int minChests, maxChests;
    public final int minSpawners, maxSpawners;
    public final int minDigs, maxDigs;

    Condition(int erosionBase, int erosionRise, float vegetationScale,
              int minChests, int maxChests, int minSpawners, int maxSpawners,
              int minDigs, int maxDigs) {
        this.erosionBase = erosionBase;
        this.erosionRise = erosionRise;
        this.vegetationScale = vegetationScale;
        this.minChests = minChests;
        this.maxChests = maxChests;
        this.minSpawners = minSpawners;
        this.maxSpawners = maxSpawners;
        this.minDigs = minDigs;
        this.maxDigs = maxDigs;
    }

    /** 50 / 35 / 15: RUINED / CRUMBLING / WEATHERED. */
    public static Condition roll(RandomSource random) {
        int n = random.nextInt(20);
        if (n < 10) return RUINED;
        if (n < 17) return CRUMBLING;
        return WEATHERED;
    }

    public boolean roofsIntact() {
        return intact();
    }

    /** Whether the fabric stands whole: silhouettes complete, nothing fallen or toppled. */
    public boolean intact() {
        return this == WEATHERED || this == PRISTINE;
    }

    public String id() {
        return name().toLowerCase();
    }

    /** Case-insensitive lookup, defaulting to CRUMBLING (the median state) on a bad tag. */
    public static Condition byId(String id) {
        for (Condition condition : values()) {
            if (condition.id().equalsIgnoreCase(id)) return condition;
        }
        return CRUMBLING;
    }
}
