#!/usr/bin/env python3
"""The lang tool: keeps en_us, es_es and es_mx in step.

  python3 tools/dev/lang.py check
      Same key set in all three files, no duplicate keys, the same %s / %d placeholders per key. Exit 1 if not.

  python3 tools/dev/lang.py add KEY "English" "Español" ["Español de México"]
      Adds (or updates) one key in all three files. es_mx defaults to the es_es text.

  python3 tools/dev/lang.py add-file new_keys.json
      Adds many keys at once. The file maps KEY -> {"en_us": ..., "es_es": ..., "es_mx": ...} (es_mx optional),
      or KEY -> [en, es] / [en, es_es, es_mx].

  python3 tools/dev/lang.py get KEY           show a key in all three files
  python3 tools/dev/lang.py find WORD [N]     keys or English texts containing WORD (first N, default 20)

  python3 tools/dev/lang.py merge BASE OURS THEIRS [OUT]
      A 3-way merge of one lang file (key by key: both sides' new keys are kept, a change on one side wins,
      a key deleted on one side and untouched on the other is deleted). Writes OUT (default OURS). Exit 1 and
      lists the keys if both sides changed the same key differently (ours is kept for those).

  python3 tools/dev/lang.py resolve
      After a `git merge` that conflicted on lang files: merges each conflicted lang file from the git index
      (base/ours/theirs) and `git add`s it.

  python3 tools/dev/lang.py install-driver
      Makes git use `merge` above automatically for the lang files in this clone (.gitattributes names the
      driver; this sets the command in .git/config). Run once per clone.

Files are written exactly as they are kept: two-space JSON, UTF-8, no ASCII escaping, a final newline.
"""
import json
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
LANG_DIR = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'noirm', 'lang')
LANGS = ['en_us', 'es_es', 'es_mx']


def path(lang):
    return os.path.join(LANG_DIR, lang + '.json')


def load_pairs(text):
    return json.loads(text, object_pairs_hook=lambda pairs: pairs)


def load(p):
    with open(p, encoding='utf-8') as f:
        return json.loads(f.read())


def dump(d, p):
    with open(p, 'w', encoding='utf-8', newline='\n') as f:
        f.write(json.dumps(d, ensure_ascii=False, indent=2) + '\n')


def args_used(text):
    """The argument numbers a format string uses (%s counts up; %2$s names argument 2), so reordering is fine."""
    used, n = set(), 0
    for m in re.finditer(r'%(?:(\d+)\$)?([sdf%])', text):
        if m.group(2) == '%':
            continue
        if m.group(1):
            used.add(int(m.group(1)))
        else:
            n += 1
            used.add(n)
    return used


def check():
    ok = True
    data = {}
    for lang in LANGS:
        with open(path(lang), encoding='utf-8') as f:
            text = f.read()
        try:
            pairs = load_pairs(text)
        except json.JSONDecodeError as e:
            print(f'{lang}: not valid JSON: {e}')
            return 1
        seen, dups = set(), []
        for k, _ in pairs:
            if k in seen:
                dups.append(k)
            seen.add(k)
        if dups:
            ok = False
            print(f'{lang}: duplicate keys: {", ".join(dups[:10])}' + (' …' if len(dups) > 10 else ''))
        data[lang] = dict(pairs)
    base = set(data['en_us'])
    for lang in LANGS[1:]:
        missing = sorted(base - set(data[lang]))
        extra = sorted(set(data[lang]) - base)
        if missing:
            ok = False
            print(f'{lang}: missing {len(missing)} keys: {", ".join(missing[:10])}' + (' …' if len(missing) > 10 else ''))
        if extra:
            ok = False
            print(f'{lang}: {len(extra)} keys not in en_us: {", ".join(extra[:10])}' + (' …' if len(extra) > 10 else ''))
    bad = []
    for k, en in data['en_us'].items():
        if not isinstance(en, str):
            continue
        want = args_used(en)
        for lang in LANGS[1:]:
            v = data[lang].get(k)
            if isinstance(v, str) and args_used(v) != want:
                bad.append(f'{k} ({lang})')
    if bad:
        ok = False
        print(f'placeholders differ from en_us in {len(bad)} texts: {", ".join(bad[:10])}' + (' …' if len(bad) > 10 else ''))
    if ok:
        print(f'lang OK: {len(base)} keys in each of {", ".join(LANGS)}')
    return 0 if ok else 1


