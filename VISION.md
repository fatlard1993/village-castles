# Vision: Village Castles

## What This Is

Villages with teeth. A Fabric mod for Minecraft 26.1.2 that adds castle structures to village generation. Each biome gets its own architectural language — not palette swaps, but genuinely different shapes, proportions, and character. A desert citadel doesn't look like a taiga fortress with the blocks changed.

The mod stands alone. Village Builder and Village Quests stand alone. Together they're better.

## Destination

A player finds a plains village with a small stone watchtower behind the smith's house, a fence around it, a guard post with a bed and a chest. Normal village otherwise — houses, farms, villagers going about their day. The tower is just... there. Someone built it.

The next village has nothing. Just the usual.

The one after that has a full Nordic fortress rising above the spruce trees — steep timber roof, heavy stone walls, a great hall with a throne, corner towers connected by walkways. The village grew around it. Streets lead up to its gate. That one's rare. That one matters.

~15% of villages have no fortification. ~30% have a small fort. ~30% have a medium castle. ~25% have the full thing — a large castle complex with walls, towers, maybe a moat. The distribution inverts rarity: most villages have *something*, but the impressive ones are the find.

Rarer still: ruins in the wilderness. No village, just a crumbling keep on a hilltop, moss crawling up the walls, the roof collapsed, a flooded basement with a chest. These are castle pieces aged and broken, placed independently in worldgen. Landmarks, not clutter.

With Village Builder installed, villages build defenses over time. Castle pieces become options in the builder's vocabulary. With Village Quests installed, villagers talk about their fortifications — the mason worries about the east wall, the fletcher stocks the tower, the cleric wants to rescue zombie villagers from the old ruins.

## Biome Architecture

Each biome has a distinct architectural identity. Not just different materials — different structural concepts, shapes, and cultural feel.

### Plains — Medieval European
Stone brick walls, oak timber framing, cobblestone foundations. Peaked roofs, arched windows, crenellated battlements. The classic castle silhouette.
- **Small**: Square watchtower with a peaked oak roof, fenced yard. A guard post.
- **Medium**: Keep with corner turrets, a gatehouse, cobblestone curtain walls. Training yard with a well.
- **Large**: Full castle — great hall, multiple towers, curtain walls with walkway, gatehouse with portcullis, moat.

### Desert — Sandstone Citadel
Cut sandstone, smooth sandstone, terracotta accents, acacia wood. Flat roofs, thick walls, open-air courtyards, covered cisterns. Built for heat, not cold.
- **Small**: Squat sandstone tower, wide and low. Flat roof with parapet. Covered water cistern.
- **Medium**: Walled compound — thick sandstone walls, corner bastions, shaded courtyard with archery range. Acacia gate.
- **Large**: Stepped pyramid citadel — terraced levels, throne room at the summit, interior chambers, cactus gardens, treasure vaults.

### Savanna — Acacia Stronghold
Mud brick, acacia wood, packed mud, terracotta. Low-slung, organic shapes. Wide rather than tall. Inspired by Great Zimbabwe, West African mud architecture.
- **Small**: Circular mud-brick enclosure with acacia palisade. Round hut with thickened walls.
- **Medium**: Multi-compound stronghold — connected circular enclosures, thick mud-brick walls, acacia watchtower. Open-air council area.
- **Large**: Great enclosure — massive mud-brick walls (thick, slightly tapered), internal passages, raised platform with throne, granary stores, monument tower.

### Taiga — Nordic Fortress
Cobblestone, mossy cobblestone, spruce timber. Steep roofs, heavy log construction, stone foundations. Built against the cold and the dark.
- **Small**: Fortified spruce longhouse — thick log walls, steep roof, stone hearth. Palisade fence.
- **Medium**: Timber-and-stone fort — spruce-framed keep on stone base, corner watchtowers with steep spire roofs, log palisade walls.
- **Large**: Grand fortress — massive stone-and-timber great hall, multiple connected buildings, tall stone walls with spruce walkways, gate with drawbridge mechanism.

