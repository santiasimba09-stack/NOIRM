# Tuning NOIRM yourself

This guide is for changing numbers without Claude: more chakra, longer Gates, fewer wild ninjas, and so on.
For each wish it gives the file, the key, what the number means, a safe range, and how to apply the change.

## The three ways to change a number

| Way | What it covers | How to apply |
|---|---|---|
| **1. The config file** | Difficulty, wild ninjas, legends, terrain, gore, progression speed, pills | Edit `config/noirm-common.toml` in your game folder (or `run/config/` with `./gradlew runClient`). The game picks up the saved file by itself within a second or two. You can also type `/noirm config <option> <value>` in game. |
| **2. A data file + `/reload`** | Jutsu, clans and clan odds, perks, talents, combat moves and styles, wild-ninja groups, hound traits, genjutsu scenes, pills and foods, masters, missions, kits, hand seals, jutsu clashes | Put your copy of the file in a **tuning data pack** in your world (see below), edit it, then type **`/reload`** in game. You need cheats on (single player: *Open to LAN → Allow Cheats: ON*). |
| **3. A constant in the Java code** | Everything else (hound growth curve, Aburame colony numbers, training XP amounts, combat system rules) | These need a rebuild. Ask Claude, or change the constant named below and run `./gradlew build`. |

### The tuning data pack (way 2)

The mod's own data files sit inside the jar. To change one, you put a copy with the same path in a data pack in your
world. The copy wins over the mod's file, and deleting the copy brings the mod's numbers back.

- **The quick way (with the repo on your computer):**
  `python3 tools/dev/tuning_pack.py "<your world folder>" data/noirm/clans/uzumaki.json`
  This makes `<world>/datapacks/noirm_tuning/` and copies the file into it. Edit the copy, then `/reload`.
- **By hand:**
  1. In your world's save folder (`.minecraft/saves/<world>/`), make `datapacks/noirm_tuning/`.
  2. In it, make `pack.mcmeta` with `{"pack": {"pack_format": 15, "description": "my NOIRM numbers"}}`.
  3. Copy the file from the repo (`src/main/resources/data/noirm/...`) to the same path under
     `datapacks/noirm_tuning/data/noirm/...`.
  4. Edit the copy, then type `/reload`. `/datapack list` should show `file/noirm_tuning`.

