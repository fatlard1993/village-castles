package com.villagecastles.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import com.villagecastles.VillageCastles;
import java.util.Optional;

/**
 * A standalone castle structure that places a biome-specific castle_small NBT template.
 *
 * <p>Registered as {@code "villagecastles:castle"} in BuiltInRegistries.STRUCTURE_TYPE.
 * The JSON data files provide the biome list and structure-set placement config.
 */
public class CastleStructure extends Structure {

    private final String biome;
    private final String size; // "small", "medium", or "large"

    public static final MapCodec<CastleStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            Structure.settingsCodec(instance),
            net.minecraft.util.ExtraCodecs.NON_EMPTY_STRING
                .fieldOf("biome_variant")
                .forGetter(s -> s.biome),
            net.minecraft.util.ExtraCodecs.NON_EMPTY_STRING
                .optionalFieldOf("size_variant", "small")
                .forGetter(s -> s.size)
        ).apply(instance, CastleStructure::new)
    );

    public CastleStructure(StructureSettings settings, String biome, String size) {
        super(settings);
        this.biome = biome;
        this.size = size;
    }

    private static final int SEA_LEVEL = 63;
    private static final int MAX_SLOPE = 6;

    // Vanilla random_spread parameters for each biome's village structure set.
    // Same algorithm MC uses internally — lets us predict where the nearest village
    // would land without querying the live world state.
    private static final int VILLAGE_SPACING    = 32;  // chunks between village grid cells
    private static final int VILLAGE_SEPARATION = 8;   // minimum chunks between villages
    // Village salts by biome (from vanilla structure_set JSONs):
    private static final long PLAINS_VILLAGE_SALT   = 10387312L;
    private static final long DESERT_VILLAGE_SALT   = 14357617L;
    private static final long SAVANNA_VILLAGE_SALT  = 10387320L;
    private static final long TAIGA_VILLAGE_SALT    = 10387316L;
    private static final long SNOWY_VILLAGE_SALT    = 10387319L;
    /** Max chunk distance from the nearest village candidate to allow. */
    private static final int VILLAGE_SEARCH_RADIUS = 12; // ~192 blocks

    /** Returns true if a vanilla village would generate within VILLAGE_SEARCH_RADIUS chunks. */
    private boolean hasNearbyVillage(GenerationContext context) {
        long worldSeed = context.seed();
        int chunkX = context.chunkPos().x();
        int chunkZ = context.chunkPos().z();

        long villageSalt = switch (biome) {
            case "desert"  -> DESERT_VILLAGE_SALT;
            case "savanna" -> SAVANNA_VILLAGE_SALT;
            case "taiga"   -> TAIGA_VILLAGE_SALT;
            case "snowy"   -> SNOWY_VILLAGE_SALT;
            default        -> PLAINS_VILLAGE_SALT; // plains + meadow
        };

        int spacing = VILLAGE_SPACING;
        int separation = VILLAGE_SEPARATION;
        int spread = spacing - separation;

        // Check every village grid cell within the search radius
        int cellRadius = (VILLAGE_SEARCH_RADIUS / spacing) + 1;
        int cellX0 = Math.floorDiv(chunkX, spacing);
        int cellZ0 = Math.floorDiv(chunkZ, spacing);

        for (int dx = -cellRadius; dx <= cellRadius; dx++) {
            for (int dz = -cellRadius; dz <= cellRadius; dz++) {
                int cellX = cellX0 + dx;
                int cellZ = cellZ0 + dz;

                // Replicate MC's RandomSpreadStructurePlacement seed calculation
                long seed = (long)(cellX * 341873128712L + cellZ * 132897987541L
                        + worldSeed + villageSalt);
                // LCG step from WorldgenRandom.setLargeFeatureSeed equivalent
                java.util.Random rng = new java.util.Random(seed);
                rng.nextInt(); rng.nextInt(); // discard first two (matches MC internals)
                int offX = rng.nextInt(spread);
                int offZ = rng.nextInt(spread);

                int villageChunkX = cellX * spacing + offX;
                int villageChunkZ = cellZ * spacing + offZ;

                int distChunks = Math.max(Math.abs(chunkX - villageChunkX),
                                          Math.abs(chunkZ - villageChunkZ));
                if (distChunks <= VILLAGE_SEARCH_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Reject positions not near a village — castle density is handled by the
        // structure_set spacing; this bailout ensures they only appear near villages.
        if (!hasNearbyVillage(context)) return Optional.empty();

        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();

        int r = 12;
        int centerH = context.chunkGenerator().getFirstOccupiedHeight(
            cx, cz, Heightmap.Types.WORLD_SURFACE_WG,
            context.heightAccessor(), context.randomState()
        );
        // Reject water/ocean — check center and all 4 corners
        if (centerH <= SEA_LEVEL + 2) return Optional.empty();

        int minH = centerH, maxH = centerH;
        int[][] corners = {{cx-r, cz-r}, {cx+r, cz-r}, {cx-r, cz+r}, {cx+r, cz+r}};
        for (int[] c : corners) {
            int h = context.chunkGenerator().getFirstOccupiedHeight(
                c[0], c[1],
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),
                context.randomState()
            );
            if (h <= SEA_LEVEL + 2) return Optional.empty(); // corner in or near water
            if (h < minH) minH = h;
            if (h > maxH) maxH = h;
        }
        if (maxH - minH > MAX_SLOPE) return Optional.empty();

        return Structure.onTopOfChunkCenter(
            context,
            Heightmap.Types.WORLD_SURFACE_WG,
            (StructurePiecesBuilder builder) -> generatePieces(builder, context, centerH)
        );
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context, int surfaceH) {
        Rotation rotation = Rotation.getRandom(context.random());
        int cx = context.chunkPos().getMiddleBlockX();
        int cz = context.chunkPos().getMiddleBlockZ();

        // getFirstOccupiedHeight(WORLD_SURFACE_WG) returns grassY+1 (first *empty* block).
        // local y=0 = player's feet at capture time (the intended ground floor).
        // Place at surfaceH so local y=0 lands exactly at the terrain surface.
        // Plains/medium (motte castle): same convention — motte base at ground level.
        int y = surfaceH;
        VillageCastles.LOGGER.debug("[castle] cx={} cz={} surfaceH={} placing at y={}", cx, cz, surfaceH, y);

        BlockPos origin = new BlockPos(cx, y, cz);
        CastlePiece piece = new CastlePiece(context.structureTemplateManager(), biome, size, origin, rotation);
        builder.addPiece(piece);
    }

    @Override
    public StructureType<?> type() {
        return CastleStructureRegistration.CASTLE_STRUCTURE_TYPE;
    }
}