def add_many(entries):
    """entries: KEY -> (en, es_es, es_mx)."""
    files = {lang: load(path(lang)) for lang in LANGS}
    for key, texts in entries.items():
        for lang, text in zip(LANGS, texts):
            files[lang][key] = text
    for lang in LANGS:
        dump(files[lang], path(lang))
    print(f'added/updated {len(entries)} keys in {", ".join(LANGS)}')


def norm(v):
    if isinstance(v, dict):
        en, es = v['en_us'], v.get('es_es', v.get('es'))
        return en, es, v.get('es_mx', es)
    if isinstance(v, list):
        return v[0], v[1], v[2] if len(v) > 2 else v[1]
    raise SystemExit('each value must be {"en_us","es_es"[,"es_mx"]} or [en, es(, es_mx)]')


def merge_dicts(base, ours, theirs):
    out = {}
    conflicts = []
    keys = list(ours)
    # theirs' new keys go right after the key that precedes them in theirs
    tkeys = list(theirs)
    for i, k in enumerate(tkeys):
        if k in ours or k in base:
            continue
        prev = next((tkeys[j] for j in range(i - 1, -1, -1) if tkeys[j] in keys), None)
        keys.insert(keys.index(prev) + 1 if prev else len(keys), k)
    for k in keys:
        b, o, t = base.get(k), ours.get(k), theirs.get(k)
        if o == t:
            v = o
        elif o == b:
            v = t
        elif t == b:
            v = o
        else:
            conflicts.append(k)
            v = o if o is not None else t
        if v is not None:
            out[k] = v
    return out, conflicts


def merge(base_p, ours_p, theirs_p, out_p=None):
    def read(p):
        try:
            return load(p)
        except (OSError, json.JSONDecodeError):
            return {}
    merged, conflicts = merge_dicts(read(base_p), read(ours_p), read(theirs_p))
    dump(merged, out_p or ours_p)
    if conflicts:
        print(f'lang merge: {len(conflicts)} keys changed on both sides (kept ours): {", ".join(conflicts[:20])}', file=sys.stderr)
        return 1
    return 0


def git(*args, check=True):
    return subprocess.run(['git', *args], cwd=ROOT, capture_output=True, text=True, check=check).stdout


def resolve():
    conflicted = [p for p in git('diff', '--name-only', '--diff-filter=U').split() if p.startswith('src/main/resources/assets/noirm/lang/')]
    if not conflicted:
        print('no conflicted lang files')
        return 0
    status = 0
    for rel in conflicted:
        stages = {}
        for n in (1, 2, 3):
            stages[n] = subprocess.run(['git', 'show', f':{n}:{rel}'], cwd=ROOT, capture_output=True, text=True).stdout
        tmp = []
        for n in (1, 2, 3):
            p = os.path.join(ROOT, 'build', f'lang-merge-{n}.json')
            os.makedirs(os.path.dirname(p), exist_ok=True)
            with open(p, 'w', encoding='utf-8') as f:
                f.write(stages[n] or '{}')
            tmp.append(p)
        status |= merge(tmp[0], tmp[1], tmp[2], os.path.join(ROOT, rel))
        git('add', rel)
        print(f'merged {rel}')
    return status


def install_driver():
    git('config', 'merge.noirm-lang.name', 'NOIRM lang 3-way merge')
    git('config', 'merge.noirm-lang.driver', 'python3 tools/dev/lang.py merge %O %A %B')
    print('git merge driver "noirm-lang" installed for this clone (see .gitattributes)')
    return 0


def main(argv):
    if not argv or argv[0] in ('-h', '--help', 'help'):
        print(__doc__)
        return 0
    cmd, args = argv[0], argv[1:]
    if cmd == 'check':
        return check()
    if cmd == 'add' and len(args) in (3, 4):
        add_many({args[0]: (args[1], args[2], args[3] if len(args) == 4 else args[2])})
        return check()
    if cmd == 'add-file' and len(args) == 1:
        add_many({k: norm(v) for k, v in load(args[0]).items()})
        return check()
    if cmd == 'get' and len(args) == 1:
        for lang in LANGS:
            print(f'{lang}: {load(path(lang)).get(args[0])!r}')
        return 0
    if cmd == 'find' and len(args) in (1, 2):
        word, n = args[0].lower(), int(args[1]) if len(args) == 2 else 20
        hits = [(k, v) for k, v in load(path('en_us')).items() if word in k.lower() or word in str(v).lower()]
        for k, v in hits[:n]:
            print(f'{k}: {v}')
        print(f'({len(hits)} matches)')
        return 0
    if cmd == 'merge' and len(args) in (3, 4):
        return merge(*args)
    if cmd == 'resolve':
        return resolve()
    if cmd == 'install-driver':
        return install_driver()
    print(__doc__)
    return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
