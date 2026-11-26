---
name: client-shot
description: Run the real NOIRM client off-screen (Xvfb) to check something client-side (a renderer doesn't crash, a HUD shows) and, only if genuinely needed, take one small cropped screenshot. Use after adding an entity or changing client code that game tests can't reach.
---

# Client smoke check (screenshots optional)

The owner tests every build and reports what's wrong, so **screenshots are not deliverables**. Run the client to
prove it starts and the thing renders without errors. Take a screenshot only as a quick self-check when something
is purely visual and genuinely uncertain (e.g. "is the model facing forward?"). For model shapes, use
`python3 tools/models/preview.py` instead (no Minecraft, seconds, a few KB).

## Steps
1. `run/world` must exist (any game-test run creates it).
2. Write your own setup (copy the first lines of `tools/client-smoke/setup.mcfunction`, add what you need, end with
   `tag @s add smoke_setup`). Summon with `NoAI:1b` and a `Rotation` so it stays put:
   `summon noirm:giant_insect ~2 200 ~6 {Rotation:[90f,0f],NoAI:1b}`
3. Launch in the background (`run_in_background`), log to your scratchpad:
   `SMOKE_SETUP=<your file> flock /tmp/noirm-client.lock tools/client-smoke/run.sh > <scratch>/client.log 2>&1`
4. Wait for `joined the game` in the log (a Monitor until-loop, not sleeps).
5. Check the log: `grep -n "Exception\|ERROR" client.log | grep -vi "openal\|sound\|netty"`; renderer crashes show as
   `Rendering entity in world` / `NullPointerException`. `Loaded N Blockbench models` confirms the model loader.
6. **Only if needed**, one small shot (keeps tokens low: crop and shrink before reading it):
   ```
   export XAUTHORITY=$(ls -t /tmp/xvfb-run.*/Xauthority | head -1)   # the NEWEST one
   python3 -I tools/client-smoke/xinput.py :99 key F1 hold s 1.2      # hide the HUD, step back
   import -display :99 -window root <scratch>/shot.png
   python3 -c "from PIL import Image; Image.open('<scratch>/shot.png').crop((240,80,1040,560)).resize((480,288)).save('<scratch>/small.png')"
   ```
   Read `small.png` (480x288, a few KB), never the full 1280x720 frame. Don't commit shots.
7. Stop **your** client: `ps -eo pid,args | grep -E "Xvfb :99|<your worktree>.*java"` and `kill` those PIDs. Never `pkill -f`.

## Notes
- More options (FOV zoom, GUI scale, subtitles, creator screen): `tools/client-smoke/README.md`.
- The software-GL scene looks dark; judge shape and placement, not brightness.
- If Forge's early loading window hangs, set `earlyWindowControl = false` in `run/config/fml.toml`.
- Delete big logs when done (`client.log` can reach tens of MB).
