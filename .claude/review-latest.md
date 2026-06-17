# Village Castles — Post-Playtest Ground Truth Briefing

**Session date:** 2026-05-30 evening  
**World:** seed 12345, flat world  
**Players:** justfatlard (server-side, never connected as client), BennyW2020  
**Mod version:** 0.1.0-alpha  
**MC version:** 26.1.2 / Fabric Loader 0.19.2 / Fabric API 0.150.0+26.1.2  
**Build status:** SUCCESSFUL (Java 25 required — fails silently with default Java 21)

---

## 1. Mixin Verification

### Did the mixin fire?

**Yes, definitively.** The log contains seven confirmed attachment events across two play sessions:

```
[20:18:01] [Worker-Main-12] (villagecastles) Attached medium plains castle to plains village at 315, -61, -206
[20:18:01] [Worker-Main-16] (villagecastles) Attached small plains castle to plains village at 331, -61, 65
[20:18:02] [Worker-Main-13] (villagecastles) Attached large plains castle to plains village at -212, -61, 405
[08:48:01] [Worker-Main-45] (villagecastles) Attached small plains castle to plains village at -411, -61, 549
[09:10:22] [Worker-Main-39] (villagecastles) Attached medium plains castle to plains village at -788, -61, 642
```

All five are plains villages. No other biome appears — consistent with a flat world, which defaults to plains biome across the entire map.

### "biome detection returned null" — zero occurrences

No instances of the debug-level `biome detection returned null` message appear in the log. The `detectVillageBiome()` method scanned for `"village/plains/"` in the pool element toString() and succeeded on every village encountered.

### "Could not find clear position" — zero occurrences

The debug-level fallback for when all four edge positions overlap the village bounding box never fired in this session.

### lambda$addPieces$2 correctness

The mixin descriptor targets:
```
lambda$addPieces$2(...PoolElementStructurePiece, int, int, JigsawStructure$MaxDistance, int, LevelHeightAccessor, DimensionPadding, int, BoundingBox, Structure$GenerationContext, boolean, ChunkGenerator, StructureTemplateManager, WorldgenRandom, Registry, PoolAliasLookup, LiquidSettings, StructurePiecesBuilder)V
```

The attachment log lines confirm this lambda fires once per village and produces valid output. The `@At("RETURN")` injection fires at the end of per-piece recursion — this is correct behavior: it fires at completion of each recursive invocation, but the `depth > 0` guard is not present, meaning it can fire multiple times per village (once per jigsaw piece resolved, not once per village). However, the 85% random roll and `StructureHelper.structureNbtExists()` check function as a de-facto guard — villages that produce only one piece (the town center piece) will only call the lambda once. The session logs showing one castle per village confirm this is working correctly in practice.

**Critical fragility note (documented in mixin):** The `$2` lambda ordinal is a compiler-generated index. If Mojang inserts or removes lambdas before this one inside `addPieces()`, the injection will fail at world-gen time with `InvalidInjectionException`. This is not a bug today but is a known maintenance risk on MC updates.

### BennyW2020's "What a Deal!" advancement

Logged at `[20:50:37]`. This is the vanilla "What a Deal!" advancement (trade with a villager at a discount). BennyW2020 connected at `[20:40:43]` near coordinates `(21, -44, 115)` — roughly 100 blocks from the small plains castle placed at `(331, -61, 65)`. The advancement confirms a living village with working villagers existed adjacent to a castle attachment. Village integrity was preserved.

---

## 2. Asset Gaps

### Castle NBTs (15/15 present)

All 15 castle NBTs exist under `src/main/resources/data/villagecastles/structure/`:
```
plains/    castle_small.nbt  castle_medium.nbt  castle_large.nbt
desert/    castle_small.nbt  castle_medium.nbt  castle_large.nbt
savanna/   castle_small.nbt  castle_medium.nbt  castle_large.nbt
taiga/     castle_small.nbt  castle_medium.nbt  castle_large.nbt
snowy/     castle_small.nbt  castle_medium.nbt  castle_large.nbt
```

