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
- **Faces:** sharp, intense eyes (dark lids, small bright highlight), strong brows, shadowed jaw.
  Owner, round 15: big round eyes read "soulless and way too bubbly". Eyes are a narrow slit: a heavy,
  angled upper lid cutting across the top of a small iris (dark ring, lighter lower half, small pupil,
  a one-pixel highlight), white showing on the outer side, the inner corner low (a focused tilt), the
  brow close above casting a shadow on the lid. Dojutsu overlays are drawn per eye style so they sit
  inside the lids.

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
