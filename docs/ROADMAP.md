# NOIRM roadmap

## Vision

A Naruto-inspired Forge 1.20.1 mod where the player grows from nobody into a shinobi by
**training under masters**, **fighting**, and **exploring** a world full of villages.
We build for single-player first.

The four pillars, in priority order:

1. **Combat that feels good.** Responsive inputs, combos, dodges, guard and lock-on, plus the
   "game feel" layer: hit-stop, knockback, camera shake, sound and particles.
2. **Training and masters.** You get stronger by practising and by finding masters who teach
   you techniques and fighting styles. Power is earned, not bought.
3. **World and villages.** Villages that go beyond vanilla: hidden villages, training grounds,
   dojos and ruins, with masters living in them, all generated naturally in the world.
4. **Exploration.** Reasons to travel: rare masters, scrolls, ruins and dangerous places.

Missions come later, as a secondary system built on top of villages and masters.

## What we take from NYDO (see `reference/nydo/`)

- **Ideas and structure:** stats on a ninja card, scroll → seal training → mastery for jutsu,
  per-style combat with light, heavy, launcher, guard and parry, and masters in villages.
- **Data-driven design:** jutsu VFX, animations and cinematics written as JSON. New content
  should mean new data files, not new code.
- **Not the assets.** NYDO is "All Rights Reserved". The only reusable pieces are the MIT
  Boundless combat animations (with attribution) and the CC-BY katana model.

## Milestones

Each milestone ends with something you can play and test.

| # | Milestone | What it gives you |
|---|---|---|
| 0 | **Foundation** ✅ | The project builds and the mod loads in Minecraft. An automated test proves it boots. |
| 1 | **Shinobi core** ✅ | Chakra and stamina: saved with the world, regenerating, and shown as HUD bars. A `/noirm` debug command. (Stats like taijutsu and agility come with training.) |
| 2 | **Combat feel v1** ✅ | A switchable combat mode, input buffering and cancels, light/heavy combo routes, a dodge with i-frames, guard with posture and parry, perfect-dodge focus, perception slowdown, impact (hit-stop, knockback, shake, sound), lock-on, a sparring ninja, and animations (our own engine since round 5). See `COMBAT_DESIGN.md`. |
| 3 | **Identity & powers** ✅ | Clans (canon + generated per world), natures, 4-slot jutsu loadout with 9 jutsu, the Sharingan (perception, precognition, genjutsu, copy), the near-death awakening (Resolve), the jinchūriki cloak. See `POWERS_DESIGN.md`. |
| 3.5 | **Training** ✅ (part 1) | A training dummy; taijutsu and speed grow from practice (drills to 30, then real fights). Also built: the Byakugan and tailed beasts (fight, subdue, seal). Part 2: training post, very hard nature training. |
| 3.6 | **Playtest round 1** ✅ | Animations fixed; eyes vs all mobs; living Sharingan; Vitality and Reserves; the nature tree with 9 combinations; 39 jutsu; the new jutsu screen. |
| 4 | **First master** (part 1 ✅, v0.9.0) | Might Guy, a taijutsu master who tests you with a spar, then teaches the Eight Gates, Front Lotus, Morning Peacock and Daytime Tiger. Masters are JSON, so more follow (a ninjutsu master, a medical ninja). Next: Reverse Lotus, later gates, technique animations. |
| 5 | **First jutsu** | Hand seals, a chakra cost, and three starter jutsu with data-driven VFX. |
| 6 | **Villages** (parts 1–4 ✅) | Part 1 (round 4): hidden villages with a Kage tower and Kage, outposts, residents and merchants. Part 2 (v0.5.0): planned ~250-block capitals, towns, landmarks, living villagers. Part 3 (v0.6.0): ~545-block capitals built from per-chunk wards, a fan layout with a river and districts, colourways and façades, yards, spires, village life. Part 4 (v0.7.0): the Mist, Cloud and Stone villages, the canon Leaf wall, seats, mist. Next: village clans, a dojo where masters live, missions. |
| 7 | **Exploration** | Ruins, scroll loot, rare wandering masters, and a first custom biome. |
| 3.7 | **Playtest rounds 2–4** ✅ | Combat styles, destruction, 56 jutsu, crew allies with respect and growth, beast bond and tails, NPC animations, wearable clan gear, random looks. |
| 3.8 | **Playtest round 5** ✅ (v0.8.0) | Our own animation engine for players and ninjas (jointed body, ninja run, springs, first-person arms) with a new 51-animation set; ninja weapons (kunai, shuriken, fūma, senbon, explosive tags, bō staff, ninjatō, chakra blade) with throws and weapon movesets; allies use what they hold, eat what you feed them, and fight whoever engages them; ninjas use jutsu much more; water walking; the Sharingan awakens and grows through real fights. |
| 3.9 | **Playtest round 6** ✅ (v0.9.0) | Armor bends with the body; planted footwork; 39 gear pieces and special weapons; a new ally screen with nature teaching; the Mangekyō Sharingan (abilities, Susanoo, strain, transplants, Eternal); Might Guy; weapon trails, hit reactions by strength, hand seals per jutsu, 11 new moves. |
| 3.10 | **Crew overhaul + blueprint Phase 1** ✅ (v0.10.0) | Crew jobs, behaviours, Guard/Patrol posts, crew-wide orders (K), a lesson queue and "Teach all"; meditation and nature energy; the owner's blueprint (`BLUEPRINT.md`) Phase 1: CTL/WIL stats, perception focus and the just-frame parry, the casting engine (seal phase, 80% movement, interruption and fizzle, spam fall-off, elemental clashes). Next: the owner decides character creation and the systems the blueprint would rebuild. |
| 3.11 | **Blueprint Phase 2** ✅ (v0.11.0) | Character creation on first join (looks, height, clan rolls); movement feel (slide, pivot, heavier gravity, landing recovery); no melee while sealing; gore (damage marks, severed limbs that burn away). |
| 3.12 | **Our own art** ✅ (v0.12.0) | HD 4× skins and gear in our own "raw and tough" style, start in underwear, gore v2 (blood everywhere), fire and lightning redone with 5 new jutsu. Next ideas from the owner: Wood Release at epic scale (`ART_STYLE.md`), detailed tailed beasts. |
| 3.13 | **A living world of ninjas** ✅ (v0.13.0) | Personal jutsu sets for ninjas and crew, wild ninjas (solo, pairs, squads, rogues), 7 new blades and HD weapon art, 28 new HD armor pieces. |
| later | **Squads:** recruit characters you meet, defeat or befriend while exploring; they fight beside you and feed Resolve. Then missions, clans, nature releases, dōjutsu, tailed beasts, multiplayer polish | |

## Art without an artist

- **Player animations need no textures.** They're JSON keyframes made in Blockbench, which is
  free, and combat feel lives mostly in code, animation and sound.
- Use vanilla textures and placeholders first. Make it fun, then make it pretty.
- Blockbench can also make the models and pixel-art textures. It's beginner-friendly, and we can
  learn it together when we need it.
- When we're ready: CC0 or CC-BY asset packs (always credited), or commissioning artists for key
  pieces like masters and signature weapons.
