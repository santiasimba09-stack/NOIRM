# Work metrics (for the owner's "did the tooling help?" review)

Usage per helper, as reported by the harness (subagent tokens, tool calls, wall time). The tooling wave landed on
2026-11-26; compare the waves after it with those before.

## Before the tooling wave
| Wave | Tokens | Tool calls | Time | Notes |
|---|---|---|---|---|
| Inuzuka hound (finish) | 687k | 279 | 96 min | plus an earlier stopped attempt |
| Creator + Shoji UI | 594k (last leg) | — | several legs | hit the 5-hour limit 4 times; screenshots as deliverables |
| Clan clothing | 477k (last leg) | — | several legs | hit the limit 3 times |
| Clan boosts + rolls | ~398k | — | several legs | hit the limit 3 times |
| Clan gaps (round 2) | 659k | 77 | 41 min | |
| Clan research (2 passes) | 275k + 305k | 53 + 6 | 19 + 5 min | read-only |
| Aburame research | 276k | 74 | 14 min | read-only |

Habits then: 3–4 helpers in parallel, full suite twice per helper, screenshots as deliverables, big docs read at start.

## The tooling wave itself
| Wave | Tokens | Tool calls | Time |
|---|---|---|---|
| Tooling | 385k | 195 | 38 min |

Startup reading per helper: about 42 KB before → about 20 KB after (a clan task: about 170 KB → 40 KB).
Quick runner: one system's tests in 2–3 min instead of about 4 min × 2 for the full suite. GitHub CI: full build +
610 tests in about 5 min, free.

## After the tooling wave
### The clones-and-training wave (2026-11-26, branch `claude/wave-clones`)
| Who | Tokens | Tool calls | Time | Notes |
|---|---|---|---|---|
| Crew helper (Sharingan + fusion lessons) | 280k | 74 | 29 min | strongest model (design + UI); ran its own full suite twice |
| Coordinator (clones, training, Gates, beasts, Byakugan, merge, docs) | not reported to itself | ~110 | one session | built six of the seven items directly |

- **Helpers:** 1 (the cap was 2). Doing the rest in the coordinator avoided briefing and merge costs; the one helper's
  merge was clean apart from both sides bumping the protocol to 39 (fixed with one more bump, to 40).
- **Full-suite runs:** 2 by the coordinator at the end (plus the helper's 2). No mid-wave full runs.
- **Quick runner:** 7 runs (gates/eggs, Hyuga ×4, clones, plus fixes), 1–25 tests each, about 2–3 min each.
- **Rework:** three red quick runs, each found in one look at the 3-line summary: the JSON `count` default (1)
  shrinking 64 Palms to one wave; the clone/gate tests needing NPC stats pinned; the internal damage being parried and
  shoving the target (fixed with a new `CombatSystem.unblockable`).
- **What helped:** `test.sh <words>` + `gtsummary.py` (the biggest win), `lang.py add` (59 keys, no hand JSON),
  `protocol.py`, the codemap, docsec.py, the skills' checklists (add-jutsu: behaviour labels, seals; add-entity
  wasn't needed).
- **What got in the way:** the protocol "bump once per branch" rule doesn't cover two branches bumping in parallel
  (both picked 39, the merge was textually clean, so nothing flagged it). Suggestion: `protocol.py check` against the
  base branch in the merge skill. The helper also notes: squash onto the base commit hash, not the branch name
  (the branch moved under it).

### The Hyūga wave (2026-11-26, branch `claude/wave-hyuga`)
| Who | Tokens | Tool calls | Time | Notes |
|---|---|---|---|---|
| One session, no helpers | not visible to itself | ~110 | ~2.5 h | numbers/data wave + two mid-wave additions (Gentle Fist arts, Byakugan lag) |

- **Quick runner:** 3 targeted runs (37, 26 and 6 tests), all green.
- **Full suite:** 3 complete runs plus one first half (4 suite passes in all): 627/629, 632/635, 633/635 on a fresh world; every failure passed on
  a targeted rerun (flamebombsticksandburns failed only on the reused `run/world`, fixed by a fresh world). A
  4th pass was stopped at the owner's request ("deliver the new version") — the "twice green in a row" bar was **not** met.
- **Rework:** one compile error (a variable name clash); no red quick runs.
- **Helped:** `test.sh <words>`, `lang.py add` (9 keys), `protocol.py`, docsec.py, the add-clan-perk skill; a game test
  that times the x-ray scan gave a real before/after number without a GPU.
- **Got in the way:** the full suite is flaky on a reused test world (different tests each run); comparing with
  `main` needed a separate worktree run. Suggestion: `test.sh --fresh` that moves `run/world` aside. The wiki page
  for the canon quote refused automated fetches.
