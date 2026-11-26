#!/usr/bin/env python3
"""Read a big Markdown doc one section at a time.

  python3 tools/dev/docsec.py docs/ABURAME.md                 the outline: every heading with its line and size
  python3 tools/dev/docsec.py docs/ABURAME.md "hive"          only the section(s) whose heading contains "hive"
  python3 tools/dev/docsec.py docs/COMBAT_DESIGN.md "Fighting styles" --depth 2

A section runs from its heading to the next heading of the same or a higher level (so a "##" section includes its
"###" subsections). Matching ignores case. Cheaper than reading a 70 KB design doc for one part of it.
"""
import re
import sys

HEAD = re.compile(r'^(#{1,6})\s+(.*)')


def headings(lines):
    out, fence = [], False
    for i, l in enumerate(lines):
        if l.startswith('```'):
            fence = not fence
        m = None if fence else HEAD.match(l)
        if m:
            out.append((i, len(m.group(1)), m.group(2).strip()))
    return out


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    path = argv[0]
    depth = 6
    if '--depth' in argv:
        depth = int(argv[argv.index('--depth') + 1])
        argv = argv[:argv.index('--depth')] + argv[argv.index('--depth') + 2:]
    lines = open(path, encoding='utf-8').read().split('\n')
    hs = headings(lines)

    def end_of(k):
        i, lvl, _ = hs[k]
        for j in range(k + 1, len(hs)):
            if hs[j][1] <= lvl:
                return hs[j][0]
        return len(lines)
    if len(argv) == 1:
        for k, (i, lvl, title) in enumerate(hs):
            if lvl <= depth:
                size = sum(len(l) + 1 for l in lines[i:end_of(k)])
                print(f'{i + 1:5d}  {"  " * (lvl - 1)}{title}  ({(size + 1023) // 1024} KB)')
        return 0
    want = argv[1].lower()
    found = False
    for k, (i, lvl, title) in enumerate(hs):
        if want in title.lower():
            found = True
            print('\n'.join(lines[i:end_of(k)]).rstrip())
            print()
    if not found:
        print(f'no heading contains {argv[1]!r}; run without a section to see the outline')
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
