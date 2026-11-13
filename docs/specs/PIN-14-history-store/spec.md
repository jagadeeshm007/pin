# PIN-14: History store and retention

Status: Approved (maintainer instruction "start building" and "continue", 2026-11-13; open to revision)
Owner: maintainer
Roadmap phase: 1
Blueprint sections: 7.7 (session history), 11 (privacy)
ADR: 0005 (full local history with configurable retention)

## Problem

Users must be able to see everything Pin heard, saw and did, and the agent rule says
every step is recorded before its action runs. Today the orchestrator emits events
that nothing stores.

## Scope

- In: Room database (sessions, tasks, steps); screen snapshots stored as files;
  an `ActionRecorder` that writes each orchestrator event; retention that deletes
  unpinned sessions older than the window (default 7 days) with their files; pin and
  delete; debug commands to list history and run retention.
- Out (later tasks): planner calls and reasoning summaries (PIN-40), screenshots
  (PIN-47), transcripts (PIN-23), the history viewer and retention settings UI
  (PIN-50), export.

## Acceptance criteria

- AC-1: WHEN the orchestrator records an event THE store SHALL persist it before `record` returns, so the step exists before the device is touched.
- AC-2: WHEN an attempt starts or succeeds THE store SHALL save the screen snapshot as a compressed file linked to the step; password fields SHALL have no text in the file.
- AC-3: WHEN retention runs THE store SHALL delete every unpinned session that ended more than the window ago, its tasks, steps and files, and SHALL keep pinned and still-open sessions.
- AC-4: WHEN a session is deleted THE store SHALL remove its rows and its file directory together.
- AC-5: Database and file access SHALL NOT run on the main thread.
- AC-6: THE schema SHALL be exported to `app/schemas/` and covered by a test that opens the database on a device.

## Budgets touched

| Budget | Target |
| --- | --- |
| History per agent step without screenshot | < 100 KB |
| APK size | record the delta (Room, KSP, kotlinx.serialization) |

## Privacy and safety

Data stays in app-private storage; backup is already off (PIN-1). No keys, no
password text. Retention deletes files and rows.

## Open questions

- Q1: Retention runs when the accessibility service connects and at most once a day
  after that, instead of a WorkManager job (avoids a dependency). Revisit if users keep
  the service connected for weeks without restarts.
