package com.villagecastles.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.Direction;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

import java.nio.file.Files;
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

    private static CastleGenerator.CastleBounds lastBounds = null;
    private static String lastBiome = null;
    private static String lastSize = null;
    // Center of the last generate — used as flood-fill origin for auto-bounds on capture
    private static BlockPos lastGenerateCenter = null;

    // Mutable capture region (used by bounds show/expand/shrink if needed)
    private static BlockPos captureMin = null;
    private static BlockPos captureMax = null;

    private static final java.util.function.Predicate<CommandSourceStack> REQUIRES_OP =
        source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    private static final SuggestionProvider<CommandSourceStack> BIOME_SUGGESTIONS =
        (context, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(BiomePalette.values()).map(p -> p.id),
            builder
        );

    private static final SuggestionProvider<CommandSourceStack> SIZE_SUGGESTIONS =
        (context, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(CastleGenerator.CastleSize.values()).map(s -> s.name().toLowerCase()),
            builder
        );

    private static final SuggestionProvider<CommandSourceStack> WALL_SEGMENT_SUGGESTIONS =
        (context, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(VillageWallGenerator.SegmentType.values()).map(s -> s.name().toLowerCase()),
            builder
        );

    private static final SuggestionProvider<CommandSourceStack> RUINS_VARIANT_SUGGESTIONS =
        (context, builder) -> SharedSuggestionProvider.suggest(
            java.util.stream.Stream.of("1", "2"),
            builder
        );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("villagecastles")
                .requires(CommandSourceStack::isPlayer) // Player only
                .then(Commands.literal("generate")
                    .requires(REQUIRES_OP)
                    .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeGenerate(ctx, "large")) // Default to large
                        .then(Commands.argument("size", StringArgumentType.word())
                            .suggests(SIZE_SUGGESTIONS)
                            .executes(ctx -> executeGenerate(ctx,
                                StringArgumentType.getString(ctx, "size")))
                        )
                    )
                )
                .then(Commands.literal("wall")
                    .requires(REQUIRES_OP)
                    .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeWall(ctx, "straight")) // Default to straight
                        .then(Commands.argument("segment", StringArgumentType.word())
                            .suggests(WALL_SEGMENT_SUGGESTIONS)
                            .executes(ctx -> executeWall(ctx,
                                StringArgumentType.getString(ctx, "segment")))
                        )
                    )
                )
                .then(Commands.literal("walls")
                    .requires(REQUIRES_OP)
                    .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(GenerateCastleCommand::executeWallShowcase)
                    )
                )
                .then(Commands.literal("ruins")
                    .requires(REQUIRES_OP)
                    .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeRuins(ctx, "1"))
                        .then(Commands.argument("variant", StringArgumentType.word())
                            .suggests(RUINS_VARIANT_SUGGESTIONS)
                            .executes(ctx -> executeRuins(ctx,
                                StringArgumentType.getString(ctx, "variant")))
                        )
                    )
                )
                .then(Commands.literal("exportruins")
                    .requires(REQUIRES_OP)
                    .executes(GenerateCastleCommand::executeExportRuins)
                )
                .then(Commands.literal("exportall")
                    .requires(REQUIRES_OP)
                    .executes(GenerateCastleCommand::executeExportAll)
                )
                .then(Commands.literal("showcase")
                    .requires(REQUIRES_OP)
                    .executes(GenerateCastleCommand::executeShowcase)
                )
                .then(Commands.literal("export")
                    .requires(REQUIRES_OP)
                    .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executeExport(ctx, "large", false))
                        .then(Commands.argument("size", StringArgumentType.word())
                            .suggests(SIZE_SUGGESTIONS)
                            .executes(ctx -> executeExport(ctx,
                                StringArgumentType.getString(ctx, "size"), false))
                            .then(Commands.literal("force")
                                .executes(ctx -> executeExport(ctx,
                                    StringArgumentType.getString(ctx, "size"), true))
                            )
                        )
                    )
                )
                .then(Commands.literal("place")
                    .requires(REQUIRES_OP)
                    .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .executes(ctx -> executePlace(ctx, "large"))
                        .then(Commands.argument("size", StringArgumentType.word())
                            .suggests(SIZE_SUGGESTIONS)
                            .executes(ctx -> executePlace(ctx,
                                StringArgumentType.getString(ctx, "size")))
                        )
                    )
                )
                .then(Commands.literal("capture")
                    .requires(REQUIRES_OP)
                    .executes(ctx -> executeCapture(ctx, 1))
                    .then(Commands.argument("biome", StringArgumentType.word())
                        .suggests(BIOME_SUGGESTIONS)
                        .then(Commands.argument("size", StringArgumentType.word())
                            .suggests(SIZE_SUGGESTIONS)
                            .executes(ctx -> executeCaptureAtPlayer(ctx, 1))
                            .then(Commands.argument("yOffset", IntegerArgumentType.integer(-64, 64))
                                .executes(ctx -> executeCaptureAtPlayer(ctx, IntegerArgumentType.getInteger(ctx, "yOffset")))
                            )
                        )
                    )
                )
                .then(Commands.literal("bounds")
                    .requires(REQUIRES_OP)
                    .executes(GenerateCastleCommand::executeBoundsShow)
                    .then(Commands.literal("show")
                        .executes(GenerateCastleCommand::executeBoundsShow)
                    )
                )
                .then(Commands.literal("status")
                    .executes(GenerateCastleCommand::executeStatus)
                )
                .then(Commands.literal("list")
                    .executes(GenerateCastleCommand::executeList)
                )
                .then(Commands.literal("help")
                    .executes(GenerateCastleCommand::executeHelp)
                )
        );

        VillageCastles.LOGGER.info("Registered /villagecastles command");
    }

    private static int executeGenerate(CommandContext<CommandSourceStack> ctx, String sizeStr) {
        CommandSourceStack source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        // Parse biome
        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        // Parse size
        CastleGenerator.CastleSize size;
        try {
            size = CastleGenerator.CastleSize.valueOf(sizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_size", sizeStr));
            return 0;
        }

        // Get player position
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ServerLevel world = source.getLevel();

        // Validate Y range -- castles need headroom and foundation
        if (playerPos.getY() < -50 || playerPos.getY() > 300) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.position_extreme", playerPos.getY()));
            return 0;
        }

        // Generate in front of player based on their facing direction
        Direction facing;
        try {
            facing = source.getPlayerOrException().getDirection();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return 0;
        }
        BlockPos generatePos = playerPos.relative(facing, size.diameter / 2 + 10);

        source.sendSuccess(() -> Component.translatable("commands.villagecastles.generate.starting",
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
            lastBounds = bounds;
            lastBiome = palette.id;
            lastSize = size.name().toLowerCase();
            lastGenerateCenter = generatePos;
            captureMin = bounds.min;
            captureMax = bounds.max;

            source.sendSuccess(() -> Component.translatable("commands.villagecastles.generate.success",
                bounds.getWidth(), bounds.getHeight(), bounds.getDepth()), false);

            source.sendSuccess(() -> Component.literal("§7Use /villagecastles capture to save this structure after editing"), false);

            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate castle", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendFailure(Component.translatable("commands.villagecastles.generate.failed", msg));
            return 0;
        }
    }

    /**
     * Place a saved NBT structure file at the player's position.
     * This loads the captured/exported NBT and places it in the world.
     */
    private static int executePlace(CommandContext<CommandSourceStack> ctx, String sizeStr) {
        CommandSourceStack source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendFailure(Component.literal("Unknown biome: " + biomeStr));
            return 0;
        }

        CastleGenerator.CastleSize size;
        try {
            size = CastleGenerator.CastleSize.valueOf(sizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown size: " + sizeStr));
            return 0;
        }

        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ServerLevel world = source.getLevel();

        String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
        Path runDir = source.getServer().getServerDirectory();
        Path nbtPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

        if (!Files.exists(nbtPath)) {
            source.sendFailure(Component.literal("NBT file not found: " + nbtPath));
            source.sendFailure(Component.literal("Generate and capture first, or run /villagecastles export " + biomeStr + " " + sizeStr));
            return 0;
        }

        try {
            CompoundTag nbt;
            try (java.io.InputStream is = Files.newInputStream(nbtPath)) {
                nbt = NbtIo.readCompressed(is, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            }
            StructureTemplate template = new StructureTemplate();
            template.load(world.registryAccess().lookupOrThrow(Registries.BLOCK), nbt);

            // Place in front of player at their feet. local Y=0 = player's feet Y.
            // Stand at the position where you want the ground floor to appear.
            net.minecraft.core.Direction facing = source.getPlayerOrException().getDirection();
            int halfX = template.getSize().getX() / 2;
            int halfZ = template.getSize().getZ() / 2;
            int frontDist = Math.max(halfX, halfZ) + 2;
            BlockPos frontCenter = playerPos.relative(facing, frontDist);
            BlockPos placePos = frontCenter.offset(-halfX, 0, -halfZ);

            StructurePlaceSettings placement = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(false);

            template.placeInWorld(world, placePos, placePos, placement, world.getRandom(), StructureHelper.SET_FLAGS);

            source.sendSuccess(() -> Component.literal("§aPlaced " + structurePath + " (" +
                template.getSize().getX() + "x" + template.getSize().getY() + "x" + template.getSize().getZ() +
                ") at " + placePos.toShortString()), true);

            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to place structure from NBT", e);
            source.sendFailure(Component.literal("Failed to place: " + e.getMessage()));
            return 0;
        }
    }

    private static int executeWall(CommandContext<CommandSourceStack> ctx, String segmentStr) {
        CommandSourceStack source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        VillageWallGenerator.SegmentType segmentType;
        try {
            segmentType = VillageWallGenerator.SegmentType.valueOf(segmentStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_segment", segmentStr));
            return 0;
        }

        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ServerLevel world = source.getLevel();
        BlockPos generatePos = playerPos.north(5);

        try {
            long seed = world.getSeed() + generatePos.hashCode();
            VillageWallGenerator generator = new VillageWallGenerator(palette, new java.util.Random(seed));
            generator.generate(world, generatePos, Direction.NORTH, segmentType);

            source.sendSuccess(() -> Component.translatable("commands.villagecastles.wall.success",
                segmentType.name().toLowerCase(), palette.id), false);

            source.sendSuccess(() -> Component.translatable("commands.villagecastles.wall.save_hint",
                palette.id, segmentType.name().toLowerCase()), false);

            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate wall", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendFailure(Component.translatable("commands.villagecastles.wall.failed", msg));
            return 0;
        }
    }

    private static int executeWallShowcase(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ServerLevel world = source.getLevel();

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

            source.sendSuccess(() -> Component.translatable("commands.villagecastles.walls.success", palette.id), false);
            return 1;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate wall showcase", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendFailure(Component.translatable("commands.villagecastles.walls.failed", msg));
            return 0;
        }
    }

    /**
     * Capture the CURRENT world state at the last generated bounds and save as NBT.
     * This lets you: generate → hand-edit in creative → capture the polished result.
     */
    private static int executeCapture(CommandContext<CommandSourceStack> ctx, int yOffset) {
        CommandSourceStack source = ctx.getSource();

        if (lastGenerateCenter == null || lastBiome == null || lastSize == null) {
            source.sendFailure(Component.literal("Nothing to capture. Generate a structure first with /villagecastles generate"));
            return 0;
        }

        try {
            ServerLevel world = source.getLevel();

            // --- Auto-derive tight bounding box from non-air blocks near the generation center ---
            // The castle was generated in an air-cleared region, so every non-air block is structure.
            int searchR = 80; // large enough for any castle size
            BlockPos center = lastGenerateCenter;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            int found = 0;
            for (int x = center.getX() - searchR; x <= center.getX() + searchR; x++) {
                for (int y = center.getY() - 10; y <= center.getY() + searchR; y++) {
                    for (int z = center.getZ() - searchR; z <= center.getZ() + searchR; z++) {
                        if (!world.getBlockState(new BlockPos(x, y, z)).isAir()) {
                            if (x < minX) minX = x; if (x > maxX) maxX = x;
                            if (y < minY) minY = y; if (y > maxY) maxY = y;
                            if (z < minZ) minZ = z; if (z > maxZ) maxZ = z;
                            found++;
                        }
                    }
                }
            }
            if (found == 0) {
                source.sendFailure(Component.literal("No structure blocks found near the generation center. Did you clear it accidentally?"));
                return 0;
            }
            final BlockPos tightMin = new BlockPos(minX, minY, minZ);
            final BlockPos tightMax = new BlockPos(maxX, maxY, maxZ);
            final int blockCount = found;

            // Use the generate position Y as local Y=0, matching the batch exporter convention.
            // The batch exporter exports from bounds.min.Y = originalCenter.Y = generatePos.Y.
            // Worldgen places at surfaceH - 3, so local Y=3 = surfaceH (visual floor).
            // originY = player's feet - yOffset. Default yOffset=1 → block below feet.
            // Use /villagecastles capture <biome> <size> <yOffset> to adjust.
            BlockPos captureStandPos = BlockPos.containing(source.getPosition());
            int originY = captureStandPos.getY() - yOffset;

            final int fw = tightMax.getX() - tightMin.getX();
            final int fh = tightMax.getY() - tightMin.getY();
            final int fd = tightMax.getZ() - tightMin.getZ();
            source.sendSuccess(() -> Component.literal("§eFound " + blockCount + " blocks, bounds " +
                fw + "×" + fh + "×" + fd + ". Ground reference Y=" + originY + " (your feet)"), true);

            BlockPos exportMin = new BlockPos(tightMin.getX(), originY, tightMin.getZ());
            BlockPos exportMax = tightMax;
            captureMin = exportMin;
            captureMax = exportMax;

            String structurePath = lastBiome + "/castle_" + lastSize;
            Path runDir = source.getServer().getServerDirectory();
            Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

            boolean exported = NbtExporter.exportRegion(world, exportMin, exportMax, outputPath);

            if (exported) {
                NbtExporter.markPolished(outputPath);
                source.sendSuccess(() -> Component.literal("§aCaptured and marked as §6POLISHED§a! Saved to " + outputPath), false);
                source.sendSuccess(() -> Component.literal("§7Export/exportall will skip this file. Use 'export <biome> <size> force' to overwrite."), false);
            } else {
                source.sendFailure(Component.literal("Failed to capture structure"));
            }

            return exported ? 1 : 0;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to capture structure", e);
            source.sendFailure(Component.literal("Capture failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Capture world state at PLAYER POSITION using known bounds for biome/size.
     * Stand at the CENTER of the structure you edited, then run:
     *   /villagecastles capture <biome> <size>
     * This saves your hand-edited work without regenerating.
     */
    private static int executeCaptureAtPlayer(CommandContext<CommandSourceStack> ctx, int yOffset) {
        CommandSourceStack source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");
        String sizeStr = StringArgumentType.getString(ctx, "size");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendFailure(Component.literal("Unknown biome: " + biomeStr));
            return 0;
        }

        CastleGenerator.CastleSize size;
        try {
            size = CastleGenerator.CastleSize.valueOf(sizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Unknown size: " + sizeStr));
            return 0;
        }

        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ServerLevel world = source.getLevel();

        // Generous bounds based on size — covers any structure variant
        int hRadius = size.diameter / 2 + 10;
        int belowGround = 15; // cisterns, dungeons, foundations
        int aboveGround;
        // Ice citadel is very tall
        if (palette == BiomePalette.SNOWY && size == CastleGenerator.CastleSize.LARGE) {
            aboveGround = 100;
        } else {
            aboveGround = size == CastleGenerator.CastleSize.LARGE ? 40 : 25;
        }

        // originY = player's feet - yOffset. Default yOffset=1 → block below feet = local Y=0.
        int captureOriginY = playerPos.getY() - yOffset;
        BlockPos min = new BlockPos(playerPos.getX() - hRadius, captureOriginY, playerPos.getZ() - hRadius);
        BlockPos max = playerPos.offset(hRadius, aboveGround, hRadius);

        try {
            String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
            Path runDir = source.getServer().getServerDirectory();
            Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

            source.sendSuccess(() -> Component.literal("§eCapturing " + (hRadius * 2) + "x" +
                (belowGround + aboveGround) + "x" + (hRadius * 2) +
                " region centered on you..."), true);

            boolean exported = NbtExporter.exportRegion(world, min, max, outputPath);

            if (exported) {
                NbtExporter.markPolished(outputPath);
                source.sendSuccess(() -> Component.literal("§aCaptured and marked as §6POLISHED§a! Saved to " + outputPath), false);
                source.sendSuccess(() -> Component.literal("§7Export/exportall will skip this file. Use 'export <biome> <size> force' to overwrite."), false);
            } else {
                source.sendFailure(Component.literal("Failed to capture structure"));
            }

            return exported ? 1 : 0;

        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to capture structure at player pos", e);
            source.sendFailure(Component.literal("Capture failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * Generate a single castle and export it as NBT.
     * Refuses to overwrite polished files unless force=true.
     */
    private static int executeExport(CommandContext<CommandSourceStack> ctx, String sizeStr, boolean force) {
        CommandSourceStack source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        CastleGenerator.CastleSize size;
        try {
            size = CastleGenerator.CastleSize.valueOf(sizeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_size", sizeStr));
            return 0;
        }

        // Check for polished marker BEFORE generating
        Path runDir = source.getServer().getServerDirectory();
        String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
        Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

        if (!force && NbtExporter.isPolished(outputPath)) {
            source.sendFailure(Component.literal("§c" + structurePath + " is marked as POLISHED (hand-edited)."));
            source.sendFailure(Component.literal("§cUse '/villagecastles export " + biomeStr + " " + sizeStr + " force' to overwrite."));
            return 0;
        }

        ServerLevel world = source.getLevel();
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        Direction facing;
        try {
            facing = source.getPlayerOrException().getDirection();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return 0;
        }
        BlockPos generatePos = playerPos.relative(facing, size.diameter / 2 + 10);

        try {
            StructureHelper.forceLoadChunks(world, generatePos, size.diameter / 2 + 5);
            try {
                long seed = world.getSeed() + generatePos.hashCode();
                CastleGenerator generator = new CastleGenerator(palette, seed, size);
                CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                boolean exported = NbtExporter.exportRegion(world, bounds.min, bounds.max, outputPath);

                if (exported) {
                    if (force) {
                        // Remove polished marker since we just overwrote with generated output
                        try { Files.deleteIfExists(NbtExporter.getPolishedMarkerPath(outputPath)); } catch (Exception ignored) {}
                        source.sendSuccess(() -> Component.literal("§aForce-exported " + structurePath + ".nbt §7(polished marker removed)"), true);
                    } else {
                        source.sendSuccess(() -> Component.literal("§aExported " + structurePath + ".nbt"), true);
                    }
                } else {
                    source.sendFailure(Component.literal("Failed to export " + structurePath));
                }

                return exported ? 1 : 0;
            } finally {
                StructureHelper.unforceChunks(world, generatePos, size.diameter / 2 + 5);
            }
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to export castle", e);
            source.sendFailure(Component.literal("Export failed: " + e.getMessage()));
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
    private static int executeShowcase(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        BlockPos playerPos = BlockPos.containing(source.getPosition());

        source.sendSuccess(() -> Component.literal("\u00a7eGenerating all 15 castle variants..."), true);

        int xOffset = 0;
        for (BiomePalette palette : BiomePalette.values()) {
            int zOffset = 0;
            for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
                BlockPos genPos = playerPos.offset(xOffset, 0, zOffset);

                try {
                    StructureHelper.withForcedChunks(world, genPos, size.diameter / 2 + 10, () -> {
                        long seed = world.getSeed() + genPos.hashCode();
                        CastleGenerator generator = new CastleGenerator(palette, seed, size);
                        generator.generate(world, genPos);
                    });
                    source.sendSuccess(() -> Component.literal("\u00a7a  \u2714 " + palette.displayName + " " + size.name().toLowerCase()), false);
                } catch (Exception e) {
                    source.sendSuccess(() -> Component.literal("\u00a7c  \u2718 " + palette.displayName + " " + size.name().toLowerCase() + ": " + e.getMessage()), false);
                }

                zOffset += 100; // Space rows 100 blocks apart
            }
            xOffset += 100; // Space columns 100 blocks apart
        }

        source.sendSuccess(() -> Component.literal("\u00a7aShowcase complete! 5 biomes \u00d7 3 sizes in a grid."), true);
        return 1;
    }

    private static int executeExportAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        Path runDir = source.getServer().getServerDirectory();

        source.sendSuccess(() -> Component.literal("\u00a7eGenerating and exporting all structures..."), true);

        int exported = 0;
        int failed = 0;
        int xOffset = 0;

        // Generate castles for each biome and size
        for (BiomePalette palette : BiomePalette.values()) {
            for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
                // Space each castle apart to avoid overlap
                BlockPos generatePos = playerPos.offset(xOffset, 0, 0);
                xOffset += size.diameter + 20;

                try {
                    StructureHelper.forceLoadChunks(world, generatePos, size.diameter / 2 + 5);
                    try {
                        long seed = world.getSeed() + generatePos.hashCode();
                        CastleGenerator generator = new CastleGenerator(palette, seed, size);
                        CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                        String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
                        Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                        if (NbtExporter.isPolished(outputPath)) {
                            final String path = structurePath;
                            source.sendSuccess(() -> Component.literal("  §6⊘ " + path + " (polished — skipped)"), false);
                        } else if (NbtExporter.exportRegion(world, bounds.min, bounds.max, outputPath)) {
                            exported++;
                            final String path = structurePath;
                            source.sendSuccess(() -> Component.literal("  \u00a7a\u2713 " + path), false);
                        } else {
                            failed++;
                        }
                    } finally {
                        StructureHelper.unforceChunks(world, generatePos, size.diameter / 2 + 5);
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
                BlockPos generatePos = playerPos.offset(xOffset, 0, 0);
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
                    BlockPos wallMin = origin.offset(-halfWidth, -2, -depth);
                    BlockPos wallMax = origin.offset(halfWidth, 12, depth);

                    String structurePath = "village_walls/" + palette.id + "/wall_" + segmentType.name().toLowerCase();
                    Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                    if (NbtExporter.exportRegion(world, wallMin, wallMax, outputPath)) {
                        exported++;
                        final String path = structurePath;
                        source.sendSuccess(() -> Component.literal("  \u00a7a\u2713 " + path), false);
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
        source.sendSuccess(() -> Component.literal("\u00a7aExported " + totalExported + " structures"
            + (totalFailed > 0 ? " \u00a7c(" + totalFailed + " failed)" : "")), true);

        return exported;
    }

    /**
     * Generate a ruins variant at the player's position.
     */
    private static int executeRuins(CommandContext<CommandSourceStack> ctx, String variantStr) {
        CommandSourceStack source = ctx.getSource();
        String biomeStr = StringArgumentType.getString(ctx, "biome");

        BiomePalette palette = BiomePalette.fromId(biomeStr);
        if (palette == null) {
            source.sendFailure(Component.translatable("commands.villagecastles.error.unknown_biome", biomeStr));
            return 0;
        }

        int variantIndex;
        try {
            variantIndex = Integer.parseInt(variantStr);
            if (variantIndex < 1 || variantIndex > 2) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("Invalid variant: " + variantStr + ". Valid: 1, 2"));
            return 0;
        }

        java.util.List<RuinsGenerator.RuinsVariant> variants = RuinsGenerator.RuinsVariant.getVariantsForBiome(palette);
        RuinsGenerator.RuinsVariant variant = variants.get(variantIndex - 1);

        BlockPos playerPos = BlockPos.containing(source.getPosition());
        ServerLevel world = source.getLevel();
        Direction facing;
        try {
            facing = source.getPlayerOrException().getDirection();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("This command must be run by a player"));
            return 0;
        }
        BlockPos generatePos = playerPos.relative(facing, variant.baseSize.diameter / 2 + 10);

        source.sendSuccess(() -> Component.literal("\u00a7eGenerating ruins: " + variant.displayName + "..."), true);

        try {
            final CastleGenerator.CastleBounds[] result = new CastleGenerator.CastleBounds[1];
            StructureHelper.withForcedChunks(world, generatePos, variant.baseSize.diameter / 2 + 5, () -> {
                long seed = world.getSeed() + generatePos.hashCode();
                RuinsGenerator generator = new RuinsGenerator(variant, seed);
                result[0] = generator.generate(world, generatePos);
            });

            CastleGenerator.CastleBounds bounds = result[0];
            source.sendSuccess(() -> Component.literal("\u00a7aRuins generated! Size: "
                + bounds.getWidth() + "x" + bounds.getHeight() + "x" + bounds.getDepth()), false);
            source.sendSuccess(() -> Component.literal("\u00a77Save as: " + variant.getStructurePath() + ".nbt"), false);

            return 1;
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Failed to generate ruins", e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            source.sendFailure(Component.literal("Failed to generate ruins: " + msg));
            return 0;
        }
    }

    /**
     * Generate and export ALL ruins variants (2 per biome, 10 total).
     */
    private static int executeExportRuins(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel world = source.getLevel();
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        Path runDir = source.getServer().getServerDirectory();

        source.sendSuccess(() -> Component.literal("\u00a7eGenerating and exporting all ruins variants..."), true);

        int exported = 0;
        int failed = 0;
        int xOffset = 0;

        for (RuinsGenerator.RuinsVariant variant : RuinsGenerator.RuinsVariant.values()) {
            BlockPos generatePos = playerPos.offset(xOffset, 0, 0);
            xOffset += variant.baseSize.diameter + 30;

            try {
                StructureHelper.forceLoadChunks(world, generatePos, variant.baseSize.diameter / 2 + 5);
                try {
                    long seed = world.getSeed() + generatePos.hashCode();
                    RuinsGenerator generator = new RuinsGenerator(variant, seed);
                    CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                    String structurePath = variant.getStructurePath();
                    Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                    if (NbtExporter.exportRegion(world, bounds.min, bounds.max, outputPath)) {
                        exported++;
                        final String path = structurePath;
                        source.sendSuccess(() -> Component.literal("  \u00a7a\u2713 " + path), false);
                    } else {
                        failed++;
                    }
                } finally {
                    StructureHelper.unforceChunks(world, generatePos, variant.baseSize.diameter / 2 + 5);
                }
            } catch (Exception e) {
                VillageCastles.LOGGER.error("Failed to export ruins {}: {}", variant.displayName, e.getMessage());
                failed++;
            }
        }

        final int totalExported = exported;
        final int totalFailed = failed;
        source.sendSuccess(() -> Component.literal("\u00a7aExported " + totalExported + " ruins"
            + (totalFailed > 0 ? " \u00a7c(" + totalFailed + " failed)" : "")), true);

        return exported;
    }

    private static int executeStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.translatable("commands.villagecastles.status.header"), false);

        String[] biomes = {"plains", "desert", "savanna", "taiga", "snowy"};
        String[] sizes = {"small", "medium", "large"};
        String[] wallTypes = {"wall_straight", "wall_corner", "wall_gate", "wall_tower", "wall_terminator"};

        int found = 0;
        int missing = 0;

        for (String biome : biomes) {
            source.sendSuccess(() -> Component.literal(""), false);
            final String biomeName = biome.substring(0, 1).toUpperCase() + biome.substring(1);
            source.sendSuccess(() -> Component.translatable("commands.villagecastles.status.biome_header", biomeName), false);

            // Castle variants
            for (String size : sizes) {
                String path = biome + "/castle_" + size;
                boolean exists = structureExists(path);
                if (exists) found++; else missing++;
                final String status = exists ? "\u00a7a\u2713" : "\u00a7c\u2717";
                final String displayPath = path;
                source.sendSuccess(() -> Component.literal("  " + status + " \u00a7r" + displayPath), false);
            }

            // Ruins (2 per biome)
            for (int v = 1; v <= 2; v++) {
                String ruinsPath = biome + "/castle_ruins_" + v;
                boolean ruinsExist = structureExists(ruinsPath);
                if (ruinsExist) found++; else missing++;
                final String ruinsStatus = ruinsExist ? "\u00a7a\u2713" : "\u00a7c\u2717";
                final String ruinsDisplay = ruinsPath;
                source.sendSuccess(() -> Component.literal("  " + ruinsStatus + " \u00a7r" + ruinsDisplay), false);
            }

            // Village walls
            for (String wallType : wallTypes) {
                String wallPath = "village_walls/" + biome + "/" + wallType;
                boolean wallExists = structureExists(wallPath);
                if (wallExists) found++; else missing++;
                final String wallStatus = wallExists ? "\u00a7a\u2713" : "\u00a7c\u2717";
                final String wallDisplay = wallPath;
                source.sendSuccess(() -> Component.literal("  " + wallStatus + " \u00a7r" + wallDisplay), false);
            }
        }

        final int totalFound = found;
        final int totalMissing = missing;
        final int total = found + missing;
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.status.summary",
            totalFound, total, totalMissing), false);

        return 1;
    }

    private static boolean structureExists(String structurePath) {
        return StructureHelper.structureNbtExists(structurePath);
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.translatable("commands.villagecastles.list.biomes_header"), false);
        for (BiomePalette palette : BiomePalette.values()) {
            source.sendSuccess(() -> Component.translatable("commands.villagecastles.list.biome_entry",
                palette.id, palette.displayName), false);
        }

        source.sendSuccess(() -> Component.translatable("commands.villagecastles.list.sizes_header"), false);
        for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
            source.sendSuccess(() -> Component.translatable("commands.villagecastles.list.size_entry",
                size.name().toLowerCase(), size.diameter), false);
        }

        source.sendSuccess(() -> Component.translatable("commands.villagecastles.list.segments_header"), false);
        for (VillageWallGenerator.SegmentType type : VillageWallGenerator.SegmentType.values()) {
            source.sendSuccess(() -> Component.translatable("commands.villagecastles.list.segment_entry",
                type.name().toLowerCase()), false);
        }

        return 1;
    }

    private static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();

        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.header"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.generate_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.generate_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.wall_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.wall_desc"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.wall_segments"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.walls_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.walls_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.ruins_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.ruins_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.export_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.export_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.exportruins_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.exportruins_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.exportall_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.exportall_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.status_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.status_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.list_cmd"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.list_desc"), false);
        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.workflow"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.workflow_1"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.workflow_2"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.workflow_3"), false);
        source.sendSuccess(() -> Component.translatable("commands.villagecastles.help.workflow_4"), false);

        return 1;
    }

    private static int executeBoundsShow(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (captureMin == null || captureMax == null) {
            source.sendFailure(Component.literal("No capture region — run /villagecastles generate first."));
            return 0;
        }
        int w = captureMax.getX() - captureMin.getX();
        int h = captureMax.getY() - captureMin.getY();
        int d = captureMax.getZ() - captureMin.getZ();
        source.sendSuccess(() -> Component.literal("§eLast capture region: " + w + "×" + h + "×" + d), false);
        source.sendSuccess(() -> Component.literal("  §7Min: " + captureMin.toShortString() + "  Max: " + captureMax.toShortString()), false);
        source.sendSuccess(() -> Component.literal("§7Run /villagecastles capture from ground level to auto-derive tight bounds and anchor Y."), false);
        return 1;
    }
}
