# Project status

_Last updated: 2026-10-13 (round 4, NPC animation) · branch `round4-animation`_
_Last updated: 2026-10-13 (round 4, villages) · branch `round4-worldgen`_

## Where we are

**The owner's third feedback round is done (v0.3.0). 95/95 game tests green; checked in the real
client** (screenshots `docs/screenshots/r3-*.png`, `skins-catalog.png`).

| Area | State |
|---|---|
| **Fix:** a big Great Fireball burst on its own caster (its touch check didn't skip the caster) | ✅ tested |
| **Lightning**: real crackling bolts (client renderer, `BoltRenderer`) on every raiton jutsu | ✅ seen in client |
| **8 new jutsu** + behaviors strike (Kirin), wave, nova | ✅ tested |
| **Lightning Armor** ×2 speed, ×1.8 damage, 55% less damage taken, ×2.2 perception; modes +25%/mastery | ✅ tested |
| **Styles feel different**: per-style stances, Strong Fist's own animations, per-style impacts | ✅ seen in client |
| **Crew (allies)**: beat ninjas low → they yield → invite; follow, fight, wait; no friendly fire | ✅ tested |
| **Killable tailed beasts** (seal or finish once subdued) | ✅ tested |
| **Deeper Shadow Clones**: real health, your jutsu, Sharingan, mode, cloak | ✅ tested |
| **Clan outfits**: 23 generated skins (`tools/skingen`) | ✅ |
| **NPCs animate like the player** (same JSON, synced per move/dodge, stances, cross-fades) | ✅ tested (server side); visuals not yet seen in client |
| **Bendy NPC model**: elbows, knees, waist; `"bend"` keyframes; natural walk bends | ✅ UVs checked (`check_uv.py`); visuals not yet seen in client |
| **Speed movement**: run ×√(speed/10), higher jumps, sprint-jump leap, step-ups | ✅ tested |
| **No stat cap**: power ∝ stat, durability √, beasts 30→470, Tailed Beast Bomb at beast scale | ✅ tested |
| **Ninja villages (round 4)**: rare hidden village (wall, gate, Kage tower, Kage Rock, houses with water tanks, stalls, training ground) and common outposts; Leaf/Sand/Snow looks; residents, Kage, merchants (`docs/WORLDGEN.md`) | ✅ tested (game tests + a real server world); not yet seen in the client |

## In progress

Round 4 animation: **needs a look in the real client** (smoke test): ninja stance in a fight,
moves matching the player's, rolls, guard, flinch, bent elbows/knees, skin seams at the joints.
Round 4 villages are done on `round4-worldgen`. They still need a look in the real client (renderer
for `village_merchant`, how villages look).

## Next steps (in order)

1. **Owner playtest + feedback**: style feel (move JSON in `data/noirm/combat/moves/`), clinch
   timing, destruction amount, jutsu scale.
2. **More canon jutsu** on the sample's pattern (one behavior + JSON each); Phoenix Sage Fire's
   hidden shuriken was skipped.
3. **Training, part 2:** chakra control (tree climbing → Rasengan path), nature training.
4. **Might Guy (M4):** the first master; teaches Strong Fist properly and the Eight Gates.
5. **Villages, part 2:** residents from the village's clan, an academy, a mission board, masters
   living in villages; the Kage as a boss/teacher.
6. Later: beasts' homes, squads, missions, Mangekyō.

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
| `com.noirm.biju` | `BijuSystem` (cloak, sealing, born jinchūriki, beast names), `BijuData`, `SealingScrollItem` |
| `com.noirm.data` | `CodecJsonLoader` (generic JSON data loader) |
| `com.noirm.network` | `NoirmNetwork` (protocol 6), `VfxPackets`, `JutsuCatalogPacket`, `CombatInputPackets`, `CombatClientPackets`, `PowerPackets`, `SyncShinobiDataPacket` |
| `com.noirm.client` | combat (`CombatKeys`, `CombatInput`, `LockOn`, `CombatCamera`, `PlayerAnimations`, `ClientCombat`), powers (`PowerKeys`, `ClientPowers`, `PowerHud`, `JutsuScreen`, `SharinganEyesLayer`), `ShinobiHudOverlay`, `NoirmRenderers`, `SparringNinjaRenderer`, `TrainingDummyRenderer`, `TailedBeastRenderer`, `ShadowCloneRenderer`, `BoltRenderer` (lightning), `ByakuganVision` (x-ray silhouettes, chakra points, radar), `EyeEffects` (heartbeat warning, afterimages, activation), `JutsuScreen` (cards + nature tree), `ClientJutsuCatalog` |
| `com.noirm.client.anim` | NPC animation: `KeyframeAnimation` (reads `player_animation/*.json`, plain Java so tests use it), `SkeletonPose`, `NpcAnimations` (client reload listener), `NpcAnimator` (layers + cross-fades); the model is `client/BendyNinjaModel` |
| `com.noirm.ally` | `Crew` (yield, recruit, sides, crew rules) |
| `com.noirm.vfx` | `Vfx` (palettes, emitters, presets), `VfxOptions`/`VfxKind`, `NoirmParticles`; client side `VfxParticle` |
| `com.noirm.world` | `Destruction` (craters, scorch, freeze, wall smash… and the regen queue; gamerules) |
| `com.noirm.world.village` | `NinjaVillageStructure` (where villages go), `VillagePiece` (saved per chunk), `VillageBuilder` (every building, in code), `VillageCanvas` (rotated, chunk-clipped drawing), `VillagePalette` (Leaf/Sand/Snow), `VillageKind` (capital/outpost), `VillageResidents` (spawning, alarm) |
| `com.noirm.entity` | `SparringNinja` (+ jinchūriki, style, village resident flag), `VillageMerchant` (emerald trades), `ShadowClone`, `NinjaFightGoal`, `TrainingDummy`, `TailedBeast`, `BeastFightGoal` |
| `com.noirm.registry` | `NoirmBlocks`, `NoirmEntities`, `NoirmItems` (incl. ramen), `NoirmStructures` (structure + piece types) |
| `com.noirm.command` | `NoirmCommand` (resources/stats), `PowersCommand` (clan, nature, jutsu, sharingan, resolve, awaken, biju) |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests`, `CombatGameTests`, `PowersGameTests`, `TrainingGameTests`, `ByakuganGameTests`, `BijuGameTests`, `ThreatSenseGameTests`, `NatureGameTests`, `DangerGameTests`, `CanonGameTests`, `StyleGameTests`, `CrewGameTests`, `GreatGameTests`, `VillageGameTests`, `TestClans` |
| `data/noirm/` | `combat/` (moves, styles: basic, brawler, strong_fist, gentle_fist, beast), `clans/` (canon), `jutsu/` (by nature, `byakugan/`, `biju/`), `recipes/`, `structures/empty.nbt`, `worldgen/structure` + `structure_set` (villages), `tags/worldgen/biome/has_structure/` |
| `tools/skingen/` | `gen_skins.py`: the clan outfit skins |
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
  players or ninjas. Beasts are subdued, never killed by fighting. Placeholder art (scaled zoglin).
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
- **Villages (round 4):** built procedurally in Java (no NBT files), one piece per village,
  `top_layer_modification` step so trees are cleared. Residents never start fights; attacking one
  alarms the village. Capital: spacing 64 / separation 32 chunks; outpost 24 / 10.

## Open questions for the owner

- Do the NPCs' bent limbs look right? Should the player bend too (needs bendy-lib, an extra mod)?
- How do the animations feel now that they show? Which moves need new or different ones?
- Jutsu balance: costs, cooldowns, how fast mastery chains unlock; which natures need more jutsu?
- How hard should training a basic nature you weren't born with be (time, a master, an item)?
- Should Yin, Yang and Yin-Yang join the tree (genjutsu and medical / body jutsu)?
- Where should tailed beasts live in the world?
- Villages: should each village belong to a clan (Uchiha district…)? Should the Kage teach or
  give missions? Is the capital too rare (about one per 1,000 blocks at best)?