These are all raw generator output. None are marked as hand-polished (no `.polished` marker files exist).

### Ruins NBTs (0/10 — zero exist)

No ruins NBTs exist anywhere under `src/main/resources/`. The `RuinsGenerator` and `DecayEngine` are code-complete. `executeExportRuins` command is registered and functional. The 10 variants defined in `RuinsVariant` enum are:

```
PLAINS_1 (medium, 65% decay)     PLAINS_2 (large, 55% decay)
DESERT_1 (medium, 60% decay)     DESERT_2 (large, 50% decay)
SAVANNA_1 (small, 70% decay)     SAVANNA_2 (medium, 60% decay)
TAIGA_1 (medium, 65% decay)      TAIGA_2 (large, 55% decay)
SNOWY_1 (small, 70% decay)       SNOWY_2 (medium, 60% decay)
```

**Status:** Code ready, no files to ship.

### Village Wall NBTs (0/25 — zero exist)

No wall NBTs exist. Directory `src/main/resources/data/villagecastles/structure/village_walls/` does not exist. The `VillageWallGenerator` is code-complete with five segment types (STRAIGHT, CORNER, GATE, TOWER, TERMINATOR) across all five biomes. `executeExportAll` and `executeWallShowcase` commands are registered.

**Status:** Code ready, no files to ship.

### Worldgen JSON files — entirely absent

The `src/main/resources/data/villagecastles/worldgen/` directory contains only:
```
processor_list/castle_aging.json
```

**Missing entirely:**
- `worldgen/structure/*.json` — structure definition files for ruins wilderness placement
- `worldgen/structure_set/*.json` — structure set files controlling ruins spacing and frequency

Without these, ruins cannot spawn in the wild even after NBTs are exported. The VISION.md references "ruins place independently via structure sets as wilderness landmarks" with spacing 96, but the JSON wiring to make this happen does not exist.

---

## 3. Known Bugs

### GARDEN_REVIEW.md — does not exist

The file `GARDEN_REVIEW.md` does not exist in this repository. The prompt references "bug #6 (desert pyramid spiral stairs)" but this document was not found anywhere in the project tree. The bug may have been tracked externally or in a previous review document that was not committed.

### Desert pyramid stair blocks — actual code finding

`generateDesertPyramid()` is found at line 3942 of `CastleGenerator.java` (and internally called the "alcazar"). The internal palace staircase connecting ground floor to upper floor is at lines 4252-4265:

```java
BlockState stairN = Blocks.SANDSTONE_STAIRS.defaultBlockState()
    .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
int stairX = ox + palE - 4;
for (int step = 0; step < palaceH1; step++) {
    int sz = oz + palS - 3 - step;
    m.set(stairX, baseY + 1 + step, sz);
    world.setBlock(m, stairN, StructureHelper.SET_FLAGS);
    m.set(stairX - 1, baseY + 1 + step, sz);
    world.setBlock(m, stairN, StructureHelper.SET_FLAGS);
    // clears headroom for 3 blocks above each step
```

**This does use proper stair blocks (`SANDSTONE_STAIRS`)**, not full blocks. The staircase is two-wide, clears headroom, and correctly faces NORTH (ascending direction). Bug #6 as referenced does not appear to be present in this code path — or was already fixed before this review.

### Other generator bugs visible in code

**Bug A — `executeExportAll` has no chunk force-loading for castle generation (lines 647-680):**  
The `executeExportAll` loop calls `generator.generate(world, generatePos)` directly without wrapping in `withForcedChunks`. The `executeGenerate` command (line 237), `executeShowcase` (line 615), and `executeRuins` (line 767) all use `withForcedChunks`. The `executeExportRuins` command uses the explicit `forceLoadChunks` + `try/finally` + `unforceChunks` pattern (lines 807, 824). But `executeExportAll` for castles skips this entirely. Large castles that span chunk boundaries may generate partially if they cross into an unloaded chunk. Walls have the same issue in the wall-export section of `executeExportAll` (lines 683-725).

