# Wave "Hyūga": the strongest clan in the Leaf

_Owner brief (2026-11-26), in our words. Branch `claude/wave-hyuga`. Research: [`clans/hyuga.md`](clans/hyuga.md)._

## What the owner asked
- Make the Hyūga **deadlier and more important**: in canon they're called Konoha's strongest clan, on par with
  or above the Uchiha. "Get crazy", but **build nothing new**: no new jutsu, entities or items. Make what exists
  better: stats, perks, unique traits, the Gentle Fist, the Byakugan.
- **Hyūga techniques are taijutsu**: they cost **no chakra**, and need **no Reserves** (no minimum chakra pool)
  to learn or cast.
- **Balance through mastery**: those techniques get a much higher mastery ceiling, **up to 10**, each level a
  clear gain, and far more uses to get there.
- Housekeeping: `tools/dev/protocol.py check` warns when PROTOCOL_VERSION isn't above the base branch's.

## How we built it (summary; numbers in the PR and `docs/TUNING.md`)
- **Data flag `"chakra_free": true`** on a jutsu JSON: cost 0, no Reserves gate when it unlocks, power follows the
  Taijutsu stat (not Reserves), and the clan's `jutsu_power` penalty (weak ninjutsu) doesn't touch it. Set on the
  five Byakugan techniques. The open Byakugan no longer drains chakra. No stamina cost exists for jutsu, so
  "energy" = chakra.
- **`"max_mastery"`** in a jutsu JSON (default 5, the old ceiling; up to 10). Levels 1–5 keep their XP and effect;
  levels 6–10 need far more casts and add a steady gain. The Hyūga techniques have 10, plus their own per-level
  gains (Rotation radius, palms' points, Air Palm range…).
- **Clan perks** raised across the board, two new perk keys (`parry_window`, `guard`), Gentle Fist and Byakugan
  constants buffed. Canon costs kept: the blind spot (closes at a lower CTL now), weak ninjutsu (`jutsu_power`).

## Added mid-wave by the owner (via the coordinator)
1. **New Gentle Fist mechanics are allowed** (the one exception to "nothing new"). Built in `dojutsu/GentleFistArts`,
   numbers in `data/noirm/gentle_fist/tuning.json`:
   - **Targeted tenketsu:** aim low = leg points (Slowness, stacks to III), high = arm points (Weakness, to II),
     the middle = chest (×1.5 points and chakra drained). NPCs pick a region.
   - **Organ strikes:** a heavy Gentle Fist hit makes the target bleed inside: 25% of the hit again every second for
     4 s, through armour.
   - **Chakra burst:** a Gentle Fist hit on someone forming hand seals breaks the jutsu (it fizzles); a heavy hit also
     cancels a charge or channel.
   - **Reopening tenketsu:** a Byakugan bearer reopens 2 of their own closed points a second, and a seal on them wears
     off twice as fast (Neji).
   - **Palm parry:** guard raised with the eye open; a projectile arriving within twice the parry window is turned back
     at its thrower (not from the blind spot).
   - Gentle Step follow-ups: not built (would need new moves and animations).
2. **The Byakugan lag.** Cause: every frame the eye re-ran the full model renderer of **every** living thing in sight
   (up to 120 blocks, and this wave's range buff made it worse), looked them up again for the radar, sorted the glow
   buffer, and once a second scanned the whole x-ray sphere in one frame (up to ~137,000 blocks at the widest).
   Fix: who's in sight is looked up every 5 ticks and kept nearest first; full silhouettes for the nearest 20 within
   48 blocks, a one-quad chakra core for up to 80 more, chakra points on the nearest 8, radar dots capped at 96; no
   buffer sorting; the x-ray scans 12,000 blocks per tick (`XRayScanner`), keeps the nearest 96 finds and rescans
   every 2 s or after 3 blocks of movement. Measured (game test `xrayScanIsSpreadOverTicks`): the widest scan costs
   ~19 ms, which used to land in a single frame; now it's spread over 12 ticks (~1.6 ms each).
