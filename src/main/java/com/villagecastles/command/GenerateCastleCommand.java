package com.villagecastles.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.villagecastles.VillageCastles;
import com.villagecastles.generator.BiomePalette;
import com.villagecastles.generator.CastleGenerator;
import com.villagecastles.generator.VillageWallGenerator;
import net.minecraft.command.CommandSource;
import net.minecraft.util.math.Direction;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;

/**
 * Command for generating castles in-game.
 *
 * Usage:
 *   /villagecastles generate <biome> [size]
 *   /villagecastles preview <biome>
 *
 * Examples:
 *   /villagecastles generate plains large
 *   /villagecastles generate desert medium
 *   /villagecastles generate snowy
 */
public class GenerateCastleCommand {

    private static final SuggestionProvider<ServerCommandSource> BIOME_SUGGESTIONS =
        (context, builder) -> CommandSource.suggestMatching(
            Arrays.stream(BiomePalette.values()).map(p -> p.id),
            builder
        );

    private static final SuggestionProvider<ServerCommandSource> SIZE_SUGGESTIONS =
        (context, builder) -> CommandSource.suggestMatching(
            Arrays.stream(CastleGenerator.CastleSize.values()).map(s -> s.name().toLowerCase()),
            builder
        );

    private static final SuggestionProvider<ServerCommandSource> WALL_SEGMENT_SUGGESTIONS =
        (context, builder) -> CommandSource.suggestMatching(
            Arrays.stream(VillageWallGenerator.SegmentType.values()).map(s -> s.name().toLowerCase()),
            builder
        );

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("villagecastles")
                .requires(ServerCommandSource::isExecutedByPlayer) // Player only
                .then(CommandManager.literal("generate")
                    .then(CommandManager.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeGenerate(ctx, "large")) // Default to large
                        .then(CommandManager.argument("size", StringArgumentType.word())
                            .suggests(SIZE_SUGGESTIONS)
                            .executes(ctx -> executeGenerate(ctx,
                                StringArgumentType.getString(ctx, "size")))
                        )
                    )
                )
                .then(CommandManager.literal("wall")
                    .then(CommandManager.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeWall(ctx, "straight")) // Default to straight
                        .then(CommandManager.argument("segment", StringArgumentType.word())
                            .suggests(WALL_SEGMENT_SUGGESTIONS)
                            .executes(ctx -> executeWall(ctx,
                                StringArgumentType.getString(ctx, "segment")))
                        )
                    )
                )
                .then(CommandManager.literal("walls")
                    .then(CommandManager.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(GenerateCastleCommand::executeWallShowcase)
                    )
                )
                .then(CommandManager.literal("list")
                    .executes(GenerateCastleCommand::executeList)
                )
                .then(CommandManager.literal("help")
                    .executes(GenerateCastleCommand::executeHelp)
                )
        );

        VillageCastles.LOGGER.info("Registered /villagecastles command");
    }

    private static int executeGenerate(CommandContext<ServerCommandSource> ctx, String sizeStr) {
        ServerCommandSource source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        // Parse biome
        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendError(Text.literal("Unknown biome: " + biomeStr +
                ". Valid options: plains, desert, savanna, taiga, snowy"));
            return 0;
        }

        // Parse size
        CastleGenerator.CastleSize size;
        try {
            size = CastleGenerator.CastleSize.valueOf(sizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Unknown size: " + sizeStr +
                ". Valid options: small, medium, large"));
            return 0;
        }

        // Get player position
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        ServerWorld world = source.getWorld();

        // Generate slightly in front of player, at ground level
        BlockPos generatePos = playerPos.north(size.diameter / 2 + 10);

        source.sendFeedback(() -> Text.literal("Generating " + size.name().toLowerCase() +
            " " + palette.displayName + " at " + generatePos.toShortString() + "..."), true);

        try {
            // Create generator with world seed + player position for unique castles
            long seed = world.getSeed() + generatePos.hashCode();
            CastleGenerator generator = new CastleGenerator(palette, seed, size);

            // Generate!
            CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

            source.sendFeedback(() -> Text.literal("Castle generated! Size: " +
                bounds.getWidth() + "x" + bounds.getHeight() + "x" + bounds.getDepth()), false);

            source.sendFeedback(() -> Text.literal("Use Structure Blocks to save it as NBT."), false);

            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate castle", e);
            source.sendError(Text.literal("Failed to generate castle: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeWall(CommandContext<ServerCommandSource> ctx, String segmentStr) {
        ServerCommandSource source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendError(Text.literal("Unknown biome: " + biomeStr));
            return 0;
        }

        VillageWallGenerator.SegmentType segmentType;
        try {
            segmentType = VillageWallGenerator.SegmentType.valueOf(segmentStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.literal("Unknown segment type: " + segmentStr +
                ". Valid: straight, corner, gate, tower, terminator"));
            return 0;
        }

        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        ServerWorld world = source.getWorld();
        BlockPos generatePos = playerPos.north(5);

        try {
            long seed = world.getSeed() + generatePos.hashCode();
            VillageWallGenerator generator = new VillageWallGenerator(palette, new java.util.Random(seed));
            generator.generate(world, generatePos, Direction.NORTH, segmentType);

            source.sendFeedback(() -> Text.literal("Generated " + segmentType.name().toLowerCase() +
                " wall segment (" + palette.id + ")"), false);
            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate wall", e);
            source.sendError(Text.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeWallShowcase(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendError(Text.literal("Unknown biome: " + biomeStr));
            return 0;
        }

        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        ServerWorld world = source.getWorld();

        try {
            long seed = world.getSeed() + playerPos.hashCode();
            VillageWallGenerator generator = new VillageWallGenerator(palette, new java.util.Random(seed));

            // Generate all segment types in a row
            int spacing = 12;
            int offset = 0;
            for (VillageWallGenerator.SegmentType type : VillageWallGenerator.SegmentType.values()) {
                BlockPos pos = playerPos.north(10).east(offset);
                generator.generate(world, pos, Direction.NORTH, type);
                offset += spacing;
            }

            source.sendFeedback(() -> Text.literal("Generated wall showcase for " + palette.id +
                " (5 segments)"), false);
            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate wall showcase", e);
            source.sendError(Text.literal("Failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        source.sendFeedback(() -> Text.literal("=== Available Castle Biomes ==="), false);
        for (BiomePalette palette : BiomePalette.values()) {
            source.sendFeedback(() -> Text.literal("  " + palette.id + " - " + palette.displayName), false);
        }

        source.sendFeedback(() -> Text.literal("=== Castle Sizes ==="), false);
        for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
            source.sendFeedback(() -> Text.literal("  " + size.name().toLowerCase() +
                " (~" + size.diameter + " blocks)"), false);
        }

        source.sendFeedback(() -> Text.literal("=== Wall Segment Types ==="), false);
        for (VillageWallGenerator.SegmentType type : VillageWallGenerator.SegmentType.values()) {
            source.sendFeedback(() -> Text.literal("  " + type.name().toLowerCase()), false);
        }

        return 1;
    }

    private static int executeHelp(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        source.sendFeedback(() -> Text.literal("=== Village Castles Commands ==="), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.literal("/villagecastles generate <biome> [size]"), false);
        source.sendFeedback(() -> Text.literal("  Generate a full castle"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.literal("/villagecastles wall <biome> [segment]"), false);
        source.sendFeedback(() -> Text.literal("  Generate a single wall segment"), false);
        source.sendFeedback(() -> Text.literal("  segment: straight, corner, gate, tower, terminator"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.literal("/villagecastles walls <biome>"), false);
        source.sendFeedback(() -> Text.literal("  Generate all wall segment types (showcase)"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.literal("/villagecastles list"), false);
        source.sendFeedback(() -> Text.literal("  List all biomes, sizes, and segment types"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.literal("Workflow:"), false);
        source.sendFeedback(() -> Text.literal("  1. Generate structures in creative mode"), false);
        source.sendFeedback(() -> Text.literal("  2. Polish/customize as desired"), false);
        source.sendFeedback(() -> Text.literal("  3. Use Structure Blocks to save as NBT"), false);
        source.sendFeedback(() -> Text.literal("  4. Copy NBT to mod's data folder"), false);

        return 1;
    }
}
