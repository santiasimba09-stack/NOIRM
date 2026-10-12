# Project status

_Last updated: 2026-10-12 · branch `claude/keen-cray-uc2d11` · PR #1 (draft)_

## Where we are

**Milestone 3.5: Training, plus the Byakugan and tailed beasts. ✅ Built, 58/58 game tests
green, checked in the real client.** Design and numbers: `docs/POWERS_DESIGN.md` (sections 8–10).
M2 combat and M3 powers are also done. All of it waits on the owner's hands-on feedback.

| Area | State |
|---|---|
| M0–M2: foundation, chakra/stamina, combat feel v1 | ✅ (see `COMBAT_DESIGN.md`) |
| M3: clans, jutsu loadout, Sharingan, awakening, cloak, ninjas with clans | ✅ tested + seen in client |
| Training: dummy (item + recipe), taijutsu/speed XP, speed → movement | ✅ tested; dummy, combo counter, stat bars seen in client |
| Byakugan (Hyūga): x-ray chakra sight, all-round guard and warnings, Gentle Fist, Rotation, 64 Palms | ✅ tested; eyes, silhouettes seen in client |
| Tailed beasts: beast (1–9 tails), subdue, Sealing Scroll, born/NPC jinchūriki, Tailed Beast Ball | ✅ tested; see the smoke notes in the CHANGELOG |

## In progress

Nothing. Waiting for the owner to play (see `docs/GETTING_STARTED.md`, "How to test").

## Next steps (in order)

1. **Owner playtest + feedback** on combat, powers, training, Byakugan and beasts. Then tune: JSON
   in `data/noirm/combat/` and `data/noirm/jutsu/`, constants in `CombatSystem`,
   `TrainingSystem`, `Sharingan`, `Byakugan`, `AwakeningSystem`, `BijuSystem`, `TailedBeast`.
2. **Training, part 2:** the training post (speed drills), very hard nature training (the only
   way to gain a nature besides imitation), chakra control.
3. **Might Guy (M4):** the first master. He teaches a taijutsu style in stages (new
   `combat/styles` + moves); the Eight Gates as his students' awakening path.
4. Later: where beasts live in the world (rare lairs, exploration), villages (where clans live),
   squads, missions, Mangekyō, beast art.

## Code map

| Package / path | What lives there |
|---|---|
| `com.noirm` | `Noirm`: entry point; `id()` / `vanillaId()` helpers |
| `com.noirm.training` | `TrainingSystem` (XP sources, curve, drill cap, speed → movement), `TrainingData`, `TrainingDummyItem` |
| `com.noirm.player` | `ShinobiData` (the hub of all player data: resources, stats and the powers sub-objects), capability, `ShinobiEvents` (resources), `PowerEvents` (powers: first-join clan, ticking, sync, Resolve and awakening hooks), `PowerVisuals` |
| `com.noirm.combat` | `CombatSystem` (rules + constants), `CombatState`, `Perception` (incl. eye/state multipliers), `CombatData`, `CombatEffects`, `CombatEvents` |
| `com.noirm.clan` | `Clan`, `Nature`, `ClanTrait`, `AwakeningPath`, `ClanGenerator`, `WorldClans` (per-world registry), `ClanData` (canon), `ClanIdentity` (on the player), `ClanSystem` (joining) |
| `com.noirm.jutsu` | `JutsuDefinition`/`JutsuData` (JSON), `JutsuBehaviors` (what jutsu do), `HyugaTechniques` (Rotation, 64 Palms), `JutsuCaster` (casting, affinity, power, cost), `JutsuBook` (known, mastery, loadout), `JutsuProjectile`, `Substitution`, `EarthWallBlock`, `JutsuUser` (NPCs) |
| `com.noirm.dojutsu` | `Sharingan`, `Byakugan` (incl. Gentle Fist, chakra points), `EyeData` |
| `com.noirm.awakening` | `AwakeningSystem`, `AwakeningData` |
| `com.noirm.biju` | `BijuSystem` (cloak, sealing, born jinchūriki, beast names), `BijuData`, `SealingScrollItem` |
| `com.noirm.data` | `CodecJsonLoader` (generic JSON data loader) |
| `com.noirm.network` | `NoirmNetwork`, `CombatInputPackets`, `CombatClientPackets`, `PowerPackets`, `SyncShinobiDataPacket` |
| `com.noirm.client` | combat (`CombatKeys`, `CombatInput`, `LockOn`, `CombatCamera`, `PlayerAnimations`, `ClientCombat`), powers (`PowerKeys`, `ClientPowers`, `PowerHud`, `JutsuScreen`, `SharinganEyesLayer`), `ShinobiHudOverlay`, `NoirmRenderers`, `SparringNinjaRenderer`, `TrainingDummyRenderer`, `TailedBeastRenderer`, `ByakuganVision` (x-ray silhouettes) |
| `com.noirm.entity` | `SparringNinja` (+ jinchūriki), `NinjaFightGoal`, `TrainingDummy`, `TailedBeast`, `BeastFightGoal` |
| `com.noirm.registry` | `NoirmBlocks`, `NoirmEntities`, `NoirmItems` |
| `com.noirm.command` | `NoirmCommand` (resources/stats), `PowersCommand` (clan, nature, jutsu, sharingan, resolve, awaken, biju) |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests`, `CombatGameTests`, `PowersGameTests`, `TrainingGameTests`, `ByakuganGameTests`, `BijuGameTests`, `TestClans` |
| `data/noirm/` | `combat/` (moves, styles: basic, beast), `clans/` (canon), `jutsu/` (by nature, `byakugan/`, `biju/`), `recipes/`, `structures/empty.nbt` |
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
- Text is in English (`en_us`) and Spanish (`es_es`, `es_mx`).

## Open questions for the owner

- How do combat, powers, training, the Byakugan and the beasts feel? Balance: XP speed, drill cap,
  beast strength, Tailed Beast Ball damage, how rare jinchūriki should be.
- Where should tailed beasts live in the world (lairs, special biomes, events)? Right now they
  come from spawn eggs, fallen jinchūriki ninjas and released scrolls.
- Should other clans get rare bloodlines (generated kekkei genkai)?
