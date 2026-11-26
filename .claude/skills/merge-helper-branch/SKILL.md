---
name: merge-helper-branch
description: Merge a helper's worktree branch into the main NOIRM branch, resolving the usual conflicts (the three lang JSONs, PROTOCOL_VERSION, docs) and verifying in the cheapest order. Use when the coordinator integrates helper work.
---

# Merge a helper branch

## Once per clone
`python3 tools/dev/lang.py install-driver` makes git merge the lang files key by key by itself (`.gitattributes`
names the `noirm-lang` driver; this sets its command in `.git/config`, shared by all worktrees).

## Steps
1. Read the helper's report: what changed, whether the protocol was bumped, tests run.
2. `git merge --no-ff <helper-branch>` on the main branch (or the integration branch).
3. **Conflicts:**
   - **Lang files** (`assets/noirm/lang/*.json`): with the driver installed they merge themselves. Without it, or after a
     manual merge: `python3 tools/dev/lang.py resolve` (merges each conflicted lang file from git's base/ours/theirs and
     `git add`s it). Keys changed differently on both sides are listed (ours kept): fix those by hand.
   - **PROTOCOL_VERSION** (`network/NoirmNetwork.java`): `python3 tools/dev/protocol.py resolve`. Rule: both branches
     bumped → one above the higher number, so the merged jar never shares a number with either branch's jar.
     Before merging (and after), run `python3 tools/dev/protocol.py check [base]` (default `origin/main`): it warns
     when the branch's number isn't above the base's although network files changed (two branches picked the same
     number last wave). A warning exits 1.
   - **Generated files** (`docs/COMMANDS.md`, animations, textures): don't hand-merge; rerun the generator
     (`python3 tools/docs/gen_commands.py`, the `tools/*gen` script) after the code merge.
   - **Docs** (STATUS, CHANGELOG): the coordinator's own; keep main's version and add the helper's news by hand.
   - Code: resolve normally; keep both sides' entries in registries and `Map.ofEntries` lists.
4. **Verify, cheapest first**, stop at the first failure:
   1. `python3 tools/dev/lang.py check` (key sets, duplicates, placeholders)
   2. `flock /tmp/noirm-gradle-heavy.lock ./gradlew compileJava -q`
   3. `tools/dev/test.sh <words for the merged features>` (both helpers' areas)
   4. Before a release only: `tools/dev/test.sh --twice` (full suite twice) and the client smoke check if entities or
      client code changed (skill `client-shot`).
5. Commit the merge with the trailers from `helper-rules`.

## After several merges
Run the full suite twice once, not after each merge. Note the merged branches for the CHANGELOG entry (skill `release`).
