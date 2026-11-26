# Project status

_Last updated: 2026-11-26 · branch `claude/keen-cray-uc2d11` · protocol 38 · a short dashboard: history lives in
[`status/history.md`](status/history.md) and [`CHANGELOG.md`](CHANGELOG.md)._

**Progress map:** https://claude.ai/artifact/2vipQQ4goqPot6eTa3xMQp (page source `docs/progress-map/index.html`; its data
lives in the artifact's database). It shows every system by status, the road ahead, the release trail with test counts,
and the owner's questions and notes. Keep it up to date (CLAUDE.md, "Keeping context fresh").

## Where we are

**NOIRM v0.3 (= v0.20.0, 2026-11-25)** is out: the richer creator and the Shoji UI, 52 clan wearables, clan boosts
(Uzumaki pool ~100×, canon clans ~80% of rolls), and the Aburame (merged as a snapshot; the remaining phases are v0.3.1).
Before it: NOIRM v0.2 (= v0.19.0: styles, 26 canon clans, talents, genjutsu scenes, Body Flicker, Flying Raijin, the
Inuzuka hound) on top of NOIRM APX 0.1 (= v0.18.0, the baseline). Details: [`CHANGELOG.md`](CHANGELOG.md).

## In progress

- **The tooling wave** (2026-11-26): project skills (`.claude/skills/`), the Blockbench model loader and offline
  preview (`tools/models/`), the quick test runner, log summariser and lang tool (`tools/dev/`), slimmer docs
  ([`INDEX.md`](INDEX.md)), the tuning guide ([`TUNING.md`](TUNING.md)).

## Next steps (in order)

1. **The owner's queued requests** ([`WAVE_NEXT.md`](WAVE_NEXT.md)): shadow clones, deeper training, crew Sharingan
   and fused natures, the Eight Gates without self-damage, tailed-beast spawning, an overpowered Gentle Fist, a better
   Byakugan.
2. **On hold:** the remaining Aburame phases (v0.3.1, [`ABURAME.md`](ABURAME.md)), the complex clan work
   ([`clans/README.md`](clans/README.md)), the solid-jutsu wave ([`WAVE_JUTSU_VISUALS.md`](WAVE_JUTSU_VISUALS.md)).
3. Later: the rest of the survival staples, exploration, new paths, endgame ([`SURVIVAL.md`](SURVIVAL.md)).

## Known flaky tests

Fail now and then while the machine is busy; each passed on the next run (none root-caused):
`woodlockingwallclosesthensplinters`, `outpostisbuilt`, `vacuumwavecutsallaround`, `despawnrules` (mostly on a freshly
generated test world), `groupmatesjointhefight`, `dodgeIntoCounterLandsBehind`, `wavesRollOverAndCarry`,
`ninjasCastFromTheirOwnSet`, `catchingAMobChangesWhatItDoes`.

## Where the rest went

| Was in STATUS | Now in |
|---|---|
| Release-by-release state tables (APX 0.1, chakra wave, survival plan, v0.17–v0.19) | [`status/history.md`](status/history.md) |
| Code map | [`CODEMAP.md`](CODEMAP.md) (or `python3 tools/dev/codemap.py` for a generated one) |
| Decisions made | [`DECISIONS.md`](DECISIONS.md) |
| Open questions for the owner | the progress map's `questions` collection; the per-release archive is [`QUESTIONS.md`](QUESTIONS.md) |
