# Research notes

Sources are listed at the end. Narutopedia (naruto.fandom.com) blocks automated reads, so these
come from search results summarising it and other wikis. Where they disagree, that's noted. Treat
ranks and seal counts as "fan-wiki consensus", not official databook quotes.

## 1. The first 10 canon jutsu (owner: "start with 10")

| # | Canon name | Kanji / romaji | Rank | Canon behaviour | How NOIRM does it |
|---|---|---|---|---|---|
| 1 | Fire Release: Great Fireball Technique | 火遁・豪火球の術 Katon: Gōkakyū no Jutsu | C | Chakra kneaded into fire, breathed out as a huge fireball (or flame); **size depends on the chakra put in**. An Uchiha signature. | Hold to charge: more charge spends more chakra and makes a bigger, hotter ball that scorches and burns the ground. |
| 2 | Fire Release: Phoenix Sage Fire Technique | 火遁・鳳仙火の術 Katon: Hōsenka no Jutsu | C | A volley of small fireballs, **each steered with chakra** so they're hard to dodge; **shuriken can hide inside the flames**. | 5 small fireballs that curve toward the target; some carry a hidden shuriken that pierces. |
| 3 | Water Release: Water Dragon Bullet Technique | 水遁・水龍弾の術 Suiton: Suiryūdan no Jutsu | B | A dragon of water; 44 hand seals at first (the most in the series). **Drawn from a water source.** | A big homing water dragon; much stronger (and cheaper) with water nearby. |
| 4 | Water Release: Water Formation Wall | 水遁・水陣壁 Suiton: Suijinheki | B | Water blown out **in a circle around the user**; blocks attacks; strength set by chakra. | A water dome around you: stops projectiles and hits, puts out fire. |
| 5 | Earth Release: Earth-Style Wall | 土遁・土流壁 Doton: Doryūheki | B (unconfirmed) | Earth raised (or spat out) as a wall **coated in chakra, highly resistant to fire and water**. | The earth wall, now stopping fire and water jutsu completely. |
| 6 | Wind Release: Great Breakthrough | 風遁・大突破 Fūton: Daitoppa | C | A huge gust from the mouth. | A wide gust: throws enemies, turns projectiles, strips leaves and grass. |
| 7 | Chidori | 千鳥 | A | Lightning chakra concentrated in the hand ("a thousand birds" chirping), a charging thrust. **Weakness: tunnel vision** during the rush, which the Sharingan cancels. | Charge-and-dash strike with the chirping sound; without a Sharingan your vision narrows and you're open to counters. |
| 8 | Rasengan | 螺旋丸 | A | A sphere of chakra **rotating in many directions at once**, held in the palm, **no hand seals**; grinds and drills; created by Minato, learned in 3 stages (rotation, power, containment). | Form it in your hand, then drive it into an enemy: grinding hits, a spiral blast, a crater. Learned through chakra control (Reserves). |
| 9 | Shadow Clone Technique | 影分身の術 Kage Bunshin no Jutsu | B | **Real, solid clones**; chakra **divided evenly**; a clone vanishes in a puff when hit hard; what clones experience returns to the user. | Clones that fight beside you (your skin), each taking a share of your chakra; one hit dispels them; their training XP comes back to you. |
| 10 | Shadow Imitation Technique | 影真似の術 Kagemane no Jutsu | C (D in one source) | Nara clan **Yin Release**: the shadow stretches to the target, who must **copy the user's movements**; ~5 min for Shikamaru, **shorter with repeated use, weaker in poor light**. | Channel: your shadow runs along the ground; the target is frozen and mirrors your movement; weaker in the dark, drains chakra while held. |

Also canon and now used for names: Swamp of the Underworld (Doton: Yomi Numa), Water Prison
(Suirō), Air Palm / Eight Trigrams Vacuum Palm (Hakke Kūshō), Mystical Palm (Shōsen Jutsu,
medical, Yang), Partial Multi-Size (Bubun Baika, Akimichi, Yang).

## 2. Yin, Yang, Yin–Yang

- **Yin Release** (陰遁, Inton): spiritual energy, imagination, "form from nothing". Tends to run
  in the **Uchiha**; the **Nara** use it for shadows. Genjutsu sits close to it.
- **Yang Release** (陽遁, Yōton): physical energy, vitality. Tends to run in the **Senju**; the
  **Akimichi** use it to grow their bodies. Usually grouped with medical ninjutsu (the weakest
  claim of these).
- **Yin–Yang Release** (陰陽遁, Onmyōton): both together, the Sage of Six Paths' "Creation of All
  Things" (giving life to form). Naruto later uses it to heal.
- In NOIRM: Yin and Yang are non-elemental natures; Uchiha lean Yin, Senju lean Yang; Yin–Yang is a
  combination in the tree (own both + mastery).

## 3. How fights should look (owner: not only striking)

- **Naruto taijutsu styles:** Strong Fist (Gōken, Guy and Lee: big, external, flashy kicks,
  Leaf Whirlwind, Leaf Rising Wind, Dynamic Entry); Gentle Fist (Jūken, Hyūga: palm strikes into
  chakra points, internal damage); brawling (Naruto, Kiba: scrappy, dirty).
- **Real clinch fighting (Muay Thai):** the clinch is an *attacking* position, not a stall: grips
  (collar tie, underhook, body lock) set up **knees** (the main damage), **elbows** (high damage,
  high risk), **sweeps** (counter a committed knee, put the opponent down while you stay up) and
  **throws**. Defence: posture, frames, the knee block.
