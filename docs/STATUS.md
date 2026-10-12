# Project status

_Last updated: 2026-10-12 · branch `claude/keen-cray-uc2d11` · PR #1 (draft)_

## Where we are

**Milestone 2: Combat feel v1. ✅ Built, 20/20 game tests green, and checked in the real client**
(off-screen smoke test, see `docs/screenshots/m2-*.png`). What's left is the owner's hands-on
feel test, and tuning based on it.

| Area | State |
|---|---|
| M0 Foundation, M1 chakra/stamina + HUD | ✅ (HUD bars seen in the client) |
| Combat mode toggle, light/heavy/launcher, combo routes (data-driven JSON) | ✅ tested + seen in client |
| Input buffer (10 ticks, paused in hit-stop), cancel windows, hit-stop, knockback, lunge | ✅ tested |
| Guard + posture + parry + posture break; dodge i-frames, perfect dodge; focus | ✅ tested; guard/posture seen in client |
| Perception (proportional wind-up slowdown, cap 2.5×); speed/taijutsu stats | ✅ tested |
| Sparring ninja (AI, telegraph poses, glint on heavies) | ✅ tested + seen in client |
| Player animations (PlayerAnimator, MIT Boundless set) | ✅ seen playing in client (kick) |
| Lock-on, camera shake, focus FOV pulse | ⚠️ compiles and runs; not visually confirmed |

## In progress

Nothing. Waiting for the owner to play milestone 2 and report how it feels.

## Next steps (in order)

1. **Owner feel test of M2.** See "How to test combat" in `docs/GETTING_STARTED.md`. Collect
   feedback on speed, damage, knockback, how aggressive the ninja is, and the controls.
2. **Tune** from that feedback. Most numbers are in `data/noirm/combat/moves/basic/*.json`
   (editable without code) and the constants at the top of `combat/CombatSystem.java`.
3. **Milestone 3: Training and awakening.** Training dummy and post; taijutsu XP and speed
   growth; the near-death awakening with the Resolve meter, identity-based (Uchiha, Guy's
   students, jinchūriki). See `COMBAT_DESIGN.md` §2.
4. Milestone 4: Might Guy as the first master, teaching a new style (a new
   `combat/styles/*.json` plus moves) in stages.

## Code map

| Package / path | What lives there |
|---|---|
| `com.noirm` | `Noirm`: entry point; `Noirm.id()` / `Noirm.vanillaId()` helpers |
| `com.noirm.player` | `ShinobiData` (chakra, stamina, speed, taijutsu), its capability and events |
| `com.noirm.combat` | `CombatSystem` (all combat rules and tuning constants), `CombatState` (per fighter), `Perception`, `CombatData` (loads moves/styles JSON), `CombatEffects` (sounds/particles/feedback), `CombatEvents` |
| `data/noirm/combat/` | `styles/basic.json` (combo openers), `moves/basic/*.json` (each move's timing, hit and routes) |
| `com.noirm.network` | `NoirmNetwork` (channel), `CombatInputPackets` (client → server), `CombatClientPackets` (server → client), `SyncShinobiDataPacket` |
| `com.noirm.client` | `CombatKeys`, `CombatInput` (mouse → attacks), `LockOn`, `CombatCamera`, `PlayerAnimations`, `ClientCombat` (client state), `ShinobiHudOverlay`, `SparringNinjaRenderer` |
| `com.noirm.entity` | `SparringNinja`, `NinjaFightGoal` (its AI) |
| `com.noirm.registry` | `NoirmEntities`, `NoirmItems` |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests`, `CombatGameTests` |
| `assets/noirm/player_animation/` | MIT Boundless animations (`credits.md`, `license_boundless.txt`) |
| `tools/client-smoke/` | Run the real client off-screen and drive it (see its README) |

## Decisions made

- **Forge 1.20.1** (owner's choice), with the official Mojang mappings. Single-player first.
- **Pillars:** combat feel, then training and masters, then villages and world gen, then
  exploration. Missions are secondary. **Squads** (recruited allies) come later.
- **No copying NYDO assets.** We use them for ideas and data formats only. The MIT Boundless
  animations and the CC-BY katana are the exceptions.
- **Art:** placeholders and vanilla textures until the mechanics are fun.
- **Combat is switchable** (R). With it off, vanilla controls apply.
- **Launcher = crouch + heavy** (not forward + heavy, which would trigger whenever you chase).
- **Masters teach in stages;** Might Guy is the first taijutsu master.
- **Perception (agreed):** proportional to the strength ratio (20% stronger → 20% slower
  wind-ups, capped at 2.5×), derived from speed + taijutsu, boosted by dōjutsu later. It applies
  both ways (a stronger NPC sees *your* wind-ups slower and reacts more often).
- **Awakening (agreed):** a Resolve meter rather than pure luck; depends on identity. Planned for M3.
- **Focus moments** slow nearby fighters' actions and movement (not the global game clock), so
  they also work in multiplayer.
- **NOIRM hits cancel vanilla knockback** and apply only the move's own, so combos connect.
- **The server is authoritative** for all gameplay state; the client sends button presses only.
- Game tests live in `com.noirm.gametest` and use `template = "empty"`. Tests that trigger focus
  run in their own batch (focus slows everything within 10 blocks, including other tests).
- Text is in English (`en_us`) and Spanish (`es_es`, `es_mx`).

## Open questions for the owner

- How does M2 feel? (speed, damage, ninja aggression, knockback, controls)
