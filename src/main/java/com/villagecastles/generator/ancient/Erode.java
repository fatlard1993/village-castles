package com.villagecastles.generator.ancient;

/**
 * Decay that happens while drawing, not after. There is no world to sweep at worldgen time, so a
 * wall is never built and then broken: each block either goes in or it doesn't, decided by hashes
 * that any chunk computes identically.
 *
 * <p>Two grains, both quantised to 3-block cells so damage comes in contiguous bites rather than
 * salt-and-pepper (the same fix {@code BiomePalette.getWallBlockAt} records for material mixing):
 * {@link #bitten} eats patches out of a wall's face, and {@link #columnTop} shortens whole runs of
 * columns so silhouettes go ragged together. Column-aware erosion never strands a floating block,
 * which is why the old DecayEngine's collapse sweep has no counterpart here.
 */
public final class Erode {

    private Erode() {}

    /**
     * Deterministic hash over a position and the structure seed: same castle, same holes, from
     * whichever chunk asks; different castle, different holes.
     */
    public static int hash(long seed, int x, int y, int z) {
        int salt = (int) (seed ^ (seed >>> 32));
        int h = x * 0x27D4EB2D + y * 0x165667B1 + z * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h;
    }

    /** Roll in [0, bound) from the position hash, patch-quantised. */
    public static int patchRoll(long seed, int x, int y, int z, int bound) {
        return Math.floorMod(hash(seed, Math.floorDiv(x, 3), Math.floorDiv(y, 3), Math.floorDiv(z, 3)), bound);
    }

    /** Erosion percentage at a height within a wall: the condition's base, rising toward the top. */
    public static int wornPercent(Condition condition, int y, int baseY, int height) {
        return condition.erosionBase + condition.erosionRise * Math.max(0, y - baseY) / Math.max(1, height);
    }

    /** Whether erosion took this block of a wall spanning [baseY, baseY + height). */
    public static boolean bitten(long seed, Condition condition, int x, int y, int z,
                                 int baseY, int height) {
        return patchRoll(seed, x, y, z, 100) < wornPercent(condition, y, baseY, height);
    }

    /**
     * One eroded wall column: masonry from base toward a target top, shortened and bitten by the
     * palette's condition. Returns the Y the column actually reached.
     */
    public static int wallColumn(Plan plan, CastlePalette palette, int x, int z,
                                 int baseY, int targetTopY) {
        return wallColumn(plan, palette, palette.masonry(), x, z, baseY, targetTopY);
    }

    /** {@link #wallColumn} with a caller-chosen material (an island's seaworn courses). */
    public static int wallColumn(Plan plan, CastlePalette palette, Geo.Material material,
                                 int x, int z, int baseY, int targetTopY) {
        int height = Math.max(1, targetTopY - baseY);
        long seed = palette.seed();
        Condition condition = palette.condition();
        int top = columnTop(seed, condition, x, z, baseY, height);
        for (int y = baseY; y <= top; y++) {
            // The base and top courses never erode: the base ties the wall to its plinth and the
            // top carries the coping, so a bite at either strands blocks in the air.
            boolean anchor = y == baseY || y == top;
            if (!anchor && bitten(seed, condition, x, y, z, baseY, height)) continue;
            plan.set(x, y, z, material.at(x, y, z));
        }
        return top;
    }

    /**
     * Where a wall column actually stops. Full height when roofs stand; a patch-shared fraction of
     * it when they don't, so neighbouring columns break off together.
     */
    public static int columnTop(long seed, Condition condition, int x, int z, int baseY, int height) {
        // A weathered or new-built castle stands complete: decay is texture, never silhouette.
        if (condition.intact()) return baseY + height;
        int lowPct;
        int spanPct;
        if (condition == Condition.RUINED) { lowPct = 40; spanPct = 40; }
        else { lowPct = 65; spanPct = 35; }
        int patch = patchRoll(seed, x, 0, z, spanPct + 1);
        int jitter = Math.floorMod(hash(seed, x, 1, z), 2);
        int kept = height * (lowPct + patch) / 100 - jitter;
        return baseY + Math.max(1, Math.min(height, kept));
    }
}
