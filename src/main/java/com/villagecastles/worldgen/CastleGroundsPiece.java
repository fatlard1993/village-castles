package com.villagecastles.worldgen;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.BiomePalette;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The castle's grounds: everything that has to happen to the world <em>around</em> the
 * castle template rather than inside it.
 *
 * <p>Two jobs, both of which have to run after the castle's own blocks exist:
 *
 * <ol>
 *   <li><b>Underfill.</b> A castle is a RIGID jigsaw element: it does not terrain-match, so
 *       anchoring it at the village bounding-box edge can leave it hanging over a drop with
 *       open air under the footprint. Every footprint column gets filled down to the first
 *       solid ground, bounded by {@link #MAX_UNDERFILL_DEPTH} so a castle on the lip of a
 *       ravine plugs its own footprint instead of the whole ravine.</li>
 *   <li><b>Garrison.</b> The 15 castle templates contain beds and workstations but zero
 *       entities, and nothing moves in on its own: vanilla POI acquisition
 *       ({@code AcquirePoi.SCAN_RANGE} = 48, measured from the villager's own
 *       {@code blockPosition()}, and needing a path the ground navigator can actually walk)
 *       never reaches castle beds from inside the village. So the castle seeds its own
 *       residents next to its own beds.</li>
 * </ol>
 *
 * <p><b>Why this is a StructurePiece and not a chunk-load hook.</b> {@code postProcess} runs
 * during the chunk's one and only worldgen pass, so the garrison cannot double-spawn on a
 * chunk unload/reload cycle: reloading a chunk reads blocks and entities from disk and never
 * re-runs structure placement. No persisted "already spawned" marker is needed.
 *
 * <p><b>Why it is added to the collector after the castle piece.</b>
 * {@code StructureStart.placeInChunk} iterates {@code PiecesContainer.pieces()} in list order
 * and calls {@code postProcess} on each piece intersecting the chunk (verified against the
 * 26.3-snapshot-8 bytecode), so a piece appended after the castle sees the castle's blocks
 * already placed in the current chunk.
 *
 * <p><b>Garrison headcount is bounded without cross-chunk state.</b> {@code postProcess} is
 * called once per chunk and each call spawns at most one villager: the one bed head in this
 * chunk with the lowest (y, x, z). Chunk-local and position-derived, so it is deterministic
 * and stays bounded however the pieces are serialized, re-read, or visited. In practice the
 * bed clusters in these templates land in 1-6 chunks, so a castle seeds 1-6 residents and
 * breeding fills the remaining beds from there.
 */
public class CastleGroundsPiece extends StructurePiece {

    /** Deepest a footprint column will reach for solid ground before giving up. */
    private static final int MAX_UNDERFILL_DEPTH = 24;

    /**
     * How far above the castle's base plane to look for the block that column supports.
     * Several templates leave their lowest course(s) empty, so the base plane alone is not a
     * reliable footprint test.
     */
    private static final int BASE_SEARCH_HEIGHT = 8;

    /**
     * Villagers seeded per chunk of the castle that contains beds. Two, not one, so even the
     * smallest castle (plains/castle_small: 4 beds, all in one chunk) starts with a breeding
     * pair and can fill its remaining beds on its own.
     */
    private static final int GARRISON_PER_CHUNK = 2;

    private static final String TAG_BIOME = "VCBiome";

    private final String biomeId;

    public CastleGroundsPiece(BoundingBox castleBox, String biomeId) {
        super(CastleStructureRegistration.CASTLE_GROUNDS_PIECE_TYPE, 1, castleBox);
        this.biomeId = biomeId;
    }

    public CastleGroundsPiece(CompoundTag tag) {
        super(CastleStructureRegistration.CASTLE_GROUNDS_PIECE_TYPE, tag);
        this.biomeId = tag.getStringOr(TAG_BIOME, "plains");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString(TAG_BIOME, this.biomeId);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos pivot) {
        try {
            underfill(level, chunkBox);
            spawnGarrison(level, chunkBox, random);
        } catch (Exception e) {
            // Never let castle grounds work abort the rest of the village's placement.
            VillageCastles.LOGGER.error("Castle grounds pass failed at {}: {}", chunkPos, e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ underfill

    private void underfill(WorldGenLevel level, BoundingBox chunkBox) {
        BoundingBox castle = this.boundingBox;
        int x0 = Math.max(castle.minX(), chunkBox.minX());
        int x1 = Math.min(castle.maxX(), chunkBox.maxX());
        int z0 = Math.max(castle.minZ(), chunkBox.minZ());
        int z1 = Math.min(castle.maxZ(), chunkBox.maxZ());
        int baseY = castle.minY();
        int worldFloor = level.getMinY();

        BlockState fallback = BiomePalette.foundationStateFor(this.biomeId);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int filled = 0;

        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                // Only columns the castle actually stands on. Castle footprints are not solid
                // squares (mottes, courtyards, moats), so filling the whole box would build a
                // pedestal under empty air.
                //
                // The search window matters: several templates start above their own box
                // floor (measured solid fraction of local layer 0: desert/large 0.00,
                // savanna/large 0.00, plains/large 0.95, snowy/medium 1.00), so testing only
                // the base plane would leave the ones with a hollow first course unsupported.
                BlockState footprint = null;
                boolean fluidFootprint = false;
                for (int up = 0; up <= BASE_SEARCH_HEIGHT; up++) {
                    int y = baseY + up;
                    if (y > castle.maxY()) break;
                    cursor.set(x, y, z);
                    BlockState candidate = level.getBlockState(cursor);
                    if (candidate.isAir()) continue;
                    // Moat water needs a bed as much as a wall needs a footing: without this
                    // the moat hangs in the air over the drop. Measured on a score-16 plains
                    // site: 87 footprint columns still open underneath, all of them moat.
                    if (!candidate.getFluidState().isEmpty()) {
                        footprint = candidate;
                        fluidFootprint = true;
                        break;
                    }
                    // A sturdy DOWN face is the test for "something is resting here", which
                    // keeps slabs and stairs in and torches and banners out.
                    if (candidate.isFaceSturdy(level, cursor, Direction.DOWN)) {
                        footprint = candidate;
                        break;
                    }
                }
                if (footprint == null) continue;

                // Mirror the block the castle put down so the skirt reads as that castle's own
                // foundation; fall back to the biome palette when the bottom course is a slab,
                // stair, other non-cube, or water.
                BlockState fill = !fluidFootprint && footprint.isCollisionShapeFullBlock(level, cursor)
                    ? footprint : fallback;

                // Filling ALWAYS starts below the castle's base plane, never inside it. The
                // base plane sits at the highest terrain sample under the footprint, so on flat
                // ground this loop stops on its first step and a gateway arch or a courtyard
                // keeps its floor instead of being plugged solid.
                for (int depth = 1; depth <= MAX_UNDERFILL_DEPTH; depth++) {
                    int y = baseY - depth;
                    if (y <= worldFloor) break;
                    cursor.set(x, y, z);
                    if (!chunkBox.isInside(cursor)) break;
                    BlockState below = level.getBlockState(cursor);
                    // Stop at the first real ground. Air, water and lava are all filled through
                    // so the castle gets a plinth rather than a moat it never asked for.
                    if (below.getFluidState().isEmpty() && below.isFaceSturdy(level, cursor, Direction.UP)) break;
                    level.setBlock(cursor, fill, Block.UPDATE_CLIENTS);
                    filled++;
                }
            }
        }

        if (filled > 0) {
            VillageCastles.LOGGER.debug("Underfilled {} blocks under castle {}", filled, castle);
        }
    }

    // ------------------------------------------------------------------ garrison

    private void spawnGarrison(WorldGenLevel level, BoundingBox chunkBox, RandomSource random) {
        for (BlockPos bed : findBedHeads(level, chunkBox, GARRISON_PER_CHUNK)) {
            BlockPos spot = findStandingSpot(level, bed, chunkBox);
            if (spot == null) {
                // Castle bedroom is flooded, buried or clipped by terrain: leave it empty
                // rather than dropping a villager into a wall.
                VillageCastles.LOGGER.debug("No standable spot beside castle bed {}", bed);
                continue;
            }

            Villager villager = EntityTypes.VILLAGER.create(level.getLevel(), EntitySpawnReason.STRUCTURE);
            if (villager == null) return;

            villager.snapTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D,
                random.nextFloat() * 360.0F, 0.0F);
            // Leaves the villager unemployed on purpose: it then claims whichever of the
            // castle's own workstations is nearest and reachable, so a castle's professions
            // match the workstations that template actually contains instead of a hardcoded
            // list. Verified in-world: the first garrison villager generated came out a
            // cartographer, off the cartography table plains/castle_small actually has.
            villager.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), EntitySpawnReason.STRUCTURE, null);
            level.addFreshEntityWithPassengers(villager);

            VillageCastles.LOGGER.info("Castle garrison villager at {} beside bed {}",
                spot.toShortString(), bed.toShortString());
        }
    }

    /**
     * Up to {@code limit} bed heads in this chunk, in (y, x, z) order. Position-derived and
     * chunk-local, which is what keeps the headcount deterministic and bounded.
     */
    private List<BlockPos> findBedHeads(WorldGenLevel level, BoundingBox chunkBox, int limit) {
        BoundingBox castle = this.boundingBox;
        int x0 = Math.max(castle.minX(), chunkBox.minX());
        int x1 = Math.min(castle.maxX(), chunkBox.maxX());
        int z0 = Math.max(castle.minZ(), chunkBox.minZ());
        int z1 = Math.min(castle.maxZ(), chunkBox.maxZ());

        List<BlockPos> found = new ArrayList<>(limit);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = castle.minY(); y <= castle.maxY(); y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    // PoiTypes.HOME is registered over the bed states whose PART is HEAD, so
                    // only a head is a claimable home.
                    if (state.getBlock() instanceof AbstractBedBlock
                        && state.getValue(AbstractBedBlock.PART) == BedPart.HEAD) {
                        found.add(cursor.immutable());
                        if (found.size() >= limit) return found;
                    }
                }
            }
        }
        return found;
    }

    private BlockPos findStandingSpot(WorldGenLevel level, BlockPos bed, BoundingBox chunkBox) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = bed.relative(dir);
            if (isStandable(level, candidate, chunkBox)) return candidate;
        }
        // Last resort: in the bed itself, which is by definition supported and unobstructed
        // (a villager sleeps there), and the villager steps out on its first tick. Worth
        // having: an earlier revision required bare air on all four sides and a sturdy floor,
        // and every bedroom in savanna/castle_medium failed it (measured 4 beds found, 4
        // "no standable spot") because the bunks are flanked by carpet and wall.
        return isClear(level, bed, chunkBox) ? bed : null;
    }

    private boolean isStandable(WorldGenLevel level, BlockPos pos, BoundingBox chunkBox) {
        BlockPos under = pos.below();
        if (!chunkBox.isInside(under)) return false;
        BlockState floor = level.getBlockState(under);
        if (!floor.getFluidState().isEmpty() || !floor.isFaceSturdy(level, under, Direction.UP)) return false;
        return isClear(level, pos, chunkBox);
    }

    /** Feet and head space a villager will not suffocate in. */
    private boolean isClear(WorldGenLevel level, BlockPos pos, BoundingBox chunkBox) {
        BlockPos head = pos.above();
        // Everything read has to be inside the chunk currently being generated: reaching into
        // a neighbouring chunk during worldgen either deadlocks or reads pre-placement blocks.
        if (!chunkBox.isInside(pos) || !chunkBox.isInside(head)) return false;
        return !level.getBlockState(pos).isSuffocating(level, pos)
            && !level.getBlockState(head).isSuffocating(level, head);
    }
}
