# Changelog

Newest first. One entry per working session or PR.

## 2026-11-26 — The clones-and-training wave (branch `claude/wave-clones`, protocol 40)

The owner's queued requests (`docs/WAVE_NEXT.md`).
- **Shadow clones closer to canon:** split chakra, one-hit pop, memories and mental fatigue, scouting, clones training
  on dummies, Multi Shadow Clone (up to 24), Clone Swap, clone-assisted Rasengan, feints.
- **Training:** clone training (N clones ≈ N× the learning, paid in fatigue), training weights, leaf focus and tree
  climbing drills.
- **Crew mates:** a Sharingan by transplant or awakening; fusion lessons (a Senju mate learns Wood Release).
- **Eight Gates:** no self-damage or screen shake; a time limit from DUR/WIL/TAI/VIT/Resolve; no limit from mid-game.
- **Tailed beasts:** nine spawn eggs, one per beast.
- **Gentle Fist and Byakugan:** internal damage through armour and guards, chakra shut-off, CTL-scaled range, blind
  spot, x-ray, point sight; 128 Palms, Air Palm, Twin Lion Fists.

## 2026-11-25 — NOIRM v0.3 (= v0.20.0): creator + Shoji UI, clan clothing, clan boosts, the Aburame (branch `claude/keen-cray-uc2d11`, PR #1)

The owner's v0.3 plan (`docs/WAVE_V03.md`). Shipped on the owner's word while the Aburame were still being finished.
- **The richer creator and the Shoji UI** (`docs/WAVE_CREATOR_UI.md`, `docs/UI.md`):
  - **Creator content:** 38 new 3D hair styles (61 in all), hair dyes, 8 eye shapes, 6 pupils, eye details, brows,
    16 iris colours, 5 builds, muscle levels, 14 skin tones, scars, marks, 12 face paints, 8 tattoos.
  - **Creator layout:** 7 tabs.
  - **The Shoji UI:** every screen and the HUD use black-lacquer wood, dark paper, red hanko seals and brush lines
    (`tools/uigen`).
- **Clan clothing:**
  - **52 wearables:** an outfit for every canon clan, capes with a cloth simulation (they can be sewn onto chest
    pieces), hoods, 9 ANBU masks (sneak+use pushes one aside), flowing gowns and skirts, the cloud cloak and a
    straw hat.
  - **Who gets it:** clan NPCs wear their clothes, and shops have a tailor shelf.
- **Clan boosts** (`docs/CLANS.md`):
  - **Pools:** the Uzumaki pool is about 100× a normal ninja's own (no staged unsealing), and the great clans are
    raised to their canon standing. Regen scales with the pool, NPC pools are capped, and big numbers on the HUD
    are shortened (12.4k).
  - **Rolls:** canon clans are about 80% of rolls for players and NPCs (`data/noirm/clan_odds/canon.json`), and the
    creator gives 5 clan rolls plus 5 separate talent rerolls.
- **The Aburame** (`docs/ABURAME.md`, deep research first):
  - **The colony:** a living colony you can see grow; seal-free insect techniques that spend insects.
  - **Swarms:** swarms of voxel beetles that drain chakra and eat jutsu.
  - **Senses:** female-insect marks, scouts and jamming.
  - **Hives:** hives, breeding and bug collecting.
  - **Shibi:** Shibi's trial.

  Merged as a snapshot; the remaining phases follow in v0.3.1.
- Protocol 38.

## 2026-11-19 — NOIRM v0.2 (= v0.19.0): styles, clans, talents, genjutsu scenes, Body Flicker, Flying Raijin, clan gaps, the Inuzuka hound, v0.19.0 (branch `claude/keen-cray-uc2d11`, PR #1)

The owner's wave (`docs/WAVE_STYLES_CLANS.md`), built by parallel helpers and merged here.
- **Fighting styles** (`docs/COMBAT_DESIGN.md`, "Fighting styles"):
  - 14 new styles. Unarmed: Drunken Fist, Iron Body, Viper, Crane, Mountain, Wind Step, Shadow, Leaf
    Hurricane, Sumo, Monkey. Weapon: Iai, Blade Dance, Bo Wall, Spear Dance.
  - Each has its own stance, guard, dodge, speed and mechanics (feints, armour, counter stance, backstab,
    aerial bonus).
  - Learned from masters, style scrolls and by watching. **I** opens the style picker (moved from Y).
