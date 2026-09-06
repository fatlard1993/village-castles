package com.villagecastles.generator.ancient;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;

import java.util.Optional;

/**
 * Reads the shape of the land around a candidate site and names it.
 *
 * <p>Three sampling shells: an inner 5×5 grid at 8-block step (the footprint), a square ring at
 * ±{@link #RING} (what the site stands above, or in), and a sparse mid ring that separates a
 * plateau rim from a gentle hill. Everything is derived from two heightmap questions per column,
 * asked through {@link HeightSampler} so worldgen and the debug commands share one classifier.
 *
 * <p>Classification is first-match-wins in the order island, plateau, cliff, hill: the earlier
 * shapes are the more specific ones, and a site that matches none is rejected outright. Rejection
 * plus structure-set spacing is what makes these castles landmarks.
 */
public final class LandformSurvey {

    private LandformSurvey() {}

    /** A named site: what the land is, where the castle anchors, and which way it faces. */
    public record Site(Landform landform, BlockPos anchor, int radius, Direction facing) {}

    /** Every metric the classifier derived, plus its verdict. The scan command prints all of it. */
    public record Survey(int innerMin, int innerMax, int innerMean, int innerRelief,
                         int ringMean, int drop, int ringBelowCount,
                         int axisGrad, int crossGrad, Direction highSide,
                         int wetInner, int wetMid, int wetRing,
                         Optional<Site> site) {}

    private static final int INNER_STEP = 8;      // 5x5 grid spans ±16
    private static final int MID = 26;
    private static final int RING = 40;

    /** Water deeper than this makes a column "wet" (the attachment survey's convention). */
    private static final int MAX_WATER_DEPTH = 2;

    // Island: surrounded by open water, dry core, high enough to not be a sandbar.
    private static final int ISLAND_WET_RING_MIN = 13;   // of 16
    private static final int ISLAND_WET_MID_MIN = 5;     // of 8
    private static final int ISLAND_WET_INNER_MAX = 4;   // of 25
    private static final int ISLAND_MIN_MEAN = 66;

    // Plateau: flat top, most of the surroundings well below it.
    private static final int PLATEAU_RELIEF_MAX = 5;
    private static final int PLATEAU_DROP = 10;          // ring sample this far below innerMin
    private static final int PLATEAU_RING_LOW_MIN = 10;  // of 16

    // Cliff: one strong directional gradient, a wall rather than a corner.
    private static final int CLIFF_AXIS_MIN = 16;
    private static final int CLIFF_CROSS_MAX = 7;
    private static final int CLIFF_RELIEF_MIN = 8;       // the face crosses the footprint
    private static final int CLIFF_WET_MAX = 2;
    private static final int CLIFF_FOOT_SHIFT = 12;      // anchor slides this far downhill

    // Hilltop: standing above the ring, rounded, and we are actually on the top.
    private static final int HILL_DROP_MIN = 7;
    private static final int HILL_RELIEF_MAX = 9;
    private static final int HILL_TOP_SLACK = 2;

    // Headland: water on three sides, one clearly dry neck. Checked before island, because an
    // island has no dry side to find.
    private static final int HEADLAND_WET_SIDE_MIN = 3;  // of a side's 5 samples
    private static final int HEADLAND_DRY_SIDE_MAX = 1;
    private static final int HEADLAND_WET_RING_MIN = 8;  // of 16
    private static final int HEADLAND_WET_INNER_MAX = 6; // of 25
    private static final int HEADLAND_MIN_MEAN = 64;

    // Ridge: one axis continues (or rises, for a saddle), the crossing axis falls away. Checked
    // before plateau and cliff, both of which would otherwise claim a spine.
    private static final int RIDGE_DROP = 8;
    private static final int RIDGE_CONT_SLACK = 4;
    private static final int RIDGE_WET_INNER_MAX = 2;

