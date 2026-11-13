---
description: Break an approved plan into ordered, testable tasks (stage 3)
argument-hint: PIN-<n>
---

Write `tasks.md` for $ARGUMENTS from `docs/sdlc/templates/tasks.md`.

1. Confirm `plan.md` says `Status: Approved`.
2. Each task is one commit, traces to an AC, and names the test that proves it.
3. Order by dependency; mark parallel tasks `[P]`.
4. Then create the branch `feat/$ARGUMENTS-<slug>` (or the right type) from `main`.
