# Village Castles

A Fabric mod for Minecraft 1.21.11 that adds grand, biome-themed castle structures to villages.

> **Status: Alpha — Development Tool**
> The generators work. The worldgen pipeline is scaffolded. Zero NBT structure files exist yet. This mod currently provides creative-mode commands for generating castle structures to polish and export. Worldgen placement will function once NBT files are produced.

## What It Does (When Complete)

- **5 Castle Biome Themes**: Plains (medieval stone), Desert (sandstone citadel), Savanna (acacia stronghold), Taiga (Nordic fortress), Snowy (ice keep)
- **Castle Components**: Multi-story keeps, defensive walls, corner towers, gatehouses, courtyards with wells, training areas, stables, barracks
- **Village Integration**: Castle pieces appear as rare additions to village generation (walls more common than towers, towers more common than keeps)
- **Village Perimeter Walls**: Palisades, stone walls, and adobe walls at village edges
- **Wilderness Ruins**: Standalone crumbling castles in the landscape, rarer than village castles
- **Village Builder Integration**: Optional — when Village Builder is installed, villages can grow fortifications over time

## What Works Now

- `/villagecastles generate <biome> [size]` — Generate a full castle in creative mode
- `/villagecastles wall <biome> [segment]` — Generate a single wall segment
- `/villagecastles walls <biome>` — Generate all wall segment types (showcase)
- `/villagecastles list` — Show all biomes, sizes, and segment types
- `/villagecastles help` — Display usage help

**Biomes**: `plains`, `desert`, `savanna`, `taiga`, `snowy`
**Sizes**: `small`, `medium`, `large` (default: large)
**Segments**: `straight`, `corner`, `gate`, `tower`, `terminator`

## Workflow

The generators are tools for producing starting points that get hand-polished into NBT structure files.

1. **Generate**: Use `/villagecastles generate` in creative mode (generates in front of the player)
2. **Polish**: Fix algorithmic corners, adjust proportions, add character
3. **Export**: Use Structure Blocks to save as NBT
4. **Install**: Place NBT files into the mod's data structure

See [VISION.md](VISION.md) for the full design philosophy and roadmap.

## Building

```bash
./gradlew build
```

Requires JDK 21. The mod JAR will be in `build/libs/`.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the mod JAR in your `.minecraft/mods` folder

Note: Until NBT structure files are produced, the mod provides development commands only. No structures will appear in worldgen.

## Structure Files

After generating and polishing structures, place NBT files here:

### Castles (3 sizes per biome)

```
src/main/resources/data/villagecastles/structure/
├── plains/
│   ├── castle_small.nbt
│   ├── castle_medium.nbt
│   ├── castle_large.nbt
│   └── castle_ruins.nbt
├── desert/     (same structure)
├── savanna/
├── taiga/
└── snowy/
```

### Village Walls (5 segments per biome)

```
src/main/resources/data/villagecastles/structure/village_walls/
├── plains/
│   ├── wall_straight.nbt
│   ├── wall_corner.nbt
│   ├── wall_gate.nbt
│   ├── wall_tower.nbt
│   └── wall_terminator.nbt
├── desert/     (same structure)
├── savanna/
├── taiga/
└── snowy/
```

## Configuration

Castle spawn rate can be adjusted per biome in:
- `data/villagecastles/worldgen/structure_set/<biome>_castle.json`
  - `spacing`: chunks between structures (default 64)
  - `separation`: minimum chunks between structures (default 16)

## Village Builder Integration

When [Village Builder](https://github.com/villagebuilder) is also installed, castle pieces register into its expansion pools. Villages can grow fortifications over time. The integration is optional; both mods function independently.

## License

MIT
