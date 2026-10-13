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

_Round 5 (villages v2) replaced the first version: villages are now planned, then built._

Everything is built from Java code (`com.noirm.world.village`). There are no hand-made structure
files, so there's no artist dependency.

**How it works**
- `VillagePlanner` lays a village out once from its seed (`VillagePlan`): streets first, then the
  landmarks, then house plots packed along every street (7–12 wide, 8–13 deep, door to the
  street), then back-street houses, gardens and parks in what's left, then trees, lanterns,
  power poles and wires, and finally the people. An occupancy grid stops overlaps.
- `VillageBuilder` builds one chunk at a time from the (cached) plan: level the ground, pave the
  streets, the walls / Kage Rock / cliffs, then only the lots that touch the chunk.
- Each lot is drawn in a `LotFrame` ("front faces south"), so every building works on any side
  of any street. `Buildings` has the homes and shops, `Landmarks` the big buildings.
- **Size limit:** a structure can only reach 8 chunks from where it starts (vanilla's
  `ChunkGenerator.createReferences`), about 128 blocks. The capital uses nearly all of it
  (radius 124). Going bigger would need several structures stitched together.
- `/noirm village <capital|town|outpost> [leaf|sand|snow]` builds one where you stand (no site
  checks) for testing.

| | Hidden village (`hidden_village`) | Town (`ninja_town`) | Outpost (`ninja_outpost`) |
|---|---|---|---|
| Size | ~250 blocks across (wall radius 120) | ~110 (fence radius 52) | ~30 (palisade radius 14) |
| How often | spacing 64 chunks, separation 32 (rare) | shares a set with outposts (weight 2 of 5), spacing 26, never within 12 chunks of a capital | weight 3 of 5 |
| Contents | Great wall (14 high, wall walk, battlements, lookout pavilions, ladders), the great gate (pillars, roof, symbol plaque, open doors painted "a" / "un"), two side gates; the **Kage tower** (r 12, 4 floors: reception, archives, council room; balcony; office; symbol 2× size); the **Kage Rock** (a mountain with 4 carved faces and a forest on top; the Sand village has a ring of desert cliffs instead); **Academy** (classrooms with desks and blackboards, swing tree, sandpit, targets); **hospital** (wards, red cross); a **clan district** (Uchiha or Hyūga: wall, crest on the gate, koi pond, clan houses); a **shrine** with torii and cherry trees; a **hot spring**; two **training grounds** (posts, targets, memorial stone, one with a lake); the **ramen shop**; a gate guard booth; ~130 lots of houses (1–4 storeys), apartment blocks with galleries, round towers, shops (weapons, scrolls, flowers), restaurants, stalls, warehouses, gardens, parks; avenues, a plaza, streets, lanterns, power lines, trees | Main and cross street, a well on the square, the chief's house, an inn, a shrine, a training ground, a guard post, shops and houses, a fence and hedge with four wooden gates | Palisade, watchtower, 2–3 houses, campfire, training posts |
| People | Kage, guards, 4 wall patrols, ~6 rooftop runners, trainees, clan ninjas, 2 teachers, ~16 shopkeepers, ~18 villagers, ~6 children | chief, guards, a trainee, a ninja, ~8 shopkeepers, ~10 villagers, children | 3 ninjas, a merchant, a dummy |

Houses have insides: kitchens (stove, cauldron, barrel), tables and stools, beds, chests,
bookshelves, flower pots, rugs, ladders between floors, lanterns; balconies, chimneys with smoke,
rooftop water tanks and gardens. Shops have counters, cloth curtains (noren) and awnings.

**Looks by biome** (`VillagePalette`):
- **Leaf**: plains and forests. White and birch walls, orange and red roofs, a red tower.
- **Sand**: desert, badlands and savanna. Sandstone, terracotta, acacia, the Kazekage.
- **Snow**: cold biomes. Spruce, white walls and blue roofs.

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

## Limitations / ideas for later

- About 250 blocks is the most one structure can cover; a bigger capital would need several
  structures (districts) placed together.
- Trees growing in a neighbouring chunk *after* the village chunk was built can still poke over
  the edge.
- Villages need fairly flat, dry land, so capitals are rare (often 1,500–2,500 blocks apart).
- Leaf villages don't prefer Leaf clans yet (except the clan district).
- Next: missions board, masters living in villages (M4), the Kage as a teacher, bridges over rivers.