**What `/reload` refreshes:** every NOIRM data folder above. The jutsu list is sent to your client again by itself.
Changes show within a tick: a new chakra pool shows on the HUD at once, and a jutsu's new damage applies on the next
cast. **What needs a restart:** blade stats (`data/noirm/weapons/stats.json`; items are made before data packs load),
the hair styles, cloth drapes and random ninja-look tables (read from the mod's own files at start-up), and every
texture, model and animation (those are assets: press **F3+T** in game with a resource pack instead).

**If a file is wrong** (a missing comma, a typo in a key), `/reload` keeps going and the log says
`Invalid <folder> file noirm:<name>`. That one file is skipped, so its jutsu or clan disappears until you fix it.
Check the file at https://jsonlint.com if you're unsure.

## Common wishes

Ranges are "safe" in the sense that nothing breaks; outside them, things still work but the game gets silly.

### Chakra and clans

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| Bigger (or smaller) Uzumaki chakra | `data/noirm/clans/uzumaki.json` → `perks.chakra_pool` | The pool is ×(1 + this). `99` = 100× a normal ninja's | 0–200 | data + `/reload` |
| Uzumaki jinchūriki pool | same file → `perks.jinchuriki_pool` | With a beast inside, the pool is ×(1 + `chakra_pool` + this) | 0–200 | data + `/reload` |
| Any clan's bonuses | `data/noirm/clans/<clan>.json` → `perks` | Each key is a perk (meanings in `com.noirm.traits.Perk`). `0.35` = +35% for percent perks; `max_health: 10` = +10 health points (5 hearts) | percent perks −0.9…3 | data + `/reload` |
| Clan odds (who you roll) | `data/noirm/clan_odds/canon.json` → `canon_share`, `weights.<clan>` | `canon_share`: chance a new character is a canon clan (0.8 = 80%). `weights`: relative odds between canon clans (Nara 60 vs Uchiha 35 = Nara is ~1.7× as likely) | `canon_share` 0–1; weights 0–200 (0 = never) | data + `/reload` (affects new characters and NPCs) |
| A talent's strength | `data/noirm/traits/<talent>.json` → `modifiers` | Same perk keys as clans; `weight` = how often it is rolled | — | data + `/reload` |
| Chakra cost of all jutsu | config `difficulty.chakraCost` | 1.0 normal, 0.5 = half price (players only) | 0.1–10 | config |

### Jutsu

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| Jutsu damage | `data/noirm/jutsu/<nature>/<jutsu>.json` → `params.damage` | Health points per hit (20 = a full vanilla health bar) | 1–60 | data + `/reload` |
| Jutsu cost / cooldown | same file → `chakra_cost`, `cooldown` | Chakra points; cooldown in **ticks** (20 ticks = 1 second) | cost 1–500; cooldown 0–6000 | data + `/reload` |
| Jutsu size and reach | same file → `params.radius`, `params.range` | Blocks | radius 0.5–15; range 2–64 | data + `/reload` |
| When a jutsu unlocks | same file → `unlock.after` and `unlock.mastery` | Needs that other jutsu at this many stars (★1–★5, or up to Lv 10 for a jutsu with `max_mastery` 10) | mastery 1–10 | data + `/reload` |
| A jutsu's mastery ceiling | same file → `max_mastery` | Highest mastery level (default 5). Levels 6–10 need 250 / 400 / 600 / 850 / 1150 casts and add +10% power and −4% cooldown each (the Hyūga techniques have 10) | 1–10 | data + `/reload` |
| A jutsu costs no chakra | same file → `"chakra_free": true` | A body art: no chakra cost, no Reserves gate, power follows Taijutsu, no clan `jutsu_power` penalty (the Hyūga techniques) | true / false | data + `/reload` |
| Mastery's per-level effect | constants in `com.noirm.jutsu.JutsuCaster` (`MASTERY_POWER` 0.15, `HIGH_MASTERY_POWER` 0.10, `MASTERY_SAVING` 0.08, `HIGH_MASTERY_SAVING` 0.04); XP table `LEVEL_XP` in `JutsuBook` | power and cost/cooldown change per level (★1–5, then 6–10) | — | code (rebuild) |
| **Gates last longer** | `data/noirm/jutsu/taijutsu/gate_of_*.json` → `mode.health_drain`, `mode.end_health` | The Gates have no timer: they burn `health_drain` health **per second** and slam shut when health reaches `end_health` × max health. Lower `health_drain` = they last longer; lower `end_health` = they stay open closer to death | `health_drain` 0–5; `end_health` 0.05–0.9 | data + `/reload` |
| Gates power | same files → `mode.damage`, `mode.speed`, `mode.defense` | Multipliers (`damage: 1.6` = +60% hit damage); `speed` is added movement speed | damage 1–4; speed 0–1.5 | data + `/reload` |
| Hand seals of a jutsu | `data/noirm/jutsu_seals/<nature>/<jutsu>.json` | The seal names in order | — | data + `/reload` (the animation is generated separately: ask Claude) |

### Combat

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| A punch or kick hits harder | `data/noirm/combat/moves/<style>/<move>.json` → `hit.damage`, `hit.posture` | Health points; posture = guard damage (guards break at the posture bar) | damage 1–20; posture 0–80 | data + `/reload` |
| A move is faster or slower | same file → `timing.windup`, `timing.recovery` | Ticks (20 = 1 s). Lower = faster | windup 2–20; recovery 3–25 | data + `/reload` (keep the animation in mind: big changes look off) |
| Enemies hit harder / have more health | config `difficulty.npcDamage`, `difficulty.npcHealth` | 1.0 = normal | 0.1–10 | config |
| Armour against ninjas | config `difficulty.ninjaArmour` | Share of vanilla armour that counts against ninja attacks (1.0 = full) | 0–1 | config |
| Combat system rules (perception cap, juggles, wall splats) | constants at the top of `com.noirm.combat.CombatSystem` | Read `docs/COMBAT_DESIGN.md` first | — | code (rebuild) |

### The world

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| More or fewer wild ninjas | config `wildNinjas.ratePercent`, `maxGroupsNear`, `maxNinjasNear`, `safeStartMinutes` | 100 = normal rate; caps within 128 blocks; minutes of peace at the start | rate 0–1000; groups 0–16; ninjas 0–64 | config |
| Which wild groups appear | `data/noirm/encounters/<group>.json` → `weight`, `night_weight`, `min_size`, `max_size`, `leader_strength`, `member_strength` | Relative odds by day and night; group size; strength vs you (1.0 = your level) | weight 0–50; strength 0.3–3 | data + `/reload` |
| Legends | config `legends.perWorld`, `safeRadius`, `graceMinutes` | How many, how far from spawn, minutes before they hunt you | 0–6; 0–100000; 0–100000 | config (perWorld is read when the bingo book is first filled) |
| Terrain damage and regrowth | config `world.terrainDestruction`, `world.terrainRegenScale` | On/off; 0.5 = regrows twice as fast | 0.05–100 | config |
| Gore | config `gore.enabled`, `gore.intensity` | On/off; how fast wounds build and limbs sever | 0–3 | config |

### Training and progression

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| Stats grow faster | config `progression.trainingSpeed` | 2.0 = twice as fast (all stat XP) | 0.1–10 | config |
| Nature drills take less time | config `progression.drillTime` | 0.5 = half as long | 0.05–10 | config |
| More ryo | config `progression.ryoRewards` | Missions and bounties | 0–10 | config |
| One clan trains a stat faster | `data/noirm/clans/<clan>.json` → `perks.xp_taijutsu` (or `xp_speed`, `xp_reserves`…) | +fraction (`3.0` = ×4) | −0.9…5 | data + `/reload` |
| The solo-drill cap (30) and XP per hit, dodge, parry | constants at the top of `com.noirm.training.TrainingSystem` (`DRILL_CAP`, `HIT_XP`, `PARRY_XP`…) | XP points | — | code (rebuild) |
| Shadow clone training: share each idle clone banks, fatigue per clone/XP, tired/exhausted lines, decay | constants at the top of `com.noirm.training.CloneTraining` (`CLONE_TRAIN_SHARE` 0.8, `FATIGUE_PER_CLONE` 3, `TIRED_AT` 40, `EXHAUSTED_AT` 75, `NO_CLONES_AT` 95) | shares and fatigue points (0–100) | — | code (rebuild) |
| Shadow clones: solid hit, feint stun, Rasengan bonus, Multi cap, chakra per clone | constants at the top of `com.noirm.entity.ShadowClone` (`SOLID_HIT` 1.5, `FEINT_STUN_TICKS` 16, `RASENGAN_CLONE_BONUS` 1.5, `MULTI_MAX` 24, `MIN_CHAKRA_SHARE` 10) | damage, ticks, multipliers, counts | — | code (rebuild) |
| Training weights, leaf and climbing drills | `com.noirm.training.WeightedTraining` (`XP_MULTIPLIER` 1.5, `SLOW` 0.2), `ChakraDrills` (`LEAF_XP`, `CLIMB_XP`, `WALL_CONTROL` 30) | multipliers, XP | — | code (rebuild) |
| Eight Gates time limit | constants at the top of `com.noirm.jutsu.EightGates` (`BASE_SECONDS`, seconds per DUR/WIL/TAI/VIT point, `RESOLVE_BONUS_SECONDS`, `TIER_COST`, `UNLIMITED_AVERAGE` 35/45…) | seconds; the stat average with no limit | — | code (rebuild) |
| Byakugan and Gentle Fist | constants at the top of `com.noirm.dojutsu.Byakugan` (`SIGHT_RANGE` 64, `SIGHT_PER_CONTROL` 3, `MAX_SIGHT` 256, `BLIND_SPOT_CLOSED_AT` 45, `GENTLE_FIST_GUARD` 1.0, `INTERNAL_DAMAGE` 0.8, `POINTS_PER_HIT` 4, `SEAL_TICKS` 400, `DRAIN_PER_TICK` 0…); jutsu numbers in `data/noirm/jutsu/byakugan/*.json` | shares, ticks, blocks | — | code / data + `/reload` |
| Hyūga techniques' mastery gains | constants at the top of `com.noirm.jutsu.HyugaTechniques` (`ROTATION_RADIUS_PER_LEVEL` 0.2, `ROTATION_TICKS_PER_LEVEL` 2, `PALMS_POINTS_PER_LEVEL` 0.1, `AIR_PALM_RANGE_PER_LEVEL` 1.2, `LION_TICKS_PER_LEVEL` 20, `PALMS_AIM_DEGREES` 40, `AIR_PALM_AIM_DEGREES` 32) | per mastery level: blocks, ticks, share; aim cones in degrees | — | code (rebuild) |
| Gentle Fist arts (wave "Hyūga") | `data/noirm/gentle_fist/tuning.json`: `legs_below` / `arms_above` (where a palm counts as legs, chest or arms, as a share of body height), `region_ticks`, `legs_max_level`, `arms_max_level` (Slowness / Weakness), `chest_points`, `bleed_share` + `bleed_seconds` (organ strikes), `burst_heavy_only`, `reopen_per_second`, `reopen_seal_speed`, `palm_parry_window`, `palm_parry_speed` | shares, ticks, levels (0 = I), multipliers | see the defaults in the file | data + `/reload` |
| Byakugan lag caps | constants at the top of `client/ByakuganVision` (`MAX_SILHOUETTES` 20, `SILHOUETTE_RANGE` 48, `MAX_CORES` 80, `MAX_POINT_BODIES` 8, `MAX_RADAR_DOTS` 96, `TARGET_REFRESH_TICKS` 5) and `dojutsu/XRayScanner` (`BLOCKS_PER_STEP` 12000, `MAX_RESULTS` 96, `REFRESH_TICKS` 40) | counts, blocks, ticks: lower = faster, less shown | — | code (rebuild) |
| Hyūga perks (parry, guard) | `data/noirm/clans/hyuga.json` → `perks.parry_window` (0.5 = 50% longer), `perks.guard` (0.3 = 30% less through a guard) | shares | parry 0–1.5; guard 0–0.7 | data + `/reload` |
| Pills stronger | config `medicine.pillStrength`; one pill: `data/noirm/pills/<pill>.json` | Multiplier; the pill's own boosts | 0.1–5 | config / data + `/reload` |

### The Inuzuka hound

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| A hound trait's effect | `data/noirm/hound_traits/<trait>.json` → `size`, `health`, `speed`, `weight` | Multipliers (`1.15` = +15%); `weight` = how often it is rolled | 0.5–2 | data + `/reload` (new rolls; existing hounds keep their traits, the numbers apply live) |
| Hound growth speed (XP per level) | `com.noirm.hound.HoundGrowth.xpFor` (`10 × (level−1)^1.55`) and XP gains at the top of `com.noirm.hound.Hounds` (`XP_PER_DAMAGE`, `XP_PER_KILL`, `TRAINING_SHARE`) | XP points | — | code (rebuild) |
| How big hounds get | `com.noirm.hound.HoundGrowth` → `SIZE_LEVELS`, `SIZE_SHOULDER` | Shoulder height in blocks at each level | — | code (rebuild) |
| A breed's shape | `assets/noirm/hound/breeds/<breed>.json` | Body proportions | — | resource pack + **F3+T** |

### The Aburame

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| Bigger colony in the body | `com.noirm.aburame.ColonyGrowth` → `BODY_BASE` (4000), `BODY_PER_RESERVES` (600), `BODY_PER_CONTROL` (200) | Insects the body holds: base + per Reserves point + per Control point | — | code (rebuild) |
| Jar, gourd and hive sizes | same class → `JAR_CAPACITY`, `GOURD_CAPACITY`, `HIVE_CAPACITY`, `MAX_HIVES` | Insects each holds | — | code (rebuild) |
| Breeding speed | same class → `BREED_SHARE_PER_MIN`, `BREED_FLAT_PER_MIN` | Share of the colony and flat insects added per minute | — | code (rebuild) |
| An insect technique's cost | `data/noirm/jutsu/aburame/<jutsu>.json` → `hiden.insects`, `hiden.share` | Insects it spends (flat, or a share of the colony) | — | data + `/reload` |

### Crew mates' eyes and fusions

| Wish | File and key | What the number means | Safe range | Apply |
|---|---|---|---|---|
| Fusion lessons faster or slower | constants at the top of `com.noirm.ally.CrewFusion` → `FUSION_SECONDS` (4800), `APTITUDE_RATE` (3), `BORN_RATE` (1), `TAUGHT_RATE` (0.35) | Seconds of full-pace study (1,200 = a Minecraft day); pace multipliers by who they are | — | code (rebuild) |
| Which clan is gifted for which mix | same class → `APTITUDE` | Clan id → natures (Senju → Wood…) | — | code (rebuild) |
| A crew mate's Sharingan grows faster | constants at the top of `com.noirm.ally.CrewEyes` → `FIGHT_XP_PER_SECOND`, `HIT_XP`, `DANGER_XP`; tomoe thresholds `Sharingan.STAGE_XP` | Growth points (300 for 2 tomoe, 1,200 for 3) | — | code (rebuild) |
| When "near death" awakens an Uchiha crew mate | `CrewEyes.DANGER_HEALTH` (0.3), `DANGER_RANGE` (24) | Share of max health; blocks | — | code (rebuild) |

## Telling Claude what you changed

If a number you found feels right, tell Claude the file, the key and the new value so it goes into the mod for
everyone. If something broke after a change, delete your copy from the tuning pack, `/reload`, and say which file it was.
