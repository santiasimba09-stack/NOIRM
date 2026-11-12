# Combat design

_Living design doc. "Proposed" items are waiting on the owner's OK; "Agreed" ones are decided._

## Principles (agreed)

- Combat feel is the #1 pillar. Readable beats fast: the player must always be able to
  *see and react to* what's happening.
- Power comes from training under masters, in stages. A technique is only usable once
  strength, mastery and speed are high enough for it.

## 1. Perception: stronger fighters see attacks slower (owner's idea, agreed)

Speed should not mean "everything moves faster than your eyes". The faster or stronger you
are relative to an opponent, the **slower their attacks look to you**.

- **Proportional (agreed):** if you're 20% stronger than an opponent, their attacks play 20%
  slower against you. Formula: `slow factor = clamp(your perception ÷ their perception, 1, 2.5)`.
  The cap keeps it from ever becoming absurd. If you're weaker, there's no speed-up penalty;
  they simply get no slowdown.
- **Perception (agreed):** derived from **speed and taijutsu**, and boosted by **dōjutsu**
  (Sharingan: see attacks slower or before they happen). Until training exists (M3), speed and
  taijutsu are plain stats in `ShinobiData`, defaulting to 10.

Implementation (Minecraft 1.20.1 has no per-player time scale):

- **Wind-up scaling.** Every NOIRM attack has a telegraphed wind-up. When an attack starts, its
  wind-up length is multiplied by the target's slow factor. This is game logic, so it also
  works in multiplayer.
- **Focus moments.** A perfect dodge or parry briefly slows nearby enemies' actions (to 30%
  speed) and movement for about 1 s. It uses the same per-fighter time scale as perception, so
  it also works in multiplayer. (A true whole-game slow-motion is possible later in
  single-player only.)
- **Projectile slowdown:** later, once there are projectiles.
- Vanilla mobs don't use NOIRM's attack system, so perception only affects NOIRM fighters
  (e.g. the sparring ninja).

## 2. Near-death awakening, the "locked in" state (owner's idea, agreed; planned for M3)

- Trigger: health under 20%, plus a chance check driven by a hidden **Resolve** meter (agreed),
  filled by hard fights, training and protecting allies (see Squads in the roadmap).
- Effect: a flash, a sound cue and about 1 s of invulnerability. Then about 15–30 s of boosted
  speed, damage, stamina regen and perception.
- Cost: an exhaustion debuff afterwards, and a long cooldown.
- **Depends on who you are (agreed):** an Uchiha may awaken the Sharingan or gain a tomoe; a
  student of Guy may open an inner gate; a jinchūriki may *lose control* as the beast takes
  over, which is powerful but dangerous. Awakenings can permanently unlock things.

## 3. Feel checklist (agreed: all of it is milestone 2)

1. **Responsiveness:** input buffering (an early press is remembered) and cancel windows
   (the end of an attack can cancel into a dodge).
2. **Impact:** hit-stop scaled by hit weight, knockback, layered sounds, camera kick, hit
   sparks. No custom art needed.
3. **Combo routes, not mashing:** L-L-L string, L-L-H finisher, crouch+H launcher. Masters
   teach new routes.
4. **Posture / guard-break** meter alongside health (Sekiro-style).
5. **Perfect dodge or parry** triggers a Focus moment (see section 1).
6. **Stamina costs fall on defence** (dodge, guard, dash), not on basic attacks.
7. **A sparring ninja enemy** with readable wind-ups, block and dodge, needed to judge feel at all.

## 4. Masters teach in stages (agreed)

Example, Might Guy: basic taijutsu → Leaf Whirlwind → Front Lotus → Reverse Lotus →
Eight Gates stages → Hirudora. Each stage requires taijutsu mastery, speed or stamina
thresholds, and a test from the master.

## 5. As built (milestone 2)

**Controls** (rebindable under Controls → NOIRM): **R** toggles combat mode. In combat mode:
**left click** = light, **right click** (empty hand or sword) = heavy, **crouch + right click** =
launcher, **Alt** = dodge (direction from WASD; none = step back), **hold V** = guard,
**Z** = lock-on. Mining is disabled in combat mode; right click still eats and places blocks
when holding something else.

