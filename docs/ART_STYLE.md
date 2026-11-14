# NOIRM art style (owner's direction, 2026-10-22)

Our own designs, drawn by our generator scripts (`tools/skingen/`, `tools/gear/`, `tools/goregen/`).
Textures are **HD at 4×** (256×256 for the 64×64 skin layout, 256×128 for armor). The owner shared
reference images (kept outside the repo; they're other people's art, for mood only: never copy,
trace or sample them).

## The mood: "raw and tough"
- **Ink first.** High contrast: deep near-black shadows, hard light edges, few mid-tones. Shapes read
  from far away by their dark/light pattern.
- **Raw texture.** Gritty grain, ink scratches, dry-brush streaks and splatter instead of smooth
  gradients. Edges a little rough, never airbrushed.
- **Anatomy that cuts.** Muscle groups carved with hard shadows (chest, abs, shoulders, forearms),
  tendons and veins as thin dark lines. Lean and wiry rather than bulky.
- **Palette:** ash blacks and greys, bone/off-white highlights, desaturated skin tones, and **blood
  red** as the accent (seals, trims, blood, eyes). Chakra blue/green only for chakra effects.
- **Faces:** sharp, intense eyes, strong brows, a **soft** jaw (owner, 2026-11-12: the jaw was "way too sharp": its
  shadow shapes start low on the face and are one tone lighter). **The eye rule (2026-11-12, from the owner's eye
  references, after "too bubbly / too gentle" and then "too round"):** aggressive and raw, never round or bubbly.
  - **Shape:** a long, narrow leaf. The upper lid rises fast at the inner corner, then runs **almost flat**,
    slanting down towards the nose like a glare, and tapers into a **sharp outer point**. The lower lid is a
    shallow curve that meets it there. Both corners are points.
  - **Size:** compact, not stretched (owner, 2026-11-12, fourth pass): about 2.5 model pixels wide.
  - **The ends:** the heavy upper lash line **tapers into the outer point and stops there**: nothing black runs
    past the eye, and there is no hook. No separate lashes.
  - **Lines:** **no crease line and no lid shadow** above the eye (owner: "the eyelid… I don't like"); a thin lower
    lid line on the middle-to-outer part only (open at the outer corner).
  - **Iris:** medium to small, its top cut by the upper lid, with a dark rim, fibres, a small pupil and **one
    small highlight**.
  - **Shapes in the creator:** Steady (default), Sharp (aggressive: flatter, narrower, smaller iris), Fierce (a
    wide glare), Narrow (sleepy and cold, with a line under the eye), plus Pale for the Hyūga.
  - **Resolution:** the high-detail eye is drawn at 32 px per model pixel so the points and the sweep stay crisp.
  The skin carries the eye at 4× (`gen_parts.py`); a high-detail copy at 16 px per model pixel is drawn
  on top by `client/EyeDecalLayer` (`gen_eye_decals.py`), on exactly the same lid curves
  (`anatomy.EYE_SHAPES`). Dōjutsu use the same atlas: the Sharingan (1–3 tomoe with curling tails, a red
  iris lit from below, black ring and pupil) and the pale Byakugan iris are drawn full-bright inside the
  lids; the Mangekyō disc sits between the iris and the lash line.

## Clothing
- Like the clothing references: the outfit reads as **real cloth on a body**: big folds and
  creases with dark crevices and lit ridges, layered pieces (robe over shirt, sash over robe, wraps
  over sleeves), visible seams, stitching and trims, belts and knots that stand out.
- Worn and lived in: frayed hems, dirt toward the feet, faded patches. Metal (forehead protectors,
  vest plates) gets scratches and a hard highlight.
- Each outfit keeps one clear silhouette and two or three main colours plus the accent.

## Gore
- Same ink language: blood is deep red to near-black, splattered and dripping with rough edges;
  dried blood darker and duller.

## Jutsu effects (owner's references, 2026-10-22: fire and lightning release panels)
- **Fire:** big, layered, *cellular* flame masses: bright yellow-white cores, orange bodies, deep red
  edges, with dark outline-like smoke at the rim; flames curl in tongues and swirls; shockwaves throw
  dust, rocks and splinters; streams leave ash and embers; Phoenix Flower is many small fire bullets
  with trailing tails; dragons have readable heads (jaw, eyes) made of swirling flame.
- **Lightning:** blue-white, jagged and branching, never smooth: arcs that crawl over the body
  (armor), bolts that fork across the ground and between targets, sharp spark bursts ("ice-shard"
  shaped) at contact points; a pale blue glow on everything nearby; heavy contrast with dark shadows.
- Both: readable at a distance, a strong first frame (impact), then a slower decay; screen shake and
  hit-stop for the big ones.

## Wood Release ideas (owner's references, 2026-10-22; NOT to build yet, "just get the idea")
The owner's direction for Wood Release (Mokuton) at its most epic, for later:
- **Wood clones** standing behind the user in a fan, rough grey-brown wood texture, bark grain carved
  like the user's own face and armor (the user clasping a seal in front).
- **Wood Dragon:** a long coiling dragon of bark-scaled wood that wraps and binds a target (or a
  tailed beast), horned head with an open jaw.
- **Giant wooden statues and golems:** a huge seated/standing wooden figure with countless arms
  fanning out behind it like a halo (a "thousand hands" scale: towering over tailed beasts and
  Susanoo), a fist-forest of hands crashing down.
