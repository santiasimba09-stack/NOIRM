---
name: noirm-entity-animation
description: Bring a NOIRM creature to life in vanilla Forge 1.20.1 code - the entity class, attributes, mood and action sync, idle AI goals, configurable scale and multi-part hitboxes, and a renderer that layers procedural animation (gaits with planted feet, breathing, blinking, head tracking, spring-driven tails, snakes and cloth, flinches, mood postures) over Blockbench keyframes, plus glowing eyes, chakra aura, summon smoke and distance LOD. Use after noirm-entity-textures, or whenever a NOIRM summon, beast or boss moves stiffly, looks robotic, needs new animations or AI, or a placeholder like the zoglin tailed beast should be replaced, even without the word "animation".
---

# NOIRM entity animation & behaviour

This is step 3 of 3. Its inputs are the brief `docs/entities/<id>.md` (moods, idle life, reactions, effects)
and the `.bbmodel` + textures from `noirm-entity-textures`. The owner wants **vanilla only**: no GeckoLib. We
render with our own `client/model/BbModels` → `BakedBbModel` (posable `Part`s by name, `animate(name, seconds)`
for Blockbench keyframes, `render`). Read the repo skills `add-entity` (registration checklist, required
renderer, lang, game test, client check), `blockbench-model` and `game-tests` first, and
`docs/COMBAT_DESIGN.md` before touching combat. Study `client/hound/HoundModel` too: it is our best existing
procedural rig (gaits on diagonal pairs, breathing, panting, wag chains, sniff, knockout pose).

## Why things look robotic, and the fixes this skill applies
| Robotic | Alive |
|---|---|
| One sine wave per bone, same speed forever | Several layered waves with different periods + seeded per-entity phase offsets |
| Every bone moves at once | Motion travels through chains with delay (follow-through, overlap) |
| Feet slide on the ground | Stance feet stay put and swing feet lift; the body bobs on footfalls |
| The head snaps to targets | The look splits over neck → head → eyes, eased, with limits |
| Constant posture | Posture comes from mood and blends over 0.3–1 s |
| Loops forever | Random idle actions on seeded timers, chosen by AI and synced |
| Instant poses | Anticipation → action → follow-through → settle, on every action |

## 1. Server side: the entity
Follow `add-entity` for registration (feature registry or `registry/NoirmEntities`, attributes, lang, spawn
egg). On top of that:
- **Synced state** (`SynchedEntityData`, no protocol bump needed):
  - `MOOD` (byte: calm, alert, angry, tired, plus the brief's special moods);
  - `ACTION` (byte) and `ACTION_START` (int, the game time it started);
  - for bosses, `PHASE` (byte).
  The server decides; the client only animates.
- **Mood logic** in `aiStep`/`tick`: switch on the brief's triggers (target nearby → alert, hurt or target in
  reach → angry, low health or long fight → tired, nothing for 30 s → calm). Add a minimum hold time, so moods
  don't flicker.
- **Idle AI goals** (owner asked for them; template `entity/BeastIdleGoal`): one small `Goal` per idle action from the brief (sniff the ground,
  groom, sit or lie down, look around, yawn, pipe puff, coil up, tongue-flick, wander with the summoner or the
  pack). Each goal runs only when calm and not targeting, has a seeded random cooldown, sets `ACTION` +
  `ACTION_START` when it starts, holds the mob still if the action needs it, and clears the action when it ends.
  Packs: followers keep 2–5 blocks from a leader and copy the leader's mood with a delay.
  **Gotcha:** vanilla calls a running goal's `tick()` only every other tick unless `requiresUpdateEveryTick()`, so
  time durations by game time (`endsAt = gameTime + duration`), not by counting `tick()` calls.
- Combat goals reuse the existing patterns (`BeastFightGoal`, `NinjaFightGoal`). Each attack sets an `ACTION` with
  a **wind-up** of at least 6 ticks before damage (anticipation the player can read), then recovery.
- **Configurable scale:** add `<id>Scale` (default from the brief, with min/max) to `config/NoirmConfig` in the
  common/server spec, because hitboxes depend on it. Apply it with `refreshDimensions()` and `getDimensions(pose)`
  scaled. `getDimensions` only runs on a refresh, so reading the config there is cheap; vanilla `/reload` does **not**
  reload configs (the file watcher does), so re-check the size every ~100 ticks and refresh if it changed. Add a row to `docs/TUNING.md`.
- **Multi-part hitboxes** (only when the default size is above about 6 blocks; skip otherwise): Forge `net.minecraftforge.entity.PartEntity<T>` children (head,
  body, tail segments) placed each tick from the root's yaw and the bone positions in the brief, with
  `isMultipartEntity()` true and `getParts()` overridden. Damage on a part forwards to the root (head ×1.5).
  Keep the root box small enough to path.
- **Summon / dismiss:** spawn with a smoke poof (below), dismiss with a poof and `discard()`. Summons get
  `.noSave()` if canon has them return home.