    // Eyrie: prominent above every side and either jagged or dramatically high - the summits the
    // hilltop predicate rejects.
    private static final int EYRIE_PROMINENCE = 10;      // innerMean above even the highest side
    private static final int EYRIE_MIN_DROP = 12;
    private static final int EYRIE_RELIEF_MIN = 8;
    private static final int EYRIE_DRAMATIC_DROP = 18;

    public static Optional<Site> classify(HeightSampler sampler, int centerX, int centerZ,
                                          RandomSource random) {
        return survey(sampler, centerX, centerZ, random).site();
    }

    public static Survey survey(HeightSampler sampler, int centerX, int centerZ,
                                RandomSource random) {
        // Inner 5x5. Ground heights carry the terrain shape; wet counts carry the water.
        int[] innerGround = new int[25];
        int innerMin = Integer.MAX_VALUE, innerMax = Integer.MIN_VALUE, innerSum = 0;
        int wetInner = 0;
        int bestInnerX = centerX, bestInnerZ = centerZ, bestInnerY = Integer.MIN_VALUE;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                int x = centerX + (i - 2) * INNER_STEP;
                int z = centerZ + (j - 2) * INNER_STEP;
                int ground = sampler.floor(x, z);
                int surface = sampler.surface(x, z);
                innerGround[i * 5 + j] = ground;
                innerSum += ground;
                if (ground < innerMin) innerMin = ground;
                if (ground > innerMax) innerMax = ground;
                boolean wet = surface - ground > MAX_WATER_DEPTH;
                if (wet) wetInner++;
                if (!wet && ground > bestInnerY) {
                    bestInnerY = ground;
                    bestInnerX = x;
                    bestInnerZ = z;
                }
            }
        }
        int innerMean = innerSum / 25;
        int innerRelief = innerMax - innerMin;

        // Outer square ring: every (dx, dz) with coordinates in {-RING, -RING/2, 0, RING/2, RING}
        // whose larger magnitude is RING. 16 points; corners count toward both their sides.
        int[] sideSum = new int[4];   // Direction.get2DDataValue(): 0=S 1=W 2=N 3=E
        int[] sideCount = new int[4];
        int[] sideWet = new int[4];
        int ringSum = 0, ringCount = 0, wetRing = 0, ringBelowCount = 0;
        int half = RING / 2;
        for (int di = -2; di <= 2; di++) {
            for (int dj = -2; dj <= 2; dj++) {
                if (Math.max(Math.abs(di), Math.abs(dj)) != 2) continue;
                int x = centerX + di * half;
                int z = centerZ + dj * half;
                int ground = sampler.floor(x, z);
                int surface = sampler.surface(x, z);
                boolean wet = surface - ground > MAX_WATER_DEPTH;
                ringSum += ground;
                ringCount++;
                if (wet) wetRing++;
                if (ground <= innerMin - PLATEAU_DROP) ringBelowCount++;
                if (dj == -2) tally(sideSum, sideCount, sideWet, Direction.NORTH, ground, wet);
                if (dj == 2) tally(sideSum, sideCount, sideWet, Direction.SOUTH, ground, wet);
                if (di == -2) tally(sideSum, sideCount, sideWet, Direction.WEST, ground, wet);
                if (di == 2) tally(sideSum, sideCount, sideWet, Direction.EAST, ground, wet);
            }
        }
        int ringMean = ringSum / ringCount;
        int drop = innerMean - ringMean;

        Direction[] horizontals = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        int[] sideMean = new int[4];
        Direction highSide = Direction.NORTH;
        Direction wetSide = Direction.NORTH;
        int highMean = Integer.MIN_VALUE, lowMean = Integer.MAX_VALUE, wetBest = -1;
        for (Direction side : horizontals) {
            int idx = side.get2DDataValue();
            int mean = sideSum[idx] / sideCount[idx];
            sideMean[idx] = mean;
            if (mean > highMean) { highMean = mean; highSide = side; }
            if (mean < lowMean) lowMean = mean;
            if (sideWet[idx] > wetBest) { wetBest = sideWet[idx]; wetSide = side; }
        }
        int axisGrad = highMean - lowMean;
        int aIdx = highSide.getClockWise().get2DDataValue();
        int bIdx = highSide.getCounterClockWise().get2DDataValue();
        int crossGrad = Math.abs(sideMean[aIdx] - sideMean[bIdx]);

        // Mid ring: 8 points at ±MID.
        int wetMid = 0;
        int[][] midOffsets = {{-MID, 0}, {MID, 0}, {0, -MID}, {0, MID},
                              {-MID, -MID}, {-MID, MID}, {MID, -MID}, {MID, MID}};
        for (int[] o : midOffsets) {
            int ground = sampler.floor(centerX + o[0], centerZ + o[1]);
            int surface = sampler.surface(centerX + o[0], centerZ + o[1]);
            if (surface - ground > MAX_WATER_DEPTH) wetMid++;
        }

        int centerGround = sampler.floor(centerX, centerZ);
        int centerSurface = sampler.surface(centerX, centerZ);
        boolean centerDry = centerSurface - centerGround <= MAX_WATER_DEPTH;

        // Headland: exactly one dry side, the other three clearly wet.
        Direction drySide = null;
        int wetSides = 0;
        for (Direction side : horizontals) {
            int idx = side.get2DDataValue();
            if (sideWet[idx] >= HEADLAND_WET_SIDE_MIN) wetSides++;
            else if (sideWet[idx] <= HEADLAND_DRY_SIDE_MAX) drySide = side;
        }

        // Ridge: which axis continues (spine) or rises on both hands (saddle) while the crossing
        // axis stays passable. The gate faces the lower crossing side; the wall runs the other axis.
        Direction ridgeGate = null;
        int n = sideMean[Direction.NORTH.get2DDataValue()];
        int s = sideMean[Direction.SOUTH.get2DDataValue()];
        int e = sideMean[Direction.EAST.get2DDataValue()];
        int w = sideMean[Direction.WEST.get2DDataValue()];
        boolean nsCarries = (n >= innerMean - RIDGE_CONT_SLACK && s >= innerMean - RIDGE_CONT_SLACK
                && e <= innerMean - RIDGE_DROP && w <= innerMean - RIDGE_DROP)
            || (n >= innerMean + RIDGE_DROP && s >= innerMean + RIDGE_DROP
                && e <= innerMean + RIDGE_CONT_SLACK && w <= innerMean + RIDGE_CONT_SLACK);
        boolean ewCarries = (e >= innerMean - RIDGE_CONT_SLACK && w >= innerMean - RIDGE_CONT_SLACK
                && n <= innerMean - RIDGE_DROP && s <= innerMean - RIDGE_DROP)
            || (e >= innerMean + RIDGE_DROP && w >= innerMean + RIDGE_DROP
                && n <= innerMean + RIDGE_CONT_SLACK && s <= innerMean + RIDGE_CONT_SLACK);
        if (nsCarries) ridgeGate = e < w ? Direction.EAST : Direction.WEST;
        else if (ewCarries) ridgeGate = n < s ? Direction.NORTH : Direction.SOUTH;

        Optional<Site> site = Optional.empty();
        if (wetSides == 3 && drySide != null && wetRing >= HEADLAND_WET_RING_MIN
                && wetInner <= HEADLAND_WET_INNER_MAX && centerDry
                && innerMean > HEADLAND_MIN_MEAN) {
            site = Optional.of(new Site(Landform.HEADLAND,
                new BlockPos(centerX, centerGround - 1, centerZ), Landform.HEADLAND.radius,
                drySide));
        } else if (wetRing >= ISLAND_WET_RING_MIN && wetMid >= ISLAND_WET_MID_MIN
                && wetInner <= ISLAND_WET_INNER_MAX && centerDry && innerMean > ISLAND_MIN_MEAN) {
            site = Optional.of(new Site(Landform.ISLAND,
                new BlockPos(bestInnerX, bestInnerY - 1, bestInnerZ), Landform.ISLAND.radius, wetSide));
        } else if (ridgeGate != null && wetInner <= RIDGE_WET_INNER_MAX && centerDry) {
            site = Optional.of(new Site(Landform.RIDGE,
                new BlockPos(centerX, centerGround - 1, centerZ), Landform.RIDGE.radius,
                ridgeGate));
        } else if (innerRelief <= PLATEAU_RELIEF_MAX && ringBelowCount >= PLATEAU_RING_LOW_MIN
                && wetInner == 0) {
            site = Optional.of(new Site(Landform.PLATEAU,
                new BlockPos(centerX, centerGround - 1, centerZ), Landform.PLATEAU.radius,
                highSide));
        } else if (innerMean - highMean >= EYRIE_PROMINENCE && drop >= EYRIE_MIN_DROP
                && wetInner == 0
                && (innerRelief >= EYRIE_RELIEF_MIN || drop >= EYRIE_DRAMATIC_DROP)) {
            site = Optional.of(new Site(Landform.EYRIE,
                new BlockPos(bestInnerX, bestInnerY - 1, bestInnerZ), Landform.EYRIE.radius,
                Direction.from2DDataValue(random.nextInt(4))));
        } else if (axisGrad >= CLIFF_AXIS_MIN && crossGrad <= CLIFF_CROSS_MAX
                && innerRelief >= CLIFF_RELIEF_MIN && wetInner <= CLIFF_WET_MAX) {
            Direction lowSide = highSide.getOpposite();
            int footX = centerX + lowSide.getStepX() * CLIFF_FOOT_SHIFT;
            int footZ = centerZ + lowSide.getStepZ() * CLIFF_FOOT_SHIFT;
            site = Optional.of(new Site(Landform.CLIFF_FACE,
                new BlockPos(footX, sampler.floor(footX, footZ) - 1, footZ),
                Landform.CLIFF_FACE.radius, highSide));
        } else if (drop >= HILL_DROP_MIN && innerRelief <= HILL_RELIEF_MAX
                && centerGround >= innerMax - HILL_TOP_SLACK && wetInner == 0) {
            site = Optional.of(new Site(Landform.HILLTOP,
                new BlockPos(bestInnerX, bestInnerY - 1, bestInnerZ), Landform.HILLTOP.radius,
                Direction.from2DDataValue(random.nextInt(4))));
        }

        return new Survey(innerMin, innerMax, innerMean, innerRelief, ringMean, drop,
            ringBelowCount, axisGrad, crossGrad, highSide, wetInner, wetMid, wetRing, site);
    }

    private static void tally(int[] sum, int[] count, int[] wetCount, Direction side,
                              int ground, boolean wet) {
        int idx = side.get2DDataValue();
        sum[idx] += ground;
        count[idx]++;
        if (wet) wetCount[idx]++;
    }

    /** Skirt beyond the site radius that the fine field covers, for foundations and outer works. */
    public static final int FIELD_SKIRT = 12;
    private static final int FIELD_STEP = 4;

    /** The fine terrain grid for an accepted site, sampled on a 4-block step over radius + skirt. */
    public static HeightField sampleField(HeightSampler sampler, Site site) {
        int reach = site.radius() + FIELD_SKIRT;
        int originX = site.anchor().getX() - reach;
        int originZ = site.anchor().getZ() - reach;
        int samples = (2 * reach) / FIELD_STEP + 1;
        int[] ground = new int[samples * samples];
        int[] cover = new int[samples * samples];
        for (int gx = 0; gx < samples; gx++) {
            for (int gz = 0; gz < samples; gz++) {
                int x = originX + gx * FIELD_STEP;
                int z = originZ + gz * FIELD_STEP;
                ground[gx * samples + gz] = sampler.floor(x, z);
                cover[gx * samples + gz] = sampler.surface(x, z);
            }
        }
        return new HeightField(originX, originZ, FIELD_STEP, samples, samples, ground, cover);
    }
}