- **Fighting-game structure we adopt:** the triangle **strikes beat grabs, grabs beat guard, guard
  beats strikes**; posture/balance as the resource fought over; knockdowns and wall slams as
  rewards; juggles after launchers.

## 4. Particles and effects (owner: look at how others do it)

- Minecraft's own tools (Bedrock's Snowstorm, Fabric/Forge custom particle types) build effects
  from **render parameters + components** (lifetime, motion, color over time, size over time),
  and animate by swapping textures. Mods like Iron's Spells add their own particle types and
  textures rather than relying on vanilla ones.
- Game VFX practice: design with **shape, timing, colour**. Timing: a short **anticipation**
  (gather), a sharp **impact** (flash), a long **follow-through** (smoke, embers, debris): cutting
  the tail short looks cheap. Colour: **near-white core, saturated element-coloured middle, dark
  or complementary edge**, additive blending for the core; value contrast first. Restraint:
  readable beats busy; watch overdraw.
- In NOIRM: our own particle types (glow, spark, smoke, ring, shard), additive and full-bright where
  it should glow, colour and size over life; effect presets per nature built in layers
  (anticipation → impact flash → shockwave ring → sparks/debris → lingering smoke/embers).

## Sources

- Great Fireball: [Naruto Fanon Wiki](https://narutofanon.fandom.com/wiki/Fire_Release:_Great_Fireball_Technique), [Narutofantasy wiki](https://narutofantasyworldrecreated.fandom.com/wiki/Fire_Release:_Great_Fireball_Technique)
- Phoenix Sage Fire: [Narutopedia](https://naruto.fandom.com/wiki/Fire_Release:_Phoenix_Sage_Fire_Technique), [Naruto Fanon Wiki](https://narutofanon.fandom.com/wiki/Fire_Release:_Phoenix_Sage_Fire_Technique)
- Water Dragon Bullet: [Anime & Manga Universe wiki](https://animeandmangauniverse.fandom.com/wiki/Water_Release:_Water_Dragon_Bullet_Technique), [Sportskeeda: hand seals](https://www.sportskeeda.com/anime/5-jutsus-difficult-hand-signs-naruto-5-easy-copy)
- Water Formation Wall / Earth-Style Wall: [Animemagics: Suijinheki](https://animemagics.fandom.com/wiki/Suiton_%E2%80%A2_Suijinheki), [Anime & Manga Universe: Water Formation Wall](https://animeandmangauniverse.fandom.com/wiki/Water_Release:_Water_Formation_Wall), [Ex-Naruto: Doryuu Heki](https://ex-naruto.fandom.com/wiki/Doton:_Doryuu_Heki)
- Chidori: [Narutopedia](https://naruto.fandom.com/wiki/Chidori), [FandoHub](https://naruto.fandohub.com/en/techs/chidori), [Wikipedia: Kakashi Hatake](https://en.wikipedia.org/wiki/Kakashi_Hatake)
- Rasengan: [Narutopedia](https://naruto.fandom.com/wiki/Rasengan), [FandoHub](https://naruto.fandohub.com/en/techs/rasengan), [Jutsu wiki](https://jutsu.fandom.com/wiki/Rasengan)
- Shadow Clone: [Narutopedia](https://naruto.fandom.com/wiki/shadow_clone), [Deidarapedia](https://deidarapedia.fandom.com/wiki/Shadow_Clone_Technique?oldid=4300), [TechNadu](https://www.technadu.com/?p=292234)
- Shadow Imitation: [Anime & Manga Universe](https://animeandmangauniverse.fandom.com/wiki/Shadow_Imitation_Technique), [Baidu Baike](https://baike.baidu.com/en/item/Shadow%20Imitation%20Jutsu/1450118), [Wikipedia: Shikamaru Nara](https://en.wikipedia.org/wiki/Shikamaru_Nara)
- Yin / Yang: [Narutopedia: Yin Release](https://naruto.fandom.com/wiki/Yin_Release), [Yang Release](https://naruto.fandom.com/wiki/Yang_Release), [Yin–Yang Release](https://naruto.fandom.com/wiki/Yin%E2%80%93Yang_Release), [Sportskeeda](https://sportskeeda.com/anime/what-yin-yang-release-naruto-s-rarest-nature-transformation-explained)
- Clinch: [Evolve MMA: the Muay Thai clinch](https://evolve-mma.com/blog/heres-how-to-utilize-the-muay-thai-clinch/), [Muay Thai Guy: clinch techniques](https://muay-thai-guy.com/muay-thai-clinch-techniques.html), [Evolve: clinch sweeps](https://evolve-mma.com/blog/mastering-the-clinch-sweep-for-muay-thai/)
- Particles / VFX: [Microsoft: Bedrock particle effects](https://learn.microsoft.com/en-us/minecraft/creator/documents/particleeffects?view=minecraft-bedrock-stable), [Fabric: creating custom particles](https://docs.fabricmc.net/develop/rendering/particles/creating-particles), [real-time VFX principles](https://playbooks.com/skills/omer-metin/skills-for-antigravity/vfx-realtime), [SIGGRAPH: VFX for Games](https://history.siggraph.org/experience/vfx-for-games-particle-effects-by-herfort)
