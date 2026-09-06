package com.villagecastles.command;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.villagecastles.generator.ancient.AncientCastleDesigner;
import com.villagecastles.generator.ancient.AncientPalette;
import com.villagecastles.generator.ancient.Condition;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.HeightSampler;
import com.villagecastles.generator.ancient.Landform;
import com.villagecastles.generator.ancient.LandformSurvey;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.util.StructureHelper;
import com.villagecastles.worldgen.AncientCastlePiece;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The ancient-castle halves of {@code /village-castles}: worldgen structures are hard to iterate
 * on, so both instruments run the real pipeline against the live world.
 *
 * <ul>
 *   <li>{@code ancient <landform> [condition] [seed]} builds one castle at the player through the
 *       same designer and the same placement code the worldgen piece uses, with an optional seed
 *       so a shape worth studying can be reproduced.</li>
 *   <li>{@code ancientscan} runs the landform classifier where the player stands and reports
 *       every metric it derived plus its verdict: the threshold-tuning instrument.</li>
 * </ul>
 */
public final class AncientCastleCommands {

    private AncientCastleCommands() {}

    private static final java.util.function.Predicate<CommandSourceStack> REQUIRES_OP =
        source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);

    public static LiteralArgumentBuilder<CommandSourceStack> ancient() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ancient")
            .requires(REQUIRES_OP);
        for (Landform landform : Landform.values()) {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(landform.id())
                .executes(ctx -> build(ctx.getSource(), landform, null,
                    ctx.getSource().getLevel().getRandom().nextLong()));
            for (Condition condition : Condition.values()) {
                node.then(Commands.literal(condition.id())
                    .executes(ctx -> build(ctx.getSource(), landform, condition,
                        ctx.getSource().getLevel().getRandom().nextLong()))
                    .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(ctx -> build(ctx.getSource(), landform, condition,
                            LongArgumentType.getLong(ctx, "seed")))));
            }
            root.then(node);
        }
        return root;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> ancientScan() {
        return Commands.literal("ancientscan")
            .requires(REQUIRES_OP)
            .executes(ctx -> scan(ctx.getSource()));
    }

    public static LiteralArgumentBuilder<CommandSourceStack> villager() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("villager")
            .requires(REQUIRES_OP);
        for (com.villagecastles.generator.villager.VillagerCastleDesigner.Size size
                : com.villagecastles.generator.villager.VillagerCastleDesigner.Size.values()) {
            LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(size.id())
                .executes(ctx -> buildVillager(ctx.getSource(), size, null,
                    ctx.getSource().getLevel().getRandom().nextLong()));
            for (com.villagecastles.generator.BiomePalette biome
                    : com.villagecastles.generator.BiomePalette.values()) {
                node.then(Commands.literal(biome.id)
                    .executes(ctx -> buildVillager(ctx.getSource(), size, biome.id,
                        ctx.getSource().getLevel().getRandom().nextLong()))
                    .then(Commands.argument("seed", LongArgumentType.longArg())
                        .executes(ctx -> buildVillager(ctx.getSource(), size, biome.id,
                            LongArgumentType.getLong(ctx, "seed")))));
            }
            root.then(node);
        }
        return root;
    }

    private static int buildVillager(CommandSourceStack source,
            com.villagecastles.generator.villager.VillagerCastleDesigner.Size size,
            String biomeId, long seed) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        HeightSampler sampler = HeightSampler.live(level);

        Direction facing = source.getPlayer() != null
            ? source.getPlayer().getDirection() : Direction.NORTH;
        BlockPos anchor = new BlockPos(at.getX(), sampler.floor(at.getX(), at.getZ()) - 1, at.getZ());
        String biome = biomeId != null ? biomeId
            : villagerBiomeFor(level.getBiome(anchor).unwrapKey()
                .map(key -> key.identifier().getPath()).orElse(""));

        var site = new com.villagecastles.generator.villager.VillagerCastleDesigner.Site(
            anchor, size, facing);
        int reach = size.clearance / 2 + LandformSurvey.FIELD_SKIRT + 12;
        final int[] blocks = new int[1];
        StructureHelper.withForcedChunks(level, anchor, reach + 16, () -> {
            LandformSurvey.Site fieldSite = new LandformSurvey.Site(
                com.villagecastles.generator.ancient.Landform.HILLTOP, anchor,
                size.clearance / 2, facing);
            HeightField field = LandformSurvey.sampleField(sampler, fieldSite);
            Plan plan = com.villagecastles.generator.villager.VillagerCastleDesigner.draw(
                site, field, biome, seed);
            BoundingBox everything = new BoundingBox(
                anchor.getX() - reach - 16, level.getMinY(), anchor.getZ() - reach - 16,
                anchor.getX() + reach + 16, level.getMaxY(), anchor.getZ() + reach + 16);
            AncientCastlePiece.place(level, plan, everything, seed, level.getRandom());
            blocks[0] = plan.blocks().size();
        });

        String summary = "Raised a villager " + size.id() + " castle (" + biome + ") at "
            + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ()
            + " - seed " + seed + ", " + blocks[0] + " blocks";
        source.sendSuccess(() -> Component.literal(summary), true);
        return 1;
    }

    /** Folds a biome id path to the five villager-palette ids. */
    private static String villagerBiomeFor(String path) {
        if (path.contains("desert") || path.contains("badlands")) return "desert";
        if (path.contains("savanna")) return "savanna";
        if (path.contains("snowy") || path.contains("frozen") || path.contains("grove")) return "snowy";
        if (path.contains("taiga")) return "taiga";
        return "plains";
    }

    private static int build(CommandSourceStack source, Landform landform, Condition forced,
                             long seed) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        HeightSampler sampler = HeightSampler.live(level);

        Direction facing = source.getPlayer() != null
            ? source.getPlayer().getDirection() : Direction.NORTH;
        BlockPos anchor = new BlockPos(at.getX(), sampler.floor(at.getX(), at.getZ()) - 1, at.getZ());
        LandformSurvey.Site site = new LandformSurvey.Site(landform, anchor, landform.radius, facing);

        // The condition rides the seed too, so one number reproduces the whole find.
        Condition condition = forced != null ? forced
            : Condition.roll(RandomSource.create(seed ^ 0xC0FFEEL));

        String tint = AncientPalette.tintIdForBiomePath(
            level.getBiome(anchor).unwrapKey().map(key -> key.identifier().getPath()).orElse(""));

        int reach = landform.radius + LandformSurvey.FIELD_SKIRT + 12;
        final int[] stats = new int[2]; // blocks placed, bbox violations
        StructureHelper.withForcedChunks(level, anchor, reach + 16, () -> {
            HeightField field = LandformSurvey.sampleField(sampler, site);
            Plan plan = AncientCastleDesigner.draw(site, condition, field, tint, seed);

            // The piece's own bounding box, recomputed: anything the plan draws outside it would
            // silently vanish at worldgen time, so the command is where that gets caught.
            BoundingBox pieceBox = new BoundingBox(
                anchor.getX() - reach, field.minY() - 10, anchor.getZ() - reach,
                anchor.getX() + reach, field.maxY() + 44, anchor.getZ() + reach);
            for (BlockPos pos : plan.blocks().keySet()) {
                if (!pieceBox.isInside(pos)) stats[1]++;
            }

            // No chunk to clip against here, so the whole level's height stands in for one.
            BoundingBox everything = new BoundingBox(
                anchor.getX() - reach - 16, level.getMinY(), anchor.getZ() - reach - 16,
                anchor.getX() + reach + 16, level.getMaxY(), anchor.getZ() + reach + 16);
            AncientCastlePiece.place(level, plan, everything, seed, level.getRandom());
            stats[0] = plan.blocks().size();
        });

        String summary = "Raised an ancient " + landform.id() + " (" + condition.id() + ", "
            + tint + ") at " + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ()
            + " - seed " + seed + ", " + stats[0] + " blocks";
        source.sendSuccess(() -> Component.literal(summary), true);
        if (stats[1] > 0) {
            source.sendFailure(Component.literal("WARNING: " + stats[1]
                + " plan blocks fall outside the worldgen bounding box and would be lost in the wild"));
        }
        return 1;
    }

    private static int scan(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos at = BlockPos.containing(source.getPosition());
        LandformSurvey.Survey survey = LandformSurvey.survey(
            HeightSampler.live(level), at.getX(), at.getZ(), level.getRandom());

        String verdict = survey.site()
            .map(site -> site.landform().id() + " (anchor " + site.anchor().toShortString()
                + ", facing " + site.facing() + ")")
            .orElse("none - no landform matched");
        String report = "Landform survey at " + at.getX() + ", " + at.getZ() + ":"
            + "\n inner: min " + survey.innerMin() + " max " + survey.innerMax()
            + " mean " + survey.innerMean() + " relief " + survey.innerRelief()
            + "\n ring: mean " + survey.ringMean() + " drop " + survey.drop()
            + " below-count " + survey.ringBelowCount() + "/16"
            + "\n gradient: axis " + survey.axisGrad() + " cross " + survey.crossGrad()
            + " high side " + survey.highSide()
            + "\n wet: inner " + survey.wetInner() + "/25 mid " + survey.wetMid()
            + "/8 ring " + survey.wetRing() + "/16"
            + "\n verdict: " + verdict
            + "\n (live heightmaps count trees and buildings; worldgen sees bare terrain)";
        source.sendSuccess(() -> Component.literal(report), false);
        return 1;
    }
}
