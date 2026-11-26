---
name: add-jutsu
description: Add a new jutsu (or change one) in NOIRM - the data JSON, an existing or new behaviour, hand seals and their animation, lang names, unlock chain and a game test. Use for any new technique, clan jutsu, mode (like the Gates) or insect technique.
---

# Add a jutsu

A jutsu is data: `src/main/resources/data/noirm/jutsu/<nature-folder>/<name>.json`. Its id is `noirm:<folder>/<name>`.
Folders: `katon` (fire), `suiton`, `futon`, `raiton`, `doton`, combined natures (`mokuton`, `hyoton`, `yoton`, `ranton`,
`futton`, `shakuton`, `jinton`, `jiton`, `bakuton`), `yin`, `yang`, `yin_yang`, `medical`, `taijutsu`, `universal`,
`clan`, `sharingan`, `mangekyo`, `byakugan`, `biju`, `senjutsu`, `space_time`, `hound`, `aburame`.

## Checklist
1. **Pick a behaviour** from `jutsu/JutsuBehaviors.BEHAVIORS` (the `Map.entry("name", ...)` list: `projectile`,
   `burst`, `wall`, `dash_strike`, `beam`, `bind`, `pillar`, `cloud`, `rain`, `wave`, `strike`, `nova`, `mode`, ...).
   Copy a JSON that uses the same behaviour and change the numbers. Only write a new behaviour if none fits: add a
   `private static void name(JutsuContext ctx)` in the right `*Techniques` class (or `JutsuBehaviors`) and a `Map.entry` for it, plus the label
   `screen.noirm.jutsu.behavior.<name>` in the lang files. NPCs pick jutsu by range from `entity/NinjaFightGoal.RANGED`.
2. **The JSON** (fields from `jutsu/JutsuDefinition`):
   ```json
   {"nature": "fire", "behavior": "projectile", "rank": "B", "cast": "tap",
    "chakra_cost": 30, "cooldown": 200,
    "unlock": {"after": "noirm:katon/fire_stream", "mastery": 2},
    "params": {"damage": 10, "radius": 2, "range": 16, "speed": 1.2, "knockback": 0.8, "posture": 30,
               "duration": 40, "fire_seconds": 4, "effect": "slowness:1", "effect_ticks": 40}}
   ```
   - `cast`: `tap`, `charge` (`min_charge`/`max_charge` ticks) or `channel` (`max_duration`). Times are **ticks** (20 = 1 s).
   - Learnable: `"starter": true` (everyone with the nature) **or** an `unlock` chain. Dōjutsu: `"requires": {"dojutsu": "sharingan", "stage": 2}`.
   - Modes (toggles like the Gates, Lightning Armour): `"behavior": "mode"` + a `"mode"` object (`speed`, `damage`, `defense`, `drain`, `health_drain`, `end_health`, `gate`, `aura` colours).
   - Clan secret techniques: `"hiden": {...}` (Aburame insect costs).
   - Other `params`: `charge_damage`, `stun`, `count`, `spread`, `block`, `homing`, `look`.
3. **Name:** `python3 tools/dev/lang.py add jutsu.noirm.<folder>.<name> "Fire Release: Thing" "Elemento Fuego: Cosa"`.
4. **Hand seals:** add the sequence to `SEQUENCES` in `tools/animgen/seals.py`, run `python3 tools/animgen/seals.py`.
   It writes `data/noirm/jutsu_seals/<folder>/<name>.json` and the animation `assets/noirm/player_animation/seals_<folder>_<name>.json`.
   `"quick": true` in the jutsu JSON skips seals for tap jutsu.
5. **Clan jutsu:** list it in the clan's `data/noirm/clans/<clan>.json` → `clan_jutsu`.
6. **Test** (skill `game-tests`): a load check like `ChakraWaveBGameTests.waveBJutsuLoadWithSealsAndNames` (exists, known
   behaviour, seals, learnable, a name in all three lang files) plus a behaviour test: cast it with `JutsuCaster` at a
   target in its own batch and assert the effect. Run `tools/dev/test.sh <your batch word>`.
7. **Balance:** numbers you're unsure of go in your report as questions; the owner can tune them later (`docs/TUNING.md`).

## Notes
- Jutsu JSON reloads with `/reload`; the jutsu list is re-sent to clients automatically.
- Visuals come from the behaviour (`vfx/Vfx` palettes and presets by nature); a new look is a new preset there.
- Big jutsu that change terrain use `world/Destruction` (it queues regrowth and respects the gamerules).
