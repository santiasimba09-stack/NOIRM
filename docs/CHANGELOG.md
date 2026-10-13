# Changelog

Newest first. One entry per working session or PR.

## 2026-10-13 — Playtest feedback round 4, v0.4.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Every jutsu is more dramatic:** vivid four-tone palettes, a cast flourish on every jutsu,
  double shockwave rings, camera-shaking explosions, spiral orbs, cones for breath jutsu, glinting
  auras. Genjutsu, binds, buffs, Body Flicker, Substitution, walls and Rotation got new effects.
- **Deep allies:** crew mates level their stats (from fights, training at a dummy, meditating),
  master the jutsu they use and equip up to 4 (picked in the ally screen). **Respect** (0–100)
  starts from how strong you are next to them and moves with what you do (winning fights, gifts of gear,
  hitting them, letting crew mates die); low respect means they disobey or leave. Each has a
  **trait** (brave, cautious, loyal, proud), a morale boost, retreats when hurt, and healers
  support. Right-click a crew mate for the ally screen: their gear and hands, orders
  (follow / wait / train / meditate), style, look, jutsu to teach and equip.
- **Tailed beasts, part 2:** in creative you can seal a beast at any health. Tail progression
  1 → 9: each press of the cloak key draws one more tail, each tail is stronger and harder to
  hold; tails unlock with use. A **bond** with your beast (−100…100): it makes requests (defeat a
  strong enemy, rest, let it out, offer chakra), may take over when the bond is low, gives you
  chakra when you're low, and you can ask for chakra (crouch + cloak key). Version 1 orange,
  version 2 crimson (burns), full-bond gold. `/noirm beast`, `/noirm beast bond <n>`,
  `/noirm beast tails <n>`.
- **NPCs fight with the player's animations:** a keyframe player on the client reads the same
  animation files, synced per move and dodge, with stances, rolls, guard and flinches,
  cross-faded. A new jointed NPC model with **elbows, knees and a bending waist**.
- **Real ninja gear:** 16 wearable pieces with armor points, recipes and their own art
  (forehead protectors, Senju red battle armor, Uchiha tunic, Hyūga robes, Uzumaki coat, flak
  vest, ANBU mask and armor…). Ninjas wear their clan's gear (5% drop).
- **Clean random looks:** each ninja's skin is built from 55 generated parts (hair, eyes, brows,
  marks, accessories…) with clan colors, so thousands of different faces instead of 23 skins.
- **Ninja villages:** a rare **hidden village** (wall and great gate, red Kage tower with the
  village symbol, Kage Rock, round houses with water tanks, food stalls, torii, training
  ground) with a **Kage** (the strongest ninja around), guards and wandering ninjas, and common
  **outposts**. Leaf, Sand and Snow looks by biome. Residents leave you alone unless you attack
  the village; **Village Merchants** trade for emeralds (kunai, ramen, scrolls…). See
  `docs/WORLDGEN.md`.
- **Spawn ninjas by clan:** a spawn egg per canon clan (never a surprise jinchūriki) and
  `/noirm ninja <clan> [tails] [strength]`.
- **Cleanup:** dead code removed, the game tests share their helpers (`TestKit`). Fixed: village
  merchants drew with a missing texture.
- 114 game tests, all green; checked in the real client (`docs/screenshots/r4-*.png`).

## 2026-10-13 — Playtest feedback round 3, v0.3.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Fixed:** the Great Fireball burst on its caster. Jutsu never hit their caster, clones or crew.
- **Lightning:** client-drawn crackling bolts on every raiton jutsu; 8 new jutsu (Kirin, Chidori
  Current, Chidori Senbon, Great Fire Annihilation, Fire Dragon, Great Waterfall, Earth Dragon,
  Pressure Damage) with new behaviors strike, wave and nova.
- **Lightning Armor** is far stronger; every mode grows 25% per mastery level.
- **Styles feel different:** stances, Strong Fist's own animations, per-style impact sounds/effects.
- **Crew:** beaten ninjas yield and can be invited; they follow, fight and never hit you.
- **Tailed beasts** can be killed once subdued (or sealed).
- **Shadow Clones** have real health and carry your jutsu, eyes, mode and cloak.
- **Outfits:** 23 original clan skins for ninjas.
- **Movement:** speed makes you run faster, jump higher, leap and step up blocks.
- **No cap:** stats grow without limit; power is proportional to stats, toughness to √vitality;
  the Nine-Tails and its Tailed Beast Bomb are on a different scale.

## 2026-10-13 — Playtest feedback round 2, v0.2.0 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Fights feel like fights:** three new styles (Brawler, Strong Fist, Gentle Fist) switched with
  H; sprint and back attacks; a grab (C) and clinch with knees, headbutt and throw (grabs beat
  guard, strikes beat grabs, dodge to escape); wall splats, knockdowns and juggles. Ninjas use
  them too. 11 new animations from our own generator (`tools/animgen`).