**The basic style** (`data/noirm/combat/`): light route jab → hook → spin kick; jab → hook →
**heavy** = flip-kick finisher (launches); heavy alone = heavy kick; crouch + heavy = uppercut
launcher. Each move's timing, damage, posture, range, arc, knockback, lift, lunge, hitstun,
hit-stop and shake are in its JSON.

**Key numbers** (`CombatSystem`): input buffer 10 ticks; parry window 5 ticks; guard keeps 25% of
damage and costs stamina (4 + half the damage); dodge costs 20 stamina, lasts 10 ticks with 6
invulnerable (the first 3 count as a perfect dodge); posture 100, a break stuns for 1.5 s; a focus
moment slows everyone within 10 blocks to 30% speed for 1 s.

**Sparring ninja:** 40 HP, speed/taijutsu 10 by default
(`/summon noirm:sparring_ninja ~ ~ ~ {Speed:15,Taijutsu:15}` for a stronger one). It picks from
5 combo patterns, then backs off for 1–2 s; against an opponent's wind-up it guards or dodges
with a chance of 25% × its perception slow factor.

## Styles, grappling and impacts (round 2, 2026-10-13)

- **Styles** (`data/noirm/combat/styles/*.json`): each maps inputs to opening moves (`openers`),
  with optional `taijutsu` and `byakugan` requirements and an `npc_weight`. Academy (basic),
  Brawler (elbows, knees, headbutts), Strong Fist (taijutsu 18: Leaf Whirlwind, Leaf Rising Wind,
  Dynamic Entry), Gentle Fist (Byakugan: palms with `pierce` 0.6 and `chakra` drain). H cycles.
- **Inputs:** light, heavy, launcher, plus `forward_*` (sprinting) and `back_*` (holding S), which
  fall back to the plain input when a style or route has nothing for them; and `grab` (C).
- **Clinch:** a move with `"clinch": "grab"` grabs. Grabs ignore guard; grabbing someone in their
  active frames fails and staggers you (strikes beat grabs). While holding, the grab move's `next`
  routes decide (knee = `hold`, headbutt and throw = `release`). The held fighter escapes with a
  dodge (1.5× stamina). Getting hit makes you let go. Max 50 ticks.
- **Wall splat:** any unguarded hit with knockback ≥ 0.8 and a solid block behind the target:
  +40% damage, 24-tick stun, extra hit-stop, and the wall cracks (`Destruction.smashWall`,
  scaled by damage and taijutsu).
- **Knockdown** (`"knockdown": true`): at least a 32-tick stun and a small bounce.
- **Juggles:** unguarded hits on an airborne target keep them floating (up to 5), each 10% weaker
  (min 50%).

## Round 3 (2026-10-13)

- **Stances:** each style loops its own upper-body stance in combat mode (`stance_<style>`, a
  PlayerAnimator layer under the attacks). Impacts differ per style (`CombatEffects.styleImpact`).
- **Power scale:** no stat cap. `Perception.statPower(stat) = stat/10` drives taijutsu damage and
  jutsu power; `Perception.durability` divides damage taken by √(vitality/10) (NPCs: their
  strength). XP per point steepens past 50.
- **Movement** (`player.Movement`): run ×√(speed/10) (cap ×5), jump +0.11 per doubling of speed,
  sprint-jump leap, step-ups at 25 and 60, softer landings.
- **Crew:** no friendly fire between a player, their shadow clones and their crew (`ally.Crew`).

## NPC animation and bendy limbs (round 4, 2026-10-13)

**Owner:** "NPCs are not following the same animations as I do. Make the character have more
moving parts, like Better Combat and Epic Fight."

**How those mods do it (research notes):**
- **Better Combat** plays its attacks and idle poses through **PlayerAnimator** (KosmX), the same
  library we use for the player. PlayerAnimator moves the six vanilla parts plus a whole-body
  transform; when **bendy-lib** (also KosmX) is installed it can also *bend* each limb and the
  torso in the middle (a `bend` angle and a bend direction per part), deforming the mesh so the
  elbow and knee fold. That is where Better Combat's "softer" look comes from. bendy-lib is on
  `maven.kosmx.dev` as `io.github.kosmx.bendy-lib:bendy-lib-forge` (versions up to 5.1).
