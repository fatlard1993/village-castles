package com.villagecastles.generator;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.DyeColor;

/**
 * Defines material palettes for each village biome type.
 * Each palette provides themed blocks for castle construction.
 */
public enum BiomePalette {
    PLAINS(
        "plains",
        "Classic Medieval Castle",
        // Primary building materials
        Blocks.STONE_BRICKS,
        Blocks.MOSSY_STONE_BRICKS,
        Blocks.CRACKED_STONE_BRICKS,
        // Wood types
        Blocks.OAK_PLANKS,
        Blocks.OAK_LOG,
        Blocks.OAK_STAIRS,
        Blocks.OAK_SLAB,
        Blocks.OAK_FENCE,
        Blocks.OAK_FENCE_GATE,
        Blocks.OAK_DOOR,
        Blocks.OAK_TRAPDOOR,
        // Roof and accent
        Blocks.COBBLESTONE,
        Blocks.STONE_BRICK_STAIRS,
        Blocks.STONE_BRICK_SLAB,
        Blocks.STONE_BRICK_WALL,
        // Floor
        Blocks.POLISHED_ANDESITE,
        // Decoration
        Blocks.IRON_BARS,
        Blocks.LANTERN,
        // Furnishing
        Blocks.BED.pick(DyeColor.RED),
        Blocks.CARPET.pick(DyeColor.RED)
    ),

    DESERT(
        "desert",
        "Sandstone Citadel",
        // Primary building materials
        Blocks.SANDSTONE,
        Blocks.CUT_SANDSTONE,
        Blocks.SMOOTH_SANDSTONE,
        // Wood types (minimal, terracotta instead)
        Blocks.ACACIA_PLANKS,
        Blocks.ACACIA_LOG,
        Blocks.ACACIA_STAIRS,
        Blocks.ACACIA_SLAB,
        Blocks.ACACIA_FENCE,
        Blocks.ACACIA_FENCE_GATE,
        Blocks.ACACIA_DOOR,
        Blocks.ACACIA_TRAPDOOR,
        // Roof and accent
        Blocks.TERRACOTTA,
        Blocks.SANDSTONE_STAIRS,
        Blocks.SANDSTONE_SLAB,
        Blocks.SANDSTONE_WALL,
        // Floor
        Blocks.SMOOTH_SANDSTONE,
        // Decoration
        Blocks.IRON_BARS,
        Blocks.SOUL_LANTERN,
        // Furnishing
        Blocks.BED.pick(DyeColor.YELLOW),
        Blocks.CARPET.pick(DyeColor.YELLOW)
    ),

