package com.villagecastles.generator.decay;

import com.villagecastles.generator.BiomePalette;
import com.villagecastles.util.StructureHelper;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SnowBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Decay engine that transforms generated castle structures into ruins.
 * Works directly on ServerWorld within a bounded region, applying weathering,
 * structural damage, collapse, vegetation, and atmospheric effects in phases.
 */
public class DecayEngine {

    private final ServerWorld world;
    private final BlockPos min;
    private final BlockPos max;
    private final Random random;
    private final double intensity;
    private final BiomePalette palette;
    private final int midY;
    private final BlockState air;

    // Track removed block positions for rubble generation
    private final List<BlockPos> removedAboveMid = new ArrayList<>();
    // Track original light source positions for soul lantern placement
    private final List<BlockPos> lightPositions = new ArrayList<>();

    public DecayEngine(ServerWorld world, BlockPos min, BlockPos max, Random random, double intensity, BiomePalette palette) {
        this.world = world;
        this.min = min;
        this.max = max;
        this.random = random;
        this.intensity = Math.max(0.0, Math.min(1.0, intensity));
        this.palette = palette;
        this.midY = (min.getY() + max.getY()) / 2;
        this.air = Blocks.AIR.getDefaultState();
    }

    /**
     * Apply all decay phases in order.
     */
    public void apply() {
        phaseWeathering();
        phaseStructuralDamage();
        phaseCollapse();
        phaseVegetation();
        phaseAtmosphere();
    }

    // ── Phase 1: Weathering ──────────────────────────────────────────────

