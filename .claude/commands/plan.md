---
description: Write the technical plan for an approved spec (stage 2)
argument-hint: PIN-<n>
---

Write the plan for $ARGUMENTS.

1. Confirm `docs/specs/$ARGUMENTS-*/spec.md` says `Status: Approved`. If not, stop.
2. Read the relevant code, ADRs and `.claude/rules/`. Search before proposing new code.
3. Create `plan.md` from `docs/sdlc/templates/plan.md`: approach, files, contracts, dependencies with APK cost, alternatives, risks, test strategy.
4. If a technology choice is new or reverses an ADR, draft the ADR from `docs/sdlc/templates/adr.md` with `Status: Proposed`.
5. Write no code. Stop and ask the maintainer to approve.
