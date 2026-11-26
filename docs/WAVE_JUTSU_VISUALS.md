# Next wave: solid, full-scale jutsu (owner's brief, 2026-11-18)

**When:** only after the current wave (bigger clan gaps and the Inuzuka hound) is finished and the
next version (v0.19.0) has been delivered to the owner. Do not start it before that.

## What the owner said (summary)
- The **Wood Dragon** and **Dust Release: Detachment of the Primitive World (expanding cube)** look
  amazing: their animations and textures are the quality bar. Bring **every other jutsu** up to
  that level.
- **Bigger and to scale.** Today the Wood Dragon scales with the height of the entity it's meant
  for. It should instead have one standard size, and a big one.
- **Solid, not particles.** Build jutsu out of voxels and cubes, like a real Minecraft thing, so
  they look and feel solid. Particles can accent them but must not be the body. This goes for
  every nature.
- **Dust Release:**
  - the expanding detachment cube should be a **real cube**;
  - **Primitive World** should be, as in the series, a **cylinder** that vaporises everything
    inside.
- **The Wood Release statue is still too small.**
- **Deep Forest Emergence** should be **much bigger**, grow **leaves**, and its wood should
  **stay forever** and work in **normal wood recipes**: real, harvestable logs, not temporary
  blocks.
- The owner likes how things are going; this wave is about scale, solidity and finish.

## Notes for whoever builds it
- Start by listing every jutsu with its current look and size, and the target size, in this file.
- Keep move numbers in JSON. Size, shape and block palette should be data-driven too.
- Permanent wood must respect `terrainDestruction` / `noirmTerrainDamage` for what it replaces,
  and must not grief villages (no placing inside protected areas).
- Screenshots of each jutsu before and after, next to the player for scale.
