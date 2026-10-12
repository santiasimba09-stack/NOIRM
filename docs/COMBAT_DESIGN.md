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
