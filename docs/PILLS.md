# Foods and pills

The owner's brief (2026-11-12): "first focus on the foods; the development of pills is very important".
This is survival step 3 (`SURVIVAL.md`), aimed at the quiet middle of the game (chūnin → jōnin).
Code: `com.noirm.pills`. Data: `data/noirm/pills/*.json`, `data/noirm/foods/*.json`. Art and text:
`tools/pillgen/` (`gen_pills.py` draws every icon and plant, `pill_lang.py` holds the text in all
three languages). Tests: `gametest/PillGameTests`.

## How it works, in short

- **Foods** fill hunger like vanilla food and add a short bonus (faster stamina or chakra, steadier
  posture, training that sticks, a little healing). Eating the same food again refreshes the bonus;
  it never stacks. Foods have no toxicity, cooldown or crash.
- **Pills** work at once (chakra, stamina, health, a cure), then their effects run, then the strong
  ones **crash** (slowness, weakness, slower regeneration, borrowed stat points taken away).
- **Cooldown:** each pill has its own; while it runs, that pill won't go down.
- **Toxicity (tolerance):** every pill adds toxicity (0–100). It wears off by 0.1 a second (6 a
  minute). A pill taken on top of others works at `1 − toxicity × 0.6 / 100` of its strength. After a
  pill: 50+ nausea, 75+ hunger and weakness, 95+ poison (an overdose). Death ends every effect and
  halves toxicity.
- **Grades:** crude, standard, refined, perfect (on the stack; shop and loot pills are standard).

| Grade | Strength | Toxicity | Crash (strength and length) |
|---|---|---|---|
| Crude | ×0.65 | ×1.3 | ×1.25 |
| Standard | ×1.0 | ×1.0 | ×1.0 |
| Refined | ×1.3 | ×0.8 | ×0.8 |
| Perfect | ×1.6 (it glints) | ×0.6 | ×0.6 |

Strength scales the instant refills and the effects: a multiplier `m` becomes `1 + (m − 1) × strength`;
heals, posture and stat shares are multiplied. The config option `medicine.pillStrength` (default 1.0,
`config/noirm-common.toml`) multiplies every pill's strength too; crashes and toxicity are not scaled.

**Stat boosts** add a share of your real stat (at least one point) and take exactly that back when they
end, so training in between is kept. Points borrowed from a pill never count for stat advancements.

## The pills

Numbers are a standard pill on a clean body. "×" is a multiplier on regeneration or perception.

| Pill | Instant | Effects | Crash (after the effects) | Cooldown | Toxicity |
|---|---|---|---|---|---|
| **Stamina pill** | full stamina | stamina ×1.5, 40 s | stamina ×0.8, 20 s | 30 s | 12 |
| **Soldier pill** (hyōrōgan) | 60% chakra, full stamina | stamina ×2, chakra ×1.5, 30 s | slowness I, weakness I, stamina ×0.5, chakra ×0.6, 30 s | 60 s | 35 |
| **Chakra pill** | 40% chakra | chakra ×1.6, 20 s | chakra ×0.7, 20 s | 40 s | 25 |
| **Blood pill** | 4 health | 1 health a second, 15 s | hunger, 15 s | 40 s | 25 |
| **Antidote pill** | cures poison, wither, nausea, hunger | poison ward (poison and wither washed out), 30 s | – | 20 s | 10 |
| **Focus pill** | – | perception ×1.2, CTL +25%, 60 s | mining fatigue, 30 s; perception ×0.9, 30 s | 90 s | 20 |
| **Iron-bone pill** (rare) | – | DUR +50%, VIT +25%, posture +6/s, 45 s | slowness II, 45 s; speed −10%, 45 s | 5 min | 40 |
| **Three-seal pill** (rare, our "three-colour" idea) | full chakra and stamina, 6 health | speed, taijutsu, CTL, DUR, WIL +25%, perception ×1.15, 60 s | speed, taijutsu, CTL, DUR −15%, slowness, weakness, chakra ×0.3, 120 s; hunger 60 s | 10 min | 60 |
| Ruined pill | – | nausea, 5 s (a failed brew) | – | – | – |

## The foods

