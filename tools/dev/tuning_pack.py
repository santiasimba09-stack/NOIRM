#!/usr/bin/env python3
"""Copy NOIRM data files into a world's tuning data pack, so you can edit them and `/reload` in game.

  python3 tools/dev/tuning_pack.py WORLD_FOLDER data/noirm/clans/uzumaki.json [more files...]
  python3 tools/dev/tuning_pack.py WORLD_FOLDER --list

WORLD_FOLDER is the save folder, e.g. `.minecraft/saves/My World` (or `run/saves/<name>` when you play from
`./gradlew runClient`). The tool creates `WORLD_FOLDER/datapacks/noirm_tuning/` (with its pack.mcmeta) and copies
each file there at the same path. Edit the copies, then type `/reload` in game. A file in the pack replaces the
mod's own file of the same path; delete the copy to go back to the mod's numbers. See docs/TUNING.md.
"""
import json
import os
import shutil
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    world, files = argv[0], argv[1:]
    if not os.path.isfile(os.path.join(world, 'level.dat')):
        print(f'{world} is not a world folder (no level.dat)')
        return 1
    pack = os.path.join(world, 'datapacks', 'noirm_tuning')
    if files == ['--list']:
        for dirpath, _, fs in os.walk(os.path.join(pack, 'data')):
            for f in fs:
                print(os.path.relpath(os.path.join(dirpath, f), pack))
        return 0
    os.makedirs(pack, exist_ok=True)
    meta = os.path.join(pack, 'pack.mcmeta')
    if not os.path.exists(meta):
        with open(meta, 'w', encoding='utf-8') as f:
            json.dump({'pack': {'pack_format': 15, 'description': 'NOIRM tuning (your own numbers)'}}, f, indent=2)
    for rel in files:
        rel = rel.replace('\\', '/').lstrip('/')
        if rel.startswith('src/main/resources/'):
            rel = rel[len('src/main/resources/'):]
        if not rel.startswith('data/'):
            print(f'skip {rel}: only data/... files can be tuned this way (assets need a resource pack)')
            continue
        src = os.path.join(RES, rel)
        if not os.path.isfile(src):
            print(f'skip {rel}: no such file in the mod')
            continue
        dst = os.path.join(pack, rel)
        if os.path.exists(dst):
            print(f'kept your copy of {rel} (already in the pack)')
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copyfile(src, dst)
        print(f'copied {rel} -> {os.path.relpath(dst, world)}')
    print('Edit the copies, then type /reload in game.')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