- **Attacks are dangerous:** jutsu power grows with reserves, mastery and charge; terrain
  destruction (craters, scorch marks, ice, spikes, smashed walls) that heals itself (gamerules
  `noirmTerrainDamage`, `noirmTerrainRegen`).
- **Canon scale and canon jutsu:** researched names and behavior (`docs/RESEARCH.md`); a huge
  Great Fireball, homing Phoenix Sage Fire and Water Dragon, Lightning Armor as a real mode;
  new Rasengan (Reserves 20), Shadow Clone (Reserves 15), Shadow Imitation, Water Formation Wall.
- **Yin, Yang and Yin–Yang** natures (Uchiha lean Yin, Senju Yang) with Mystical Palm,
  Partial Multi-Size, Demonic Illusion, Truth-Seeking Ball, Creation Rebirth.
- **Particles:** custom additive particles and layered presets per nature, on every jutsu and hit.
- **Byakugan ≠ Sharingan:** no prediction or perception boost; instead no blind spot, chakra
  points on bodies and a 360° chakra radar. Hyūga default to the Gentle Fist.
- 87 game tests; network protocol 5.

## 2026-10-12 — Playtest feedback round 1 (branch `claude/keen-cray-uc2d11`, PR #1)

- **Fixed: combat animations never showed** (a PlayerAnimator layer with a modifier in front of
  the animation reported "inactive"). Combos and kicks now play in full.
- **Eyes against everything:** Sharingan (2+ tomoe) and Byakugan precognition now warn about
  vanilla mobs (melee reach, bow draw, creeper swell, spider leap) and projectiles; perception
  slows projectiles coming at you (arrows keep their damage).
- **A living Sharingan:** heartbeat warning that quickens as the hit nears, markers pointing at
  each threat, afterimages on attackers, an activation flash with spinning tomoe, a faint tint.
- **Two new stats:** Vitality (max health; trained by surviving hits) and Reserves (max chakra and
  regen; trained by spending chakra). The HUD shows all four.
- **Nature tree:** 9 combined natures (Wood, Ice, Lava, Storm, Boil, Explosion, Magnet, Scorch,
  Dust), unlocked by owning the parts and mastering a jutsu of each; 8% of new clans carry one.
- **39 jutsu:** mastery chains for the basic natures, 2 for each combination; new behaviors bind,
  pillar, cloud, rain, beam, buff; multi-shot projectiles with effects. Ninjas use them all.
- **New jutsu screen (J):** cards by nature with lock reasons and details, plus the nature tree
  with unlocking.
- Commands: `/noirm byakugan`, `/noirm jutsu mastery`, `/noirm stat vitality|reserves`.
- 13 new game tests (71 total), including one that casts every nature jutsu.

## 2026-10-12 — Training, the Byakugan and tailed beasts (branch `claude/keen-cray-uc2d11`, PR #1)

- **Training:** a training dummy (item + recipe: hay bales and sticks) that never dies, stays
  planted and shows your combo and damage. Landing hits and parrying train taijutsu; dodging
  and sprinting train speed; each point costs more. Solo drills stop at 30, real opponents teach
  more the stronger they are. Speed now also makes you move faster. Stat bars in the combat HUD.
- **Byakugan (Hyūga):** born with it, G opens it. Everyone nearby is drawn as a chakra
  silhouette through walls; +15% perception; warned of every attack; guard from any direction.
  Gentle Fist: more posture damage, half damage through guard, drains chakra, closes chakra
  points (24 closed = no jutsu for 6 s). Eight Trigrams Palm Rotation and 64 Palms. Hyūga
  ninjas use them.
- **Tailed beasts:** 1–9 tails (bigger, tougher and stronger with more tails), fighting with the
  combat system (claw combo, slam) and a charged Tailed Beast Ball. Beaten down, they are
  subdued; a Sealing Scroll captures them, then seals them into you, another player or a ninja.
  3% of newcomers are born jinchūriki; 5% of sparring ninjas secretly are (cloak at low health,
  and the beast breaks free when they fall). Jinchūriki learn the Tailed Beast Ball (cloak only).
