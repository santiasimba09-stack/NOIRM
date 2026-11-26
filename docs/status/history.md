# Status history

_Release-by-release state tables moved out of `STATUS.md` (2026-11-26) so the dashboard stays short. Newest last. The full story is in [`CHANGELOG.md`](CHANGELOG.md) and [`changelog/archive.md`](changelog/archive.md)._

## APX 0.1 and before (was "Where we are")

**Progress map:** https://claude.ai/artifact/2vipQQ4goqPot6eTa3xMQp (page source `docs/progress-map/index.html`;
its data lives in the artifact's database). It shows every system by status, the road ahead, the release trail
with test counts, and the owner's questions and notes. Keep it up to date (CLAUDE.md, "Keeping context fresh").


**NOIRM APX 0.1 (v0.14.0) is out: the game is playable from a fresh character to a legend. 365/365
game tests are green twice in a row; every piece was checked in the real client.** The owner's
decisions are in `docs/APX.md`; the history is in `CHANGELOG.md`.

| APX 0.1 | State |
|---|---|
| **Stats:** gentler speed curve (cap at 50, perception past 35), Durability (up to 60% negation), speed trained in combat, kill XP to the stats you used | ✅ tested |
| **Natures:** chakra paper (hidden natures), drills, crew and master teachers, nature and jutsu scrolls | ✅ tested; checked in client |
| **Sage path:** Iwao the Stone Hermit, sanctuary, petrification, Sage Mode | ✅ tested; checked in client |
| **Ryo, shops, missions board (17 missions D–S), rank Academy → Kage candidate with exams** | ✅ tested; checked in client |
| **Classes D–S and the bingo book:** bounties, legendary ninjas with auras, entrances and boss bars | ✅ tested; checked in client |
| **Masters:** Might Guy, Rensai the Scholar, Tsubaki the medic; the Shinobi Handbook | ✅ tested; checked in client |
| **Follow-ups:** fewer wild ninjas, squads defend you, long held weapons, four new chakra flows | ✅ tested; checked in client |
| **UI redesign:** one UI kit (`client/ui`, rules in `docs/UI.md`), quiet HUD, every screen flat and animated in the same palette | ✅ checked in client at GUI scale auto and 2 (`ui-before-*` / `ui-after-*`) |

Before APX: v0.13.0 (personal jutsu sets, wild ninjas, blades, armor), v0.12 (our own HD art, gore v2,
fire and lightning), v0.11 (character creation, movement feel), v0.10 (crew overhaul, blueprint
Phase 1), round 6 and earlier: see `CHANGELOG.md`.


## The chakra wave (was "In progress" until v0.16.0)

**The chakra wave** (owner's plan in `docs/CHAKRA_WAVE.md`; three parts in the owner's order):

| Part | State |
|---|---|
| **1. The jutsu wave (v0.15.0):** 71 new jutsu: epic Wood Release (6), Ice/Lava/Boil/Storm/Explosion (20), Scorch/Dust/Magnet/Wind/Earth (23), Yin/Yang/Yin-Yang/Medical (22) | ✅ 436 game tests green twice; each share checked in client (`cw-*` screenshots) |
| **2. Solo transformations + the jutsu workshop (v0.16.0):** nature fusion trial, 8 chakra shapes and shape + nature fusion, Yin/Yang/Yin-Yang alone, a Training tab, the Workshop tab (your own jutsu: cost, backlash, stat budget, misfires), teaching crew, writing scrolls | ✅ tested; checked in client (`cw2-*`) |
| **3. Solid-flame chakra:** solid shell + rising flame edge for beast cloaks, tails, every Susanoo stage (semi-opaque bone), Sage, the Gates and fitting jutsu; per-user Susanoo colours; Low/Medium/High setting | after part 2 |


## Survival plan (`docs/SURVIVAL.md`)

| Step | State |
|---|---|
| **Path of the Shinobi:** 49 advancements in 9 branches, each saying how to do it; 14 triggers in `progress/` | ✅ v0.16.0, tested; checked in client (`sv-advancements-*`) |
| **Config file** `config/noirm-common.toml` (`config/NoirmConfig`): difficulty, wild ninjas, legends, terrain, gore, progression speeds; `/noirm config` | ✅ v0.16.0, tested |
| **Sound:** 54 synthesised sounds (152 variants) for seals, chakra, hits, weapons, every nature, big moments, UI, ambience (`docs/SOUND.md`, `tools/soundgen/`) | ✅ v0.16.0, tested; subtitles checked in client (`sv-sound-*`) |
| Full survival playthrough + balance pass (`docs/PLAYTHROUGH.md`, `tools/balance/simulate.py`): 14 fixes (free-mission bug, shop money loop, exam walls, wild danger, S jutsu too early, village hint) | ✅ v0.17.0, tested; start played in client (`pt-*`) |
| Survival staples, exploration, new paths, endgame | planned |


## v0.17.0 (2026-11-12)

