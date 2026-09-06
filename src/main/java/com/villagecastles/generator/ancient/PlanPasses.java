package com.villagecastles.generator.ancient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finishing passes shared by every castle designer, ancient or villager: they make physics agree
 * with what the generators wanted, whatever erosion or terrain did to the rooms in between.
 */
public final class PlanPasses {

    private PlanPasses() {}

    /**
     * Every lantern ends supported or gone: standing when stone sits below it, hanging when
     * there is only a ceiling, removed when neither survived.
     */
    public static void settleLanterns(Plan plan) {
        List<BlockPos> standing = new ArrayList<>();
        List<BlockPos> hanging = new ArrayList<>();
        List<BlockPos> orphaned = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : plan.blocks().entrySet()) {
            if (!(entry.getValue().getBlock() instanceof LanternBlock)
                    && !entry.getValue().is(Blocks.TORCH)) continue;
            BlockPos pos = entry.getKey();
            boolean isTorch = entry.getValue().is(Blocks.TORCH);
            if (supports(plan.get(pos.below()))) standing.add(pos);
            else if (!isTorch && supports(plan.get(pos.above()))) hanging.add(pos);
            else orphaned.add(pos);
        }
        for (BlockPos pos : standing) {
            BlockState state = plan.get(pos);
            plan.set(pos, state.hasProperty(LanternBlock.HANGING)
                ? state.setValue(LanternBlock.HANGING, false) : state);
        }
        for (BlockPos pos : hanging) {
            BlockState state = plan.get(pos);
            plan.set(pos, state.hasProperty(LanternBlock.HANGING)
                ? state.setValue(LanternBlock.HANGING, true) : state);
        }
        for (BlockPos pos : orphaned) {
            plan.set(pos, Blocks.AIR.defaultBlockState());
        }
    }

    /**
     * Every chest ends on solid footing. Unlike a lantern, a chest carries loot and is never
     * removed: when the cell below is carved air, or open air above natural ground, a dressed
     * support block goes under it instead.
     */
    public static void settleChests(Plan plan, HeightField terrain, CastlePalette palette) {
        for (Plan.Chest chest : plan.chests()) {
            BlockPos below = chest.pos().below();
            BlockState planned = plan.get(below);
            boolean supported = planned != null
                ? (!planned.isAir() && planned.getFluidState().isEmpty())
                : below.getY() <= terrain.surfaceY(below.getX(), below.getZ());
            if (!supported) plan.set(below, palette.dressed());
        }
    }

    private static boolean supports(BlockState state) {
        return state != null && !state.isAir() && state.getFluidState().isEmpty();
    }

    /**
     * Connects fences, bars, and panes to their planned neighbours. Plan blocks are written as
     * literal states with no neighbour updates, so a fence line placed bare stands as isolated
     * posts; this pass computes each cross-shaped block's four connection properties from the
     * plan itself - deterministic, chunk-order independent, and blind to unplanned terrain
     * (a yard fence never grabs at a grass bump beside it).
     */
    public static void connectCross(Plan plan) {
        java.util.Map<BlockPos, BlockState> updates = new java.util.LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : plan.blocks().entrySet()) {
            BlockState state = entry.getValue();
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock)) continue;
            BlockPos pos = entry.getKey();
            BlockState connected = state;
            for (net.minecraft.core.Direction side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                connected = connected.setValue(
                    net.minecraft.world.level.block.CrossCollisionBlock.PROPERTY_BY_DIRECTION.get(side),
                    connectsTo(state, plan.get(pos.relative(side)), side));
            }
            updates.put(pos, connected);
        }
        updates.forEach(plan::set);
    }

    private static boolean connectsTo(BlockState self, BlockState neighbour,
                                      net.minecraft.core.Direction side) {
        if (neighbour == null || neighbour.isAir()) return false;
        // Fences join fences and gates of any wood; bars and panes join their own kind. A gate
        // carries the line only when it swings across it.
        boolean selfIsFence = self.getBlock() instanceof net.minecraft.world.level.block.FenceBlock;
        if (neighbour.getBlock() instanceof net.minecraft.world.level.block.FenceBlock) {
            return selfIsFence;
        }
        if (neighbour.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
            return selfIsFence && neighbour.getValue(
                net.minecraft.world.level.block.FenceGateBlock.FACING).getAxis() != side.getAxis();
        }
        if (neighbour.getBlock() instanceof net.minecraft.world.level.block.CrossCollisionBlock) {
            return !selfIsFence;
        }
        // Anything else: connect when the touching face is sturdy (walls, full masonry).
        return neighbour.isFaceSturdy(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,
            BlockPos.ZERO, side.getOpposite());
    }

    /** How far above natural ground the canopy strip reaches: over any vanilla tree's crown. */
    private static final int CANOPY_CLEAR = 12;
    /** Columns beyond the built footprint that are also stripped, so no crown leans on a wall. */
    private static final int CANOPY_MARGIN = 2;

    /**
     * Strips trees over and beside the works. A castle drawn through a forest otherwise bisects
     * trunks and leaves crowns stuck to its walls: this clears every column the plan builds in,
     * plus a margin, from the ground to above tree height - whole trees go, not halves. Cleared
     * cells are written as air entries so they clip per chunk like everything else; they never
     * overwrite what the generators drew.
     */
    public static void clearCanopy(Plan plan, HeightField terrain) {
        java.util.Set<Long> columns = new java.util.HashSet<>();
        for (BlockPos pos : plan.blocks().keySet()) {
            for (int dx = -CANOPY_MARGIN; dx <= CANOPY_MARGIN; dx++) {
                for (int dz = -CANOPY_MARGIN; dz <= CANOPY_MARGIN; dz++) {
                    columns.add(((long) (pos.getX() + dx) << 32) ^ ((pos.getZ() + dz) & 0xFFFFFFFFL));
                }
            }
        }
        for (long key : columns) {
            int x = (int) (key >> 32);
            int z = (int) key;
            int ground = terrain.surfaceY(x, z);
            // Start above the WATER, not above the seabed. surfaceY is the ocean floor - water is
            // excluded from it - so clearing up from there emptied the whole water column and left
            // a dry rectangular pit in the lake beside the castle, seabed showing, with a sheer
            // wall of water around it. Nothing refilled it, because this pass only ever subtracts.
            //
            // coverY is the water's top on a wet column and equal to ground on a dry one, so this
            // is the same clear as before on land, and on water it takes only what is above the
            // surface - the overhanging branch, never the lake. Water deeper than the clearance
            // skips the column entirely, which is right: there is no canopy out there to cut.
            for (int y = terrain.coverY(x, z) + 1; y <= ground + CANOPY_CLEAR; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!plan.has(pos)) plan.set(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }
}
