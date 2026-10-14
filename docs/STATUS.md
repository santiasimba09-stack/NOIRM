# Project status

_Last updated: 2026-10-14 (villages part 4, v0.7.0) · branch `claude/keen-cray-uc2d11`_

## Where we are

**Villages part 4 is done (v0.7.0). 144/144 game tests green; checked in the real client**
(screenshots `docs/screenshots/r7-*.jpg`). Earlier rounds are in `CHANGELOG.md`.

| Area | State |
|---|---|
| **Hidden village ~545 blocks**, built from per-chunk "ward" starts sharing one plan | ✅ tested; a world-generated one checked in client (whole across ~1,000 chunks) |
| **Six looks:** Leaf (fan + river + canon wall), Sand, Snow, **Mist** (islands, canals, mist), **Cloud** (rock spires, domes), **Stone** (tapering towers, rope bridges) | ✅ tested; each built in client |
| **Looks:** colourways, façades, ring eaves, spires, rooftop clutter, signage, lamps, yards, landmarks | ✅ |
| **Life:** villagers chat, sit, run home from monsters; residents fight monsters in their patch; animals | ✅ tested |
| Living with other structures: vanilla villages kept clear, squatter mobs removed, no raider spawns inside | ✅ |
| Towns (~145 blocks) and outposts | ✅ tested |

## In progress

Nothing. Waiting on the owner's playtest of v0.7.0.

## Next steps (in order)

1. **Owner playtest + feedback** on all six looks; how often villages appear; world generation speed.
2. **Villages, part 5 (researched, not built):** terraces on uneven land, the village's own clans
   in the clan district (Mist/Cloud/Stone clans), town designs per look, roads between settlements.
3. **Villages and masters:** missions board, masters living in villages, the Kage as a teacher.
4. **Training, part 2:** chakra control (tree climbing → Rasengan path), nature training.
5. **Might Guy (M4):** the first master; teaches Strong Fist properly and the Eight Gates.

## Code map

