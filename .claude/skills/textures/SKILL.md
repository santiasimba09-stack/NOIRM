---
name: textures
description: Where every NOIRM texture, icon, sound and generated asset comes from (the tools/*gen scripts), how to regenerate one, and how to add a new generator. Use before changing or adding any PNG, item icon, particle, UI art, skin part, sound or other generated asset.
---

# Textures and generated assets

All art is **ours, drawn by scripts** (no copied images; style rules in `docs/ART_STYLE.md`, UI in `docs/UI.md`).
Never hand-edit a generated PNG: change the script and rerun it, or the next run overwrites your edit.
Run every script from the repository root.

## Who makes what
| Asset | Script | Writes |
|---|---|---|
| Player/NPC skin parts, hair, eyes decals, sage marks | `tools/skingen/gen_parts.py`, `gen_player_parts.py`, `gen_hair.py`, `gen_eye_decals.py`, `gen_sage.py` (`looks.py` = look tables, `pixlib.py` / `hdskin.py` = shared helpers) | `textures/entity/...` parts, `assets/noirm/hair/` |
| Worn gear, clan clothing, drapes | `tools/gear/gen_gear.py`, `more_gear.py`, `armor_wave.py`, `clan_wear.py`, `drape_art.py` | `textures/models/armor/`, item icons |
| Weapon icons, held models, weapon moves | `tools/weapongen/gen_weapons.py`, `gen_held.py`, `gen_moves.py`, `gen_blade_anims.py` | `textures/item/`, `data/noirm/weapons/stats.json`, moves |
| Animations (moves, styles, seals) | `tools/animgen/gen_anims.py`, `gen_styles.py`, `seals.py` (`motion.py` = the authoring kit) | `assets/noirm/player_animation/`, `data/noirm/jutsu_seals/` |
| Hound fur atlas and layers | `tools/houndgen/gen_hound.py` (`atlas.py`, `paint.py`) | `assets/noirm/hound/atlas.json`, `textures/entity/hound/` |
| Aburame beetles, items, data | `tools/aburamegen/gen_art.py`, `gen_data.py`, `gen_jutsu.py`, `clan_perks.py`, `lang.py` | `textures/entity/aburame/`, Aburame JSON |
| Blockbench models from code | `tools/models/export_beetle.py` (skill `blockbench-model`) | `assets/noirm/models/bb/*.bbmodel` |
| Mangekyō patterns, eye icons | `tools/eyegen/gen_eyes.py` | `textures/entity/mangekyo_patterns.png`, icons |
| Gore marks and decals | `tools/goregen/gen_gore.py` | gore textures |
| Fire/lightning/space-time particles | `tools/vfxgen/gen_particles.py`, `gen_particles_kekkei_a.py`, `gen_spacetime.py` | `textures/particle/` |
| Wood Release blocks | `tools/woodgen/gen_wood.py` | `textures/block/mokuton_*` |
| UI kit atlas | `tools/uigen/gen_ui.py` | `textures/gui/` (one 256x256 atlas per look) |
| Pills, foods, apothecary | `tools/pillgen/gen_pills.py` (`pill_lang.py`) | icons, pill data |
| Nature/jutsu scrolls, chakra paper | `tools/naturegen/gen_items.py` | item icons |
| Guide and effect icons | `tools/icons/gen_guide_icons.py`, `gen_effect_icons.py` | item / mob_effect icons |
| Bingo book icon | `tools/bingogen/gen_book.py` | item icon |
| Sounds | `tools/soundgen/gen_sounds.py` (`recipes.py`, `dsp.py`; `docs/SOUND.md`) | `assets/noirm/sounds/`, `sounds.json` |
| Advancements | `tools/advancements/gen_advancements.py` | `data/noirm/advancements/` |
| Command docs | `tools/docs/gen_commands.py` | `docs/COMMANDS.md` |

Not sure who wrote a file? `grep -rl "<file name>" tools/` finds the script.

## Change or add art
1. Find the script above; read its docstring (it lists what it writes and the texture layout the game expects).
2. Change the drawing code, rerun the script, and check the result small: open the PNG (they're tiny) or, for an
   entity model, `python3 tools/models/preview.py <model>.bbmodel --texture <png>`.
3. Commit the script **and** the regenerated files together.

## A new generator
- Put it in `tools/<thing>gen/gen_<thing>.py` with a docstring: what it draws, what it writes (paths), the layout.
- Use `tools/skingen/pixlib.py` (Canvas, `tone`, `mix`, `hx`) or numpy + PIL (both installed). Seed any randomness
  so reruns give the same bytes (small diffs).
- Our own designs only; owner references are mood only. Add the row to the table above.
- Textures at 4x for skins and worn gear (256x256 for the 64x64 layout); item icons stay 16x16.
