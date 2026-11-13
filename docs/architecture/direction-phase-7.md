# Direction: Pin as a device agent (Phases 7 and 8)

Status: direction recorded 2026-11-14 (maintainer). Not a spec. Each item below gets a
spec and an ADR before code, after Phase 6 ships.

## What the maintainer asked for

1. Pin works like a coding agent for the phone: it reasons, plans, acts, notices when it
   is stuck or hit an error, re-plans and tries again.
2. Pin can use every Android capability an app may have: read and write files and media,
   contacts, messages, send messages natively, call other apps' APIs, through a safe
   script runner.
3. Pin connects to the Google ecosystem the user already uses (Calendar, Gmail, Tasks,
   Notes, more) and to the phone's radios (join Wi-Fi, pair and hand off Bluetooth).
4. Pin remembers the user: builds context, learns habits and patterns, adapts.
5. Long-running tasks: "keep scrolling Instagram reels" loops until the user says stop,
   and Pin judges when to scroll.
6. Hard guardrails: data-loss actions (reset, delete, and the like) must never be
   triggered by any route.
7. A visible hand on the overlay shows Pin tapping, holding, scrolling and interacting.

## Constraints that already bind these

- Everything still goes through the action registry and `ActionOrchestrator` (AGENTS.md).
  New capabilities are new registry tools, not a bypass.
- No API key ships (ADR-0004). Google access uses the user's own OAuth client and scopes.
- Audio, screens and memory stay local (ADR-0005). Memory gets the same viewer, export,
  delete and retention controls as history.
- The sensitivity gate (blueprint 7.6) stays. PIN-72 adds a deny list above it: some
  targets are refused, not confirmed.
- "Safe script runner" means a small typed composition of registry tools with budgets,
  not arbitrary code. Scripts that write or send are shown as a dry run first.
- Android limits apply: SMS and call roles need the user to pick Pin as the default or
  grant runtime permissions; Wi-Fi connect uses suggestion and settings-panel APIs;
  Bluetooth pairing needs the companion or user-visible pairing flow. Each spec checks
  the current API before promising behaviour (ground rule 6).

## Open questions to settle in the specs

- Which of these need an on-device model versus the cloud planner, given privacy.
- How memory is scoped (per app, per routine) and how the user corrects it.
- What "stuck" means per task type and how many re-plans before asking the user.
- Whether the visible hand is also the confirmation surface ("tap here?").
