# NYDO reference dump

This folder holds the assets extracted from **NydoMod 5.5** (`modId: nydomod`, by Yeyo & Daniel),
a Naruto RPG mod for **Minecraft Forge 1.20.1** (Forge 47.3+, GeckoLib 4.8, PlayerAnimator,
bendylib, TerraBlender, Mixins).

It is here as **inspiration and reference only**. It is not our mod and it doesn't build.

## What is (and isn't) here

| Present | Missing |
|---|---|
| JSON assets: models, blockstates, animations, VFX, cinematics, shaders, lang, a few recipes and advancements | **All Java code.** No `.class` or `.java`, so every mechanic is known only from its lang strings and data |
| `mods.toml` (dependencies, mixins) | **All textures.** No `.png` files, so the models reference textures that don't exist here |
| Credits and licence notices | Most `data/` (loot tables, recipes, worldgen, tags): only 15 files survived |

The original upload was flattened. Some files that share a filename probably overwrote each other.
For example, many block models are missing because the blockstate with the same name won.
This folder restores a Forge-like layout based on what each file actually contains.
37 byte-identical duplicates were dropped.

## Layout

```
META-INF/                 mods.toml, MANIFEST, credits + licence notices (read these!)
assets/
  lang/                   en_us, es_es, es_mx, pt_br  (~7.4k keys each — the best design doc we have)
  blockstates/            110  (village/biome blocks: sage_*, myoboku_*, frosted_*, akatsuki_*, mokuton_*…)
  models/item/            125  Blockbench item models (weapons, scrolls, dojutsu eyes, nature-release icons)
  models/obj/             kunai / shuriken / weapon OBJ meshes
  animations/player/      ~290 PlayerAnimator/Bedrock player animations, grouped by fighting style
                          (basic, combo, core, naruto, kurama, hyuga, uchiha, rock_lee, kiba, frog_mode,
                          toad_sage, weapon, jutsu, jutsu_misc = rasengan/chidori/hand-signs/shinra…)
  animations/susanoo/     Susanoo stage animations per user (itachi, kakashi, madara, sasuke, shisui)
  animations/entity/      GeckoLib entity animations (summons, clay creatures, Gedo Mazo, jutsu beasts)
  geo/                    GeckoLib geometry (Jiraiya, jutsu entities, biju)
  vfx/presets/            reusable particle-emitter presets (fire, water, raiton, wind, chakra…)
  vfx/effects/            331 composed effects by element (water 47, susanoo 75, raiton 29, bakuton 29…)
  cinematics/             camera + actor timelines (genjutsu, struggle, story, hyuga, jutsu)
  shaders/                core/program/post shaders (HUD, biju blur, shinra tensei post-fx)
  particles/              vanilla-style particle definitions
  wardrobe/models/        outfit definitions (Akatsuki, ANBU, Hokage…)
  font/, atlases/, rigs/, ocular/, susanoo/ (HYTMO manifest), sounds.json
data/
  advancements/, recipes/, loot_tables/
```

## The game systems, reconstructed from lang + data

- **Ninja progression.** Ranks run Civilian → Academy → Genin → Chūnin → Jōnin → ANBU → Kage.
  Ninja level goes up to 100, with Ninja XP, Jutsu XP and Taijutsu XP. There are 240 stat points
  across 8 attributes: Ninjutsu, Taijutsu, Genjutsu, Chakra Control, Vitality, Agility, Medicine and
  Defense. Everything is shown on the "Ninja Card" UI.
- **Identity.** There are 9 clans (Uchiha, Hyūga, Uzumaki, Hatake, Sarutobi, Kaguya, Hōzuki, Yuki
  and Yotsuki) plus Unaffiliated. "DNA" gates the kekkei genkai natures: Mokuton, Hyoton, Yooton,
  Futton, Shakuton, Ranton, Bakuton, Jiton, Koton, Meiton, Shoton, Jinton and Swift.
- **Jutsu.** Scrolls come from chests, mob drops or shops paid in Ryō. You learn a scroll through
  hand-seal training, then gain mastery through Jutsu XP. Techniques are selected from a jutsu row
  or wheel and cast against chakra cost, cooldown and conditions.
- **NYCombat.** A lock-on action-combat layer with light and heavy attacks, launchers, air chase,
  guard and guard-break, parry and dodge. It has per-character styles (Basic, Uzumaki, Uchiha, Hyūga
  Juken with tenketsu, Rock Lee, Kiba, Kurama, Matatabi), a taijutsu skill tree, and Rasengan and
  Chidori clashes.
- **Dōjutsu.** Sharingan → Mangekyō (Amaterasu, Kamui, Eternal, Kotoamatsukami, Izanagi and
  others), Rinnegan (the Pain paths), Byakugan, Tenseigan and Ketsuryugan. Susanoo has stages,
  per-user models and a palette. There's eye surgery and transplanting with an operating table.
- **Tailed beasts.** Biju cloak phases are earned through XP, with a mindscape fight and a biju
  bond advancement tree.
- **World.** Konoha has missions graded D to S, a Hokage, Jiraiya, Guy, mentors, NPC dialogue, a
  reputation and "standing" system, Akatsuki membership, shops and a wallet. There's a Shinobi
  Atlas map, a Sage/Myoboku region, and frozen and weathered biomes via TerraBlender.
- **Tooling.** In-game authoring tools for custom NPCs, dialogue, missions, spawns and content
  folders, plus a codex and a tutorial.

The mod also carries **267 entities**: jutsu projectiles, summons, canon characters as bosses or
NPCs, and traders.

## Licensing — important before we reuse anything

- NydoMod itself is **"All Rights Reserved"**. We take ideas, structure and data *formats* from it.
  We do **not** copy its models, animations or text into our mod unless the authors give permission.
- The Susanoo designs, models and animations belong to **HYTMO** (`META-INF/HYTMO_SUSANOO_RIGHTS_NOTICE.txt`).
- `animations/player/licensed_boundless/` is **MIT** (© LeBoundlessJame). We *can* reuse it, as long
  as we keep the licence text and attribution.
- The Sasuke katana model is **CC-BY-4.0** (theveis). It's reusable with credit.