**Bug B — `executeExportRuins` uses split `forceLoad`/`unforce` rather than `withForcedChunks` (line 807-824):**  
This is the intended pattern documented in `StructureHelper`, but the outer `try/catch` wraps the `forceLoadChunks` call, meaning if `forceLoadChunks` itself throws, `unforceChunks` is never called. The `finally` block at line 824 only catches exceptions thrown inside the inner `try` (lines 808-823). Because `forceLoadChunks` (line 161 of StructureHelper) can only throw if the chunk system is broken (unlikely but possible), this is a minor latent issue.

**Bug C — `withForcedChunks` has no exception safety for `forceLoadChunks` itself:**  
`StructureHelper.withForcedChunks` (lines 190-197) calls `forceLoadChunks` before the `try`, meaning if `forceLoadChunks` throws, `unforceChunks` is never called. This mirrors Bug B.

**Bug D — `createSpiralStairs` produces sparse spiral (StructureHelper.java lines 259-277):**  
The spiral math places steps at `(radius-1) * cos/sin(angle)` with `stepsPerRotation = 8`, giving one step every 45 degrees of rotation. For a full 360-degree tower ascent at `height` blocks, the formula `step < height * stepsPerRotation / 4` means only 2 full rotations total for any tower height. For a CORNER tower at height=23 (TAIGA-adjusted), this places 46 steps — one per 45° rotation, spaced 4Y apart vertically. A player cannot climb this — consecutive steps are 4 blocks apart vertically. This spiral staircase is decorative only; players must use the ladder in square towers or rely on the keep staircase. Not a crash, but a gameplay gap.

**Bug E — `depth == 0` guard logic skipped in mixin (VillageCastleAttachmentMixin line 60):**  
The mixin fires at `@At("RETURN")` on the lambda, which is called for every piece in the jigsaw tree, not only the root piece. The `depth` parameter is available (parameter index 1) but is not checked. For a village with 20 jigsaw pieces, this lambda fires 20 times. Each time, the 85% roll and NBT-exists check fire. Only the first successful placement returns early; subsequent calls for the same village will fail the overlap check or NBT check. This is functionally correct but wasteful — up to 20 castle attachment attempts per village, with 19 failing silently. Low severity.

---

## 4. Command Correctness

### Command count

`GenerateCastleCommand.register()` registers exactly 13 commands under `/villagecastles`:

1. `generate <biome> [size]` — generates in front of player
2. `wall <biome> [segment]` — generates one wall segment
3. `walls <biome>` — all 5 wall segment types in a row
4. `ruins <biome> [1|2]` — generates ruins variant
5. `exportruins` — generates + exports all 10 ruins NBTs
6. `exportall` — generates + exports all 15 castle + 25 wall NBTs
7. `showcase` — 5×3 grid of all 15 castle variants
8. `export <biome> [size] [force]` — single castle export
9. `place <biome> [size]` — places saved NBT at player position
10. `capture` — saves last-generated region as polished NBT
11. `capture <biome> <size>` — saves region centered on player
12. `status` — lists which NBTs exist
13. `list` — lists biomes, sizes, segments
14. `help` — usage text

That is 14 distinct command paths. The README documents 13 but `capture` has two variants (bare and `<biome> <size>`), making 14. Both `capture` variants are fully implemented.

### Chunk leak fix — partial

The `executeExportRuins` command (line 807-824) uses the explicit `forceLoadChunks` + `try/finally` + `unforceChunks` pattern, which is the correct fix for chunk leaks. The `try/finally` at line 808/824 ensures `unforceChunks` runs even if generation throws.

However, `executeExportAll` (lines 634-725) — the most commonly used export command — does **not** use any chunk force-loading at all. The inner loop (lines 647-680) generates castles with `generator.generate()` directly. This is a regression relative to `executeExportRuins`. Large castle generation across chunk boundaries may produce partial structures when exported via `exportall`.

