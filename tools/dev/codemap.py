#!/usr/bin/env python3
"""A compact, always-fresh code map: each package and its classes, biggest first.

  python3 tools/dev/codemap.py             every package, one line each (class names, size in lines)
  python3 tools/dev/codemap.py hound       only packages whose name contains "hound"
  python3 tools/dev/codemap.py hound -d    plus each class's first javadoc sentence
  python3 tools/dev/codemap.py -r          the data and asset folders and how many files each holds

Cheaper than opening files to find your way: the whole map is a few KB.
"""
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
JAVA = os.path.join(ROOT, 'src', 'main', 'java', 'com', 'noirm')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
DOC = re.compile(r'/\*\*(.*?)\*/\s*(?:@[\w.]+(?:\([^)]*\))?\s*)*(?:public |final |abstract |sealed )*(?:class|interface|enum|record)\s', re.S)


def first_sentence(text):
    m = DOC.search(text)
    if not m:
        return ''
    s = re.sub(r'\s*\*\s?', ' ', m.group(1))
    s = re.sub(r'\{@\w+ ([^}]*)\}', r'\1', s)
    s = re.sub(r'<[^>]+>', '', s).strip()
    s = re.split(r'(?<=[.!?])\s', s, maxsplit=1)[0]
    return s[:160]


def java_map(filt, docs):
    out = []
    for dirpath, _, files in sorted(os.walk(JAVA)):
        classes = []
        for f in files:
            if f.endswith('.java'):
                p = os.path.join(dirpath, f)
                text = open(p, encoding='utf-8', errors='replace').read()
                classes.append((text.count('\n'), f[:-5], first_sentence(text) if docs else ''))
        if not classes:
            continue
        pkg = 'com.noirm' + dirpath[len(JAVA):].replace(os.sep, '.')
        if filt and filt not in pkg.lower():
            continue
        classes.sort(reverse=True)
        total = sum(c[0] for c in classes)
        if docs:
            out.append(f'{pkg} ({len(classes)} classes, {total} lines)')
            for n, name, d in classes:
                out.append(f'  {name} ({n}){": " + d if d else ""}')
        else:
            out.append(f'{pkg} ({total}): ' + ', '.join(f'{name} {n}' for n, name, _ in classes))
    return out


def res_map():
    out = []
    for top in ('data/noirm', 'assets/noirm'):
        base = os.path.join(RES, top)
        for name in sorted(os.listdir(base)):
            p = os.path.join(base, name)
            if os.path.isdir(p):
                n = sum(len(fs) for _, _, fs in os.walk(p))
                subs = sorted(d for d in os.listdir(p) if os.path.isdir(os.path.join(p, d)))
                out.append(f'{top}/{name}/ ({n} files)' + (f': {", ".join(subs[:12])}' + (' …' if len(subs) > 12 else '') if subs else ''))
    return out


def main(argv):
    docs = '-d' in argv
    if '-r' in argv:
        print('\n'.join(res_map()))
        return 0
    words = [a for a in argv if not a.startswith('-')]
    print('\n'.join(java_map(words[0].lower() if words else None, docs)))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
