# Vision: Village Castles

## What This Is

Castles as generated architecture. No NBT templates, no hand-polish pipeline, no frozen assets:
every castle is drawn by code at generation time against the terrain it stands on. A fix to a
generator improves every castle that will ever exist; an NBT would have improved one file.

Two lineages, one grammar. The **ancients** built first: monumental deepslate works fitted to
the land's own features. The **villagers** came later and built in imitation of the ruins they
found: the same shapes in their own timber and stone. A player who has seen one lineage
recognizes the other on sight - and understands, without being told, which came first.

The mod stands alone. Village Builder and Village Quests stand alone. Together they're better.

## Destination

A player crests a ridge and sees a dark curtain wall wrapping a plateau's rim, battlements level
over uneven footings, moss on the stone, a keep with slate-spired turrets. Nobody lives there.
The garrison that never left is skeletons in the crypt; the treasury is still under the keep.
This is a landmark - roughly woodland-mansion rare, and the terrain chose the site: plateaus get
citadels, hilltops get ringforts, cliffs get holds carved into the face, islands get sea forts,
headlands get neck-walled promontory forts, ridges get wall-holds, jagged peaks get lone beacon
eyries. Condition is rolled per site - half are ruins, a third are crumbling, and the rare
weathered-but-standing find is the jackpot, still fully garrisoned.

Days later the player finds a plains village with a small stone watch keep behind the smith's
house - corner pilasters, a string course at each storey, coping along the yard fence posts.
The same bones as the plateau citadel, half the size, warm light in the windows, villagers
asleep upstairs. The village built it. Most villages have nothing; about one in five is
fortified (40/35/25 small watch keep / medium walled fort / large castle seat).

With Village Builder installed, an unfortified village can raise one over time: the builder
gathers materials for days and puts the castle up at dawn, sited and biome-dressed by the same
generator that serves worldgen. With Village Quests installed, villagers talk about their
fortifications.

## The Grammar

What makes the two lineages kin is a shared architectural vocabulary, implemented once:

- **Keeps** - square or round, plinth pads proud of the walls, corner pilasters that never
  erode, a string course at every storey line, arrow slits, a corbelled crown, an inset roof.
- **Curtain walls** - every column footed on its own ground, merlon-and-slab coping, terrain
  writing the silhouette.
- **Gates** - arches with dressed rims; monumental trilithons for the ancients, hung wooden
  doors for the villagers.
- **The program** - halls, wells, crypts, stair towers, vaults.

What separates them is voice. Ancients: deepslate body, slate roofs, a regional accent stone in
the trim (tuff / cut sandstone / mud brick / mossy stone brick / calcite), soul-lantern light,
decay drawn in (erosion bites, collapsed roofs, moss and vines), loot guarded by spawners.
Villagers: the biome's 21-slot material vocabulary, timber roofs, torch light, condition
PRISTINE, furniture, beds - and the beds are load-bearing: the grounds piece spawns the garrison
at bed heads, and Village Builder's villagers claim them.

## How It Works

One discipline everywhere: a castle is a **plan** - an ordered block map plus chests, spawners,
and digs - drawn deterministically from a seed and a serialized height field. Worldgen pieces
rebuild the identical plan in every overlapping chunk and write only their slice; the debug
commands and Village Builder's build path place the same plans through the same code. Decay is
computed while drawing (position-hashed, chunk-order independent), never swept afterwards.
Finishing passes make physics agree with intent: lanterns end supported or gone, chests always
get footing, canopies are stripped whole so no tree is ever bisected by a wall.

Placement: ancient castles pick their own sites (a ~100-column heightmap survey per structure-set
candidate classifies the landform or rejects the site; villages are avoided by predicted-grid
math). Villager castles attach after jigsaw assembly via a per-side terrain survey, or arrive
through Village Builder's `BuildPlanProvider` API at build time.

## Roadmap

- Interiors deepen: more room types, biome-specific furnishing, wall walks.
- Ancient oddities: the sunken sanctuary, the moated lowland castle (each behind a rare gate).
- Village Builder: progressive block-by-block construction if VB ever grows it; auto-costing of
  plans through `StructureAnalyzer`.
- Village Quests: quest lines that point at ancient ruins near the village.
