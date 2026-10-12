# Project status

_Last updated: 2026-10-12 · branch `claude/keen-cray-uc2d11` · PR #1 (draft)_

## Where we are

**Milestone 3: Identity & powers. ✅ Built, 37/37 game tests green, and checked in the real
client** (screenshots: `docs/screenshots/m3-*.png`). Design and numbers are in
`docs/POWERS_DESIGN.md`. M2 combat is also done. Both wait on the owner's hands-on feedback.

| Area | State |
|---|---|
| M0–M2: foundation, chakra/stamina, combat feel v1 | ✅ (see `COMBAT_DESIGN.md`) |
| Clans: canon JSON + generated per world, natures, traits, first-join roll | ✅ tested + seen in client (chat + HUD) |
| Jutsu: 4-slot loadout (keys 1–4 in combat mode), J screen, 9 jutsu, mastery, cooldowns | ✅ tested; fireball, charge bar, cooldown, screen seen in client |
| Sharingan: toggle G, perception, precognition, genjutsu, copy, red eyes | ✅ tested; HUD + eyes seen in client |
| Awakening: Resolve, locked in → exhausted, identity paths | ✅ tested (not seen in client: needs a near-death fight) |
| Jinchūriki cloak (B), beast takeover | ✅ tested; cloak aura seen in client |
| Sparring ninjas with clans, jutsu, Sharingan | ✅ tested (fireball/genjutsu/precog); ranged AI casting not seen yet |

## In progress

Nothing. Waiting for the owner to play M2 and M3 (see `docs/GETTING_STARTED.md`, "How to test").

## Next steps (in order)

1. **Owner playtest + feedback** on combat (M2) and powers (M3). Then tune: JSON in
   `data/noirm/combat/` and `data/noirm/jutsu/`, constants in `CombatSystem`, `Sharingan`,
   `AwakeningSystem` and `BijuSystem`.
2. **Training (M3.5):** a training dummy and post; taijutsu XP and speed growth; very hard nature
   training (the only way to gain a nature besides imitation).
3. **Might Guy (M4):** the first master. He teaches a taijutsu style in stages (new
   `combat/styles` + moves); the Eight Gates as his students' awakening path.
4. Later: villages (where clans live), squads, missions, Mangekyō, full tailed beasts.

## Code map

| Package / path | What lives there |
|---|---|
| `com.noirm` | `Noirm`: entry point; `id()` / `vanillaId()` helpers |
| `com.noirm.player` | `ShinobiData` (the hub of all player data: resources, stats and the powers sub-objects), capability, `ShinobiEvents` (resources), `PowerEvents` (powers: first-join clan, ticking, sync, Resolve and awakening hooks), `PowerVisuals` |
| `com.noirm.combat` | `CombatSystem` (rules + constants), `CombatState`, `Perception` (incl. eye/state multipliers), `CombatData`, `CombatEffects`, `CombatEvents` |
| `com.noirm.clan` | `Clan`, `Nature`, `ClanTrait`, `AwakeningPath`, `ClanGenerator`, `WorldClans` (per-world registry), `ClanData` (canon), `ClanIdentity` (on the player), `ClanSystem` (joining) |
| `com.noirm.jutsu` | `JutsuDefinition`/`JutsuData` (JSON), `JutsuBehaviors` (what jutsu do), `JutsuCaster` (casting, affinity, power, cost), `JutsuBook` (known, mastery, loadout), `JutsuProjectile`, `Substitution`, `EarthWallBlock`, `JutsuUser` (NPCs) |
| `com.noirm.dojutsu` | `Sharingan`, `EyeData` |
| `com.noirm.awakening` | `AwakeningSystem`, `AwakeningData` |
| `com.noirm.biju` | `BijuSystem`, `BijuData` |
| `com.noirm.data` | `CodecJsonLoader` (generic JSON data loader) |
| `com.noirm.network` | `NoirmNetwork`, `CombatInputPackets`, `CombatClientPackets`, `PowerPackets`, `SyncShinobiDataPacket` |
| `com.noirm.client` | combat (`CombatKeys`, `CombatInput`, `LockOn`, `CombatCamera`, `PlayerAnimations`, `ClientCombat`), powers (`PowerKeys`, `ClientPowers`, `PowerHud`, `JutsuScreen`, `SharinganEyesLayer`), `ShinobiHudOverlay`, `NoirmRenderers`, `SparringNinjaRenderer` |
| `com.noirm.entity` | `SparringNinja`, `NinjaFightGoal` |
| `com.noirm.registry` | `NoirmBlocks`, `NoirmEntities`, `NoirmItems` |
| `com.noirm.command` | `NoirmCommand` (resources/stats), `PowersCommand` (clan, nature, jutsu, sharingan, resolve, awaken, biju) |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests`, `CombatGameTests`, `PowersGameTests`, `TestClans` |
| `data/noirm/` | `combat/` (moves, styles), `clans/` (canon), `jutsu/` (by nature), `structures/empty.nbt` |
| `tools/client-smoke/` | Run the real client off-screen (Uchiha + 3 tomoe + 9 tails + a Hyūga ninja) |

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
- **Biju (owner):** start with the jinchūriki cloak; full beasts later (they need art).
- Game tests use fixed clans (`TestClans`) so random rolls never change timings; tests that
  trigger focus or eye effects run in their own batches.
- Text is in English (`en_us`) and Spanish (`es_es`, `es_mx`).

## Open questions for the owner

- How do combat (M2) and powers (M3) feel? Balance: chakra costs, Sharingan drain, awakening
  chance, cloak strength, ninja aggression.
- How should players *become* jinchūriki for real (a sealing event, a boss, a village ritual)?
- Should Hyūga get the Byakugan next, or other clans' kekkei genkai (e.g. generated clans with
  rare bloodlines)?
