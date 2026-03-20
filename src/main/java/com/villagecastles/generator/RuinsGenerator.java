package com.villagecastles.generator;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.CastleGenerator.CastleBounds;
import com.villagecastles.generator.CastleGenerator.CastleSize;
import com.villagecastles.generator.decay.DecayEngine;
import com.villagecastles.util.StructureHelper;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates ruined castle variants for wilderness exploration.
 *
 * These are NOT village structures -- they spawn in the wild as dark, dangerous,
 * loot-rich dungeons. The generation pipeline:
 *   1. Build a complete castle via CastleGenerator
 *   2. Apply DecayEngine to destroy portions of it
 *   3. Seed the ruins with hidden treasure rooms, spawners, and ground loot
 *
 * Each biome gets exactly 2 ruins variants at different scales,
 * chosen for architectural interest at that size.
 */
public class RuinsGenerator {

    // ---------------------------------------------------------------
    // Ruins Variant Enum
    // ---------------------------------------------------------------

    /**
     * Defines every ruins variant in the game. Each biome has two variants
     * at different castle sizes with tuned decay intensities.
     *
     * Higher decayIntensity = more destruction (0.0 = pristine, 1.0 = rubble).
     */
    public enum RuinsVariant {
        PLAINS_1(BiomePalette.PLAINS,   CastleSize.MEDIUM, 0.65, "Ruined Motte Fort"),
        PLAINS_2(BiomePalette.PLAINS,   CastleSize.LARGE,  0.55, "Ruined Moat Castle"),
        DESERT_1(BiomePalette.DESERT,   CastleSize.MEDIUM, 0.60, "Ruined Desert Fort"),
        DESERT_2(BiomePalette.DESERT,   CastleSize.LARGE,  0.50, "Ruined Pyramid"),
        SAVANNA_1(BiomePalette.SAVANNA, CastleSize.SMALL,  0.70, "Ruined Watchtower"),
        SAVANNA_2(BiomePalette.SAVANNA, CastleSize.MEDIUM, 0.60, "Ruined Monument"),
        TAIGA_1(BiomePalette.TAIGA,     CastleSize.MEDIUM, 0.65, "Ruined Norse Settlement"),
        TAIGA_2(BiomePalette.TAIGA,     CastleSize.LARGE,  0.55, "Ruined Norse Fortress"),
        SNOWY_1(BiomePalette.SNOWY,     CastleSize.SMALL,  0.70, "Collapsed Igloo"),
        SNOWY_2(BiomePalette.SNOWY,     CastleSize.MEDIUM, 0.60, "Frozen Ruins");

        public final BiomePalette palette;
        public final CastleSize baseSize;
        public final double decayIntensity;
        public final String displayName;

        RuinsVariant(BiomePalette palette, CastleSize baseSize, double decayIntensity, String displayName) {
            this.palette = palette;
            this.baseSize = baseSize;
            this.decayIntensity = decayIntensity;
            this.displayName = displayName;
        }

        /**
         * Returns the two ruins variants for a given biome palette.
         */
        public static List<RuinsVariant> getVariantsForBiome(BiomePalette palette) {
            List<RuinsVariant> result = new ArrayList<>();
            for (RuinsVariant variant : values()) {
                if (variant.palette == palette) {
                    result.add(variant);
                }
            }
            return result;
        }

        /**
         * Returns the structure path for worldgen template pools.
         * Format: "{biome}/castle_ruins_{index}" where index is 1-based within the biome.
         */
        public String getStructurePath() {
            List<RuinsVariant> biomeVariants = getVariantsForBiome(this.palette);
            int index = biomeVariants.indexOf(this) + 1;
            return palette.id + "/castle_ruins_" + index;
        }
    }

    // ---------------------------------------------------------------
    // Instance fields
    // ---------------------------------------------------------------

    private final RuinsVariant variant;
    private final long seed;
    private final Random random;

    // Spawner mob type per biome
    private static final EntityType<?> PLAINS_MOB = EntityType.ZOMBIE;
    private static final EntityType<?> DESERT_MOB = EntityType.HUSK;
    private static final EntityType<?> SAVANNA_MOB = EntityType.SPIDER;
    private static final EntityType<?> TAIGA_MOB = EntityType.SKELETON;
    private static final EntityType<?> SNOWY_MOB = EntityType.STRAY;

    // ---------------------------------------------------------------
    // Constructor
    // ---------------------------------------------------------------

    public RuinsGenerator(RuinsVariant variant, long seed) {
        this.variant = variant;
        this.seed = seed;
        this.random = new Random(seed);
    }

    // ---------------------------------------------------------------
    // Main Generation Pipeline
    // ---------------------------------------------------------------