### Snowy — Ice Keep
Packed ice, blue ice, stone brick, spruce. Thick walls for insulation, enclosed spaces, minimal windows. Everything sealed against the cold.
- **Small**: Fortified igloo — packed-ice dome with stone-brick reinforcement, entrance tunnel, underground stores.
- **Medium**: Ice-walled compound — packed-ice outer wall, stone-brick inner keep, enclosed courtyard with campfire hearth, windbreak walls.
- **Large**: Glacier fortress — blue-ice towers, packed-ice curtain walls, great hall with massive hearth, underground chambers, ice-vaulted ceilings.

## How It Works

### Village Integration

Villages generate completely normally — vanilla center, streets, houses, villagers. After the jigsaw assembly finishes, a mixin intercepts the completed structure and attaches a castle to the village edge.

The castle is placed as an additional structure piece adjacent to the village's bounding box. It extends outward from the village, never overlapping existing buildings. The village's streets, houses, and villagers are untouched. The castle is an addition, not a replacement.

This approach:
- Never breaks vanilla village generation
- Preserves all villagers, beds, workstations, and iron golem spawning
- Works with any other mod that modifies villages
- Gracefully does nothing if removed — vanilla villages everywhere

### The Generator Pipeline

The Java generators produce castle structures procedurally. They exist to create **fully furnished, lived-in starting points** for NBT structure files.

1. Generate a variant with `/villagecastles generate <biome> [size]`
2. Polish by hand in creative — fix corners where algorithms show, adjust rooflines, add character
3. Export as NBT with `/villagecastles export` or auto-export pipeline
4. The mod ships the NBT files; generators are dev tools, not runtime code

The hand-editing step is about structural character, not adding content. The content is already there — furnished rooms, loot chests, beds, workstations. The polish pass makes it feel *built* rather than *computed*.

### Ruins

Abandoned variants follow the same pipeline: generate a castle, then break it. The DecayEngine applies phased degradation:
1. Weathering — block variant swaps (cracked, mossy)
2. Structural damage — holes, fragile block removal, roof collapse
3. Collapse — rubble piles, floating block cleanup
4. Vegetation — vines, moss, biome-specific growth
5. Atmosphere — darkness, cobwebs, soul lanterns

Ruins place independently via structure sets as wilderness landmarks. Darker than village castles, with mob spawners, hidden loot rooms, and dungeon-tier treasure. 2 variants per biome, 10 total.

### The Bridges

**Village Builder**: When present, castle pieces register into its build pools via reflection. Villages that expand over time can grow fortifications. No hard dependency — if Village Builder isn't installed, nothing breaks.

**Village Quests**: When present, castle-themed quests and dialogue register. The mason repairs walls, the fletcher stocks towers, the cleric wants golden apples for the zombie villagers in the ruins. Professions get castle-specific roles that make fortified villages feel alive.

## Principles

**Biome truth.** Shape, proportion, and structural concept vary per biome. The palette is a starting point, not the whole answer. A savanna stronghold has circular enclosures and thick tapered walls. A taiga fortress has steep timber roofs and heavy log framing. These aren't the same building in different colors.

**Rarity is graduated.** Most villages get something — even a small fort changes the feel. But the impressive castles are uncommon. A village with a large castle complex is a landmark worth remembering.

**Foundations follow terrain.** Castles sit on the land, not on platforms. Use biome-appropriate materials where foundations are needed (stone for plains, sandstone for desert, packed mud for savanna). Avoid cobblestone footprints that read as artificial. Where the terrain is flat enough, skip the foundation entirely.

**Abandoned tells a story.** Ruins imply history — collapsed roofs, overgrown courtyards, flooded basements. A ruin should make the player wonder what happened here.

**Generators are tools, NBTs are the product.** Generator code quality matters for producing good starting points, but don't over-engineer the tool when the output is what ships.

## Current State

