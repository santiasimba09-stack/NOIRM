# Client smoke test (no monitor needed)

Game tests (`runGameTestServer`) cover the rules on a server. They can't see the client: the
HUD, input, animations, renderers. This runs the **real client off-screen** and drives it.

1. `./gradlew runGameTestServer` once, so `run/world` exists.
2. `tools/client-smoke/run.sh > client.log 2>&1 &`, then wait for `joined the game` in the log.
   You spawn in a glass arena in the sky with a sparring ninja 3 blocks ahead.
3. Drive it and take screenshots (the display's auth file is created by `xvfb-run`):
   ```
   export XAUTHORITY=$(ls /tmp/xvfb-run.*/Xauthority | head -1)
   python3 -I tools/client-smoke/xinput.py :99 move 640 360 click 3 key F5 key r click 1 click 1
   import -display :99 -window root shot.png
   ```
   `xinput.py` commands: `key <keysym>`, `hold <keysym> <seconds>`, `click <button>`, `holdclick <button> <seconds>`, `down <keysym>` / `up <keysym>` (hold a key across other commands),
   `move <x> <y>`, `drag <x1> <y1> <x2> <y2>` (left-button drag, e.g. to pan the advancement map), `sleep <seconds>`, `type "<text>"`, `combo <held> <key>` (e.g. `combo F3 t` reloads resources). Keysyms: `r`, `v`, `Alt_L`, `F5`, `z`, …
   Cheats are on in the copied world, so you can run commands:
   `key t sleep 0.5 type "/time set night" key Return`. Typing `~` is unreliable on the Xvfb
   keyboard; put positions in `setup.mcfunction` instead. Mouse `move` turns the camera a lot.
4. Check `client.log` for `Exception` or `ERROR` (the OpenAL/sound errors are expected without
   `ALSOFT_DRIVERS=null`: no audio device). Stop the client by killing its `Xvfb :99` and `java` processes.

`GUI_SCALE=2 tools/client-smoke/run.sh` starts at that GUI scale (0 = auto, which is 3 at 1280×720), for
checking that screens fit at other scales.

Opening screens for screenshots: the spawn point moves a little each time, so don't type absolute
coordinates. In your setup, summon invisible marker armor stands (`Marker:1b`, with a `Rotation`
that looks at the NPC or block) and get there with `/tp @s @e[tag=<spot>,limit=1]` or
`/execute at @e[tag=<spot>,limit=1] run tp @s ~ ~ ~ <yaw> <pitch>`. Pick an empty hotbar slot before
right-clicking an NPC or block (a held item may take the click). An `Escape` with no screen open
opens the game menu and swallows the next keys.

`ALSOFT_DRIVERS=null SMOKE_SUBTITLES=true tools/client-smoke/run.sh` starts the sound engine on
OpenAL's silent "No Output" device and turns subtitles on, so you can see which sounds play (without
`ALSOFT_DRIVERS=null` there's no audio device, the engine stays off and no subtitles show). The F3
screen's "Sounds: a/b + c/d" line counts playing sounds and streams (the ambience loops stream).

`SMOKE_SETUP=path/to/setup.mcfunction` runs your own setup instead (it must end with `tag @s add smoke_setup`),
for example a bare new player for a playthrough.

Character creation: a newcomer gets the creation screen and is held until it's done. The setup
finishes it at once (`noirm creator skip`). Start with `SMOKE_CREATOR=1 tools/client-smoke/run.sh`
to keep the screen open (you stay at the world spawn, held), or type `/noirm creator reroll` in
game to open it again with clan rolls.

Known gotcha: the client crashes at launch without the mixin refmap properties in
`build.gradle` (`mixin.env.remapRefMap`). Keep them.
