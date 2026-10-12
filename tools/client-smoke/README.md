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
   `xinput.py` commands: `key <keysym>`, `hold <keysym> <seconds>`, `click <button>`,
   `move <x> <y>`, `sleep <seconds>`, `type "<text>"`. Keysyms: `r`, `v`, `Alt_L`, `F5`, `z`, …
   Cheats are on in the copied world, so you can run commands:
   `key t sleep 0.5 type "/time set night" key Return`. Typing `~` is unreliable on the Xvfb
   keyboard; put positions in `setup.mcfunction` instead. Mouse `move` turns the camera a lot.
4. Check `client.log` for `Exception` or `ERROR` (the OpenAL/sound errors are expected: no audio
   device). Stop the client by killing its `Xvfb :99` and `java` processes.

Known gotcha: the client crashes at launch without the mixin refmap properties in
`build.gradle` (`mixin.env.remapRefMap`). Keep them.
