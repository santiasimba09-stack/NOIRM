---
name: noirm-entity-design
description: Design a new NOIRM creature, summon or boss before any model, texture or code is made - canon research (manga first), anatomy and joint plan, silhouette, scale, personality, mood states and the brief the texture and animation skills build from. Use whenever NOIRM needs a new or reworked entity from Naruto (tailed beasts, toads, snakes, slugs, ninken, hawks, Pain's summons, Susanoo, Gedo Mazo, puppets, Zetsu, giant jutsu) or an original lore-fitting boss, even if the request only says "add Gamabunta" or "make Kurama look real".
---

# NOIRM entity design

This is step 1 of 3. It produces an **entity brief** (`docs/entities/<id>.md`). After it come
`noirm-entity-textures` (the PNG and `.bbmodel`) and `noirm-entity-animation` (the rig, the
procedural motion, the Java entity). Don't model or paint before the brief exists: a creature feels alive
because its shape, weight and behaviour come from one idea. They can't be bolted on afterwards.

Read first, in the repo: `CLAUDE.md`, `docs/ART_STYLE.md` (the ink style, which all art follows),
`docs/POWERS_DESIGN.md` (the tailed-beast section) and the existing skills `add-entity` and `blockbench-model`.
Never open or copy anything from `reference/nydo/` art (All Rights Reserved).

## The owner's standing answers (2026-11-26)
- Forge 1.20.1, **vanilla rendering only**: our own `BbModels` / `BakedBbModel` loader, no GeckoLib.
- Entities: **summons & beasts and bosses**, i.e. every Naruto creature, plus original bosses.
- Canon: **manga first**. Anime and databooks only fill gaps. No Boruto unless asked.
- Scale: **configurable** per entity. Texels: **64 px per block** at the authored size.
- Must feel alive: idle life, reactive motion, physical secondary motion, mood states, idle AI behaviours.
- Effects: glowing eyes (emissive), chakra aura particles, the summoning smoke poof.
- Performance: **balanced**, with LOD. The owner's PC is a Ryzen 5 5600G with an RX 6500 XT (4 GB VRAM) at 165 Hz.

## 1. Canon research (accuracy is the point)
Our art is our own drawing, but the creature must be recognisable to a fan from its **canon anchors**:
the features a fan would name if asked to describe it.
1. Gather anchors: body plan, number of tails/limbs/eyes, colours, markings, worn items, size relative to
   others, elemental nature, personality, how it moves and fights, and its summoner or jinchūriki.
2. **Verify every anchor** with a web search (Narutopedia / the manga chapter where it appears). Use the
   `grounded-research` skill when available. Record the source next to each anchor in the brief. If the
   manga and the anime differ, take the manga and note the difference. If something can't be confirmed,
   write "unverified" and ask the owner. Don't invent it.
3. The roster below is a **starting checklist**. Its details are the points to check, not the final word.

### Roster (verify before use)
**Tailed beasts** (boss and partner forms; tails = power tier, as in `TailedBeast.scaleFor`)
| # | Name | Body plan & anchors to check | Nature / tie |
|---|---|---|---|
| 1 | Shukaku | tanuki, sand-coloured body covered in dark blue-indigo seal markings, one thick tail, odd star-like pupils | sand, wind; Gaara |
| 2 | Matatabi | big cat of blue-black flame, two tails, mismatched eyes | fire; Yugito Nii |
| 3 | Isobu | turtle/shellfish, spiked coral-like shell, three tails, crustacean limbs | water, coral; Yagura |
| 4 | Son Gokū | ape, red fur, four tails, muscular, lava | lava (fire+earth); Rōshi |
| 5 | Kokuō | horse-dolphin hybrid, white, horns, five tails | boil/steam; Han |
| 6 | Saiken | huge pale slug with six tails, bubbles and acid | bubbles, corrosion; Utakata |
| 7 | Chōmei | horned beetle, six wings and one tail (wings count as tails) | scales/powder, flight; Fū |
| 8 | Gyūki | ushi-oni: ox head and horns on a body with eight octopus-tentacle tails | ink, Tailed Beast Bomb; Killer B |
| 9 | Kurama | red-orange fox, nine tails, long rabbit-like ears, red slit-pupil eyes, lips/mask markings | chakra, sensing hate; Naruto |
| 10 | Ten-Tails | its forms (husk, mature, Shinju); one ripple+tomoe eye | everything; Madara / Obito |

**Summon clans**
- **Mount Myōboku toads:** Gamabunta (chief: huge, rust-red, scarred eye, kiseru pipe, tantō, vest), Gamaken
  (armour, shield and jitte, clumsy and polite), Gamahiro (two swords), Gamakichi and Gamatatsu (young),
  Fukasaku and Shima (tiny elder sages), the Great Toad Sage, Gamatora (scroll keeper), Gerotora (Kushina's seal
  key toad), the toad sage-mode fusion, and gate/stomach toads (Toad Mouth Trap, Gamaguchi).
- **Ryūchi Cave snakes:** Manda (giant purple snake, arrogant), Aoda (Sasuke's loyal blue-green serpent), the
  White Snake Sage, Kabuto's Manda II, the eight-headed Yamata no Jutsu, Orochimaru's sleeve snakes.
- **Shikkotsu Forest slugs:** Katsuyu (white with blue stripes; it splits into a swarm of small slugs to heal).
- **Ninken:** Kakashi's eight (Pakkun the pug with his vest, Bull, Urushi, Shiba, Bisuke, Akino, Ūhei, Guruko),
  Akamaru (already the hound system, `docs/INUZUKA.md`; reuse it, don't duplicate it).
- **Other summons:** Enma the Monkey King (turns into the Adamantine staff), Kamatari (Temari's one-eyed weasel
  with a scythe), Garuda (Sasuke's hawk), Itachi's crows, Ninkame (Gai's turtle), Rashōmon gates, Hiruzen's
  and Jiraiya's specials.
- **Pain's Animal Path:** the giant centipede, the crustacean, the drill-beaked bird, the chameleon, the
  rhinoceros, the giant panda, the multi-headed dog that splits when struck. All wear Rinnegan eyes and black
  receivers. Check the full list.
- **Akatsuki and war creatures:** Gedō Mazō (the statue: stitched eyes, hands for the beasts), Sasori's puppets
  (Hiruko, the Third Kazekage), White Zetsu clones and the spore swarm, Kakuzu's masked hearts, Deidara's clay
  birds and C-series, Samehada as a living sword.
- **Jutsu giants:** Susanoo (rib cage → skeletal → humanoid → armoured → complete, per user and colour:
  Itachi red with the Yata Mirror and Totsuka Blade, Sasuke purple, Madara blue, Shisui green, Kakashi blue),
  Wood Dragon, Wood Golem, the True Several Thousand Hands, Kurama chakra mode.

**Original bosses** must pass a lore test: which village, clan or nature they come from, why they exist, and
which canon elements they echo without copying a character. Write a 3-line legend first.

## 2. Anatomy & articulation plan
The plan is what makes limbs articulate. Design the skeleton like a creature, not a statue.
- **Bone tree:** list every group (bone) with its parent and pivot point, and say where the pivot really is: a
  shoulder pivots at the top of the arm, a jaw at the back of the skull, a tail segment at its front edge.
  Wrong pivots are the #1 cause of stiff models.
- **Joints per limb:** at least 3 for walking legs (upper, lower, foot/paw), and digitigrade limbs for cats,
  foxes and dogs. Toads get a folded back leg (thigh, shin, long foot) and a throat sac bone. Snakes and tails
  get **chains** of 6–14 segments that shrink towards the tip. Tentacles get 5–8 segments each.
- **Faces that act:** jaw, separate eyelids (upper and lower), a brow bone if it emotes, and ears with a tip
  bone. Whisker or lip bones where canon shows expressive mouths (Kurama's snarl, Gamabunta's pipe).
- **Soft parts** become their own bones so secondary motion can move them: throat sac, belly, cheeks, manes,
  fur tufts, cloth (vests, haori), rope belts, armour plates.
- **Naming:** lower_snake_case, sides `_l` / `_r`, chains `tail1_3` (tail 1, segment 3). The animation skill
  finds bones by these names.
- **Budget (balanced LOD):** normal summons ≤ 60 bones / 250 cubes; bosses ≤ 120 bones / 500 cubes. Spend the
  cubes on the silhouette and the face, not on hidden inner parts.

## 3. Silhouette, scale and proportions
- Sketch the silhouette in words from front, side and top. Then check it against canon: could a fan
  name it from a black shadow alone? That's the readability test from `ART_STYLE.md` ("shapes read from far away").
- **Authoring size vs. game size:** model at a manageable authored size (normal summons at real size; giants
  authored 3–6 blocks tall), then scale in game. Record:
  - `authoredHeightBlocks`, `defaultScale`, `minScale`, `maxScale` (config);
  - the canon size note (e.g. "Gamabunta towers over buildings").
  The configurable scale goes in `NoirmConfig` (see the animation skill).
- Above about 6 blocks of hitbox, plan **multi-part hitboxes** (head, body, tails) instead of one huge box.
  One huge box breaks pathfinding and lets attacks hit thin air.

## 4. Life: personality → behaviour → motion
Write these four tables in the brief. The animation skill turns them into code.
1. **Personality** (3–5 words from canon): Gamabunta "gruff, proud, protective, smoker"; Manda "arrogant,
   hungry, sly"; Kurama "contemptuous, then warm, explosive".
2. **Mood states** (at least: calm, alert, angry, tired or hurt, plus any unique ones such as "subdued" for beasts or
   "chakra cloak" for jinchūriki forms): for each mood give the posture (head height, spine curve, tail
   carriage, ears), the speed multiplier, the breathing rate, the eye openness and the triggers that switch in and out.
3. **Idle life list:** at least 6 small things it does when nothing happens. They come from the animal and the
   character: blinking, breathing, ear flicks, licking a paw, tail curl, scratching, a pipe puff with a smoke
   ring, a tongue flick, sniffing the ground, shifting weight, yawning, settling down to lie. Give each a rough
   frequency.
4. **Reactions:** what it looks at (players, the summoner, loud hits), how it flinches (by size: small ones jump,
   giants rock), its intimidation display and its death or dismissal (smoke poof for summons).

## 5. Effects plan
- **Emissive parts:** eyes (Rinnegan, beast eyes, Sharingan on Susanoo), chakra markings, lava cracks. These are
  painted in a separate `_glow` texture by the texture skill.
- **Aura:** the particle type and colour (use `vfx/Vfx` helpers and `VfxKind`), where it spawns (bones), its density per
  mood, and the LOD cut-off.
- **Summon and dismiss:** smoke poof size (scales with the entity), the seal or circle and the sound cue.

## 6. The brief (write it to `docs/entities/<id>.md`)
```
# <Name> (<id>)
Source anchors: <anchor> — <chapter/page or link> (verified | unverified)
Manga vs anime notes:
Role: summon / beast / boss (phases?) — summoner/jinchūriki — clan/village
Legend (originals only):
Bone tree: <indented tree with pivots in model pixels>
Silhouette: front / side / top
Size: authored <h> blocks; default scale <s> (min <a>, max <b>); hitbox parts
Palette: base, shadow, light, accent, glow (hex)
Personality:
Mood table: | mood | posture | speed | breath | eyes | enter/exit |
Idle life: | action | how often | bones used |
Reactions:
Effects: emissive / aura / summon poof
Attacks & AI (boss phases):
Open questions for the owner:
```
Add the doc to `docs/INDEX.md`. Show the owner the brief and the open questions **before** moving on, and
wait for their answers. Then call `noirm-entity-textures`, then `noirm-entity-animation`.
