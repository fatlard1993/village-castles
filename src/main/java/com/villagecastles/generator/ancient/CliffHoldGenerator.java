package com.villagecastles.generator.ancient;

import com.villagecastles.generator.ancient.LandformSurvey.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

/**
 * The cliff hold: a castle that uses the face as its fourth wall. A walled lower ward at the
 * cliff's foot, a watch on the lip, and a great tower engaged with the rock - footed at the base,
 * rising past the lip, its back against the carved face. A rock-cut switchback stair climbs
 * between them, and a columned hall is carved into the cliff behind the ward.
 *
 * <p>{@code site.facing()} is the high side; the anchor sits at the cliff's foot.
 */
public final class CliffHoldGenerator {

    private CliffHoldGenerator() {}

    private static final int WARD_ALONG = 15;   // half-extent along the face
    private static final int WARD_DEEP = 12;    // how far the ward reaches away from the face
    private static final int WARD_WALL_HEIGHT = 7;
    private static final int TOWER_HALF = 3;

    public static void draw(Plan plan, Site site, HeightField terrain, AncientPalette palette,
                            Condition condition, RandomSource random) {
        Direction up = site.facing();          // toward the high ground
        Direction down = up.getOpposite();
        Direction along = up.getClockWise();
        int cx = site.anchor().getX();
        int cz = site.anchor().getZ();
        int baseY = site.anchor().getY();

        // Where the face stands: walk uphill from the anchor until the ground jumps.
        int faceDist = 4;
        for (int d = 2; d <= 12; d++) {
            int x = cx + up.getStepX() * d;
            int z = cz + up.getStepZ() * d;
            if (terrain.surfaceY(x, z) >= baseY + 6) {
                faceDist = d;
                break;
            }
        }
        int fx = cx + up.getStepX() * faceDist;
        int fz = cz + up.getStepZ() * faceDist;
        int lipX = fx + up.getStepX() * 6;
        int lipZ = fz + up.getStepZ() * 6;
        int lipY = terrain.surfaceY(lipX, lipZ);

        lowerWard(plan, terrain, palette, condition, cx, cz, up, along, baseY);
        carvedHall(plan, palette, condition, random, fx, fz, up, along, baseY);
        int towerTopY = greatTower(plan, palette, condition, fx, fz, up, along, baseY, lipY);
        upperWatch(plan, terrain, palette, condition, lipX, lipZ, up, lipY);
        switchbackStair(plan, palette, fx, fz, up, along, baseY, lipY);

        // Bridge from the tower's crown back to the lip, three wide with parapet rails.
        int bridgeY = towerTopY;
        for (int d = 0; d <= 8; d++) {
            int bx = fx + up.getStepX() * d;
            int bz = fz + up.getStepZ() * d;
            if (terrain.surfaceY(bx, bz) >= bridgeY) break;
            for (int w = -1; w <= 1; w++) {
                int x = bx + along.getStepX() * w;
                int z = bz + along.getStepZ() * w;
                plan.set(x, bridgeY, z, palette.trimAt(x, bridgeY, z));
                if (w != 0 && condition.roofsIntact()) {
                    plan.set(x, bridgeY + 1, z, palette.parapet());
                }
            }
        }

        // Arched buttresses leaning on the face, flanking the tower.
        for (int side : new int[] {-1, 1}) {
            for (int arch = 1; arch <= 2; arch++) {
                int bx = fx + along.getStepX() * side * (TOWER_HALF + 3 * arch + 1);
                int bz = fz + along.getStepZ() * side * (TOWER_HALF + 3 * arch + 1);
                int ground = terrain.surfaceY(bx, bz);
                Geo.column(plan, bx + down.getStepX() * 3, bz + down.getStepZ() * 3,
                    ground + 1, baseY + 8, Geo.solid(palette.plinth()));
                for (int d = 0; d <= 3; d++) {
                    int y = baseY + 8 + (d >= 2 ? 1 : 0);
                    plan.set(bx + down.getStepX() * d, y, bz + down.getStepZ() * d,
                        palette.trimAt(bx, y, bz));
                }
            }
        }

        AncientCastleDesigner.scatterDigs(plan, terrain, condition, random, cx, cz, 9,
            Integer.MIN_VALUE);
        AncientCastleDesigner.scatterRubble(plan, terrain, palette, random, cx, cz,
            site.radius() + 3, condition);
    }

