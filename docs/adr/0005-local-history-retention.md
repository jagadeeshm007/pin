# 0005: Full local history with configurable retention

Status: Accepted
Date: 2026-11-13

## Context

Users must be able to see what the agent heard, saw, reasoned and did. Storage
on phones is limited.

## Decision

Record sessions, tasks, steps, screen snapshots, screenshots, planner requests
and responses, reasoning summaries and verification results in Room plus
app-private files. A daily job deletes unpinned sessions older than the retention
window (default 7 days; options 1, 3, 7, 14, 30 days, forever). Keys and
password-field contents are never stored. Providers return reasoning summaries,
not raw chains of thought; the viewer labels them as summaries.

## Consequences

- Full auditability and a ready dataset for evals.
- Storage budget per step must be enforced (`docs/sdlc/budgets.md`).
