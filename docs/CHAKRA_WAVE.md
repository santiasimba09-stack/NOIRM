# The chakra wave: jutsu, the workshop, solid-flame chakra (owner's brief, 2026-11-04)

The owner's request: "work on the particles once again… solid while also going into a lighter tone
on the edges… animated like flaming chakra, for biju auras, Susanoo, etc. Make the ribs, cloak feel
more solid… more jutsu with these characteristics, more variety for the natures left apart, and a
way to get new chakra transformations alone and develop jutsu." All answers below are the owner's;
ask before changing them.

**Order (owner):** first the **jutsu wave**, then the **jutsu workshop** and solo transformations,
and last the **solid-flame chakra**. The look for the jutsu comes from the owner's Wood, Fire and
Lightning reference panels (mood only, see `ART_STYLE.md`).

## Part 1: the jutsu wave
- **Which natures:**
  - every combined nature: Ice, Lava, Boil, Storm, Wood, Explosion, Scorch, Dust, Magnet;
  - Yin, Yang and Yin-Yang;
  - Medical;
  - Wind and Earth (5 each today, against Fire's 10).
- **How many:** **4–5 new jutsu per nature**, across ranks (a basic, a tool, an A-rank attack, an
  S-rank signature).
- **Which jutsu:** a **mix of canon and original** designs: the famous ones where they exist, our own
  to fill the gaps.
- **Wood Release, epic, now:** wood clones, the Wood Dragon, a thousand-hands-scale giant,
  Deep Forest Emergence, domes and binds. Raw grey-green wood, ink grain, dust and debris, and an
  overwhelming scale.

## Part 2: solo transformations and the jutsu workshop
- **Combined natures alone:** fuse two natures you have mastered into their combined nature. It is
  **very hard and rare**: both natures mastered, high CTL, a long trial of several in-game days, and
  it can fail. Clans still get theirs for free.
- **Shape transformation:** chakra shapes are trained alone as their own skill (ball, blade, spear,
  wave, wall, beast…), and **shape + nature fusion** (a wind Rasengan, for example) is the top step.
- **Yin, Yang and Yin-Yang alone:** a solo path to each.
- **Drills:** keep today's speed and make them clearer to follow.
- **Jutsu workshop:**
  - a screen where you combine a nature, a shape and size/speed/power trade-offs, then name the jutsu;
  - you practise it before it works reliably;
  - its cost and seals come from your choices.
- **Power:** your own jutsu can **equal or surpass canon**. They are kept in check by a **big chakra
  cost plus backlash**, and by **stat requirements** (Reserves, CTL, nature mastery).
- **Sharing:** teach your creations to crew mates, and **write scrolls** of them to give or sell.
  NPC ninjas don't get them.
- **Forms stay special:** the workshop never makes armour, chakra-beast or avatar forms.

## Part 3: solid-flame chakra
- **Look:**
  - a **real solid shell** around the body, opaque and lit like a surface;
  - **animated flame tongues** rising off its outline, with tips that fade to a lighter tint;
  - particles only for sparks and embers.
- **Motion:** **rising, wild flames** that flow faster when you run or attack, and flare when you're
  hit or cast.
- **Applies to:**
  - the tailed-beast cloak, tails and chakra arms;
  - **every Susanoo stage**;
  - Sage Mode and the Eight Gates;
  - jutsu effects where it fits (fire, lightning, the Rasengan…).
- **Susanoo:** **semi-opaque bone**. The ribs and armour are thick glowing chakra you can just see
  through, with edges that burn in a lighter tone.
- **Colours:**
  - beast cloaks are their beast's colour;
  - **each Susanoo user has their own colour**, rolled with their Mangekyō;
  - Sage and the Gates have fixed colours.
- **Graphics setting:** Low, Medium or High in the mod config, with High as the default.

### Wave share: Scorch, Dust, Magnet, Wind, Earth (23 jutsu)
Code: `jutsu/KekkeiTechniquesB` (Scorch, Dust, Magnet, and the shared `ward_shell`),
`jutsu/BasicNatureTechniques` (Wind, Earth), the looks in `vfx/ScorchDustMagnetVfx` and `vfx/WindEarthVfx`,
and `jutsu/RockAndCubeDisplays` (solid shapes made of vanilla block displays: the Dust cube, the falling
mountain). Tests: `gametest/ChakraWaveBGameTests`. Wind's palette is now pale grey-white, not green.

| Nature | Jutsu (rank, behaviour) |
|---|---|
| Scorch | Heat Mote (D, heat_orbs) · Scorching Halo (C, ward_shell) · Swarm of Suns (A, heat_orbs) · Extreme Steaming Murder (S, steam_mummify) |
| Dust | Detachment Needle (D, beam) · Detachment Shell (C, ward_shell) · Cone-Shaped Detachment (B, detach_cone) · Expanding Detachment Cube (A, detach_cube) |
| Magnet | Lodestone Pull (D, magnet_pull: metal armour pulls harder) · Iron Sand Shield (C, ward_shell) · Giant Arm (B, sand_arm) · Gathering Needle Rain (A, needle_rain) · World Order (S, sand_tide) |
| Wind | Wind Blade (D, wind_crescent) · Vacuum Blast (C, projectile) · Gale Wall (B, ward_shell: turns jutsu back) · Vacuum Wave (A, a 360° ring of crescents) · Raging Tornado (S, tornado) |
| Earth | Headhunter (D) · Rock Hounds (C) · Earth Flow Rampart (B) · Added-Weight Rock (A: you light, the foe heavy) · Falling Mountain (S) |

Scorch, Dust and Magnet D-rank jutsu are starters for their clans; everything else unlocks by mastery
from an older jutsu of the same nature. NPC ninjas use all of them (`KekkeiTechniquesB.NPC_RANGED` / `NPC_DEFENSIVE`).