    /** The walled yard at the cliff's foot: three eroded walls, the face is the fourth. */
    private static void lowerWard(Plan plan, HeightField terrain, AncientPalette palette,
                                  Condition condition, int cx, int cz, Direction up,
                                  Direction along, int baseY) {
        Direction down = up.getOpposite();
        for (int a = -WARD_ALONG; a <= WARD_ALONG; a++) {
            for (int d = 0; d <= WARD_DEEP; d++) {
                int x = cx + along.getStepX() * a + down.getStepX() * (d - 3);
                int z = cz + along.getStepZ() * a + down.getStepZ() * (d - 3);
                boolean edge = a == -WARD_ALONG || a == WARD_ALONG || d == WARD_DEEP;
                int ground = terrain.surfaceY(x, z);
                if (edge) {
                    Geo.column(plan, x, z, ground - 4, ground, Geo.solid(palette.plinth()));
                    Erode.wallColumn(plan, palette, x, z, ground + 1, baseY + WARD_WALL_HEIGHT);
                } else {
                    plan.set(x, ground, z, palette.floorAt(x, ground, z));
                }
            }
        }
        // The ward gate: an arch through the outer wall, opposite the face.
        BlockPos gate = new BlockPos(
            cx + down.getStepX() * (WARD_DEEP - 3), baseY + 1, cz + down.getStepZ() * (WARD_DEEP - 3));
        Geo.archway(plan, gate, down, 2, 4, Geo.solid(palette.dressed()));
    }

    /** The hall carved into the rock behind the ward: columned, half-buried in its own ceiling
     *  when the hold is ruined. */
    private static void carvedHall(Plan plan, AncientPalette palette, Condition condition,
                                   RandomSource random, int fx, int fz, Direction up,
                                   Direction along, int baseY) {
        Direction side = along.getOpposite();
        int hallY = baseY + 1;
        for (int d = 1; d <= 7; d++) {
            for (int a = -4; a <= 4; a++) {
                int x = fx + up.getStepX() * d + along.getStepX() * a;
                int z = fz + up.getStepZ() * d + along.getStepZ() * a;
                plan.set(x, hallY - 1, z, palette.floorAt(x, hallY - 1, z));
                for (int h = 0; h < 4; h++) {
                    plan.set(x, hallY + h, z, Blocks.CAVE_AIR.defaultBlockState());
                }
                plan.set(x, hallY + 4, z, palette.trimAt(x, hallY + 4, z));
                boolean pillar = (Math.abs(a) == 2) && (d == 2 || d == 5);
                if (pillar) Geo.column(plan, x, z, hallY, hallY + 3, Geo.solid(palette.monolith()));
            }
        }
        if (condition == Condition.RUINED) {
            for (int i = 0; i < 10; i++) {
                int d = 1 + random.nextInt(7);
                int a = -4 + random.nextInt(9);
                int x = fx + up.getStepX() * d + along.getStepX() * a;
                int z = fz + up.getStepZ() * d + along.getStepZ() * a;
                plan.set(x, hallY, z, palette.rubble(random));
            }
        }
        // Entrance from the ward, and the vault deep at the hall's far end.
        BlockPos door = new BlockPos(fx, hallY, fz);
        Geo.archway(plan, door, up, 1, 3, Geo.solid(palette.dressed()));
        AncientRooms.vault(plan,
            new BlockPos(fx + up.getStepX() * 10, hallY, fz + up.getStepZ() * 10),
            palette, AncientCastleDesigner.TREASURY);
        AncientRooms.spawnerCell(plan,
            new BlockPos(fx + up.getStepX() * 6 + side.getStepX() * 8, hallY + 1,
                fz + up.getStepZ() * 6 + side.getStepZ() * 8),
            palette, AncientCastleDesigner.garrison(palette));
    }

