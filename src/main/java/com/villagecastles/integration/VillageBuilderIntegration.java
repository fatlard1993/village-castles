package com.villagecastles.integration;

import com.villagecastles.VillageCastles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional integration with the Village Builder mod.
 * Registers castle structures so they can be built by Builder villagers.
 * Uses reflection to avoid compile-time dependency on village-builder.
 */
public class VillageBuilderIntegration {

    private static boolean initialized = false;
    private static Method registerStructureMethod = null;

    /**
     * Initialize the integration if village-builder is present.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("village-builder")) {
            VillageCastles.LOGGER.info("Village Builder not found, skipping integration");
            return;
        }

        VillageCastles.LOGGER.info("Village Builder detected, registering castle structures...");

        try {
            // Get the API class and method via reflection
            Class<?> apiClass = Class.forName("justfatlard.village_builder.api.VillageBuilderAPI");
            registerStructureMethod = apiClass.getMethod(
                "registerStructure",
                Identifier.class,
                String.class,
                String.class,
                Map.class,
                int.class
            );

            registerStructures();
            VillageCastles.LOGGER.info("Successfully registered castle structures with Village Builder!");
        } catch (ClassNotFoundException e) {
            VillageCastles.LOGGER.warn("Village Builder API class not found: " + e.getMessage());
        } catch (NoSuchMethodException e) {
            VillageCastles.LOGGER.warn("Village Builder API method not found: " + e.getMessage());
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to register with Village Builder: " + e.getMessage());
        }
    }

    /**
     * Call the registerStructure method via reflection.
     */
    private static void registerStructure(
        Identifier structureId,
        String displayName,
        String villageType,
        Map<Item, Integer> materials,
        int complexity
    ) {
        if (registerStructureMethod == null) return;

        try {
            registerStructureMethod.invoke(null, structureId, displayName, villageType, materials, complexity);
        } catch (Exception e) {
            VillageCastles.LOGGER.warn("Failed to register structure " + displayName + ": " + e.getMessage());
        }
    }

    /**
     * Register all castle structures with the Village Builder API.
     */
    private static void registerStructures() {
        // Register each biome's castle
        registerPlainscastle();
        registerDesertCastle();
        registerSavannaCastle();
        registerTaigaCastle();
        registerSnowyCastle();

        // Register wall segments for each biome
        registerWallSegments();
    }

    private static void registerPlainscastle() {
        registerStructure(
            Identifier.of("villagecastles", "structure/plains/castle_center"),
            "Medieval Castle",
            "plains",
            createCastleMaterials(
                Items.STONE_BRICKS, 800,
                Items.COBBLESTONE, 400,
                Items.OAK_LOG, 200,
                Items.OAK_PLANKS, 150,
                Items.GLASS_PANE, 64,
                Items.IRON_BARS, 48,
                Items.TORCH, 64,
                Items.IRON_INGOT, 32,
                Items.COAL, 48
            ),
            5 // Very complex
        );
    }

    private static void registerDesertCastle() {
        registerStructure(
            Identifier.of("villagecastles", "structure/desert/castle_center"),
            "Sandstone Citadel",
            "desert",
            createCastleMaterials(
                Items.SANDSTONE, 600,
                Items.CUT_SANDSTONE, 400,
                Items.RED_TERRACOTTA, 200,
                Items.ACACIA_LOG, 100,
                Items.ACACIA_PLANKS, 100,
                Items.GLASS_PANE, 48,
                Items.IRON_BARS, 32,
                Items.TORCH, 64,
                Items.IRON_INGOT, 24,
                Items.COAL, 32
            ),
            5
        );
    }

    private static void registerSavannaCastle() {
        registerStructure(
            Identifier.of("villagecastles", "structure/savanna/castle_center"),
            "Acacia Stronghold",
            "savanna",
            createCastleMaterials(
                Items.MUD_BRICKS, 500,
                Items.COBBLESTONE, 300,
                Items.ACACIA_LOG, 250,
                Items.ACACIA_PLANKS, 200,
                Items.TERRACOTTA, 150,
                Items.GLASS_PANE, 48,
                Items.IRON_BARS, 32,
                Items.TORCH, 64,
                Items.IRON_INGOT, 24,
                Items.COAL, 32
            ),
            5
        );
    }

