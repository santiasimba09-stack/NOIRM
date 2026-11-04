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

## Part 1 share: Yin, Yang, Yin-Yang, Medical (done)
Code: `jutsu/SpiritTechniques` (Yin, Yang, Yin-Yang), `jutsu/MedicalTechniques`, looks in
`vfx/SpiritVfx`, the player's view in `client/SpiritVision` (packet `SpiritPackets.Vision`, protocol 23).
Tests: `gametest/SpiritGameTests`.

**Looks:** Yin is solid ink (dark, unlit puffs) with violet light at the rim; Yang is a solid
golden-white body glow; Yin-Yang keeps white light and black ink apart in swirls (never grey);
Medical is soft green and slow; poisons are sickly violet with green specks.

**Genjutsu counterplay:** a strong mind (perception, and WIL for players) may resist; an open
Sharingan sees straight through; **Kai** breaks it, even while stunned, on yourself or a crew mate
within 5 blocks; a friend's slap or use (right-click), or an NPC crew mate standing beside you for a
second, wakes you; NPCs try their own Kai. A caught player sees a violet tint, ink at the edges and a
slight view sway (no blindness).

| Jutsu | Rank | What it does |
|---|---|---|
| Genjutsu Release (Kai) `universal/genjutsu_release` | E | Breaks a genjutsu on you or a nearby crew mate; works while stunned. Unlocks from Substitution ★1. |
| False Surroundings `yin/false_surroundings` | B | The target sees you elsewhere (an ink phantom): their next 3 attacks on you hit a false image; creatures turn on their own kind. |
| Shadow Neck Bind `yin/shadow_neck_bind` | B | Channel: a shadow runs to the target, holds them, ink hands climb to the throat and choke. |
| Shadow Stitching `yin/shadow_stitching` | A | Up to 4 shadow tendrils race over the ground and burst up as ink spikes, piercing and pinning. |
| Tree Binding Death `yin/tree_binding_death` | S | A phantom tree holds the victim 3 s, then your phantom strikes from behind (or at once if you hit them). |
| Rapid Regeneration `yang/rapid_regeneration` | C | Instant heal plus regeneration, the body glowing gold. |
| Vitality Mode `yang/yang_vitality` | B | Mode: stronger, tougher, a little faster; solid gold glow. |
| Super Expansion Fist `yang/super_expansion_fist` | A | The arm swells into a giant gold fist that slams down: crater, shake, hit-stop. |
| Life Force Conversion `yang/life_force_conversion` | B | Spend up to 8 health for chakra (×3). |
| One's Own Life Reincarnation `yang/life_reincarnation` | S | Brings back a crew mate who fell nearby (within 5 min), at the cost of 60% of your health, all chakra and 1 min of weakness. Refunded if no one can be saved. |
| Renewal `yin_yang/renewal` | B | Heal and cleanse every harmful effect. |
| Attraction `yin_yang/attraction` | B | Pull the target to you and stun them. |
| Repulsion `yin_yang/repulsion` | A | Throw every enemy around you away and turn projectiles back. |
| Chakra Hawks `yin_yang/chakra_hawks` | A | Three white-and-black chakra hawks hunt your enemies for 10 s. |
| Equilibrium Collapse `yin_yang/equilibrium_collapse` | S | A black-and-white sphere pulls everything in for 1.5 s, then bursts: big damage, crater. |
| Deep Mend `medical/deep_mend` | A | Channel: strong healing on the friend you look at (or you), drawing out a poison each second. Tsubaki, chūnin. |
| Scalpel Strike `medical/scalpel_strike` | B | A short lunge and a cut under the skin: slows and weakens. |
| Poison Mist `medical/poison_mist` | B | A violet cloud drifts out and hangs, poisoning enemies inside. |
| Poison Extraction `medical/poison_extraction` | C | The antidote: draws out every harmful effect from a friend or you, small heal. |
| Healing Field `medical/healing_field` | A | A green circle for 8 s that heals your side only. |
| Body Pathway Derangement `medical/body_pathway_derangement` | A | A palm that scrambles movement: a player's keys reverse for up to 4 s (high WIL shortens it, down to 40%); an NPC stumbles and loses its target. |
| Hundred Healings `medical/hundred_healings` | S | Long charge, then 30 s of self-healing and one snap-back heal when low. Tsubaki, jōnin. |