    /**
     * The great tower engaged with the face: square shell from below the base to past the lip,
     * its uphill wall carved straight out of the cliff line.
     *
     * @return the Y of the tower's crown deck.
     */
    private static int greatTower(Plan plan, AncientPalette palette, Condition condition,
                                  int fx, int fz, Direction up, Direction along,
                                  int baseY, int lipY) {
        int topY = lipY + 6;
        Direction down = up.getOpposite();
        int tx = fx + down.getStepX() * TOWER_HALF;
        int tz = fz + down.getStepZ() * TOWER_HALF;
        Geo.Material face = palette.banded(baseY, 6);

        for (int a = -TOWER_HALF; a <= TOWER_HALF; a++) {
            for (int d = -TOWER_HALF; d <= TOWER_HALF; d++) {
                int x = tx + along.getStepX() * a + up.getStepX() * d;
                int z = tz + along.getStepZ() * a + up.getStepZ() * d;
                boolean shell = Math.abs(a) == TOWER_HALF || Math.abs(d) == TOWER_HALF;
                if (shell) {
                    Geo.column(plan, x, z, baseY - 4, baseY, Geo.solid(palette.plinth()));
                    // The tower erodes from the top like everything else, but its engaged back
                    // wall is rock-fast: only the free faces shorten.
                    boolean engaged = d == TOWER_HALF;
                    if (engaged) {
                        Geo.column(plan, x, z, baseY + 1, topY, face);
                    } else {
                        Erode.wallColumn(plan, palette, face, x, z, baseY + 1, topY);
                    }
                } else {
                    for (int floorY = baseY; floorY <= topY; floorY += 6) {
                        plan.set(x, floorY, z, palette.floorAt(x, floorY, z));
                    }
                }
            }
        }
        Geo.spiralStairs(plan, tx, tz, baseY + 1, topY - 1, TOWER_HALF - 1,
            palette.stairs(), Geo.solid(palette.plinth()));

        // Door from the ward, and the slate-spired crown seen from below while it stands.
        BlockPos door = new BlockPos(
            tx + down.getStepX() * TOWER_HALF, baseY + 1, tz + down.getStepZ() * TOWER_HALF);
        plan.set(door, Blocks.AIR.defaultBlockState());
        plan.set(door.above(), Blocks.AIR.defaultBlockState());
        if (condition.roofsIntact()) {
            Geo.pyramid(plan, tx, tz, topY + 1, TOWER_HALF, Geo.solid(palette.roof()),
                palette.roofSlab());
        }
        return topY;
    }

    /** The watch on the lip: a small drum tower and lantern, visible from the ward below. */
    private static void upperWatch(Plan plan, HeightField terrain, AncientPalette palette,
                                   Condition condition, int lipX, int lipZ, Direction up,
                                   int lipY) {
        int wx = lipX + up.getStepX() * 4;
        int wz = lipZ + up.getStepZ() * 4;
        int ground = terrain.surfaceY(wx, wz);
        for (int[] o : Geo.disc(4)) {
            Geo.column(plan, wx + o[0], wz + o[1], ground - 3, ground, Geo.solid(palette.plinth()));
        }
        for (int[] o : Geo.ring(4)) {
            Erode.wallColumn(plan, palette, wx + o[0], wz + o[1], ground + 1, ground + 9);
        }
        plan.set(wx, ground + 1, wz, palette.floorAt(wx, ground + 1, wz));
        plan.set(wx + up.getStepX() * -4, ground + 1, wz + up.getStepZ() * -4,
            Blocks.AIR.defaultBlockState());
        plan.set(wx + up.getStepX() * -4, ground + 2, wz + up.getStepZ() * -4,
            Blocks.AIR.defaultBlockState());
        if (condition.roofsIntact()) {
            Geo.cone(plan, wx, wz, ground + 10, 4, Geo.solid(palette.roof()), palette.roofSlab());
        }
        plan.set(wx, ground + 9, wz, palette.lantern());
    }

    /** The rock-cut switchback: stairs carved into the face, zigzagging from ward to lip. */
    private static void switchbackStair(Plan plan, AncientPalette palette, int fx, int fz,
                                        Direction up, Direction along, int baseY, int lipY) {
        int a = TOWER_HALF + 2;
        int direction = 1;
        for (int y = baseY + 1; y <= lipY + 1; y++) {
            int x = fx + along.getStepX() * a + up.getStepX();
            int z = fz + along.getStepZ() * a + up.getStepZ();
            // Carve headroom into the face, then lay the tread.
            for (int h = 0; h < 3; h++) {
                plan.set(x, y + h, z, Blocks.CAVE_AIR.defaultBlockState());
                plan.set(x + up.getStepX(), y + h, z + up.getStepZ(),
                    Blocks.CAVE_AIR.defaultBlockState());
            }
            Direction stairFacing = direction > 0 ? along : along.getOpposite();
            plan.set(x, y, z, palette.stairs().setValue(StairBlock.FACING, stairFacing));
            plan.set(x + up.getStepX(), y - 1, z + up.getStepZ(), palette.plinth());

            a += direction;
            if (a > TOWER_HALF + 8 || a < -(TOWER_HALF + 8)) {
                direction = -direction;
                a += direction * 2;
            }
        }
    }
}