## 2. Client side: the renderer and the motion stack
The renderer lives in `com.noirm.client` (or `client/<feature>/`) and is registered in
`client/NoirmRenderers.registerRenderers`. That's required, or the client crashes on spawn. Parts in a
`BakedBbModel` are **shared by every entity**, so keep per-entity animation memory in a side table:
```java
// client/<feature>/<Name>Motion.java — per-entity memory (springs, timers, blink state)
private static final Int2ObjectMap<<Name>Motion> STATE = new Int2ObjectOpenHashMap<>();
static <Name>Motion of(Entity e) { return STATE.computeIfAbsent(e.getId(), id -> new <Name>Motion(e.getUUID())); }
// drop entries for removed entities (EntityLeaveLevelEvent on the client) so the map can't grow
```
Every frame in `render(...)`:
```java
float t = entity.tickCount + partial;          // ticks; seconds = t / 20f
float dt = motion.frameDelta(t);               // clamp to [0, 0.1] s
model.resetPose();                             // ALWAYS first (shared parts)
int lod = lodFor(entity);                      // 0 full, 1 mid, 2 far
CreatureRig.basePose(model, motion);           // rest pose + mood posture blend
CreatureRig.locomotion(model, entity, motion, partial);
CreatureRig.idleLife(model, motion, t, lod);   // breathing, blinking, twitches
CreatureRig.look(model, entity, motion, partial);
CreatureRig.action(model, entity, motion, t);  // Blockbench clip for ACTION, eased in and out
if (lod == 0) CreatureRig.secondary(model, motion, dt); // springs on chains & soft parts
CreatureRig.reactions(model, motion, dt);      // hurt flinch spring, impact rock
// render colour, then glow
```
Put the reusable maths in **one shared class**: it exists now, `client/model/CreatureRig` (`approach`, `Spring`,
`Blinker`, `Twitch`, `envelope`, `lod`); add to it rather than starting another. (`client/anim` is the player
animation engine; keep creatures out of it.) Worked example: `client/beast/ShukakuRenderer` + `ShukakuMotion`. Each creature
then supplies only a small profile: bone names, amplitudes, gait type and mood postures. Build the helper once,
with the first creature, and reuse it for every one after.

