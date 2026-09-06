package com.villagecastles.worldgen;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.ancient.AncientCastleDesigner;
import com.villagecastles.generator.ancient.Condition;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.Landform;
import com.villagecastles.generator.ancient.LandformSurvey;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.util.StructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Map;

/**
 * One ancient castle. Wider than a chunk, so {@link #postProcess} runs once per overlapping chunk
 * and may only write inside the chunk it was handed; the whole castle is therefore drawn into a
 * {@link Plan} from serialized state - seed, site, condition, tint, and the terrain field sampled
 * at site selection - and each chunk writes the subset that lands inside it.
 *
 * <p>The plan cache is volatile because chunk workers run in parallel: two threads racing here
 * waste a build, which is harmless, but a half-filled plan published early would silently drop
 * blocks.
 */
public class AncientCastlePiece extends StructurePiece {

    private static final String TAG_SEED = "Seed";
    private static final String TAG_LANDFORM = "Landform";
    private static final String TAG_CONDITION = "Condition";
    private static final String TAG_TINT = "Tint";
    private static final String TAG_FACING = "Facing";
    private static final String TAG_ORIGIN_X = "OriginX";
    private static final String TAG_ORIGIN_Y = "OriginY";
    private static final String TAG_ORIGIN_Z = "OriginZ";
    private static final String TAG_RADIUS = "Radius";
    private static final String TAG_FIELD = "Field";

    /**
     * Slack beyond the terrain field's own skirt: jetties, breakwaters, and rubble are sited on
     * surveyed ground but drawn outward from there, and a piece that writes outside its bounding
     * box writes into chunks that were never told to call it.
     */
    private static final int OVERHANG = 10;
    /** Vertical budget over the highest terrain sample; the tallest tower must fit under it. */
    private static final int TOP_BUDGET = 44;
    private static final int BOTTOM_BUDGET = 10;

    private final Landform landform;
    private final Condition condition;
    private final String tint;
    private final long shapeSeed;
    private final BlockPos origin;
    private final int radius;
    private final Direction facing;
    private final HeightField field;

    private volatile Plan plan;

    public AncientCastlePiece(LandformSurvey.Site site, Condition condition, String tint,
                              HeightField field, long shapeSeed) {
        super(CastleStructureRegistration.ANCIENT_CASTLE_PIECE_TYPE, 0,
            bounds(site.anchor(), site.radius(), field));
        this.landform = site.landform();
        this.condition = condition;
        this.tint = tint;
        this.shapeSeed = shapeSeed;
        this.origin = site.anchor();
        this.radius = site.radius();
        this.facing = site.facing();
        this.field = field;
    }

    public AncientCastlePiece(CompoundTag tag) {
        super(CastleStructureRegistration.ANCIENT_CASTLE_PIECE_TYPE, tag);
        this.landform = Landform.byId(tag.getStringOr(TAG_LANDFORM, Landform.HILLTOP.id()));
        this.condition = Condition.byId(tag.getStringOr(TAG_CONDITION, Condition.CRUMBLING.id()));
        this.tint = tag.getStringOr(TAG_TINT, "temperate");
        this.shapeSeed = tag.getLongOr(TAG_SEED, 0L);
        this.origin = new BlockPos(
            tag.getIntOr(TAG_ORIGIN_X, boundingBox.getCenter().getX()),
            tag.getIntOr(TAG_ORIGIN_Y, boundingBox.getCenter().getY()),
            tag.getIntOr(TAG_ORIGIN_Z, boundingBox.getCenter().getZ()));
        this.radius = tag.getIntOr(TAG_RADIUS, landform.radius);
        this.facing = Direction.from2DDataValue(tag.getIntOr(TAG_FACING, 0));
        this.field = HeightField.fromTag(tag.getCompoundOrEmpty(TAG_FIELD));
    }

