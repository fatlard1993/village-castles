package com.villagecastles.integration;

import com.villagecastles.worldgen.CastleGroundsPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Counts castles already standing in a village.
 *
 * <p>Exists for Village Builder's per-village limits. Those count what the builder has completed,
 * which leaves out the castles worldgen attached: a village generated with a keep would read as
 * having none and be offered another. This answers the question the builder asks once, when it
 * first surveys a village.
 */
public final class CastleCensus {

    /**
     * How far out to look for the village's own structure, in chunks.
     *
     * <p>One would usually do. A castle is attached to the village's own {@code StructureStart},
     * so finding that start finds the castle with it, and the chunk under the builder's table is
     * almost always part of the village. The ring of neighbours covers a table placed at the very
     * edge of town, and stays small because this runs on the server thread during village
     * discovery and can pull chunks in.
     */
    private static final int SEARCH_CHUNK_RADIUS = 1;

    private CastleCensus() {}

    /**
     * The number of castles in the village around this position.
     *
     * <p>Detection is by piece, not by blocks: {@link CastleGroundsPiece} is added alongside the
     * castle template itself, so its presence in a structure's piece list is exactly the record of
     * "this village had a castle attached at generation". A block scan would have to guess from
     * materials, and would confuse a castle with the stone house next to it.
     */
    public static int countCastlesInVillage(ServerLevel world, BlockPos villageCenter) {
        Set<StructureStart> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        ChunkPos origin = new ChunkPos(
            net.minecraft.core.SectionPos.blockToSectionCoord(villageCenter.getX()),
            net.minecraft.core.SectionPos.blockToSectionCoord(villageCenter.getZ()));
        int castles = 0;

        for (int dx = -SEARCH_CHUNK_RADIUS; dx <= SEARCH_CHUNK_RADIUS; dx++) {
            for (int dz = -SEARCH_CHUNK_RADIUS; dz <= SEARCH_CHUNK_RADIUS; dz++) {
                ChunkPos chunk = new ChunkPos(origin.x() + dx, origin.z() + dz);
                for (StructureStart start : world.structureManager().startsForStructure(chunk.x(), chunk.z(), s -> true)) {
                    if (!visited.add(start)) continue;
                    castles += countCastlePieces(start);
                }
            }
        }
        return castles;
    }

    private static int countCastlePieces(StructureStart start) {
        int found = 0;
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof CastleGroundsPiece) found++;
        }
        return found;
    }
}
