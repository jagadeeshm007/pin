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
8. Connectors and MCP natively: Pin can add MCP servers and work with their tools, like
   other agent harnesses do.
9. Skills: user-added instruction packs that other harnesses support, usable in Pin.
10. Scripts on the phone: where running code achieves an action, Pin should be able to.
11. Reach as deep into the Android ecosystem as an app can, so the user gets a full
    agentic experience through Pin.

## Running code on the phone: what is possible

- Possible: an embedded interpreter in the APK (QuickJS for JavaScript, about 1 MB, or
  Lua) with Pin's registry tools exposed as host functions. The planner writes short
  scripts with loops and conditions; the runner enforces step and time budgets, no
  network, no file access except through tools, and shows a dry run before writes or
  sends. This is PIN-75. The interpreter needs an ADR (dependency and APK cost).
- Not possible or not allowed: shell commands outside the app sandbox (no root);
  loading downloaded native code (Android 17 and ADR-0009 rule); compiling Kotlin or
  Java on the device; running other apps' code.
- Alternative for power users: hand off to a Termux-style environment the user installs,
  through its intent API. Optional, never required.

## MCP and skills

- MCP: Pin is an MCP client, never a server that exposes the phone to the network.
  Servers are added by the user with their own credentials (SecretStore); each server's
  tools appear in the registry with the same validation, sensitivity gate and deny list
  as device actions. Remote servers over HTTPS; local servers only on the user's network
  and only when the user adds them.
- Skills: Markdown instruction packs with a name, a description, an optional tool
  allowlist and examples, stored locally and shareable as files. Loaded into the planner
  prompt when a task matches. Keys and secrets are rejected on import.

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
