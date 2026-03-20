package com.villagecastles.integration;

import com.villagecastles.VillageCastles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.List;
import java.util.UUID;

/**
 * Optional integration with the Village Quests mod.
 * Registers castle-themed quests and dialogue that make villages with castles
 * feel like fortified settlements with garrisons, armories, and history.
 *
 * Uses reflection to avoid compile-time dependency on village-quests.
 *
 * Quest design philosophy:
 * - Quests should feel like they BELONG in a castle village
 * - They reference walls, towers, guards, garrisons, ruins
 * - They give professions castle-specific roles (mason repairs walls, fletcher stocks towers)
 * - The cleric quest ties directly to zombie villager spawners in ruins
 */
public class VillageQuestsIntegration {

    private static boolean initialized = false;

    // Reflection references
    private static Method registerProfessionQuestMethod;
    private static Method registerUniversalQuestMethod;
    private static Method registerProfessionDialogueMethod;
    private static Method registerDialogueHandlerMethod;
    private static Constructor<?> fetchQuestConstructor;
    private static Constructor<?> dialogueOptionConstructor;

    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("village-quests")) {
            VillageCastles.LOGGER.info("Village Quests not found, skipping quest integration");
            return;
        }

        VillageCastles.LOGGER.info("Village Quests detected, registering castle quests...");

        try {
            // QuestRegistry API
            Class<?> questRegistryClass = Class.forName("justfatlard.village_quests.api.QuestRegistry");
            Class<?> questGeneratorClass = Class.forName("justfatlard.village_quests.api.QuestRegistry$QuestGenerator");
            registerProfessionQuestMethod = questRegistryClass.getMethod(
                "registerProfessionQuest", String.class, questGeneratorClass);
            registerUniversalQuestMethod = questRegistryClass.getMethod(
                "registerUniversalQuest", questGeneratorClass);

            // FetchItemQuest constructor
            Class<?> fetchQuestClass = Class.forName("justfatlard.village_quests.quest.FetchItemQuest");
            fetchQuestConstructor = fetchQuestClass.getConstructor(
                String.class, UUID.class,
                net.minecraft.item.Item.class, int.class,
                int.class, String.class);

            // DialogueRegistry API
            Class<?> dialogueRegistryClass = Class.forName("justfatlard.village_quests.api.DialogueRegistry");
            Class<?> dialogueProviderClass = Class.forName("justfatlard.village_quests.api.DialogueRegistry$DialogueProvider");
            Class<?> dialogueHandlerClass = Class.forName("justfatlard.village_quests.api.DialogueRegistry$DialogueHandler");
            registerProfessionDialogueMethod = dialogueRegistryClass.getMethod(
                "registerProfessionDialogue", String.class, dialogueProviderClass);
            registerDialogueHandlerMethod = dialogueRegistryClass.getMethod(
                "registerDialogueHandler", String.class, dialogueHandlerClass);

            // DialogueOption constructor
            Class<?> dialogueOptionClass = Class.forName("justfatlard.village_quests.api.DialogueRegistry$DialogueOption");
            dialogueOptionConstructor = dialogueOptionClass.getConstructor(
                String.class, Text.class, int.class, int.class);

            registerCastleQuests();
            registerCastleDialogue();

            VillageCastles.LOGGER.info("Registered castle quests and dialogue with Village Quests");

        } catch (ClassNotFoundException e) {
            VillageCastles.LOGGER.warn("Village Quests API class not found: {}", e.getMessage());
        } catch (NoSuchMethodException e) {
            VillageCastles.LOGGER.warn("Village Quests API method not found (version mismatch?): {}", e.getMessage());
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to register with Village Quests: {}", e.getMessage());
        }
    }


    // ---------------------------------------------------------------
    // Biome & Weather Helpers
    // ---------------------------------------------------------------

    private static String classifyBiome(net.minecraft.entity.passive.VillagerEntity villager) {
        if (!(villager.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return "plains";
        String path = sw.getBiome(villager.getBlockPos()).getKey()
            .map(k -> k.getValue().getPath()).orElse("");
        if (path.contains("desert")) return "desert";
        if (path.contains("taiga")) return path.contains("snowy") ? "snowy" : "taiga";
        if (path.contains("snowy") || path.contains("ice") || path.contains("frozen")) return "snowy";
        if (path.contains("savanna")) return "savanna";
        if (path.contains("jungle") || path.contains("bamboo")) return "jungle";
        if (path.contains("swamp") || path.contains("mangrove")) return "swamp";
        return "plains";
    }

    private static String getWeatherFlavor(net.minecraft.entity.passive.VillagerEntity villager) {
        if (!(villager.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return null;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        if (rng.nextDouble() > 0.35) return null; // 35% chance to fire
        long time = sw.getTimeOfDay() % 24000;
        if (sw.isThundering()) {
            String[] s = {"Storm weakened the north wall. ", "Lightning cracked the tower mortar. "};
            return s[rng.nextInt(s.length)];
        }
        if (sw.isRaining()) {
            String[] s = {"Water's getting into the armory. ", "Moat overflow near the east gate. "};
            return s[rng.nextInt(s.length)];
        }
        if (time >= 13000) {
            String[] s = {"Guards doubled. Still not enough. ", "Something scratching at the portcullis. "};
            return s[rng.nextInt(s.length)];
        }
        if (time < 2000) return "Morning inspection. Found cracks in the foundation. ";
        return null;
    }

    // ---------------------------------------------------------------
    // Quest Registration
    // ---------------------------------------------------------------

    private static void registerCastleQuests() throws Exception {
        // Mason — wall repair and fortification
        registerBiomeAwareFetchQuest("mason", 32, 6, 0.15,
            new Object[][] {
                {"plains", Items.STONE_BRICKS, "The eastern wall section is crumbling. Need stone bricks to shore it up."},
                {"desert", Items.SANDSTONE, "The sandstone is flaking off the east wall. Sand does that. Need more."},
                {"taiga", Items.SPRUCE_PLANKS, "The spruce beams are splitting in the cold. Need planks to reinforce."},
                {"snowy", Items.PACKED_ICE, "Ice walls need packed ice to patch. Sounds strange. Works."},
                {"savanna", Items.MUD_BRICKS, "The mud bricks crack in the dry heat. Need fresh ones."}
            });

        registerFetchQuest("mason",
            Items.COBBLESTONE, 48, 5, 0.12,
            "Foundation's settling under the tower. Cobblestone for repairs, if you've got any.");

        // Weaponsmith — arming the garrison
        registerFetchQuest("weaponsmith",
            Items.IRON_SWORD, 2, 7, 0.10,
            "Two of my swords broke during training drills. The night watch can't patrol unarmed.");

        registerFetchQuest("weaponsmith",
            Items.IRON_INGOT, 6, 6, 0.12,
            "I'm forging new pikes for the gatehouse. Iron ingots — as many as you can get.");

        // Armorer — outfitting the watch
        registerFetchQuest("armorer",
            Items.IRON_CHESTPLATE, 1, 8, 0.08,
            "New recruits need proper armor. Even one chestplate would help.");

        registerFetchQuest("armorer",
            Items.LEATHER, 8, 5, 0.12,
            "The tower guards need new bracers. Leather — enough for the whole watch rotation.");

        // Fletcher — stocking the towers
        registerFetchQuest("fletcher",
            Items.ARROW, 64, 5, 0.15,
            "Tower guards are running low. A full stack should last the week.");

        registerFetchQuest("fletcher",
            Items.FEATHER, 16, 4, 0.12,
            "Got plenty of shafts and tips, but feathers... the chickens aren't cooperating.");

        // Farmer — provisioning the garrison
        registerBiomeAwareFetchQuest("farmer", 16, 5, 0.15,
            new Object[][] {
                {"plains", Items.BREAD, "The soldiers eat more than my fields can handle. Bread for the barracks."},
                {"desert", Items.MELON_SLICE, "Melons are all we grow here. The garrison eats through them fast."},
                {"taiga", Items.POTATO, "Potatoes. Only thing the frost doesn't kill. The garrison needs more."},
                {"savanna", Items.DRIED_KELP, "Dried kelp keeps. The garrison needs rations that last."}
            });

        registerFetchQuest("farmer",
            Items.HAY_BLOCK, 4, 4, 0.10,
            "The stables need feed. Four hay bales and the horses eat through winter.");

        // Toolsmith — maintaining the castle
        registerFetchQuest("toolsmith",
            Items.IRON_INGOT, 8, 6, 0.10,
            "The portcullis mechanism is worn through. I need iron to forge replacement gears.");

        registerFetchQuest("toolsmith",
            Items.IRON_AXE, 2, 5, 0.08,
            "The woodcutters broke their axes reinforcing the palisade. Two replacements, sharp ones.");

        // Cleric — the ruins connection
        registerFetchQuest("cleric",
            Items.GOLDEN_APPLE, 1, 10, 0.06,
            "Travelers found a zombie villager in the old ruins. They were one of ours, once. A golden apple might bring them back.");

        registerFetchQuest("cleric",
            Items.GLISTERING_MELON_SLICE, 4, 5, 0.10,
            "The garrison took wounds in the last skirmish. I need glistering melon for healing potions.");

        // Librarian — castle lore and records
        registerFetchQuest("librarian",
            Items.BOOK, 3, 5, 0.12,
            "I'm writing down the castle's history — who built it, who held it. Need blank books.");

        registerFetchQuest("librarian",
            Items.WRITABLE_BOOK, 1, 6, 0.08,
            "The old charter's fading. I need a book and quill to copy it before it's gone.");

        // Universal — any villager in a castle village might ask
        registerBiomeAwareFetchQuestUniversal(32, 4, 0.08,
            new Object[][] {
                {"snowy", Items.SOUL_TORCH, "Lanterns freeze. Need soul torches for the watchtowers."},
                {"plains", Items.TORCH, "Castle corridors are pitch black at night. Torches — as many as you've got."}
            });

        registerFetchQuestUniversal(
            Items.COBBLESTONE, 16, 3, 0.06,
            "Wall collapsed near the well. Could you bring cobblestone? Anybody could do it, but...");
    }

    /**
     * Register a profession-specific fetch quest via reflection.
     * @param chance Probability (0-1) that this quest is offered when generating
     */
    private static void registerFetchQuest(String profession, net.minecraft.item.Item item,
                                            int count, int reputationShift, double chance,
                                            String description) throws Exception {
        Object generator = java.lang.reflect.Proxy.newProxyInstance(
            VillageQuestsIntegration.class.getClassLoader(),
            new Class<?>[]{ Class.forName("justfatlard.village_quests.api.QuestRegistry$QuestGenerator") },
            (proxy, method, args) -> {
                if (!"generate".equals(method.getName())) return null;
                java.util.Random random = (java.util.Random) args[3];
                if (random.nextDouble() >= chance) return null;
                String villagerName = (String) args[1];
                net.minecraft.entity.passive.VillagerEntity villager =
                    (net.minecraft.entity.passive.VillagerEntity) args[0];
                String desc = description;
                String weather = getWeatherFlavor(villager);
                if (weather != null) desc = weather + desc;
                return fetchQuestConstructor.newInstance(
                    villagerName, villager.getUuid(), item, count, reputationShift, desc);
            }
        );

        registerProfessionQuestMethod.invoke(null, profession, generator);
    }

    private static void registerFetchQuestUniversal(net.minecraft.item.Item item,
                                                     int count, int reputationShift, double chance,
                                                     String description) throws Exception {
        Object generator = java.lang.reflect.Proxy.newProxyInstance(
            VillageQuestsIntegration.class.getClassLoader(),
            new Class<?>[]{ Class.forName("justfatlard.village_quests.api.QuestRegistry$QuestGenerator") },
            (proxy, method, args) -> {
                if (!"generate".equals(method.getName())) return null;
                java.util.Random random = (java.util.Random) args[3];
                if (random.nextDouble() >= chance) return null;
                String villagerName = (String) args[1];
                net.minecraft.entity.passive.VillagerEntity villager =
                    (net.minecraft.entity.passive.VillagerEntity) args[0];
                return fetchQuestConstructor.newInstance(
                    villagerName, villager.getUuid(), item, count, reputationShift, description);
            }
        );

        registerUniversalQuestMethod.invoke(null, generator);
    }


    /**
     * Register a biome-aware profession fetch quest. Picks item/description based on village biome.
     * variants: Object[][] where each row is {biomeString, Item, description}. "plains" is default.
     */
    private static void registerBiomeAwareFetchQuest(String profession, int count, int repShift,
                                                      double chance, Object[][] variants) throws Exception {
        Object generator = java.lang.reflect.Proxy.newProxyInstance(
            VillageQuestsIntegration.class.getClassLoader(),
            new Class<?>[]{ Class.forName("justfatlard.village_quests.api.QuestRegistry$QuestGenerator") },
            (proxy, method, args) -> {
                if (!"generate".equals(method.getName())) return null;
                java.util.Random random = (java.util.Random) args[3];
                if (random.nextDouble() >= chance) return null;
                net.minecraft.entity.passive.VillagerEntity villager =
                    (net.minecraft.entity.passive.VillagerEntity) args[0];
                String villagerName = (String) args[1];
                String biome = classifyBiome(villager);
                // Find matching biome variant, fall back to plains
                net.minecraft.item.Item item = Items.STONE_BRICKS;
                String desc = "";
                for (Object[] v : variants) {
                    if (biome.equals(v[0]) || "plains".equals(v[0])) {
                        item = (net.minecraft.item.Item) v[1];
                        desc = (String) v[2];
                        if (biome.equals(v[0])) break; // exact match wins
                    }
                }
                String weather = getWeatherFlavor(villager);
                if (weather != null) desc = weather + desc;
                return fetchQuestConstructor.newInstance(villagerName, villager.getUuid(), item, count, repShift, desc);
            }
        );
        registerProfessionQuestMethod.invoke(null, profession, generator);
    }

    private static void registerBiomeAwareFetchQuestUniversal(int count, int repShift,
                                                                double chance, Object[][] variants) throws Exception {
        Object generator = java.lang.reflect.Proxy.newProxyInstance(
            VillageQuestsIntegration.class.getClassLoader(),
            new Class<?>[]{ Class.forName("justfatlard.village_quests.api.QuestRegistry$QuestGenerator") },
            (proxy, method, args) -> {
                if (!"generate".equals(method.getName())) return null;
                java.util.Random random = (java.util.Random) args[3];
                if (random.nextDouble() >= chance) return null;
                net.minecraft.entity.passive.VillagerEntity villager =
                    (net.minecraft.entity.passive.VillagerEntity) args[0];
                String villagerName = (String) args[1];
                String biome = classifyBiome(villager);
                net.minecraft.item.Item item = Items.TORCH;
                String desc = "";
                for (Object[] v : variants) {
                    if (biome.equals(v[0]) || "plains".equals(v[0])) {
                        item = (net.minecraft.item.Item) v[1];
                        desc = (String) v[2];
                        if (biome.equals(v[0])) break;
                    }
                }
                String weather = getWeatherFlavor(villager);
                if (weather != null) desc = weather + desc;
                return fetchQuestConstructor.newInstance(villagerName, villager.getUuid(), item, count, repShift, desc);
            }
        );
        registerUniversalQuestMethod.invoke(null, generator);
    }

    // ---------------------------------------------------------------
    // Dialogue Registration
    // ---------------------------------------------------------------

    private static void registerCastleDialogue() throws Exception {
        // Mason dialogue — talks about the walls
        registerDialogue("mason", "vc_mason_walls",
            Text.literal("How are the castle walls holding up?"), 0, 200,
            Text.literal("Better than before you started helping. The eastern section is solid now, but the north tower foundation worries me. Settling soil."));

        registerDialogue("mason", "vc_mason_ruins",
            Text.literal("Have you seen the ruins in the wilderness?"), 20, 200,
            Text.literal("Aye. Same stonework as ours. Whoever built this place built those too. Makes you wonder what happened to them."));

        // Weaponsmith — talks about defense
        registerDialogue("weaponsmith", "vc_smith_guard",
            Text.literal("Is the garrison well-armed?"), 0, 200,
            Text.literal("Well enough. I keep the grindstone going. But I'd sleep better with more iron in the armory."));

        registerDialogue("weaponsmith", "vc_smith_raids",
            Text.literal("Has the castle ever been attacked?"), 30, 200,
            Text.literal("Once. Pillagers came from the east. The walls held. That's the thing about stone — it doesn't care how angry you are."));

        // Librarian — castle history
        registerDialogue("librarian", "vc_lib_history",
            Text.literal("What do you know about this castle's history?"), 10, 200,
            Text.literal("Records don't go back far enough. Somebody built this place, though — the stonework's too good for us. Military, maybe. The ruins nearby look the same."));

        registerDialogue("librarian", "vc_lib_ruins",
            Text.literal("Tell me about the ruins nearby."), 40, 200,
            Text.literal("The old fortress? Dangerous. Full of undead. But the stonework matches ours. Same builders, different fate. I'd love to get a closer look, but... no."));

        // Cleric — talks about the fallen
        registerDialogue("cleric", "vc_cleric_zombie",
            Text.literal("Are there really zombie villagers in the ruins?"), 20, 200,
            Text.literal("They used to live here. When the old fortress fell, not everyone got out. They're still in there, wandering. A golden apple and a splash of weakness... we could bring them back."));

        // Fletcher — tower watch
        registerDialogue("fletcher", "vc_fletcher_tower",
            Text.literal("How's the view from the towers?"), 0, 200,
            Text.literal("On a clear day you can see the old ruins from up there. The guards watch the horizon. Arrows carry further from height, too."));

        // Farmer — garrison life
        registerDialogue("farmer", "vc_farmer_feed",
            Text.literal("Is it hard feeding the whole garrison?"), 10, 200,
            Text.literal("Feeding this many people wasn't the plan. Soldiers, guards, the smith, the horses... but they keep us safe, so I keep planting."));

        // Grief dialogue — surfaces when a castle villager has recently died
        registerDialogue("mason", "vc_grief_mason",
            Text.literal("The watchtower is unmanned tonight."), 0, 200,
            Text.literal("Nobody wanted the shift. Not after what happened."));

        registerDialogue("weaponsmith", "vc_grief_smith",
            Text.literal("Their post is empty."), 0, 200,
            Text.literal("Nobody's taken it. The tools are still where they left them."));

        registerDialogue("farmer", "vc_grief_farmer",
            Text.literal("The garrison feels smaller today."), 0, 200,
            Text.literal("One less mouth to feed. That's the wrong way to think about it. But I thought it."));

        // Armorer — talks about equipment
        registerDialogue("armorer", "vc_armorer_watch",
            Text.literal("Do the tower guards have good armor?"), 0, 200,
            Text.literal("Good enough for arrows. Not enough for a full siege. I keep the blast furnace running day and night, but iron doesn't grow on trees."));
    }

    private static void registerDialogue(String profession, String optionId,
                                          Text displayText, int minRep, int maxRep,
                                          Text response) throws Exception {
        // Create DialogueProvider proxy
        Object provider = java.lang.reflect.Proxy.newProxyInstance(
            VillageQuestsIntegration.class.getClassLoader(),
            new Class<?>[]{ Class.forName("justfatlard.village_quests.api.DialogueRegistry$DialogueProvider") },
            (proxy, method, args) -> {
                if (!"getOptions".equals(method.getName())) return new ArrayList<>();
                List<Object> options = new ArrayList<>();
                options.add(dialogueOptionConstructor.newInstance(optionId, displayText, minRep, maxRep));
                return options;
            }
        );

        // Create DialogueHandler proxy
        Object handler = java.lang.reflect.Proxy.newProxyInstance(
            VillageQuestsIntegration.class.getClassLoader(),
            new Class<?>[]{ Class.forName("justfatlard.village_quests.api.DialogueRegistry$DialogueHandler") },
            (proxy, method, args) -> {
                if (!"handle".equals(method.getName())) return response;
                return response;
            }
        );

        registerProfessionDialogueMethod.invoke(null, profession, provider);
        registerDialogueHandlerMethod.invoke(null, optionId, handler);
    }
}
