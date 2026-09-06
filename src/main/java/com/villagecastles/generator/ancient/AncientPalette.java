package com.villagecastles.generator.ancient;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The stone language of the ancients: one civilization's masonry everywhere, washed by the biome
 * it has weathered in. Deliberately NOT a {@code BiomePalette} constant: that enum is exhaustively
 * switched by the villager-castle generators, the exporter, and the command, and the ancients are
 * a style axis, not a sixth biome.
 *
 * <p>Base language: deepslate brick walls with cracked patches, deepslate tile roofs, tuff brick
 * trim and floors, polished blackstone plinths and monoliths, chiseled deepslate where the work
 * was dressed. Light is sparse soul lanterns only; nobody lives here.
 *
 * <p>Two pick idioms. Sequential picks serve interiors, where draw order is already
 * deterministic. Position-hashed picks ({@code Erode}'s salted patch hash, 3-block cells) are the
 * default for every wall: chunks agree within one castle, castles differ from each other, and
 * weathering comes in blotches a builder would recognise.
 */
public final class AncientPalette implements CastlePalette {

    /**
     * What the local biome does to ancient stone: growth, debris, and the accent courses. The
     * deepslate body never varies - one civilization laid it - but the finer work was dressed in
     * whatever fine stone the region gave, so castles read different-but-same across the map:
     * the accent changes, the land shapes the plan, and the bones stay kin.
     */
    public record Tint(String id, BlockState accent, Block drift, boolean creeps,
                       BlockState beam) {}

    public static final Tint TEMPERATE = new Tint("temperate",
        Blocks.TUFF_BRICKS.defaultBlockState(),
        Blocks.MOSS_BLOCK, true, Blocks.OAK_LOG.defaultBlockState());
    public static final Tint DESERT = new Tint("desert",
        Blocks.CUT_SANDSTONE.defaultBlockState(),
        Blocks.SAND, false, Blocks.STRIPPED_ACACIA_LOG.defaultBlockState());
    public static final Tint SAVANNA = new Tint("savanna",
        Blocks.MUD_BRICKS.defaultBlockState(),
        Blocks.COARSE_DIRT, true, Blocks.ACACIA_LOG.defaultBlockState());
    public static final Tint TAIGA = new Tint("taiga",
        Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
        Blocks.PODZOL, true, Blocks.SPRUCE_LOG.defaultBlockState());
    public static final Tint SNOWY = new Tint("snowy",
        Blocks.CALCITE.defaultBlockState(),
        Blocks.COBBLED_DEEPSLATE, false, Blocks.SPRUCE_LOG.defaultBlockState());

    public final Tint tint;
    private final Condition condition;
    private final long seed;

    private AncientPalette(Tint tint, Condition condition, long seed) {
        this.tint = tint;
        this.condition = condition;
        this.seed = seed;
    }

    /** Folds a biome id path to a tint id: "snowy_taiga" is snowy before it is taiga. */
    public static String tintIdForBiomePath(String path) {
        if (path.contains("snowy") || path.contains("frozen") || path.contains("ice")
                || path.contains("grove")) return "snowy";
        if (path.contains("desert") || path.contains("badlands")) return "desert";
        if (path.contains("savanna")) return "savanna";
        if (path.contains("taiga")) return "taiga";
        return "temperate";
    }

    /** Unknown or null tint ids fall back to temperate, so a bad tag still builds. */
    public static AncientPalette forTint(String tintId, Condition condition, long seed) {
        Tint tint = switch (tintId == null ? "" : tintId) {
            case "desert" -> DESERT;
            case "savanna" -> SAVANNA;
            case "taiga" -> TAIGA;
            case "snowy" -> SNOWY;
            default -> TEMPERATE;
        };
        return new AncientPalette(tint, condition, seed);
    }

    // ------------------------------------------------------------------ position-hashed picks

    /**
     * Wall masonry at a position. One material rules - deepslate brick - with cracked patches
     * whose share scales with the condition. The masons chose their stone; only time varies it.
     * The biome never enters the wall mix: tint speaks through vegetation, rubble, and ground
     * works instead, so walls read as built rather than shuffled.
     */
    public BlockState masonryAt(int x, int y, int z) {
        int agedWeight = switch (condition) {
            case PRISTINE -> 0;
            case WEATHERED -> 1;
            case CRUMBLING -> 2;
            case RUINED -> 3;
        };
        int roll = Erode.patchRoll(seed, x, y, z, 10);
        if (roll < agedWeight) return Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
        if (roll < agedWeight + 1) return Blocks.DEEPSLATE_TILES.defaultBlockState();
        return Blocks.DEEPSLATE_BRICKS.defaultBlockState();
    }

    /** Dressed trim - string courses, corridor ceilings, bridge decks - in the biome's accent
     *  stone: the one place the region speaks in the masonry. */
    public BlockState trimAt(int x, int y, int z) {
        return tint.accent();
    }

    /** Interior paving: an accent-stone field with deepslate tile flecks. */
    public BlockState floorAt(int x, int y, int z) {
        int roll = Math.floorMod(Erode.hash(seed, x, y, z + 29), 10);
        if (roll < 7) return tint.accent();
        return Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    /** Sea-worn masonry for island walls near the waterline. */
    public BlockState seawornAt(int x, int y, int z) {
        int roll = Erode.patchRoll(seed, x, y, z + 41, 10);
        if (roll < 6) return Blocks.PRISMARINE_BRICKS.defaultBlockState();
        if (roll < 9) return Blocks.PRISMARINE.defaultBlockState();
        return Blocks.DARK_PRISMARINE.defaultBlockState();
    }

    // ---------------------------------------------------------------------- sequential picks

    /** What a collapsed wall's mass becomes on the ground. */
    public BlockState rubble(RandomSource random) {
        return switch (random.nextInt(10)) {
            case 0, 1, 2, 3 -> Blocks.COBBLED_DEEPSLATE.defaultBlockState();
            case 4, 5 -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case 6, 7 -> Blocks.TUFF.defaultBlockState();
            case 8 -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
            default -> tint.drift().defaultBlockState();
        };
    }

    // ---------------------------------------------------------------------- fixed materials

    public BlockState plinth() {
        return Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
    }

    public BlockState monolith() {
        return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    }

    public BlockState dressed() {
        return Blocks.CHISELED_DEEPSLATE.defaultBlockState();
    }

    public BlockState roof() {
        return Blocks.DEEPSLATE_TILES.defaultBlockState();
    }

    public BlockState roofStairs() {
        return Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
    }

    public BlockState roofSlab() {
        return Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
    }

    public BlockState stairs() {
        return Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState();
    }

    public BlockState slab() {
        return Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState();
    }

    public BlockState beam() {
        return tint.beam();
    }

    public BlockState parapet() {
        return Blocks.DEEPSLATE_TILE_WALL.defaultBlockState();
    }

    public BlockState lantern() {
        return Blocks.SOUL_LANTERN.defaultBlockState();
    }

    public Condition condition() {
        return condition;
    }

    public long seed() {
        return seed;
    }
}
