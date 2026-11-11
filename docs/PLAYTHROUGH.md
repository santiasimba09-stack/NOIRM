# Survival playthrough and balance pass (2026-11-11)

The "full survival playthrough + balance pass" from `docs/SURVIVAL.md`, step 1. Three parts:

1. **The real start, in the client:** a fresh survival character from the creation screen to the
   first fight with a wild ninja (screenshots `docs/screenshots/pt-*.jpg`).
2. **The long arc, simulated** from the code's real numbers: `tools/balance/simulate.py` (it reads
   the Java constants and the mission/encounter JSON, so it follows any retune). Run it with
   `python3 tools/balance/simulate.py`; `--root <old checkout>` runs it on older numbers.
3. **Survival integration:** vanilla armour, mobs, crew loss, death.

Then the fixes (all small, all tested), and the questions for the owner.

---

## 1. Play log (real client, survival)

| # | Step | What happened | As a new player |
|---|---|---|---|
| 1 | Creation screen (`pt-01-creator`, `pt-02-genetics`) | Face, Body, Hair, Genetics tabs; rolled the Kuki clan (Earth + Water), "Begin". | Clear. The "+2" next to "Kept" on the roll isn't explained. (Creator art is another helper's area.) |
| 2 | First chat (`pt-03-first-chat`) | Traits, controls line, "your path begins", handbook given, tip 1/3 — five messages at once. | A wall of text in the first second; easy to miss the tip. |
| 3 | Handbook (`pt-04-handbook`) | Opened with right-click; "Read the Handbook" toast. | Good: chapters, icons, readable. |
| 4 | Advancement tab (`pt-05-advancements`) | One "Path of the Shinobi" tab; descriptions say what to do. | Good. Two descriptions were wrong (below) and are fixed. |
| 5 | Tips 2 and 3 (`pt-06-tips-village-hint`) | Tip 2 said "craft a dummy (hay bales)"; tip 3 said "find a village" with no hint where. | **Dead end for a survival player** (no `/locate`): fixed, the last tip now names the nearest settlement ("about 200 blocks to the south" — it was 224 blocks south). |
| 6 | The outpost (`pt-07-outpost-above`) | 224 blocks away; palisade, campfire, houses, a training dummy and a target. | Nice. A horse wandered in and got punched in combat mode. |
| 7 | Death by fall (`pt-08-death`) | My own mistake (switched to survival in the air). Respawned at the world spawn: rank, stats, clan, ryo all kept; the handbook dropped where I died (vanilla). | Death is vanilla, as decided. The board hands out a new handbook. |
| 8 | First training (`pt-09-first-training`) | The outpost's dummy: "Hit the Dummy" advancement, HUD shows the stats. | Good: you don't need hay bales after all — the tip now says village dummies exist. |
| 9 | First jutsu (`pt-10-first-jutsu`) | Water Bullet with key 2, "First Jutsu" toast. | Good. |
| 10 | Missions board (`pt-11-missions-board`) | Found it only from above: it looked like a plank block from every side. | **Hard to find:** the board's notice face pointed along the path. Fixed: the notices show on every side. |
| 11 | First mission (`pt-12-mission-hud`, `pt-13-bug-free-mission`) | "Chase off a bandit", marker 116 m. I flew there quickly: **"Objective complete!" with no bandit.** | **Bug:** the bandit was put into a chunk that hadn't loaded yet; "can't be found" counted as "fled". Fixed + game test. |
| 12 | Report (`pt-14-first-mission-done`) | +120 ryo, +10 points, "Reporting for Duty". | Good. |
| 13 | Shop (`pt-15-shop`, `pt-16-first-purchase`) | The outpost merchant: kunai, ramen, a dummy (160), a sealing scroll (genin), a flak vest (chūnin), the bingo book. Bought a dummy. | Clear: rank-locked rows are grey with the rank in the tooltip. |
| 14 | First wild rogue (`pt-17-first-rogue-death`) | A C-class rogue (strength 18, what a lone rogue is at 300 blocks): dead in ~10 s, untrained, mashing buttons. | **Too hard for minute 10.** Fixed: lone rogues wait 30 minutes (bands 45) and are a bit weaker (0.85×). |
| 15 | Trained rematch (`pt-19-trained-rematch-death`) | Taijutsu 20, speed 16 (half an hour of dummy work): dead in ~6 s; "LOCKED IN" fired. | Still very hard: NPC jutsu do most of the damage. |
| 16 | Same in diamond armour (`pt-20-diamond-armour-burned`) | Dead in ~4 s, on fire, the grass burning around. | Burning ignores armour: an early fire user kills a beginner even in diamond. **Question 2.** |
| 17 | Jutsu screen (`pt-18-jutsu-screen`) | Own jutsu at the top; then many locked jutsu of other paths ("Master Chakra…", truncated). | A beginner sees a long list of things they can't get. (UI area; noted.) |