- **Deep Forest Emergence:** a forest of huge twisting green trunks bursting out of the ground,
  throwing up dust clouds and rock.
- Look: raw grey-green wood with deep ink grain, dust and debris on every emergence, overwhelming
  scale (bigger than anything else we have), slow heavy motion.

## Hair, eyes and bodies rework (owner's brief, 2026-11-11)
The owner: "Rework the hair based on these types of hair references; you can also go through the eyes and
the bodies." Then: "Adapt the bodies to be more to our vision, but this is a great base to build the work
around." The references are **mood only**. Some are other mods' art or franchise images, so never copy,
trace or sample them. What they show, in words:
- **Hair, 3D clumps:** hair built from many small angled cuboid clumps and locks sticking out of the head,
  each a short box at its own angle, in 2–3 shades. Styles: spiky, messy, swept, curly, wild manes, one
  big swept lock. Very readable silhouettes in bright colours.
- **Hair, draped sheets:** layered 3D sheets with strand textures that hang. Styles: long straight hair
  down the back, side bangs over one eye, messy shoulder length, long locks in front of the shoulders.
  About 40 hair colours.
- **Eyes:** big, wide, sharp anime eyes with:
  - white sclera;
  - a thick dark upper lash line that flicks out at the outer corner;
  - a large detailed iris (a crisp, high-detail Sharingan or Mangekyō pattern);
  - dark brows;
  - sharp corners, never round or bubbly.
- **Bodies (the "great base"):** a base skin in black underwear (boxers, plus a black top for the female
  variant), with strong shaded muscle definition (pecs, abs, arms), big messy 3D hair clumps and sharp
  narrow eyes. Clean, readable and high-contrast.

**The plan** (the owner didn't pick from the options, so these are the recommended ones; the owner can
redirect):
- **Hair:**
  - 3D clumps for short and spiky styles, and draped sheets for long styles (long straight, ponytail,
    high ponytail, braid, bangs over one eye, twin buns, wild mane).
  - **About 20 styles** defined as data, plus a hair-colour picker with about 40 swatches and a custom
    colour.
  - NPC clans keep their own hair: Uchiha dark and spiky, Hyūga long and straight, Uzumaki red, and so on.
  - Hats, helmets, hoods and masks hide or squash the hair.
- **Eyes:** bigger and more detailed, but sharp, not bubbly; 3–4 eye shapes in the creator; the
  Sharingan, Mangekyō and Byakugan redrawn at higher detail as our own designs.
- **Bodies:**
  - Keep the HD 4× pipeline, adapted to the base's clean, high-contrast feel, and start in underwear.
  - Add body types in the creator: slim, average, athletic, broad.
  - Armor still has to fit.

### As built (how the rework works)
- **Hair is 3D, built from locks.** A lock is a short polyline with a width and a thickness at each point:
  short, it's a chunky tapered clump at its own angle; long and flat, it's a draped ribbon with jagged strand
  tips. Styles are data: `assets/noirm/hair/styles/<id>.json`, written by `tools/skingen/gen_hair.py` (edit
  the recipes there and rerun; `--preview out.png` draws a contact sheet from four sides with a small
  z-buffered renderer that matches the game). 23 styles: spiky, messy, short, swept, bowl, buzz, topknot,
  back spikes, bun, hime cut, ponytail, long, high ponytail, braid, side bangs, shoulder length, twin buns,
  wild mane, undercut, ridge, slicked back, curly, twin tails (and bald).
  - Short styles are **shingles**: rows of 2.5–3.5 px clumps lying along the skull, flowing away from a
    whorl, top rows lighter than lower ones (2–3 shades), plus bangs rooted on the forehead's hairline.
  - Long styles hang: back sheets over the back of the skull and down the back, side locks in front of the
    shoulders, tails and braids. Hanging locks **swing** a little (walking, falling) and stay with the body
    when the head turns.
  - **Texture:** `textures/entity/hair/strands.png`, gray strands with inked edges, root shadow, a broken
    sheen band and jagged sheet tips, coloured per hair colour with the skin tint rule.
  - The painted **scalp** under the 3D hair is a skin part (`ninja/parts/scalp_<name>`, by `gen_parts.py`).
  - **Head gear** (`assets/noirm/hair/headgear.json`): forehead protectors press the hair in, hats and
    helmets leave only the hanging hair, hoods hide it, masks leave it alone.
  - **Colours:** 40 swatches of our own palette (20 natural shades first), plus any custom colour.
  - **Clans:** Uchiha dark with back spikes or spikes, Hyūga long and straight (hime, braids), Uzumaki red and
    spiky, Senju long; generated clans use everything.
- **Eyes:** see the eye rule under "Faces" above.
- **Bodies:** the HD 4× ink pipeline (`anatomy.py`, `gen_player_parts.py`), starting in black boxers.
  - **Build** (slim, average, athletic, broad): an ink shading layer (`build_*`: ribs for slim, a hard V
    taper and shoulder caps for athletic, lit shoulders and dark flanks for broad) plus a small width change
    of the torso and arms (`client/BodyShape`). Armor still fits around it.
  - **Frame:** masculine or feminine. The feminine frame redraws the body (`fem_*`, at each muscle level)
    with a rounded bust, softer abs and a shaded waist, and adds a black crop top (`under_top`) to the
    boxers.
