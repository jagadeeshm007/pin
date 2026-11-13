# PIN-21 plan

Status: Approved (2026-11-13)
Spec: ./spec.md

## Design

| Pure (JVM tests) | Android |
| --- | --- |
| `SpeechSegmenter`: consumes VAD decisions per 32 ms window, decides when to start feeding ASR (with 300 ms pre-roll), and when an utterance ends (600 ms silence, 15 s cap) | `ListeningService` (FGS microphone, `:speech`): audio thread, sherpa Vad + OnlineRecognizer, startForeground, `Messenger` binder |
| `SpeechEvent` (State, Partial, Final) and its `Bundle` mapping | `SpeechClient` (main process): binds when listening, receives events, updates overlay, records history |
| | `PinApp`: skip main-process setup in `:speech` (one `Application` class, two processes) |
| | `MainActivity`: Start/Stop listening, permission request |
| | Manifest: `RECORD_AUDIO`, `FOREGROUND_SERVICE_MICROPHONE`, service with `foregroundServiceType="microphone"` and `process=":speech"` |

Engine config from PIN-2: Silero VAD v5 (threshold 0.5, 512-sample windows, 1 thread),
streaming Zipformer int8 with `modelType = "zipformer"`, 2 threads.

The FGS notification is posted by the speech process with the shared channel and ID 1,
so the existing notification becomes the session notification. The main process keeps
updating it.

## Test strategy

- Unit: `SpeechSegmenterTest`, `SpeechEventTest`.
- Device: start, speak a few commands, read transcripts from overlay and history; idle CPU with `top`; stop releases mic (`dumpsys audio` recorders); kill `:speech` and check the accessibility service is still bound; phone call or another recorder for pause if possible.
