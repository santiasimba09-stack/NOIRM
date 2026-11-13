# A complete survival experience (owner's brief, 2026-11-05)

The owner asked: "think about what is left for the mod to be a complete experience that can be played
perfectly in survival, so that you can have a good progression, experience, etc." These are the
owner's answers to the gaps we found; ask before changing them. **Single-player only for now.**

## What the owner wants (all of it)
- **Progression and a playthrough.** A "Path of the Shinobi" advancement tree, from your first jutsu
  to Kage (the mod has no advancements yet). Then a full survival playthrough from zero to the late
  game to fix pacing, difficulty and confusing spots.
- **Config and a balance pass.**
  - A real config file: difficulty, how often wild ninjas and legends appear, destruction and regen,
    gore.
  - A balance pass driven by the playthrough. The open APX answers keep their current values for now
    (owner: "I like it all for now").
- **Sound.** Our own sounds for jutsu, hits, seals, clashes, the UI and ambience. Never copied audio.
  Done (v0.15.x): 54 synthesised sounds, see `docs/SOUND.md`.
- **Exploration.** Ruins, rogue hideouts, scroll vaults, sealed tombs and dungeons, with loot and
  bosses. Tailed-beast lairs (today beasts only come from NPC jinchūriki). Rare wandering masters.
- **Endgame.**
  - Becoming Kage.
  - Village wars and raids.
  - Our own big rogue organisation to hunt down (an original design, not a franchise copy).
  - Tailed-beast hunting.
- **Ninja staples.**
  - **Summoning contracts:** animal clans of our own design, partners that fight with you, and
    training with them.
  - **Food, pills and tools:** soldier pills, chakra food, blood pills, smoke bombs, wire, traps.
    Foods and pills built (12 foods, 8 pills, 5 herbs and rice, the apothecary and pharmacology):
    `docs/PILLS.md`. Tools (smoke bombs, wire, traps) still to come.
  - **Sealing and other paths:** fūinjutsu (seals, barriers, storage), the puppet path, space-time
    techniques.
  - **Crafting and economy:** a chakra-metal ore, a blacksmith who forges and upgrades, homes, hiring
    crew, more ways to spend ryo.

## Order (proposed; the owner can reorder)
1. **Foundations:** advancements, the config file. Then the playthrough and balance pass, once
   chakra wave part 2 (the workshop) is in, so it covers that too.
2. **Sound** (alongside 1).
3. **Survival staples:** food, pills, tools; crafting and economy.
4. **Exploration:** structures, dungeons and bosses, beast lairs, wandering masters.
5. **New paths:** summoning contracts, then sealing, puppets and space-time.
6. **Endgame:** Kage, wars and raids, the rogue organisation, beast hunting.

Still to come from the chakra wave: part 2 (the workshop and solo transformations, being built) and
part 3 (solid-flame chakra).

## Foundations (step 1, built)

### The "Path of the Shinobi" advancements (press L)
One tab (forehead-protector icon, deepslate background), 49 advancements written by
`tools/advancements/gen_advancements.py` (edit the table there, rerun it; it writes the JSON in
`data/noirm/advancements/shinobi/`, the reward tables and the text in all three languages). Each
description says how to do the step: where to go, whom to talk to, which key to press.

| Branch | Steps (in play order) |
|---|---|
| **Beginnings** | make your character → read the Handbook → hit a training dummy → a stat at 15 → 30 (the drill cap) → 50 |
| **Jutsu** | first cast → first ★ → an A-rank jutsu → an S-rank jutsu; *a jutsu of your own* (hidden, workshop) |
| **Natures** | chakra paper shows a hidden nature → first drill → a second nature → a combined nature; *fuse two natures alone* (hidden, workshop) |
| **Missions, rank, ryo** | first mission → genin → chūnin → jōnin → ANBU → an S-rank mission → **Kage candidate**; 1,000 and 10,000 ryo |
| **Combat** | first ninja defeated → just-frame parry; classes C → B → A → S → a legend (S+); claim a bounty |
| **Crew** | first recruit → a full crew of three; teach a crew mate a nature |
| **Masters** | Might Guy (after a stat at 15), Rensai (after your first jutsu), Tsubaki (after your first mission), the Stone Hermit (after your first drill) → Sage Mode |
| **Eyes** (hidden, clan-only) | Byakugan opened; Sharingan → three tomoe → Mangekyō |
| **Beasts** | subdue a tailed beast → seal it → become a jinchūriki |