| Food | Hunger / saturation | Bonus | Made from |
|---|---|---|---|
| Steamed rice | 4 / 0.5 | stamina ×1.1, 30 s | rice in a furnace, smoker or campfire |
| Onigiri | 6 / 0.7 | stamina ×1.2, 60 s | 2 steamed rice + dried kelp → 2 |
| Pickled plums (umeboshi) | 1 / 0.3, quick | posture +1.5/s, 30 s | 3 sweet berries + sugar → 2 |
| Plum onigiri | 7 / 0.8 | posture +3/s and stamina ×1.15, 90 s | onigiri + pickled plums |
| Pork miso ramen (the old ramen) | 10 / 0.9 | chakra ×1.3, 2 min | bowl, 2 wheat, cooked porkchop, egg |
| Fish ramen | 10 / 0.9 | stamina ×1.3, 2 min | bowl, 2 wheat, cooked salmon or cod, dried kelp |
| Spicy beef ramen | 11 / 0.9 | stat XP ×1.15, 3 min | bowl, 2 wheat, cooked beef, carrot |
| Dango | 4 / 0.5, quick | 15% stamina; posture +2/s, 60 s | stick, 2 steamed rice, sugar → 2 |
| Curry rice | 12 / 1.0 | 0.5 health a second for 30 s; chakra ×1.2, 90 s | bowl, steamed rice, baked potato, carrot, cooked chicken |
| Grilled fish skewer | 7 / 0.7 | stamina ×1.2, 60 s; 0.25 health a second, 20 s | raw fish skewer (stick + cod or salmon) cooked |
| Field ration | 5 / 1.0, quick | 25% stamina | cooked meat, bread, dried kelp, paper → 3 |
| Nutrient bar | 4 / 0.8, quick | 15% chakra | 2 wheat, honey bottle, pumpkin seeds → 4 |

For comparison, vanilla posture recovers about 8 a second out of guard.

## Herbs

Five herbs and rice. Each grows wild (ripe) in its biomes and can be farmed: plant its seeds on
farmland (fast) or grass (slow; it also grows wild there). Ripe, it drops the herb and 1–4 seeds (Fortune
helps); bone meal works. Herbs also take root on sand (ashthorn in the badlands).

| Herb | Where it grows | One patch per … chunks | Used in |
|---|---|---|---|
| Moonroot (pale blue root) | taiga, forest, birch and dark forests | 10 | soldier, chakra, focus pills |
| Bloodleaf (red leaf) | jungles, swamps, dark forests | 10 | blood, antidote, three-seal pills |
| Ironmillet (grey grain) | plains, savanna, meadows | 10 | stamina, soldier, iron-bone, three-seal pills |
| Mistcap (pale lilac cap) | meadows, cherry groves, windswept hills, flower forests | 12 | antidote, focus pills |
| Ashthorn (ember thorns, rare) | badlands, savanna plateaus | 24 | iron-bone, three-seal pills |
| Rice | swamps, jungles | 8 | steamed rice, soldier pill |

Worldgen: `data/noirm/worldgen/{configured,placed}_feature/<herb>_patch.json`, the biome lists in
`data/noirm/tags/worldgen/biome/has_herb/`, added by `data/noirm/forge/biome_modifier/`.

## The apothecary and pharmacology

Craft an **Apothecary** (a stick over cobblestone, a bowl and cobblestone, three planks) and right-click
it. The screen lists every recipe: click one to fill the mortar from your bag, then **Grind**. It keeps
nothing: what's left in the mortar comes back to you when you close it.

**Pharmacology** is a new skill (1–100, saved and synced like the stats), trained by brewing. XP to the
next level: `15 + 4 × level`. A brew gives the recipe's XP × (1 + 0.25 per grade above crude); a failure
gives 40%; past 20 points above a recipe's difficulty it teaches less (down to 20% at +60). Tsubaki's
lessons add levels.

| Roll | Rule |
|---|---|
| Success | 70% at the recipe's difficulty, ±2.5% per point above or below, between 25% and 97% |
| Failure | the ingredients are used up; one ruined pill |
| Grade | score = (pharmacology − difficulty) + 0.1 per CTL point above 10 (at most +5) ± 10 (even roll): below −5 crude, below 12 standard, below 30 refined, else perfect |
| Yield | the recipe's, +1 per 20 points above its difficulty (at most +2) |

| Recipe | Ingredients | Yield | Difficulty | Least pharmacology | How you learn it | XP |
|---|---|---|---|---|---|---|
| Stamina pill | 2 ironmillet, sugar | 3 | 1 | 1 | known | 10 |
| Soldier pill | 2 ironmillet, moonroot, rice | 2 | 6 | 1 | known | 14 |
| Chakra pill | 2 moonroot, lapis | 2 | 10 | 5 | known | 18 |
| Blood pill | 2 bloodleaf, iron nugget | 2 | 10 | 5 | known | 18 |
| Antidote pill | bloodleaf, mistcap | 2 | 12 | 8 | Tsubaki's Poison Lore, or a scroll | 20 |
| Focus pill | 2 mistcap, moonroot, glowstone dust | 2 | 18 | 15 | Tsubaki's Pill Lore, or a scroll | 30 |
| Iron-bone pill | 2 ashthorn, 2 ironmillet, 2 bone meal, iron ingot | 1 | 32 | 30 | Tsubaki's High-Grade Pills, or a scroll | 50 |
| Three-seal pill | 2 bloodleaf, 2 ironmillet, 2 ashthorn, 3 gold nuggets | 1 | 45 | 40 | Tsubaki's High-Grade Pills, or a scroll | 80 |

**Tsubaki the medic** (`data/noirm/masters/medic_tsubaki.json`): *Pill Lore* (genin, 200 ryo: +5
pharmacology, the focus pill, an apothecary if you have none, moonroot, bloodleaf and mistcap seeds),
*Pill supply* (a service, genin, 250 ryo each time: 2 soldier and 2 blood pills), *High-Grade Pills*
(jōnin, CTL 25, after Pill Lore, 2,000 ryo: +5 pharmacology, the iron-bone and three-seal pills, ashthorn
seeds).

