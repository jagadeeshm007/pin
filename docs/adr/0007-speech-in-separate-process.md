# 0007: Run speech in a separate process

Status: Proposed
Date: 2026-11-13

## Context

The blueprint (section 2.2) puts everything in one process: accessibility service,
microphone service, overlay, history. The PIN-2 spike on the Nothing A059 showed:

- A native error inside ONNX Runtime (the wake word model) aborted the whole process
  with `SIGABRT`. Kotlin cannot catch it. In one process that also kills the
  accessibility service, the overlay and any open history write.
- After repeated crashes Android refused to restart the app from the background until
  it was opened in the foreground, which would leave Pin silently dead.
- With voice detection and recognition loaded the process used 154 MB, and about 170 MB
  with the wake word model, against a 150 MB budget.

## Decision

Run the microphone, voice detection, wake word and recognition in a separate process,
`:speech`, hosting `ListeningService` (the microphone foreground service). The main
process keeps the accessibility service, orchestrator, overlay, notification and
history.

- The speech process sends only small messages to the main process: wake detected,
  partial transcript, final transcript, state (listening, paused, error). Audio never
  crosses the process boundary.
- Transport: a bound service with a `Messenger` (framework only, no new dependency).
- The main process watches the speech process; if it dies, the overlay shows "Voice
  restarting", the notification stays, and the speech service is restarted with
  back-off. Device control keeps working.
- History is written only by the main process (single writer for Room).

## Consequences

- A speech-engine crash no longer takes down device control.
- Model memory is counted against the speech process, so each process can stay within
  budget; the total rises by the second runtime (about 10 to 20 MB for a minimal process).
- Messages add a little latency (a binder call, well under 1 ms) between transcript
  and action.
- Two processes mean two `Application` instances: anything created in `PinApp` must be
  safe to create in both, or guarded by process name.
- The microphone foreground service must still start from a user action, now in the
  `:speech` process.

## Alternatives

- One process (blueprint as written): simplest, but a native crash kills everything.
- Catch native errors in C++ and return codes: not possible without rebuilding
  sherpa-onnx and ONNX Runtime; upstream errors would still abort.
- Run only the wake word model in a separate process: smaller change, but recognition
  uses the same runtime and can fail the same way.