    /**
     * Generate a ruined castle at the given position.
     *
     * Pipeline:
     *   1. Build the base castle structure
     *   2. Apply decay (remove blocks, crack walls, add vegetation)
     *   3. Add hidden treasure rooms
     *   4. Place mob spawners in dark enclosed areas
     *   5. Scatter ground-level loot for surface explorers
     *
     * @param world  The server world to generate in
     * @param center The center position of the ruins
     * @return CastleBounds describing the generated structure's extents
     */
    public CastleBounds generate(ServerWorld world, BlockPos center) {
        VillageCastles.LOGGER.info("Generating ruins: {} at {}", variant.displayName, center.toShortString());

        int radius = variant.baseSize.diameter / 2;

        // Wrap generation in forced chunks to prevent partial generation
        final CastleBounds[] boundsHolder = new CastleBounds[1];

        StructureHelper.withForcedChunks(world, center, radius + 8, () -> {
            // Step 1: Generate the base castle
            CastleGenerator castleGen = new CastleGenerator(variant.palette, seed, variant.baseSize);
            CastleBounds bounds = castleGen.generate(world, center);

            // Step 2: Apply decay to the entire structure
            DecayEngine decay = new DecayEngine(
                world, bounds.min, bounds.max, random, variant.decayIntensity, variant.palette
            );
            decay.apply();

            // Step 3: Add hidden treasure rooms inside thick walls / foundations
            addSecrets(world, bounds);

            // Step 4: Place mob spawners in dark enclosed spaces
            addSpawners(world, bounds);

            // Step 5: Place visible loot on the ground floor
            addGroundLevelLoot(world, bounds, center);

            boundsHolder[0] = bounds;
        });

        VillageCastles.LOGGER.info("Ruins generation complete: {}", variant.displayName);
        return boundsHolder[0];
    }

    // ---------------------------------------------------------------
    // Secret Rooms
    // ---------------------------------------------------------------

    /**
     * Finds solid 3x3x3 regions inside the structure and hollows them out
     * into hidden treasure rooms with loot, cobwebs, and a cracked-block hint.
     *
     * Searches from the bottom of the structure upward (foundations are thickest).
     * Places 1-3 secret rooms depending on available space.
     */
    private void addSecrets(ServerWorld world, CastleBounds bounds) {
        int targetCount = 1 + random.nextInt(3); // 1-3 secret rooms
        int placed = 0;

        // Loot table depends on biome
        RegistryKey<LootTable> lootTable = getSecretLootTable();

        BlockPos.Mutable probe = new BlockPos.Mutable();
        List<BlockPos> usedPositions = new ArrayList<>();

        // Scan the structure volume for solid 3x3x3 pockets
        int minX = bounds.min.getX() + 2;
        int minY = bounds.min.getY() + 1;
        int minZ = bounds.min.getZ() + 2;
        int maxX = bounds.max.getX() - 4; // Leave room for the 3-wide room
        int maxY = bounds.max.getY() - 3;
        int maxZ = bounds.max.getZ() - 4;

        // Shuffle search order by starting at random offsets
        for (int attempt = 0; attempt < 200 && placed < targetCount; attempt++) {
            int x = minX + random.nextInt(Math.max(1, maxX - minX));
            int y = minY + random.nextInt(Math.max(1, maxY - minY));
            int z = minZ + random.nextInt(Math.max(1, maxZ - minZ));
            probe.set(x, y, z);

            // Skip if too close to an existing secret room
            if (isTooCloseToAny(probe, usedPositions, 8)) continue;

            // Check that the 5x4x5 region (room + walls) is fully solid
            if (!isRegionSolid(world, x - 1, y - 1, z - 1, x + 3, y + 3, z + 3)) continue;

            // Hollow out a 3x3x2 room (3 wide, 3 deep, 2 tall)
            carveSecretRoom(world, x, y, z, lootTable);
            usedPositions.add(probe.toImmutable());
            placed++;
        }

        if (placed > 0) {
            VillageCastles.LOGGER.debug("Placed {} secret room(s) in {}", placed, variant.displayName);
        }
    }

