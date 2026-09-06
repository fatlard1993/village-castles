package com.villagecastles.generator.ancient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one ancient castle places, resolved to absolute world positions before any of it is
 * written.
 *
 * <p>A castle is wider than a chunk. Its piece's {@code postProcess} is called once per overlapping
 * chunk and may only touch blocks inside the chunk it was handed, so the whole castle is drawn into
 * a plan from a stored seed and each chunk writes the subset that lands inside it. The plan is
 * rebuilt from scratch per structure load; the redundant work buys generators written as
 * straight-line code with an ordinary sequential {@code RandomSource} instead of forcing every
 * decision to be a pure function of its own coordinates.
 *
 * <p>The block map is a {@link LinkedHashMap} because both of its orderings carry meaning: a later
 * {@code set} at the same position wins, so a generator can draw a wall whole and then punch a
 * doorway through it, and iteration replays insertion order, so the write sequence is identical
 * from every chunk.
 */
public final class Plan {

    /** A chest, its facing, and the loot table it rolls. Tables vary per chest, not per castle. */
    public record Chest(BlockPos pos, Direction facing, ResourceKey<LootTable> table) {}

    /** A mob spawner and what it is set to produce. */
    public record Spawner(BlockPos pos, EntityType<?> entity) {}

    /** A brushable block (suspicious sand/gravel) and the archaeology table it holds. */
    public record Dig(BlockPos pos, BlockState block, ResourceKey<LootTable> table) {}

    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final List<Chest> chests = new ArrayList<>();
    private final List<Spawner> spawners = new ArrayList<>();
    private final List<Dig> digs = new ArrayList<>();

    public void set(int x, int y, int z, BlockState state) {
        blocks.put(new BlockPos(x, y, z), state);
    }

    public void set(BlockPos pos, BlockState state) {
        blocks.put(pos, state);
    }

    public BlockState get(BlockPos pos) {
        return blocks.get(pos);
    }

    public boolean has(BlockPos pos) {
        return blocks.containsKey(pos);
    }

    public void chest(BlockPos pos, Direction facing, ResourceKey<LootTable> table) {
        chests.add(new Chest(pos, facing, table));
    }

    public void spawner(BlockPos pos, EntityType<?> entity) {
        spawners.add(new Spawner(pos, entity));
    }

    public void dig(BlockPos pos, BlockState block, ResourceKey<LootTable> table) {
        digs.add(new Dig(pos, block, table));
    }

    public Map<BlockPos, BlockState> blocks() {
        return blocks;
    }

    public List<Chest> chests() {
        return chests;
    }

    public List<Spawner> spawners() {
        return spawners;
    }

    public List<Dig> digs() {
        return digs;
    }
}
