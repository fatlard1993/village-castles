package com.villagecastles.generator.villager;

import com.villagecastles.generator.BiomePalette;
import com.villagecastles.generator.ancient.AncientRooms;
import com.villagecastles.generator.ancient.Condition;
import com.villagecastles.generator.ancient.Geo;
import com.villagecastles.generator.ancient.HeightField;
import com.villagecastles.generator.ancient.Plan;
import com.villagecastles.generator.kit.KitFurnish;
import com.villagecastles.generator.kit.KitOpenings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The shared villager-castle vocabulary: a furnished keep on the ancient bones, yard fencing,
 * gate arches with hung doors, and the biome flair grafts. The three size compositions assemble
 * these; this class decides what they are.
 */
final class VillagerWorks {

    private VillagerWorks() {}

    static final ResourceKey<LootTable> KEEP_CHEST = BuiltInLootTables.VILLAGE_WEAPONSMITH;

    /** Nordic biomes raise drums; everyone else raises square keeps. */
    static boolean roundKeeps(VillagerPalette palette) {
        return palette.biome() == BiomePalette.TAIGA || palette.biome() == BiomePalette.SNOWY;
    }

    /**
     * A furnished, lived-in keep on the ancient kit's bones: hearth and table below, beds above,
     * a ladder between, a hung wooden door in the arch, and torchlight throughout.
     */
    static void keep(Plan plan, VillagerPalette palette, RandomSource random,
                     int cx, int cz, int baseY, int half, int height, Direction door) {
        if (roundKeeps(palette)) {
            AncientRooms.roundKeep(plan, palette, Condition.PRISTINE, cx, cz, baseY,
                half, height, door);
        } else {
            AncientRooms.squareKeep(plan, palette, Condition.PRISTINE, cx, cz, baseY,
                half, height, door);
        }

        // A real door hung in the kit's archway.
        BlockPos doorAt = new BlockPos(
            cx + door.getStepX() * half, baseY + 1, cz + door.getStepZ() * half);
        KitOpenings.door(plan, doorAt, door.getOpposite(), palette.door(), null, null, false);

        // The ladder well: one corner column runs floor to roof, punching through each storey
        // plane - last write wins, so the hole and the ladder are the same statement.
        Direction ladderFace = door.getOpposite();
        int lx = cx - door.getStepX() - door.getClockWise().getStepX() * (half - 2);
        int lz = cz - door.getStepZ() - door.getClockWise().getStepZ() * (half - 2);
        BlockState ladder = Blocks.LADDER.defaultBlockState()
            .setValue(LadderBlock.FACING, ladderFace);
        for (int y = baseY + 1; y <= baseY + height - 2; y++) {
            plan.set(lx, y, lz, ladder);
        }

        // Ground floor: the garrison's day room.
        int floor0 = baseY + 1;
        KitFurnish.hearth(plan, new BlockPos(cx - 1, floor0 - 1, cz - 1),
            floor0 - 1, palette.plinth(), Blocks.CAMPFIRE.defaultBlockState());
        KitFurnish.table(plan, new BlockPos(cx + 1, floor0, cz + 1), door.getClockWise(), 2,
            palette.fence(), palette.slab());
        KitFurnish.chair(plan, new BlockPos(cx + 1, floor0, cz), door.getOpposite(),
            palette.biome().woodStairs.defaultBlockState());
        plan.chest(new BlockPos(cx - 1, floor0 + 1, cz + 2), door, KEEP_CHEST);
        KitFurnish.onFloor(plan, new BlockPos(cx + 2, floor0, cz - 2),
            Blocks.SMITHING_TABLE.defaultBlockState());
        torch(plan, palette, cx + half - 1, floor0 + 1, cz);
        torch(plan, palette, cx - half + 1, floor0 + 1, cz);

        // Upper floor: the garrison pair sleeps here.
        int floor1 = baseY + 1 + 6;
        if (height > 8) {
            Direction across = door.getClockWise();
            KitFurnish.bed(plan, new BlockPos(cx + across.getStepX() * 2, floor1,
                cz + across.getStepZ() * 2), door.getOpposite(), palette.bed());
            KitFurnish.bed(plan, new BlockPos(cx - across.getStepX() * 2, floor1,
                cz - across.getStepZ() * 2), door.getOpposite(), palette.bed());
            torch(plan, palette, cx, floor1 + 1, cz + half - 1);
        }
    }

    /** A standing light in the biome's own kind: torch, lantern, whatever the palette keeps. */
    static void torch(Plan plan, VillagerPalette palette, int x, int y, int z) {
        plan.set(x, y, z, palette.lantern());
    }