| Package / path | What lives there |
|---|---|
| `com.noirm` | `Noirm`: entry point; `id()` / `vanillaId()` helpers |
| `com.noirm.training` | `TrainingSystem` (4 stats: XP sources, curve, drill cap, speed → movement, vitality → health, reserves → chakra), `TrainingData`, `TrainingDummyItem` |
| `com.noirm.player` | `ShinobiData` (the hub of all player data: resources, stats and the powers sub-objects), capability, `ShinobiEvents` (resources), `PowerEvents` (powers: first-join clan, ticking, sync, Resolve and awakening hooks), `PowerVisuals` |
| `com.noirm.combat` | `CombatSystem` (rules + constants, clinch, wall splat, juggles), `StyleSystem` (choosing styles), `CombatState`, `Perception` (incl. eye/state multipliers), `CombatData`, `CombatEffects`, `CombatEvents` |
| `com.noirm.clan` | `Clan`, `Nature` (basic + combined), `NatureSystem` (tree: owned/available/locked, unlock, mastery unlocks), `ClanTrait`, `AwakeningPath`, `ClanGenerator`, `WorldClans` (per-world registry), `ClanData` (canon), `ClanIdentity` (on the player), `ClanSystem` (joining) |
| `com.noirm.jutsu` | `JutsuDefinition`/`JutsuData` (JSON), `JutsuBehaviors` (what jutsu do), `HyugaTechniques` (Rotation, 64 Palms), `ElementalBehaviors` (bind, pillar, cloud, rain, beam, buff), `CanonTechniques` (dome, heal, Rasengan, shadow bind, shadow clone), `GreatTechniques` (strike, wave, nova), `ModeSystem` (toggle modes like Lightning Armor), `JutsuAreas` (lingering areas), `JutsuCaster` (casting, affinity, power, cost), `JutsuBook` (known, mastery, loadout), `JutsuProjectile`, `Substitution`, `EarthWallBlock`, `JutsuUser` (NPCs) |
| `com.noirm.dojutsu` | `Sharingan`, `Byakugan` (incl. Gentle Fist, chakra points), `ThreatSense` (eyes vs vanilla mobs/projectiles), `EyeData` |
| `com.noirm.awakening` | `AwakeningSystem`, `AwakeningData` |
| `com.noirm.biju` | `BijuSystem` (cloak and tails, sealing, born jinchūriki, beast names), `BijuData`, `BeastBond` (bond, requests, takeovers, chakra), `SealingScrollItem` |
| `com.noirm.data` | `CodecJsonLoader` (generic JSON data loader) |
| `com.noirm.network` | `NoirmNetwork` (protocol 7), `VfxPackets`, `JutsuCatalogPacket`, `CombatInputPackets`, `CombatClientPackets`, `PowerPackets`, `SyncShinobiDataPacket` |
| `com.noirm.client` | combat (`CombatKeys`, `CombatInput`, `LockOn`, `CombatCamera`, `PlayerAnimations`, `ClientCombat`), powers (`PowerKeys`, `ClientPowers`, `PowerHud`, `JutsuScreen`, `SharinganEyesLayer`), `ShinobiHudOverlay`, `VillageFog` (mist and haze inside villages), `NoirmRenderers`, `SparringNinjaRenderer`, `TrainingDummyRenderer`, `TailedBeastRenderer`, `ShadowCloneRenderer`, `BoltRenderer` (lightning), `ByakuganVision` (x-ray silhouettes, chakra points, radar), `EyeEffects` (heartbeat warning, afterimages, activation), `JutsuScreen` (cards + nature tree), `ClientJutsuCatalog` |
| `com.noirm.client.anim` | NPC animation: `KeyframeAnimation` (reads `player_animation/*.json`, plain Java so tests use it), `SkeletonPose`, `NpcAnimations` (client reload listener), `NpcAnimator` (layers + cross-fades); the model is `client/BendyNinjaModel` |
| `com.noirm.ally` | `Crew` (yield, recruit, sides, crew rules), `AllyMind` (respect, morale, retreat, support), `AllyTraining` (stat XP), `Trait`, `Order`, `AllyMenu` + `AllyPackets` (the ally screen) |
| `com.noirm.gear` | `NoirmGear` (wearable pieces), `GearItem`, `GearMaterial`, `ClanOutfits` (who wears what) |
| `com.noirm.vfx` | `Vfx` (palettes, emitters, presets), `VfxOptions`/`VfxKind`, `NoirmParticles`; client side `VfxParticle` |
| `com.noirm.world` | `Destruction` (craters, scorch, freeze, wall smash… and the regen queue; gamerules) |
| `com.noirm.world.village` | `NinjaVillageStructure` (where villages go; the site check; a hidden village's core start), `VillageWardStructure` + `WardPlacement` (one start per chunk of a hidden village), `VillagePiece` (saved per chunk), `VillagePlan` + `VillagePlanner` (the layout from a seed: streets and river rasters, districts, lots, props, people; cached, indexed per chunk), `VillageBuilder` (ground, streets, street furniture; draws lots per chunk), `VillageBuildQueue` (`/noirm village`, a few chunks per tick), `Waterways` (river, canals, banks, bridges), `Fortifications` (walls and bastions, gates, Kage Rock with stairs and tunnels, cliff and spire rings, fences), `VillageAtmosphere` (tells the client which village it's in), `VillageSpawns` (no raiders inside), `LotFrame` (draw a lot front-to-the-south), `Buildings` (homes, shops, façades, rooftops), `RoundBuildings` (spires, jōnin station, rock spires), `Yards` (yards, giant tree, cemetery), `Landmarks` (Kage residence, Academy, hospital, clan district, shrine, training grounds, hot spring), `Shapes` (drums, ring eaves, medallions), `Materials` (block mixes, colourways), `VillageCanvas`, `VillagePalette` (Leaf/Sand/Snow), `VillageKind`, `VillageResidents` (spawning, alarm), `VillageRoutineGoal` (rooftop runners, patrols, trainees) |
| `com.noirm.entity` | `SparringNinja` (+ jinchūriki, style, village resident flag, fights monsters in its village), `VillageMerchant` (villagers: shop roles, civilians, children, chief; day/night routine; speech bubbles), `VillagerChatGoal`, `VillagerSitGoal` + `SeatEntity`, `VillagerShelterGoal`, `ShadowClone`, `NinjaFightGoal`, `TrainingDummy`, `TailedBeast`, `BeastFightGoal` |
| `com.noirm.registry` | `NoirmBlocks`, `NoirmEntities`, `NoirmItems` (incl. ramen), `NoirmStructures` (structure + piece types) |
| `com.noirm.command` | `NoirmCommand` (resources/stats), `PowersCommand` (clan, nature, jutsu, sharingan, resolve, awaken, biju) |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests`, `CombatGameTests`, `PowersGameTests`, `TrainingGameTests`, `ByakuganGameTests`, `BijuGameTests`, `ThreatSenseGameTests`, `NatureGameTests`, `DangerGameTests`, `CanonGameTests`, `StyleGameTests`, `CrewGameTests`, `GreatGameTests`, `VillageGameTests`, `VillageLayoutGameTests`, `VillageWorldgenGameTests`, `VillageLifeGameTests`, `TestClans`, `TestKit` (shared helpers) |
| `data/noirm/` | `combat/` (moves, styles: basic, brawler, strong_fist, gentle_fist, beast), `clans/` (canon), `jutsu/` (by nature, `byakugan/`, `biju/`), `recipes/`, `structures/empty.nbt`, `worldgen/structure` + `structure_set` (villages), `tags/worldgen/biome/has_structure/` |
| `tools/skingen/` | `gen_parts.py` + `looks.py`: the skin parts and look tables behind random ninja looks |
| `tools/gear/` | `gen_gear.py`: the gear textures and icons |
| `tools/animgen/` | `gen_anims.py`: generates our own animations from simple poses (with `bend`s); `check_uv.py`: checks the bendy model's skin UVs |
| `tools/client-smoke/` | Run the real client off-screen (Hyūga player, dummy, ninja behind a wall, a beast, a subdued Nine-Tails) |

## Decisions made

- **Forge 1.20.1**, official mappings, single-player first.
- **Pillars:** combat feel, training and masters, villages and world gen, exploration. Missions
  are secondary. Squads (recruited allies) come later.
- **No copying NYDO assets.** MIT Boundless animations and the CC-BY katana are the exceptions.
- **Art:** placeholders and vanilla textures until the mechanics are fun.
- **Combat:** switchable (R); launcher = crouch + heavy; NOIRM hits cancel vanilla knockback;
  the server is authoritative.
- **Perception:** proportional to the strength ratio (cap 2.5×); from speed + taijutsu, boosted by
  the Sharingan, Keen Eyes, being locked in and the cloak. It applies both ways.
- **Clans (owner):** random at first join; mostly custom generated clans in an **open-ended,
  growing per-world list** (canon ~10%); villages will reuse the same list.
- **Natures (owner):** the clan gives a great bonus; others are very hard to get (training, M3.5).
  Sharingan-copied jutsu of a missing nature work at an imitation penalty (0.4).
- **Jutsu casting (owner):** a 4-slot loadout, each jutsu with its own feel (tap / charge /
  channel). Hand seals and wheels were rejected.
- **Sharingan (owner):** all four of perception, precognition, copy and genjutsu. It is awakened
  only through the near-death awakening, then grows with use.
- **Awakening (owner):** Resolve-based, identity-dependent (Uchiha → Sharingan, jinchūriki → beast
  takes over, nature-surge clans, will). Guy's students → gates (M4).
- **Biju (owner):** all three routes to becoming a jinchūriki: born one (3%), defeating and
  sealing a beast, or someone sealing one into you. Beasts can be sealed into yourself, other
  players or ninjas. Beasts are subdued first, then sealed or finished. Placeholder art (scaled zoglin).
- **Beast bond (round 4):** tails are drawn one at a time; how many you can hold safely depends
  on the bond (−100…100). Version 1 orange, version 2 crimson, full bond gold.
- **Allies (round 4):** respect starts from relative power and moves with your actions; below 5
  they leave. Crew mates never hurt their side, and their jutsu don't hurt you.
- **Byakugan (owner):** continue with it for the Hyūga (born with it, no awakening needed).
- **Training (owner):** first part = the dummy plus speed and taijutsu growth. Solo drills cap at 30;
  past that only real opponents teach (stronger = more).
- Game tests use fixed clans (`TestClans`) so random rolls never change timings; tests that
  trigger focus or eye effects run in their own batches.
- **Natures (owner):** a tree like the owner's reference image, in our own style: 5 basic natures,
  8 two-part combinations, Dust (three parts). A combination is available once you own both parts
  and have a jutsu of each at ★2; basic natures you lack need very hard training (not built yet).
- **Jutsu learning:** clan starters, then mastery chains (★2 on one teaches the next).
- **Eyes vs the world (owner):** the same rules apply to vanilla mobs and projectiles.
- **Stats (owner):** four trained stats: taijutsu, speed, vitality (health), reserves (chakra).
- **NPC animation:** NPCs play the player's animation files on a jointed model (elbows, knees,
  waist); players stay on PlayerAnimator without bends (bendy-lib = possible follow-up). See
  `docs/COMBAT_DESIGN.md`, "NPC animation and bendy limbs".
- Text is in English (`en_us`) and Spanish (`es_es`, `es_mx`).
- **Villages (round 4):** built procedurally in Java (no NBT files), `top_layer_modification`
  step so trees are cleared. Residents never start fights with players; attacking one alarms the
  village.
- **Villages v3:** a hidden village is ~545 blocks across, built as a core start plus one ward
  start per chunk (the 8-chunk structure reach can't be raised); spacing 96 / separation 48 chunks.
  Everything from vanilla blocks; the research (`docs/research-villages-v3.txt`) is motifs only.

## Open questions for the owner

- Ally respect: too quick or too slow to change? Should allies ever betray you?
- Do the NPCs' bent limbs look right? Should the player bend too (needs bendy-lib, an extra mod)?
- How do the animations feel now that they show? Which moves need new or different ones?
- Jutsu balance: costs, cooldowns, how fast mastery chains unlock; which natures need more jutsu?
- How hard should training a basic nature you weren't born with be (time, a master, an item)?
- Should Yin, Yang and Yin-Yang join the tree (genjutsu and medical / body jutsu)?
- Where should tailed beasts live in the world?
- Villages: does the ~545-block capital feel big enough, or too big to walk? Denser or more open?
  Capitals are rare (spacing 96 chunks, often 2,000+ blocks apart): more often? Should the Kage
  teach or give missions?
