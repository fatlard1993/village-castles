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
                xOffset += size.diameter + 120; // Extra room so motte slopes don't overlap clears

                try {
                    // Large enough to cover the plains/medium motte (baseRadius=47) plus margin
                    int radius = size.diameter / 2 + 55;
                    int clearHeight = 60; // enough headroom for any castle size

                    // Force-load chunks in the area
                    StructureHelper.forceLoadChunks(world, generatePos, radius);

                    // Clear the region to air so terrain blocks don't contaminate the export
                    BlockPos clearMin = generatePos.offset(-radius, -5, -radius);
                    BlockPos clearMax = generatePos.offset(radius, clearHeight, radius);
                    StructureHelper.fillBox(world, clearMin, clearMax, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());

                    long seed = world.getSeed() + generatePos.hashCode();
                    CastleGenerator generator = new CastleGenerator(palette, seed, size);
                    CastleGenerator.CastleBounds bounds = generator.generate(world, generatePos);

                    // Add door + 2-step entrance stairs.
                    // NBT analysis: south wall is at bounds.max.z - 9, entrance gap at local y=2.
                    // Scan inward from bounds.max.z to find the actual wall (first solid row at y=0),
                    // then find the doorway (air at y=2 with solid at y=4 above it).
                    {
                        int wallZ = -1;
                        for (int wz = bounds.max.getZ(); wz >= bounds.min.getZ() && wallZ < 0; wz--) {
                            for (int wx = bounds.min.getX(); wx <= bounds.max.getX(); wx++) {
                                net.minecraft.world.level.block.state.BlockState ws =
                                    world.getBlockState(new net.minecraft.core.BlockPos(wx, bounds.min.getY(), wz));
                                if (!ws.isAir()) {
                                    wallZ = wz;
                                    VillageCastles.LOGGER.debug("[castle-entrance] Found wall at z={} block={}", wz, ws.getBlock());
                                    break;
                                }
                            }
                        }
                        if (wallZ >= 0) {
                            int doorY = bounds.min.getY() + 2;
                            // Stairs face NORTH (player approaches from south, steps up toward north/into building)
                            net.minecraft.world.level.block.state.BlockState stairBlock =
                                net.minecraft.world.level.block.Blocks.STONE_BRICK_STAIRS.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.NORTH);

                            // First pass: collect all entrance x positions (left-to-right order)
                            java.util.List<Integer> entranceXs = new java.util.ArrayList<>();
                            for (int ex = bounds.min.getX(); ex <= bounds.max.getX(); ex++) {
                                net.minecraft.world.level.block.state.BlockState atDoor = world.getBlockState(new net.minecraft.core.BlockPos(ex, doorY, wallZ));
                                net.minecraft.world.level.block.state.BlockState above2 = world.getBlockState(new net.minecraft.core.BlockPos(ex, doorY + 2, wallZ));
                                if (atDoor.isAir() && !above2.isAir()) entranceXs.add(ex);
                            }

                            // Second pass: place doors (left gets LEFT hinge, right gets RIGHT hinge) + stairs
                            for (int i = 0; i < entranceXs.size(); i++) {
                                int ex = entranceXs.get(i);
                                net.minecraft.world.level.block.state.properties.DoorHingeSide hinge =
                                    (i == 0) ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT
                                             : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT;
                                net.minecraft.core.BlockPos doorPos = new net.minecraft.core.BlockPos(ex, doorY, wallZ);
                                world.setBlock(doorPos,
                                    net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                                        .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH)
                                        .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER)
                                        .setValue(net.minecraft.world.level.block.DoorBlock.HINGE, hinge),
                                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                                world.setBlock(doorPos.above(),
                                    net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState()
                                        .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH)
                                        .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                                            net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)
                                        .setValue(net.minecraft.world.level.block.DoorBlock.HINGE, hinge),
                                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                                world.setBlock(new net.minecraft.core.BlockPos(ex, doorY-1, wallZ+1), stairBlock, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                                world.setBlock(new net.minecraft.core.BlockPos(ex, doorY-2, wallZ+2), stairBlock, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            }
                        }

                        // Plains SMALL structural fixes (local coords from NBT audit)
                        if ("plains".equals(palette.id) && size == CastleGenerator.CastleSize.SMALL) {
                            int bx = bounds.min.getX(), by = bounds.min.getY(), bz = bounds.min.getZ();
                            // Fix floor holes at y=1, z=13 (under the podium — 4 missing oak planks)
                            for (int fx = 12; fx <= 15; fx++)
                                world.setBlock(new net.minecraft.core.BlockPos(bx+fx, by+1, bz+13), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            // Fix podium slab gap at (14,2,13)
                            world.setBlock(new net.minecraft.core.BlockPos(bx+14, by+2, bz+13),
                                net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                                        net.minecraft.world.level.block.state.properties.SlabType.BOTTOM),
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            // Fix northwest foundation gaps
                            world.setBlock(new net.minecraft.core.BlockPos(bx+7, by, bz+9),  net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            world.setBlock(new net.minecraft.core.BlockPos(bx+8, by, bz+9),  net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            world.setBlock(new net.minecraft.core.BlockPos(bx+7, by, bz+13), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            // Fix upper staircase missing landing plank at top of stairs
                            world.setBlock(new net.minecraft.core.BlockPos(bx+9, by+5, bz+15), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            // Fix staircase approach: add Y=2 floor tiles connecting to stair bottom (9,2,17)
                            world.setBlock(new net.minecraft.core.BlockPos(bx+10, by+2, bz+17), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            world.setBlock(new net.minecraft.core.BlockPos(bx+11, by+2, bz+17), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            world.setBlock(new net.minecraft.core.BlockPos(bx+11, by+2, bz+16), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            world.setBlock(new net.minecraft.core.BlockPos(bx+11, by+2, bz+15), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            // Fix floating stair risers — add planks beneath steps 2 and 3
                            world.setBlock(new net.minecraft.core.BlockPos(bx+9, by+2, bz+16), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            world.setBlock(new net.minecraft.core.BlockPos(bx+9, by+3, bz+15), net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            // Fix floating gateway lintel slabs — add stone_brick support row at y=3,z=20
                            for (int lx = 11; lx <= 16; lx++)
                                world.setBlock(new net.minecraft.core.BlockPos(bx+lx, by+3, bz+20), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                            VillageCastles.LOGGER.info("Applied plains_small structural fixes");
                        }
                    }

                    String structurePath = palette.id + "/castle_" + size.name().toLowerCase();
                    Path outputPath = NbtExporter.getStructureOutputPath(structurePath, runDir);

                    if (NbtExporter.isPolished(outputPath)) {
                        VillageCastles.LOGGER.info("  SKIP {} (polished)", structurePath);
                        exported++;
                        continue;
                    }

                    // Full bounds: stone foundation at local y=0, fence at local y=1.
                    // Place at minY-1 so the fence lands at grass level.
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