**NPCs:** Yin/Yang/Yin-Yang ninjas roll these like any jutsu; medical jutsu only go to Yang (Senju)
ninjas; Kai, Reincarnation and Life Force Conversion never go in an NPC set.

**Screenshots:** `docs/screenshots/cw-yin-1.jpg`, `cw-yin-2.jpg` (Tree Binding Death), `cw-yang-1.jpg` (Super
Expansion Fist impact), `cw-yin_yang-1.jpg`, `cw-yin_yang-2.jpg` (Equilibrium Collapse gathering, then the
burst), `cw-medical-1.jpg` (Healing Field).

## Jutsu wave, share A: Ice, Lava, Boil, Storm, Explosion (done)
Twenty new jutsu, four per nature: a C basic (given with the nature), a B tool, an A attack and an
S signature, each unlocked by mastering the one before it (★2, or ★3 for the S rank). Code:
`jutsu/KekkeiTechniquesA*` (behaviours), `vfx/KekkeiVfxA` (the look), tests in
`gametest/KekkeiAGameTests`. Three new particle sprites, drawn by
`tools/vfxgen/gen_particles_kekkei_a.py`: **crystal** (ice), **magma** (lava), **steam** (boil).

| Nature | Rank | Jutsu | What it does |
|---|---|---|---|
| Ice | C | Ice Spear | a fast crystal lance that slows |
| Ice | B | Freezing Field | frost races out from your feet; whoever it reaches is frozen and ice spikes burst up |
| Ice | A | Black Ice Dragon | a homing dragon of ice crystals that shatters and freezes the ground |
| Ice | S | Demonic Mirroring Ice Crystals | a dome of real ice mirrors traps the enemy; senbon fly from mirror to mirror, then a last volley and they all shatter |
| Lava | C | Magma Bullet | a fast molten bullet that sets alight |
| Lava | B | Quicklime Congealing | a lobbed glob of quicklime sets round the enemy and holds it |
| Lava | A | Magma Armour | a mode: tougher, harder hits, and anyone who strikes you is burned |
| Lava | S | Volcanic Eruption (charge) | the ground ruptures, turns to magma (it heals), and the vent rains lava bombs |
| Boil | C | Acid Mist | a cone of acid steam that scalds, weakens and eats armour |
| Boil | B | Scalding Geyser | the ground hisses (a warning), then a boiling jet throws enemies up |
| Boil | A | Boiling Vapour Armour | an unstoppable charge in a shell of steam that bowls through everyone |
| Boil | S | Scalding Pressure Dome | a dome of steam builds over the enemy, scalding, then bursts |
| Storm | C | Black Cloud | a small thundercloud that darkens eyes and snaps bolts down |
| Storm | B | Light Fang (charge) | a straight blue-black laser that pierces the whole line |
| Storm | A | Raging Thunder Tide | a wave of charged black water that stuns and carries |
| Storm | S | Laser Circus: Hundred Bends | one laser per enemy in reach, each bending through the air to its mark |
| Explosion | C | Clay Swallows | three homing clay birds that pop |
| Explosion | B | Detonating Fist | a punch that explodes on contact |
| Explosion | A | Great Clay Effigy | a huge clay figure falls on the target and explodes |
| Explosion | S | Final Art (long charge) | you become the bomb: the biggest blast in the game, then a heavy backlash (65% of your health, all your chakra, crippled for 15 s). NPCs never use it |

Looks: ice is crystalline and glinting (crystal sprites, white glints, frost light on the ground);
lava is cellular magma with black crust and black smoke, never a white flash; boil is thick
white-grey steam; storm is dark clouds and blue-black lasers with a white core; explosion is a white
flash, then fire, then smoke rings rolling out. Projectiles of these natures (also the older Ice
Needles, Lava Globs and Explosive Clay) now use these looks.
Screenshots: `docs/screenshots/cw-ice-*`, `cw-lava-*`, `cw-boil-*`, `cw-storm-*`, `cw-explosion-*`.
Shared files touched (one-line hooks): `JutsuBehaviors` (registration), `JutsuProjectile` (trail and
impact looks), `JutsuCaster` (charge-up look), `ModeSystem` (Magma Armour aura), `NinjaFightGoal`
(NPC use), `Destruction.temporary` (heal-back blocks), `VfxKind`/`VfxParticle` (three sprites),
`tools/animgen/seals.py` (seals). No packet changes.
