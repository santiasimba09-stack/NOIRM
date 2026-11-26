---
name: game-tests
description: Write and run NOIRM game tests (@GameTest), including the quick runner that runs only matching tests and the log summariser. Use when adding a mechanic, verifying a change, or reading a test failure.
---

# Game tests

## Run
| Need | Command | Cost |
|---|---|---|
| Only your tests | `tools/dev/test.sh hound` (words match the test name, batch or class; `a,b` for several; `=exact_name`) | ~2-3 min, a 3-line summary |
| The release bar | `tools/dev/test.sh --twice` (full suite twice, stops at the first red run) | ~10 min |
| Summarise any log | `python3 tools/dev/gtsummary.py build/gametest-1.log` | counts + `FAIL name failed! message` lines |

- `test.sh` runs `./gradlew runGameTestServer -PnoirmTests=<words>` under `flock /tmp/noirm-gradle-heavy.lock`, logs to `build/gametest-<n>.log`.
- The filter is `gametest/TestFilter` (system property `noirm.tests`), applied by `mixin/GameTestRegistryMixin`. No words = every test.
- Exit codes: 0 green, 1 failures, 2 the run never finished (compile error, crash: the summary shows the last error lines).
- Don't paste whole logs into your context: grep `build/gametest-1.log` for a test name if you need its lines.

## Write
- Put tests in `src/main/java/com/noirm/gametest/<Feature>GameTests.java`:
  ```java
  @GameTestHolder(Noirm.MOD_ID)
  @PrefixGameTestTemplate(false)
  public final class FeatureGameTests {
      @GameTest(template = "empty", batch = "noirm_feature_thing", timeoutTicks = 200)
      public static void thingDoesWhatItSays(GameTestHelper helper) { ...; helper.succeed(); }
  }
  ```
- `template = "empty"` always (`data/noirm/structures/empty.nbt`). The test name is the method name lower-cased; name batches `noirm_<feature>_<what>` so `test.sh <feature>` finds them.
- **Batches:** tests in one batch run side by side a few blocks apart. Anything that swings, knocks back, spawns big entities, or triggers focus/eye effects gets **its own batch**.
- **Cleanup:** the framework clears only the 3x3 area. Remove anything your test throws out of it; leftovers break later tests.
- **Space:** tests sit ~60 blocks underground. A floor bigger than 3x3: `TestKit.openFloor(helper, x0, z0, x1, z1, height)`. Counting or fighting what's around: `TestKit.clearStrays(helper)` first.
- **Entity ticking:** before looking for entities a few blocks away, wait until `level.isPositionEntityTicking(pos)` (use `helper.succeedWhen` / `runAfterDelay`).
- **Players:** `helper.makeMockPlayer()` (or `makeMockServerPlayerInLevel()` when packets are sent); packets to mock players go through `TestNetHandler`.
- **Fixed clans:** use `TestClans.PLAIN`, `FIRE`, `SHARINGAN`, `BYAKUGAN`, `EVERY_NATURE` so random rolls never change timings.
- **Config overrides:** `NoirmConfig.X.set(...)` in a test; `NoirmConfig.resetAll()` after.
- Data checks (every JSON parses, lang keys exist) read files via `ModList.get().getModFileById(Noirm.MOD_ID).getFile().findResource(...)` (see `ToolingGameTests.keys`).
- Add a test for each new mechanic where you can. Client-only things (renderers, HUD) can't be game-tested: use the client smoke test.

## Known flaky tests
Listed in `docs/STATUS.md`. One of them failing once, then passing on the rerun, is accepted; anything else is a real failure.
