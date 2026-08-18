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
                xOffset += 220; // fixed pitch, wide enough that no two clear regions meet

                try {
                    // Fixed and generous: the clear has to cover the widest bounds any generator
                    // declares, which is not a function of the size budget.
                    int radius = 70;
                    int clearHeight = 60; // enough headroom for any castle size
                    // Deep enough to cover the lowest a generator digs. Generators return bounds
                    // reaching well below the base plane (desert/large: -cisternDepth-2; the ice
                    // palace deeper still), and the export box is those bounds: clearing only to
                    // -5 left native rock inside it, so snowy/castle_large exported 41,656
                    // deepslate plus tuff, clay, moss and groundwater as though it were castle.
                    int clearDepth = 40;

                    // Force-load chunks in the area
                    StructureHelper.forceLoadChunks(world, generatePos, radius);

                    // Clear the region to air so terrain blocks don't contaminate the export
                    BlockPos clearMin = generatePos.offset(-radius, -clearDepth, -radius);
                    BlockPos clearMax = generatePos.offset(radius, clearHeight, radius);
                    clearToAir(world, clearMin, clearMax);

                    long seed = world.getSeed() + generatePos.hashCode();
                    CastleGenerator generator = new CastleGenerator(palette, seed, size);
                    CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                    // Export what the castle actually built, not the box the generator declared.
                    //
                    // Two things ride on this. Size: the declared bounds reserve courtyards,
                    // palisades and yard radii that several generators never build, so
                    // plains/castle_small shipped as a 51x51 box holding a 15x11 manor with 8%
                    // of its columns occupied. Registration: VillageCastleAttachmentMixin
                    // anchors template-local y=0 to the terrain surface, and generators whose
                    // declared minY sat below their own floor (desert/large: -cisternDepth-2)
                    // therefore placed the whole castle metres into the air.
                    BlockPos[] tight = tightBounds(world, bounds.min, bounds.max);
                    if (tight == null) {
                        VillageCastles.LOGGER.error("  FAIL {}/{} - generator placed no blocks", palette.id, size);
                        failed++;
                        continue;
                    }
                    BlockPos tightMin = tight[0];
                    BlockPos tightMax = tight[1];

                    // A template should never ship loose items. Suppressed drops stop generators
                    // creating them; this catches anything the world produced on its own, since
                    // fillFromWorld captures every entity inside the box.
                    int swept = 0;
                    for (net.minecraft.world.entity.item.ItemEntity stray : world.getEntitiesOfClass(
                            net.minecraft.world.entity.item.ItemEntity.class,
                            net.minecraft.world.phys.AABB.encapsulatingFullBlocks(tightMin, tightMax))) {
                        stray.discard();
                        swept++;
                    }
                    if (swept > 0) {
                        VillageCastles.LOGGER.warn("  swept {} stray item entities from {}/{}",
                            swept, palette.id, size.name().toLowerCase());
                    }
                    VillageCastles.LOGGER.info("  {} {}: declared {}x{}x{} -> actual {}x{}x{}",
                        palette.id, size.name().toLowerCase(),
                        bounds.getWidth(), bounds.getHeight(), bounds.getDepth(),
                        tightMax.getX() - tightMin.getX() + 1,
                        tightMax.getY() - tightMin.getY() + 1,
                        tightMax.getZ() - tightMin.getZ() + 1);

                    String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
                    Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                    if (NbtExporter.isPolished(outputPath)) {
                        VillageCastles.LOGGER.info("  SKIP {} (polished)", structurePath);
                        exported++;
                        continue;
                    }

                    if (NbtExporter.exportRegion(world, tightMin, tightMax, outputPath)) {
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

    /** Blank the build region, skipping cells that are already air. */
    private static void clearToAir(ServerLevel world, BlockPos min, BlockPos max) {
        net.minecraft.world.level.block.state.BlockState air =
            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (world.getBlockState(cursor.set(x, y, z)).isAir()) continue;
                    world.setBlock(cursor, air, StructureHelper.SET_FLAGS);
                }
            }
        }
    }

    /**
     * The smallest box containing every non-air block the generator placed.
     *
     * @return {min, max}, or null if the searched region is empty.
     */
    private static BlockPos[] tightBounds(ServerLevel world, BlockPos searchMin, BlockPos searchMax) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean any = false;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = searchMin.getX(); x <= searchMax.getX(); x++) {
            for (int y = searchMin.getY(); y <= searchMax.getY(); y++) {
                for (int z = searchMin.getZ(); z <= searchMax.getZ(); z++) {
                    if (world.getBlockState(cursor.set(x, y, z)).isAir()) continue;
                    any = true;
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (z < minZ) minZ = z;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                    if (z > maxZ) maxZ = z;
                }
            }
        }
        if (!any) return null;
        return new BlockPos[]{new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)};
    }

}
