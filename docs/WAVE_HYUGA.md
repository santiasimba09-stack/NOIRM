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