    private static void registerTaigaCastle() {
        registerStructure(
            Identifier.of("villagecastles", "structure/taiga/castle_center"),
            "Nordic Fortress",
            "taiga",
            createCastleMaterials(
                Items.COBBLESTONE, 600,
                Items.MOSSY_COBBLESTONE, 200,
                Items.SPRUCE_LOG, 300,
                Items.SPRUCE_PLANKS, 250,
                Items.STONE_BRICKS, 200,
                Items.GLASS_PANE, 48,
                Items.IRON_BARS, 40,
                Items.LANTERN, 48,
                Items.IRON_INGOT, 32,
                Items.COAL, 40
            ),
            5
        );
    }

    private static void registerSnowyCastle() {
        registerStructure(
            Identifier.of("villagecastles", "structure/snowy/castle_center"),
            "Ice Keep",
            "snowy",
            createCastleMaterials(
                Items.PACKED_ICE, 400,
                Items.BLUE_ICE, 100,
                Items.STONE_BRICKS, 400,
                Items.SPRUCE_LOG, 200,
                Items.SPRUCE_PLANKS, 150,
                Items.GLASS_PANE, 48,
                Items.IRON_BARS, 32,
                Items.LANTERN, 64,
                Items.IRON_INGOT, 24,
                Items.COAL, 48
            ),
            5
        );
    }

    private static void registerWallSegments() {
        // Register wall segments for each biome type
        String[] biomes = {"plains", "desert", "savanna", "taiga", "snowy"};

        for (String biome : biomes) {
            String wallType = getWallTypeForBiome(biome);
            String displayName = capitalize(biome) + " Village Wall";

            registerStructure(
                Identifier.of("villagecastles", "structure/village_walls/" + biome + "/wall_straight"),
                displayName + " (Straight)",
                biome,
                createWallMaterials(wallType),
                2 // Simple
            );

            registerStructure(
                Identifier.of("villagecastles", "structure/village_walls/" + biome + "/wall_gate"),
                displayName + " (Gate)",
                biome,
                createGateMaterials(wallType),
                3 // Medium
            );

            registerStructure(
                Identifier.of("villagecastles", "structure/village_walls/" + biome + "/wall_tower"),
                displayName + " (Tower)",
                biome,
                createTowerMaterials(wallType),
                3 // Medium
            );
        }
    }

    private static String getWallTypeForBiome(String biome) {
        return switch (biome) {
            case "plains", "taiga" -> "palisade";
            case "desert", "savanna" -> "adobe";
            case "snowy" -> "stone";
            default -> "palisade";
        };
    }

    private static Map<Item, Integer> createWallMaterials(String wallType) {
        return switch (wallType) {
            case "palisade" -> createCastleMaterials(
                Items.OAK_LOG, 32,
                Items.OAK_FENCE, 16,
                Items.TORCH, 4
            );
            case "adobe" -> createCastleMaterials(
                Items.MUD_BRICKS, 48,
                Items.SANDSTONE, 32,
                Items.TORCH, 4
            );
            case "stone" -> createCastleMaterials(
                Items.STONE_BRICKS, 64,
                Items.COBBLESTONE, 32,
                Items.TORCH, 4
            );
            default -> createCastleMaterials(Items.COBBLESTONE, 64);
        };
    }

    private static Map<Item, Integer> createGateMaterials(String wallType) {
        Map<Item, Integer> materials = new HashMap<>(createWallMaterials(wallType));
        materials.put(Items.IRON_INGOT, 8);
        materials.put(Items.OAK_DOOR, 2);
        return materials;
    }

    private static Map<Item, Integer> createTowerMaterials(String wallType) {
        Map<Item, Integer> materials = new HashMap<>(createWallMaterials(wallType));
        // Towers need more materials
        materials.replaceAll((item, count) -> count * 2);
        materials.put(Items.LADDER, 8);
        return materials;
    }

    /**
     * Helper to create material maps with varargs.
     */
    private static Map<Item, Integer> createCastleMaterials(Object... itemsAndCounts) {
        Map<Item, Integer> materials = new HashMap<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            Item item = (Item) itemsAndCounts[i];
            Integer count = (Integer) itemsAndCounts[i + 1];
            materials.put(item, count);
        }
        return materials;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
