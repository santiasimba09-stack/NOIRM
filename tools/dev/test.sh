#!/usr/bin/env bash
# The quick game-test runner. Runs only the game tests matching the words you give, and prints a short summary
# (counts + failure lines) instead of the whole log.
#
#   tools/dev/test.sh hound              # every test whose name, batch or class contains "hound"
#   tools/dev/test.sh hound,aburame      # several words (comma-separated)
#   tools/dev/test.sh =noirm_hound_spawn # an exact test or batch name
#   tools/dev/test.sh                    # the full suite (the release bar: run it twice)
#   tools/dev/test.sh --twice            # the full suite twice in a row, stops at the first red run
#
# The full log is kept in build/gametest-<n>.log. Uses the shared heavy-Gradle lock, like every helper.
set -u
cd "$(dirname "$0")/../.."
twice=0
if [ "${1:-}" = "--twice" ]; then twice=1; shift; fi
filter="${1:-}"
runs=1; [ $twice = 1 ] && runs=2
mkdir -p build
status=0
for n in $(seq 1 $runs); do
  log="build/gametest-$n.log"
  echo "== game tests${filter:+ matching '$filter'} (run $n/$runs), log: $log"
  flock /tmp/noirm-gradle-heavy.lock ./gradlew runGameTestServer ${filter:+-PnoirmTests="$filter"} > "$log" 2>&1
  python3 tools/dev/gtsummary.py "$log"
  status=$?
  [ $status -ne 0 ] && break
done
exit $status
