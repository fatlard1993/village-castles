package com.villagecastles.integration;

import com.villagecastles.VillageCastles;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * What a village with a castle has to say about it, through Village Quests.
 *
 * <p>Gated on the castle being there. The first version of this registered its
 * quests and questions for every mason and fletcher in the world, so a village
 * with no wall had a mason asking for stone to shore up the wall, and four in
 * five villages have no wall. Every offer here first asks the census whether
 * this village generated with a keep; the questions about the wall itself only
 * come up when the player is standing near it, and the questions about the old
 * places only in a village that actually has one within a morning's walk.
 *
 * <p>Vanilla nouns. There is no garrison, no watch, no recruits: the villagers
 * who sleep in the keep are villagers, the thing that walks the wall is the
 * iron golem, the one attack anyone remembers was a raid, and what stirs in the
 * ancient ruins is skeletons and whatever keeps them coming. The castle is a
 * building the village has, not a faction the village is.
 *
 * <p>Reflection throughout, so this mod loads without Village Quests. The
 * verbatim-ask hook on fetch quests is optional; without it the errand still
 * works, in the host's generic words.
 */
public class VillageQuestsIntegration {

    private static boolean initialized = false;

    private static Class<?> questGeneratorClass;
    private static Class<?> dialogueProviderClass;
    private static Class<?> richHandlerClass;
    private static Method registerProfessionQuest;
    private static Method registerUniversalQuest;
    private static Method registerProfessionDialogue;
    private static Method registerRichHandler;
    private static Method replyOf;
    private static Method replyOption;
    private static Method replyWalkAway;
    private static Method withAsk;
    private static Constructor<?> fetchQuest;
    private static Constructor<?> dialogueOption;

    private static final ResourceKey<Structure> ANCIENT_CASTLE = ResourceKey.create(Registries.STRUCTURE,
        Identifier.fromNamespaceAndPath(VillageCastles.MOD_ID, "ancient_castle"));

    /** How far from the castle's footprint still counts as standing by it. */
    private static final int NEAR_CASTLE_BLOCKS = 20;

    /** How far out, in chunks, a village looks for an old place to talk about. */
    private static final int RUINS_SEARCH_CHUNKS = 32;

    /**
     * The same reach as the search is asked for it. The locate search counts in the structure
     * set's placement cells, 48 chunks each for the ancient castle, not in chunks: asked for 32 it
     * looked some fifteen hundred chunks out. One cell covers the reach; anything it finds
     * further off than the reach is not near enough to talk about.
     */
    private static final int RUINS_SEARCH_CELLS = 1;

    /** A castle question comes up sometimes. One that is always on the list is a menu, not a remark. */
    private static final double QUESTION_CHANCE = 0.35;

    private static final long CASTLE_CACHE_TICKS = 24000L;

    private record Sighting(long tick, BoundingBox box) {}

    private static final Map<UUID, Sighting> CASTLES = new ConcurrentHashMap<>();
    private static final Map<Long, Optional<BlockPos>> RUINS = new ConcurrentHashMap<>();

    public static void init() {
        if (initialized) return;
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("village-quests-justfatlard")) {
            VillageCastles.LOGGER.info("Village Quests not found, skipping quest integration");
            return;
        }

