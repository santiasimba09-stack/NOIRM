---
name: noirm-entity-textures
description: Build a NOIRM creature's Blockbench model (.bbmodel) and paint its HD 64 px-per-block texture plus glow mask with a seeded generator script, in NOIRM's manga-anime ink style (heavy contrast outlines, cel tones, raw grain, canon colours). Use after noirm-entity-design for any new or reworked summon, beast or boss, whenever a creature's model, UV layout, texture, fur, scales, markings or glowing eyes need making or fixing, even if the owner only says "it looks flat" or "make the skin better".
---

# NOIRM entity textures & model

This is step 2 of 3. Its input is the brief `docs/entities/<id>.md` from `noirm-entity-design`. If no brief
exists, make one first. Its outputs:
- `src/main/resources/assets/noirm/models/bb/<id>.bbmodel` (the owner can open and edit it in Blockbench);
- `src/main/resources/assets/noirm/textures/entity/<group>/<id>.png` (colour) and `<id>_glow.png` (emissive mask);
- `tools/beastgen/gen_<id>.py`, the script that writes all of them. Art here is **drawn by scripts**.
  Never hand-edit a generated PNG (repo skill `textures`).

Read first: the repo skills `blockbench-model` (the format rules, `tools/models/bbmodel.py`, `preview.py`) and
`textures`, plus `docs/ART_STYLE.md`. Reuse `tools/skingen/pixlib.py` (`Canvas`, `tone`, `mix`, `hx`) or numpy+PIL.
Owner references are mood only, and so is anything from the anime or manga: draw our own pixels, never trace or sample.

## 1. Build the model from the bone tree
1. Use `bbmodel.py` (`new_model`, `add_group`, `add_cube`, `add_texture`) inside the generator script, so the
   model and its UV layout come from one source. Then the texture always lines up.
2. Follow the brief's bone tree exactly: group names, parents and **pivots at the real joint**. Units are
   pixels (16 = 1 block), y up, feet at y 0, front toward -z.
3. Shape for life, not for blocks:
   - Taper every chain (tails, snakes, tentacles, necks): each segment 8–15 % smaller, and segments overlap by
     about 1 px so no gaps open when they bend.
   - Use slight cube rotations and `inflate` for organic mass: a belly that sags, a hunched shoulder, a sloped
     skull. Straight, axis-aligned stacks read as "Minecraft mob"; angled masses read as "creature".
   - Give the face planes: a brow ridge cube over the eyes, cheek cubes, a muzzle that steps down, a lower jaw
     as its own bone, and thin eyelid cubes (top and bottom) that can close over the eye.
   - Add fur tufts, spikes, fins and cloth as thin plates (one side 0–1 px thick) on their own bones.
4. **UVs: per-face UV, not box UV**, so each face gets its own space for detail. Set the model `resolution` to
   the UV layout size in model pixels and make the PNG **4× that size**: 4 texels per model pixel is exactly
   **64 px per block** at the authored size. Pack the faces by hand in the script (a shelf packer is fine) and
   leave 1 UV px of padding (4 texels) between islands so mipmaps don't bleed.
5. Texture budget (4 GB VRAM, balanced): summons ≤ 1024×1024, bosses ≤ 2048×2048, one colour sheet + one glow
   sheet. If a boss needs more, scale down hidden faces (undersides, inner faces) before you grow the sheet.
   Giants scaled up past their authored size get fewer texels per block in the world. That is fine.
   Don't grow the sheet for scale. Detail lives in the shapes of the ink.
6. Check offline: `python3 tools/models/preview.py <model>.bbmodel --size 128` and read the PNG. Look for
   floating cubes, gaps at joints, inside-out faces and wrong pivots
   (`--anim` once animations exist).