Also seen: the rogue's name was "Sparring Ninja (Clan)" in the boss bar and the death message —
odd for a bandit who kills you. Now just "Ninja (Clan)".

Not checked in the client this time (time): the genin exam, the Stone Hermit, a crew. The
simulation and the code review cover them below.

---

## 2. The long arc (simulated)

A **normal player** (written in `ASSUMPTIONS` at the top of the script): 60% of play time on
NOIRM, beats NPCs up to 15% stronger than them, takes ~70% of a board's offers, half the day on
missions, finds a settlement in ~15 minutes, meditates at night on a drill. One board, one clan
nature, no eyes or beasts. Times are hours of play (an in-game day is 20 minutes).

| Milestone | Before (h) | After (h) | Note |
|---|---:|---:|---|
| First village, dummy, jutsu | 0.4 | 0.4 | |
| First mission | 0.6 | 0.6 | |
| Genin | 1.6 | 1.6 | |
| First nature drill done = second nature | 2.6 | 2.6 | 1.5 days if hidden, halved by Rensai |
| Combined nature (kekkei genkai) | 5.8 | 6.5 | |
| A stat at 30 (drill cap) | 5.5 | 6.7 | |
| **Chūnin** | **14.4** | **10.0** | points ready at 2.9 h both times: the exam was an 11.5 h wall |
| Beat class B | 15.2 | 13.9 | |
| **Jōnin** | **31.6** | **21.0** | points ready at 14.5 h / 10.1 h: a 17 h wall, now 11 h |
| ANBU | 32.8 | 23.0 | |
| A-rank jutsu (gates met) | 32.0 | 21.4 | a lightning clan starts with Chidori (A) |
| **S-rank jutsu, fire clan by mastery chain** | **4.5** (≈0.5 h focused) | **29.8** | now waits for Reserves 40 |
| Sage Mode | 32.6 | 27.0 | when the player first roams 1,500 blocks |
| Beat class A | 32.2 | 26.2 | |
| Kage candidate | 38.3 | 41.0 | the S defence now needs a strong A, not a mid A |
| Beat class S | 55.6 | 52.4 | |
| Beat a legend | not in 60 h | not in 60 h | needs ~200 stat power, or extras (eyes, beast, sage, crew) |

**Wild danger vs you** (the real wall behind everything):

| Hour | You (power) | Rogue near home, before | after |
|---:|---|---:|---:|
| 0 | 10 (D) | 19 (C) | 16 (C) |
| 3 | 18 (C) | 32 (B) | 22 (C) |
| 6 | 22 (C) | 44 (B) | 27 (C) |
| 7+ | 22–25 (C) | 45 (A) | 29–34 (B) |

Before, play time added +4 danger an hour up to +25: after ~6 hours every rogue around your home
was A-class while you were still C. Now +2 an hour up to +20, and the lone rogue is 0.85×.

### Walls, skips, dead ends
- **Walls:** the chūnin exam (points at 2.9 h, passable at 14.4 h) and the jōnin trial (points at
  14.5 h, passable at 31.6 h); wild danger outrunning the player from hour 3. Shortened (exams
  retuned, time danger slowed). What's left: hours 13–21 (chūnin → jōnin) have few new things;
  that's where survival staples and exploration (SURVIVAL.md steps 3–4) should go.
- **Skips:** a fire-clan Academy student held an S-rank jutsu after 60 casts of the fireball (half
  an hour of practice); lightning clans start with Chidori (A). Chains now wait for Reserves. Ryo
  stops mattering after ~10 h (10,000 ryo at 10.6 h, 400,000+ by 60 h): nothing big to buy yet.
