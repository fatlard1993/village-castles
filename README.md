# Village Castles

A Fabric mod for Minecraft 26.1.2 that adds grand, biome-themed castle structures to villages.

> **Status: Alpha — Pre-release**
> 15 castle NBTs exist (all 5 biomes × 3 sizes). Village worldgen is active — castles attach to villages at an 85% rate. All NBTs are raw generator output awaiting a hand-polish pass. Ruins and village walls are code-complete but have no exported NBTs yet.

## What It Does

- **5 Castle Biome Themes**: Plains (medieval stone), Desert (sandstone citadel), Savanna (acacia stronghold), Taiga (Nordic fortress), Snowy (ice keep)
- **3 Sizes per Biome**: Small (watchtower/outpost), Medium (walled fort), Large (full castle complex)
- **Village Integration**: Castles attach to village edges after jigsaw assembly (85% of villages, size-distributed 35/35/30)
- **Castle Aging**: `castle_aging` processor applies subtle weathering at placement (mossy stone, cracked bricks, etc.)
- **Village Builder Integration**: Optional — when Village Builder is installed, castle and wall pieces register into its build pools
- **Village Quests Integration**: Optional — 16 profession-specific quests + 11 dialogue options when Village Quests is installed

## Coming Soon

- **Village Perimeter Walls**: Generators complete (5 segment types per biome), no NBTs exported yet
- **Wilderness Ruins**: DecayEngine complete (5-phase degradation), no NBTs exported yet

## Commands

- `/villagecastles generate <biome> [size]` — Generate a castle in front of the player
- `/villagecastles wall <biome> [segment]` — Generate a single wall segment
- `/villagecastles walls <biome>` — Generate all wall segment types in a row
- `/villagecastles ruins <biome> [1|2]` — Generate a ruins variant
- `/villagecastles export <biome> [size] [force]` — Generate + export single castle as NBT
- `/villagecastles exportall` — Generate + export all 15 castle NBTs (skips polished)
- `/villagecastles exportruins` — Generate + export all 10 ruins NBTs
- `/villagecastles showcase` — Generate all 15 castle variants in a 5×3 grid
- `/villagecastles place <biome> [size]` — Place an existing NBT at player position
- `/villagecastles capture [biome size]` — Capture last-generated region as NBT
- `/villagecastles status` — Show which NBT files exist
- `/villagecastles list` — List biomes, sizes, and segment types
- `/villagecastles help` — Display usage help

**Biomes**: `plains`, `desert`, `savanna`, `taiga`, `snowy`
**Sizes**: `small`, `medium`, `large` (default: large)
**Segments**: `straight`, `corner`, `gate`, `tower`, `terminator`

## Workflow

The generators are tools for producing starting points that get hand-polished into NBT structure files.

1. **Generate**: Use `/villagecastles generate` in creative mode
2. **Polish**: Fix algorithmic corners, adjust proportions, add character
3. **Export**: Use `/villagecastles export` or Structure Blocks to save as NBT
4. **Polished marker**: Run `/villagecastles capture` to mark a file as hand-edited — `exportall` will skip it

See [VISION.md](VISION.md) for the full design philosophy and roadmap.

## Building

```bash
./gradlew build
```

Requires JDK 25. The mod JAR will be in `build/libs/`.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) 0.19.2+
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) 0.150.0+26.1.2
3. Place the mod JAR in your `.minecraft/mods` folder

## Structure Files

### Castles (15 NBTs — all present, awaiting polish)

```
src/main/resources/data/villagecastles/structure/
├── plains/    castle_small.nbt  castle_medium.nbt  castle_large.nbt
├── desert/    castle_small.nbt  castle_medium.nbt  castle_large.nbt
├── savanna/   castle_small.nbt  castle_medium.nbt  castle_large.nbt
├── taiga/     castle_small.nbt  castle_medium.nbt  castle_large.nbt
└── snowy/     castle_small.nbt  castle_medium.nbt  castle_large.nbt
```

### Ruins (0/10 — pending export)

```
src/main/resources/data/villagecastles/structure/
└── {biome}/   castle_ruins_1.nbt  castle_ruins_2.nbt   (× 5 biomes)
```

### Village Walls (0/25 — pending export)

```
src/main/resources/data/villagecastles/structure/village_walls/
└── {biome}/   wall_straight.nbt  wall_corner.nbt  wall_gate.nbt
               wall_tower.nbt  wall_terminator.nbt   (× 5 biomes)
```

## Configuration

Castle spawn rate is controlled by `VillageCastleAttachmentMixin` (85% chance, 35/35/30 small/medium/large split). There is no JSON configuration file — edit the mixin constants directly.

## Village Builder Integration

When [Village Builder](https://github.com/villagebuilder) is also installed, castle pieces register into its expansion pools. Villages can grow fortifications over time. The integration is optional; both mods function independently.

## License

MIT