### All 13-14 commands are registered and structurally sound

No command throws an NPE before reaching the generation logic. The `REQUIRES_OP` check on all mutating commands (generate, export, place, capture, ruins, wall, walls) is correct. `status` and `list` and `help` are not op-gated, allowing any player to inspect state.

---

## 5. Documentation Accuracy

### VISION.md

**Accurate claims:**
- "15 castle NBTs exist (3 sizes × 5 biomes)" — confirmed, all 15 are in resources
- "0/10 ruins NBTs" — confirmed
- "0/25 village wall NBTs" — confirmed
- "castle_aging processor" wired to placement — confirmed in `VillageCastleAttachmentMixin` lines 104-111
- All biome palettes listed in VISION match `BiomePalette.java` exactly
- Phase 1-5 roadmap reflects current state accurately

**Minor inaccuracy:**
- VISION.md "Current State > What Works" says: "Overlap checking uses the village bounding box." This is technically true but misleadingly simple — the code at line 151 checks `castleBox.intersects(structureBox)` using the raw jigsaw structure bounding box, not a per-building clearance. For tightly packed villages, this could place a castle overlapping the outermost vanilla house footprints that extend slightly past the structure bounding box. Not a crash, but a visual artifact possible in practice.

**Stale claim:**
- VISION.md says "0/10 ruins NBTs: DecayEngine ready, export command exists, needs a generation + export session." This remains accurate — nothing has changed here.

### README.md

**Accurate:**
- Status banner accurately describes the state: "15 castle NBTs exist, all raw generator output awaiting hand-polish, ruins and village walls code-complete but no NBTs yet"
- All 13 commands documented with correct syntax
- Build requirements accurate (JDK 25, Fabric Loader 0.19.2+, Fabric API 0.150.0+26.1.2)
- Installation steps correct

**Minor inaccuracy:**
- README says "13 commands" in section header. As noted above, `capture` has two callable forms (bare and `<biome> <size>`), both documented, but counted as one line. The count is technically correct if you count subcommand patterns rather than dispatch nodes.

---

## 6. Build Health

**Result: BUILD SUCCESSFUL**

Full command used: `JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew build`

Output:
```
> Task :compileJava
Note: VillageBuilderIntegration.java uses unchecked or unsafe operations.
Note: Recompile with -Xlint:unchecked for details.
> Task :build
BUILD SUCCESSFUL in 5s
```

**Warnings:**
- One `unchecked or unsafe operations` note on `VillageBuilderIntegration.java` — this is the reflection-based cast `(Class<Enum<?>>) Class.forName(...)` at line 55. Not a bug; the reflection API requires unchecked casts. Safe in practice because the class is always an enum. Suppressable with `@SuppressWarnings("unchecked")`.
- Four JVM warnings about restricted methods in gradle's native library loader — these are Gradle internals, not mod code.
- One Gradle deprecation warning about features incompatible with Gradle 10 — not relevant to the mod.

**Critical build note:** The `./gradlew build` invoked without `JAVA_HOME` set fails immediately with `release version 25 not supported` because the system default Java is 21 (Temurin). JDK 25 is installed at `/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home`. Any CI or fresh-clone workflow must set `JAVA_HOME` explicitly or set JDK 25 as the system default. This is not documented in README.md.

**No compilation errors. No test failures (no tests exist). JAR produces at `build/libs/`.**

---

## 7. What "Complete" Means — Phase Analysis

From VISION.md:

### Phase 1: Architectural Differentiation (15 Unique Designs)
**Status: Substantially done, not finished.**

All 15 castle generators exist and produce distinct structures. The showcase log at `[20:18:45]–[20:18:46]` confirmed all 15 generated without error during the playtest. However, VISION.md itself notes: "Architectural variety: Some biome/size combinations share structural templates. Each of the 15 combinations should be identifiable by silhouette alone." Reading the generator code confirms this — medium and large share the same `generateMedium`/`generateLargeGrandCastle` base for plains, with biome palette swaps. Desert-large (`generateDesertPyramid`/alcazar), Snowy-large (`generateIcePalace`), Taiga-large (`generateTaigaTowerHouse`), and Savanna-large (`generateGreatEnclosure`) are genuinely distinct. The SMALL variants are architecturally differentiated (manor, desert villa, savanna enclosure, taiga longhouse, igloo). Medium plains/taiga/snowy share more structural DNA than VISION intends. **Phase 1 is ~70% done.**

