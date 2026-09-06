package com.villagecastles.worldgen;

import com.mojang.serialization.MapCodec;
import com.villagecastles.generator.ancient.Condition;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.HeightSampler;
import com.villagecastles.generator.ancient.LandformSurvey;
import com.villagecastles.util.VillagePrediction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * The standalone ancient castle: no NBT, no jigsaw. The terrain is the configuration - the
 * classifier reads the land around each structure-set candidate and either names a landform
 * (plateau, hilltop, cliff face, island) or rejects the site outright. Rejection plus set spacing
 * is what makes these castles landmarks.
 *
 * <p>The codec carries nothing but settings: landform, condition, and site are detected, never
 * configured. The matching JSON must keep {@code terrain_adaptation: none} - a beardifier would
 * flatten or carve the very landform the site was chosen for; foundations are the generators'
 * plinth work against the sampled height field.
 */
public class AncientCastleStructure extends Structure {

    public static final MapCodec<AncientCastleStructure> CODEC =
        simpleCodec(AncientCastleStructure::new);

    /** Reject candidates this close (in chunks) to a predicted village: the ancients predate towns. */
    private static final int VILLAGE_EXCLUSION_CHUNKS = 8;
    private static final int MAX_SURFACE = 200;
    private static final int MAX_WATER_DEPTH = 2;

    public AncientCastleStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int chunkX = context.chunkPos().x();
        int chunkZ = context.chunkPos().z();
        if (VillagePrediction.anyVillageWithin(context.seed(), chunkX, chunkZ,
                VILLAGE_EXCLUSION_CHUNKS)) {
            return Optional.empty();
        }

        HeightSampler sampler = HeightSampler.worldgen(context);
        int centerX = context.chunkPos().getMiddleBlockX();
        int centerZ = context.chunkPos().getMiddleBlockZ();

        // Cheap gate before the 98-column survey: reject drowned or absurd centers.
        int centerGround = sampler.floor(centerX, centerZ);
        int centerSurface = sampler.surface(centerX, centerZ);
        if (centerSurface > MAX_SURFACE) return Optional.empty();
        if (centerSurface - centerGround > MAX_WATER_DEPTH * 4) return Optional.empty();

        Optional<LandformSurvey.Site> found =
            LandformSurvey.classify(sampler, centerX, centerZ, context.random());
        if (found.isEmpty()) return Optional.empty();
        LandformSurvey.Site site = found.get();

        Condition condition = Condition.roll(context.random());
        // Drawn here and carried on the piece rather than re-derived at placement time: the
        // piece's postProcess runs once per overlapping chunk and every call has to rebuild the
        // identical castle.
        long shapeSeed = context.random().nextLong();

        String tint = resolveTint(context, site);
        HeightField field = LandformSurvey.sampleField(sampler, site);

        return Optional.of(new GenerationStub(site.anchor(), builder ->
            builder.addPiece(new AncientCastlePiece(site, condition, tint, field, shapeSeed))));
    }

    /** The biome under the anchor, folded to a palette tint. Failure means temperate, not no castle. */
    private static String resolveTint(GenerationContext context, LandformSurvey.Site site) {
        try {
            Holder<Biome> biome = context.biomeResolver().getNoiseBiome(
                QuartPos.fromBlock(site.anchor().getX()),
                QuartPos.fromBlock(site.anchor().getY()),
                QuartPos.fromBlock(site.anchor().getZ()));
            String path = biome.unwrapKey()
                .map(key -> key.identifier().getPath())
                .orElse("");
            return com.villagecastles.generator.ancient.AncientPalette.tintIdForBiomePath(path);
        } catch (Exception e) {
            return "temperate";
        }
    }

    @Override
    public StructureType<?> type() {
        return CastleStructureRegistration.ANCIENT_CASTLE_STRUCTURE_TYPE;
    }
}