| Piece | State |
|---|---|
| **Combat animation rebuild** (`COMBAT_DESIGN.md`, last sections), built on the round-5 engine: snap key poses, automatic sub-stances per style, 5-hit strings with enders, dodge into counter, dash strike, guard breaks, faster pace, NPCs use them | ✅ tested; a string and the stance label checked in client (`ca-string-third-person.jpg`). Side-view frame strips per style not captured yet |
| **Hair, eyes, bodies** (`ART_STYLE.md`, last sections): 23 3D hair styles as data, 40 colours, clan hair, headgear rules; 5 eye shapes, redrawn dōjutsu; build and frame (masculine/feminine) in the creator | ✅ tested; checked in client (`hb-after-*`) |
| **Survival playthrough + balance pass** | ✅ (see the survival table above) |

**Known flaky tests** (each failed once in five full runs, not root-caused): `woodlockingwallclosesthensplinters`,
`outpostisbuilt`, `vacuumwavecutsallaround`, `despawnrules` (all four in the first run on a freshly
generated test world) and `groupmatesjointhefight`.


## v0.18.0 = NOIRM APX 0.1 (2026-11-13; named the baseline by the owner on 2026-11-14)

The owner: "name it noirm apx 0.1 now, and we shall continue from this point forward". The jar is
`noirm-apx-0.1.jar` (the same build as `noirm-0.18.0.jar`). Everything after this builds on it.

| Piece | State |
|---|---|
| **Pills and foods** (`PILLS.md`): 8 pills with toxicity and crashes, 5 herbs, apothecary, pharmacology skill, 12 foods, NPCs use pills | ✅ tested; checked in client (`pills-*`) |
| **Chakra reinforcement** (`COMBAT_DESIGN.md`): armour mostly ignored by ninja attacks, reinforcement from Reserves + CTL, chakra durability on armour, fire capped for beginners | ✅ tested; checked in client (`cr-*`) |
| **Leviathan Maelstrom** (Water S-rank) | ✅ tested; checked in client (`cr-water-*`) |
| **Eyes, fourth pass** | ✅ checked in client (`eyes-v2-*`) |


## v0.19.0 (2026-11-19)

The owner's wave (`docs/WAVE_STYLES_CLANS.md`), built by parallel helpers.

| Piece | State |
|---|---|
| **14 new fighting styles** (`COMBAT_DESIGN.md`, "Fighting styles"): own stance, guard, dodge, mechanics; learned from masters, scrolls, watching; **I** opens the picker | ✅ tested; checked in client (`st-*`) |
| **26 canon clans** (`CLANS.md`): perks, 12 clan jutsu, clan styles; **much bigger gaps with drawbacks** (Uzumaki ×10 chakra, ×20 as jinchūriki; Might line ×4 taijutsu training) | ✅ tested; simulator: every clan reaches every rank; checked in client (`cl-*`, `cl-gaps-*`) |
| **29 personal talents** (`TRAITS.md`): 2–4 per character, clan-biased, at most one weakness | ✅ tested; checked in client |
| **Genjutsu scenes** (`GENJUTSU.md`): the victim sees a scene; breaking out shatters it | ✅ tested; checked in client (`gr-*`) |
| **Body Flicker, rebuilt** (`GENJUTSU.md`): instant, blur, afterimage, no wall clipping, flicker-strike | ✅ tested; checked in client |
| **The Flying Raijin** (`FLYING_RAIJIN.md`): marked kunai, marks by throw/touch/surface (**Y** mark, **X** jump, **U** cycle), Thunder God Slash, allies, sending jutsu away | ✅ tested; checked in client |
| **The Inuzuka hound** (`INUZUKA.md`): a puppy that grows level by level (no fixed stages), 12 traits, 5 breeds × patterns × 16 colours, Fang Over Fang together, smell tracking, Man Beast Clone, Two-Headed Wolf, riding; knocked out, never dead; panel on **O** | ✅ tested (13 tests); checked in client (`hound-*`) |

**Flaky tests** (fail now and then while the machine is busy; each passed on the next run):
`dodgeIntoCounterLandsBehind`, `wavesRollOverAndCarry`, `ninjasCastFromTheirOwnSet`, `catchingAMobChangesWhatItDoes`.


## Next steps as of v0.19.0

1. **Owner tries v0.19.0** and answers the new questions on the progress map (Uzumaki size, fighter clans,
   loud chakra, Might Guy, the Flying Raijin, Kotoamatsukami, hounds).
2. **Two waves the owner asked for after v0.19.0** (they can run in parallel):
   - **Solid, full-scale jutsu** (`docs/WAVE_JUTSU_VISUALS.md`): every jutsu built from blocks, bigger and
     to scale; a real Dust cube and a Primitive World cylinder; a bigger statue; a much bigger Deep Forest
     Emergence with leaves and permanent, usable wood.
   - **A richer creator and a ninja-style UI** (`docs/WAVE_CREATOR_UI.md`): many more hair styles, eyes and
     body types; every screen redone with a ninja feel.
3. Rest of the survival staples (smoke bombs, wire, traps), exploration, new paths, endgame (`docs/SURVIVAL.md`).