- **Epic Fight** replaces the player and mob models with its own **armature**: a joint
  hierarchy (root, torso, chest, head, shoulder → elbow → hand, hip → knee → foot, tool joints)
  and a skinned mesh whose vertices are weighted to joints, animated by its own clips. It reaches
  mobs through "patches" that give each mob type an armature, so NPCs and players share the same
  animations. Third-party mods (Fightura, EpicYSM) map other models onto those joints, which
  shows the joint list is what matters, not the exact mesh.

**What we built (no new dependency):**
- **Same animations for NPCs.** `client/anim/KeyframeAnimation` reads the very JSON files
  PlayerAnimator plays on players (`assets/noirm/player_animation/`) with the same unit and sign
  conversions as PlayerAnimator's GeckoLib reader, and samples them with linear interpolation
  (or the keyframe's easing), looping stances. When an NPC starts a move or dodges, the server
  sets synced entity data on `SparringNinja` (animation id, length, a counter), through
  `CombatAnimated`; the client plays it stretched to the move's real timing, like the player's.
- **Layers and cross-fades** (`client/anim/NpcAnimator`): vanilla walk + natural bends → the
  style's stance (while fighting) → the move/roll → guard → old wind-up poses (only for moves
  without an animation, e.g. a beast's) → hit flinch while staggered. Each fades over 2–4 ticks;
  a new move cross-fades from the old one; a 360° spin fades out the short way.
- **More moving parts** (`client/BendyNinjaModel`): arms and legs are two segments each (elbow,
  knee) and the torso two halves (waist; head and arms ride the bend). It reads the normal 64×64
  player skin, cut in half along each limb, both layers (`tools/animgen/check_uv.py` checks every
  UV rectangle). Rigid segments with skin-textured caps, not a deformed mesh: a sharply bent
  joint looks blocky at the crease, which suits the vanilla style.
- **`"bend"` keyframes** (NOIRM extension, degrees; positive flexes the joint: elbow curls the
  forearm forward, knee folds the shin back, torso hunches forward). `gen_anims.py` writes them
  for all its animations and stances; the Boundless files have none (bend 0 = the base pose's
  soft bend). PlayerAnimator ignores the key, so **players don't bend**.
- **Players + bendy-lib: follow-up, not done.** Adding bendy-lib would also need our bends in a
  format PlayerAnimator's GeckoLib reader understands (it only reads `rotation` and `position`),
  i.e. converting the files to Emotecraft JSON or a small custom loader, and it adds a mod
  players must install. Revisit if the owner wants the player to bend too.

## Animation engine (round 5, 2026-10-15)

**Owner:** "I don't like how the animations are going… the combos work great but the animations
are lacking and take away some of the feeling. Maybe change the player animation mod, or create
our own that works like it but lets you do what I want with the combat. Look at Better Combat and
Epic Fight for inspiration."

**What was wrong:** keyframes were few and linear; players couldn't bend (PlayerAnimator without
bendy-lib); the vanilla model has no hierarchy, so a torso twist left the shoulders and head
behind; legs mostly kept walking under the attack; nothing moved between moves.

**What we built (our own, replacing PlayerAnimator):**
- **One body for everyone** (`client/BendyModel`, `client/NoirmPlayerRenderer`): players are drawn
  with the same jointed model as NPC ninjas (slim and wide skins). `client/anim/Rig` bakes a real
  hierarchy into the vanilla parts: the torso turns about the hips and carries the head and arms
  (then the waist bend carries them again); armor, held items and eye layers follow untouched.
- **Living layers** (`client/anim/HumanoidAnimator`), Epic Fight's "living animations" in spirit:
  vanilla base → our gait (knees, elbows, shoulders countering the hips, bob) and the **ninja run**
  → air (tuck up, reach down) and landing (sinks into the knees by how hard) → the style's stance
  (upper body always, its footwork only standing) → the move (stretched to the server's timing,
  cross-faded) → guard / stagger → a **flinch** thrown away from the hit's direction → lean into
  turns → **springs** on every joint (frequency, damping, response per bone; arms overshoot a
  little for follow-through). **Hit-stop** freezes both fighters' animation clocks on the client
  (server sends it with the hit).
