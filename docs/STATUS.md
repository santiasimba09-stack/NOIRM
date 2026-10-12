# Project status

_Last updated: 2026-10-12 · branch `claude/keen-cray-uc2d11` · PR #1 (draft)_

## Where we are

**Milestone 0: Foundation. ✅ Done.** `./gradlew build` succeeds, and `./gradlew runGameTestServer`
reports "All 1 required tests passed".

| Area | State |
|---|---|
| Project skeleton (Forge 1.20.1 / 47.4.10, `com.noirm`) | ✅ builds, game test green |
| NYDO reference organized + indexed | ✅ `reference/nydo/README.md` |
| Docs (roadmap, getting started, context files) | ✅ |
| Gameplay features | none yet |

## In progress

Nothing. The next session starts milestone 1.

## Next steps (in order)

1. **Milestone 1: Shinobi core.**
   - A player data capability with chakra (current/max), stamina (current/max) and basic
     stats, saved with the player and kept on death.
   - Regeneration ticking on the server.
   - Server → client sync packets (a `network` package with a `SimpleChannel`).
   - HUD overlay with chakra and stamina bars (placeholder art: plain colored bars).
   - A `/noirm` debug command to read and set values, plus game tests for the save, load and
     regen logic.
2. **Milestone 2: Combat feel v1.** Add the PlayerAnimator dependency (maven.kosmx.dev, which is
   reachable). Then build the combat-mode toggle, a 3-hit combo, a heavy attack, a stamina
   dodge, guard and lock-on, plus hit-stop and knockback. Start the animations from the MIT
   Boundless set in `reference/nydo/assets/animations/player/licensed_boundless/`.

## Decisions made

- **Forge 1.20.1** (owner's choice), with the official Mojang mappings. Single-player first.
- **Pillars:** combat feel, then training and masters, then villages and world gen, then
  exploration. Missions are secondary.
- **No copying NYDO assets.** We use them for ideas and data formats only. The MIT Boundless
  animations and the CC-BY katana are the exceptions.
- **Art:** placeholders and vanilla textures until the mechanics are fun, then Blockbench and
  credited free packs.
- Game tests live in the main source set under `com.noirm.gametest`, and only run when the
  game-test system is enabled. Forge 1.20.1 ships no empty template, so tests use our own
  `data/noirm/structures/empty.nbt` (`template = "empty"`).

## Open questions for the owner

- Should combat replace vanilla left-click, or only apply while "combat mode" is toggled on?
  We propose a toggle.
- Is the first master a canon character (e.g. Guy for taijutsu) or an original character?