### What Works
- **Post-assembly attachment mixin** (`VillageCastleAttachmentMixin`): Injects into `JigsawPlacement.lambda$addPieces$2` (the per-piece recursive lambda in MC 26.1.2). Detects village biome via pool element string, rolls for castle size (85% chance, 35/35/30 small/medium/large), finds clear position at village edge, adds castle as `PoolElementStructurePiece` via `StructurePiecesBuilder.addPiece()`. Overlap checking uses the village bounding box.
- **Castle generators**: All 5 biomes × 3 sizes produce complete, furnished structures. Desert-large has a pyramid variant. Snowy-small has an igloo variant. Biome palettes cover 21+ block types per biome.
- **NBT export pipeline**: `/villagecastles exportall` or `./gradlew runExportStructures` generates and saves all 15 castle NBTs automatically. Both paths honor `.polished` markers.
- **15 castle NBTs exist** (3 sizes × 5 biomes). Raw generator output — unpolished.
- **Commands**: generate, export, exportall, exportruins, ruins, showcase, place, wall, walls, capture, status, list, help. All functional.
- **Village Builder integration**: Reflection-based, guarded, registers castle and wall segment variants with material costs.
- **Village Quests integration**: 16 profession-specific quests + 11 dialogue options. Thematic content.
- **castle_aging processor**: Light weathering applied at village placement (mossy stone, cracked bricks, blue ice, etc.). Wired to the active placement path via `StructurePoolElement.single(id, processorHolder)`.
- **DecayEngine**: 5-phase ruins degradation pipeline. Ready but no NBTs generated yet.
- **Village wall generator**: 5 segment types × 4 wall styles. Commands functional, no NBTs exported yet.

### What Needs Work
- **Architectural variety**: Some biome/size combinations share structural templates. Each of the 15 combinations should be identifiable by silhouette alone.
- **Foundations**: Generator uses cobblestone universally. Should use biome-appropriate materials or omit where terrain allows.
- **Terrain integration**: Height sampling fixed — mixin now queries `getFirstOccupiedHeight` at the castle's actual X/Z instead of using the village bounding-box minY. Terrain blending (smooth biome-appropriate foundations at edges) still needed in the generators.
- **Stairwell traversal**: Fixed in generator code but needs re-export and verification.
- **Item drops**: Fixed with SKIP_DROPS flag but needs verification.
- **0/10 ruins NBTs**: DecayEngine ready, export command exists, needs a generation + export session.
- **0/25 village wall NBTs**: Generator and commands ready, needs export session.
- **Ruins worldgen wiring**: No `worldgen/structure/` or `worldgen/structure_set/` JSON files exist. Ruins will not appear in the wild until these are authored.

### What Was Tried and Abandoned
- **Pool injection into `town_centers`**: Castles replaced the village center. Bounding box too large — jigsaw system couldn't attach streets. Villages had no houses or villagers.
- **Pool injection into `houses`**: Same bounding box overlap problem.
- **Jigsaw connectors on castles**: 8 connectors pointing to street pools. Streets couldn't generate due to bbox overlap with the monolithic castle piece.
- **VillageSizeMixin**: Increased jigsaw depth 6→10. Didn't help because the bbox overlap was the fundamental issue, not depth. Removed.
- **Standalone castle structure sets**: Castles as independent wilderness structures. Removed — ruins fill the wilderness role.

## Next Steps

### Phase 1: Architectural Differentiation (15 Unique Designs)
Rewrite generators so each biome/size produces a structurally unique castle:
- Different footprint shapes per biome (rectangular, circular, compound, etc.)
- Different vertical profiles (tall/narrow for taiga, wide/low for savanna/desert)
- Different feature vocabulary (hearths vs cisterns, peaked roofs vs flat roofs, log palisades vs stone walls)
- Each of the 15 combinations should be immediately identifiable by silhouette alone

### Phase 2: Terrain & Foundation Polish
- Biome-aware foundation materials (or no foundation where possible)
- Smoother terrain transition at castle edges
- Re-export all 15 NBTs after generator changes

### Phase 3: Production Tuning
- Castle chance: 85% (15% no castle, 30/30/25 small/medium/large)
- Clean up debug logging
- Verify all biomes produce visually distinct castles in-game
- Test across multiple world seeds

### Phase 4: Ruins
- Generate 10 ruins variants (2 per biome) via DecayEngine
- Export as NBTs
- Re-add ruins worldgen files (structure sets, spacing 96)
- Test wilderness placement

### Phase 5: Hand-Polish
- All 25 NBTs (15 castles + 10 ruins) get a creative-mode polish pass
- Fix corners where algorithms show
- Add character details
- Verify furnishing, loot, and lighting

## Constraints

- Minecraft 26.1.2 / Fabric Loader 0.19.2+ / Java 25
- No hard dependencies beyond Fabric API
- Must not break vanilla village generation — only augment
- All three mods (Village Castles, Village Builder, Village Quests) function independently
- Generators are dev tools; NBTs are the shipped product