- **Authoring** (`tools/animgen/motion.py` + `gen_anims.py`): poses in plain degrees, mirroring,
  per-key easing (back, expo, cubic… and true Catmull-Rom), and `strike()`, which times anticipation
  → contact → follow-through → settle from the move's own windup/active/recovery so the contact key
  lands on the hit tick. Whole-body by default: hips turn, feet step, the head is left to the engine.
- **First person** (`client/FirstPersonArms`): in combat mode (or mid-move) the model's own arms are
  drawn from the eyes, so fists, punches and weapon swings match third person.
- **Preview:** `/noirm anim <name> [ticks] [targets]`; F3+T reloads animation files.

**Inspiration only:** Epic Fight (armature, living animations, hit-stop) and Better Combat (bendy
limbs, weapon movesets) were studied for ideas; no code or animation data was taken from them.
The old section above ("NPC animation and bendy limbs") describes round 4; its PlayerAnimator
parts no longer apply.

## Round 6 additions

- **Footwork:** `HumanoidAnimator.plantFeet` keeps standing feet on the ground with two-bone leg
  IK (thigh and shin 6 px); a planted foot keeps its spot until the animation steps it more than
  `STEP_DISTANCE`, and the hips drop up to `MAX_PELVIS_DROP` for wide stances. `motion.strike`
  adds weight transfer to every attack: the body sinks on the wind-up (`SINK_WINDUP`) and drives
  forward at contact (`DRIVE_CONTACT`); spins and flips are left alone.
- **Hit reactions by strength:** the health a hit takes (relative to max health) sets
  `flinchStrength` (0.5–1.6): light hits move mostly the head, heavy ones shove the body back,
  buckle the knees, last longer and hold a longer hit-stop.
- **Weapon trails** (`client/WeaponTrails`): while a move plays with a sword, kunai or staff, the
  blade's base and tip are sampled each frame and drawn as a fading ribbon after particles.
- **More moves per style:** the back inputs (hold back + light / heavy) and dash heavy now have
  their own moves in every style (see `data/noirm/combat/styles/`); NPC combos use them.
- **Hand seals:** `data/noirm/jutsu_seals/<jutsu>.json` lists a jutsu's seals; `tools/animgen/seals.py`
  writes them and the matching `seals_<nature>_<name>` animation (3 ticks per seal). Charged and
  channelled jutsu play the sequence; every cast names the seals above the hotbar.

## Combat animation rebuild (owner's brief, 2026-11-11)
The owner's view: "Each time I attack, it tries to be too realistic for Minecraft and doesn't get to a
point where it feels comfortable." They want combat "clean and smooth but still brutal and raw". Their
pose sheets (mood only, never traced) show strong, exaggerated key poses: wide stances, twisted torsos,
big wind-ups, foreshortened fists and kicks. These are the owner's answers; ask before changing them.
- **Snappy key poses.** A quick wind-up pose, then an instant snap to a big, exaggerated strike pose
  that is held for a few frames on impact, then a short recovery back to stance. Few in-between
  frames, strong silhouettes. No mocap-like blending through every joint.
- **Stances: both kinds.**
  - Each style (unarmed and weapon) has its own idle guard stance, footwork and move set.
  - Each style also has 2–3 **sub-stances** (high/low, aggressive/defensive…) that change its moves.
  - **The game picks the sub-stance automatically** from what you're doing: guarding, pressing,
    crouching, and so on.
- **Expansion:**
  - **Longer combo strings:** more hits, branching routes, and enders that launch, knock down or
    wall-splat.
  - **Dash and counters:** a well-timed dodge opens a short window where your next attack becomes a
    fast counter (behind or beside the enemy); sprinting into an attack does a dash strike; a few
    guard-break moves for blockers.
