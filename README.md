# Village Castles

A Fabric mod for Minecraft 1.21.11 that adds grand, biome-themed castle structures to villages.

## Features

- **5 Castle Biome Themes**:
  - **Plains**: Classic medieval stone brick castle with oak accents
  - **Desert**: Sandstone citadel with terracotta details
  - **Savanna**: Acacia stronghold with mud brick construction
  - **Taiga**: Nordic fortress with spruce and cobblestone
  - **Snowy**: Ice keep with packed ice and stone

- **Complete Castle Generation**: Each castle includes:
  - Multi-story keep with great hall, armory, living quarters, and lord's chamber
  - Defensive walls with crenellations and arrow slits
  - Corner towers with spiral staircases
  - Gatehouse with portcullis frame
  - Courtyard features (well, training area, stables, barracks)

- **Village Integration**: Castles can appear as village center pieces

- **Village Perimeter Walls**: Defensive walls spawn at village edges:
  - **Palisade**: Log fence walls (plains, taiga)
  - **Stone**: Low crenellated stone walls (snowy)
  - **Adobe**: Mud/sandstone walls (desert, savanna)
  - Wall segments include: straight sections, corners, gates, watch towers, terminators

## Usage

### Generating Castles (Development)

Use the in-game command to generate castles for polishing:

```
/villagecastles generate <biome> [size]
```

**Biomes**: `plains`, `desert`, `savanna`, `taiga`, `snowy`
**Sizes**: `small`, `medium`, `large` (default: large)

### Examples

```
/villagecastles generate plains large
/villagecastles generate desert medium
/villagecastles generate snowy
```

### Generating Wall Segments

Generate individual wall segments for polishing:

```
/villagecastles wall <biome> [segment]
```

**Segments**: `straight`, `corner`, `gate`, `tower`, `terminator`

Generate all wall types at once (showcase):

```
/villagecastles walls <biome>
```

### Workflow

1. **Generate**: Use `/villagecastles generate` in creative mode
2. **Polish**: Customize the generated structure to your liking
3. **Export**: Use Structure Blocks to save as NBT file
4. **Install**: Copy NBT to `data/villagecastles/structure/<biome>/castle_center.nbt`

### Other Commands

- `/villagecastles list` - Show all biomes and sizes
- `/villagecastles help` - Display usage help

## Building

```bash
./gradlew build
```

The mod JAR will be in `build/libs/`.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/installer/)
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Place the mod JAR in your `.minecraft/mods` folder

## Structure Files

After generating and polishing structures, place your NBT files here:

### Castles

```
src/main/resources/data/villagecastles/structure/
├── plains/castle_center.nbt
├── desert/castle_center.nbt
├── savanna/castle_center.nbt
├── taiga/castle_center.nbt
└── snowy/castle_center.nbt
```

### Village Walls

```
src/main/resources/data/villagecastles/structure/village_walls/
├── plains/
│   ├── wall_straight.nbt
│   ├── wall_corner.nbt
│   ├── wall_gate.nbt
│   ├── wall_tower.nbt
│   └── wall_terminator.nbt
├── desert/
│   └── (same structure)
├── savanna/
├── taiga/
└── snowy/
```

## Configuration

Castle spawn rate can be adjusted in:
- `data/villagecastles/worldgen/structure_set/village_castles.json`
  - `frequency`: 0.0-1.0 (default 0.75)
  - `spacing`: chunks between structures (default 34)

Village integration weight in:
- `data/minecraft/worldgen/template_pool/village/<biome>/town_centers.json`
  - Castle weight is 15 vs vanilla ~50-74 per option

## License

MIT
