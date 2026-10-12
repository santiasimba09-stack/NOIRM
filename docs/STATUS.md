# Project status

_Last updated: 2026-10-12 · branch `claude/keen-cray-uc2d11` · PR #1 (draft)_

## Where we are

**Milestone 1: Shinobi core. ✅ Code done and 8/8 game tests green. It still needs a visual
check in a real game (`runClient`) by the owner.**

| Area | State |
|---|---|
| M0: project skeleton, game tests, docs | ✅ |
| M1: chakra + stamina data, saved with the player, kept across death and dimensions | ✅ tested |
| M1: server regen (chakra 1/s; stamina 20/s after a 1 s pause once spent) | ✅ tested |
| M1: server → client sync packet | ✅ round-trip tested |
| M1: HUD bars (top-left, hidden with F1/F3/spectator) | ⚠️ compiles, not seen in game yet |
| M1: `/noirm stats`, `/noirm set\|max chakra\|stamina <v>`, `/noirm refill` | ⚠️ compiles, not run in game yet |

## In progress

Nothing. Waiting for the owner's in-game check of the HUD and the command.

## Next steps (in order)

1. **Owner playtest of M1.** Run `runClient`, create a world with cheats on, and try
   `/noirm set stamina 10`. The green bar should drop, then refill after about 1 second.
2. **Milestone 2: Combat feel v1.**
   - Add the PlayerAnimator dependency (maven.kosmx.dev is reachable from the dev container).
   - A combat-mode toggle key. Vanilla controls apply while it's off.
   - A 3-hit light combo and a heavy attack. Dodge spends stamina via
     `ShinobiData.tryConsumeStamina`. Guard, and lock-on.
   - Game feel: hit-stop, knockback, camera shake, sounds.
   - Start the animations from the MIT Boundless set in
     `reference/nydo/assets/animations/player/licensed_boundless/`.
3. Milestone 3: Training (dummy, taijutsu XP), then milestone 4: Might Guy as the first master.

## Code map

| Package | What lives there |
|---|---|
| `com.noirm` | `Noirm`: mod entry point, `Noirm.id(path)` helper |
| `com.noirm.player` | `ShinobiData` (chakra and stamina rules), `ShinobiCapability` (attaches it to players), `ShinobiEvents` (tick, sync, death and clone) |
| `com.noirm.network` | `NoirmNetwork` (channel and packet registration), `SyncShinobiDataPacket` |
| `com.noirm.client` | `ClientShinobiData` (the client's copy), `ShinobiHudOverlay` (bars) |
| `com.noirm.command` | `NoirmCommand` (`/noirm …`) |
| `com.noirm.gametest` | `NoirmGameTests`, `ShinobiGameTests` |

## Decisions made

- **Forge 1.20.1** (owner's choice), with the official Mojang mappings. Single-player first.
- **Pillars:** combat feel, then training and masters, then villages and world gen, then
  exploration. Missions are secondary.
- **No copying NYDO assets.** We use them for ideas and data formats only. The MIT Boundless
  animations and the CC-BY katana are the exceptions.
- **Art:** placeholders and vanilla textures until the mechanics are fun, then Blockbench and
  credited free packs.
- **Combat is switchable:** a combat-mode toggle. Vanilla controls apply while it's off.
- **Masters:** there will be many, each teaching their own style and techniques. The first is
  **Might Guy, a taijutsu master** (e.g. Front/Reverse Lotus, Hirudora). This is a direction,
  not a spec: the owner welcomes suggestions at any time.
- **The server is authoritative.** Chakra and stamina live on the server and are synced to the
  client only for display. On death, values refill but the maximums carry over.
- Game tests live in the main source set under `com.noirm.gametest`, and only run when the
  game-test system is enabled. Forge 1.20.1 ships no empty template, so tests use our own
  `data/noirm/structures/empty.nbt` (`template = "empty"`).
- Text lives in `assets/noirm/lang/` in English (`en_us`) and Spanish (`es_es`, `es_mx`).

## Open questions for the owner

- None right now. Suggestions are always welcome.