- **Pace: faster overall.** Everything quicker and more fluid, with a smaller hit-stop.
- **Impact: hard but clean.** A short, sharp hit-stop, a punch of camera shake, blood and debris on
  heavy hits, enemies crumpling or flying on enders. It stays readable and fast.
- **Scope:** unarmed styles, grabs and throws; every weapon moveset; hand seals and jutsu casts; getting
  hit, dodges, guard, idle and the ninja run.

### How the rebuild is built (2026-11)

Built on the round-5 engine (HumanoidAnimator, Rig, KeyframeAnimation, SkeletonPose, leg IK,
springs, lean, hit-stop). Nothing was replaced; these are the extensions.

**Snappy key poses (engine).**
- `KeyframeAnimation` reads three new keys. `"easing": "snap"` (or `"snap": true | <fraction>`) on a
  keyframe makes a near-stepped move into it: the pose is reached in the first 35% of the gap
  (`SNAP_REACH`), overshooting by about 6% (`SNAP_OVERSHOOT`), then it holds. `"hold": <seconds>` on a
  keyframe holds its pose that long. `"strike"` and `"strike_hold"` on the animation mark the hit tick
  (the strike marker).
- `HumanoidAnimator` pins the springs to the pose while a snap key is being reached or held
  (`SNAP_PIN` 0.9), for the body too, so the strike lands as authored. The springs, leg IK and lean
  take over again for the recovery. Hit-stop still freezes the clock on top of the hold.
- `tools/animgen/motion.py` has `snap()`, a builder with the old `strike()` arguments: stance → a quick
  wind-up (half the windup) → snap to the strike pose on the hit tick, held 1–3 ticks (the active
  frames) → a short recovery → the stance. `exaggerate()` pushes poses for the blocky model: torso twist
  ×1.35, lean ×1.2, legs 45% wider and 25% longer in stride, knees 8° deeper, the body 1.2 px lower and
  driven 30% further, and the hitting arm or leg fully straight. Base stances are wider and lower too.
- `tools/animgen/check_moves.py` checks that every move's animation lasts the move and strikes on its
  windup. The game test `strikePosesLandOnTheHitTick` checks the same thing.

**Sub-stances.** Every style JSON has `"stances"`: `neutral` plus 2–3 of `aggressive`, `defensive`,
`low` and `air`, each with an `animation` (`stance_<style>_<kind>`) and a `switch` time in ticks (2).
The server picks one each tick (`CombatSystem.pickSubStance`), in this order:
1. **air:** off the ground for at least 4 ticks.
2. **low:** crouching.
3. **defensive:** guarding, or hit within the last 24 ticks.
4. **aggressive:** in a string (second hit on), or landed a hit within the last 30 ticks.

When both defensive and aggressive apply, whichever happened last wins. A style without that
sub-stance stays in neutral. The pick is synced in `SyncState` for the player and in entity data for
NPCs. The client snaps the stance over the `switch` ticks, and the HUD shows it next to "Combat mode".

A move's `"variants"` swap in another move for a sub-stance. Examples:
- The jab becomes a low kick when crouching and an air kick in the air.
- The heavy kick becomes the axe kick (a guard break) when aggressive and a push kick when defensive.

| Style | Sub-stances |
|---|---|
| Academy | aggressive, defensive, low, air |
| Brawler | aggressive, defensive, low |
| Strong Fist | aggressive, defensive, air |
| Gentle Fist | aggressive, defensive, low |
| kunai, staff, tantō, tonfa, yari, tekko-kagi | aggressive, defensive, low |
| sword, katana, nodachi, tessen | aggressive, defensive |

**Long strings.** Each light route is now 5 hits and ends in an ender, with branches on the way:
- Academy: jab › hook › body shot › spin kick › axe kick. Hook + heavy is a flip kick (launch); spin
  kick + heavy is a heavy kick (splat).
- Brawler: elbow › rising knee › back elbow › body hook › uppercut elbow (launch).
- Strong Fist: jab › straight › rapid kick › Leaf Whirlwind › Leaf Great Flash (guard break, knockdown).
- Gentle Fist: palm › palm › palm › palm flurry › Twin Lion Palms (splat).
- Weapons: at least 4 hits each (see `EXTRA_NEXT` in `tools/weapongen/gen_moves.py`).

