# 0010: Transcribe with Android's on-device recognizer, fed by Pin's microphone

Status: Accepted (maintainer, 2026-11-14)
Date: 2026-11-14

## Context

PIN-21 first transcribed with the sherpa-onnx streaming Zipformer (ADR-0003). On the
Nothing A059 (Android 17) its accuracy was poor for short commands: "scroll down please"
came out as "S CRAWLED ON PLEASE", and new streams dropped the first words. The
maintainer chose Android's speech recognizer instead (PIN-21 session, 2026-11-13).

The blueprint's objection to `SpeechRecognizer` was that the recognizer opens the
microphone itself, which breaks the single-owner rule (AGENTS.md). From API 33,
`RecognizerIntent.EXTRA_AUDIO_SOURCE` lets the caller pass audio through a pipe instead.

## Decision

- `ListeningService` keeps the only `AudioRecord` and the Silero VAD (ADR-0007).
- Each utterance the segmenter finds is streamed as 16 kHz mono PCM16 through a
  `ParcelFileDescriptor` pipe to `SpeechRecognizer.createOnDeviceSpeechRecognizer`
  (`createSpeechRecognizer` when on-device recognition is unavailable).
- The language is the first installed English pack, preferring `en-US`, read with
  `checkRecognitionSupport`. The A059 has only `en-IN` and refuses `en-US`.
- Requires API 33. Below that the session reports an error (TODO(PIN-21): sherpa fallback).

Device findings that shaped the code (Google's service, `com.google.android.as`):

- Our copy of the pipe's read end must stay open until the recognizer reports; closing
  it right after `startListening` gave `ERROR_CLIENT`.
- `detachFd()` on the write end invalidates the descriptor the stream writes through
  (`EBADF`); the write end is wrapped in `AutoCloseOutputStream`, whose close is the
  end of audio. Calling `stopListening()` interrupts the recognizer's read.
- The final text arrives in the last partial bundle (`final_result`); `onResults` gets
  an empty bundle, so the last partial is used.
- 0.5 s of leading silence and a 640 ms pre-roll recovered most first words.

## Consequences

- Better accuracy and roughly 40 ms from end of speech to final text on the A059.
- Depends on Google's on-device speech service and its language pack; devices without
  it fall back to the cloud-capable default recognizer.
- The sherpa ASR model files in the catalog and `SpeechEngine` are now unused by the
  service; whether to keep them as the fallback is open.

## Alternatives

- Keep sherpa Zipformer: fully open, but accuracy too low on device.
- Let the recognizer open the microphone: breaks the single-owner rule and
  silences Pin's VAD during recognition.
