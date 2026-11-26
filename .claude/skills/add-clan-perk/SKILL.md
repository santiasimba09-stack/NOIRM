---
name: add-clan-perk
description: Add a new clan perk or talent effect to NOIRM (a key like chakra_pool or dot_resist that clans, generated-clan packages and personal talents can carry), or give a clan new perk values. Use when a clan or talent needs an effect the Perk list doesn't have yet.
---

# Add a clan perk

Perks are keys with amounts. Clans (`data/noirm/clans/<clan>.json` → `"perks"`), perk packages for generated clans
(`data/noirm/clan_perks/<id>.json` → `"modifiers"`) and talents (`data/noirm/traits/<id>.json` → `"modifiers"`) all
use the same keys; a player's amounts are summed by `traits/Perks`. Design: `docs/CLANS.md`, `docs/TRAITS.md`;
research per clan: `docs/clans/<clan>.md`.

## Only changing numbers?
Edit the clan or talent JSON; `/reload` applies it live (skill-free; see `docs/TUNING.md`). No code needed.

## A new perk key
1. **Declare it** in `traits/Perk.java` with a javadoc saying what it does and **where it's read**:
   `MY_PERK(Kind.PERCENT, false),`. Kinds: `FLAT` (adds points/blocks/HP per second), `PERCENT` (a fraction added to
   100%: `0.35` = +35%), `CHANCE` (0-1), `FLAG` (on above 0), `TIMES` / `TIMES_MORE` (pool multipliers). The boolean is
   `lowerIsBetter` (costs, damage taken), which the UI uses to colour it.
   The JSON key is the enum name lower-cased (`my_perk`).
2. **Use it** where the effect happens, server side: `Perks.get(entity, Perk.MY_PERK)` for the summed amount or
   `Perks.mult(entity, Perk.MY_PERK)` for `1 + amount` (never below `Perks.MIN_MULTIPLIER`). Event-driven effects
   (damage, regen, attributes) go in `traits/PerkEvents`. NPC ninjas get their clan's perks through the same calls.
3. **Lang (shown in the creator and the clan screens):**
   ```
   python3 tools/dev/lang.py add perk.noirm.my_perk "My perk %s" "Mi ventaja %s"
   python3 tools/dev/lang.py add perk.noirm.my_perk.desc "What it does, in one sentence." "Qué hace."
   ```
4. **Give it to clans:** add `"my_perk": 0.3` to the clans' `perks` (and to a `clan_perks` package or talent if fitting).
   Clan tuning rule (owner): power stays big; unlock and cost carry the balance (`docs/clans/balance.md`).
5. **Test** (skill `game-tests`): give a test player a clan with the perk (build one with `TestClans.clan(...)` or
   use the canon clan) and assert the effect changes; see `ClanPerkGameTests` and `ClanBoostGameTests` for the pattern.
   Run `tools/dev/test.sh perk`.
6. If the perk changes what the client shows (HUD numbers), the value reaches the client through `ShinobiData` sync
   (`PowerPackets.SyncPowers`); a new synced field is a packet change (`tools/dev/protocol.py bump`).
7. Add a row to `docs/TUNING.md` if the owner may want to tune it, and a line to `docs/CLANS.md` for the clans that use it.