A hit's `"ender"` can be `launch` (lift at least `ENDER_LAUNCH_LIFT` 0.8), `knockdown`, or `splat`
(knockback at least `ENDER_SPLAT_KNOCKBACK` 1.4, which slams into a wall when there is one). An ender
plays a reaction on the target (`hit_launch`, `hit_crumple` or `hit_fly`) and adds `ENDER_SHAKE` 0.3 of
camera shake, plus blood (when gore is on) and debris.

**Dodge into counter.** A perfect dodge (hit in the first 3 ticks of a dodge) opens a
`DODGE_COUNTER_WINDOW` (14 ticks). The next attack, which may cut the roll short, becomes the style's
`"counter"` move:
- It slips to `DODGE_COUNTER_DISTANCE` (1.2 blocks) behind the enemy, or beside them after a sideways
  dodge (`DODGE_COUNTER_SIDE` 0.35). Sideways dodges are a side step now, not a roll.
- It has a 1-tick wind-up and hits like a parry counter: ×1.5 damage, and it can't be guarded.

**Dash strike.** Sprinting into an attack (the forward inputs) has a 1-tick shorter wind-up and 0.45
more lunge, and does ×1.2 damage and ×1.3 posture (the `DASH_STRIKE_*` constants). A trail of dust
marks it.

**Guard break.** `"guard_break": true` shatters a guard that blocks the move: a posture break, and the
hit does 60% of its damage. A parry still beats it. It telegraphs with a red glow. Guard breaks:
- Academy: axe kick.
- Brawler: hammer fist.
- Strong Fist: Leaf Great Flash.
- Gentle Fist: double palm.
- Weapons: their heaviest move.

**NPCs** use the 5-hit strings and their branches. They dash-strike from 3.2 to 6 blocks (8% a tick),
counter as soon as a perfect dodge opens the window, and against a raised guard they press with a jab
into a heavy, which is a guard break when they're aggressive.

**Pace and impact (before → after).** Weapons use `pace()` in gen_moves.py: windups longer than 2 ×0.75,
recovery ×0.7, cancel ×0.6, and hit-stop one tick less.

| Move | windup / active / recovery (total) | hit-stop |
|---|---|---|
| jab | 3/2/6 (11) → 2/2/4 (8) | 2 → 1 |
| hook | 3/2/7 (12) → 2/2/5 (9) | 2 → 1 |
| spin kick | 6/3/10 (19) → 4/3/7 (14) | 4 → 3 |
| heavy kick | 9/3/12 (24) → 6/3/8 (17) | 5 → 3 |
| uppercut | 6/3/10 (19) → 4/3/7 (14) | 4 → 3 |
| Brawler hammer fist | 8/2/13 (23) → 6/2/9 (17) | 6 → 4 |
| Leaf Great Flash | 8/3/13 (24) → 6/3/9 (18) | 6 → 4 |
| katana slash | 5/2/9 (16) → 4/2/6 (12) | 3 → 2 |
| nodachi cleave | 11/3/15 (29) → 8/3/11 (22) | 8 → 4 |

- No hit-stop is longer than `MAX_HITSTOP` (5). A wall splat adds `WALL_SPLAT_HITSTOP` (2), down from 4.
- `COMBO_RESET_TICKS` went from 12 to 10.
- A hit counts as "heavy" from hit-stop 3 (`HEAVY_HITSTOP`, was 4). Enders always count as heavy.
- The camera shake is a punch: 3.2° instead of 2.5°, faster, and it dies off faster (`SHAKE_DECAY`
  0.68, was 0.8).
- Heavy hits kick up debris from the ground, plus blood when gore is on.

**Other animations.**
- Hand seals snap into each seal and hold it, on a wider base. The jutsu release pose snaps out and
  holds.
- The rolls tuck at once.
- The ninja run leans deeper and sweeps the arms further back.
- The leg IK may drop the hips up to 4.5 px (was 3) for the lower stances.
- First-person arms show the same snapped poses, because they come from the same animator.
