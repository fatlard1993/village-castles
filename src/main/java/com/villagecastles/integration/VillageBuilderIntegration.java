package com.villagecastles.integration;

import com.villagecastles.VillageCastles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Optional integration with the Village Builder mod.
 * Registers castle structures so they can be built by Builder villagers.
 * Uses reflection to avoid compile-time dependency on village-builder.
 *
 * Only registers structures whose NBT files actually exist.
 *
 * API surface targeted:
 *   justfatlard.village_builder.api.VillageBuilderAPI
 *   justfatlard.village_builder.village.VillageNeedsAnalyzer$VillageNeed
 *   justfatlard.village_builder.building.StructureType$MaterialRequirement
 */
public class VillageBuilderIntegration {

    private static boolean initialized = false;

    // Cached reflection references
    private static Method registerStructurePersistentMethod;
    private static Constructor<?> materialReqConstructor;
    private static Object needDefense;
    private static Object needHousing;

    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("village-builder")) {
            VillageCastles.LOGGER.info("Village Builder not found, skipping integration");
            return;
        }

        VillageCastles.LOGGER.info("Village Builder detected, registering castle structures...");

        try {
            Class<?> apiClass = Class.forName("justfatlard.village_builder.api.VillageBuilderAPI");

            // VillageNeed enum
            @SuppressWarnings("unchecked")
            Class<Enum<?>> needEnum = (Class<Enum<?>>) Class.forName(
                "justfatlard.village_builder.village.VillageNeedsAnalyzer$VillageNeed");
            needDefense = Enum.valueOf((Class) needEnum, "DEFENSE");
            needHousing = Enum.valueOf((Class) needEnum, "HOUSING");

            // MaterialRequirement record constructor
            Class<?> matReqClass = Class.forName(
                "justfatlard.village_builder.building.StructureType$MaterialRequirement");
            materialReqConstructor = matReqClass.getDeclaredConstructor(Item.class, int.class);

            // registerStructurePersistent(Identifier, String, Set<VillageNeed>, List<MaterialRequirement>, Set<String>, int)
            registerStructurePersistentMethod = apiClass.getMethod("registerStructurePersistent",
                Identifier.class, String.class, Set.class, List.class, Set.class, int.class);

            int registered = registerStructures();
            if (registered > 0) {
                VillageCastles.LOGGER.info("Registered {} castle structures with Village Builder", registered);
            } else {
                VillageCastles.LOGGER.info("No NBT files found yet — Village Builder will have nothing to build. "
                    + "Use /villagecastles status to check.");
            }
        } catch (ClassNotFoundException e) {
            VillageCastles.LOGGER.warn("Village Builder API class not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            VillageCastles.LOGGER.warn("Village Builder API method not found (version mismatch?): {}", e.getMessage());
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to register with Village Builder: {}", e.getMessage());
        }
    }

    /**
     * Register a structure with Village Builder via reflection.
     * Uses registerStructurePersistent so registrations survive world reloads.
     */
    private static void registerStructure(
        Identifier structureId,
        String displayName,
        Set<Object> needs,
        Map<Item, Integer> materials,
        Set<String> biomePreferences,
        int clearanceSize
    ) {
        if (registerStructurePersistentMethod == null) return;

        try {
            // Convert Map<Item, Integer> to List<MaterialRequirement>
            List<Object> requirements = new ArrayList<>();
            for (Map.Entry<Item, Integer> entry : materials.entrySet()) {
                requirements.add(materialReqConstructor.newInstance(entry.getKey(), entry.getValue()));
            }

            registerStructurePersistentMethod.invoke(null,
                structureId, displayName, needs, requirements, biomePreferences, clearanceSize);
        } catch (Exception e) {
            VillageCastles.LOGGER.warn("Failed to register structure {}: {}", displayName, e.getMessage());
        }
    }

    /**
     * Only register structures that have NBT files on disk.
     * Returns count of successfully registered structures.
     */
    private static int registerStructures() {
        return registerBiomeCastles();
    }

    private static boolean structureExists(String path) {
        return com.villagecastles.util.StructureHelper.structureNbtExists(path);
    }

    private static int registerBiomeCastles() {
        int count = 0;

        String[][] castles = {
            {"plains/castle_small", "Medieval Castle (Small)", "plains"},
            {"plains/castle_medium", "Medieval Castle (Medium)", "plains"},
            {"plains/castle_large", "Medieval Castle", "plains"},
            {"desert/castle_small", "Sandstone Citadel (Small)", "desert"},
            {"desert/castle_medium", "Sandstone Citadel (Medium)", "desert"},
            {"desert/castle_large", "Sandstone Citadel", "desert"},
            {"savanna/castle_small", "Acacia Stronghold (Small)", "savanna"},
            {"savanna/castle_medium", "Acacia Stronghold (Medium)", "savanna"},
            {"savanna/castle_large", "Acacia Stronghold", "savanna"},
            {"taiga/castle_small", "Nordic Fortress (Small)", "taiga"},
            {"taiga/castle_medium", "Nordic Fortress (Medium)", "taiga"},
            {"taiga/castle_large", "Nordic Fortress", "taiga"},
            {"snowy/castle_small", "Ice Keep (Small)", "snowy"},
            {"snowy/castle_medium", "Ice Keep (Medium)", "snowy"},
            {"snowy/castle_large", "Ice Keep", "snowy"},
        };

        Map<String, Map<Item, Integer>> biomeMaterials = Map.of(
            "plains", createCastleMaterials(
                Items.STONE_BRICKS, 800, Items.COBBLESTONE, 400,
                Items.OAK_LOG, 200, Items.OAK_PLANKS, 150,
                Items.GLASS_PANE, 64, Items.IRON_BARS, 48,
                Items.TORCH, 64, Items.IRON_INGOT, 32
            ),
            "desert", createCastleMaterials(
                Items.SANDSTONE, 600, Items.CUT_SANDSTONE, 400,
                Items.TERRACOTTA, 200, Items.ACACIA_LOG, 100,
                Items.ACACIA_PLANKS, 100, Items.GLASS_PANE, 48,
                Items.IRON_BARS, 32, Items.TORCH, 64
            ),
            "savanna", createCastleMaterials(
                Items.MUD_BRICKS, 500, Items.COBBLESTONE, 300,
                Items.ACACIA_LOG, 250, Items.ACACIA_PLANKS, 200,
                Items.TERRACOTTA, 150, Items.GLASS_PANE, 48,
                Items.IRON_BARS, 32, Items.TORCH, 64
            ),
            "taiga", createCastleMaterials(
                Items.COBBLESTONE, 600, Items.MOSSY_COBBLESTONE, 200,
                Items.SPRUCE_LOG, 300, Items.SPRUCE_PLANKS, 250,
                Items.STONE_BRICKS, 200, Items.GLASS_PANE, 48,
                Items.IRON_BARS, 40, Items.LANTERN, 48
            ),
            "snowy", createCastleMaterials(
                Items.PACKED_ICE, 400, Items.BLUE_ICE, 100,
                Items.STONE_BRICKS, 400, Items.SPRUCE_LOG, 200,
                Items.SPRUCE_PLANKS, 150, Items.GLASS_PANE, 48,
                Items.IRON_BARS, 32, Items.LANTERN, 64
            )
        );

        for (String[] castle : castles) {
            if (!structureExists(castle[0])) continue;

            String biome = castle[2];
            Map<Item, Integer> baseMaterials = biomeMaterials.get(biome);
            int clearanceSize;
            double materialScale;
            if (castle[0].contains("large")) {
                clearanceSize = 50;
                materialScale = 1.0;
            } else if (castle[0].contains("medium")) {
                clearanceSize = 30;
                materialScale = 0.6;
            } else {
                clearanceSize = 15;
                materialScale = 0.25;
            }

            // Scale material costs by castle size
            Map<Item, Integer> materials = new HashMap<>(baseMaterials);
            materials.replaceAll((item, amount) -> Math.max(1, (int) (amount * materialScale)));

            // Castles satisfy DEFENSE + HOUSING (they have beds and walls)
            Set<Object> needs = castle[0].contains("large")
                ? Set.of(needDefense, needHousing)
                : Set.of(needDefense);

            registerStructure(
                Identifier.of("villagecastles", castle[0]),
                castle[1],
                needs,
                materials,
                Set.of(biome),
                clearanceSize
            );
            count++;
        }

        return count;
    }

    private static int registerWallSegments() {
        String[] biomes = {"plains", "desert", "savanna", "taiga", "snowy"};
        String[] segments = {"wall_straight", "wall_corner", "wall_gate", "wall_tower", "wall_terminator"};
        int count = 0;

        for (String biome : biomes) {
            String wallType = getWallTypeForBiome(biome);
            String displayName = capitalize(biome) + " Village Wall";

            for (String segment : segments) {
                String path = "village_walls/" + biome + "/" + segment;
                if (!structureExists(path)) continue;

                String segDisplay = segment.replace("wall_", "");
                Map<Item, Integer> materials = switch (segment) {
                    case "wall_gate" -> createGateMaterials(wallType);
                    case "wall_tower" -> createTowerMaterials(wallType);
                    default -> createWallMaterials(wallType);
                };
                int clearanceSize = segment.equals("wall_straight") ? 5 : 8;

                registerStructure(
                    Identifier.of("villagecastles", path),
                    displayName + " (" + capitalize(segDisplay) + ")",
                    Set.of(needDefense),
                    materials,
                    Set.of(biome),
                    clearanceSize
                );
                count++;
            }
        }

        return count;
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
                Items.OAK_LOG, 32, Items.OAK_FENCE, 16, Items.TORCH, 4
            );
            case "adobe" -> createCastleMaterials(
                Items.MUD_BRICKS, 48, Items.SANDSTONE, 32, Items.TORCH, 4
            );
            case "stone" -> createCastleMaterials(
                Items.STONE_BRICKS, 64, Items.COBBLESTONE, 32, Items.TORCH, 4
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
        materials.replaceAll((item, c) -> c * 2);
        materials.put(Items.LADDER, 8);
        return materials;
    }

    private static Map<Item, Integer> createCastleMaterials(Object... itemsAndCounts) {
        Map<Item, Integer> materials = new HashMap<>();
        for (int i = 0; i < itemsAndCounts.length; i += 2) {
            Item item = (Item) itemsAndCounts[i];
            Integer c = (Integer) itemsAndCounts[i + 1];
            materials.put(item, c);
        }
        return materials;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
