package com.villagecastles.worldgen;

import com.villagecastles.generator.ancient.Plan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A piece's plan cut into the share each chunk writes, handed out once per chunk.
 *
 * <p>Each chunk under the piece takes its own share and drops it, so a chunk walks only what lands
 * in it and the castle stops being held in memory once every chunk under it has generated. Every
 * chunk the piece's box covers starts with a share, empty or not, so a missing one means that chunk
 * already took it: a chunk generated a second time gets the whole plan drawn again for it alone.
 */
final class PlanByChunk {

    private final Supplier<Plan> draw;
    private final Supplier<BoundingBox> bounds;
    private volatile Map<Long, Plan> unplaced;

    PlanByChunk(Supplier<BoundingBox> bounds, Supplier<Plan> draw) {
        this.bounds = bounds;
        this.draw = draw;
    }

    /** What this chunk writes, in the order the whole plan would have written it. */
    Plan take(ChunkPos chunk) {
        Map<Long, Plan> shares = unplaced;
        if (shares == null) {
            synchronized (this) {
                shares = unplaced;
                if (shares == null) {
                    shares = split(draw.get(), bounds.get());
                    unplaced = shares;
                }
            }
        }

        Plan share = shares.remove(chunk.pack());
        return share != null ? share : draw.get();
    }

    private static Map<Long, Plan> split(Plan plan, BoundingBox bounds) {
        Map<Long, Plan> shares = new ConcurrentHashMap<>();
        for (int cx = bounds.minX() >> 4; cx <= bounds.maxX() >> 4; cx++) {
            for (int cz = bounds.minZ() >> 4; cz <= bounds.maxZ() >> 4; cz++) {
                shares.put(ChunkPos.pack(cx, cz), new Plan());
            }
        }

        // Anything drawn in a chunk outside the box is left out: no chunk there is ever asked to place it.
        for (Map.Entry<BlockPos, BlockState> entry : plan.blocks().entrySet()) {
            Plan share = shares.get(ChunkPos.pack(entry.getKey()));
            if (share != null) share.set(entry.getKey(), entry.getValue());
        }
        for (Plan.Chest chest : plan.chests()) {
            Plan share = shares.get(ChunkPos.pack(chest.pos()));
            if (share != null) share.chest(chest.pos(), chest.facing(), chest.table());
        }
        for (Plan.Spawner spawner : plan.spawners()) {
            Plan share = shares.get(ChunkPos.pack(spawner.pos()));
            if (share != null) share.spawner(spawner.pos(), spawner.entity());
        }
        for (Plan.Dig dig : plan.digs()) {
            Plan share = shares.get(ChunkPos.pack(dig.pos()));
            if (share != null) share.dig(dig.pos(), dig.block(), dig.table());
        }

        return shares;
    }
}