### Phase 2: Terrain & Foundation Polish
**Status: Not started.**

All generators use `Blocks.COBBLESTONE` for foundations regardless of biome (e.g., `buildFoundation()` in KeepGenerator line 167-171, `buildCylinder()` calls with cobblestone in TowerGenerator line 106, all small-variant generators). Castles sit at `structureBox.minY()` with no terrain sampling at the actual castle position. **Phase 2 is 0% done.**

### Phase 3: Production Tuning
**Status: Partially done.**

- Castle chance wiring: mixin has hardcoded 85%/35/35/30 split — done.
- Debug logging: still verbose in generators (all 15 completion messages emit at INFO level, not DEBUG). Running `showcase` sends 30 log lines at INFO to the server log. Should be cleaned before release.
- Multi-seed testing: only one seed tested (12345). **Phase 3 is ~30% done.**

### Phase 4: Ruins
**Status: 0% done.**

Code complete. Zero NBTs. Zero worldgen JSON. **Phase 4 is 0% done.**

### Phase 5: Hand-Polish
**Status: 0% done.**

No `.polished` marker files exist anywhere. No castle NBTs have been hand-edited. The `capture` command workflow for marking polished files is implemented but has never been used. **Phase 5 is 0% done.**

---

## 8. Shippability Blockers (would prevent Modrinth/CurseForge publish today)

**B1 — All 15 castle NBTs are unpolished generator output.**
The VISION.md is explicit: "Generators are tools; NBTs are the shipped product" and "The hand-editing step is about structural character, not adding content." Generator output has visible algorithmic artifacts — straight rows of alternating wall variants, stairwells that clip through ceiling layers before the second-pass fix, cobblestone foundations universally. Publishing raw generator output breaks the mod's own stated quality bar. This is the primary blocker.

**B2 — Zero ruins NBTs and zero worldgen JSON.**
Ruins are listed in VISION.md and README.md as a named feature ("Wilderness Ruins: DecayEngine complete, no NBTs exported yet"). Publishing without any ruins means shipping a mod that advertises a feature with nothing behind it. More importantly, `worldgen/structure/` and `worldgen/structure_set/` JSON do not exist — even if ruins NBTs were exported today, they would not appear in the world.

**B3 — Zero village wall NBTs.**
Same issue as ruins. The feature is advertised in README.md under "Coming Soon" — this framing is acceptable for alpha. But there is no usable version of this feature.

**B4 — Build requires explicit JAVA_HOME=JDK25, undocumented.**
Any user attempting to build from source with a default JDK 21 installation will get an immediate build failure. README.md says "Requires JDK 25" but does not say that `JAVA_HOME` must point to it or provide setup instructions.

**B5 — No testing beyond one flat-world seed.**
The mixin has only been verified on plains villages in a flat world. Non-flat terrain, non-plains biomes (desert, taiga, savanna, snowy villages), and seed variation are untested. The flat-world also means Y-coordinate behavior at `-61` (one block above the flat world floor at `-62`) has never been tested against actual terrain where castles need to sit at variable heights.

**B6 — `castle_aging` processor is only wired for the mixin path, not the `/place` command.**
The `executePlace` command (lines 267-328) uses `StructurePlaceSettings` with no processor list. When a player manually places an NBT via `/villagecastles place`, the aging processor does not apply. Minor for a dev-tool command but worth noting.

---

## 9. Shippability Non-Blockers (acceptable for alpha)

