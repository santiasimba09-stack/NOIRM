---
name: helper-rules
description: The rules every NOIRM helper (a Claude agent working in a worktree on one task) follows. Use at the start of any helper task, and when writing a brief for a helper.
---

# Helper rules

## Start
- Start from the base branch your brief names: `git reset --hard <base>` (you have no changes yet). Default base: `claude/keen-cray-uc2d11`.
- Read only `CLAUDE.md`, `docs/INDEX.md` and the files your task needs. `python3 tools/dev/codemap.py <word>` finds classes; `python3 tools/dev/codemap.py -r` lists data folders.
- **Fresh container:** `run/` doesn't exist. The first game-test run creates it (needed before the client smoke test).

## Work
- **Commit often:** small WIP commits on your worktree branch. Don't push. Squash or leave clean at the end.
- **Commit trailers** (no model names anywhere else):
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: <the session URL from your brief>
  ```
- **Code layout:** the server is authoritative; client-only code stays in `com.noirm.client`, with distinct subscriber class names (CLAUDE.md, "Code conventions").
- **No gameplay changes** outside your task. Refactors keep behaviour identical and tests green.
- **Recipes:** use the skills: `add-jutsu`, `add-entity`, `add-command`, `add-clan-perk`, `game-tests`, `blockbench-model`, `textures`.
- **Packets:** if they change, `python3 tools/dev/protocol.py bump` once on your branch and say so in your report. Merges resolve it with `protocol.py resolve`.
- **Lang:** add keys with `python3 tools/dev/lang.py add KEY "English" "Español" ["es_mx"]` (all three files at once); `lang.py check` before you commit.
- **Data reloads:** jutsu, clans, moves, encounters, traits and every other `data/noirm/` loader refresh on vanilla `/reload` (see `docs/TUNING.md`). A new data loader must be a `CodecJsonLoader` registered in `AddReloadListenerEvent` so it does too.

## Verify (cheapest first)
1. `./gradlew compileJava -q` (under the lock, below).
2. **Your tests only:** `tools/dev/test.sh <word>` (name, batch or class word, e.g. `hound`). Prints counts and failure lines.
3. Shapes: `python3 tools/models/preview.py <model>.bbmodel` (a small PNG, seconds, no Minecraft).
4. **The full suite twice, at the end only:** `tools/dev/test.sh --twice`. Everything must pass twice in a row.
   Known flaky tests are listed in `docs/STATUS.md`; one failure there that passes on the rerun is fine, say so.
- Gradle runs go under `flock /tmp/noirm-gradle-heavy.lock` (`test.sh` does this for you).
- **Screenshots are optional.** The owner tests builds and reports what's wrong. Take one only as a quick self-check when something is purely visual and genuinely uncertain, small and cropped (skill `client-shot`), never as a deliverable.

## Client checks
- `flock /tmp/noirm-client.lock tools/client-smoke/run.sh` (read its README). Needed after adding an entity (renderer crash check) or touching client code you can't test otherwise.
- Kill only your own client's PIDs, never `pkill -f`. If Forge's early window hangs, disable it in `run/config/fml.toml`. Watch the disk; delete big logs.
- If the game-test server runs out of memory loading an old `run/world`, move the world into your scratch folder and let a fresh one generate.

## Rules
- **Commands:** follow `add-command` (CommandGuide, three lang files, `gen_commands.py`).
- **Art:** our own, made by our scripts. Never copy NYDO, other mods or franchise art. Owner references are mood only. NYDO is "All Rights Reserved": inspiration only (CLAUDE.md).
- **Docs:** don't edit `docs/STATUS.md`, `docs/CHANGELOG.md` or `docs/ROADMAP.md`, and don't write to the progress map; the coordinator does those. Do update the design doc of the system you build.
- **Scratch files:** use unique names, in your scratchpad.
- **Research:** web search or fetch if you have them, else your own knowledge. Never copy text, art, models or sound into the repo.

## Briefing a helper
The coordinator writes briefs from `.claude/skills/helper-rules/BRIEF_TEMPLATE.md` (goal, what to read, steps, rules, report).

## Report
Plain language for an owner who is new to programming: what changed, how you verified it (test names and counts),
whether the protocol was bumped, files touched, and a short **what to test** list: what to try in game and what to look for.
