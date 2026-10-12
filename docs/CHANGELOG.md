# Changelog

Newest first. One entry per working session or PR.

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
