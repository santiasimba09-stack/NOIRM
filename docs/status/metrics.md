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
(fill in for the clones-and-training wave)
