# Project status

_Last updated: 2026-11-26 · branch `claude/wave-shukaku` · protocol 41 · a short dashboard: history lives in
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

- **Shukaku, the One-Tail** (2026-11-26, branch `claude/wave-shukaku`, draft PR, v0.23.0): the first creature from the
  three creature skills: own model, texture, glowing eyes, moods, idle habits, the belly-drum Air Bullet. Beasts 2–9
  still use the zoglin. Brief: [`entities/shukaku.md`](entities/shukaku.md). Waiting for the owner's playtest and
  answers to its open questions (size, marking colour, taunts, sand moves).

- **The Hyūga wave** (2026-11-26, branch `claude/wave-hyuga`, draft PR): the Hyūga as the Leaf's strongest clan, with
  nothing new built. Big clan perk raise (CTL +12, TAI +5, Gentle Fist ×2.5, parry and guard perks), a stronger
  Byakugan (free to keep open, 64-256 block sight) and Gentle Fist, every Hyūga technique **chakra-free** with no
  Reserves gate, and **mastery up to Lv 10** for them (`max_mastery` in jutsu JSON). New Gentle Fist arts (aimed
  tenketsu, organ bleeding, chakra burst, reopening points, palm parry) and the **Byakugan lag fixed**. Brief: [`WAVE_HYUGA.md`](WAVE_HYUGA.md).
  Waiting for the owner's playtest.
- NOIRM v0.4 (the clones-and-training wave) is merged on main ([`CHANGELOG.md`](CHANGELOG.md)).

## Next steps (in order)

1. **Playtest** Shukaku; if the owner likes the skills' result, the next beasts the same way (Matatabi, Kurama…).
   **Playtest and release** the Hyūga wave; then the owner's next request ([`WAVE_NEXT.md`](WAVE_NEXT.md)).
   Later idea for the Hyūga (needs new content, so not in this wave): main and branch houses, the caged-bird seal.
2. **On hold:** the remaining Aburame phases (v0.3.1, [`ABURAME.md`](ABURAME.md)), the complex clan work
   ([`clans/README.md`](clans/README.md)), the solid-jutsu wave ([`WAVE_JUTSU_VISUALS.md`](WAVE_JUTSU_VISUALS.md)).
3. Later: the rest of the survival staples, exploration, new paths, endgame ([`SURVIVAL.md`](SURVIVAL.md)).

## Known flaky tests

Fail now and then while the machine is busy; each passed on the next run (none root-caused):
`woodlockingwallclosesthensplinters`, `outpostisbuilt`, `vacuumwavecutsallaround`, `despawnrules` (mostly on a freshly
generated test world), `groupmatesjointhefight`, `dodgeIntoCounterLandsBehind`, `wavesRollOverAndCarry`,
`ninjasCastFromTheirOwnSet`, `catchingAMobChangesWhatItDoes`, `aHurtNinjaSwallowsABloodPill`.

## Where the rest went

| Was in STATUS | Now in |
|---|---|
| Release-by-release state tables (APX 0.1, chakra wave, survival plan, v0.17–v0.19) | [`status/history.md`](status/history.md) |
| Code map | [`CODEMAP.md`](CODEMAP.md) (or `python3 tools/dev/codemap.py` for a generated one) |
| Decisions made | [`DECISIONS.md`](DECISIONS.md) |
| Open questions for the owner | the progress map's `questions` collection; the per-release archive is [`QUESTIONS.md`](QUESTIONS.md) |
