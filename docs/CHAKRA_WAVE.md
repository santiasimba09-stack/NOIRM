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

## Part 1, share: Wood Release (built)
Code: `jutsu/WoodTechniques` (behaviours), `jutsu/WoodDragon` and `jutsu/WoodColossus` (model
entities), `entity/WoodClone`, `vfx/WoodVfx`, `client/WoodReleaseRenderers`. Art comes from
`tools/woodgen/gen_wood.py` (grey-green bark with ink grain, our own; preview in
`docs/screenshots/cw-wood-textures.png`). Wood now grows as its own block, `noirm:mokuton_wood`, which
Wood Binding and Deep Forest Spears use too.

| Jutsu | Rank | What it does |
|---|---|---|
| Root Snare (`mokuton/root_snare`) | C, starter | Roots run under the ground to the enemy you look at and burst up round their legs: a hit and a 2 s hold, with real roots for a moment. |
| Wood Clone (`mokuton/wood_clone`) | B, after Root Snare ★2 | Two bark clones carved in your likeness step out of the ground in a fan behind you. They fight and cost no share of your chakra. Whoever breaks one is caught by its roots, and your Substitution swaps you with a clone instead of a log. |
| Wood Locking Wall (`mokuton/wood_locking_wall`) | B, after Wood Binding ★2 | A dome of wood (radius 3.5) closes over you from the ground up, with slits at eye height, and splinters away after 5 s. |
| Wood Dragon (`mokuton/wood_dragon`) | A, after Wood Binding ★3 | A long bark-scaled dragon with a horned head and an open jaw bursts out of the ground and coils round the target from the feet up, sized to the target (a tailed beast gets a bigger one). It holds the target 3.5 s and drinks 6 chakra a second, half of which flows back to you, then crushes the target. |
| Deep Forest Emergence (`mokuton/deep_forest_emergence`) | S, after Deep Forest Spears ★3 | The ground heaves, then 12 huge twisting trunks burst out one after another across a radius of 11 or more. They throw up whoever stands on them and tangle everyone in the forest. The trunks are real blocks (at most 1,300) that regrow away with the terrain. |
| Thousand-Arm Colossus (`mokuton/thousand_arm_colossus`) | S, after Deep Forest ★2 | A 17-block wooden figure (our own design: a hooded stump head, a crown of branches, hands clasped in a seal, a halo of 30 arms) rises behind you. 16 fists crash down round the target, then it sinks away. It's one model entity, not blocks. Timing: it rises for 1.5 s, then a fist lands every 0.25 s (the first at 1.9 s, the last at about 5.7 s), and it's gone at about 7.6 s. |

NPC ninjas of Wood use them all (`NinjaFightGoal`: ranged, opener and defensive sets).
Screenshots: `docs/screenshots/cw-wood-dragon.jpg`, `cw-wood-deep-forest.jpg`, `cw-wood-colossus.jpg`.
Open questions for the owner: is the colossus' look right (it's placeholder-simple boxes)? Should
the forest's trunks stay longer than the terrain regen (about 45 s by default)?
