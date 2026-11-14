# World generation: ninja villages

_Round 4 · branch `round4-worldgen`_

## What the owner asked for

Villages that feel like the Naruto world, with ninjas living in them. Big hidden villages (like
the Hidden Leaf) are rare. Small outposts are common.

## Research notes (inspiration only, nothing copied)

**The Hidden Leaf (Konohagakure).** Most of its look comes from a few things:
- **Round buildings.** The Hokage Residence is a big round tower in the middle of the village,
  taller than everything around it. It's red/orange, with the kanji for "fire" (火) painted on it,
  and it sits right under the Hokage Rock ([Narutopedia: Hokage Residence][res]).
- **The Hokage Rock**: a cliff behind the village with the faces of the Hokage carved into it
  ([Narutopedia: Hokage Rock][rock]).
- **Lots of houses** with flat or rounded roofs in orange and red, and **water tanks on the
  rooftops**. This is the classic background in the anime; the wiki doesn't describe it, so
  that detail comes from watching the show.
- **A wall around the village with a big double gate.** The gate is the one with "あ" and "ん"
  painted on its doors.
- **Training grounds**: open fields with **three wooden posts** (the bell test), targets for
  kunai.
- **Food stalls** like Ichiraku Ramen: a counter facing the street, stools, a cloth curtain.
- Shrine gates (**torii**), lanterns, little bridges over ponds.

**Other villages.** The Sand village (Sunagakure) has sandstone domes and round
buildings in the desert. Snowy places (the Land of Snow) use plain wood and stone. Small
outposts and hamlets in the Land of Fire are a few houses with a fence and a lookout.

