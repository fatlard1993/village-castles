# Garden Review: Village Castles

**Date**: 2026-02-16
**Scope**: Full codebase audit -- architecture, correctness, resources, project health
**Method**: Four independent deep reviews synthesized with cross-validation

---

## Executive Summary

Village Castles has a solid conceptual foundation: clean package structure, elegant BiomePalette enum design, well-separated generator classes, and a good creative-mode workflow for building castle structures. The code is readable and the intent is clear throughout.

However, the project has **no version control**, several **geometry bugs that will produce visibly broken structures**, a **performance chokepoint** that will freeze servers during generation, and an **entirely non-functional worldgen pipeline** (template pools reference NBT files that don't exist). The command system is missing basic permission controls.

This review categorizes findings by severity, identifies cross-cutting themes, and proposes a prioritized action plan.

---

## Table of Contents

1. [Critical Findings](#1-critical-findings)
2. [Warnings](#2-warnings)
3. [Notes](#3-notes)
4. [What's Working Well](#4-whats-working-well)
5. [Cross-Cutting Themes](#5-cross-cutting-themes)
6. [Open Questions](#6-open-questions)
7. [Action Plan](#7-action-plan)

---

## 1. Critical Findings

These are things that are broken, will cause visible failures, or represent significant risk.

### 1.1 No Version Control

The project is **not a git repository**. There is no `.git/` directory, no `.gitignore`, no commit history. A 2,700+ line project with complex spatial algorithms has no safety net for reverting mistakes, no path to collaboration, and no change history.

Additionally, a **736 MB Java heap dump** (`java_pid11208.hprof`) sits in the project root. This contains raw memory contents and must be deleted before any repository is initialized.

**Action**: Initialize git, create `.gitignore`, delete the heap dump, make first commit. This is prerequisite to everything else.

---

### 1.2 Square Tower Crenellations Placed at Wrong Y-Coordinate

`TowerGenerator.java:185-186` -- `generateSquareTower()` passes `height + 1` as the `topY` parameter to `addCrenellations()`, but `addCrenellations()` uses this as an **absolute world Y-coordinate**. For a WALL tower with height 16, crenellations are placed at Y=17 in world space -- underground or far below the tower top -- instead of at `center.getY() + height + 1`.

```java
// Current (broken):
StructureHelper.addCrenellations(world, corner1.withY(center.getY() + height),
    corner2.withY(center.getY() + height), height + 1, palette.getPrimaryWallState());

// Should be:
StructureHelper.addCrenellations(world, corner1.withY(center.getY() + height),
    corner2.withY(center.getY() + height), center.getY() + height + 1, palette.getPrimaryWallState());
```

**Impact**: Every square tower has invisible/underground crenellations. Visually broken.

---

### 1.3 Wall Interpolation Produces Gaps on Diagonal Walls

`WallGenerator.java:36-57` -- Wall length is computed using Euclidean distance (`Math.sqrt(dx*dx + dz*dz)`) and positions are interpolated with `(int)(dx * t)`. For diagonal walls, integer truncation skips grid cells, producing visible gaps. The same bug exists in `generateWithArrowSlits()` (line 110) and `generateWithLighting()` (line 176).

**Impact**: Any non-axis-aligned wall segment will have missing blocks. Currently, `CastleGenerator` only passes axis-aligned coordinates, so this may not manifest in practice -- but the `WallGenerator` API is broken for its general case.

**Fix**: Use Bresenham's line algorithm, or validate that all callers only pass axis-aligned coordinates and add a guard.

---

### 1.4 clearInterior Double-Insets Rooms

`KeepGenerator.java:135-144` passes corners already inset by 1 block from the outer wall. `StructureHelper.clearInterior()` (lines 86-99) applies an additional +1/-1 inset. Result: rooms are shrunk by **2 blocks** on each side instead of 1. For a small keep (width=9), the usable interior is only 5 blocks wide instead of the expected 7.

**Impact**: Keep interiors are visibly cramped. Furniture placement may overlap with the extra ring of uncleared wall blocks.

**Fix**: Remove the inset from either the caller or `clearInterior()`, not both.

---

### 1.5 prepareGround: ~259K Block Updates with Neighbor Notifications

`CastleGenerator.java:138-167` -- For a LARGE castle (radius=35), `prepareGround()` makes approximately **258,750 `setBlockState()` calls**, each triggering neighbor notifications (default flag 3). This causes cascading block updates (water flow, sand gravity, redstone) and will freeze the server for multiple seconds.

Additionally, 40 blocks of air are cleared above ground for every column, regardless of whether those blocks are already air.

**Fix**:
1. Use `world.setBlockState(pos, state, Block.NOTIFY_LISTENERS)` (flag 2) during generation
2. Skip setting blocks that are already the target state
3. Use `BlockPos.Mutable` to reduce GC pressure
4. Consider chunked/async generation for large sizes

---

### 1.6 No Operator Permission on Generation Commands

`GenerateCastleCommand.java:56` -- The command only requires `.isExecutedByPlayer()` with no permission level check. Any player on a multiplayer server can generate massive structures anywhere, overwriting blocks and causing server lag. This is a griefing vector.

```java
// Current:
.requires(ServerCommandSource::isExecutedByPlayer)

// Should be:
.requires(source -> source.isExecutedByPlayer() && source.hasPermissionLevel(2))
```

---

### 1.7 Worldgen Pipeline Is Non-Functional

The mod registers:
- 5 biome tag files (`tags/worldgen/biome/has_structure/`)
- 5 castle template pools + village wall pools
- 1 processor list (`castle_aging.json`)

But is **missing**:
- `worldgen/structure/*.json` -- no structure definitions
- `worldgen/structure_set/*.json` -- no placement rules
- All `.nbt` structure files referenced by template pools

Without these, castles will **never generate naturally** in the world. The template pools and biome tags are orphaned. The command-based procedural generation works, but the worldgen integration is entirely aspirational.

---

### 1.8 BiomePalette.fromId() Never Returns Null -- Validation Is Dead Code

`BiomePalette.java:255` returns `PLAINS` as a default fallback, never `null`. But `GenerateCastleCommand.java:101-106` checks for `null` to show an error for unknown biome names. Since `fromId()` never returns null, **invalid biome names silently generate a Plains castle** with no error message.

This was independently confirmed by three reviewers examining different areas of the codebase.

**Fix**: Change `fromId()` to return `null` on unknown input, or add explicit validation in the command before calling `fromId()`.

---

## 2. Warnings

Issues that don't break things outright but reduce quality, cause confusion, or will become bugs during maintenance.

### 2.1 Staircase Support Blocks Obstruct Interior

`KeepGenerator.java:382-392` -- The support structure fills solid blocks where the staircase interior should be open. Players walking up stairs will hit a wall of `primaryWallState` blocks at Y levels where only the stair treads should exist.

### 2.2 Gate is Single Fence-Gate Height (Knee-Level)

`GateGenerator.java:245-257` -- The portcullis/gate is a single row of `OAK_FENCE_GATE` blocks at Y+1, while the archway opening is 6 blocks tall. One row of knee-height fence gates is not a functional gate.

### 2.3 Hardcoded OAK_FENCE_GATE Ignores BiomePalette

`GateGenerator.java:252` -- Always places oak fence gates regardless of biome. Desert castles get oak gates instead of acacia. The `BiomePalette` enum has no fence gate field.

### 2.4 Hardcoded Block Choices Throughout Generators

| Location | Hardcoded Block | Should Use |
|----------|----------------|------------|
| `GateGenerator.java:252` | `OAK_FENCE_GATE` | Palette fence gate |
| `KeepGenerator.java:241-246` | `RED_BED` | Palette bed color |
| `KeepGenerator.java:185, 279-282` | `RED_CARPET` | Palette carpet color |
| `CastleGenerator.java:431-433` | `RED_WOOL` (bed placeholder) | Actual `BedBlock` |

### 2.5 Duplicate Cylinder/Tower Logic

`GateGenerator.buildTower()` (lines 82-136) duplicates nearly all of `TowerGenerator.generateCylindricalTower()` (lines 60-127). Any fix to cylinder rendering must be applied in two places.

### 2.6 Chest Facing Not Applied

`StructureHelper.java:201-202` -- `placeChest()` accepts a `facing` parameter but never applies it to the block state. All chests face north.

### 2.7 Back Gate Position Is Identical to North Gate

`CastleGenerator.java:80,123` -- `backGatePos` and `northGatePos` compute the exact same position with different variable names. Not a functional bug yet, but a maintenance trap.

### 2.8 Wall THICKNESS Constant Is Misleading

`WallGenerator.java:22` -- `WALL_THICKNESS = 2` but the loop `for (p = -THICKNESS/2; p <= THICKNESS/2; p++)` produces 3 iterations. Walls are always 3 blocks thick. The name does not match the behavior.

### 2.9 README States Wrong Minecraft Version

README says "Minecraft 1.21.4" but `gradle.properties` targets **1.21.11**. Misleading for anyone evaluating the mod.

### 2.10 Unused Localization -- All Strings Hardcoded

`en_us.json` defines 9 translation keys. **Zero** are used anywhere in the codebase. All command output uses `Text.literal()` with hardcoded English strings. The `export.success`/`export.failed` keys reference an export command that doesn't exist.

### 2.11 Missing Mod Icon

`fabric.mod.json` references `assets/villagecastles/icon.png` which does not exist on disk.

### 2.12 No Progress Feedback for Long Operations

Large castle generation can take seconds. The player sees "Generating..." then nothing until "Castle generated!" or a timeout. No intermediate progress.

### 2.13 No Dimension or Bounds Validation

No checks for: Nether ceiling collisions, End void, world border proximity, unloaded chunks, or player altitude (flying creates sky castles, caves create underground castles).

### 2.14 Exception Logging Discards Stack Traces

`VillageBuilderIntegration.java` catch blocks log only `e.getMessage()`, not the full throwable. Makes debugging impossible for end users.

### 2.15 Only Plains Has Complete Village Wall Pools

Plains has `walls.json`, `gates.json`, and `corners.json`. Desert, savanna, taiga, and snowy only have `walls.json`. The Java code registers all segment types for all biomes.

### 2.16 Castle Template Pools Don't Use castle_aging Processor

All 5 castle template pools specify `minecraft:empty` as their processor, despite a `castle_aging` processor existing. Castles would appear pristine with no weathering.

### 2.17 Fabric API Version Unconstrained

`fabric.mod.json` declares `"fabric-api": "*"`. An older Fabric API missing required APIs would cause runtime crashes instead of a clear version error.

---

## 3. Notes

Lower-priority observations for eventual cleanup.

| # | Finding | Location |
|---|---------|----------|
| 3.1 | Generation always goes north regardless of player facing | `GenerateCastleCommand.java:123` |
| 3.2 | "Use Structure Blocks to save it as NBT" confuses non-developer players | `GenerateCastleCommand.java:139` |
| 3.3 | Multiple magic numbers (5, 8, 10, 12, 40) without named constants | Throughout generators |
| 3.4 | `java.util.Random` used instead of Minecraft's random system | All generator files |
| 3.5 | Stables have no door -- sealed box | `CastleGenerator.java:333-385` |
| 3.6 | Barracks beds are wool blocks, not actual BedBlocks | `CastleGenerator.java:430-434` |
| 3.7 | No interior lighting in stables/barracks (mob spawning) | `CastleGenerator.java` |
| 3.8 | `StructureHelper.createArchedDoorway()` has incomplete TODO | `StructureHelper.java:186-196` |
| 3.9 | Spiral stair math depends on `stepsPerRotation` being a double | `StructureHelper.java:218-228` |
| 3.10 | SNOWY palette uses packed ice (melts near light sources) | `BiomePalette.java:124` |
| 3.11 | Pointed dripstone on palisade tips has gravity physics | `VillageWallGenerator.java:128-131` |
| 3.12 | Log blocks always vertical -- wrong for horizontal beams | `BiomePalette.java:218-219` |
| 3.13 | Seed collision risk from weak hash mixing | `GenerateCastleCommand.java:130` |
| 3.14 | Unused imports in multiple files | GateGenerator, VillageWallGenerator, StructureHelper |
| 3.15 | `registerPlainscastle` naming inconsistency (lowercase 'c') | `VillageBuilderIntegration.java:84` |
| 3.16 | `mod_version` is 1.0.0 but project is clearly alpha | `gradle.properties` |
| 3.17 | `maven-publish` plugin included but not configured | `build.gradle` |
| 3.18 | Datagen run configured in build.gradle but no entrypoint or providers exist | `build.gradle:19-28` |
| 3.19 | No `.editorconfig` or code formatting configuration | Project root |
| 3.20 | No tests of any kind | No `src/test/` directory |
| 3.21 | `VillageWallGenerator` shares no interface or base class with `WallGenerator` | `generator/` package |
| 3.22 | Courtyard features (well, stables, barracks) inline in CastleGenerator | `CastleGenerator.java:293-438` |

---

## 4. What's Working Well

| # | Finding | Location |
|---|---------|----------|
| 4.1 | **Clean orchestrator pattern** -- CastleGenerator delegates cleanly to sub-generators | `CastleGenerator.java` |
| 4.2 | **Elegant BiomePalette enum** -- compile-time safe, internally consistent block families, extensible | `BiomePalette.java` |
| 4.3 | **Well-structured Brigadier command tree** with suggestion providers derived from enum values | `GenerateCastleCommand.java:35-91` |
| 4.4 | **Graceful Village Builder integration** -- checks mod presence, catches all reflection failures, never crashes | `VillageBuilderIntegration.java` |
| 4.5 | **Minimal, clean mod entry point** -- 28 lines, correct initialization order | `VillageCastles.java` |
| 4.6 | **Good Javadoc** with ASCII art castle layout diagram | `CastleGenerator.java:16-25` |
| 4.7 | **Consistent code style** throughout -- naming, formatting, indentation | All files |
| 4.8 | **Exception handling in commands** prevents server crashes, logs full traces server-side | `GenerateCastleCommand.java` |
| 4.9 | **Clean Fabric Loom build** with proper Java 21 targeting, sources jar, license inclusion | `build.gradle` |
| 4.10 | **Detailed README** with feature descriptions, command usage, workflow guide | `README.md` |

---

## 5. Cross-Cutting Themes

### Theme A: The Two-Path Architecture

The mod has two distinct generation paths that are disconnected:

1. **Procedural command-based generation** (`/villagecastles generate`) -- works, generates structures in real-time using the Java generator classes
2. **Worldgen data-pack integration** (template pools, biome tags, NBT structures) -- scaffolded but non-functional, missing structure definitions and NBT files

The README describes a workflow where path 1 is used to create structures, which are then saved as NBT to feed path 2. But path 2 is incomplete, and the scaffolding (template pools pointing to nonexistent NBT files) creates confusion about what works.

**This is the most important architectural question to resolve**: Is the mod a generation tool (path 1), a worldgen content pack (path 2), or both? The answer determines which findings are relevant.

### Theme B: The Palette Gap

BiomePalette is well-designed for what it covers, but generators frequently reach outside the palette for blocks it doesn't define (fence gates, beds, carpets, bed colors, banners). Each hardcoded block is a place where biome theming breaks.

### Theme C: Block Placement Performance

Every `setBlockState` call uses the default flag (3 = notify + update). For structure generation placing hundreds of thousands of blocks, this is a significant performance anti-pattern. Combined with per-block `new BlockPos()` allocation and no air-check optimization, generation of large structures will cause noticeable server lag.

### Theme D: Validation Gaps

The command system has almost no environmental validation: no permission checks, no dimension checks, no bounds checks, no chunk loading verification. The `BiomePalette.fromId()` fallback creates a silent-failure path for invalid input. The system trusts everything and validates nothing.

---

## 6. Open Questions

These are ambiguities where the reviewer cannot determine intent from the code alone. The answers will significantly affect which recommendations are relevant.

**Q1: What is the primary delivery mechanism?**
Is this mod meant to (a) generate structures procedurally via commands for creative-mode building, (b) add structures to natural worldgen via data packs, or (c) both? This determines whether the worldgen scaffolding should be completed or removed.

**Q2: Is the Village Builder integration the primary distribution path?**
If Village Builder is the intended way castles appear in villages, the worldgen data pack may be secondary. If Village Builder is just a bonus, the data pack needs to be completed.

**Q3: What is the target audience?**
If this is a creative-mode tool for mapmakers, the NBT workflow tip makes sense and OP permissions may be unnecessary. If it's a survival-mode content mod, permissions are critical and the "save as NBT" message is confusing.

**Q4: Is the version number intentional?**
`mod_version=1.0.0` for a project with missing NBT files, no worldgen structures, no tests, and no git -- is this a placeholder, or does the author consider the procedural generation feature complete?

---

## 7. Action Plan

Organized into phases, highest priority first. Each phase builds on the previous.

### Phase 0: Foundation (Do First)

| # | Task | Files | Effort |
|---|------|-------|--------|
| 0.1 | Delete `java_pid11208.hprof` | Project root | 1 min |
| 0.2 | Create `.gitignore` (see appendix) | Project root | 5 min |
| 0.3 | `git init` and initial commit | Project root | 5 min |
| 0.4 | Change `mod_version` to `0.1.0-alpha` | `gradle.properties` | 1 min |
| 0.5 | Fix README Minecraft version (1.21.4 -> 1.21.11) | `README.md` | 1 min |

### Phase 1: Fix Broken Geometry

| # | Task | Files | Effort |
|---|------|-------|--------|
| 1.1 | Fix square tower crenellation Y-coordinate | `TowerGenerator.java:185-186` | 10 min |
| 1.2 | Fix `clearInterior` double-inset | `KeepGenerator.java:135-144` or `StructureHelper.java:86-99` | 15 min |
| 1.3 | Fix staircase support block obstruction | `KeepGenerator.java:382-393` | 20 min |
| 1.4 | Fix `BiomePalette.fromId()` to return null on unknown | `BiomePalette.java:255` | 5 min |
| 1.5 | Apply chest facing parameter | `StructureHelper.java:201-202` | 5 min |
| 1.6 | Fix or guard diagonal wall interpolation | `WallGenerator.java:36-57` | 30 min |

### Phase 2: Performance & Safety

| # | Task | Files | Effort |
|---|------|-------|--------|
| 2.1 | Add `Block.NOTIFY_LISTENERS` flag (2) to all `setBlockState` in generators | All generator files | 30 min |
| 2.2 | Use `BlockPos.Mutable` in hot loops | `CastleGenerator.java`, `StructureHelper.java` | 20 min |
| 2.3 | Skip setting blocks already in target state | `CastleGenerator.prepareGround()` | 15 min |
| 2.4 | Add OP level 2 requirement to generation commands | `GenerateCastleCommand.java:56` | 5 min |
| 2.5 | Add basic dimension/bounds validation | `GenerateCastleCommand.java` | 30 min |

### Phase 3: Polish & Completeness

| # | Task | Files | Effort |
|---|------|-------|--------|
| 3.1 | Add fence gate, bed color, carpet color to BiomePalette | `BiomePalette.java` | 30 min |
| 3.2 | Replace hardcoded blocks with palette lookups | `GateGenerator.java`, `KeepGenerator.java`, `CastleGenerator.java` | 30 min |
| 3.3 | Replace `Text.literal()` with `Text.translatable()` | `GenerateCastleCommand.java` | 45 min |
| 3.4 | Update `en_us.json` with all command strings | `lang/en_us.json` | 20 min |
| 3.5 | Remove orphaned export keys from lang file | `lang/en_us.json` | 5 min |
| 3.6 | Add mod icon | `assets/villagecastles/icon.png` | 10 min |
| 3.7 | Extract magic numbers to named constants | Throughout generators | 30 min |
| 3.8 | Add door to stables, replace wool beds with BedBlocks in barracks | `CastleGenerator.java` | 20 min |
| 3.9 | Add interior lighting to stables/barracks | `CastleGenerator.java` | 15 min |

### Phase 4: Architecture (Depends on Q1-Q3 Answers)

| # | Task | Condition | Effort |
|---|------|-----------|--------|
| 4.1 | Create `worldgen/structure/` JSONs for all 5 biomes | If worldgen path pursued | 1 hr |
| 4.2 | Create `worldgen/structure_set/` JSONs | If worldgen path pursued | 30 min |
| 4.3 | Generate and save NBT structure files | If worldgen path pursued | 2 hr |
| 4.4 | Complete village wall pools for all biomes (gates, corners) | If worldgen path pursued | 1 hr |
| 4.5 | Wire `castle_aging` processor into castle template pools | If worldgen path pursued | 10 min |
| 4.6 | Remove orphaned worldgen scaffolding | If worldgen path NOT pursued | 30 min |
| 4.7 | Extract courtyard features to `CourtyardGenerator` | Cleanup | 1 hr |
| 4.8 | Deduplicate `GateGenerator.buildTower()` / `TowerGenerator` | Cleanup | 45 min |

### Phase 5: Infrastructure

| # | Task | Effort |
|---|------|--------|
| 5.1 | Remove orphaned datagen configuration from `build.gradle` (or implement providers) | 15 min |
| 5.2 | Remove or configure `maven-publish` plugin | 5 min |
| 5.3 | Set minimum Fabric API version in `fabric.mod.json` | 5 min |
| 5.4 | Add basic unit tests for BiomePalette and geometry helpers | 2 hr |
| 5.5 | Set up CI (GitHub Actions `./gradlew build`) | 30 min |
| 5.6 | Clean up unused imports | 10 min |

---

## Appendix: Recommended .gitignore

```
# Gradle
.gradle/
build/

# IDE
.idea/
*.iml
.vscode/

# Minecraft runtime
run/

# Java artifacts
*.hprof
*.class

# Fabric Loom
remappedSrc/

# OS
.DS_Store
Thumbs.db

# Generated resources (regenerate with gradle)
src/main/generated/
```

---

## Appendix: Finding Count

| Severity | Count |
|----------|-------|
| CRITICAL | 8 |
| WARNING | 17 |
| NOTE | 22 |
| PRAISE | 10 |

---

*Review conducted by four independent gardeners examining: generator architecture, command system, integration/resources, and project health. Findings cross-validated where overlapping.*
