package com.villagecastles.generator;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

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
        Blocks.LANTERN
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
        Blocks.SOUL_LANTERN
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
        Blocks.ACACIA_DOOR,
        Blocks.ACACIA_TRAPDOOR,
        // Roof and accent
        Blocks.ORANGE_TERRACOTTA,
        Blocks.MUD_BRICK_STAIRS,
        Blocks.MUD_BRICK_SLAB,
        Blocks.MUD_BRICK_WALL,
        // Floor
        Blocks.PACKED_MUD,
        // Decoration
        Blocks.IRON_BARS,
        Blocks.LANTERN
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
        Blocks.LANTERN
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
        Blocks.SOUL_LANTERN
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

    BiomePalette(String id, String displayName,
                 Block primaryWall, Block secondaryWall, Block accentWall,
                 Block planks, Block log, Block woodStairs, Block woodSlab,
                 Block fence, Block door, Block trapdoor,
                 Block roofBlock, Block stoneStairs, Block stoneSlab, Block wall,
                 Block floor, Block bars, Block light) {
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
        this.door = door;
        this.trapdoor = trapdoor;
        this.roofBlock = roofBlock;
        this.stoneStairs = stoneStairs;
        this.stoneSlab = stoneSlab;
        this.wall = wall;
        this.floor = floor;
        this.bars = bars;
        this.light = light;
    }

    public BlockState getPrimaryWallState() {
        return primaryWall.getDefaultState();
    }

    public BlockState getSecondaryWallState() {
        return secondaryWall.getDefaultState();
    }

    public BlockState getAccentWallState() {
        return accentWall.getDefaultState();
    }

    public BlockState getPlanksState() {
        return planks.getDefaultState();
    }

    public BlockState getLogState() {
        return log.getDefaultState();
    }

    public BlockState getFloorState() {
        return floor.getDefaultState();
    }

    public BlockState getRoofState() {
        return roofBlock.getDefaultState();
    }

    public BlockState getLightState() {
        return light.getDefaultState();
    }

    public BlockState getWallState() {
        return wall.getDefaultState();
    }

    public BlockState getBarsState() {
        return bars.getDefaultState();
    }

    public BlockState getFenceState() {
        return fence.getDefaultState();
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
     * Get a random wall block for variety (weighted towards primary).
     */
    public Block getRandomWallBlock(java.util.Random random) {
        int roll = random.nextInt(10);
        if (roll < 6) return primaryWall;
        if (roll < 9) return secondaryWall;
        return accentWall;
    }
}
