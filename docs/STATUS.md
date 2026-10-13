# Project status

_Last updated: 2026-10-13 · branch `claude/keen-cray-uc2d11` · PR #1 (draft)_

## Where we are

**The owner's second feedback round is done (v0.2.0). 87/87 game tests green, checked in the
real client** (screenshots: `docs/screenshots/r2-*.png`). Research notes: `docs/RESEARCH.md`.

| Area | State |
|---|---|
| **Fighting styles**: Brawler, Strong Fist (taijutsu 18), Gentle Fist (Byakugan); H switches | ✅ tested |
| **Directional attacks** (sprint = dash, back = back attack), **grab + clinch** (knee, headbutt, throw, dodge to escape) | ✅ tested |
| **Wall splats, knockdowns, juggles**; 11 new generated animations (`tools/animgen`) | ✅ tested |
| **Danger**: jutsu power from reserves, mastery and charge; **terrain destruction that heals** | ✅ tested; seen in client |
| **Canon scale**: huge Great Fireball, homing Phoenix Sage Fire / Water Dragon, Lightning Armor as a real mode | ✅ |
| **Canon jutsu** (10, see RESEARCH.md): + Rasengan, Shadow Clone, Shadow Imitation, Water Formation Wall | ✅ tested; clones seen in client |
| **Yin, Yang, Yin–Yang** natures with jutsu (Mystical Palm, Partial Multi-Size, Truth-Seeking Ball…) | ✅ tested |
| **Particles**: custom additive glow/spark/smoke/ring/shard, layered presets per nature | ✅ seen in client |
| **Byakugan rework**: no prediction; no blind spot, chakra points, 360° radar | ✅ tested; seen in client |

## In progress

Nothing. Waiting for the owner's next playtest.

## Next steps (in order)

1. **Owner playtest + feedback**: style feel (move JSON in `data/noirm/combat/moves/`), clinch
   timing, destruction amount, jutsu scale.
2. **More canon jutsu** on the sample's pattern (one behavior + JSON each); Phoenix Sage Fire's
   hidden shuriken was skipped.
3. **Training, part 2:** chakra control (tree climbing → Rasengan path), nature training.
4. **Might Guy (M4):** the first master; teaches Strong Fist properly and the Eight Gates.
5. Later: villages, beasts' homes, squads, missions, Mangekyō.

## Code map

| Package / path | What lives there |
|---|---|
| `com.noirm` | `Noirm`: entry point; `id()` / `vanillaId()` helpers |
| `com.noirm.training` | `TrainingSystem` (4 stats: XP sources, curve, drill cap, speed → movement, vitality → health, reserves → chakra), `TrainingData`, `TrainingDummyItem` |
| `com.noirm.player` | `ShinobiData` (the hub of all player data: resources, stats and the powers sub-objects), capability, `ShinobiEvents` (resources), `PowerEvents` (powers: first-join clan, ticking, sync, Resolve and awakening hooks), `PowerVisuals` |
| `com.noirm.combat` | `CombatSystem` (rules + constants, clinch, wall splat, juggles), `StyleSystem` (choosing styles), `CombatState`, `Perception` (incl. eye/state multipliers), `CombatData`, `CombatEffects`, `CombatEvents` |
| `com.noirm.clan` | `Clan`, `Nature` (basic + combined), `NatureSystem` (tree: owned/available/locked, unlock, mastery unlocks), `ClanTrait`, `AwakeningPath`, `ClanGenerator`, `WorldClans` (per-world registry), `ClanData` (canon), `ClanIdentity` (on the player), `ClanSystem` (joining) |
| `com.noirm.jutsu` | `JutsuDefinition`/`JutsuData` (JSON), `JutsuBehaviors` (what jutsu do), `HyugaTechniques` (Rotation, 64 Palms), `ElementalBehaviors` (bind, pillar, cloud, rain, beam, buff), `CanonTechniques` (dome, heal, Rasengan, shadow bind, shadow clone), `ModeSystem` (toggle modes like Lightning Armor), `JutsuAreas` (lingering areas), `JutsuCaster` (casting, affinity, power, cost), `JutsuBook` (known, mastery, loadout), `JutsuProjectile`, `Substitution`, `EarthWallBlock`, `JutsuUser` (NPCs) |
| `com.noirm.dojutsu` | `Sharingan`, `Byakugan` (incl. Gentle Fist, chakra points), `ThreatSense` (eyes vs vanilla mobs/projectiles), `EyeData` |
| `com.noirm.awakening` | `AwakeningSystem`, `AwakeningData` |
| `com.noirm.biju` | `BijuSystem` (cloak, sealing, born jinchūriki, beast names), `BijuData`, `SealingScrollItem` |
| `com.noirm.data` | `CodecJsonLoader` (generic JSON data loader) |
| `com.noirm.network` | `NoirmNetwork` (protocol 5), `JutsuCatalogPacket`, `CombatInputPackets`, `CombatClientPackets`, `PowerPackets`, `SyncShinobiDataPacket` |
| `com.noirm.client` | combat (`CombatKeys`, `CombatInput`, `LockOn`, `CombatCamera`, `PlayerAnimations`, `ClientCombat`), powers (`PowerKeys`, `ClientPowers`, `PowerHud`, `JutsuScreen`, `SharinganEyesLayer`), `ShinobiHudOverlay`, `NoirmRenderers`, `SparringNinjaRenderer`, `TrainingDummyRenderer`, `TailedBeastRenderer`, `ShadowCloneRenderer`, `ByakuganVision` (x-ray silhouettes, chakra points, radar), `EyeEffects` (heartbeat warning, afterimages, activation), `JutsuScreen` (cards + nature tree), `ClientJutsuCatalog` |
| `com.noirm.vfx` | `Vfx` (palettes, emitters, presets), `VfxOptions`/`VfxKind`, `NoirmParticles`; client side `VfxParticle` |
| `com.noirm.world` | `Destruction` (craters, scorch, freeze, wall smash… and the regen queue; gamerules) |
| `com.noirm.entity` | `SparringNinja` (+ jinchūriki, style), `ShadowClone`, `NinjaFightGoal`, `TrainingDummy`, `TailedBeast`, `BeastFightGoal` |
| `com.noirm.registry` | `NoirmBlocks`, `NoirmEntities`, `NoirmItems` |
| `com.noirm.command` | `NoirmCommand` (resources/stats), `PowersCommand` (clan, nature, jutsu, sharingan, resolve, awaken, biju) |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests`, `CombatGameTests`, `PowersGameTests`, `TrainingGameTests`, `ByakuganGameTests`, `BijuGameTests`, `ThreatSenseGameTests`, `NatureGameTests`, `DangerGameTests`, `CanonGameTests`, `StyleGameTests`, `TestClans` |
| `data/noirm/` | `combat/` (moves, styles: basic, brawler, strong_fist, gentle_fist, beast), `clans/` (canon), `jutsu/` (by nature, `byakugan/`, `biju/`), `recipes/`, `structures/empty.nbt` |
| `tools/animgen/` | `gen_anims.py`: generates our own player animations from simple poses |
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
- Text is in English (`en_us`) and Spanish (`es_es`, `es_mx`).

## Open questions for the owner

- How do the animations feel now that they show? Which moves need new or different ones?
- Jutsu balance: costs, cooldowns, how fast mastery chains unlock; which natures need more jutsu?
- How hard should training a basic nature you weren't born with be (time, a master, an item)?
- Should Yin, Yang and Yin-Yang join the tree (genjutsu and medical / body jutsu)?
- Where should tailed beasts live in the world?
