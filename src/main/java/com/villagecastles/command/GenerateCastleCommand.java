package com.villagecastles.command;

import com.mojang.brigadier.CommandDispatcher;
import com.villagecastles.VillageCastles;
import com.villagecastles.generator.ancient.Condition;
import com.villagecastles.generator.ancient.Landform;
import com.villagecastles.generator.villager.VillagerCastleDesigner;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The {@code /village-castles} root. Every castle is generated, none is pasted:
 *
 * <pre>
 *   /village-castles ancient &lt;landform&gt; [condition] [seed]  - raise an ancient castle here
 *   /village-castles ancientscan                            - what landform the classifier sees here
 *   /village-castles villager &lt;size&gt; [biome] [seed]         - raise a villager castle here
 *   /village-castles list                                   - the vocabulary
 * </pre>
 */
public class GenerateCastleCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("village-castles")
                .requires(CommandSourceStack::isPlayer)
                .then(AncientCastleCommands.ancient())
                .then(AncientCastleCommands.ancientScan())
                .then(AncientCastleCommands.villager())
                .then(Commands.literal("list").executes(ctx -> {
                    String landforms = Arrays.stream(Landform.values())
                        .map(Landform::id).collect(Collectors.joining(", "));
                    String conditions = Arrays.stream(Condition.values())
                        .filter(c -> c != Condition.PRISTINE)
                        .map(Condition::id).collect(Collectors.joining(", "));
                    String sizes = Arrays.stream(VillagerCastleDesigner.Size.values())
                        .map(VillagerCastleDesigner.Size::id).collect(Collectors.joining(", "));
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "Ancient landforms: " + landforms
                            + "\nAncient conditions: " + conditions
                            + "\nVillager sizes: " + sizes
                            + "\nVillager biomes: plains, desert, savanna, taiga, snowy"), false);
                    return 1;
                }))
                .then(Commands.literal("help").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "/village-castles ancient <landform> [condition] [seed]"
                            + "\n/village-castles ancientscan"
                            + "\n/village-castles villager <size> [biome] [seed]"
                            + "\n/village-castles list"), false);
                    return 1;
                }))
        );

        VillageCastles.LOGGER.info("Registered /village-castles command");
    }
}
