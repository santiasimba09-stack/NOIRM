# Helper brief template

Copy, fill in, delete what doesn't apply. Short briefs start faster: point at files, don't paste them.

```
You are working on NOIRM (Forge 1.20.1 mod, Java 17, package com.noirm). The owner is new to programming:
explain plainly. Follow the skill `helper-rules` (.claude/skills/helper-rules/SKILL.md). Base:
`git reset --hard <branch>` (NOIRM v0.N, protocol P). Session URL for commit trailers: <url>.

## Goal
<one or two sentences, in the owner's words if possible>

## Read
- docs/INDEX.md, then: <the 1-3 docs or sections that matter, e.g. docs/INUZUKA.md "The model">
- Skills: <add-jutsu / add-entity / blockbench-model / ...>
- Code: <the classes to start from, e.g. hound/HoundGrowth, client/hound/HoundRenderer>

## Build
1. <step>
2. <step>
Numbers to start from: <...> (tunable ones get a row in docs/TUNING.md)

## Rules for this task
- Don't change: <systems to leave alone>
- Packets: <expected / not expected>; if they change, `tools/dev/protocol.py bump` and say so.
- Tests: <what must be tested>; `tools/dev/test.sh <word>` while working, `tools/dev/test.sh --twice` at the end.
- No screenshots needed (optional self-check only).

## Report
Plain language: what changed, tests (names, counts, both full runs), protocol, files touched, open questions,
and a "what to test" list for the owner (try → look for).
```