- **Clans that matter** (`docs/CLANS.md`):
  - 26 canon clans with researched perks, 12 clan jutsu and clan styles; generated clans get perk
    packages.
  - Clan tab in the creator; `/noirm me`.
- **Much bigger clan gaps, with drawbacks** (owner's follow-up):
  - **Uzumaki:** chakra pool ×10 a normal ninja's at the same Reserves (×20 as a jinchūriki, about 33× a
    Hatake), strong regen and healing, longevity, sealing ×2, and a Flying Raijin gift. Drawbacks: rough
    control early (costly jutsu, weak genjutsu) and loud chakra (more wild encounters, beasts notice from
    further).
  - **Might/Lee line:** taijutsu training ×4, willpower +8 and ×2 training, stamina +150, Eight Gates at
    half wear, born knowing the Gate of Opening. Drawbacks: jutsu −60%, genjutsu −80%, half chakra, no
    nature.
  - **Every other clan:** deepened the same way, with defining strengths and real weaknesses.
  - **Balance:** raw combat perks are capped (about 1.2–1.3×), and NPCs count them at half. The simulator
    (`--clan`, `--clans`) shows every clan reaching every rank and beating class S.
- **Personal talents** (`docs/TRAITS.md`, `traits/`):
  - 29 talents; 2–4 rolled per character, biased by clan but different within a clan, at most one
    weakness.
  - `/noirm trait`; a sensor radar for Chakra Sensor.
- **Genjutsu scenes** (`docs/GENJUTSU.md`, `genjutsu/`):
  - The victim's own client draws a scene: a false sky, an ink/water/blood floor, crows, phantoms, binding
    hands, the Tsukuyomi negative with its clock, and our own sounds.
  - Breaking out shatters it like glass. Caught mobs act on what they see.
- **Body Flicker, rebuilt** (`jutsu/BodyFlicker`): instant, with a blur and an afterimage, no wall
  clipping, and a flicker-strike against a locked-on target.
- **The Flying Raijin** (`docs/FLYING_RAIJIN.md`, `hiraishin/`):
  - Marked three-pronged kunai; marks by throw, touch and surface. **Y** marks, **X** jumps, **U** cycles
    marks.
  - An instant jump with a yellow flash, the Thunder God Slash, bringing allies, sending jutsu away,
    chains.
  - NPC users; `/noirm spacetime`.
- **The Inuzuka ninja hound** (`docs/INUZUKA.md`, `hound/`): Inuzuka players start with a puppy; Hatake players get a Ninken contract.
  - **Growth:** the hound grows level by level with no fixed stages (its own XP, the bond, your power).
    Size and proportions change smoothly from a big-headed puppy to an adult taller than a horse, then
    level off. Strength keeps rising.
  - **Milestones:** carried on your head or in your jacket (small), Fang Over Fang together and Man Beast
    Clone (lv 6), guard (lv 12), riding and the Two-Headed Wolf (lv 20).
  - **Traits:** 12 (JSON), 2–3 per hound with at most one weakness.
  - **Looks:** 5 breeds, 3 ear and 3 tail types, 6 coat patterns, 16 colours, markings, all rolled at
    bonding.
  - **Smell tracking:** marks hidden enemies and follows the scent.
  - **Food and pills:** foods, bones and a hound ration pill.
  - **Knockout, never death:** a downed hound is knocked out and recovers.
  - **UI:** panel on **O**, a HUD line, `/noirm hound`.
  - **NPCs:** Inuzuka NPCs bring their own hounds.
- **Fixes:**
  - The clan tab's long perk lines shrink to fit.
  - Tests that failed by chance (`genjutsuStunsAWeakerMind`, `despawnRules`, `groupMatesJoinTheFight`, `theApothecaryFillsGrindsAndRefuses`)
    now wait for or retry what they check.
- Protocol 32. 555 game tests green twice in a row.

## Older entries

Everything before NOIRM v0.2 (v0.18.0 = APX 0.1 back to the Foundation) is in [`changelog/archive.md`](changelog/archive.md).
