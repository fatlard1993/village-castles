# Garden Review: Castle Generators

Audit of all 15 generator methods in `CastleGenerator.java` (3441 lines).
Focus: coordinate math, bed orientation, stair facing, Y-levels, walkability, structural logic.

## Conventions Verified

These rules were checked against every placement in the file:

- **Beds**: `FACING` points from FOOT toward HEAD. SOUTH = HEAD at higher Z. EAST = HEAD at higher X.
- **Stairs**: `FACING` = direction you face walking UP. For thrones = direction seated player faces.
- **Wall torches**: `FACING` = direction torch points (away from wall). Must have solid block behind.
- **Fence gates**: `FACING` = perpendicular to opening direction.
- **Y-levels**: Floor at baseY, items at baseY+1. Sunken structures: floor at baseY-depth, items at baseY-depth+1.

**All 14 bed pairs, all wall torch placements, all fence gates, and all throne stairs are correctly oriented.** The systemic bed fix from the previous pass resolved all HEAD/FOOT reversals.

## Bugs Found

### CRITICAL

| # | Method | Line(s) | Issue |
|---|--------|---------|-------|
| 1 | `generateIgloo` | ~1103 | **Cellar inaccessible.** Trapdoor at ox+6, cellar at ox+/-1. Access shaft drops into solid stone, not the cellar. 5 blocks off target. |
| 2 | `generateGreatEnclosure` | ~1521 | **Digs below bedrock on superflat.** depth=5 with superflat baseY~-61 gives floorY=-66, below world minimum -64. |

### HIGH

| # | Method | Line(s) | Issue |
|---|--------|---------|-------|
| 3 | `generateDesertPyramid` | ~2489 | **TNT trap removed.** Village castles are inhabited — traps belong in ruins only. |

### MEDIUM

| # | Method | Line(s) | Issue |
|---|--------|---------|-------|
| 4 | `generateDesertOutpost` | ~313 | Fence gate at baseY but interior floor at baseY+1. 1-block step-down at entrance. |
| 5 | `generateTaigaLonghouse` | ~678 | No floor plank at doorway threshold. Interior floor stops 1 block before wall. |
| 6 | `generateDesertPyramid` | ~2871 | Spiral staircase uses full blocks, not stair blocks. Player must jump each step. |

## Clean Methods (No Bugs)

These passed all checks:

- `generatePlainsWatchtower` — beds, ladders, Y-levels, door clearance all correct
- `generateDesertCompound` — delegates to sub-generators, no direct issues
- `generateSavannaEnclosure` — beds correct, ground at baseY-1, hut doors punch through
- `buildRoundHut` — door clears both radius and radius-1, carpet/floor logic sound
- `generateSavannaCompound` — all beds correct, throne at floor level (acceptable), Y-levels consistent
- `generateGreatEnclosure` — beds, stairs, torches all correct (except bedrock depth)
- `generateIcePalace` — all 8 beds correct, lanterns on pillar supports, gate entrance cleared
- `generateMediumFort` — delegates to sub-generators
- `generateLargeGrandCastle` — delegates to sub-generators
- `generatePerimeterFence` — gate facing correct
- `prepareGround` — replaces terrain, doesn't create raised platform
- `buildMoat` — drawbridge gaps correct
- `buildMotte` — stairs face correct direction, headroom cleared

## Fix Plan

### Phase 1: Critical (do now)
1. **Igloo cellar**: Move trapdoor to `(ox+1, baseY+1, oz)` directly above cellar, clear shaft connecting them
2. **Sunken longhouse depth**: Clamp depth to `Math.min(5, baseY + 63)` to stay above y=-63

### Phase 2: High (do now)
3. **Pyramid TNT trap**: Place pressure plate at yFloor-1 directly on TNT at yFloor-2 (remove intervening floor block at that position)

### Phase 3: Medium (polish pass)
4. **Desert outpost gate**: Move to baseY+1 or add step
5. **Taiga longhouse threshold**: Extend floor to include door position
6. **Pyramid spiral stairs**: Use actual stair blocks with appropriate facing

### Phase 4: Content (separate session)
- Plains small watchtower — user says it's ugly, needs redesign (not a bug, a content issue)
- All structures need visual verification in real terrain (not just superflat)
- Hand-polish pass on exported NBTs
