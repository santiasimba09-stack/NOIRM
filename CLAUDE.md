# NOIRM — context for Claude sessions

NOIRM is a Naruto-inspired **Minecraft Forge 1.20.1** mod (Forge 47.4.10, Java 17, official
Mojang mappings). The mod id is `noirm` and the base package is `com.noirm`. The name has
personal meaning to the owner, so keep it.

## Read first (and only this, unless the task needs more)

1. **`docs/INDEX.md`**: one line per doc, what it's for and when to read it, with sizes. Then open only the
   docs your task needs (big ones a section at a time: `python3 tools/dev/docsec.py docs/X.md "heading"`).
2. **The skill for your task** in `.claude/skills/<name>/SKILL.md`: `helper-rules` (every helper), `add-jutsu`,
   `add-entity`, `add-command`, `add-clan-perk`, `game-tests`, `blockbench-model`, `textures`, `client-shot`,
   `merge-helper-branch`, `release`. They are short checklists pointing at the real files and scripts.
3. `docs/STATUS.md` (a 3 KB dashboard) when planning or briefing; `docs/COMBAT_DESIGN.md` /
   `docs/POWERS_DESIGN.md` before combat or powers work.

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
| `tools/dev/test.sh <words>` | **The quick runner:** only the game tests whose name, batch or class contains a word (`hound`, `a,b`, `=exact`), then a 3-line summary. Use it after each change. |
| `tools/dev/test.sh --twice` | The full suite twice in a row (the bar before a release or at the end of a helper task). Plain `./gradlew runGameTestServer` still works. |
| `python3 tools/dev/gtsummary.py <log>` | Pass/fail counts and failure lines from any game-test log. |
| `python3 tools/dev/lang.py check` / `add KEY en es [es_mx]` / `resolve` | The three lang files: sync check, add a key to all three, 3-way merge after a conflict (`install-driver` once per clone makes git do it). |
| `python3 tools/dev/protocol.py bump` / `resolve` | PROTOCOL_VERSION: bump once per branch that changes packets; resolve a merge conflict (higher + 1). |
| `python3 tools/dev/codemap.py [word] [-d]` / `-r` | A compact, fresh code map (packages → classes); `-r` lists data and asset folders. |
| `python3 tools/dev/docsec.py <doc> ["heading"]` | A big doc's outline, or one section. |
| `python3 tools/models/preview.py <model>.bbmodel` | Draws a Blockbench model to a small PNG from 4 angles in under a second (no Minecraft). |
| `python3 tools/dev/tuning_pack.py <world> data/noirm/...` | Copies data files into a world's tuning data pack (owner tuning, `docs/TUNING.md`). |
| `./gradlew runClient` | Launches the game. It needs a display, so the owner runs it locally. |
| `tools/client-smoke/run.sh` | Runs the **real client off-screen** (Xvfb) in a test arena (skill `client-shot`). Use it for client-side checks (a renderer doesn't crash, a HUD shows). Screenshots are optional self-checks only, never deliverables: the owner tests builds. |

Run Gradle in the background under `flock /tmp/noirm-gradle-heavy.lock` (the test script does). A green
game-test run is the bar for "works": your own tests with the quick runner while working, the full suite twice at the
end. Add a game test for each new mechanic where you can, using
`template = "empty"` (our `data/noirm/structures/empty.nbt`, since Forge 1.20.1 ships none).
Tests in one batch run side by side, a few blocks apart: give any test where fighters swing,
knock things back or spawn big entities its own `batch`, and remove anything it throws out of
its 3×3 area (cleanup only clears entities inside it). Leftovers break later tests. More: skill `game-tests`.

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
- Client-only classes (anything importing `net.minecraft.client`) stay in
  `com.noirm.client`, and server→client packets reach them through `DistExecutor`.
- Every entity type needs a client renderer in `client/NoirmRenderers` (even particle-only ones,
  via `NoopRenderer`), or the client crashes when it spawns. Game tests can't catch this; run the
  client smoke test after adding entities.
- Forge names its generated event-handler classes after the **simple** class name + method +
  event type, per package. Two nested `Registration` classes in `com.noirm.client` that both have
  `registerOverlays(RegisterGuiOverlaysEvent)` collide and the client crashes at launch. Give
  subscriber classes distinct names (`VignetteRegistration`, …).
- New command: register it under `/noirm` in the right `command/*Command` class, add it to
  `CommandGuide` (syntax, example, cheats) with a `help.noirm.<section>.<key>` text in all three lang
  files (`tools/dev/lang.py add`), then run `python3 tools/docs/gen_commands.py`. A game test parses every example.
  Data reloads need no command: vanilla `/reload` refreshes every `data/noirm/` loader (skill `add-command`).
- New jutsu: add JSON under `data/noirm/jutsu/` using an existing behavior (`JutsuBehaviors`),
  plus a `jutsu.noirm.<path>` name in the lang files. New player data goes in a sub-object of
  `ShinobiData` and is synced via `PowerPackets.SyncPowers`. Recipes: skills `add-jutsu`, `add-entity`, `add-clan-perk`.
- Entity models: prefer Blockbench `.bbmodel` files in `assets/noirm/models/bb/` (the owner can edit them), loaded by
  `client/model/BbModels` (skill `blockbench-model`). Numbers the owner may tune get a row in `docs/TUNING.md`.

## Keeping context fresh (required)

At the end of every working session, before the final commit:

1. Update **`docs/STATUS.md`**: what's done, what's in progress, the next steps. Keep it short; it's a dashboard,
   not a diary (old release tables go to `docs/status/history.md`, decisions to `docs/DECISIONS.md`, open
   questions to the progress map). Add new docs to `docs/INDEX.md`.
2. Add an entry to **`docs/CHANGELOG.md`**: date, PR or branch, and a few bullets on what changed (skill `release`).
3. If the vision or milestones changed, update `docs/ROADMAP.md`.
4. Update the **progress map** (https://claude.ai/artifact/2vipQQ4goqPot6eTa3xMQp, page source
   `docs/progress-map/index.html`) through the artifact's database (`ArtifactData`, version-pinned):
   - `systems`: one doc per system, with status `done`, `doing`, `owner` (needs the owner) or `planned`;
   - `releases`: each release, with its game-test count;
   - `roadmap`: lanes `now`, `next`, `later`;
   - `meta/now`: what's being built right now;
   - `questions`: open questions for the owner.

   Before planning, read `questions` and `notes`: the owner answers questions and leaves notes there.
