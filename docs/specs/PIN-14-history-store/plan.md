# PIN-14 plan

Status: Approved (maintainer instruction "continue", 2026-11-13)
Spec: ./spec.md

## Schema (Room, version 1)

```
sessions  id PK auto, started_at, ended_at NULL, pinned, label
tasks     id PK auto, session_id FK -> sessions ON DELETE CASCADE, utterance, route,
          status, started_at, ended_at NULL, summary NULL
steps     id PK auto, task_id FK -> tasks ON DELETE CASCADE, seq, kind, at,
          action TEXT, strategy NULL, attempt NULL, reason NULL, screen_file NULL
```

Indexes on `tasks.session_id`, `steps.task_id`, `sessions.ended_at`.
Files: `files/history/<sessionId>/<stepId>.json.gz` (screen snapshot as JSON).

This is the smallest schema that holds what exists today. Planner calls, transcripts
and screenshots come with their features through migrations.

## Components

| Pure (JVM tests) | Android |
| --- | --- |
| `StepMapper`: `ActionEvent` to `StepRecord` (kind, action text, strategy, reason, whether to keep the screen) | `HistoryDatabase`, DAOs, entities |
| `RetentionPolicy`: which sessions to delete for a given now and window | `HistoryStore`: sessions, tasks, steps, files, retention, pin, delete; all on `Dispatchers.IO` |
| `SnapshotCodec`: `ScreenSnapshot` to and from JSON (kotlinx.serialization) | `HistoryRecorder : ActionRecorder` |

## Dependencies

| Library | Why | APK cost |
| --- | --- | --- |
| Room 2.8.5 (runtime, ktx; compiler via KSP) | ADR-0002 storage choice; migrations and exported schema | measured in verify.md |
| KSP 2.3.12 | Room's code generator | build only |
| kotlinx-serialization-json 1.11.0 + plugin | snapshot files now, provider adapters later (ADR-0004) | measured in verify.md |
| room-testing (androidTest) | open the schema on device | test only |

No WorkManager (spec Q1).

## Test strategy

- Unit: `StepMapperTest`, `RetentionPolicyTest`, `SnapshotCodecTest`.
- Instrumented (on the Nothing A059): `HistoryStoreTest` writes a session with steps and a snapshot file, runs retention, checks rows and files are gone and pinned stays.
- Device: debug commands record real actions; `history` lists them.
