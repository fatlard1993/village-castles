package com.villagecastles.export;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.BiomePalette;
import com.villagecastles.generator.CastleGenerator;
import com.villagecastles.generator.VillageWallGenerator;
import com.villagecastles.util.NbtExporter;
import com.villagecastles.util.StructureHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generates and exports all structure NBTs on the first server tick.
 * Activated via -Dvillagecastles.exportall=true system property.
 * After export, stops the server automatically.
 */
public class StructureExporter implements ServerTickEvents.EndTick {

    private final AtomicBoolean executed = new AtomicBoolean(false);
    private int tickDelay = 5; // Wait a few ticks for world to fully load

    @Override
    public void onEndTick(MinecraftServer server) {
        if (tickDelay-- > 0) return;
        if (!executed.compareAndSet(false, true)) return;

        ServerLevel world = server.overworld();
        if (world == null) {
            VillageCastles.LOGGER.error("Overworld not available, cannot export structures");
            return;
        }

        Path runDir = server.getServerDirectory();
        VillageCastles.LOGGER.info("=== Starting structure export ===");

        int exported = 0;
        int failed = 0;

        // Pre-generate the spawn area chunks so we have ground to build on
        // Use Y=64 on superflat, or find surface
        int baseY = 4; // superflat grass level is Y=4 on default superflat; we'll use a safe default
        // Try to find actual ground level at 0,0
        BlockPos probe = world.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, new BlockPos(0, 0, 0));
        if (probe.getY() > 0) {
            baseY = probe.getY();
        }

        int xOffset = 0;

        // === Export Castles ===
        for (BiomePalette palette : BiomePalette.values()) {
            for (CastleGenerator.CastleSize size : CastleGenerator.CastleSize.values()) {
                BlockPos generatePos = new BlockPos(xOffset, baseY, 0);
                xOffset += size.diameter + 30; // Space them apart

                try {
                    // Force-load chunks in the area
                    StructureHelper.forceLoadChunks(world, generatePos, size.diameter / 2 + 5);

                    long seed = world.getSeed() + generatePos.hashCode();
                    CastleGenerator generator = new CastleGenerator(palette, seed, size);
                    CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                    String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
                    Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                    if (NbtExporter.isPolished(outputPath)) {
                        VillageCastles.LOGGER.info("  SKIP {} (polished)", structurePath);
                        exported++;
                        continue;
                    }

                    if (NbtExporter.exportRegion(world, bounds.min, bounds.max, outputPath)) {
                        exported++;
                        VillageCastles.LOGGER.info("  OK {}", structurePath);
                    } else {
                        failed++;
                        VillageCastles.LOGGER.error("  FAIL {}", structurePath);
                    }
                } catch (Exception e) {
                    VillageCastles.LOGGER.error("  FAIL {} {} - {}", palette.id, size, e.getMessage());
                    failed++;
                }
            }
        }

        VillageCastles.LOGGER.info("=== Export complete: {} succeeded, {} failed ===", exported, failed);

        // Stop the server after export
        VillageCastles.LOGGER.info("Stopping server...");
        server.halt(false);
    }

}