    private void phaseWeathering() {
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();

                    if (block == Blocks.STONE_BRICKS) {
                        if (shouldDecay(0.40)) {
                            setBlock(pos, Blocks.CRACKED_STONE_BRICKS.getDefaultState());
                        } else if (shouldDecay(0.30)) {
                            setBlock(pos, Blocks.MOSSY_STONE_BRICKS.getDefaultState());
                        }
                    } else if (block == Blocks.COBBLESTONE) {
                        if (shouldDecay(0.25)) {
                            setBlock(pos, Blocks.MOSSY_COBBLESTONE.getDefaultState());
                        }
                    } else if (block == Blocks.SANDSTONE) {
                        if (shouldDecay(0.15)) {
                            setBlock(pos, Blocks.CUT_SANDSTONE.getDefaultState());
                        }
                    } else if (block == Blocks.CUT_SANDSTONE) {
                        if (shouldDecay(0.15)) {
                            setBlock(pos, Blocks.SAND.getDefaultState());
                        }
                    } else if (block == Blocks.MUD_BRICKS) {
                        if (shouldDecay(0.20)) {
                            setBlock(pos, Blocks.PACKED_MUD.getDefaultState());
                        }
                    } else if (block == Blocks.PACKED_ICE) {
                        if (shouldDecay(0.15)) {
                            setBlock(pos, Blocks.ICE.getDefaultState());
                        }
                    } else if (block == Blocks.DEEPSLATE_TILES) {
                        if (shouldDecay(0.30)) {
                            setBlock(pos, Blocks.CRACKED_DEEPSLATE_TILES.getDefaultState());
                        }
                    }
                }
            }
        }
    }

    // ── Phase 2: Structural Damage ───────────────────────────────────────

    private void phaseStructuralDamage() {
        Set<BlockPos> toRemove = new HashSet<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();

                    // Fragile blocks: 100% removal
                    if (isFragile(block)) {
                        toRemove.add(pos.toImmutable());
                        continue;
                    }

                    // Glass and glass panes: 90%
                    if (isGlass(block)) {
                        if (shouldDecay(0.90)) {
                            toRemove.add(pos.toImmutable());
                        }
                        continue;
                    }

                    // Doors and trapdoors: 80%
                    if (isDoor(block)) {
                        if (shouldDecay(0.80)) {
                            toRemove.add(pos.toImmutable());
                        }
                        continue;
                    }

                    // Ladders: 70%
                    if (block == Blocks.LADDER) {
                        if (shouldDecay(0.70)) {
                            toRemove.add(pos.toImmutable());
                        }
                        continue;
                    }

                    // Structural block removal with spatial clustering
                    if (state.isAir()) continue;
                    if (!state.isOpaqueFullCube() && !isSolidBuildingBlock(block)) continue;

                    if (isExposed(pos)) {
                        double baseProbability = 0.12 * intensity;

                        // Height bias: upper blocks 1.5x more likely
                        if (y > midY) {
                            baseProbability *= 1.5;
                        }

                        // Roof removal: top 3 layers get higher probability
                        if (y >= max.getY() - 2) {
                            baseProbability = 0.3 * intensity;
                        }

                        if (random.nextDouble() < baseProbability) {
                            toRemove.add(pos.toImmutable());
                            // Cascade to neighbors with 40% chance
                            cascadeRemoval(pos.toImmutable(), toRemove);
                        }
                    }
                }
            }
        }

        // Apply all removals
        for (BlockPos removePos : toRemove) {
            if (removePos.getY() > midY) {
                removedAboveMid.add(removePos);
            }
            setBlock(removePos, air);
        }
    }

    private void cascadeRemoval(BlockPos origin, Set<BlockPos> toRemove) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = origin.offset(dir);
            if (!isInBounds(neighbor)) continue;
            if (toRemove.contains(neighbor)) continue;

            BlockState neighborState = world.getBlockState(neighbor);
            if (neighborState.isAir()) continue;

            if (random.nextDouble() < 0.40) {
                toRemove.add(neighbor);
            }
        }
    }

    // ── Phase 3: Collapse ────────────────────────────────────────────────

    private void phaseCollapse() {
        // Pass 1: Remove floating blocks (scan top-down)
        boolean changed = true;
        int passes = 0;
        while (changed && passes < 10) {
            changed = false;
            passes++;
            BlockPos.Mutable pos = new BlockPos.Mutable();

            for (int y = max.getY(); y >= min.getY(); y--) {
                for (int x = min.getX(); x <= max.getX(); x++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        pos.set(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if (state.isAir()) continue;
                        if (!state.isOpaqueFullCube()) continue;

                        if (isFloating(pos)) {
                            removedAboveMid.add(pos.toImmutable());
                            setBlock(pos, air);
                            changed = true;
                        }
                    }
                }
            }
        }

        // Pass 2: Create rubble piles from removed upper blocks
        int[] rubbleHeight = new int[(max.getX() - min.getX() + 1) * (max.getZ() - min.getZ() + 1)];
        int width = max.getX() - min.getX() + 1;

        for (BlockPos removed : removedAboveMid) {
            if (random.nextDouble() >= 0.30) continue;

            int idx = (removed.getX() - min.getX()) * (max.getZ() - min.getZ() + 1) + (removed.getZ() - min.getZ());
            if (idx < 0 || idx >= rubbleHeight.length) continue;
            if (rubbleHeight[idx] >= 3) continue;

            // Find ground level below
            BlockPos.Mutable ground = new BlockPos.Mutable(removed.getX(), min.getY(), removed.getZ());
            for (int y = removed.getY() - 1; y >= min.getY(); y--) {
                ground.setY(y);
                BlockState belowState = world.getBlockState(ground);
                if (!belowState.isAir()) {
                    ground.setY(y + 1);
                    break;
                }
            }

            // Stack rubble
            int placeY = ground.getY() + rubbleHeight[idx];
            if (placeY <= max.getY()) {
                BlockPos.Mutable rubblePos = new BlockPos.Mutable(removed.getX(), placeY, removed.getZ());
                BlockState rubbleState = world.getBlockState(rubblePos);
                if (rubbleState.isAir()) {
                    setBlock(rubblePos, getRandomRubble());
                    rubbleHeight[idx]++;
                }
            }
        }

        // Pass 3: Ground-level scatter
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                // Find ground level
                for (int y = min.getY(); y <= max.getY(); y++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() && y > min.getY()) {
                        BlockState below = world.getBlockState(pos.down());
                        if (!below.isAir() && below.isOpaqueFullCube()) {
                            if (random.nextDouble() < 0.05) {
                                BlockState scatter = random.nextBoolean()
                                    ? Blocks.COBBLESTONE.getDefaultState()
                                    : Blocks.GRAVEL.getDefaultState();
                                setBlock(pos, scatter);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    // ── Phase 4: Vegetation ──────────────────────────────────────────────

    private void phaseVegetation() {
        boolean isSnowy = palette == BiomePalette.SNOWY;
        boolean isDesert = palette == BiomePalette.DESERT;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);

                    if (state.isAir()) continue;

                    Block block = state.getBlock();

                    // Top surface decorations
                    if (isTopSurface(pos)) {
                        if (isSnowy) {
                            // Snow layers on top surfaces
                            if (shouldDecay(0.25)) {
                                setBlock(pos.up(), Blocks.SNOW.getDefaultState().with(SnowBlock.LAYERS, random.nextInt(3) + 1));
                            }
                        } else if (isDesert) {
                            // Sand replacement at ground level
                            if (y <= midY && shouldDecay(0.15)) {
                                setBlock(pos, Blocks.SAND.getDefaultState());
                                // Dead bush on sand
                                if (random.nextDouble() < 0.10) {
                                    setBlock(pos.up(), Blocks.DEAD_BUSH.getDefaultState());
                                }
                            }
                        } else {
                            // Moss carpet on stone/brick surfaces
                            if (isStoneOrBrick(block) && shouldDecay(0.15)) {
                                setBlock(pos.up(), Blocks.MOSS_CARPET.getDefaultState());
                            }

                            // Grass/fern on dirt/grass blocks
                            if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK) {
                                if (random.nextDouble() < 0.10) {
                                    BlockState plant = random.nextBoolean()
                                        ? Blocks.SHORT_GRASS.getDefaultState()
                                        : Blocks.FERN.getDefaultState();
                                    setBlock(pos.up(), plant);
                                }
                            }
                        }
                    }

                    // Vine placement (non-snowy, non-desert)
                    if (!isSnowy && !isDesert && !state.isAir()) {
                        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                            BlockPos adjacent = pos.offset(dir);
                            if (!world.getBlockState(adjacent).isAir()) continue;

                            // The vine hangs on the block face that is toward air
                            if (shouldDecay(0.20)) {
                                // Vine attaches to the face opposite the air direction
                                BooleanProperty faceProp = getVineFaceProperty(dir.getOpposite());
                                if (faceProp == null) continue;

                                int vineLength = random.nextInt(6) + 3; // 3-8 blocks
                                BlockPos.Mutable vinePos = new BlockPos.Mutable();

                                for (int v = 0; v < vineLength; v++) {
                                    vinePos.set(adjacent.getX(), adjacent.getY() - v, adjacent.getZ());
                                    if (!isInBounds(vinePos)) break;
                                    BlockState vineTarget = world.getBlockState(vinePos);
                                    if (!vineTarget.isAir()) break;

                                    BlockState vineState = Blocks.VINE.getDefaultState().with(faceProp, true);
                                    setBlock(vinePos, vineState);
                                }
                                break; // Only one vine direction per block
                            }
                        }
                    }

                    // Desert: soul fire on sandstone
                    if (isDesert && isTopSurface(pos) && isSandstone(block)) {
                        if (random.nextDouble() < 0.05 * intensity) {
                            setBlock(pos.up(), Blocks.SOUL_FIRE.getDefaultState());
                        }
                    }
                }
            }
        }
    }

    // ── Phase 5: Atmosphere ──────────────────────────────────────────────

    private void phaseAtmosphere() {
        // First pass: collect light positions and remove all light sources
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    Block block = state.getBlock();

                    if (isLightSource(block)) {
                        lightPositions.add(pos.toImmutable());
                        setBlock(pos, air);
                    }
                }
            }
        }

        // Place soul lanterns at 10-15% of original light positions
        double soulLanternRate = 0.10 + random.nextDouble() * 0.05;
        for (BlockPos lightPos : lightPositions) {
            if (random.nextDouble() < soulLanternRate) {
                // Only place hanging lanterns (must have solid block above)
                BlockState above = world.getBlockState(lightPos.up());
                if (above.isOpaqueFullCube()) {
                    BlockState hangingLantern = Blocks.SOUL_LANTERN.getDefaultState()
                        .with(LanternBlock.HANGING, true);
                    setBlock(lightPos, hangingLantern);
                }
            }
        }

        // Cobwebs in enclosed spaces
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    pos.set(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir()) continue;

                    // Check: block above is solid
                    BlockState above = world.getBlockState(pos.up());
                    if (!above.isOpaqueFullCube()) continue;

                    // Count solid neighbors (horizontal + below)
                    int solidNeighbors = 0;
                    for (Direction dir : Direction.values()) {
                        if (dir == Direction.UP) continue;
                        BlockPos neighbor = pos.offset(dir);
                        BlockState neighborState = world.getBlockState(neighbor);
                        if (neighborState.isOpaqueFullCube()) {
                            solidNeighbors++;
                        }
                    }

                    if (solidNeighbors >= 2 && shouldDecay(0.25)) {
                        setBlock(pos, Blocks.COBWEB.getDefaultState());
                    }
                }
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private void setBlock(BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, StructureHelper.SET_FLAGS);
    }

    private boolean shouldDecay(double baseProbability) {
        return random.nextDouble() < baseProbability * intensity;
    }

    private boolean isInBounds(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
            && pos.getY() >= min.getY() && pos.getY() <= max.getY()
            && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    private boolean isExposed(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).isAir()) {
                return true;
            }
        }
        return false;
    }

    private boolean isTopSurface(BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return false;
        return world.getBlockState(pos.up()).isAir();
    }

    private boolean isFloating(BlockPos pos) {
        // A block is floating if there is air below AND no horizontal support
        BlockState below = world.getBlockState(pos.down());
        if (!below.isAir()) return false;

        // Check horizontal neighbors for support
        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockState neighbor = world.getBlockState(pos.offset(dir));
            if (!neighbor.isAir() && neighbor.isOpaqueFullCube()) {
                return false;
            }
        }
        return true;
    }

    private boolean isFragile(Block block) {
        return block == Blocks.TORCH
            || block == Blocks.WALL_TORCH
            || block == Blocks.SOUL_TORCH
            || block == Blocks.SOUL_WALL_TORCH
            || block == Blocks.LANTERN
            || block == Blocks.SOUL_LANTERN
            || block == Blocks.FLOWER_POT
            || isCarpet(block)
            || isBed(block)
            || isBanner(block);
        // Note: paintings and item frames are entities, not blocks.
        // We handle what we can as blocks; entity removal would need separate logic.
    }

    private boolean isGlass(Block block) {
        return block == Blocks.GLASS
            || block == Blocks.GLASS_PANE
            || block == Blocks.WHITE_STAINED_GLASS
            || block == Blocks.WHITE_STAINED_GLASS_PANE
            || block == Blocks.ORANGE_STAINED_GLASS
            || block == Blocks.ORANGE_STAINED_GLASS_PANE
            || block == Blocks.LIGHT_BLUE_STAINED_GLASS
            || block == Blocks.LIGHT_BLUE_STAINED_GLASS_PANE
            || block == Blocks.YELLOW_STAINED_GLASS
            || block == Blocks.YELLOW_STAINED_GLASS_PANE
            || block == Blocks.BROWN_STAINED_GLASS
            || block == Blocks.BROWN_STAINED_GLASS_PANE
            || block == Blocks.GRAY_STAINED_GLASS
            || block == Blocks.GRAY_STAINED_GLASS_PANE
            || block == Blocks.LIGHT_GRAY_STAINED_GLASS
            || block == Blocks.LIGHT_GRAY_STAINED_GLASS_PANE
            || block == Blocks.RED_STAINED_GLASS
            || block == Blocks.RED_STAINED_GLASS_PANE
            || block == Blocks.BLACK_STAINED_GLASS
            || block == Blocks.BLACK_STAINED_GLASS_PANE;
    }

    private boolean isDoor(Block block) {
        return block == Blocks.OAK_DOOR
            || block == Blocks.SPRUCE_DOOR
            || block == Blocks.BIRCH_DOOR
            || block == Blocks.ACACIA_DOOR
            || block == Blocks.DARK_OAK_DOOR
            || block == Blocks.JUNGLE_DOOR
            || block == Blocks.IRON_DOOR
            || block == Blocks.MANGROVE_DOOR
            || block == Blocks.CHERRY_DOOR
            || block == Blocks.BAMBOO_DOOR
            || block == Blocks.CRIMSON_DOOR
            || block == Blocks.WARPED_DOOR
            || block == Blocks.OAK_TRAPDOOR
            || block == Blocks.SPRUCE_TRAPDOOR
            || block == Blocks.BIRCH_TRAPDOOR
            || block == Blocks.ACACIA_TRAPDOOR
            || block == Blocks.DARK_OAK_TRAPDOOR
            || block == Blocks.JUNGLE_TRAPDOOR
            || block == Blocks.IRON_TRAPDOOR
            || block == Blocks.MANGROVE_TRAPDOOR
            || block == Blocks.CHERRY_TRAPDOOR
            || block == Blocks.BAMBOO_TRAPDOOR
            || block == Blocks.CRIMSON_TRAPDOOR
            || block == Blocks.WARPED_TRAPDOOR;
    }

    private boolean isCarpet(Block block) {
        return block == Blocks.WHITE_CARPET
            || block == Blocks.ORANGE_CARPET
            || block == Blocks.LIGHT_BLUE_CARPET
            || block == Blocks.YELLOW_CARPET
            || block == Blocks.RED_CARPET
            || block == Blocks.BROWN_CARPET
            || block == Blocks.GRAY_CARPET
            || block == Blocks.LIGHT_GRAY_CARPET
            || block == Blocks.BLACK_CARPET
            || block == Blocks.MOSS_CARPET;
    }

    private boolean isBed(Block block) {
        return block == Blocks.WHITE_BED
            || block == Blocks.ORANGE_BED
            || block == Blocks.LIGHT_BLUE_BED
            || block == Blocks.YELLOW_BED
            || block == Blocks.RED_BED
            || block == Blocks.BROWN_BED
            || block == Blocks.GRAY_BED
            || block == Blocks.LIGHT_GRAY_BED
            || block == Blocks.BLACK_BED;
    }

    private boolean isBanner(Block block) {
        return block == Blocks.WHITE_BANNER
            || block == Blocks.WHITE_WALL_BANNER
            || block == Blocks.ORANGE_BANNER
            || block == Blocks.ORANGE_WALL_BANNER
            || block == Blocks.LIGHT_BLUE_BANNER
            || block == Blocks.LIGHT_BLUE_WALL_BANNER
            || block == Blocks.RED_BANNER
            || block == Blocks.RED_WALL_BANNER
            || block == Blocks.BLACK_BANNER
            || block == Blocks.BLACK_WALL_BANNER
            || block == Blocks.BROWN_BANNER
            || block == Blocks.BROWN_WALL_BANNER
            || block == Blocks.GRAY_BANNER
            || block == Blocks.GRAY_WALL_BANNER;
    }

    private boolean isLightSource(Block block) {
        return block == Blocks.TORCH
            || block == Blocks.WALL_TORCH
            || block == Blocks.SOUL_TORCH
            || block == Blocks.SOUL_WALL_TORCH
            || block == Blocks.LANTERN
            || block == Blocks.SOUL_LANTERN
            || block == Blocks.GLOWSTONE;
    }

    private boolean isStoneOrBrick(Block block) {
        return block == Blocks.STONE_BRICKS
            || block == Blocks.MOSSY_STONE_BRICKS
            || block == Blocks.CRACKED_STONE_BRICKS
            || block == Blocks.COBBLESTONE
            || block == Blocks.MOSSY_COBBLESTONE
            || block == Blocks.STONE
            || block == Blocks.DEEPSLATE_TILES
            || block == Blocks.CRACKED_DEEPSLATE_TILES
            || block == Blocks.POLISHED_ANDESITE
            || block == Blocks.MUD_BRICKS;
    }

    private boolean isSandstone(Block block) {
        return block == Blocks.SANDSTONE
            || block == Blocks.CUT_SANDSTONE
            || block == Blocks.SMOOTH_SANDSTONE;
    }

    private boolean isSolidBuildingBlock(Block block) {
        return isStoneOrBrick(block)
            || isSandstone(block)
            || block == Blocks.PACKED_MUD
            || block == Blocks.PACKED_ICE
            || block == Blocks.BLUE_ICE
            || block == Blocks.TERRACOTTA
            || block == Blocks.ORANGE_TERRACOTTA
            || block == Blocks.SNOW_BLOCK;
    }

    private BooleanProperty getVineFaceProperty(Direction face) {
        return switch (face) {
            case NORTH -> VineBlock.NORTH;
            case SOUTH -> VineBlock.SOUTH;
            case EAST -> VineBlock.EAST;
            case WEST -> VineBlock.WEST;
            default -> null;
        };
    }

    private BlockState getRandomRubble() {
        int choice = random.nextInt(4);
        return switch (choice) {
            case 0 -> Blocks.COBBLESTONE.getDefaultState();
            case 1 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
            case 2 -> Blocks.GRAVEL.getDefaultState();
            case 3 -> Blocks.STONE_BRICK_SLAB.getDefaultState();
            default -> Blocks.COBBLESTONE.getDefaultState();
        };
    }
}
