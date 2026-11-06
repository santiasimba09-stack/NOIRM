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
Screenshots (client smoke, first person unless noted): `docs/screenshots/cw-scorch-steaming-murder-{latched,flare}.jpg`,
`cw-dust-expanding-cube{,-growing}.jpg`, `cw-magnet-world-order-{gather,burst}.jpg`, `cw-wind-raging-tornado.jpg`,
`cw-wind-vacuum-wave.jpg` (third person), `cw-earth-falling-mountain-{gather,impact}.jpg`.

## Part 2 built: solo transformations and the jutsu workshop
Code: `arts/` (`ChakraShape`, `ShapeTraining`, `FusionTrial`, `SpiritPaths`, `ArtsData` saved in
`ShinobiData.arts()`, `ArtsSync`, `ArtsEvents`), `workshop/` (`JutsuDesign`, `Workshop` (the rules and
numbers), `WorkshopRegistry` (creations as runtime jutsu), `CreationBook` (`ShinobiData.creations()`),
`WorkshopItems`, `WorkshopPackets`), the screens `client/TrainingScreen`, `client/WorkshopScreen`
(the J screen's new **Training** and **Workshop** tabs), `client/ArtsHud`, commands in
`command/ArtsCommand` (`/noirm help arts`). Tests: `gametest/ChakraWave2GameTests`.
Network: protocol **24** (the powers sync carries shapes, fusions, the trial and creations; three new
packets). Every number below is a constant at the top of its class.

### Fusing two natures alone (`FusionTrial`)
- **To begin:** own both parts, master each (a jutsu of it at ★4), CTL 40. Start from the nature
  tree ("Begin the fusion trial"), the Training tab or `/noirm fusion start <nature>`. Clans born to a
  combined nature still get it free.
- **Stages, while meditating (N):** Attune to each part in its drill setting (1,200 s = one in-game day
  each) → Blend where two parts' settings meet at once (1,800 s; Dust ×1.5) → Proof: cast a jutsu of
  each part within 3 s of each other, 5 times. About 3.5 in-game days of focus in all.
- **Failure:** every 300 s of the blend: 15% chance, −0.2% per CTL point above 40, −0.1% per WIL point
  above 10 (between 4% and 25%). A failure loses half the blend, 30% of max health (never the last
  point) and half your chakra.
- **Reward:** the nature at your weaker part's strength (at most 1.0) and its starting jutsu.
- The old instant "Unlock" at ★2 is gone for elemental mixes (it was the earlier placeholder).

### Shapes (`ShapeTraining`, `ChakraShape`)
Eight shapes: Ball 球, Blade 刃, Spear 槍, Wave 波, Wall 壁, Beast 獣, Field 陣, Bind 縛. Hold the
shape's focus item and meditate (ball: slime ball, blade: flint, spear: arrow, wave: water bucket,
wall: brick, beast: bone, field: amethyst shard, bind: string).

| Stage | Takes | Drain | Rule |
|---|---|---|---|
| 1 Form (the ball's "Rotation") | 600 s | 0.2 chakra/s | — |
| 2 Power | 600 s | 1.5 chakra/s | a canon jutsu of the shape cast in this stage adds 20 s |
| 3 Containment | 900 s | 1 chakra/s | only above 50% chakra; pace = CTL/20 (×0.5 to ×2) |
| + Nature fusion | 1,200 s | — | stage 3, a nature at ★3, meditate in that nature's drill setting |

Each stage gives canon jutsu of that shape +5% power and −4% chakra (up to +15% / −12%), and raises
the workshop's rank cap for that shape: stage 1 → C, 2 → B, 3 → A; fused with the nature → S.

### Yin, Yang, Yin-Yang alone (`SpiritPaths`, in `NatureDrills`)
Same drill speed as the elements (3,600 s), with teachers and the hidden-nature bonus working the same:
- **Yin:** needs WIL 20. Meditate in darkness (light 7 or less) or within a minute of a genjutsu (cast
  or broken with Kai). WIL 40: ×1.5.
- **Yang:** needs Vitality 20. Meditate in daylight under open sky or within a minute of landing a hit.
  Vitality 40: ×1.5.
- **Yin-Yang:** once both are held with a jutsu of each at ★2; meditate anywhere. Comes at your weaker
  half's strength.

### The workshop (`Workshop`)
Pick a nature you own, a trained shape, and size / speed / power / range (1–5 each), then name it.
- **Weight** = size + speed + 2 × power + range (5–25). **Rank:** D ≤7, C ≤10, B ≤14, A ≤19, S above.
- **Chakra** = 6 × (weight/5)^1.45, then +25% per point above 19 (weight 15 ≈ 29.5, 25 ≈ 155).
- **Cooldown** = (40 + 1.2 × weight²) × (1.15 − 0.075 × speed) ticks.
- **Seals:** 1 + weight/4 (2–7): the nature's opening seal, seals picked from the choices (the same
  choices always give the same seals), the shape's closing seal.
- **Power:** base damage × (0.6/1.0/1.4/1.9/2.5 by power); a full-power ball does 28 (the Rasengan 14).
- **Backlash:** above weight 17, 3.5% of max health per point per cast (weight 25: 28%), never lethal.
- **Stat budget:** 6 + 0.15 per Reserves point above 10 + 0.2 per CTL point above 10 + 1.5 per ★ of
  your best jutsu of the nature. A design heavier than your budget can't be made, and a learned one can't
  be cast (`TOO_DEMANDING`).
- **Practice:** misfire chance 55/40/25/12/5/0% at ★0–★5: the chakra is spent, half the cooldown runs,
  and it still counts as practice.
- **Behaviours:** ball → `rasengan` (charged, in the nature's colours), blade → `wind_crescent`, spear →
  `beam`, wave → `wave`, wall → `ward_shell`, beast → `projectile` (dragon look, homing), field → `nova`,
  bind → `bind`. Fire-family natures add burning, ice slows, lightning and storm stun briefly.
- **No forms:** armour, cloaks, avatars, Susanoo or modes are refused by name, and no shape uses a form's
  behaviour.
- **Limits:** 12 creations of your own, 24 in all.
- **Sharing:** teach a crew mate from the ally screen once the creation is at ★2; they keep its design
  in their save. Write a scroll at ★2 (the author only): a blank scroll (paper + paper + stick) or a
  sheet of paper, an ink sac, and half the jutsu's chakra cost (at least 10). Reading the scroll teaches
  that exact jutsu to anyone who owns its nature and passes the rank gate. NPC ninjas never roll
  creations, and the Sharingan can't copy them.
