#!/usr/bin/env python3
"""Summarise a game-test log: pass/fail counts and only the failure lines.

    python3 tools/dev/gtsummary.py build/gametest.log
    ./gradlew runGameTestServer 2>&1 | python3 tools/dev/gtsummary.py -

Prints at most a dozen lines instead of a 3,000-line log. Exit code: 0 all passed, 1 failures,
2 the run never finished (crash, compile error: the last error lines are shown).
"""
import re
import sys

COMPLETE = re.compile(r'=+ (\d+) GAME TESTS COMPLETE')
FAILED_LINE = re.compile(r'\[minecraft/LogTestReporter\]: (.*)$')
REQ_FAILED = re.compile(r'(\d+) required tests failed')
OPT_FAILED = re.compile(r'(\d+) optional tests failed')
ALL_PASSED = re.compile(r'All (\d+) required tests passed')
SELECTED = re.compile(r'\[noirm\.tests\] filter (.*) selected (\d+) of (\d+) game tests')
ERR = re.compile(r'(error:|Exception|FAILURE:|BUILD FAILED|What went wrong|Could not|\.java:\d+: )')


def main(path):
    text = sys.stdin.read() if path == '-' else open(path, encoding='utf-8', errors='replace').read()
    lines = text.splitlines()
    total = req = opt = None
    passed_all = False
    failures = []
    selected = None
    for line in lines:
        m = SELECTED.search(line)
        if m:
            selected = f'filter {m.group(1)}: {m.group(2)} of {m.group(3)} tests'
        m = COMPLETE.search(line)
        if m:
            total = int(m.group(1))
        m = REQ_FAILED.search(line)
        if m:
            req = int(m.group(1))
        m = OPT_FAILED.search(line)
        if m:
            opt = int(m.group(1))
        if ALL_PASSED.search(line):
            passed_all = True
        m = FAILED_LINE.search(line)
        if m and ' failed!' in m.group(1):
            failures.append(m.group(1)[:300])
    if selected:
        print(selected)
    if total is None:
        print('RUN DID NOT FINISH (no "GAME TESTS COMPLETE" line). Last error lines:')
        errs = [l for l in lines if ERR.search(l)]
        for l in (errs or lines)[-15:]:
            print('  ' + l[:300])
        return 2
    nfail = req or 0
    print(f'{total} game tests: {total - nfail - (opt or 0)} passed, {nfail} required failed'
          + (f', {opt} optional failed' if opt else ''))
    for f in failures:
        print('  FAIL ' + f)
    if passed_all or nfail == 0:
        return 0
    return 1


if __name__ == '__main__':
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
