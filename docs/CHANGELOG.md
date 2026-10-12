# Changelog

Newest first. One entry per working session or PR.

## 2026-10-12 — Milestone 1: Shinobi core (branch `claude/keen-cray-uc2d11`, PR #1)

- Chakra and stamina (`ShinobiData`) attached to every player through a Forge capability. They're
  saved in the player file, carried over on respawn (refilled after a death) and on dimension
  change.
- Server-side regen: chakra at 1 per second; stamina at 20 per second after a 1-second pause
  once spent.
- `SyncShinobiDataPacket` sends the values to the client when they change. HUD bars sit in the
  top-left corner.
- `/noirm stats | set | max | refill` debug command.
- Text in English and Spanish (`assets/noirm/lang/`).
- 7 new game tests (8 total, all passing). Added the `Noirm.id()` helper.
- Recorded the owner's decisions: combat is switchable, and Might Guy is the first taijutsu master.

## 2026-10-12 — Foundation (branch `claude/keen-cray-uc2d11`, PR #1)

- Reorganized the uploaded NydoMod 5.5 assets into `reference/nydo/` by content type (1,209
  files moved, 37 exact duplicates removed). Wrote `reference/nydo/README.md` covering the
  inventory, the reconstructed game systems and licensing.
- Created the NOIRM Forge 1.20.1 project from the official MDK (Forge 47.4.10): mod id
  `noirm`, main class `com.noirm.Noirm`.
- Added the first game test (`com.noirm.gametest.NoirmGameTests.modIsLoaded`) and the shared
  empty test arena `data/noirm/structures/empty.nbt`. `runGameTestServer` passes.
- Added the docs: `ROADMAP.md`, `GETTING_STARTED.md`, `STATUS.md`, this changelog, and
  `CLAUDE.md` for session context.
