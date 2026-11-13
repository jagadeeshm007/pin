---
description: Draft the spec for a roadmap task (stage 1)
argument-hint: PIN-<n>
---

Draft the spec for $ARGUMENTS.

1. Read the task line in `docs/roadmap.md` and the blueprint sections it names in `docs/architecture/blueprint.md`.
2. Create `docs/specs/$ARGUMENTS-<slug>/spec.md` from `docs/sdlc/templates/spec.md`.
3. Write numbered, testable acceptance criteria (WHEN ... THE app SHALL ...). Every number comes from the blueprint or `docs/sdlc/budgets.md`; do not invent targets.
4. List open questions instead of guessing. Leave `Status: Draft`.
5. Write no code. Stop and ask the maintainer to approve.
