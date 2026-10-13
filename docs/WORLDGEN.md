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

Everything is built from Java code (`com.noirm.world.village`). There are no hand-made structure
files, so there's no artist dependency.

| | Hidden village (`noirm:hidden_village`) | Outpost (`noirm:ninja_outpost`) |
|---|---|---|
| Size | ~85 blocks across (wall radius 40) | ~30 blocks (palisade radius 14) |
| How often | spacing 64 chunks, separation 32 (rare) | spacing 24, separation 10 |
| Contents | Wall with battlements and a great double gate (painted doors folded open). Central **Kage tower**: red drum, 3 floors, ladder, the village symbol on a plaque, a railed balcony, and the Kage's office with a pointed roof. **Kage Rock** with 4 carved faces. ~10–15 round, two-storey and square houses with orange/red roofs and rooftop **water tanks**, some pond gardens with a footbridge. 6 food **stalls** along the avenue, a **torii**, a **training ground** (three posts, targets, a bench), lantern posts, paved streets. | Log palisade with a gate, a **watchtower** (ladder, platform, roof), 2–3 houses, a campfire with seats, training posts with a target |
| People | **Kage** (speed/taijutsu 120–200, named Hokage / Kazekage / Village Head), 4 guards, 4 ninjas about town, 2 at the training ground, 6 merchants, 2 training dummies | 3 ninjas (gate, campfire, lookout), 1 merchant, 1 dummy |

**Looks by biome** (`VillagePalette`):
- **Leaf**: plains and forests. White and birch walls, orange and red roofs, a red tower.
- **Sand**: desert, badlands and savanna. Sandstone, terracotta, acacia, the Kazekage.
- **Snow**: cold biomes. Spruce, white walls and blue roofs.

**The site**: we sample the land height over the whole footprint (25 points for the capital, 9
for an outpost). We skip spots that are steep (more than 16 / 9 blocks of difference) or watery,
or below sea level. Then the builder clears everything above the ground (trees, hill tops) and
fills holes below with the foundation block.

**Residents** (`VillageResidents`):
- Village ninjas are `SparringNinja`s with a **resident** flag (saved as `Resident` plus `Home` in NBT).
- Residents **don't challenge** players who enter combat mode. They fight back when hit, and
  hurting any resident (or merchant) **raises the alarm**: every resident within 40 blocks turns
  on the attacker.
- Residents stay near their home spot and walk back after a fight.
- A resident beaten and recruited into your crew leaves the village.
- **Merchants** (`VillageMerchant`, a new villager-like entity) trade for emeralds:
  - They sell kunai, ramen (a new food), training dummies, sealing scrolls, arrows and a flak vest.
  - They buy wheat, iron, coal, paper and string.

## Limitations / ideas for later

- One village "piece" covers the whole site. It's simple, but the builder runs once per chunk
  (cheap, since everything outside the chunk is skipped).
- Trees growing in a neighbouring chunk *after* the village chunk was built can still poke over
  the edge.
- Houses are furnished lightly: a crafting table, a barrel and a lantern. There are no beds,
  chests or loot yet.
- The Kage's clan is random (like any ninja). Leaf villages don't prefer Leaf clans yet.
- Next steps: village clans (residents from the world's clan list), missions board, masters living
  in villages (M4), more building types (academy, hospital, Ichiraku proper), bridges over rivers.
