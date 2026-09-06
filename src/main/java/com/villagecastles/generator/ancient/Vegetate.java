package com.villagecastles.generator.ancient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Growth over the finished plan, with no world in sight: every fact vegetation needs - is this
 * face exposed, is there open sky above - is answered from the plan itself plus the terrain field.
 * Runs once at the end of the plan build, so vines and moss are ordinary plan entries and clip at
 * chunk borders like everything else.
 */
public final class Vegetate {

    private Vegetate() {}

    /** Base chance that an exposed top grows cover, before the condition scales it. */
    private static final int DRIFT_PERCENT = 30;
    /** Base chance that an exposed face above ground grows a vine. */
    private static final int CREEP_PERCENT = 12;

    public static void apply(Plan plan, HeightField terrain, AncientPalette palette,
                             Condition condition, RandomSource random) {
        List<BlockPos> tops = new ArrayList<>();
        List<BlockPos[]> faces = new ArrayList<>(); // {vine pos, solid pos}

        for (Map.Entry<BlockPos, BlockState> entry : plan.blocks().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (state.is(Blocks.VINE) || state.is(Blocks.MOSS_CARPET) || state.is(Blocks.SNOW)) continue;

            BlockPos above = pos.above();
            // Drift only settles on full cubes: a moss carpet perched on a merlon post or a
            // coping slab reads as litter, not growth.
            boolean fullCube = !(state.getBlock() instanceof net.minecraft.world.level.block.WallBlock)
                && !(state.getBlock() instanceof net.minecraft.world.level.block.SlabBlock)
                && !(state.getBlock() instanceof net.minecraft.world.level.block.StairBlock);
            if (fullCube && open(plan, above)
                    && pos.getY() >= terrain.surfaceY(pos.getX(), pos.getZ())) {
                tops.add(pos);
            }
            if (palette.tint.creeps()) {
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    BlockPos out = pos.relative(side);
                    if (!open(plan, out)) continue;
                    if (out.getY() <= terrain.surfaceY(out.getX(), out.getZ())) continue;
                    faces.add(new BlockPos[] {out, pos});
                }
            }
        }

        int driftChance = (int) (DRIFT_PERCENT * condition.vegetationScale);
        int creepChance = (int) (CREEP_PERCENT * condition.vegetationScale);

        for (BlockPos pos : tops) {
            if (random.nextInt(100) >= driftChance) continue;
            BlockState cover = topCover(palette, random);
            if (cover != null) plan.set(pos.above(), cover);
        }

        for (BlockPos[] face : faces) {
            if (random.nextInt(100) >= creepChance) continue;
            BlockPos at = face[0];
            // The plan may have grown a vine here off another face already; merge rather than
            // overwrite so a corner can carry two.
            BlockState existing = plan.get(at);
            BlockState vine = existing != null && existing.is(Blocks.VINE)
                ? existing : Blocks.VINE.defaultBlockState();
            Direction toWall = directionTo(at, face[1]);
            plan.set(at, vine.setValue(faceProperty(toWall), true));
        }
    }

    /** A cell counts as open when the plan has nothing there or only cleared air (the canopy
     *  strip writes air entries; growth must see through them). */
    private static boolean open(Plan plan, BlockPos pos) {
        BlockState state = plan.get(pos);
        return state == null || state.isAir();
    }

    private static BlockState topCover(AncientPalette palette, RandomSource random) {
        // Snowy castles get no drift of their own: vanilla snowfall dresses them better than we
        // could, and placed snow reads as build material, which it must never be.
        if (palette.tint == AncientPalette.SNOWY) {
            return null;
        }
        if (palette.tint == AncientPalette.DESERT) {
            return Blocks.SANDSTONE_SLAB.defaultBlockState();
        }
        return Blocks.MOSS_CARPET.defaultBlockState();
    }

    private static Direction directionTo(BlockPos from, BlockPos to) {
        if (to.getX() > from.getX()) return Direction.EAST;
        if (to.getX() < from.getX()) return Direction.WEST;
        if (to.getZ() > from.getZ()) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private static BooleanProperty faceProperty(Direction toWall) {
        return switch (toWall) {
            case NORTH -> VineBlock.NORTH;
            case SOUTH -> VineBlock.SOUTH;
            case EAST -> VineBlock.EAST;
            default -> VineBlock.WEST;
        };
    }
}
