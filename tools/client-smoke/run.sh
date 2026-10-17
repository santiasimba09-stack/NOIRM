#!/usr/bin/env bash
# Launches the real game client off-screen (Xvfb + software OpenGL) into a test world with a
# glass arena and a sparring ninja, for checking client-side features without a monitor.
# Usage: tools/client-smoke/run.sh            (then drive it with xinput.py, see README.md)
# Needs: an existing world in run/world (any `./gradlew runGameTestServer` creates one), Xvfb.
set -euo pipefail
cd "$(dirname "$0")/../.."
RUN=run
[ -d "$RUN/world" ] || { echo "run/world missing: run ./gradlew runGameTestServer once first"; exit 1; }
rm -rf "$RUN/saves/smoke" && mkdir -p "$RUN/saves" && cp -r "$RUN/world" "$RUN/saves/smoke" && rm -f "$RUN/saves/smoke/session.lock"
# Turn cheats on in the copy, so commands can be typed in chat (xinput.py type "/...").
python3 -I - "$RUN/saves/smoke/level.dat" <<'PY'
import gzip, sys
path = sys.argv[1]
data = bytearray(gzip.open(path).read())
marker = b"\x01\x00\x0dallowCommands"
at = data.find(marker)
if at >= 0:
    data[at + len(marker)] = 1
    gzip.open(path, "wb").write(bytes(data))
PY
D="$RUN/saves/smoke/datapacks/noirm_smoke"
mkdir -p "$D/data/smoke/functions" "$D/data/minecraft/tags/functions"
echo '{ "pack": { "pack_format": 15, "description": "NOIRM client smoke test" } }' > "$D/pack.mcmeta"
echo '{ "values": ["smoke:tick"] }' > "$D/data/minecraft/tags/functions/tick.json"
echo 'execute as @a[tag=!smoke_setup] at @s run function smoke:setup' > "$D/data/smoke/functions/tick.mcfunction"
cp tools/client-smoke/setup.mcfunction "$D/data/smoke/functions/setup.mcfunction"
cat > "$RUN/options.txt" <<OPTS
onboardAccessibility:false
tutorialStep:none
pauseOnLostFocus:false
renderDistance:${RENDER_DISTANCE:-4}
simulationDistance:5
narrator:0
OPTS
LIBGL_ALWAYS_SOFTWARE=1 exec xvfb-run -n 99 -s "-screen 0 1280x720x24" ./gradlew runClient --console=plain \
  --args="--quickPlaySingleplayer smoke --width 1280 --height 720"