- **Client smoke test found and fixed:** a launch crash from two event subscribers named
  `Registration` with the same handler (Forge's generated class names collided). The smoke tool
  can now type chat commands (cheats on in the copied world).
- **Tests:** tests where fighters swing or knock things back now run in their own batches and
  clean up what leaves the test area. 21 new game tests (58 total, all passing).

## 2026-10-12 — Milestone 3: Identity & powers (branch `claude/keen-cray-uc2d11`, PR #1)

- **Clans:** four canon clans (JSON) plus procedurally generated custom clans in a growing,
  per-world registry. Newcomers roll ~10% canon, otherwise a known or brand-new custom clan.
  Clans give natures (with affinities), 10 possible traits, stat leanings and an awakening path.
- **Jutsu:** data-driven definitions on reusable behaviors; a 4-slot loadout cast with keys 1–4
  in combat mode (tap / charge / channel); the J screen; nature affinity, mastery and cooldowns;
  9 jutsu.
- **Sharingan:** 1–3 tomoe, toggle (G), chakra drain, perception boost, precognition, genjutsu,
  copy at 3 tomoe, and glowing red irises.
- **Awakening:** the Resolve meter and near-death "locked in" state, with identity paths (Sharingan,
  nature surge, beast, will), followed by exhaustion.
- **Jinchūriki:** the chakra cloak (B), and the beast taking over on awakening.
- **Sparring ninjas** get clans, cast jutsu and (Uchiha) use the Sharingan.
- **Test commands:** `/noirm clan | nature | jutsu | sharingan | resolve | awaken | biju`.
- **Client smoke test found and fixed:** a crash on the first jutsu cast (missing projectile
  renderer); the fireball trail is now a dense ball.
- 17 new game tests (37 total, all passing).

## 2026-10-12 — Milestone 2: Combat feel v1 (branch `claude/keen-cray-uc2d11`, PR #1)

- Combat system: switchable combat mode; data-driven moves and styles
  (`data/noirm/combat/`); combo routes (L-L-L, L-L-H finisher, H, crouch+H launcher);
  10-tick input buffer; cancel windows; hit-stop; per-move knockback, lift and lunge.
- Defence: guard with posture and parry (5-tick window), posture break, dodge with i-frames,
  perfect dodge, and focus moments that slow nearby fighters.
- Perception, the owner's idea: wind-ups scale with the strength ratio, capped at 2.5×. Added
  speed and taijutsu stats, plus `/noirm stat`.
- Client: keys (R, Alt, V, Z), mouse buttons become attacks, lock-on, camera shake, focus FOV
  pulse, posture/combat HUD, and a target bar.
- PlayerAnimator dependency, with 8 MIT Boundless animations (with attribution).
- Sparring ninja: an enemy using the same combat system, with telegraph poses.
- Fixes found by the off-screen client smoke test: the mixin refmap remap (the client crashed
  at launch), lowercase resource names, and vanilla knockback stacking on combo hits.
- `tools/client-smoke/` to run and drive the real client without a monitor; screenshots are in
  `docs/screenshots/`.
- 12 new game tests (20 total, all passing).

## 2026-10-12 — Combat design ideas (branch `claude/keen-cray-uc2d11`, PR #1)

- Added `docs/COMBAT_DESIGN.md`, recording the owner's perception idea (stronger fighters see
  attacks slower), the near-death awakening, staged teaching by masters, and the proposed
  combat-feel checklist. Open questions are listed in `STATUS.md`.

## 2026-10-12 — Milestone 1: Shinobi core (branch `claude/keen-cray-uc2d11`, PR #1)

- Chakra and stamina (`ShinobiData`) attached to every player through a Forge capability. They're
  saved in the player file, carried over on respawn (refilled after a death) and on dimension
  change.
- Server-side regen: chakra at 1 per second; stamina at 20 per second after a 1-second pause
  once spent.
- `SyncShinobiDataPacket` sends the values to the client when they change. HUD bars sit in the
  top-left corner.
- `/noirm stats | set | max | refill` debug command.
- Text in English and Spanish (`assets/noirm/lang/`).
- 7 new game tests (8 total, all passing). Added the `Noirm.id()` helper.
- Recorded the owner's decisions: combat is switchable, and Might Guy is the first taijutsu master.

## 2026-10-12 — Foundation (branch `claude/keen-cray-uc2d11`, PR #1)

- Reorganized the uploaded NydoMod 5.5 assets into `reference/nydo/` by content type (1,209
  files moved, 37 exact duplicates removed). Wrote `reference/nydo/README.md` covering the
  inventory, the reconstructed game systems and licensing.
- Created the NOIRM Forge 1.20.1 project from the official MDK (Forge 47.4.10): mod id
  `noirm`, main class `com.noirm.Noirm`.
- Added the first game test (`com.noirm.gametest.NoirmGameTests.modIsLoaded`) and the shared
  empty test arena `data/noirm/structures/empty.nbt`. `runGameTestServer` passes.
- Added the docs: `ROADMAP.md`, `GETTING_STARTED.md`, `STATUS.md`, this changelog, and
  `CLAUDE.md` for session context.