**Medical scrolls** teach one recipe you don't know (any but the starters); with nothing left, 40 XP.

## Where pills and foods come from

| Source | What |
|---|---|
| General store | stamina pills (2 for 60 ryo), soldier pill (90, genin), blood pill (80, genin), antidote pills (2 for 70, genin), chakra pill (120, chūnin), an apothecary (150), rice (8 for 20) |
| Ramen stand | fish ramen (2 for 50), spicy beef ramen (2 for 70), onigiri (4 for 30), curry rice (2 for 60) |
| Teahouse | dango (4 for 30), plum onigiri (3 for 40), nutrient bars (4 for 40) |
| Weaponsmith | field rations (4 for 40) |
| Florist | moonroot, bloodleaf, ironmillet seeds (4 for 40), mistcap spores (4 for 60, genin), ashthorn seeds (2 for 80, chūnin) |
| Scroll seller | a medical scroll (400, chūnin) |
| Missions | B: 30% a soldier or blood pill; A: 50% a refined one, 10% a medical scroll; S: 2 refined, 25% a scroll, 10% a rare pill |
| Old chests | seeds (30%), ashthorn seeds (8%), a common pill (20%), a medical scroll (7%), a rare pill (1%) (`loot_modifiers/pill_loot.json`) |
| Beaten ninjas | the pills they didn't use; class A: 25% a refined pill, 8% a scroll; class S: 50% a refined or perfect pill, 20% a scroll, 5% a rare pill; legends always a perfect pill and a scroll, 20% a rare pill |

## Ninjas use pills

Ninjas carry a few, by class: C a blood pill 40% of the time; B a blood pill and a soldier pill half the
time; A one of each; S two blood pills and a soldier pill. Masters carry none. In a fight, below 35%
health they swallow a blood pill (4 health, regeneration II for 6 s); below 20% chakra a soldier pill
(60% chakra back, strength I and speed I for 15 s, then slowness and weakness for 10 s). At most one pill
every 20 s. You see a puff in the pill's colour and hear them swallow it (and a chime or a chakra charge).

## On screen

- **HUD** (top left, under the status lines): each food or pill at work in its colour with a thin bar of
  the time left; a crash in red (dim while it waits: "Crash in 12s"); cooldowns on one grey line; the
  toxicity bar while there is any (yellow, amber past 50, red past 75).
- **Apothecary screen:** pharmacology and its progress; recipes with the success chance (or what's
  missing: the level, a lesson, a scroll); the mortar, the output, Grind; success and yield, the odds of
  each grade as a four-colour bar, the ingredients.
- **Tooltips:** a pill's grade, what it does, and its cooldown left.

## Advancements ("Medicine" branch of Path of the Shinobi)

A Proper Meal (eat a shinobi meal) → Bitter Medicine (swallow a pill) → Mortar and Pestle (brew a pill;
herb seeds) → A Steady Hand (a refined pill; 2 soldier pills) → Herbalist (pharmacology 25; a medical
scroll) → Flawless (a perfect pill, challenge) and Three Seals Broken (live through a three-seal
backlash, challenge). Hidden: One Too Many (an overdose). Trigger: `noirm:medicine` (`progress/PillTrigger`).

The first-steps guide has a fourth tip after the missions board: gather herbs and brew a first pill
(ticked off by a first brew or a first pill). The Shinobi Handbook has a Foods and Pills chapter.

## Commands (`/noirm help medicine`)

`/noirm pill` (status), `/noirm pill recipes`, and with cheats `/noirm pill give <pill> [grade] [count]`,
`/noirm pill take <pill> [grade]`, `/noirm pill pharmacology <1-100>`, `/noirm pill learn <recipe|all>`,
`/noirm pill clear`.

## Adding a pill or a food

1. Register the item in `PillItems` (`pill(...)` or `food(...)` with its hunger and saturation).
2. Add `data/noirm/pills/<id>.json` (or `foods/`): effects, crash, cooldown, toxicity, colour, recipe.
3. Draw its icon in `tools/pillgen/gen_pills.py`, add its name and tip to `tools/pillgen/pill_lang.py`;
   rerun both.
4. `PillGameTests.everyPillAndFoodDefinitionLoads` checks it loads.

## Screenshots (`docs/screenshots/pills-*.jpg`)

`pills-apothecary` (the screen with a recipe in the mortar), `pills-apothecary-tooltip` (a locked
recipe), `pills-apothecary-missing-ingredient`, `pills-apothecary-failed-brew` (a real 5% failure: a
ruined pill), `pills-herbs-row` and `pills-herbs-close` (the five herbs and rice, ripe and growing),
`pills-icons` (every icon and texture at 4×), `pills-hud` (a meal, three pills, their crashes,
cooldowns and toxicity), `pills-npc-pill` (a hurt ninja after swallowing a blood pill mid-fight: the
regeneration swirls).
