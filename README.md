# Village Castles

A Fabric mod that adds grand, biome-themed castle structures to villages.

> **Status: Alpha, pre-release**
> 15 castle NBTs exist (all 5 biomes × 3 sizes), and all of them are raw generator output awaiting a hand-polish pass. Ruins and village walls are code-complete but have no exported NBTs yet.
>
> Known defects in the shipped templates: 12 of the 15 gatehouses have no door, two carry stray dropped items, and the size in a template's name does not always match its footprint. These are polish-pass work, not code bugs.

## What It Does

- **5 Castle Biome Themes**: Plains (medieval stone), Desert (sandstone citadel), Savanna (acacia stronghold), Taiga (Nordic fortress), Snowy (ice keep)
- **3 Sizes per Biome**: Small (watchtower/outpost), Medium (walled fort), Large (full castle complex)
- **Village Integration**: Castles attach to village edges after jigsaw assembly (85% of villages, size-distributed 35/35/30), with a 2-block gap so a castle sits at the edge of town rather than out in the wilderness
- **Faces the town**: the castle is rotated so its entrance points back at the village, and its site is picked by scoring a 4×4 terrain survey, so it does not straddle a ridge or hang off a cliff
- **Underfilled foundations**: `CastleGroundsPiece` fills the ground beneath the footprint down to solid terrain, so a castle on a slope stands on rock instead of stilts
- **Garrisoned**: castles generate with inhabitants, roughly 2 villagers per chunk placed at bed heads. They spawn unemployed, so they take up the castle's own workstations rather than commuting into the village
- **Castle Aging**: `castle_aging` processor applies subtle weathering at placement (mossy stone, cracked bricks, etc.)
- **Village Builder Integration**: Optional: when Village Builder is installed, castle and wall pieces register into its build pools
- **Village Quests Integration**: Optional: 16 profession-specific quests + 11 dialogue options when Village Quests is installed

## Coming Soon

- **Village Perimeter Walls**: Generators complete (5 segment types per biome), no NBTs exported yet
- **Wilderness Ruins**: DecayEngine complete (5-phase degradation), no NBTs exported yet

## Commands

- `/villagecastles generate <biome> [size]` - Generate a castle in front of the player
- `/villagecastles wall <biome> [segment]` - Generate a single wall segment
- `/villagecastles walls <biome>` - Generate all wall segment types in a row
- `/villagecastles ruins <biome> [1|2]` - Generate a ruins variant
- `/villagecastles export <biome> [size] [force]` - Generate + export single castle as NBT
- `/villagecastles exportall` - Generate + export all 15 castle NBTs (skips polished)
- `/villagecastles exportruins` - Generate + export all 10 ruins NBTs
- `/villagecastles showcase` - Generate all 15 castle variants in a 5×3 grid
- `/villagecastles place <biome> [size]` - Place an existing NBT at player position
- `/villagecastles capture [biome size]` - Capture last-generated region as NBT
- `/villagecastles status` - Show which NBT files exist
- `/villagecastles list` - List biomes, sizes, and segment types
- `/villagecastles help` - Display usage help

**Biomes**: `plains`, `desert`, `savanna`, `taiga`, `snowy`
**Sizes**: `small`, `medium`, `large` (default: large)
**Segments**: `straight`, `corner`, `gate`, `tower`, `terminator`

## Workflow

The generators are tools for producing starting points that get hand-polished into NBT structure files.

1. **Generate**: Use `/villagecastles generate` in creative mode
2. **Polish**: Fix algorithmic corners, adjust proportions, add character
3. **Export**: Use `/villagecastles export` or Structure Blocks to save as NBT
4. **Polished marker**: Run `/villagecastles capture` to mark a file as hand-edited so `exportall` skips it

See [VISION.md](VISION.md) for the full design philosophy and roadmap.

## Building

```bash
# macOS/Linux: JDK 25 must be set explicitly if it isn't your system default
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home ./gradlew build

# Windows
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25...
gradlew.bat build
```

Requires JDK 25. The system default is often JDK 21; running `./gradlew build` without setting `JAVA_HOME` will fail immediately with `release version 25 not supported`. The JAR builds to `build/libs/`.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Vanilla clients need nothing. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Structure Files

### Castles (15 NBTs: all present, awaiting polish)

```
src/main/resources/data/villagecastles/structure/
├── plains/    castle_small.nbt  castle_medium.nbt  castle_large.nbt
├── desert/    castle_small.nbt  castle_medium.nbt  castle_large.nbt
├── savanna/   castle_small.nbt  castle_medium.nbt  castle_large.nbt
├── taiga/     castle_small.nbt  castle_medium.nbt  castle_large.nbt
└── snowy/     castle_small.nbt  castle_medium.nbt  castle_large.nbt
```

### Ruins (0/10, pending export)

```
src/main/resources/data/villagecastles/structure/
└── {biome}/   castle_ruins_1.nbt  castle_ruins_2.nbt   (× 5 biomes)
```

### Village Walls (0/25, pending export)

```
src/main/resources/data/villagecastles/structure/village_walls/
└── {biome}/   wall_straight.nbt  wall_corner.nbt  wall_gate.nbt
               wall_tower.nbt  wall_terminator.nbt   (× 5 biomes)
```

## Configuration

There is no JSON configuration file; edit the constants in `VillageCastleAttachmentMixin` directly. The ones worth knowing:

| Constant | Value | What it controls |
|---|---|---|
| attach chance | 85% | Share of villages that get a castle |
| size split | 35/35/30 | small / medium / large |
| `CLEARANCE` | 2 | Blocks of gap between the village bounding box and the castle. Raising it pushes castles out of walking range |

## Village Builder Integration

When [Village Builder](../village-builder) is also installed, castle pieces register into its expansion pools. Villages can grow fortifications over time. The integration is optional; both mods function independently.

## License

MIT, see [LICENSE](LICENSE).