## 2. The NOIRM ink look (owner: "between manga and anime, I like those big contrast outlines")
Paint every face from a shared set of rules, so the whole creature looks like one drawing:
- **Palette per material:** 5 tones. Ink (near-black, hue-tinted, never pure #000), shadow, base, light, and
  highlight (bone or off-white). **Hue-shift** them: shadows cooler or toward purple-blue, lights warmer. Take
  canon colours for the base (Kurama red-orange, Gamabunta rust-red, Manda purple, Katsuyu white and blue) and
  push the saturation down a step for NOIRM's raw mood. Blood red stays the accent.
- **Cel shading, mostly 3 tones** (shadow / base / light) with **hard edges**, plus occasional highlights.
  Light comes from above and front-left. Each face gets the tone its direction earns: top faces light, sides base,
  undersides and the faces under overhangs shadow. Soft gradients only inside glow areas.
- **Big contrast outlines, the signature:**
  - a 2–3 texel ink line along every face edge that is a **silhouette edge** of its part (the outer rim of
    limbs, tail segments, ears, jaw line);
  - a thicker ink line (3–4 texels) where a part meets the body (under the head, armpits, the tail root), to
    fake the contact shadow;
  - interior ink lines for anatomy and markings (eye rims, mouth line, muscle separation, scale or plate edges),
    drawn with a pressure taper: thick in the middle, thin at the ends, like a brush.
  - Lines break here and there (1–2 texel gaps) on the lit side. That keeps it inked, not vector-clean.
- **Raw texture:** seeded grain (±4–6 % value noise), dry-brush streaks that follow the form (fur flows from the
  head to the tail, along the limbs, down the cheeks), short ink hatching in the shadow tone, occasional scuffs and
  scars. Keep the noise *inside* the tone bands so the cel shapes still read.
- **Materials:**
  - fur = clumps of strands with dark roots and light tips;
  - toad skin = wet: warts with a light rim and a dark core, plus a few sharp specular dots;
  - snake scales = a tiled shingle pattern that shrinks toward the tail, each scale lit on top with an ink lower edge;
  - slug = translucent-looking, done with lighter edges and a sheen band;
  - shells and armour = plates with hard highlights and chipped edges;
  - cloth = folds with dark crevices (see `ART_STYLE.md` Clothing).
- **Eyes** follow the ART_STYLE eye rule: sharp, never round or bubbly. Beast eyes get slit or canon pupils, a dark
  rim and **one** small highlight. Paint them at full texel density.
- **Readability test:** downscale the front preview to 32 px tall. The silhouette, the eyes and the main markings
  must still read. If they don't, the ink and value contrast is too weak.

## 3. The glow mask (`<id>_glow.png`)
Same size and UVs as the colour sheet. Fully transparent everywhere except the emissive parts: eyes, chakra
markings, lava cracks, Rinnegan rings, Susanoo bodies. Paint those parts in their full glow colour with a 1–2 texel
softer halo. The animation skill draws this sheet a second time with `RenderType.eyes(...)`, so it ignores darkness.
Keep the glowing area small: a few bright spots read as alive, while large glowing areas look flat.

## 4. Variants and moods in texture
- Mood changes that need paint (an angry eye with a smaller pupil, the chakra-cloak tint, the subdued form, a
  damaged boss phase): paint them as **extra UV islands** in the same sheet (for example a second eye set) and let the
  animation skill swap visibility. That's cheaper than more textures.
- Colour variants (ninken coats, tailed-beast forms): reuse the hound approach of gray tint layers
  (`pixlib.gray` / `tint`) when the runtime has to recolour. Otherwise use separate seeded sheets.

## 5. The generator script
```python
"""gen_<id>.py — <Name>: writes models/bb/<id>.bbmodel, textures/entity/<group>/<id>.png and _glow.png.
Layout: per-face UV at resolution <W>x<H> model px; PNG is 4x (64 px/block). Seed: <n>."""
```
- Seed all randomness (from the id), so reruns give identical bytes.
- Keep a `PALETTE` dict, `paint_face(face, material, light_dir)`, `ink_edges(face)`, `ink_line(points, w0, w1)`
  and `glow(face)` helpers. Each face is painted from its cube, its side and its bone, so all edits stay in the script.
- Embed the PNG in the `.bbmodel` texture `source` (data URI), so the owner sees it in Blockbench.
- Add the script's row to the table in the repo skill `textures`.

## 6. Check before handing over
1. `preview.py` front, side, back and 3/4 at 128 and at 32 (the readability test). Read the PNGs.
2. Check the canon anchors from the brief one by one (tails counted, markings placed, colours) and tick them in
   the brief.
3. `tools/dev/test.sh tooling` (the model reader still parses).
4. Show the owner the previews (as self-checks only; the owner tests in game) and say that the `.bbmodel`
   can be opened in Blockbench. Then go to `noirm-entity-animation`.

If the owner edits the model in Blockbench, the edited `.bbmodel` becomes the source for the shape. Update the
script so it only paints the texture over the owner's UVs, rather than regenerating the geometry over their edits.