    private static BoundingBox bounds(BlockPos origin, int radius, HeightField field) {
        int reach = radius + LandformSurvey.FIELD_SKIRT + OVERHANG;
        return new BoundingBox(
            origin.getX() - reach,
            field.minY() - BOTTOM_BUDGET,
            origin.getZ() - reach,
            origin.getX() + reach,
            field.maxY() + TOP_BUDGET,
            origin.getZ() + reach);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString(TAG_LANDFORM, landform.id());
        tag.putString(TAG_CONDITION, condition.id());
        tag.putString(TAG_TINT, tint);
        tag.putLong(TAG_SEED, shapeSeed);
        tag.putInt(TAG_ORIGIN_X, origin.getX());
        tag.putInt(TAG_ORIGIN_Y, origin.getY());
        tag.putInt(TAG_ORIGIN_Z, origin.getZ());
        tag.putInt(TAG_RADIUS, radius);
        tag.putInt(TAG_FACING, facing.get2DDataValue());
        tag.put(TAG_FIELD, field.toTag());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot) {
        try {
            place(level, plan(), chunkBox, shapeSeed, random);
        } catch (Exception e) {
            // One bad chunk of a castle beats aborting the chunk's whole decoration pass.
            VillageCastles.LOGGER.error("Ancient castle placement failed at {}", chunkPos, e);
        }
    }

    private Plan plan() {
        Plan cached = plan;
        if (cached == null) {
            cached = AncientCastleDesigner.draw(
                new LandformSurvey.Site(landform, origin, radius, facing),
                condition, field, tint, shapeSeed);
            plan = cached;
        }
        return cached;
    }

    /**
     * Writes one plan through one clip box. Shared verbatim by worldgen (per-chunk box) and the
     * debug command (whole-structure box): the command has to build what worldgen builds or it is
     * not worth testing with.
     */
    public static void place(WorldGenLevel level, Plan plan, BoundingBox clipBox, long shapeSeed,
                             RandomSource random) {
        for (Map.Entry<BlockPos, BlockState> entry : plan.blocks().entrySet()) {
            BlockPos pos = entry.getKey();
            if (!clipBox.isInside(pos)) continue;
            level.setBlock(pos, entry.getValue(), StructureHelper.SET_FLAGS);
        }

        for (Plan.Chest chest : plan.chests()) {
            BlockPos pos = chest.pos();
            if (!clipBox.isInside(pos)) continue;
            // A chest with anything solid over it cannot be opened; clearing the lid after every
            // block the plan had to say means no drawing pass can bury one.
            BlockPos lid = pos.above();
            if (clipBox.isInside(lid)) {
                level.setBlock(lid, Blocks.AIR.defaultBlockState(), StructureHelper.SET_FLAGS);
            }
            level.setBlock(pos, Blocks.CHEST.defaultBlockState()
                .setValue(ChestBlock.FACING, chest.facing()), StructureHelper.SET_FLAGS);
            if (level.getBlockEntity(pos) instanceof ChestBlockEntity container) {
                // Seeded off position and castle rather than the world's RNG, so a chest rolls
                // the same contents whichever order its chunk happened to generate in.
                container.setLootTable(chest.table(), shapeSeed ^ pos.asLong());
            }
        }

        for (Plan.Spawner spawner : plan.spawners()) {
            BlockPos pos = spawner.pos();
            if (!clipBox.isInside(pos)) continue;
            level.setBlock(pos, Blocks.SPAWNER.defaultBlockState(), StructureHelper.SET_FLAGS);
            // A spawner with no block entity behind it is an empty cage that spawns pigs.
            if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity cage) {
                cage.setEntityId(spawner.entity(), random);
            }
        }

        for (Plan.Dig dig : plan.digs()) {
            BlockPos pos = dig.pos();
            if (!clipBox.isInside(pos)) continue;
            level.setBlock(pos, dig.block(), StructureHelper.SET_FLAGS);
            if (level.getBlockEntity(pos) instanceof BrushableBlockEntity site) {
                site.setLootTable(dig.table(), shapeSeed ^ pos.asLong());
            }
        }
    }
}
