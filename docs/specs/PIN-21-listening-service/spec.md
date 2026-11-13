# PIN-21: Listening service in the speech process

Status: Approved (maintainer instruction "continue" and ADR-0007/0009, 2026-11-13; open to revision)
Owner: maintainer
Roadmap phase: 2
Blueprint sections: 5.1, 5.2, 12
ADRs: 0003, 0007 (separate process), 0008 (models), 0009 (library)

## Problem

Pin cannot hear yet. This task adds the microphone session: a foreground service in its
own `:speech` process that captures audio, finds speech with voice detection, transcribes
it with streaming recognition, and sends the text to the main process.

## Interim behaviour

The wake word model crashes (PIN-2). Until PIN-22 fixes or replaces it, a running
session treats every utterance as a command. The notification and overlay say so.

## Scope

- In: `ListeningService` (microphone foreground service, `android:process=":speech"`)
  started and stopped from the main screen; microphone permission request; audio thread
  (16 kHz mono); VAD gate; streaming ASR with partials, endpoint on 600 ms of silence after
  speech; mic loss (silenced by a call or another app) shown as paused; messages to the
  main process over a bound `Messenger` (state, partial, final); main process shows them
  in the overlay pill and records each final transcript as a history task; debug commands.
- Out: wake word (PIN-22), acting on transcripts (PIN-30), TTS (PIN-24), quick settings
  tile, battery measurements over hours (PIN-51).

## Acceptance criteria

- AC-1: WHEN the user taps Start listening with the microphone permission granted and models ready THE service SHALL start in the foreground in the `:speech` process; WHEN Stop is tapped (screen or notification) it SHALL release the microphone within 500 ms.
- AC-2: WHEN models are not ready THE start control SHALL be disabled with the reason shown.
- AC-3: WHILE no one speaks THE recognizer SHALL not run (VAD gate).
- AC-4: WHEN speech ends THE final transcript SHALL reach the main process and appear on the overlay; partials SHALL appear while speaking.
- AC-5: WHEN another app or a call silences the microphone THE state SHALL become paused and return to listening when audio comes back.
- AC-6: WHEN the speech process dies THE accessibility service SHALL keep running (separate process).
- AC-7: Audio SHALL NOT leave the speech process; only text and state cross to the main process.

## Budgets touched

| Budget | Target |
| --- | --- |
| Idle listening CPU (VAD only) | < 2 % of one core |
| Speech process memory, models loaded | record (budget 150 MB applies per process) |
| Speech end to final transcript | record |
