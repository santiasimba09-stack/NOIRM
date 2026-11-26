---
name: blockbench-model
description: Make, edit, preview and render Blockbench models (.bbmodel) in NOIRM - write the JSON, check it offline with the preview renderer, load it in game through BbModels, animate bones by name or play Blockbench animations, and export existing code-built models. Use for any new entity model or when the owner sends back a model edited in Blockbench.
---

# Blockbench models

The owner can open and edit `.bbmodel` files in Blockbench (free, blockbench.net); Claude writes and reads the same JSON.

## Where things are
| What | Where |
|---|---|
| Models | `src/main/resources/assets/noirm/models/bb/<name>.bbmodel` (id `noirm:<name>`) |
| Reader (common, no client code) | `com.noirm.model.BbModel` (bones, cubes, UVs, textures, animations) + `BbGeometry` (the face/UV/rotation maths) |
| Game loader (client) | `client/model/BbModels` (loads on start and F3+T) → `BakedBbModel` (posable `Part`s, `render`, `animate`) |
| Python twin of the maths | `tools/models/bbmodel.py` (load, save, `new_model`, `add_texture`, `add_group`, `add_cube`, `model_quads`) |
| Offline preview | `tools/models/preview.py` |
| Exporter from code | `tools/models/export_beetle.py` (BeetleMesh → `giant_insect`, `kikaichu`) |
| Proof in game | the Giant Insect (`client/aburame/AburameRenderers.Giant`), with the old mesh as fallback |
| Tests | `ToolingGameTests.blockbenchModelReads`, `blockbenchGeometryRules` (`tools/dev/test.sh tooling`) |

## The format rules (what Blockbench shows = what the game draws)
- Units are pixels (16 = 1 block), **y up**, feet at y 0, the model's **front toward -z** (north). Use the "Generic Model"
  format (`"model_format": "free"`); "Modded Entity" works too.
- Groups are bones: `origin` = pivot (absolute pixels), `rotation` = degrees, applied x, then y, then z. Cubes have
  absolute `from`/`to`, their own `origin` + `rotation`, `inflate`, and either `box_uv` + `uv_offset` (+ `mirror_uv`) or
  per-face `uv` [u1, v1, u2, v2] with optional `rotation` (90° steps). A face with `"texture": null` isn't drawn.
- Face UVs are seen from outside: side faces u to the viewer's right, v down; top face u east, v south; bottom face u
  east, v north. Box UV is the vanilla layout (east, north, west, south in a row under up and down), sizes rounded down.
- Texture: the first texture's `relative_path` from the model file (e.g. `../../textures/entity/aburame/x.png`), else
  `textures/entity/<texture name>`. Embed the PNG as `source` (a data URI) too, so Blockbench shows it.
- Not supported: mesh elements, locators, Molang expressions in keyframes (read as 0), bezier handles (read as linear).

## Make a model
1. Write it with `tools/models/bbmodel.py` (preferred: stable uuids, small diffs) or by hand:
   ```python
   import sys; sys.path.insert(0, 'tools/models'); import bbmodel as bb
   doc = bb.new_model('thing', 32, 32)
   bb.add_texture(doc, 'src/main/resources/assets/noirm/textures/entity/thing.png', relative_path='../../textures/entity/thing.png')
   body = bb.add_group(doc, 'body', origin=[0, 8, 0])
   bb.add_cube(doc, body, 'body', [-4, 4, -6], [4, 12, 6], box_uv=(0, 0))
   head = bb.add_group(doc, 'head', origin=[0, 12, -6], parent=body)
   bb.add_cube(doc, head, 'head', [-3, 10, -11], [3, 16, -5], box_uv=(0, 20))
   bb.save(doc, 'src/main/resources/assets/noirm/models/bb/thing.bbmodel')
   ```
2. **Check it offline** (seconds, a few KB, no Minecraft):
   `python3 tools/models/preview.py src/main/resources/assets/noirm/models/bb/thing.bbmodel`
   → `build/preview/thing.png` (front, side, back, 3/4). Options: `--views front,top`, `--size 96`, `--hide wings`,
   `--anim walk --time 0.25`, `--texture other.png`. Read the PNG to check shape and texture placement.
3. **Render it** in an entity renderer (`com.noirm.client`):
   ```java
   BakedBbModel model = BbModels.get(Noirm.id("thing"));
   if (model == null) { /* fallback: the old renderer, or nothing */ return; }
   stack.pushPose();
   stack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partial, e.yRotO, e.getYRot())));
   model.resetPose();
   model.part("head").xRot = headPitchRadians;          // pose bones by name (radians; x/y/z offsets in pixels)
   model.animate("walk", (e.tickCount + partial) / 20f); // or play a Blockbench animation by name
   model.render(stack, buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
   stack.popPose();
   ```
   Parts are shared by every entity using the model: always `resetPose()` before posing. `render(..., sideShade)`
   darkens faces per side (for copying an old mesh's baked shading).
4. Run `tools/dev/test.sh tooling` (the reader still parses) and the `client-shot` skill once for a new renderer.

## Animations
Blockbench animations (`"animations"` in the file) play with `model.animate(name, seconds)`: `loop` wraps, `once`
and `hold` stop at the end. Values follow Blockbench's preview: rotation keyframes in degrees (x and y turn opposite to
the group rotation fields, as Blockbench does), position in pixels, scale as multipliers; linear, step and catmullrom.
The player/NPC humanoid animations stay on our own engine (`client/anim`, `player_animation/*.json`); Blockbench
animations are for creatures and props drawn from `.bbmodel` files.

## The owner edits a model
1. Owner: open the `.bbmodel` in Blockbench (File → Open Model), edit, File → Save Model (same file). If the texture
   was painted: in the Textures panel, right-click the texture → Save (it writes the PNG at its path), or Export
   and send the PNG along.
2. Owner sends back the `.bbmodel` (and PNG). Claude copies them over the files in `assets/noirm/models/bb/` and
   `textures/...`, runs `preview.py` to look, `tools/dev/test.sh tooling`, and commits. In game: F3+T reloads models.
3. If a code-built model was exported (`export_beetle.py`), the edited `.bbmodel` is now the source: don't rerun the
   exporter over it.

## Export code-built models
`export_beetle.py` shows the pattern: rerun the old drawing maths in Python, turn each box into a cube (a rotated
canonical box + per-face UVs found by matching corners), and compare quads against the original (all 44 Giant Insect
faces match). Candidates next: the hound (`client/hound/HoundRigBuilder`, one model per breed/level and a mixed coat
texture, so it needs a fixed level and a baked texture), the training dummy, the tailed beast placeholder.
