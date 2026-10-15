# Village Castles - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Building

```bash
# JDK 25 required; the system default is often 21 and fails immediately
JAVA_HOME=<path-to-jdk-25> ./gradlew build
```

The JAR builds to `build/libs/`. Install server-side; vanilla clients need nothing. Version
targets live in `gradle.properties` and `fabric.mod.json`.

## How it generates

Every castle is generated in code; there are no templates. Ancient and villager castles share one
architectural grammar (keeps with corner pilasters and string courses, merlon-and-slab coping,
gatehouse arches). Ancient castles use deepslate-and-slate masonry with a regional accent stone
(tuff, cut sandstone, mud brick, mossy stone brick, calcite); villager castles use the biome's own
timber and stone at condition PRISTINE.

A castle is drawn once into a deterministic block plan (an ordered position->state map plus chests,
spawners and dig sites) from a seed and a serialized terrain sample, and each chunk writes the subset
that lands inside it, so a castle spanning many chunks assembles in any generation order. Decay
happens while drawing, never as a post-pass: erosion and collapse heights come from position hashes
every chunk computes identically.

- Wilderness placement: `village-castles:ancient_castle`, structure-set spacing 48 / separation 20,
  thinned by `LandformSurvey` rejecting sites, and refused within 8 chunks of a predicted village
  (`VillagePrediction`).
- Village attachment (`VillageCastleAttachmentMixin`) runs after jigsaw assembly with a per-side
  terrain survey, 20% of villages, sizes 40/35/25. `CastleGroundsPiece` underfills the footprint
  and spawns the villagers at bed heads.
- Keep spiral stairs cut a helical two-block stairwell above each tread so they climb through the
  six-block floors.
- `Condition.minChests/maxChests` are declared but not read anywhere.

## Integrations

Both are wired reflectively; either mod runs alone.

- **Village Builder**: three procedural entries (small / medium / large) register through its
  `BuildPlanProvider` API, running the villager-castle designer against the village's terrain, biome
  and a chosen facing. All share the limit group `village-castles:castle` (max 1), and
  `CastleCensus` walks a village's structure pieces so a village that generated with a castle is
  never offered another. An older Village Builder logs one warning and offers no castles.
- **Village Quests**: errands (masons, fletchers, farmers, librarians, toolsmiths, occasionally
  anyone) and dialogue gated on the village having a castle; cleric, mason and librarian lines and
  the cleric's bone errand gated on an ancient castle being locatable from the village.

## Tuning the landform classifier

`/village-castles ancientscan` (op level 2) runs the landform classifier where you stand and prints
every metric plus the verdict.