- **Dead ends:** finding a village without `/locate` (fixed: the tip points to it). Legends are not
  a dead end but need ~100 h without eyes/beasts/crew. Water has no S-rank jutsu at all (a
  water-only player needs a second nature for "Forbidden Knowledge").

### Every advancement, reachable in survival?
All 49 are. Notes: *Beasts* need a jinchūriki ninja (5% of ninjas with strength ≥ 20) or a legend;
*Might Guy* only lives in Leaf villages with a training ground (rare: capitals are 1,500+ blocks
apart); *Sage Mode* needs the sanctuary (1,000+ blocks, `/noirm sage rumour` works without cheats);
*eyes* are clan-only (hidden). Fixed texts: "Hit the Dummy" (village dummies), "A-Rank Arsenal"
(chains need Reserves 25), "Forbidden Knowledge" (said legends carry S scrolls: they don't; S
scrolls are rare chest loot), "Chūnin" (said "C- and B-rank missions": a genin can't take B),
"Jōnin" (now says what the trial is).

---

## 3. Survival integration

- **Vanilla armour.** NOIRM hits use normal attack damage, so armour applies in full to melee:
  diamond (20 armour, 8 toughness) cuts a strength-16 heavy (≈11) by ~70%, ~90% with Protection
  IV. So diamond *does* trivialise ninja **melee** — but not their **jutsu**: fire sets you
  burning (armour doesn't help) and the grass around you on fire, and I died in 4 s in full diamond
  at the start. Net: melee too weak against armour, fire too strong against beginners. **Question 2.**
- **Early ninja combat** doesn't one-shot by melee (a strength-12 rogue's heavy ≈ 8 of your 20 hp),
  but a charged Great Fireball (up to 20 + fire) nearly does. Legends (180–240) do one-shot, as the
  APX answer accepted.
- **Mobs and nights.** At taijutsu 30 a jab does ~9: zombies and skeletons fall in 2–3 hits and
  arrows (2–5) barely matter by mid game. Creepers still matter (≈22 at point blank vs 30–40 hp,
  DUR cuts 20–33%). Nights matter mainly through rogues (night weight 22 vs 12 by day).
- **Crew loss.** A crew mate leaves only when respect drops below 5, and the slow drift toward
  "what your strength commands" never goes below 5, so they only leave after your own actions
  (hitting them −3, letting a mate die −5 to the others). Deaths are permanent. Fair; it would be
  kind to warn at respect 15 (not done).
- **Death.** Vanilla only, as decided: all NOIRM data (stats, rank, ryo wallet, jutsu, natures,
  crew list, guide) is copied to the respawned player; Sage Mode and the stone end. Items drop as
  in vanilla (the handbook too: any board gives another). A running exam fails. Nothing wrong found.
- **Rank gifts are craftable** (ANBU mask, Kage hats have recipes): cosmetic, but a Kage hat at
  minute 5 is a little odd. Not changed.

---

## 4. Issues and what changed

| # | Issue | Change | Test |
|---|---|---|---|
| 1 | **Free mission:** a hunt (or the retrieve camp) reached before its chunks loaded spawned no one, and "gone" counted as fled → done with no fight | Spawn only into entity-ticking ground; an empty group retries; a missing foe counts as fled only where the world is loaded; if the marked spot is water, look wider for dry land (`mission/Missions`) | `huntNeverCompletesWhileItsPlaceIsUnloaded` |
| 2 | **Money loop:** senbon bought 16 for 40 ryo sold back at 5 each; crafted senbon sold for 15 each at a general store (an iron ingot ≈ 540 ryo) | Resale ≤ ¼ of the shop's own price (min 1, not 5); unstocked stackables 1 ryo each; unstocked gear 4 ryo per armour point up to 20 (a wheat straw hat 4, a flak vest 20) (`economy/Shop`) | `shopsNeverBuyBackForMoreThanTheySell` |
| 3 | **S-rank jutsu in half an hour:** mastery chains ignored the Reserves a rank needs | From casting, a chain jutsu of rank B/A/S waits for Reserves 15/25/40 (the scroll rule), says so, and comes by itself when the Reserves are there (`clan/NatureSystem`, `jutsu/JutsuCaster`, `training/TrainingSystem`). Commands still teach ungated. | `masteryChainsWaitForTheReservesARankNeeds` |
| 4 | Exam opponents could roll a tailed beast (5% from strength 20: the chūnin rival and jōnin examiner) | Exam opponents are never jinchūriki (`mission/Exams`) | asserts in the chūnin and jōnin exam tests |
| 5 | **Exam walls** (11.5 h and 17 h with the points in hand) | Chūnin rounds 22/30 → 20/26; jōnin examiner 48 → 40; trial rogue 20 → 17 (×2.2 = 37). A jōnin is now a strong B facing A-rank work | existing exam tests |
| 6 | Wild danger outran the player after ~3 h | Time danger +4/h up to +25 → +2/h up to +20 (`world/wild/WildSpawner`) | `WildGameTests` updated |
| 7 | First rogue (C, minute 10) killed an untrained player in seconds | Lone rogue at 30 min (was 10) and 0.85× strength; rogue bands at 45 min (was 20) | assert in `WildGameTests` |
| 8 | Kage candidate came at mid-A (the S defence mission's foes were 55 → A) | S "Village under attack" strength 55 → 62 (leader ≈ 72, a strong A); the S hunt stays S | sim |
| 9 | No way to find a village in survival | The last tip names the nearest settlement's distance and direction (`guide/FirstSteps`) | `boardTipPointsTowardASettlement`; seen in client |
| 10 | Tip 2 sent new players to farm 18 wheat for hay bales | Says village training grounds have dummies | lang |
| 11 | The missions board looked like a plank block | Notices on every side of the block (model) | client |
| 12 | Rogues named "Sparring Ninja" | Ninjas are "Ninja (Clan)" | — |
| 13 | Wrong advancement texts (Chūnin missions, S scrolls from legends, chains, the dummy) | Rewritten in `tools/advancements/gen_advancements.py` (all three languages) | the generator; lang tests |
| 14 | Handbook "Jutsu and scrolls" didn't mention the chain Reserves gate | Updated (three languages) | lang test |

`NinjaClass`'s doc comment now matches the new exam and mission strengths. No packets changed
(**protocol stays 24**).

### Not changed (and why)
- NPC jutsu damage and burning vs armour — combat/jutsu balance is the combat helper's and a
  design call (question 2).
- The rank gate on chain jutsu (scrolls need chūnin/jōnin/ANBU; chains don't) — a design change
  (question 3).
- Vanilla armour vs NOIRM melee — design (question 2).
- The creator's "+2", the jutsu screen listing locked other-path jutsu — other helpers' screens.
- First-join chat wall — small, but the creator/clan messages belong to the creator area.

---

## 5. Questions for the owner (most important first)

1. **The middle of the game (chūnin → jōnin, ~hours 10–21) has little that's new.** Should the
   next work (SURVIVAL.md step 3–4: pills, the blacksmith, ruins and dungeons) aim at that stretch?
2. **Armour and fire.** Vanilla diamond cuts ninja melee by ~70–90%, but fire jutsu burn through it
   (I died in 4 s in full diamond to a C-class rogue at the start). Options: chakra strikes ignore
   part of vanilla armour (say a third), and NPC fire jutsu don't set grass alight near beginners /
   burn shorter. Which way?
3. **Should mastery chains also need the rank** (B chūnin, A jōnin, S ANBU), like scrolls? Today
   they only need the Reserves (my fix). And should lightning clans keep Chidori (A) as a starter?
4. **Exams:** chūnin final 26 and jōnin examiner 40 (were 30 and 48). Right feel?
5. **Rogues:** first lone rogue at 30 minutes, 0.85× strength; time danger +2/h up to +20. OK?
6. **Ryo has no sink after ~10 h** (400,000 by hour 60). Big purchases (homes, crew wages, the
   blacksmith) planned in SURVIVAL.md would fix it: priority?
7. **Water has no S-rank jutsu.** Add one (our own design), or fine?
8. **Legends** need ~100 hours for a player without eyes/beast/crew. Is that the intended endgame?
9. Small ones: warn when a crew mate's respect drops below 15? Should rank gifts (ANBU mask, Kage
   hats) stay craftable from the start?
