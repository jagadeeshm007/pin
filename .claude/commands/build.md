---
description: Implement the next unchecked task test-first (stage 4)
argument-hint: PIN-<n>
---

Build the next unchecked task in `docs/specs/$ARGUMENTS-*/tasks.md`.

1. Write the failing test named in the task. Run it and see it fail for the right reason.
2. Write the smallest code that passes. Refactor.
3. Run `make check | tail -40`. Fix until green; never weaken a check.
4. Commit: `<type>(<scope>): <what> [$ARGUMENTS]`. Tick the task.
5. If the task cannot be done as planned, stop and explain; do not change scope silently.
6. Continue with the next task only if the maintainer asked for more than one.
