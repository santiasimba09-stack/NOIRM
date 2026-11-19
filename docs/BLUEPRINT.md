# NOIRM blueprint (owner's direction, 2026-10-21)

The owner's master blueprint for where NOIRM is going. **It guides; it doesn't erase.** The owner
said: keep the work we have, build what fits, and *ask before rebuilding or replacing a system*
unless the blueprint is explicit about it. `STATUS.md` tracks what's done; this file is the target.

## Pillars

- **"Ash, iron and chakra"**: gritty, grounded, high-contrast; never cartoonish or muddy.
  Base palette Soot `#17191D`, Charcoal `#292D33`, Cold Iron `#606A73`, Ash `#565B60`,
  Worn Indigo `#1F2430`. Clans, elements and awakened states get one primary accent and a small
  secondary one. Wear sits only at points of motion and contact.
- **Grounded, tactical combat**: no floaty, 100 mph spell spam. Inspired by Ninshu Origins
  (mind control, shadow possession, wall/ceiling traversal, puppets and seals) and NYDO's
  minimal UI.
- **Performance first**: 60+ FPS client, 20 TPS server, even in big fights.

## 1. Performance and memory (priority 1)

- No `new` in hot code (tick, render, animation): reuse pre-allocated buffers/pools; primitive
  collections (fastutil) instead of boxed maps in registries.
- Rendering only through `RenderType`/`VertexConsumer` (Embeddium, ModernFix, ImmediatelyFast,
  Oculus/Iris compatible); emissive alpha 180–240; LabPBR `_s`/`_n` support; translucent layers
  in one batched pass after solid geometry.
- Three-tier LOD for custom entities, beasts, summons and particles: near 0–16 m full; mid 16–48 m
  (no decorative physics, half animation blending, 70% fewer particles, flat trails); far >48 m
  (proxy silhouettes / threat indicators only).
- Server AI time-slicing: stagger companion/NPC decisions with `(id + tick) % interval`; cache
  line-of-sight for 2–4 ticks; a coarse 16×16 grid for AoE, friendly fire and proximity.

## 2. Look, feel and UI

- HUD minimal, bottom-left (health, chakra, stamina).
- Jutsu library and radial wheel: drag/drop, enable/disable, save loadout presets; a wheel for
  hot-swapping mid-fight.
- Menus: dark slate/charcoal panels, crisp type, subtle sumi-e ink borders.
- Flaming chakra: a near-white core shell tight to the body plus an offset outer shell (+0.08)
  with upward-scrolling noise; gradient white core → element hue → charcoal smoke edge
  (`#111824`); ember sparks, heat-shimmer rings, low ash smoke.

## 3. Combat, movement and tactics

- **Five-beat contract** for every strike and cast: anticipation (2–3 frames) → committed release
  (frame-precise hitbox, audio snap, camera impulse) → hit-confirm hold (hit-stop 2–4 frames) →
  recovery → cancel window (dash or combo).
- **Perception (PER)** = (TAI + SPD) / 2. A localized 15–40% slowdown against incoming heavy
  threats (camera stays free, 60 FPS), with a ~100 Hz low-pass on the audio.
  **Just-frame parry**: guarding inside that window negates the hit, breaks the attacker's guard
  and opens a 0.5 s counter window.
- **Movement**: grounded inertia (150–200 ms slide to a stop, brief momentum pivot); heavier,
  snappier gravity with a short landing recovery (1–3 ticks) unless you roll/slide.
- **Stat-driven guard**: an attacker who far outclasses the guard cuts through for reduced damage;
  a weak strike into a strong guard bounces off with a punishable recoil.
- **Ninshu-style tactics**: wall and ceiling walking with chakra control; mind-body switch,
  shadow possession (16-block line), adamantine chains that drag or lock.
- **Camera**: follows hips/spine/torso, tilting into wall-runs, dashes and hard turns. First
  person fully supported; third person framed for big fights.

## 4. Jutsu

- Automated hand seals on the hotkey, **80% movement** while weaving.
- **Interruption**: light hit → brief pause, then resume; medium → slowed for 1 s; heavy → the
  seals break, the jutsu fizzles in black smoke, part of the chakra is lost.
- Projectiles travel straight for a fixed distance/time; spamming minor jutsu falls off in damage.
- **Canon elemental clashes** (water beats fire, …): projectiles meeting mid-air resolve by power
  and chakra into cross-element explosions; big jutsu break terrain (toggleable).
- Sound: deep sub-bass, tearing wind, muffled thunder, with crisp anime cracks and chakra hums.

## 5. Progression, survival and exploration

- Core stats RES, TAI, SPD, PER, RSV; supplementary **CTL** (chakra control: efficiency, senjutsu
  stability) and **WIL** (willpower: Mangekyō blindness resistance, beast suppression).
- No passive grinding: stats grow from active play (parries, spending chakra under pressure,
  surviving "locked in", beating bosses).
- **World heat**: exploring the wilds draws roving rogue hunting parties; rare scrolls and materials
  sit in guarded ruins.
- **Sage Mode**: physical (TAI) : spiritual (RES) : nature energy = 1 : 1 : 1. Absorption is gated
  by CTL; nature energy above CTL petrifies you (frozen, slate overlay).

## 6. Character creation and dōjutsu

- A creation screen on first join (pauses entry): 3D turnable/zoomable viewport, dark industrial
  style, ash particles. Tabs: face (eye/nose/jaw/mouth/brow variants), physique and base texture
  (athletic base; underwear sets with colour pickers; muscle definition, scars, weathering,
  markings), hair (styles, length/volume, colour), genetics (clan + innate traits by a **fixed
  number of rolls**, e.g. 3; randomized blank stats RES/TAI/SPD/CTL/WIL modified by traits);
  height 0.85–1.15× (hitbox follows). Saved as light NBT IDs, synced in one compact packet.
- Dōjutsu render on a dedicated face layer anchored at standard head coordinates
  (x ±1.5, y −2.0, z −4.0), independent of skins.

## 7. Gore (visual only)

- Bruising, dirt, soot and cuts build up on models as damage accumulates (no bleeding DoT).
- Lethal/heavy crits can sever limbs into ragdolls that dissolve after 5 s in a chakra flame into
  ash; culled beyond 32 m.

## 8. Economy, fūinjutsu and territory

- Shinobi forge (rhythm hammering, chakra quench) and sealmaker's desk.
- Modular fūinjutsu: canvas + core (storage / barrier / detonation / suppression) + trigger
  (proximity / manual seal / timer).
- Clan banners claim territory (16-block radius); camp → fortified outpost (crew builds palisades,
  watchtowers) → independent village (crest, name, rival raids).

## 9. Data first

All balance numbers (costs, damage, slowdown ratios, traits, territory costs) live in JSON under
`data/noirm/`.

## Priority order

1. Performance, render hygiene, solid hitboxes.
2. Combat feel, the five-beat state machine, perception slowdown, movement cancels.
3. Stat progression, character creation, dōjutsu rendering, fūinjutsu, the casting engine.
4. Auxiliary screens and cosmetic menus.

**Phase 1 (asked first):** CTL and WIL on the stat capability; the perception slowdown and
just-frame parry; the character creation screen; the automated casting engine (80% movement,
black-smoke fizzle, elemental clashes).