    /**
     * Clears the open bailey inside a curtain ring. The canopy strip follows built columns, and
     * a courtyard has none - without this, the jungle stays inside the walls.
     */
    static void clearCourt(Plan plan, HeightField terrain, int cx, int cz, int radius) {
        for (int[] o : Geo.disc(radius)) {
            int x = cx + o[0];
            int z = cz + o[1];
            int ground = terrain.surfaceY(x, z);
            // Above the water, not above the seabed - the same reason the canopy pass does it
            // (see PlanPasses.clearCanopy): surfaceY excludes water, so clearing up from it drains
            // a lakeside bailey to bare seabed instead of leaving the shallows in it.
            for (int y = terrain.coverY(x, z) + 1; y <= ground + 10; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!plan.has(pos)) plan.set(pos, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** A fenced yard with a gate on the entrance side and torch posts at the corners. */
    static void yard(Plan plan, HeightField terrain, VillagerPalette palette,
                     int cx, int cz, int radius, Direction entrance) {
        for (int[] o : Geo.squareRing(radius)) {
            int x = cx + o[0];
            int z = cz + o[1];
            int ground = terrain.surfaceY(x, z);
            plan.set(x, ground + 1, z, palette.fence());
        }
        // The gate, swung to open along the entrance axis so the fence line carries through it.
        int gx = cx + entrance.getStepX() * radius;
        int gz = cz + entrance.getStepZ() * radius;
        BlockState gate = palette.fenceGate();
        if (gate.hasProperty(net.minecraft.world.level.block.FenceGateBlock.FACING)) {
            gate = gate.setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, entrance);
        }
        plan.set(gx, terrain.surfaceY(gx, gz) + 1, gz, gate);
        // Torch posts at the corners.
        for (int sx : new int[] {-radius, radius}) {
            for (int sz : new int[] {-radius, radius}) {
                int ground = terrain.surfaceY(cx + sx, cz + sz);
                plan.set(cx + sx, ground + 2, cz + sz, palette.fence());
                torch(plan, palette, cx + sx, ground + 3, cz + sz);
            }
        }
    }

    /**
     * A gate arch through a curtain ring with a hung double door and flanking lights: the
     * villagers' reading of the ancients' monumental gateways.
     */
    static void gate(Plan plan, HeightField terrain, VillagerPalette palette,
                     int cx, int cz, int radius, Direction entrance) {
        int gx = cx + entrance.getStepX() * radius;
        int gz = cz + entrance.getStepZ() * radius;
        int ground = terrain.surfaceY(gx, gz);
        BlockPos arch = new BlockPos(gx, ground + 1, gz);
        Geo.archway(plan, arch, entrance, 2, 4, Geo.solid(palette.dressed()));
        Geo.archway(plan, arch.relative(entrance.getOpposite(), 1), entrance, 2, 4,
            Geo.solid(palette.dressed()));
        KitOpenings.door(plan, arch.relative(entrance.getClockWise().getOpposite(), 0),
            entrance.getOpposite(), palette.door(), null, null, true);
        Direction across = entrance.getClockWise();
        torch(plan, palette, gx + across.getStepX() * 3, ground + 4, gz + across.getStepZ() * 3);
        torch(plan, palette, gx - across.getStepX() * 3, ground + 4, gz - across.getStepZ() * 3);
    }

    /** The biome's own touch in the yard: what these particular villagers do with open ground. */
    static void flair(Plan plan, HeightField terrain, VillagerPalette palette,
                      RandomSource random, int cx, int cz, int reach) {
        int fx = cx + reach / 2;
        int fz = cz - reach / 2;
        int ground = terrain.surfaceY(fx, fz);
        switch (palette.biome()) {
            case SAVANNA -> {
                // Fire pit with log seats, and a small stock pen.
                KitFurnish.hearth(plan, new BlockPos(fx, ground, fz), ground,
                    palette.plinth(), Blocks.CAMPFIRE.defaultBlockState());
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    plan.set(fx + side.getStepX() * 2, ground + 1, fz + side.getStepZ() * 2,
                        palette.log());
                }
                int px = cx - reach / 2;
                int pz = cz + reach / 2 - 2;
                for (int[] o : Geo.squareRing(2)) {
                    int x = px + o[0];
                    int z = pz + o[1];
                    plan.set(x, terrain.surfaceY(x, z) + 1, z, palette.fence());
                }
                BlockState penGate = palette.fenceGate();
                if (penGate.hasProperty(net.minecraft.world.level.block.FenceGateBlock.FACING)) {
                    penGate = penGate.setValue(
                        net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.EAST);
                }
                plan.set(px + 2, terrain.surfaceY(px + 2, pz) + 1, pz, penGate);
            }
            case DESERT -> {
                // The courtyard fountain, remembered from the old compounds.
                for (int[] o : Geo.squareRing(1)) {
                    plan.set(fx + o[0], ground + 1, fz + o[1], palette.slab());
                }
                plan.set(fx, ground, fz, palette.dressed());
                plan.set(fx, ground + 1, fz, Blocks.WATER.defaultBlockState());
            }
            case TAIGA -> {
                // A woodpile and a brazier against the cold.
                for (int i = 0; i < 3; i++) {
                    plan.set(fx + i, ground + 1, fz, palette.log());
                }
                plan.set(fx + 1, ground + 2, fz, palette.log());
                KitFurnish.hearth(plan, new BlockPos(fx - 2, ground, fz + 2), ground,
                    palette.plinth(), Blocks.CAMPFIRE.defaultBlockState());
            }
            case SNOWY -> {
                // A soul campfire beacon and hay for the animals that winter inside.
                KitFurnish.hearth(plan, new BlockPos(fx, ground, fz), ground,
                    palette.plinth(), Blocks.SOUL_CAMPFIRE.defaultBlockState());
                plan.set(fx - 2, ground + 1, fz + 1, Blocks.HAY_BLOCK.defaultBlockState());
            }
            default -> {
                // Plains: hay, a composter, the working yard.
                plan.set(fx, ground + 1, fz, Blocks.HAY_BLOCK.defaultBlockState());
                plan.set(fx + 1, ground + 1, fz, Blocks.HAY_BLOCK.defaultBlockState());
                plan.set(fx, ground + 2, fz, Blocks.HAY_BLOCK.defaultBlockState());
                plan.set(fx - 2, ground + 1, fz + 1, Blocks.COMPOSTER.defaultBlockState());
            }
        }
    }
}
