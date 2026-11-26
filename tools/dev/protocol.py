#!/usr/bin/env python3
"""PROTOCOL_VERSION helper (network/NoirmNetwork.java).

  python3 tools/dev/protocol.py            print the current protocol
  python3 tools/dev/protocol.py bump       raise it by one (do this once per branch that changes packets)
  python3 tools/dev/protocol.py check [base]
                                           compare with the base branch's value (default origin/main): warns
                                           when packets changed but the number isn't higher, or when it equals
                                           the base's (two branches picking the same number). Exit 1 on a warning.
  python3 tools/dev/protocol.py resolve    after a merge conflict on that line: keep the higher of the two
                                           numbers plus one (both branches changed packets) and `git add` the file
                                           if no other conflict is left in it

The rule: a merge of two branches that both bumped the protocol ends one above the higher of the two, so the
merged build never shares a number with either branch's jar.
"""
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
REL = 'src/main/java/com/noirm/network/NoirmNetwork.java'
PATH = os.path.join(ROOT, REL)
LINE = re.compile(r'(private static final String PROTOCOL_VERSION = ")(\d+)(";)')


def main(argv):
    text = open(PATH, encoding='utf-8').read()
    cmd = argv[0] if argv else 'show'
    if cmd == 'show':
        m = LINE.search(text)
        print(m.group(2) if m else 'conflicted (run: protocol.py resolve)')
        return 0
    if cmd == 'bump':
        m = LINE.search(text)
        new = int(m.group(2)) + 1
        open(PATH, 'w', encoding='utf-8').write(LINE.sub(lambda x: f'{x.group(1)}{new}{x.group(3)}', text, count=1))
        print(f'protocol {m.group(2)} -> {new}')
        return 0
    if cmd == 'check':
        base = argv[1] if len(argv) > 1 else 'origin/main'
        m = LINE.search(text)
        if not m:
            print('conflicted (run: protocol.py resolve)')
            return 1
        ours = int(m.group(2))
        got = subprocess.run(['git', 'show', f'{base}:{REL}'], cwd=ROOT, capture_output=True, text=True)
        if got.returncode != 0:
            print(f'could not read {REL} on {base}: {got.stderr.strip()}')
            return 1
        bm = LINE.search(got.stdout)
        theirs = int(bm.group(2)) if bm else -1
        diff = subprocess.run(['git', 'diff', '--name-only', f'{base}...HEAD', '--', 'src/main/java/com/noirm/network/'],
                              cwd=ROOT, capture_output=True, text=True).stdout.split()
        dirty = subprocess.run(['git', 'diff', '--name-only', 'HEAD', '--', 'src/main/java/com/noirm/network/'],
                               cwd=ROOT, capture_output=True, text=True).stdout.split()
        packets = sorted(set(f for f in diff + dirty if not f.endswith('NoirmNetwork.java')))
        print(f'protocol: this branch {ours}, {base} {theirs}')
        if ours < theirs:
            print(f'WARNING: lower than {base}. Run protocol.py bump (or resolve after merging {base}).')
            return 1
        if packets and ours == theirs:
            print('WARNING: network files changed but PROTOCOL_VERSION equals the base. Run: protocol.py bump')
            for f in packets:
                print('  changed:', f)
            return 1
        if ours > theirs + 1:
            print(f'note: more than one above {base}; fine after a merge, otherwise one bump per branch is enough')
        print('OK')
        return 0
    if cmd == 'resolve':
        block = re.compile(r'<<<<<<< [^\n]*\n(.*?)=======\n(.*?)>>>>>>> [^\n]*\n', re.S)
        changed = False

        def fix(m):
            nonlocal changed
            ours, theirs = LINE.search(m.group(1)), LINE.search(m.group(2))
            rest_o, rest_t = LINE.sub('', m.group(1)).strip(), LINE.sub('', m.group(2)).strip()
            if not ours or not theirs or rest_o != rest_t:
                return m.group(0)
            changed = True
            new = max(int(ours.group(2)), int(theirs.group(2))) + 1
            print(f'protocol: ours {ours.group(2)}, theirs {theirs.group(2)} -> {new}')
            return LINE.sub(lambda x: f'{x.group(1)}{new}{x.group(3)}', m.group(1), count=1)
        text = block.sub(fix, text)
        open(PATH, 'w', encoding='utf-8').write(text)
        if not changed:
            print('no PROTOCOL_VERSION conflict found')
        if '<<<<<<<' in text:
            print('other conflicts remain in NoirmNetwork.java: fix them by hand')
            return 1
        subprocess.run(['git', 'add', REL], cwd=ROOT, check=False)
        return 0
    print(__doc__)
    return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