        try {
            Class<?> questRegistry = Class.forName("justfatlard.village_quests.api.QuestRegistry");
            questGeneratorClass = Class.forName("justfatlard.village_quests.api.QuestRegistry$QuestGenerator");
            registerProfessionQuest = questRegistry.getMethod("registerProfessionQuest", String.class, questGeneratorClass);
            registerUniversalQuest = questRegistry.getMethod("registerUniversalQuest", questGeneratorClass);

            Class<?> fetchQuestClass = Class.forName("justfatlard.village_quests.quest.FetchItemQuest");
            fetchQuest = fetchQuestClass.getConstructor(String.class, UUID.class, Item.class, int.class, int.class);
            try {
                withAsk = fetchQuestClass.getMethod("withAsk", String.class);
            } catch (NoSuchMethodException older) {
                VillageCastles.LOGGER.info("Village Quests without verbatim asks; castle errands use the generic wording");
            }

            Class<?> dialogueRegistry = Class.forName("justfatlard.village_quests.api.DialogueRegistry");
            dialogueProviderClass = Class.forName("justfatlard.village_quests.api.DialogueRegistry$DialogueProvider");
            richHandlerClass = Class.forName("justfatlard.village_quests.api.DialogueRegistry$RichDialogueHandler");
            Class<?> replyClass = Class.forName("justfatlard.village_quests.api.DialogueRegistry$Reply");
            registerProfessionDialogue = dialogueRegistry.getMethod("registerProfessionDialogue", String.class, dialogueProviderClass);
            registerRichHandler = dialogueRegistry.getMethod("registerRichDialogueHandler", String.class, richHandlerClass);
            replyOf = replyClass.getMethod("of", String.class);
            replyOption = replyClass.getMethod("option", String.class, richHandlerClass);
            replyWalkAway = replyClass.getMethod("walkAway", String.class);
            dialogueOption = Class.forName("justfatlard.village_quests.api.DialogueRegistry$DialogueOption")
                .getConstructor(String.class, Component.class, int.class, int.class);

            registerQuests();
            registerDialogue();
            VillageCastles.LOGGER.info("Registered castle errands and questions with Village Quests");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            VillageCastles.LOGGER.warn("Village Quests API not as expected (version mismatch?): {}", e.getMessage());
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to register with Village Quests: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Where things are
    // ---------------------------------------------------------------

    /** The castle in this villager's village, or null. Asked once a day per villager. */
    private static BoundingBox castleOf(Villager villager) {
        if (!(villager.level() instanceof ServerLevel world)) return null;

        long now = world.getGameTime();
        Sighting seen = CASTLES.get(villager.getUUID());
        if (seen != null && now - seen.tick() < CASTLE_CACHE_TICKS) return seen.box();

        BoundingBox box = CastleCensus.nearestCastleBox(world, villager.blockPosition());
        CASTLES.put(villager.getUUID(), new Sighting(now, box));
        return box;
    }

    private static boolean inCastleVillage(Villager villager) {
        return castleOf(villager) != null;
    }

    private static boolean nearCastle(Villager villager) {
        BoundingBox box = castleOf(villager);
        return box != null && box.inflatedBy(NEAR_CASTLE_BLOCKS).isInside(villager.blockPosition());
    }

    /**
     * The nearest ancient castle to this village, or null. The search is the
     * one behind the locate command, so it runs once per region and is only
     * asked after every cheaper gate has passed.
     */
    private static BlockPos ruinsNear(Villager villager) {
        if (!(villager.level() instanceof ServerLevel world)) return null;

        BlockPos pos = villager.blockPosition();
        long region = ((long) (pos.getX() >> 7) << 32) ^ ((pos.getZ() >> 7) & 0xffffffffL);
        return RUINS.computeIfAbsent(region, key -> {
            Optional<Holder.Reference<Structure>> holder = world.registryAccess()
                .lookup(Registries.STRUCTURE)
                .flatMap(registry -> registry.get(ANCIENT_CASTLE));
            if (holder.isEmpty()) return Optional.empty();

            BlockPos found = world.findNearestMapStructure(HolderSet.direct(holder.get()), pos, RUINS_SEARCH_CELLS, false);
            int reach = RUINS_SEARCH_CHUNKS * 16;
            if (found != null && found.distSqr(new BlockPos(pos.getX(), found.getY(), pos.getZ())) > (double) reach * reach) found = null;
            return Optional.ofNullable(found);
        }).orElse(null);
    }

    private static String directionTo(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? "east" : "west";
        return dz > 0 ? "south" : "north";
    }

    private static String biomeOf(Villager villager) {
        if (!(villager.level() instanceof ServerLevel world)) return "plains";
        String path = world.getBiome(villager.blockPosition()).unwrapKey()
            .map(k -> k.identifier().getPath()).orElse("");
        if (path.contains("desert")) return "desert";
        if (path.contains("snowy") || path.contains("ice") || path.contains("frozen")) return "snowy";
        if (path.contains("taiga")) return "taiga";
        if (path.contains("savanna")) return "savanna";
        return "plains";
    }

    // ---------------------------------------------------------------
    // Errands
    // ---------------------------------------------------------------

    @FunctionalInterface
    private interface Errand {
        Object offer(Villager villager, String villagerName, int reputation, Random random) throws Exception;
    }

    private static void registerQuests() throws Exception {
        // The wall, in whatever the wall is made of here.
        profession("mason", 0.10, 4, (villager, name, reputation, random) -> {
            if (!inCastleVillage(villager)) return null;
            return switch (biomeOf(villager)) {
                case "desert" -> fetch(name, villager, Items.SANDSTONE, 8, 4,
                    "Wind's taken the top course off the wall by the gate. Sandstone. Eight, and I'll dress it myself.");
                case "taiga" -> fetch(name, villager, Items.SPRUCE_PLANKS, 8, 4,
                    "The wall by the gate split where the frost got into it. Spruce planks, eight, to sister the beams.");
                case "snowy" -> fetch(name, villager, Items.COBBLESTONE, 8, 4,
                    "Ice heaved the wall by the gate again. Cobblestone. Eight, before it goes further.");
                case "savanna" -> fetch(name, villager, Items.MUD_BRICKS, 8, 4,
                    "The wall by the gate cracked in the heat. Mud bricks. Eight, and I'll pack them wet.");
                default -> fetch(name, villager, Items.STONE_BRICKS, 8, 4,
                    "The wall's down a course by the gate. Stone bricks. Eight would see it right.");
            };
        });

        profession("fletcher", 0.08, 4, (villager, name, reputation, random) -> {
            if (!inCastleVillage(villager)) return null;
            return fetch(name, villager, Items.ARROW, 12, 4,
                "Nobody's stood on that wall with a bow in a year, and the night somebody has to, there won't be an arrow up there. A dozen.");
        });

        profession("farmer", 0.08, 4, (villager, name, reputation, random) -> {
            if (!inCastleVillage(villager)) return null;
            return fetch(name, villager, Items.BREAD, 8, 4,
                "More of us sleep in the keep now than in my house, and they all come down hungry. Bread. Eight loaves.");
        });

        profession("librarian", 0.08, 5, (villager, name, reputation, random) -> {
            if (!inCastleVillage(villager)) return null;
            return fetch(name, villager, Items.BOOK, 1, 5,
                "Whoever built the keep is going to be nobody's grandfather soon. I want it written down while somebody still remembers. A book. I've the ink.");
        });

        profession("toolsmith", 0.08, 4, (villager, name, reputation, random) -> {
            if (!inCastleVillage(villager)) return null;
            return fetch(name, villager, Items.IRON_INGOT, 3, 4,
                "The gate's hinges are rust and hope. Three ingots and I'll forge new ones.");
        });

        // The old place. Rare, late, and only where there is one.
        profession("cleric", 0.06, 20, (villager, name, reputation, random) -> {
            if (!inCastleVillage(villager)) return null;
            BlockPos ruins = ruinsNear(villager);
            if (ruins == null) return null;
            String direction = directionTo(villager.blockPosition(), ruins);
            return fetch(name, villager, Items.BONE, 1, 6,
                "There's a place out to the " + direction + " the old ones built. Something still walks in it at night, and it isn't ours. "
                    + "If you go, bring me a bone from it. I want to know what they were.");
        });

        // Anyone in a castle village.
        universal(0.06, 3, (villager, name, reputation, random) -> {
            if (!inCastleVillage(villager)) return null;
            return fetch(name, villager, Items.TORCH, 8, 3,
                "It's black inside the keep after dark, and dark is how things get in. Torches. Eight.");
        });
    }

    private static Object fetch(String villagerName, Villager villager, Item item, int count, int reputationShift, String ask) throws Exception {
        Object quest = fetchQuest.newInstance(villagerName, villager.getUUID(), item, count, reputationShift);
        if (withAsk != null) withAsk.invoke(quest, ask);
        return quest;
    }

    private static void profession(String profession, double chance, int minReputation, Errand errand) throws Exception {
        registerProfessionQuest.invoke(null, profession, generator(chance, minReputation, errand));
    }

    private static void universal(double chance, int minReputation, Errand errand) throws Exception {
        registerUniversalQuest.invoke(null, generator(chance, minReputation, errand));
    }

    private static Object generator(double chance, int minReputation, Errand errand) {
        return Proxy.newProxyInstance(questGeneratorClass.getClassLoader(), new Class<?>[]{questGeneratorClass},
            (proxy, method, args) -> {
                if (!"generate".equals(method.getName())) return null;
                int reputation = (Integer) args[2];
                Random random = (Random) args[3];
                if (reputation < minReputation || random.nextDouble() >= chance) return null;
                return errand.offer((Villager) args[0], (String) args[1], reputation, random);
            });
    }

    // ---------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------

    private enum Where { CASTLE_VILLAGE, NEAR_CASTLE, RUINS_NEAR }

    /** A villager's line, the player's exit from it, and the moves that keep it going. */
    private record Node(String text, String walkAway, List<Branch> branches) {}

    private record Branch(String label, Node node) {}

    private record Topic(String id, int minReputation, Where where, String question, Node tree) {}

    private static Node close(String text, String walkAway) {
        return new Node(text, walkAway, List.of());
    }

    private static Node node(String text, String walkAway, Branch... branches) {
        return new Node(text, walkAway, List.of(branches));
    }

    private static Branch then(String label, Node node) {
        return new Branch(label, node);
    }

    private static void registerDialogue() throws Exception {
        topics("mason", List.of(
            new Topic("vc_walls", 0, Where.NEAR_CASTLE, "How's the wall holding?",
                node("Holding. That's not the same as sound. There's a course by the gate I don't like, and I check it every morning and hope loudly.",
                    "Sounds like you've got it in hand.",
                    then("What's wrong with it?",
                        close("Water. It finds the one weak stone in a hundred and works at it all winter. Whoever built the old places knew a trick for that. We don't.",
                            "Keep checking.")))),
            new Topic("vc_mason_ruins", 20, Where.RUINS_NEAR, "The old place out there. Same stone as ours?",
                node("Same corners. Same string course under every floor. Ours is a drawing of theirs, done from memory, by people who never went inside.",
                    "A good drawing, then.",
                    then("Why not go inside?",
                        close("Because the ones who built it are gone and the ones who guard it aren't. I lay stone. I don't argue with what's under it.",
                            "Nor would I."))))));

        topics("fletcher", List.of(
            new Topic("vc_wall_walk", 0, Where.NEAR_CASTLE, "What can you see from up there?",
                node("Everything, on a clear day. The fields, the road, the golem doing its rounds. Mostly nothing happens up there. I've come to like nothing.",
                    "Enjoy the view.",
                    then("And on a bad night?",
                        close("The one raid we had, I saw them coming across the east field before the bell went. That's what the wall's for. Not the stone. The seeing.",
                            "Here's to nothing."))))));

        topics("farmer", List.of(
            new Topic("vc_feeding", 0, Where.CASTLE_VILLAGE, "Is the keep a lot of extra mouths?",
                node("It is. Nobody asked me before they moved in, either. But the golem walks the wall and the door's iron, and I've stopped sleeping with one ear open. So I plant more.",
                    "Plant on, then.",
                    then("Do they help with the fields?",
                        close("The ones who sleep up there? They're us. Same people, thicker walls. They help the way anyone does, which is when asked twice.",
                            "Fair enough."))))));

        topics("armorer", List.of(
            new Topic("vc_golem", 0, Where.CASTLE_VILLAGE, "Does the golem ever go inside the keep?",
                node("Never. It walks the wall and stands at the gate and never once goes through it. I've stopped taking that personally.",
                    "It knows its post.",
                    then("Does it know what the keep is for?",
                        close("It knows the gate. Stand at the gate long enough and it comes and stands with you. That's as much as I know about it.",
                            "Good company, then."))))));

        topics("weaponsmith", List.of(
            new Topic("vc_raid", 10, Where.CASTLE_VILLAGE, "Has the wall ever been tested?",
                node("Once. Pillagers, from the east, at dusk. The bell went, we got inside, the golem did the rest. The wall did nothing but stand there, which is all I ever asked of it.",
                    "Good wall.",
                    then("Were you frightened?",
                        close("I was up on the wall with a sword I'd made myself, thinking about every flaw in it. That's the honest answer.",
                            "Good sword, then."))))));

        topics("librarian", List.of(
            new Topic("vc_history", 10, Where.CASTLE_VILLAGE, "Who built the keep?",
                node("We did. That's the short answer and the one on the sign. The long answer is we built it looking at theirs. The old ones out in the wild. Same corners, same string course under each floor. Ours is smaller and has beds in it.",
                    "Makes sense.",
                    then("Why copy theirs?",
                        close("Because theirs is still standing and whoever built it isn't. That seemed like the part to copy.",
                            "Can't argue with that.")))),
            new Topic("vc_lib_ruins", 20, Where.RUINS_NEAR, "The old place out there. What is it?",
                node("Older than anything here. Deepslate all the way up, slate on what's left of the roofs, and nobody who built it left a name on it. I've been twice, in daylight, and not past the gate.",
                    "Best from a distance.",
                    then("What's past the gate?",
                        close("Bones that walk. And under them, whatever they were left to guard. I'd like the names off the lintels, if you ever go. I'll do the rest from here.",
                            "Names off the lintels."))))));

        topics("cleric", List.of(
            new Topic("vc_ruins_dead", 20, Where.RUINS_NEAR, "The ones out at the old place. Do they bother you?",
                node("They're not dead. That's what bothers me. Something keeps them walking, down in the dark under it, and it's been keeping them since before this village had a well.",
                    "I'll leave them be.",
                    then("Could it be stopped?",
                        close("Break what keeps them, and they stop. I know that much and no more. If you find it, don't bring it back here.",
                            "I won't."))))));
    }

    /**
     * One profession's questions. Of the ones that fit where the player is
     * standing, at most one is offered, and only some of the time, so a castle
     * comes up in conversation the way a building does rather than as a menu of
     * things to ask about it.
     */
    private static void topics(String profession, List<Topic> topics) throws Exception {
        for (Topic topic : topics) {
            registerRichHandler.invoke(null, topic.id(), handler((villager, player, id) -> reply(topic.tree())));
        }

        Object provider = Proxy.newProxyInstance(dialogueProviderClass.getClassLoader(), new Class<?>[]{dialogueProviderClass},
            (proxy, method, args) -> {
                if (!"getOptions".equals(method.getName())) return new ArrayList<>();
                Villager villager = (Villager) args[0];
                int reputation = (Integer) args[2];
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                List<Object> offered = new ArrayList<>();
                if (rng.nextDouble() >= QUESTION_CHANCE || !inCastleVillage(villager)) return offered;

                List<Topic> fitting = new ArrayList<>();
                for (Topic topic : topics) {
                    if (reputation < topic.minReputation()) continue;
                    boolean fits = switch (topic.where()) {
                        case CASTLE_VILLAGE -> true;
                        case NEAR_CASTLE -> nearCastle(villager);
                        case RUINS_NEAR -> ruinsNear(villager) != null;
                    };
                    if (fits) fitting.add(topic);
                }
                if (fitting.isEmpty()) return offered;

                Topic topic = fitting.get(rng.nextInt(fitting.size()));
                offered.add(dialogueOption.newInstance(topic.id(), Component.literal(topic.question()), topic.minReputation(), Integer.MAX_VALUE));
                return offered;
            });
        registerProfessionDialogue.invoke(null, profession, provider);
    }

    @FunctionalInterface
    private interface Answer {
        Object reply(Villager villager, ServerPlayer player, String optionId) throws Exception;
    }

    private static Object handler(Answer answer) {
        return Proxy.newProxyInstance(richHandlerClass.getClassLoader(), new Class<?>[]{richHandlerClass},
            (proxy, method, args) -> {
                if (!"handle".equals(method.getName())) return null;
                return answer.reply((Villager) args[0], (ServerPlayer) args[1], (String) args[2]);
            });
    }

    /** A tree node as a Village Quests reply: the line, its exit, and a handler per follow-up. */
    private static Object reply(Node node) throws Exception {
        Object reply = replyOf.invoke(null, node.text());
        if (node.walkAway() != null) replyWalkAway.invoke(reply, node.walkAway());
        for (Branch branch : node.branches()) {
            replyOption.invoke(reply, branch.label(), handler((villager, player, id) -> reply(branch.node())));
        }
        return reply;
    }
}
