package com.villagecastles.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.villagecastles.VillageCastles;
import com.villagecastles.generator.BiomePalette;
import com.villagecastles.generator.CastleGenerator;
import com.villagecastles.generator.RuinsGenerator;
import com.villagecastles.generator.VillageWallGenerator;
import com.villagecastles.util.NbtExporter;
import com.villagecastles.util.StructureHelper;
import net.minecraft.command.CommandSource;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionCheck;
import net.minecraft.command.permission.PermissionLevel;
import net.minecraft.util.math.Direction;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Path;
import java.util.Arrays;

/**
 * Command for generating castles in-game.
 *
 * Usage:
 *   /villagecastles generate <biome> [size]
 *   /villagecastles wall <biome> [segment]
 *   /villagecastles walls <biome>
 *   /villagecastles status
 *   /villagecastles list
 *   /villagecastles help
 */
public class GenerateCastleCommand {

    private static final java.util.function.Predicate<ServerCommandSource> REQUIRES_OP =
        CommandManager.requirePermissionLevel(
            new PermissionCheck.Require(new Permission.Level(PermissionLevel.GAMEMASTERS)));

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

    private static final SuggestionProvider<ServerCommandSource> RUINS_VARIANT_SUGGESTIONS =
        (context, builder) -> CommandSource.suggestMatching(
            java.util.stream.Stream.of("1", "2"),
            builder
        );

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("villagecastles")
                .requires(ServerCommandSource::isExecutedByPlayer) // Player only
                .then(CommandManager.literal("generate")
                    .requires(REQUIRES_OP)
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
                    .requires(REQUIRES_OP)
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
                    .requires(REQUIRES_OP)
                    .then(CommandManager.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(GenerateCastleCommand::executeWallShowcase)
                    )
                )
                .then(CommandManager.literal("ruins")
                    .requires(REQUIRES_OP)
                    .then(CommandManager.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeRuins(ctx, "1"))
                        .then(CommandManager.argument("variant", StringArgumentType.word())
                            .suggests(RUINS_VARIANT_SUGGESTIONS)
                            .executes(ctx -> executeRuins(ctx,
                                StringArgumentType.getString(ctx, "variant")))
                        )
                    )
                )
                .then(CommandManager.literal("exportruins")
                    .requires(REQUIRES_OP)
                    .executes(GenerateCastleCommand::executeExportRuins)
                )
                .then(CommandManager.literal("exportall")
                    .requires(REQUIRES_OP)
                    .executes(GenerateCastleCommand::executeExportAll)
                )
                .then(CommandManager.literal("showcase")
                    .requires(REQUIRES_OP)
                    .executes(GenerateCastleCommand::executeShowcase)
                )
                .then(CommandManager.literal("export")
                    .requires(REQUIRES_OP)
                    .then(CommandManager.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeExport(ctx, "large"))
                        .then(CommandManager.argument("size", StringArgumentType.word())
                            .suggests(SIZE_SUGGESTIONS)
                            .executes(ctx -> executeExport(ctx,
                                StringArgumentType.getString(ctx, "size")))
                        )
                    )
                )
                .then(CommandManager.literal("status")
                    .executes(GenerateCastleCommand::executeStatus)
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
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        // Parse size
        CastleGenerator.CastleSize size;
        try {
            size = CastleGenerator.CastleSize.valueOf(sizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_size", sizeStr));
            return 0;
        }

        // Get player position
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        ServerWorld world = source.getWorld();

        // Validate Y range -- castles need headroom and foundation
        if (playerPos.getY() < -50 || playerPos.getY() > 300) {
            source.sendError(Text.translatable("commands.villagecastles.error.position_extreme", playerPos.getY()));
            return 0;
        }

        // Generate in front of player based on their facing direction
        Direction facing;
        try {
            facing = source.getPlayerOrThrow().getHorizontalFacing();
        } catch (CommandSyntaxException e) {
            source.sendError(Text.literal("This command must be run by a player"));
            return 0;
        }
        BlockPos generatePos = playerPos.offset(facing, size.diameter / 2 + 10);

        source.sendFeedback(() -> Text.translatable("commands.villagecastles.generate.starting",
            size.name().toLowerCase(), palette.displayName, generatePos.toShortString()), true);

        try {
            // Force-load chunks in the generation area to prevent partial structures
            final CastleGenerator.CastleBounds[] result = new CastleGenerator.CastleBounds[1];
            StructureHelper.withForcedChunks(world, generatePos, size.diameter / 2 + 5, () -> {
                long seed = world.getSeed() + generatePos.hashCode();
                CastleGenerator generator = new CastleGenerator(palette, seed, size);
                result[0] = generator.generate(world, generatePos);
            });

            CastleGenerator.CastleBounds bounds = result[0];
            source.sendFeedback(() -> Text.translatable("commands.villagecastles.generate.success",
                bounds.getWidth(), bounds.getHeight(), bounds.getDepth()), false);

            source.sendFeedback(() -> Text.translatable("commands.villagecastles.generate.save_hint",
                palette.id, size.name().toLowerCase()), false);

            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate castle", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendError(Text.translatable("commands.villagecastles.generate.failed", msg));
            return 0;
        }
    }

    private static int executeWall(CommandContext<ServerCommandSource> ctx, String segmentStr) {
        ServerCommandSource source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        VillageWallGenerator.SegmentType segmentType;
        try {
            segmentType = VillageWallGenerator.SegmentType.valueOf(segmentStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_segment", segmentStr));
            return 0;
        }

        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        ServerWorld world = source.getWorld();
        BlockPos generatePos = playerPos.north(5);

        try {
            long seed = world.getSeed() + generatePos.hashCode();
            VillageWallGenerator generator = new VillageWallGenerator(palette, new java.util.Random(seed));
            generator.generate(world, generatePos, Direction.NORTH, segmentType);

            source.sendFeedback(() -> Text.translatable("commands.villagecastles.wall.success",
                segmentType.name().toLowerCase(), palette.id), false);

            source.sendFeedback(() -> Text.translatable("commands.villagecastles.wall.save_hint",
                palette.id, segmentType.name().toLowerCase()), false);

            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate wall", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendError(Text.translatable("commands.villagecastles.wall.failed", msg));
            return 0;
        }
    }

    private static int executeWallShowcase(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
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

            source.sendFeedback(() -> Text.translatable("commands.villagecastles.walls.success", palette.id), false);
            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate wall showcase", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendError(Text.translatable("commands.villagecastles.walls.failed", msg));
            return 0;
        }
    }

    /**
     * Generate a single castle and export it as NBT.
     */
    private static int executeExport(CommandContext<ServerCommandSource> ctx, String sizeStr) {
        ServerCommandSource source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        CastleGenerator.CastleSize size;
        try {
            size = CastleGenerator.CastleSize.valueOf(sizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_size", sizeStr));
            return 0;
        }

        ServerWorld world = source.getWorld();
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        Direction facing;
        try {
            facing = source.getPlayerOrThrow().getHorizontalFacing();
        } catch (CommandSyntaxException e) {
            source.sendError(Text.literal("This command must be run by a player"));
            return 0;
        }
        BlockPos generatePos = playerPos.offset(facing, size.diameter / 2 + 10);

        try {
            long seed = world.getSeed() + generatePos.hashCode();
            CastleGenerator generator = new CastleGenerator(palette, seed, size);
            CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

            Path runDir = source.getServer().getRunDirectory();
            String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
            Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

            boolean exported = NbtExporter.exportRegion(world, bounds.min, bounds.max, outputPath);

            if (exported) {
                source.sendFeedback(() -> Text.literal("\u00a7aExported " + structurePath + ".nbt"), true);
            } else {
                source.sendError(Text.literal("Failed to export " + structurePath));
            }

            return exported ? 1 : 0;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to export castle", e);
            source.sendError(Text.literal("Export failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Generate and export ALL structures for all biomes — castles (3 sizes) + wall segments (5 types).
     * This is the mass-production command for populating the mod's NBT files.
     */
    /**
     * Generate all 15 castle variants in a grid for visual inspection.
     * 5 columns (biomes) × 3 rows (sizes), spaced 100 blocks apart.
     */
    private static int executeShowcase(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());

        source.sendFeedback(() -> Text.literal("\u00a7eGenerating all 15 castle variants..."), true);

        int xOffset = 0;
        for (BiomePalette palette : BiomePalette.values()) {
            int zOffset = 0;
            for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
                BlockPos genPos = playerPos.add(xOffset, 0, zOffset);

                try {
                    StructureHelper.withForcedChunks(world, genPos, size.diameter / 2 + 10, () -> {
                        long seed = world.getSeed() + genPos.hashCode();
                        CastleGenerator generator = new CastleGenerator(palette, seed, size);
                        generator.generate(world, genPos);
                    });
                    source.sendFeedback(() -> Text.literal("\u00a7a  \u2714 " + palette.displayName + " " + size.name().toLowerCase()), false);
                } catch (Exception e) {
                    source.sendFeedback(() -> Text.literal("\u00a7c  \u2718 " + palette.displayName + " " + size.name().toLowerCase() + ": " + e.getMessage()), false);
                }

                zOffset += 100; // Space rows 100 blocks apart
            }
            xOffset += 100; // Space columns 100 blocks apart
        }

        source.sendFeedback(() -> Text.literal("\u00a7aShowcase complete! 5 biomes \u00d7 3 sizes in a grid."), true);
        return 1;
    }

    private static int executeExportAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        Path runDir = source.getServer().getRunDirectory();

        source.sendFeedback(() -> Text.literal("\u00a7eGenerating and exporting all structures..."), true);

        int exported = 0;
        int failed = 0;
        int xOffset = 0;

        // Generate castles for each biome and size
        for (BiomePalette palette : BiomePalette.values()) {
            for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
                // Space each castle apart to avoid overlap
                BlockPos generatePos = playerPos.add(xOffset, 0, 0);
                xOffset += size.diameter + 20;

                try {
                    long seed = world.getSeed() + generatePos.hashCode();
                    CastleGenerator generator = new CastleGenerator(palette, seed, size);
                    CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                    String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
                    Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                    if (NbtExporter.exportRegion(world, bounds.min, bounds.max, outputPath)) {
                        exported++;
                        final String path = structurePath;
                        source.sendFeedback(() -> Text.literal("  \u00a7a\u2713 " + path), false);
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    VillageCastles.LOGGER.error("Failed to export {} {}", palette.id, size, e);
                    failed++;
                }
            }
        }

        // Generate wall segments for each biome
        for (BiomePalette palette : BiomePalette.values()) {
            for (VillageWallGenerator.SegmentType segmentType : VillageWallGenerator.SegmentType.values()) {
                BlockPos generatePos = playerPos.add(xOffset, 0, 0);
                xOffset += 20;

                try {
                    long seed = world.getSeed() + generatePos.hashCode();
                    VillageWallGenerator wallGen = new VillageWallGenerator(palette, new java.util.Random(seed));

                    // Generate the segment
                    BlockPos origin = generatePos;
                    wallGen.generate(world, origin, Direction.NORTH, segmentType);

                    // Calculate tight bounds for wall segments
                    int halfWidth = VillageWallGenerator.getSegmentLength() / 2 + 1;
                    int depth = 3;
                    BlockPos wallMin = origin.add(-halfWidth, -2, -depth);
                    BlockPos wallMax = origin.add(halfWidth, 12, depth);

                    String structurePath = "village_walls/" + palette.id + "/wall_" + segmentType.name().toLowerCase();
                    Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                    if (NbtExporter.exportRegion(world, wallMin, wallMax, outputPath)) {
                        exported++;
                        final String path = structurePath;
                        source.sendFeedback(() -> Text.literal("  \u00a7a\u2713 " + path), false);
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    VillageCastles.LOGGER.error("Failed to export wall {} {}", palette.id, segmentType, e);
                    failed++;
                }
            }
        }

        final int totalExported = exported;
        final int totalFailed = failed;
        source.sendFeedback(() -> Text.literal("\u00a7aExported " + totalExported + " structures"
            + (totalFailed > 0 ? " \u00a7c(" + totalFailed + " failed)" : "")), true);

        return exported;
    }

    /**
     * Generate a ruins variant at the player's position.
     */
    private static int executeRuins(CommandContext<ServerCommandSource> ctx, String variantStr) {
        ServerCommandSource source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendError(Text.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        int variantIndex;
        try {
            variantIndex = Integer.parseInt(variantStr);
            if (variantIndex < 1 || variantIndex > 2) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            source.sendError(Text.literal("Invalid variant: " + variantStr + ". Valid: 1, 2"));
            return 0;
        }

        java.util.List<RuinsGenerator.RuinsVariant> variants = RuinsGenerator.RuinsVariant.getVariantsForBiome(palette);
        RuinsGenerator.RuinsVariant variant = variants.get(variantIndex - 1);

        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        ServerWorld world = source.getWorld();
        Direction facing;
        try {
            facing = source.getPlayerOrThrow().getHorizontalFacing();
        } catch (CommandSyntaxException e) {
            source.sendError(Text.literal("This command must be run by a player"));
            return 0;
        }
        BlockPos generatePos = playerPos.offset(facing, variant.baseSize.diameter / 2 + 10);

        source.sendFeedback(() -> Text.literal("\u00a7eGenerating ruins: " + variant.displayName + "..."), true);

        try {
            final CastleGenerator.CastleBounds[] result = new CastleGenerator.CastleBounds[1];
            StructureHelper.withForcedChunks(world, generatePos, variant.baseSize.diameter / 2 + 5, () -> {
                long seed = world.getSeed() + generatePos.hashCode();
                RuinsGenerator generator = new RuinsGenerator(variant, seed);
                result[0] = generator.generate(world, generatePos);
            });

            CastleGenerator.CastleBounds bounds = result[0];
            source.sendFeedback(() -> Text.literal("\u00a7aRuins generated! Size: "
                + bounds.getWidth() + "x" + bounds.getHeight() + "x" + bounds.getDepth()), false);
            source.sendFeedback(() -> Text.literal("\u00a77Save as: " + variant.getStructurePath() + ".nbt"), false);

            return 1;
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate ruins", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendError(Text.literal("Failed to generate ruins: " + msg));
            return 0;
        }
    }

    /**
     * Generate and export ALL ruins variants (2 per biome, 10 total).
     */
    private static int executeExportRuins(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerWorld world = source.getWorld();
        BlockPos playerPos = BlockPos.ofFloored(source.getPosition());
        Path runDir = source.getServer().getRunDirectory();

        source.sendFeedback(() -> Text.literal("\u00a7eGenerating and exporting all ruins variants..."), true);

        int exported = 0;
        int failed = 0;
        int xOffset = 0;

        for (RuinsGenerator.RuinsVariant variant : RuinsGenerator.RuinsVariant.values()) {
            BlockPos generatePos = playerPos.add(xOffset, 0, 0);
            xOffset += variant.baseSize.diameter + 30;

            try {
                StructureHelper.forceLoadChunks(world, generatePos, variant.baseSize.diameter / 2 + 5);

                long seed = world.getSeed() + generatePos.hashCode();
                RuinsGenerator generator = new RuinsGenerator(variant, seed);
                CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                String structurePath = variant.getStructurePath();
                Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                if (NbtExporter.exportRegion(world, bounds.min, bounds.max, outputPath)) {
                    exported++;
                    final String path = structurePath;
                    source.sendFeedback(() -> Text.literal("  \u00a7a\u2713 " + path), false);
                } else {
                    failed++;
                }
            } catch (Exception e) {
                VillageCastles.LOGGER.error("Failed to export ruins {}: {}", variant.displayName, e.getMessage());
                failed++;
            }
        }

        final int totalExported = exported;
        final int totalFailed = failed;
        source.sendFeedback(() -> Text.literal("\u00a7aExported " + totalExported + " ruins"
            + (totalFailed > 0 ? " \u00a7c(" + totalFailed + " failed)" : "")), true);

        return exported;
    }

    private static int executeStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        source.sendFeedback(() -> Text.translatable("commands.villagecastles.status.header"), false);

        String[] biomes = {"plains", "desert", "savanna", "taiga", "snowy"};
        String[] sizes = {"small", "medium", "large"};
        String[] wallTypes = {"wall_straight", "wall_corner", "wall_gate", "wall_tower", "wall_terminator"};

        int found = 0;
        int missing = 0;

        for (String biome : biomes) {
            source.sendFeedback(() -> Text.literal(""), false);
            final String biomeName = biome.substring(0, 1).toUpperCase() + biome.substring(1);
            source.sendFeedback(() -> Text.translatable("commands.villagecastles.status.biome_header", biomeName), false);

            // Castle variants
            for (String size : sizes) {
                String path = biome + "/castle_" + size;
                boolean exists = structureExists(path);
                if (exists) found++; else missing++;
                final String status = exists ? "\u00a7a\u2713" : "\u00a7c\u2717";
                final String displayPath = path;
                source.sendFeedback(() -> Text.literal("  " + status + " \u00a7r" + displayPath), false);
            }

            // Ruins (2 per biome)
            for (int v = 1; v <= 2; v++) {
                String ruinsPath = biome + "/castle_ruins_" + v;
                boolean ruinsExist = structureExists(ruinsPath);
                if (ruinsExist) found++; else missing++;
                final String ruinsStatus = ruinsExist ? "\u00a7a\u2713" : "\u00a7c\u2717";
                final String ruinsDisplay = ruinsPath;
                source.sendFeedback(() -> Text.literal("  " + ruinsStatus + " \u00a7r" + ruinsDisplay), false);
            }

            // Village walls
            for (String wallType : wallTypes) {
                String wallPath = "village_walls/" + biome + "/" + wallType;
                boolean wallExists = structureExists(wallPath);
                if (wallExists) found++; else missing++;
                final String wallStatus = wallExists ? "\u00a7a\u2713" : "\u00a7c\u2717";
                final String wallDisplay = wallPath;
                source.sendFeedback(() -> Text.literal("  " + wallStatus + " \u00a7r" + wallDisplay), false);
            }
        }

        final int totalFound = found;
        final int totalMissing = missing;
        final int total = found + missing;
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.status.summary",
            totalFound, total, totalMissing), false);

        return 1;
    }

    private static boolean structureExists(String structurePath) {
        return StructureHelper.structureNbtExists(structurePath);
    }

    private static int executeList(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        source.sendFeedback(() -> Text.translatable("commands.villagecastles.list.biomes_header"), false);
        for (BiomePalette palette : BiomePalette.values()) {
            source.sendFeedback(() -> Text.translatable("commands.villagecastles.list.biome_entry",
                palette.id, palette.displayName), false);
        }

        source.sendFeedback(() -> Text.translatable("commands.villagecastles.list.sizes_header"), false);
        for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
            source.sendFeedback(() -> Text.translatable("commands.villagecastles.list.size_entry",
                size.name().toLowerCase(), size.diameter), false);
        }

        source.sendFeedback(() -> Text.translatable("commands.villagecastles.list.segments_header"), false);
        for (VillageWallGenerator.SegmentType type : VillageWallGenerator.SegmentType.values()) {
            source.sendFeedback(() -> Text.translatable("commands.villagecastles.list.segment_entry",
                type.name().toLowerCase()), false);
        }

        return 1;
    }

    private static int executeHelp(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();

        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.header"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.generate_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.generate_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.wall_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.wall_desc"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.wall_segments"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.walls_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.walls_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.ruins_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.ruins_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.export_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.export_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.exportruins_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.exportruins_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.exportall_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.exportall_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.status_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.status_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.list_cmd"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.list_desc"), false);
        source.sendFeedback(() -> Text.literal(""), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.workflow"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.workflow_1"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.workflow_2"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.workflow_3"), false);
        source.sendFeedback(() -> Text.translatable("commands.villagecastles.help.workflow_4"), false);

        return 1;
    }
}