    SAVANNA(
        "savanna",
        "Acacia Stronghold",
        // Primary building materials
        Blocks.MUD_BRICKS,
        Blocks.PACKED_MUD,
        Blocks.COBBLESTONE,
        // Wood types
        Blocks.ACACIA_PLANKS,
        Blocks.ACACIA_LOG,
        Blocks.ACACIA_STAIRS,
        Blocks.ACACIA_SLAB,
        Blocks.ACACIA_FENCE,
        Blocks.ACACIA_FENCE_GATE,
        Blocks.ACACIA_DOOR,
        Blocks.ACACIA_TRAPDOOR,
        // Roof and accent
        Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE),
        Blocks.MUD_BRICK_STAIRS,
        Blocks.MUD_BRICK_SLAB,
        Blocks.MUD_BRICK_WALL,
        // Floor
        Blocks.PACKED_MUD,
        // Decoration
        Blocks.IRON_BARS,
        Blocks.LANTERN,
        // Furnishing
        Blocks.BED.pick(DyeColor.ORANGE),
        Blocks.CARPET.pick(DyeColor.ORANGE)
    ),

    TAIGA(
        "taiga",
        "Nordic Fortress",
        // Primary building materials
        Blocks.COBBLESTONE,
        Blocks.MOSSY_COBBLESTONE,
        Blocks.STONE,
        // Wood types
        Blocks.SPRUCE_PLANKS,
        Blocks.SPRUCE_LOG,
        Blocks.SPRUCE_STAIRS,
        Blocks.SPRUCE_SLAB,
        Blocks.SPRUCE_FENCE,
        Blocks.SPRUCE_FENCE_GATE,
        Blocks.SPRUCE_DOOR,
        Blocks.SPRUCE_TRAPDOOR,
        // Roof and accent
        Blocks.DEEPSLATE_TILES,
        Blocks.COBBLESTONE_STAIRS,
        Blocks.COBBLESTONE_SLAB,
        Blocks.COBBLESTONE_WALL,
        // Floor
        Blocks.SPRUCE_PLANKS,
        // Decoration
        Blocks.IRON_BARS,
        Blocks.LANTERN,
        // Furnishing
        Blocks.BED.pick(DyeColor.BROWN),
        Blocks.CARPET.pick(DyeColor.BROWN)
    ),

    SNOWY(
        "snowy",
        "Ice Keep",
        // Primary building materials
        Blocks.PACKED_ICE,
        Blocks.BLUE_ICE,
        Blocks.STONE_BRICKS,
        // Wood types
        Blocks.SPRUCE_PLANKS,
        Blocks.SPRUCE_LOG,
        Blocks.SPRUCE_STAIRS,
        Blocks.SPRUCE_SLAB,
        Blocks.SPRUCE_FENCE,
        Blocks.SPRUCE_FENCE_GATE,
        Blocks.SPRUCE_DOOR,
        Blocks.SPRUCE_TRAPDOOR,
        // Roof and accent
        Blocks.SNOW_BLOCK,
        Blocks.STONE_BRICK_STAIRS,
        Blocks.STONE_BRICK_SLAB,
        Blocks.STONE_BRICK_WALL,
        // Floor
        Blocks.PACKED_ICE,
        // Decoration
        Blocks.IRON_BARS,
        Blocks.SOUL_LANTERN,
        // Furnishing
        Blocks.BED.pick(DyeColor.LIGHT_BLUE),
        Blocks.CARPET.pick(DyeColor.LIGHT_BLUE)
    );

    public final String id;
    public final String displayName;

    // Primary structure blocks
    public final Block primaryWall;
    public final Block secondaryWall;
    public final Block accentWall;

    // Wood components
    public final Block planks;
    public final Block log;
    public final Block woodStairs;
    public final Block woodSlab;
    public final Block fence;
    public final Block fenceGate;
    public final Block door;
    public final Block trapdoor;

    // Stone components
    public final Block roofBlock;
    public final Block stoneStairs;
    public final Block stoneSlab;
    public final Block wall;

    // Floor and decoration
    public final Block floor;
    public final Block bars;
    public final Block light;

    // Furnishing
    public final Block bed;
    public final Block carpet;

    BiomePalette(String id, String displayName,
                 Block primaryWall, Block secondaryWall, Block accentWall,
                 Block planks, Block log, Block woodStairs, Block woodSlab,
                 Block fence, Block fenceGate, Block door, Block trapdoor,
                 Block roofBlock, Block stoneStairs, Block stoneSlab, Block wall,
                 Block floor, Block bars, Block light,
                 Block bed, Block carpet) {
        this.id = id;
        this.displayName = displayName;
        this.primaryWall = primaryWall;
        this.secondaryWall = secondaryWall;
        this.accentWall = accentWall;
        this.planks = planks;
        this.log = log;
        this.woodStairs = woodStairs;
        this.woodSlab = woodSlab;
        this.fence = fence;
        this.fenceGate = fenceGate;
        this.door = door;
        this.trapdoor = trapdoor;
        this.roofBlock = roofBlock;
        this.stoneStairs = stoneStairs;
        this.stoneSlab = stoneSlab;
        this.wall = wall;
        this.floor = floor;
        this.bars = bars;
        this.light = light;
        this.bed = bed;
        this.carpet = carpet;
    }

    public BlockState getPrimaryWallState() {
        return primaryWall.defaultBlockState();
    }

    public BlockState getSecondaryWallState() {
        return secondaryWall.defaultBlockState();
    }

    public BlockState getAccentWallState() {
        return accentWall.defaultBlockState();
    }

    public BlockState getPlanksState() {
        return planks.defaultBlockState();
    }

    public BlockState getLogState() {
        return log.defaultBlockState();
    }

    public BlockState getFloorState() {
        return floor.defaultBlockState();
    }

    public BlockState getRoofState() {
        return roofBlock.defaultBlockState();
    }

    public BlockState getLightState() {
        return light.defaultBlockState();
    }

    public BlockState getWallState() {
        return wall.defaultBlockState();
    }

    public BlockState getBarsState() {
        return bars.defaultBlockState();
    }

    public BlockState getFenceState() {
        return fence.defaultBlockState();
    }

    public BlockState getFenceGateState() {
        return fenceGate.defaultBlockState();
    }

    public BlockState getBedState() {
        return bed.defaultBlockState();
    }

    public BlockState getCarpetState() {
        return carpet.defaultBlockState();
    }

    /**
     * Get a palette by its biome ID string.
     */
    public static BiomePalette fromId(String id) {
        for (BiomePalette palette : values()) {
            if (palette.id.equalsIgnoreCase(id)) {
                return palette;
            }
        }
        return null;
    }

    /**
     * Get a random wall block state for variety (weighted towards primary).
     */
    public BlockState getRandomWallBlock(java.util.Random random) {
        int roll = random.nextInt(10);
        if (roll < 6) return primaryWall.defaultBlockState();
        if (roll < 9) return secondaryWall.defaultBlockState();
        return accentWall.defaultBlockState();
    }
}
