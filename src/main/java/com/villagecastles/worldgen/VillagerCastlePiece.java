package com.villagecastles.worldgen;

import com.villagecastles.VillageCastles;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.LandformSurvey;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.generator.villager.VillagerCastleDesigner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * One villager castle, attached to a village's structure start by the attachment mixin. The same
 * plan discipline as {@link AncientCastlePiece}: everything rebuilds identically per chunk from
 * serialized state, and placement goes through the one shared clipped-write path.
 */
public class VillagerCastlePiece extends StructurePiece {

    private static final String TAG_SIZE = "Size";
    private static final String TAG_BIOME = "Biome";
    private static final String TAG_SEED = "Seed";
    private static final String TAG_ENTRANCE = "Entrance";
    private static final String TAG_ORIGIN_X = "OriginX";
    private static final String TAG_ORIGIN_Y = "OriginY";
    private static final String TAG_ORIGIN_Z = "OriginZ";
    private static final String TAG_FIELD = "Field";

    private static final int OVERHANG = 8;
    private static final int TOP_BUDGET = 30;
    private static final int BOTTOM_BUDGET = 8;

    private final VillagerCastleDesigner.Size size;
    private final String biomeId;
    private final long seed;
    private final BlockPos origin;
    private final Direction entrance;
    private final HeightField field;

    private volatile Plan plan;

    public VillagerCastlePiece(VillagerCastleDesigner.Site site, String biomeId,
                               HeightField field, long seed) {
        super(CastleStructureRegistration.VILLAGER_CASTLE_PIECE_TYPE, 0,
            bounds(site.anchor(), site.size(), field));
        this.size = site.size();
        this.biomeId = biomeId;
        this.seed = seed;
        this.origin = site.anchor();
        this.entrance = site.entrance();
        this.field = field;
    }

    public VillagerCastlePiece(CompoundTag tag) {
        super(CastleStructureRegistration.VILLAGER_CASTLE_PIECE_TYPE, tag);
        this.size = VillagerCastleDesigner.Size.byId(
            tag.getStringOr(TAG_SIZE, VillagerCastleDesigner.Size.SMALL.id()));
        this.biomeId = tag.getStringOr(TAG_BIOME, "plains");
        this.seed = tag.getLongOr(TAG_SEED, 0L);
        this.origin = new BlockPos(
            tag.getIntOr(TAG_ORIGIN_X, boundingBox.getCenter().getX()),
            tag.getIntOr(TAG_ORIGIN_Y, boundingBox.getCenter().getY()),
            tag.getIntOr(TAG_ORIGIN_Z, boundingBox.getCenter().getZ()));
        this.entrance = Direction.from2DDataValue(tag.getIntOr(TAG_ENTRANCE, 0));
        this.field = HeightField.fromTag(tag.getCompoundOrEmpty(TAG_FIELD));
    }

    private static BoundingBox bounds(BlockPos origin, VillagerCastleDesigner.Size size,
                                      HeightField field) {
        int reach = size.clearance / 2 + LandformSurvey.FIELD_SKIRT + OVERHANG;
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
        tag.putString(TAG_SIZE, size.id());
        tag.putString(TAG_BIOME, biomeId);
        tag.putLong(TAG_SEED, seed);
        tag.putInt(TAG_ENTRANCE, entrance.get2DDataValue());
        tag.putInt(TAG_ORIGIN_X, origin.getX());
        tag.putInt(TAG_ORIGIN_Y, origin.getY());
        tag.putInt(TAG_ORIGIN_Z, origin.getZ());
        tag.put(TAG_FIELD, field.toTag());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager,
                            ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot) {
        try {
            AncientCastlePiece.place(level, plan(), chunkBox, seed, random);
        } catch (Exception e) {
            VillageCastles.LOGGER.error("Villager castle placement failed at {}", chunkPos, e);
        }
    }

    private Plan plan() {
        Plan cached = plan;
        if (cached == null) {
            cached = VillagerCastleDesigner.draw(
                new VillagerCastleDesigner.Site(origin, size, entrance), field, biomeId, seed);
            plan = cached;
        }
        return cached;
    }
}