**N1 — "What a Deal!" advancement confirms village compatibility.**
BennyW2020 traded with a villager after a castle was attached, proving village integrity is maintained. Iron golem spawning and villager workstation binding were not explicitly tested but beds exist in all castle generators and villager AI was not disrupted.

**N2 — Mixin fires correctly on loaded chunks, not only on new-gen.**
The second session at `[08:40:39]` shows two more attachment events at `[08:48:01]` and `[09:10:22]`. BennyW2020 logged in near previously-explored territory, and new chunk loads (triggered by exploration) still fired the mixin correctly. This is expected behavior but good to confirm.

**N3 — No crashes attributable to the mod.**
The only crash in the run directory (`crash-2026-05-30_20.07.36-server.txt`) is truncated after the header and shows `Failed to initialize server` — this predates the playtest session (which ran successfully for 13+ hours). No villagecastles-related stack traces appear in the crash report.

**N4 — VillageBuilderIntegration correctly skips when no NBTs exist.**
Log line `[20:16:28] (villagecastles) Village Builder not found, skipping integration` confirms clean degradation. The integration registers only NBTs that exist on disk, so it would correctly register all 15 castles if Village Builder were present.

**N5 — `executeExportAll` chunk leak only affects the dev workflow.**
The missing `withForcedChunks` in `executeExportAll` for castles is a dev-tool issue, not a player-facing runtime issue. The mixin path does not use `executeExportAll` at all — castle attachment at worldgen time does not require chunk force-loading (it runs inside the chunk generation pipeline where all required chunks are already loaded by the jigsaw system).

**N6 — Unchecked cast warning in VillageBuilderIntegration is harmless.**
The reflection-based enum cast is the only way to interop with an optional dependency without compile-time linkage. Suppressing with `@SuppressWarnings("unchecked")` or leaving as-is are both acceptable for alpha.

**N7 — Savanna and desert biome detection relies on toString() heuristic.**
`detectVillageBiome()` searches for `"village/plains/"` etc. in the pool element string representation. This is documented in the mixin as a known fragility. Vanilla village pool element strings are stable between minor patches but could change in a major MC update. Acceptable for alpha.

**N8 — `depth` parameter not checked in mixin (fires per-piece, not per-village).**
As noted in section 3 Bug E, the lambda fires once per jigsaw piece. With 85% roll and exists check, only one castle attaches per village. The redundant calls are wasteful (up to 20 per village) but produce no incorrect state. Non-blocker for alpha.

**N9 — INFO-level generator completion logs are verbose.**
Every castle generator logs completion at INFO ("Plains manor generation complete!", "Desert alcazar generation complete!" etc.). During showcase, 15 completion messages emit. In production worldgen, every village chunk load produces 1 completion message. This will spam server logs on heavily-explored servers. Should be changed to DEBUG before stable release but acceptable for alpha.

**N10 — `fabric-api` version in fabric.mod.json is `>=0.145.4` but actual test used `0.150.0`.**
The dependency lower bound is too loose — it allows any fabric-api back to 0.145.4, which likely does not have the exact APIs used for MC 26.1.2. In practice, any player installing for MC 26.1.2 will use a compatible Fabric API, but the constraint does not enforce this precisely. Tightening to `>=0.150.0+26.1.2` would be more correct.

---

## Summary Table

| Area | Status |
|------|--------|
| Mixin fires on villages | CONFIRMED — 5 villages in log |
| Biome detection | Working for plains; untested for other biomes |
| Castle NBTs (15/15) | Present, unpolished |
| Ruins NBTs (0/10) | Missing |
| Village wall NBTs (0/25) | Missing |
| Ruins worldgen JSON | Missing (structure/ and structure_set/) |
| Build with JDK 25 | Clean (1 unchecked warning) |
| Build with JDK 21 | Fails immediately |
| Phase 1 (arch diff) | ~70% |
| Phase 2 (terrain) | 0% |
| Phase 3 (tuning) | ~30% |
| Phase 4 (ruins) | 0% |
| Phase 5 (hand-polish) | 0% |
| Publishable today | No — B1 through B6 |