Frames: plain steps are tasks, milestones are goals, the hardest (stat 50, S-rank jutsu, S class,
a legend, Kage candidate, Mangekyō, Sage Mode, jinchūriki, the workshop ones) are challenges.
Rewards are XP plus, now and then, something modest: ramen, chakra paper, a sealing scroll or a
ryo pouch (50–500). Never a skip.

### How the triggers work
`com.noirm.progress` has one trigger class per subject (`BeginningsTrigger`, `StatTrigger`,
`JutsuTrigger`, `NatureTrigger`, `RankTrigger`, `MissionTrigger`, `RyoTrigger`, `DefeatTrigger`,
`CombatFeatTrigger`, `CrewTrigger`, `MasterTrigger`, `EyesTrigger`, `SageTrigger`, `BeastTrigger`).
In the JSON a criterion names one and its conditions: `event` (what happened), `key` (which: a rank,
a stat, a master…) and `min` (at least this many / this level / this class).
- **Moments** are fired where they happen: a dummy hit, a jutsu cast, a drill finished, chakra
  paper, a ninja defeated or yielding, a just-frame parry, a bounty paid, a nature lesson
  finished, a beast subdued or sealed.
- **States** are checked once a second by `ProgressWatcher` (rank, ryo, stats, natures, jutsu known
  and ★, eyes, crew size, masters' tests, Sage Mode, jinchūriki), so every route counts: scrolls,
  masters, chains, commands.

**Workshop placeholders (for chakra wave part 2):** `CreateJutsuTrigger.fire(player, rank)` grants
"A Jutsu of Your Own"; `FuseNaturesTrigger.fire(player, nature)` grants "Born of Nothing". Nothing
calls them yet.

### The config file (`config/noirm-common.toml`)
Defaults are exactly the old behaviour. `/noirm config` (operators) shows the values in use; edit
the file to change one (it reloads while the game runs).

| Section | Option | Default | What it does |
|---|---|---|---|
| difficulty | `npcDamage` | 1.0 | damage enemy ninjas and tailed beasts deal to players |
| | `npcHealth` | 1.0 | health of enemy ninjas (not your crew) and tailed beasts |
| | `chakraCost` | 1.0 | chakra cost of players' jutsu |
| wildNinjas | `ratePercent` | 100 | how often groups turn up (× the `noirmWildNinjas` gamerule) |
| | `maxGroupsNear` / `maxNinjasNear` | 2 / 6 | caps within 128 blocks of a player |
| | `safeStartMinutes` | 20 | nothing near spawn for a new player's first minutes |
| legends | `perWorld` | 4 | legends a new world's bingo book lists |
| | `safeRadius` | 1000 | legends never seen closer to spawn than this |
| | `graceMinutes` | 120 | legends leave a new player alone this long |
| world | `terrainDestruction` | true | jutsu break terrain at all |
| | `terrainRegenScale` | 1.0 | regrowth time = `noirmTerrainRegen` seconds × this |
| gore | `enabled` | true | wounds, blood, severed limbs |
| | `intensity` | 1.0 | how fast wounds build up, how often limbs come off (0 = none) |
| progression | `trainingSpeed` | 1.0 | stat XP |
| | `drillTime` | 1.0 | nature drill length |
| | `ryoRewards` | 1.0 | ryo from missions and bounties |

**Config and gamerules:** the file is for every world, the gamerules are per world, and where both
exist **both must allow it**: terrain breaks only if `terrainDestruction` and `noirmTerrainDamage`
are on; gore shows only if `gore.enabled` and `noirmGore` are on (and each player's own
`goreLevel` in `noirm-client.toml` can still turn it down on their screen); the wild-ninja rate and
the regrowth time are the gamerule times the config value.
Screenshots: `docs/screenshots/sv-advancements-*.jpg` (the tab, a tooltip, a toast).

## Owner's answers on the progress map (2026-11-12)
- **Armour:** ninja attacks should **mostly ignore vanilla armour**. A new **chakra reinforcement**
  covers the body and armour with chakra: the more chakra (Reserves) and chakra control (CTL) you have,
  the more damage you deal and the less you take.
- **The quiet middle of the game:** yes, aim the next work at it, but **first foods, and above all
  pills**: "the development of pills is very important".
- **Rank gate on mastery chains:** no, they keep waiting for Reserves only.
- **Ryo:** don't worry about the economy for now. Foods, armour and weapons to spend it on will keep
  coming.
- **Water:** add an S-rank jutsu of our own design.
- **Legends:** about 100 hours without eyes, a beast or a crew is the intended endgame.
- **Combat feel and sound:** good for now, to revisit later.
- **Eyes:** less stretched, with no lid crease and nothing black past the eye (done 2026-11-12).
