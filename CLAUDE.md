# NOIRM — context for Claude sessions

NOIRM is a Naruto-inspired **Minecraft Forge 1.20.1** mod (Forge 47.4.10, Java 17, official
Mojang mappings). The mod id is `noirm` and the base package is `com.noirm`. The name has
personal meaning to the owner, so keep it.

## Read first (and only this, unless the task needs more)

1. **`docs/STATUS.md`**: current milestone, what's in progress, next steps, the code map, decisions, open questions.
2. `docs/ROADMAP.md`: the vision and milestone list. Read it when planning.
3. `docs/COMBAT_DESIGN.md` / `docs/POWERS_DESIGN.md`: design and decisions for combat, and for
   clans, jutsu, Sharingan, awakening and biju. Read the matching one before working on those.
4. `docs/CHANGELOG.md`: history. Read it only if you need to know when or why something changed.

**Don't explore `reference/nydo/` wholesale.** It's about 1,200 JSON files, some of them several MB.
Start from `reference/nydo/README.md` (the index and system summary), then open only the
specific files you need. To find an idea in NYDO, grep `reference/nydo/assets/lang/en_us.json`
by keyword, using a narrow pattern and limited output. It's 625 KB.

## Working with the owner

- The owner is new to programming and modding. Explain changes in plain language, keep steps
  small, and make each milestone end in something playable.
- Priorities: **combat feel**, then **training and masters**, then **villages and world gen**,
  then **exploration**. Missions are secondary. Single-player first. No artist yet, so use
  placeholders and vanilla textures and avoid features that depend on custom art.
- NYDO is inspiration only. It's "All Rights Reserved", so never copy its models, animations or
  text into `src/`. The exceptions are `animations/player/licensed_boundless/` (MIT, keep the
  attribution) and the CC-BY katana model.

## Commands

| Command | Use |
|---|---|
| `./gradlew build` | Compile and package (`build/libs/noirm-<ver>.jar`). The first run takes about 4 minutes. |
| `./gradlew runGameTestServer` | Headless server that runs the `@GameTest`s in `com.noirm.gametest` and exits non-zero on failure. Use it to verify features. |
| `./gradlew runClient` | Launches the game. It needs a display, so the owner runs it locally. |
| `tools/client-smoke/run.sh` | Runs the **real client off-screen** (Xvfb) in a test arena. Drive it with `xinput.py` and take screenshots. See its README. Use it for anything client-side (HUD, input, rendering, animations). |

Run the build in the background, because Gradle's first setup is slow. A green
`runGameTestServer` is the bar for "works". Add a game test for each new mechanic where you can, using
`template = "empty"` (our `data/noirm/structures/empty.nbt`, since Forge 1.20.1 ships none).

## Code conventions

- Code goes in `src/main/java/com/noirm/<feature>/`, one package per system (`player`,
  `combat`, `training`…). Assets go in `src/main/resources/assets/noirm/`, data in
  `src/main/resources/data/noirm/`.
- Prefer data-driven content (JSON) over hard-coded content, the way NYDO does VFX and
  animations.
- Make the server authoritative for gameplay state, and use the client for input, rendering and
  animation.
- Combat tuning: move numbers live in `data/noirm/combat/moves/**.json`; system numbers are the
  constants at the top of `CombatSystem`. Read `docs/COMBAT_DESIGN.md` before combat work.
- Client-only classes (anything importing `net.minecraft.client` or PlayerAnimator) stay in
  `com.noirm.client`, and server→client packets reach them through `DistExecutor`.
- Every entity type needs a client renderer in `client/NoirmRenderers` (even particle-only ones,
  via `NoopRenderer`), or the client crashes when it spawns. Game tests can't catch this; run the
  client smoke test after adding entities.
- New jutsu: add JSON under `data/noirm/jutsu/` using an existing behavior (`JutsuBehaviors`),
  plus a `jutsu.noirm.<path>` name in the lang files. New player data goes in a sub-object of
  `ShinobiData` and is synced via `PowerPackets.SyncPowers`.

## Keeping context fresh (required)

At the end of every working session, before the final commit:

1. Update **`docs/STATUS.md`**: what's done, what's in progress, the next steps, and any new
   decisions or open questions. Keep it short; it's a dashboard, not a diary.
2. Add an entry to **`docs/CHANGELOG.md`**: date, PR or branch, and a few bullets on what changed.
3. If the vision or milestones changed, update `docs/ROADMAP.md`.
