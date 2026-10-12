# Combat design

_Living design doc. "Proposed" items are waiting on the owner's OK; "Agreed" ones are decided._

## Principles (agreed)

- Combat feel is the #1 pillar. Readable beats fast: the player must always be able to
  *see and react to* what's happening.
- Power comes from training under masters, in stages. A technique is only usable once
  strength, mastery and speed are high enough for it.

## 1. Perception: stronger fighters see attacks slower (owner's idea, agreed in principle)

Speed should not mean "everything moves faster than your eyes". The faster or stronger you
are relative to an opponent, the **slower their attacks look to you**.

Proposed implementation (Minecraft 1.20.1 has no per-player time scale):

- **Wind-up scaling.** Every attack has a telegraphed wind-up. Its length is scaled by
  `defender perception ÷ attacker speed`, so weaker enemies give long, readable windows and
  equals or stronger ones don't. This is game logic, so it works in multiplayer too.
- **Projectile slowdown.** Projectiles slow down inside a radius around a much faster target.
- **Focus moments.** A perfect dodge or parry briefly slows the whole game for about 0.5–1 s.
  This works in single-player only, by slowing the game clock. In multiplayer it would fall back
  to wind-up scaling only.
- Always relative: strong enemies stay fast and dangerous.
- Open question: is Perception its own trained stat, or derived from speed and taijutsu mastery?

## 2. Near-death awakening, the "locked in" state (owner's idea, agreed in principle)

- Trigger: health under 20%, plus a chance check. The proposal is that the chance comes from a
  hidden **Resolve** meter (filled by hard fights, training and protecting allies) rather than
  pure luck.
- Effect: a flash, a sound cue and about 1 s of invulnerability. Then about 15–30 s of boosted
  speed, damage, stamina regen and perception.
- Cost: an exhaustion debuff afterwards, and a long cooldown.
- Progression hook: a first awakening can permanently unlock something, e.g. the first of the
  Eight Gates for Guy's students.

## 3. Feel checklist (proposed)

1. **Responsiveness:** input buffering (an early press is remembered) and cancel windows
   (the end of an attack can cancel into a dodge).
2. **Impact:** hit-stop scaled by hit weight, knockback, layered sounds, camera kick, hit
   sparks. No custom art needed.
3. **Combo routes, not mashing:** L-L-L string, L-L-H finisher, forward+H launcher. Masters
   teach new routes.
4. **Posture / guard-break** meter alongside health (Sekiro-style).
5. **Perfect dodge or parry** triggers a Focus moment (see section 1).
6. **Stamina costs fall on defence** (dodge, guard, dash), not on basic attacks.
7. **A sparring ninja enemy** with readable wind-ups, block and dodge, needed to judge feel at all.

## 4. Masters teach in stages (agreed)

Example, Might Guy: basic taijutsu → Leaf Whirlwind → Front Lotus → Reverse Lotus →
Eight Gates stages → Hirudora. Each stage requires taijutsu mastery, speed or stamina
thresholds, and a test from the master.
