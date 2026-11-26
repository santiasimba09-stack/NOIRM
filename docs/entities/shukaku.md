# Shukaku, the One-Tail (shukaku)

Step 1 brief (skill `noirm-entity-design`). It is the look and behaviour of beast #1 (`TailedBeast` with 1 tail);
the zoglin placeholder stays for beasts 2–9.

## Source anchors
Checked 2026-11-26 on Narutopedia ([One-Tailed Shukaku](https://naruto.fandom.com/wiki/One-Tailed_Shukaku),
[Drilling Air Bullet](https://naruto.fandom.com/wiki/Drilling_Air_Bullet)). Chapter numbers: sources disagree (134 or 135
for the full debut, Gaara vs Naruto and Gamabunta), so **unverified**.

| Anchor | Source | Status | In the model |
|---|---|---|---|
| A tanuki (raccoon dog), standing upright on two legs, a huge round belly | Narutopedia, Appearance | verified | ✓ biped, belly is the biggest mass |
| Sandy-brown body | Narutopedia | verified | ✓ base `#C9A46A` (pushed down a step) |
| Cursed-seal markings over face, body and tail: black in the manga, dark blue in the anime | Narutopedia | verified | ✓ indigo-black `#2A2F55` (between the two) |
| Black sclera, yellow iris, black four-pointed star pupil with four dots around it | Narutopedia | verified | ✓ painted at full texel density, glowing |
| Dark rings around the eyes (like Gaara's) | Narutopedia art; the "insomnia" link is fan reading | verified look | ✓ |
| A jagged, concave mouth with no tongue | Narutopedia | verified | ✓ zig-zag teeth, dark inside, no tongue |
| One thick tail with markings | Narutopedia | verified | ✓ 6-segment chain, bands + crescents |
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
  body (0,13,0)              pelvis; tips forward when angry
    belly (0,18,-1)          breathing scale
    chest (0,27,0)
      head (0,32,-1)         skull pivots on the neck top
        jaw (0,34.5,-7)      hinge at the back of the mouth
        brow (0,40,-9)       angry: down
        eye_l / eye_r        flat panels on the face (painted)
        lid_l (3.75,40.6,-10.4) / lid_r   pivot at the top of the eye, scaleY 0.15 open → 1 closed
        ear_l (5,43,-2) → ear_l_tip (6,48,-2) ; ear_r mirrored
      arm_l (9,29,-1) → forearm_l (10.5,21.5,-2) → hand_l (10.5,15,-3)   ; arm_r mirrored
    leg_l (5,11,1) → shin_l (5.5,6,1) → foot_l (5.5,1.5,0)               ; leg_r mirrored
    tail1 (0,11,7) → tail2 … tail6   each 5 px long, 8–15 % thinner per segment
```
33 bones, about 45 cubes (budget: ≤ 60 bones / 250 cubes).

## Silhouette
- Front: a pear — small pointed ears, a wide head with a split grin, a huge round belly, stubby legs, arms held out
  from the belly with claws.
- Side: belly bulging forward past the feet, short neck, the tail sweeping back and curling up behind like a big
  question mark, taller than the head at the tip.
- Top: a round body with one thick tail.
Readable from the shadow alone: belly + one big curled tail + pointed ears.

## Size
Authored 2.8 blocks tall (ear tips at 45 px). Hitbox 1.3 × 2.6 blocks × `creatures.shukakuScale`
(default 1.3 → 1.7 × 3.4; min 0.5, max 4). Canon Shukaku is building-sized; the default stays partner-sized so it
fits the arena fights the beast system was tuned for. Above about 6 blocks (scale > 2.3) it would need multi-part
hitboxes (not done yet; open question).

## Palette (hex)
| Material | ink | shadow | base | light | highlight |
|---|---|---|---|---|---|
| sand fur | `#2A1C14` | `#8E6A45` | `#C9A46A` | `#E2C48C` | `#F2E2BC` |
| seal markings | `#14162A` | `#1E2240` | `#2A2F55` | `#3B4373` | – |
| mouth inside | `#140A0A` | `#2A1212` | `#3A1818` | – | – |
| teeth / claws | `#3A3226` | `#BDB49A` | `#E6E0CC` | `#F8F4E8` | – |
| eyes | sclera `#0E0C10`, iris `#E8C030`, iris light `#FFE680`, pupil `#0E0C10` | | | | |
| glow | iris `#FFD84A` (eyes only) | | | | |

## Personality
Proud, loud, short-tempered, bloodthirsty, childish.

## Moods
| mood | posture | speed | breath | eyes | enter / exit |
|---|---|---|---|---|---|
| calm | upright, belly out, tail curled up, ears relaxed | 1.0 | slow (0.25 Hz) | open, half-lid now and then | no target and no player within 12 blocks for 30 s |
| alert | head up, ears forward, tail raised | 1.0 | 0.35 Hz | wide | a player within 12 blocks, or a target |
| angry | body tipped forward, head low, brow down, ears back, arms out, tail stiff | 1.15 | fast (0.6 Hz) | narrowed | it was hurt in the last 3 s, or its target is in reach + 3 |
| manic | upright, rocking, jaw open in a grin, tail whipping | 1.2 | fast | wide | its target is below 30 % health (Shukaku smells blood) |
| tired | slumped forward, arms hanging, tail dragging, ears drooping | 0.75 | deep, slow | heavy-lidded | below 35 % health |
| subdued | lying on its side, eyes shut, tail limp | 0 | very slow | closed | the existing subdue (10 % health) |
Each mood holds at least 30 ticks before it can change.

## Idle life
| action | how often | bones |
|---|---|---|
| breathing (2 waves, 1 : 0.37) | always | belly, chest |
| blink (sometimes double) | every 2–6 s, seeded per beast | lid_l, lid_r |
| ear flick | every 3–8 s, one ear | ear_l/_r, tips |
| tail sway + curl springs | always | tail1–6 |
| belly scratch | 15–30 s | arm_r, forearm_r, hand_r, head tilt |
| sand sift: scoops sand and lets it trickle (sand particles) | 20–40 s | arms, hands, head down |
| laugh (rocks back, jaw open, belly shakes) | 20–40 s | body, head, jaw, belly |
| yawn → shakes it off (it hates sleep) | 30–60 s | jaw, head, lids |
| look around | 10–20 s (vanilla goal) | neck/head/eyes split |

## Reactions
- Looks at the nearest player (vanilla look goal), its target in a fight; the angle splits chest 25 % / head 75 %, eased.
- Hurt: a slow rock away from the hit and a head jerk, eyes shut briefly (it is big).
- Death: tips over backwards with a chakra burst (existing).
- Intimidation when it turns angry: jaw wide, arms spread (procedural roar), sand burst.

## Effects
- Emissive: the yellow irises (`shukaku_glow.png`), brighter when angry or manic, off when subdued.
- Aura: drifting sand dust (tan dust particles) instead of the orange beast dust; denser when angry. LOD-capped.
- Summon/dismiss: it spawns with a sand-coloured smoke poof and a scale-in (0.6 → 1.05 → 1.0 over 8 ticks).

## Attacks & AI
- Existing beast style: claws and slam (wind-ups from `BeastFightGoal`) and the Tailed Beast Ball.
- Signature: **Drilling Air Bullet**. At 5–20 blocks it stops, drums its belly twice (14-tick wind-up the player can
  read and hear), then fires `futon/vacuum_bullets` (three wind bullets, charge 1.5). Shukaku has wind affinity.

## Open questions for the owner
1. Default size: partner-sized (scale 1.3, 3.4 blocks tall) or canon-giant (needs multi-part hitboxes)?
2. Markings: indigo-black (our mix) or pure manga black or anime blue?
3. Should Shukaku talk ("ore-sama" taunts in chat) when it turns manic?
4. Sand moves: a sand-wave or sand-bullet attack next?