    /**
     * Carves a 3x3x2 hidden room at the given position.
     * Places a chest, cobwebs, and leaves a cracked block as a visual hint
     * on the outer shell so observant players can find it.
     */
    private void carveSecretRoom(ServerWorld world, int x, int y, int z,
                                  RegistryKey<LootTable> lootTable) {
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState cobweb = Blocks.COBWEB.getDefaultState();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Clear the interior: 3x2x3 (x,z = 3 wide, y = 2 tall)
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                for (int dy = 0; dy < 2; dy++) {
                    pos.set(x + dx, y + dy, z + dz);
                    world.setBlockState(pos, air, StructureHelper.SET_FLAGS);
                }
            }
        }

        // Place cobwebs in upper corners
        world.setBlockState(new BlockPos(x, y + 1, z), cobweb, StructureHelper.SET_FLAGS);
        world.setBlockState(new BlockPos(x + 2, y + 1, z + 2), cobweb, StructureHelper.SET_FLAGS);

        // Place treasure chest against the back wall
        StructureHelper.placeChest(world, new BlockPos(x + 1, y, z + 2), Direction.NORTH, lootTable);

        // Leave a cracked block hint on the exterior (one wall block adjacent to the room).
        // Try each cardinal direction to find a solid neighbor we can crack.
        BlockState crackedHint = getCrackedHintBlock();
        int[][] hintOffsets = {{-1, 0, 1}, {3, 0, 1}, {1, 0, -1}, {1, 0, 3}};
        for (int[] offset : hintOffsets) {
            pos.set(x + offset[0], y + offset[1], z + offset[2]);
            if (!world.getBlockState(pos).isAir()) {
                world.setBlockState(pos, crackedHint, StructureHelper.SET_FLAGS);
                break;
            }
        }
    }

    // ---------------------------------------------------------------
    // Mob Spawners
    // ---------------------------------------------------------------

    /**
     * Places 2-4 mob spawners in enclosed, dark areas of the ruins.
     * An "enclosed space" is defined as: solid floor below, solid ceiling above,
     * and walls on at least 2 sides. These are naturally dark and dangerous.
     */
    private void addSpawners(ServerWorld world, CastleBounds bounds) {
        int targetCount = 2 + random.nextInt(3); // 2-4 spawners
        int placed = 0;

        EntityType<?> mobType = getSpawnerMobType();
        BlockState cobweb = Blocks.COBWEB.getDefaultState();

        BlockPos.Mutable probe = new BlockPos.Mutable();
        List<BlockPos> usedPositions = new ArrayList<>();

        int minX = bounds.min.getX() + 1;
        int minY = bounds.min.getY() + 1;
        int minZ = bounds.min.getZ() + 1;
        int maxX = bounds.max.getX() - 1;
        int maxY = bounds.max.getY() - 2;
        int maxZ = bounds.max.getZ() - 1;

        for (int attempt = 0; attempt < 300 && placed < targetCount; attempt++) {
            int x = minX + random.nextInt(Math.max(1, maxX - minX));
            int y = minY + random.nextInt(Math.max(1, maxY - minY));
            int z = minZ + random.nextInt(Math.max(1, maxZ - minZ));
            probe.set(x, y, z);

            // Skip if too close to another spawner (prevent clustering)
            if (isTooCloseToAny(probe, usedPositions, 10)) continue;

            // The spawner position must be air
            if (!world.getBlockState(probe).isAir()) continue;

            // Must have solid floor below
            if (world.getBlockState(probe.down()).isAir()) continue;

            // Must have solid ceiling above (within 3 blocks)
            boolean hasCeiling = false;
            for (int dy = 1; dy <= 3; dy++) {
                if (!world.getBlockState(probe.up(dy)).isAir()) {
                    hasCeiling = true;
                    break;
                }
            }
            if (!hasCeiling) continue;

            // Must have walls on at least 2 of 4 cardinal sides (within 2 blocks)
            int wallCount = 0;
            for (Direction dir : Direction.Type.HORIZONTAL) {
                for (int dist = 1; dist <= 2; dist++) {
                    BlockPos neighbor = probe.offset(dir, dist);
                    if (!world.getBlockState(neighbor).isAir()) {
                        wallCount++;
                        break;
                    }
                }
            }
            if (wallCount < 2) continue;

            // First spawner is always a zombie villager — the inhabitants didn't all leave
            EntityType<?> thisSpawnerMob = (placed == 0) ? EntityType.ZOMBIE_VILLAGER : mobType;
            placeSpawner(world, probe.toImmutable(), thisSpawnerMob);

            // Surround with cobwebs for atmosphere
            for (Direction dir : Direction.Type.HORIZONTAL) {
                BlockPos webPos = probe.offset(dir);
                if (world.getBlockState(webPos).isAir() && random.nextFloat() < 0.5f) {
                    world.setBlockState(webPos, cobweb, StructureHelper.SET_FLAGS);
                }
            }

            usedPositions.add(probe.toImmutable());
            placed++;
        }

        if (placed > 0) {
            VillageCastles.LOGGER.debug("Placed {} spawner(s) in {}", placed, variant.displayName);
        }
    }

    /**
     * Places a mob spawner block and configures its entity type.
     */
    private void placeSpawner(ServerWorld world, BlockPos pos, EntityType<?> entityType) {
        world.setBlockState(pos, Blocks.SPAWNER.getDefaultState(), StructureHelper.SET_FLAGS);
        if (world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner) {
            spawner.setEntityType(entityType, world.getRandom());
        }
    }

    // ---------------------------------------------------------------
    // Ground-Level Loot
    // ---------------------------------------------------------------

    /**
     * Places 2-4 chests at ground level in accessible areas of the ruins.
     * These are the "easy finds" -- reward for exploring the surface before
     * venturing deeper into spawner-guarded rooms and secret chambers.
     */
    private void addGroundLevelLoot(ServerWorld world, CastleBounds bounds, BlockPos center) {
        int targetCount = 2 + random.nextInt(3); // 2-4 chests
        int placed = 0;

        BlockPos.Mutable probe = new BlockPos.Mutable();
        List<BlockPos> usedPositions = new ArrayList<>();

        // Ground level is approximately the center Y
        int groundY = center.getY();

        int minX = bounds.min.getX() + 2;
        int maxX = bounds.max.getX() - 2;
        int minZ = bounds.min.getZ() + 2;
        int maxZ = bounds.max.getZ() - 2;

        for (int attempt = 0; attempt < 200 && placed < targetCount; attempt++) {
            int x = minX + random.nextInt(Math.max(1, maxX - minX));
            int z = minZ + random.nextInt(Math.max(1, maxZ - minZ));

            // Search a few Y levels around ground level for a valid spot
            boolean foundSpot = false;
            for (int yOffset = -2; yOffset <= 2; yOffset++) {
                int y = groundY + yOffset;
                probe.set(x, y, z);

                // Need air at chest position and above it, solid floor below
                if (!world.getBlockState(probe).isAir()) continue;
                if (!world.getBlockState(probe.up()).isAir()) continue;
                if (world.getBlockState(probe.down()).isAir()) continue;

                // Skip if too close to another chest
                if (isTooCloseToAny(probe, usedPositions, 6)) break;

                // Pick a random facing direction
                Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                Direction facing = horizontals[random.nextInt(4)];

                StructureHelper.placeChest(world, probe.toImmutable(), facing,
                    LootTables.SIMPLE_DUNGEON_CHEST);

                usedPositions.add(probe.toImmutable());
                placed++;
                foundSpot = true;
                break;
            }
        }

        if (placed > 0) {
            VillageCastles.LOGGER.debug("Placed {} ground loot chest(s) in {}", placed, variant.displayName);
        }
    }

    // ---------------------------------------------------------------
    // Biome-Specific Helpers
    // ---------------------------------------------------------------

    /**
     * Returns the appropriate loot table for secret rooms based on biome.
     */
    private RegistryKey<LootTable> getSecretLootTable() {
        return switch (variant.palette) {
            case DESERT -> LootTables.DESERT_PYRAMID_CHEST;
            case SNOWY -> LootTables.IGLOO_CHEST_CHEST;
            default -> LootTables.SIMPLE_DUNGEON_CHEST; // Plains, Taiga, Savanna
        };
    }

    /**
     * Returns the mob type to use for spawners based on biome.
     */
    private EntityType<?> getSpawnerMobType() {
        return switch (variant.palette) {
            case PLAINS -> PLAINS_MOB;
            case DESERT -> DESERT_MOB;
            case SAVANNA -> SAVANNA_MOB;
            case TAIGA -> TAIGA_MOB;
            case SNOWY -> SNOWY_MOB;
        };
    }

    /**
     * Returns a "cracked" block appropriate for the biome palette.
     * This is the visual hint placed on the exterior of secret rooms
     * so observant players can find them.
     */
    private BlockState getCrackedHintBlock() {
        return switch (variant.palette) {
            case PLAINS -> Blocks.CRACKED_STONE_BRICKS.getDefaultState();
            case DESERT -> Blocks.CHISELED_SANDSTONE.getDefaultState();
            case SAVANNA -> Blocks.CRACKED_STONE_BRICKS.getDefaultState();
            case TAIGA -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
            case SNOWY -> Blocks.CRACKED_STONE_BRICKS.getDefaultState();
        };
    }

    // ---------------------------------------------------------------
    // Geometry Helpers
    // ---------------------------------------------------------------

    /**
     * Checks whether an entire region is solid (non-air) blocks.
     * Used to find thick walls and foundations suitable for secret rooms.
     */
    private boolean isRegionSolid(ServerWorld world, int x1, int y1, int z1, int x2, int y2, int z2) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                for (int z = z1; z <= z2; z++) {
                    pos.set(x, y, z);
                    if (world.getBlockState(pos).isAir()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Returns true if the given position is within minDistance of any position in the list.
     * Used to prevent spawners, chests, and secret rooms from clustering.
     */
    private boolean isTooCloseToAny(BlockPos pos, List<BlockPos> positions, int minDistance) {
        int distSq = minDistance * minDistance;
        for (BlockPos other : positions) {
            int dx = pos.getX() - other.getX();
            int dy = pos.getY() - other.getY();
            int dz = pos.getZ() - other.getZ();
            if (dx * dx + dy * dy + dz * dz < distSq) {
                return true;
            }
        }
        return false;
    }
}