**Minecraft 1.20.1 custom structures** (checked against the game's own code, see below):
- A structure is a `Structure` subclass. Its **`StructureType`** (a codec) is registered with a
  `DeferredRegister` on `Registries.STRUCTURE_TYPE`. The pieces it places are `StructurePiece`s,
  whose **`StructurePieceType`** goes in `Registries.STRUCTURE_PIECE` so they can be saved and
  loaded with chunks.
- `findGenerationPoint` decides whether a chunk gets the structure. `StructurePiece.postProcess`
  is then called **once per chunk** the piece covers. It may only write inside that chunk's box.
- The data side:
  - `data/<mod>/worldgen/structure/*.json`: type, biomes tag, generation step, terrain adaptation.
  - `worldgen/structure_set/*.json`: placement (`random_spread` with spacing and separation in
    chunks, plus a salt).
  - `tags/worldgen/biome/has_structure/*.json`: which biomes it can appear in.
- **Terrain adaptation** `beard_thin` makes the terrain meet the bottom of each piece's bounding
  box. Vanilla villages use it.
- The **generation step** matters. We use `top_layer_modification`, the last step, so trees are
  already grown when the village is built and our clearing removes them. Snow still falls on the
  roofs, because snow freezing runs after structures in the same step.
- `/locate structure noirm:hidden_village` and `/place structure …` work automatically.

[res]: https://naruto.fandom.com/wiki/Hokage_Residence
[rock]: https://naruto.fandom.com/wiki/Hokage_Rock

## Design (what we built)

_Round 6 (villages v3) made the hidden village about 545 blocks across and rebuilt its layout and
looks. Round 5 (v2) introduced the plan-then-build approach it still uses._

Everything is built from Java code (`com.noirm.world.village`). There are no hand-made structure
files, so there's no artist dependency.

**How it works**
- `VillagePlanner` lays a village out once from its seed (`VillagePlan`): streets first, then the
  landmarks, then plots packed along every street (door to the street), then back plots, then
  **yards in every leftover patch** (no idle lawns), then street furniture and the people. Streets
  and the river are kept as rasters (one entry per block), so curved streets cost nothing.
- `VillageBuilder` builds one chunk at a time from the (cached) plan: level the ground, pave the
  streets, dig the river and build its bridges (`Waterways`), the walls / Kage Rock / cliffs
  (`Fortifications`), then only the lots, props and people near that chunk (the plan keeps a
  per-chunk index).
- Each lot is drawn in a `LotFrame` ("front faces south"), so every building works on any side
  of any street. `Buildings` has the homes and shops, `Landmarks` the big buildings,
  `RoundBuildings` the spires and the jōnin station, `Yards` the yards, giant tree and cemetery;
  `Shapes` has the round parts (drums, flared ring eaves, medallions) and `Materials` the block
  mixes and colourways.
- **Bigger than one structure.** A structure can only reach 8 chunks from where it starts
  (vanilla's `ChunkGenerator.createReferences`), about 128 blocks. So a hidden village is a
  **core** start (`hidden_village`, in its anchor chunk, the one `/locate` finds) plus one
  **ward** start in every other chunk it covers (`hidden_village_ward`, placed by our own
  `noirm:village_wards` placement, `WardPlacement`). Each ward works out which anchor it belongs
  to with the same maths as vanilla's `random_spread`, asks the core for that village's site
  (measured once per anchor and remembered), and builds its own chunk from the shared plan. The
  same trick as Large Structure Lib.
- `/noirm village <capital|town|outpost> [leaf|sand|snow]` builds one where you stand (no site
  checks), a few chunks every tick (`VillageBuildQueue`), and says when it's done.

| | Hidden village (`hidden_village`) | Town (`ninja_town`) | Outpost (`ninja_outpost`) |
|---|---|---|---|
| Size | ~545 blocks across (wall radius 264) | ~145 (fence radius 68) | ~30 (palisade radius 14) |
| How often | spacing 96 chunks, separation 48 (rare) | shares a set with outposts (weight 2 of 5), spacing 26; never within 16 chunks of a capital's anchor, and never overlapping a capital | weight 3 of 5 |
| Layout | **Leaf / Snow:** a fan of 7 avenues from the Kage residence under the Kage Rock, 6 street arcs round it, alleys between them, a river with bridges. **Sand:** 8 spokes and 2 ring streets round a central plaza, no river. | a post town: main street of shopfronts with lanterns strung across and arched gates, a cross street, back lanes | palisade, watchtower |
| Contents | ~1,600 lots (~550 buildings, the rest yards, gardens, parks); districts: market, shinobi quarter, clan district (by the river), temple hill, lantern street, homes, outer ring | chief's house, inn, shrine, training ground, shops, houses | 2–3 houses, campfire, posts |
| People | up to 140: Kage, guards, wall patrols, rooftop runners, trainees, clan ninjas, teachers, shopkeepers, villagers, children, cats, chickens, koi | chief, guards, a trainee, shopkeepers, villagers, children | 3 ninjas, a merchant, a dummy |

**The look (v3)** — from the research in `docs/research-villages-v3.txt` (motifs only):
- **Colourways.** Every lot gets a paint job (`VillagePalette.colourway`): wall plaster, an accent
  timber, a roof colour (Leaf roofs weighted ~⅓ orange, ⅓ ochre, ⅙ terracotta, a few teal, rare
  green and red), timber trim and a cloth colour. The planner makes neighbours differ, so one house
  design reads as many.
- **Façades with depth:** a timber skirting, a timber band at each floor, a pent eave of roof tiles
  along the front at the floor line, window sills, open shutters, flower pots on some sills, and a
  mix of clear glass, white paper screens and a few warm lit windows.
- **Roofs:** flat roofs have a coloured tile lip and something on top (a cluster of water tanks, a
  roof garden, washing on a line, or a bench and an aerial); pitched roofs use the lot's tiles.
- **Round buildings** (round houses, round towers, spires, the jōnin station, the Kage residence)
  get the flared ring eave: a ring of tile stairs, then a ring of slabs, at the roof and slimmer
  ones between floors.
- **Skyline:** a low carpet of 1–3 storeys, a few blocks of flats (4–5) near the plaza, and 8–12
  slender **spires** (a cone, a "bottle", a bell turret) to break it up.
- **Street life:** avenue lamps (a redstone lamp under an inverted daylight sensor: lit at dusk),
  shop signboards and paper lanterns (shroomlight and froglights on chains), lanterns strung
  across the lantern street with arched gates at its ends, notice boards, benches, barrels and
  flower pots, power poles with sagging wires, transformers and service drops to the houses.
- **Yards** fill every leftover patch: kitchen gardens and fields, laundry, woodpiles, sheds, koi
  ponds, bamboo, roadside shrines and wells. Plus a giant-tree park and a cemetery with a memorial
  stone and an eternal flame.
- **The Kage residence:** the great drum with flared eaves at every floor band, two round wing
  towers with roof terraces, and the village symbol on a round medallion over the door.
- **The river:** five blocks wide between mossy stone banks, steps down to the water, red arched
  footbridges with lanterns where streets cross and flat stone bridges with parapets for avenues.

**Six looks, by biome** (`VillagePalette`; saved by position, so new ones go at the end):
- **Leaf** (plains, forests, cherry groves): cream and birch walls, orange and ochre roofs, the
  red Kage residence under the Kage Rock, a fan layout and a river. The wall is the series' one:
  reddish-brown with a tiled coping (no battlements), round bastions, red gates standing over the
  wall walk, side gates with painted "a" and "un" doors; zig-zag stairs climb the Kage Rock and
  shelter tunnels go into its foot.
- **Sand** (desert, savanna): sandstone and terracotta, domes and vaults, eight spokes round a
  central plaza, a ring of desert cliffs, the Kazekage.
- **Snow** (snowy and cold biomes, taiga): spruce, white walls, slate and blue roofs; the fan
  layout under a rock.
- **Mist** (swamps, dark forests): stone-brick and pale drums topped with moss, azalea and ferns;
  dark oak; two ring canals make three islands, crossed by causeways and footbridges; a mossy wall;
  mist closes in when you walk in (`VillageFog`, told which look you're in by
  `VillageAtmosphere`; villages built with `/noirm village` are found through `BuiltVillages`, a
  list saved with the world); the Mizukage under a dome.
- **Cloud** (meadows, windswept hills): white quartz and calcite, blue roofs, little domes on the
  flat roofs, round houses under domes; tall **rock spires** stand among the houses (bare with a
  wind-bent tree, a railed "dish" platform with a bell, or a little domed house on top; a ladder up
  each); a ring of grey cliffs with spires rising out of it; a light haze; the Raikage's tower
  under a blue onion dome.
- **Stone** (badlands, gravelly hills, stony shores): mud brick and grey stone; round rock towers
  6–8 storeys high that **taper** a block every two storeys, joined by **rope bridges** from roof to roof (a doorway is
  cut where a bridge meets a taller tower); a ring of grey cliffs; the Tsuchikage under a stepped
  stone crown.

**The site**: we sample the land height over the round footprint (a 9×9 grid for the capital, 5×5
for a town, 3×3 for an outpost). We skip spots that are steep (more than 30 / 18 / 9 blocks of
difference), watery, or below sea level, and clear high enough to take the hills off. Then the builder clears everything above the ground (trees, hill tops) and
fills holes below with the foundation block.

**Residents** (`VillageResidents`):
- Village ninjas are `SparringNinja`s with a **resident** flag (saved as `Resident` plus `Home` in NBT).
- Residents **don't challenge** players who enter combat mode. They fight back when hit, and
  hurting any resident (or merchant) **raises the alarm**: every resident within 40 blocks turns
  on the attacker.
- Residents stay near their home spot and walk back after a fight.
- A resident beaten and recruited into your crew leaves the village.
- Resident ninjas have a **routine** (`VillageRoutineGoal`): **runners leap from rooftop to
  rooftop** (the jump is aimed with the game's own physics; if a wall gets in the way twice they
  Body Flicker onto the roof in a puff of smoke), **patrols** walk the wall walk, **trainees**
  practise on the dummies. Clan-district ninjas belong to that clan.
- **Villagers** (`VillageMerchant`, now with roles):
  - **Shopkeepers** by shop: ramen (ramen, grilled meat), weapons (kunai, senbon, forehead
    protectors, flak vests, sandals), scrolls (sealing scrolls, paper, books), flowers, a
    teahouse (green tea, dango), or general goods. All trades are in emeralds.
  - **Villagers** go out into the village by day and home at night or in the rain (doors open).
  - **Children** spend the day at the Academy and go home at night.
  - The town's **chief**. Right-click anyone who isn't trading and they say something.
  - **Neighbours chat** (`VillagerChatGoal`): two villagers who meet by day stop, face each other
    and take turns talking, a speech bubble (a small text display riding on them) over the
    speaker's head. A chat leaves a cooldown of 30–60 seconds.
  - **Monsters in the village:** villagers run home, and resident ninjas go after any vanilla
    monster that comes into the patch they look after (not creepers, which would blow the houses
    up, and not endermen and other neutral mobs).
  - **Seats:** now and then, by day, a villager walks to a bench, a stool at a table or a step
    nearby and sits for half a minute or so (`VillagerSitGoal`; they ride an invisible
    `SeatEntity`).
- **Animals:** a cat on some porches, chickens in the kitchen gardens, koi in the ponds.

## Limitations / ideas for later

- Villages need fairly flat, dry land (the capital's site is measured on a 9×9 grid plus the cell
  middles, ignoring the highest and lowest tenth), so capitals are rare, and the Cloud and Stone
  looks rarest of all (their biomes are hilly).
- **Updating the mod changes villages that are half explored.** The plan is worked out from the
  seed by the current code, so chunks of a hidden village generated after an update can come out
  from a different plan (or a different look) than the chunks built before it.
- Every village's clan district belongs to the Uchiha or the Hyūga, even in the Mist, Cloud and
  Stone villages; residents aren't picked from the village's own clans yet.
- Towns and outposts always have Leaf-style buildings in the new looks' colours (no Mist, Cloud
  or Stone town designs yet).
- Researched, not built: terraces (villages that follow the land in steps), roads and travellers
  between settlements, construction sites, a village chronicle, a soundscape, satellite sites
  outside the wall (a forest-of-death circle, a valley of the end), town types (harbour, castle,
  temple and farm towns).
- Next: missions board, masters living in villages (M4), the Kage as a teacher.

**Living with other structures.** Vanilla villages keep 16 chunks clear of a hidden village's
anchor (our copy of `minecraft:villages` adds an exclusion zone); the mobs other structures left in
a village chunk (villagers, golems, illagers, witches, stray cats) are removed as it's built; and
raiders don't spawn inside a ninja village (a pillager outpost cleared away by the village would
otherwise keep spawning them). Patrols that march in still come.
- Next: missions board, masters living in villages (M4), the Kage as a teacher.
