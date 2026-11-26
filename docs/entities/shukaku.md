# Shukaku, the One-Tail (shukaku)

Step 1 brief (skill `noirm-entity-design`). It is the look and behaviour of beast #1 (`TailedBeast` with 1 tail);
the zoglin placeholder stays for beasts 2–9.

## Source anchors
Checked 2026-11-26 on Narutopedia ([One-Tailed Shukaku](https://naruto.fandom.com/wiki/One-Tailed_Shukaku),
[Drilling Air Bullet](https://naruto.fandom.com/wiki/Drilling_Air_Bullet)). Chapter numbers: sources disagree (134 or 135
for the full debut, Gaara vs Naruto and Gamabunta), so **unverified**.

| Anchor | Source | Status | In the model |
|---|---|---|---|
| A tanuki (raccoon dog) with a huge round belly | Narutopedia, Appearance | verified | ✓ belly + chest are the biggest masses |
| Hunched and top-heavy, moving on all fours like a gorilla; long thick arms; short hind legs; rears up only to roar or swipe | owner's reference images (manga colour, anime, 3D render, figure), 2026-11-26 | owner | ✓ quadruped rig |
| Sandy-brown body | Narutopedia | verified | ✓ base `#C9A46A` (pushed down a step) |
| Cursed-seal markings over face, body and tail: black in the manga, dark blue in the anime | Narutopedia | verified | ✓ indigo-black `#2A2F55` (between the two) |
| Black sclera, yellow iris, black four-pointed star pupil with four dots around it | Narutopedia | verified | ✓ painted at full texel density, glowing |
| Dark patches around small, mean eyes | Narutopedia art; owner's references | verified look | ✓ filled black patches |
| A jagged, concave mouth with no tongue | Narutopedia | verified | ✓ zig-zag teeth, dark inside, no tongue |
| One enormous tail, as big as the body, rising from the lower back and arching up and forward over the back, made of layered sand plates each with a dark mark | Narutopedia; owner's references | verified + owner | ✓ 7 segments, 3 plates each |
| Markings: curling swirls, flame streaks, "comma" spirals, stripes across arms/legs/belly, spirals on shoulders and forehead; black eye patches | owner's references | owner | ✓ painted as 3D functions |
| Skin like packed sand or sandstone; sand pours off it | owner's references | owner | ✓ speckled grain, sand particles |
| Personality: proud ("ore-sama"), short-tempered, juvenile, bloodthirsty, loud | Narutopedia, Personality | verified | moods + idle |
| Drilling Air Bullet: pounds its belly, the pressure fires a ball of wind from its mouth; repeated drums fire several | Narutopedia, Drilling Air Bullet | verified | ✓ the `drum` action → `futon/vacuum_bullets` |
| Wind + sand; sand bullets with Gaara's sand | Narutopedia | verified | sand particles, wind affinity |
| Size: towers over trees, matches Gamabunta | manga (Gaara vs Naruto) | verified look, no number | configurable scale |
| Jinchūriki: Gaara (Sunagakure) | Narutopedia | verified | (existing jinchūriki system) |

Manga vs anime: markings black (manga) vs dark blue (anime); we use a deep indigo-black so both read.

## Role
Beast (tailed beast #1, tier 1). Fought, subdued and sealed through the existing beast systems
(`/noirm beast spawn 1`, the One-Tail spawn egg, Sealing Scroll). No boss phases (it already has the subdue stage).

## Bone tree (pixels; y up, front -z, pivots at the real joints)
```
root (0,0,0)
  body (0,15,7)              pivots over the hind legs: +x rears the front up (roar, swipe, belly drum)
    belly (0,15,-2)          breathing scale
    chest (0,26,-6)          shoulders and hump: top-heavy
      head (0,26,-16)        low, sunk between the shoulders
        jaw (0,21.5,-21)     hinge at the back of a very wide mouth
        brow (0,30,-26)      angry: down
        eye_l / eye_r        flat panels on the face (painted, alpha almond)
        lid_l / lid_r        rest above the eye, slide down to blink
        ear_l (-6.5,31,-19.5) → ear_l_tip ; ear_r mirrored (short, pointed, set to the sides)
      arm_l (-11,28,-10) → forearm_l (-11.5,16,-10) → hand_l (-11.5,3.5,-10.5) + 5 claws ; arm_r mirrored (front legs)
    tail1 (0,16,12) → tail2 … tail7   each 6 px long, ~10 % thinner, core + top/left/right plates
  leg_l (-7.5,15,8) → shin_l (-8,7,9) → foot_l (-8,2.5,8) + 4 toe claws ; leg_r mirrored (under root, so rearing keeps them planted)
```
34 bones, 83 cubes (budget: ≤ 60 bones / 250 cubes).

## Silhouette
- Front: a wide gorilla stance: two thick arms planted like pillars, a low head between big shoulders, a gaping
  jagged mouth, small ears to the sides, the plated tail fanning up behind the shoulders.
- Side: low head, high shoulders sloping down to short haunches, the belly slung low, the tail rising straight up from
  the hips and arching forward over the back like a fan of stacked slabs.
- Top: broad shoulders, a round body, the tail covering the back.
Readable from the shadow alone: the hunch + the huge arched tail.

## Size
Authored about 3 blocks long and 2.4 tall at the shoulders (the tail arch higher). Hitbox 1.35 × 2.3 blocks ×
`creatures.shukakuScale` (default 1.3 → 1.75 × 3.0; min 0.5, max 4). Canon Shukaku is building-sized; the default stays
partner-sized for the arena fights the beast system was tuned for. Above about 6 blocks it would need multi-part
hitboxes (not done yet; open question).

## Palette (hex)
| Material | ink | shadow | base | light | highlight |
|---|---|---|---|---|---|
| sand skin | `#2A1C14` | `#8E6A45` | `#C9A46A` | `#E2C48C` | `#F2E2BC` |
| belly (lighter) | `#2A1C14` | `#A4825A` | `#D9BC86` | `#ECD6A6` | `#F6EACA` |
| tail plates (sandstone) | `#241812` | `#7E5E3E` | `#B8935E` | `#D6B47E` | `#EAD3A4` |
| seal markings | `#14162A` | `#1E2240` | `#2A2F55` | `#3B4373` | – |
| mouth inside | `#140A0A` | `#2A1212` | `#3A1818` | – | – |
| teeth / claws | `#3A3226` | `#BDB49A` | `#E6E0CC` | `#F8F4E8` | – |
| eyes | sclera `#0E0C10`, iris `#E8C030`, iris light `#FFE680`, pupil `#0E0C10` | | | | |
| glow | iris `#FFD84A` (eyes only) | | | | |

## Personality
Proud, loud, short-tempered, bloodthirsty, childish: a wild, cackling beast.

## Moods
| mood | posture | speed | breath | eyes | enter / exit |
|---|---|---|---|---|---|
| calm | on all fours, head level, tail arched, ears relaxed | 1.0 | slow (0.25 Hz) | open, half-lid now and then | no target and no player within 12 blocks for 30 s |
| alert | front raised a little, head up, ears forward, tail raised | 1.0 | 0.35 Hz | wide | a player within 12 blocks, or a target |
| angry | front dipped, head low, brow down, ears back, jaw ajar, tail stiff | 1.15 | fast (0.6 Hz) | narrowed | it was hurt in the last 3 s, or its target is in reach + 3 |
| manic | head up, jaw open in a grin, tail whipping, cackles | 1.2 | fast | wide | its target is below 30 % health (Shukaku smells blood) |
| tired | head hanging, tail drooping, ears drooping | 0.75 | deep, slow | heavy-lidded | below 35 % health |
| subdued | lying on its side, eyes shut, tail limp | 0 | very slow | closed | the existing subdue (10 % health) |
Each mood holds at least 30 ticks before it can change.

## Idle life
| action | how often | bones |
|---|---|---|
| breathing (2 waves, 1 : 0.37) | always | belly, chest |
| blink (sometimes double) | every 2–6 s, seeded per beast | lid_l, lid_r |
| ear flick | every 3–8 s, one ear | ear_l/_r, tips |
| tail sway + curl springs | always | tail1–6 |
| sniff the ground (head low, quick sniffs, sweeping) | 10–25 s | head, chest, ears |
| shake off sand like a wet dog (sand burst) | 20–40 s | body, head, chest, ears |
| cackle (half rears, jaw flapping, arms flailing) | 20–40 s | body, head, jaw, belly, arms |
| yawn → shakes it off (it hates sleep) | 30–60 s | jaw, head, lids |
| look around | 10–20 s (vanilla goal) | neck/head/eyes split |

## Reactions
- Looks at the nearest player (vanilla look goal), its target in a fight; the angle splits chest 25 % / head 75 %, eased.
- Hurt: a slow rock away from the hit and a head jerk, eyes shut briefly (it is big).
- Death: tips over backwards with a chakra burst (existing).
- Intimidation when it turns angry: it crouches, then rears onto its hind legs, arms wide, mouth gaping (the roar).

## Effects
- Emissive: the yellow irises (`shukaku_glow.png`), brighter when angry or manic, off when subdued.
- Aura: sand dusting off it (tan dust) instead of the orange beast dust, denser when angry; sand pouring while it
  moves, swipes and shakes; a sand cloud when it lands.
- Summon/dismiss: it spawns with a sand-coloured smoke poof and a scale-in (0.6 → 1.05 → 1.0 over 8 ticks).

## Attacks & AI
- Movement (owner: "feel like a beast"): it walks on all fours (diagonal pairs), prowls in a circle around its prey
  while its claws recover, then charges at a gallop (1.6× speed).
- Existing beast style: claws and slam (wind-ups from `BeastFightGoal`); on screen it rears up, raises one claw high
  and rakes down. And the Tailed Beast Ball.
- Signature: **Drilling Air Bullet**. At 5–20 blocks it stops, drums its belly twice (14-tick wind-up the player can
  read and hear), then fires `futon/vacuum_bullets` (three wind bullets, charge 1.5). It rears up onto its hind legs to drum.
  Shukaku has wind affinity.

## Open questions for the owner
1. Default size: partner-sized (scale 1.3, about 3 blocks tall and 4 long) or canon-giant (needs multi-part hitboxes)?
2. Markings: indigo-black (our mix) or pure manga black or anime blue?
3. Should Shukaku talk ("ore-sama" taunts in chat) when it turns manic?
4. Sand moves: a sand-wave or sand-bullet attack next?