### Layers in detail
**Rotation signs (get these right first; a wrong sign was this skill's first bug).** `Part.xRot` > 0 tips a part's
top backwards, swings a hanging limb (arm, leg) forward, lowers a chain that points back (tail) and raises a part that
points forward (head nose-up, jaw shut). So lean forward = `-body.xRot`, head down = `-head.xRot`, jaw open =
`-jaw.xRot`, tail up = `-tail.xRot`. For a model facing -z, `+x` is the creature's right; `yRot` > 0 turns it to its
left. Blockbench **keyframes** invert x and y (`BakedBbModel.animate`), so a jaw-open keyframe is `x = +30`.

**Base pose + mood.** Each mood has a posture: head height, spine curve, tail carriage, ear angle, eye
openness, breathing rate and speed. Blend the current posture toward the target with
`p += (target - p) * (1 - exp(-dt / 0.35))`. Never snap. Angry: head low and forward, shoulders up, ears back,
tail rigid, eyes narrowed. Tired: head and tail drooping, slow deep breaths, slower gait.

**Locomotion (planted feet).** Drive the gait from `walkAnimation.position(partial)` (the distance walked) and
`speed(partial)`, not from time. That keeps feet in step with the ground.
- Gaits: quadruped walk (diagonal pairs, like the hound), gallop (front pair, then back pair), toad hop (crouch →
  launch → airborne tuck → land squash, triggered by movement and with a cooldown), snake serpentine (a sine
  travelling down the chain: `segYaw[i] = A * sin(phase - i * k)`, growing toward the tail, with the head kept
  steady), slug glide (a peristaltic squash-stretch wave), insect tripod, tentacle crawl.
- **Feet:** for each leg, stance phase = the upper bone counter-rotates so the foot stays planted; swing phase =
  the lower bone folds (`max(0, sin(phase))`) to lift the foot, and the foot bone stays level. Add a 2-bone
  IK solve (`law of cosines`) when the brief asks for ground-adapting legs. Probe the block height under each
  foot (client `level.getHeight` / block collision under the foot's world position, cached per tick) and
  offset the target so legs reach down steps instead of floating.
- **Weight:** the body bobs down on each footfall (`-|cos|`), rolls a little toward the stance side, and the
  head counter-bobs at half amplitude, so the head stays steady like a real animal. Big creatures move
  slowly with bigger bobs: scale the frequency by `1/sqrt(scale)` and the amplitude by `sqrt(scale)`.

**Idle life** (always on, cheap):
- Breathing: chest/belly scale or offset at the mood's rate, plus a slower second wave at 0.37× the speed. Toads
  pump the throat sac on their own rhythm.
- Blinking: a seeded random interval (2–6 s, from the UUID), 120 ms close and 80 ms open, sometimes a double
  blink. Drive the eyelid bones (or swap the eye islands painted in the texture). Calm eyes half-lid sometimes.
- Twitches: ear flicks, whisker or nostril flares, tail-tip curls, finger or claw flexes, each on its own seeded
  timer with a quick ease-out-and-settle curve. Desynchronise everything: no two creatures blink together.

**Look.** The target comes from the entity's look target (`yHeadRot` minus `yBodyRot`, and `getXRot`). Split the
angle over the chain: neck 40 %, head 45 %, eyes the rest (eye bones or a small UV shift), with eased
following (`0.15 s` for the eyes, `0.3 s` for the head, `0.5 s` for the neck), clamped to the brief's limits.
Giants turn slowly and lead with the eyes.

**Actions.** Play the Blockbench clip named after the action (`model.animate(name, seconds since ACTION_START)`),
authored in the `.bbmodel` with anticipation, action, follow-through and settle. Blend it in over 0.15 s and
out over 0.25 s with `model.animate(name, seconds, weight)` (every channel scaled by the weight, on top of the
procedural pose); keep the previous action playing with a falling weight while the new one rises. If a clip doesn't exist yet,
write it procedurally with the same 4 beats.

**Secondary motion (springs).** For every chain (tails, tentacles, snake body when idle, ears, manes, cloth,
throat sac, belly), keep a damped spring per segment in `<Name>Motion` and feed it the angular velocity of
its parent:
```java
// per segment: angle a, velocity v; target = parent's rotation change + rest curl
v += (stiffness * (target - a) - damping * v) * dt;  a += v * dt;
```
Use stiffness 30–80 and damping 6–12 for tails, stiffer for ears and softer for cloth. Each segment's target is
the previous segment's lagged angle, so motion **flows to the tip**. This one layer does the most for "alive".
Add gravity sag for long chains when standing still.

**Reactions.** On hurt (`hurtTime` rising), kick a spring on the body and head away from the attacker's direction
(small creatures: a big kick and a hop; giants: a slow rock and a head jerk) and briefly close the eyes. On big
nearby impacts (shockwaves, explosions), give a startle: ears up, head toward the sound. Intimidation (entering
angry): a short roar or snarl clip with the jaw open and the chakra aura burst.

## 3. Effects
- **Glowing eyes and markings:** after the colour pass, render the same posed model again with the glow texture:
  `model.render(stack, buffers.getBuffer(RenderType.eyes(GLOW_TEX)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, a)`.
  Pulse `a` with the mood (angry brighter). `EyeDecalLayer` and `SpaceTimeFx` show the pattern.
- **Chakra aura** (tailed-beast cloak, chakra mode, Susanoo flicker): spawn client particles from bone world
  positions (compute from the pose stack at the bone, or approximate from the bone pivots + entity transform),
  with `vfx/Vfx` options (`glow`, `smoke`, `spark`) and `VfxKind` colours. Set the rate by mood and LOD, and cap it per
  entity (≤ 6 per tick at LOD 0, ≤ 2 at LOD 1, none at LOD 2).
- **Summoning smoke poof:** server side `Vfx.burst(level, Vfx.smoke(0xE8E8E8, size, life), pos, count, spread,
  speed)` with the count and spread scaled by the entity's scale, plus a ring at the feet. Reuse the existing
  summon/seal sound if there is one. Scale in from 0.6 to 1.0 over 6 ticks with an overshoot to 1.05.

## 4. LOD (balanced: Ryzen 5 5600G + RX 6500 XT 4 GB at 165 Hz)
Distance from the camera, scaled by entity size (`dist / max(1, scale)`), with the thresholds in the client config:
- **LOD 0** (< 24 blocks): everything.
- **LOD 1** (< 64): no springs (chains use a cheap phase-delayed sine), no foot probes, blinking every other time,
  aura reduced.
- **LOD 2** (beyond): base pose, locomotion and breathing only; no glow pulse, no aura.
Also skip the motion update entirely when the entity is off-screen (frustum check in `shouldRender`). Never allocate
in `render` (reuse arrays and `Vector3f`s).

## 5. Bosses
Phases come from `PHASE` (health thresholds in the brief). Each phase change is a staged event: invulnerable
for the transition clip (roar, transformation, smoke or chakra burst, screen shake via the existing quake),
a new posture, aura and attack set, and new texture islands if the brief has a damaged form. Telegraph every big
attack with at least 10 ticks of wind-up and a glow or particle tell. Big hits get hit-stop and camera shake
(`ART_STYLE.md`: "strong first frame, slower decay").

## 6. Done means
1. `./gradlew build` compiles (under `flock /tmp/noirm-gradle-heavy.lock`, as `CLAUDE.md` says).
2. A game test in its own batch: spawn → it ticks, the moods switch on their triggers, an idle goal sets and clears
   `ACTION`, the scale config changes the hitbox, the parts exist when big → `discard()` (skill `game-tests`).
3. The `client-shot` smoke client: summon it, check that there are no exceptions in the log (the renderer runs).
4. `preview.py --anim <clip>` for each Blockbench clip.
5. Docs: `docs/TUNING.md` rows (scale, LOD distances, spring constants worth tuning), `docs/STATUS.md`,
   `docs/CHANGELOG.md`, the progress map, as `CLAUDE.md` requires.
6. Tell the owner in plain language what to look at in game: the moods, an idle action, the flinch, the look,
   the tail follow-through, the glow at night. Then ask for their feedback. Their eyes in game are the real test.
