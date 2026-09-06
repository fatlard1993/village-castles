package com.villagecastles.util;

/**
 * Predicts where vanilla villages will land, without touching live world state: the same
 * random_spread grid math Minecraft runs for its village structure sets, replayed in-process.
 * Pure arithmetic, safe at any worldgen stage.
 */
public final class VillagePrediction {

    private VillagePrediction() {}

    /** Vanilla random_spread parameters for every village structure set. */
    public static final int VILLAGE_SPACING = 32;
    public static final int VILLAGE_SEPARATION = 8;

    // Village salts by biome, from vanilla structure_set JSONs.
    public static final long PLAINS_VILLAGE_SALT = 10387312L;
    public static final long DESERT_VILLAGE_SALT = 14357617L;
    public static final long SAVANNA_VILLAGE_SALT = 10387320L;
    public static final long TAIGA_VILLAGE_SALT = 10387316L;
    public static final long SNOWY_VILLAGE_SALT = 10387319L;

    private static final long[] ALL_SALTS = {
        PLAINS_VILLAGE_SALT, DESERT_VILLAGE_SALT, SAVANNA_VILLAGE_SALT,
        TAIGA_VILLAGE_SALT, SNOWY_VILLAGE_SALT,
    };

    /** True if the given set's village candidate lands within {@code radiusChunks} of a chunk. */
    public static boolean villageWithin(long worldSeed, int chunkX, int chunkZ, long salt,
                                        int radiusChunks) {
        int spacing = VILLAGE_SPACING;
        int spread = spacing - VILLAGE_SEPARATION;

        int cellRadius = (radiusChunks / spacing) + 1;
        int cellX0 = Math.floorDiv(chunkX, spacing);
        int cellZ0 = Math.floorDiv(chunkZ, spacing);

        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                int cellX = cellX0 + dx;
                int cellZ = cellZ0 + dz;

                // Replicate MC's RandomSpreadStructurePlacement seed calculation.
                long seed = (long) (cellX * 341873128712L + cellZ * 132897987541L
                        + worldSeed + salt);
                java.util.Random rng = new java.util.Random(seed);
                rng.nextInt();
                rng.nextInt(); // discard first two (matches MC internals)
                int offX = rng.nextInt(spread);
                int offZ = rng.nextInt(spread);

                int villageChunkX = cellX * spacing + offX;
                int villageChunkZ = cellZ * spacing + offZ;

                int distChunks = Math.max(Math.abs(chunkX - villageChunkX),
                        Math.abs(chunkZ - villageChunkZ));
                if (distChunks <= radiusChunks) return true;
            }
        }
        return false;
    }

    /** True if any biome's village candidate lands within {@code radiusChunks} of a chunk. */
    public static boolean anyVillageWithin(long worldSeed, int chunkX, int chunkZ,
                                           int radiusChunks) {
        for (long salt : ALL_SALTS) {
            if (villageWithin(worldSeed, chunkX, chunkZ, salt, radiusChunks)) return true;
        }
        return false;
    }
}
