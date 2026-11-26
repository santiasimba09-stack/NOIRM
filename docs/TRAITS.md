# Personal talents

_Owner's brief, 2026-11-18 (`WAVE_STYLES_CLANS.md`, part 3): "traits should not be per clan but
more like per player: talents can differ even within the same clan."_

## In short

- Every character rolls **2–4 talents** when it's made: 2 for sure, a 3rd half the time, and of
  those a 4th a quarter of the time (so 2: 50%, 3: 37.5%, 4: 12.5%).
- Each pick is weighted by the talent's `weight` × your clan's `talent_bias` for it. An Uzumaki
  is 5× likelier to roll Natural Sealer and 4× likelier to roll Deep Reserves, but two Uzumaki can
  still have nothing in common. No talent twice, nothing a pick excludes (Quick Learner and Late
  Bloomer; Fine Control and Shaky Control…), and **at most one weakness**.
- Talents are part of each creator roll: **a reroll rolls them again** with the clan (the reroll
  flow is unchanged: 1 + 3 rerolls, keep any roll you've seen).
- They're saved on the player (`ShinobiData` → `Talents`) and synced to the client inside
  `SyncPowers` (`ProfileSync`).
- **Shown**: the creator's **Clan** tab (hover a talent for what it does), `/noirm me`,
  `/noirm trait list` (with every modifier), `/noirm trait list all`.
- **Old characters** roll talents once on their next login (with their clan's bias), get their
  clan's perks, and the starting stats of both.
- **NPC ninjas** roll talents too (in the entity's Forge data, biased by their clan) and use
  the ones that make sense for them (health, healing, damage dealt and taken, resistances, jutsu
  power). Not on the game-test server, where tests give them fixed talents.
- **Crew personality stays separate.** A crew mate's Brave / Cautious / Loyal / Proud (`ally/Trait`)
  is how they behave (when they retreat, how fast they come to respect you); talents are what
  they're good at. Both are kept; no save changes for crew. Your own Born Leader and Kindred Spirit
  talents make crew mates respect you faster.

## The talents (29)

Kinds: **strength**, **mixed** (a strength with a cost), **weakness**. Amounts are in each
`data/noirm/traits/<id>.json`.

| Talent | Kind | What it does | Clans likelier to roll it |
|---|---|---|---|
| Prodigy | strength | Jutsu mastery ×2 | Hatake, Uchiha, Sarutobi |
| Taijutsu Genius | strength | Taijutsu training +60%, melee +10% | Might, Hyūga, Kaguya, Yotsuki, Kurogane |
| Chakra Sensor | strength | Senses chakra within 28 blocks (crouch: radar), perception +5% | Yamanaka, Aburame, Uzumaki |
| Iron Will | strength | Genjutsu resistance +30%, start WIL +3, Resolve +25% | Kurogane, Shimura, Might, Uzumaki |
| Quick Learner | strength | Every stat trains 25% faster | Hatake, Nara |
| Late Bloomer | mixed | Stats under 30 train 25% slower; from 30 on, 60% faster | Might |
| Frail but Precise | mixed | Max health −4, damage taken +10%; jutsu power +12%, perception +10% | Kurama, Iburi, Yamanaka |
| Glutton | strength | Foods +40%, max stamina +10 | Akimichi |
| Pill Prodigy | strength | Pharmacology XP ×2, pills +15%, pill toxicity −15% | Senju, Kamizuru |
| Stubborn Body | strength | Start DUR +4, durability training +50% | Kaguya, Akimichi, Kazekage line |
| Fleet-footed | strength | Start SPD +3, speed training +30% | Inuzuka, Yuki, Hōzuki |
| Natural Sealer | strength | Sealing power +40%, sealing cost −25%, beast bond +25% | Uzumaki (×5) |
| Hot-Blooded | mixed | Melee +12%, Resolve +40%; control training −30% | Jūgo's clan, Inuzuka, Might |
| Cursed Luck | mixed | Damage taken +8%, pill toxicity +25%; Resolve +20%, training +5% | Uchiha, Chinoike, Shimura |
| Deep Reserves | strength | Max chakra +40, chakra regen +10% | Uzumaki, Hoshigaki, Senju |
| Quick Recovery | strength | Stamina regen +30%, healing +0.05 HP/s | Uzumaki |
| Keen Eyes | strength | Perception +10% | Uchiha, Hyūga |
| Fine Control | strength | Jutsu cost −12%, start CTL +3 | Hyūga, Yuki, Nara, Aburame |
| Strategist | strength | Control training +40%, willpower training +20%, mastery +25% | Nara (×5), Shimura |
| Tough as Nails | strength | Max health +4, poison and burns 25% resisted | Akimichi, Senju |
| Shaky Control | weakness | Jutsu cost +15%, control training −20% | (Senju, Jūgo's clan, Uzumaki a little) |
| Sickly | weakness | Max health −4, DoT 25% worse, loses 0.02 HP/s (never below a heart) | Kaguya, Iburi |
| Easygoing | mixed | Training −15%; stamina regen +25%, chakra regen +15% | Nara |
| Genjutsu Talent | strength | Genjutsu power +30%, willpower training +15% | Kurama, Chinoike, Uchiha |
| Healing Hands | strength | Medical ninjutsu +30%, healing +0.05 HP/s | Senju, Yamanaka |
| Kindred Spirit | strength | Beast bond +60%, crew respect +20% | Inuzuka, Kamizuru |
| Born Leader | strength | Crew respect +40%, Resolve +15% | Sarutobi, Senju |
| Heavy-Handed | mixed | Melee +15%; speed training −20% | Kaguya, Yotsuki, Hoshigaki |
| Glass Jaw | weakness | Posture −20, damage taken +6% | (Hōzuki a little) |

The perk names behind these (`max_chakra`, `stat_xp`, `genjutsu_resist`…) and where each is used
are in `traits/Perk.java`; the same perks make up clan perks (`CLANS.md`).

## Commands

- `/noirm trait list` — your talents, each with its description and modifiers.
- `/noirm trait list all` — every talent.
- `/noirm trait give <talent>` / `take <talent>` / `reroll` (cheats).

## Adding a talent

1. `data/noirm/traits/<id>.json`: `kind` (`positive`, `mixed`, `negative`), `weight`,
   `modifiers` (perk → amount), optional `excludes`.
2. `talent.noirm.<id>` and `talent.noirm.<id>.desc` in all three lang files.
3. Optionally bias clans toward it (`talent_bias` in the clan or perk-package file).

## Tuning

- Talent amounts and weights: the JSON. Counts and the weakness cap: constants at the top of
  `traits/Talents`. Late Bloomer's line and numbers: constants at the top of `traits/Perks`.
