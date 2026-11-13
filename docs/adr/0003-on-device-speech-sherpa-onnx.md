# 0003: On-device speech with sherpa-onnx

Status: Accepted; the transcription part is superseded by ADR-0010
Date: 2026-11-13

## Context

Simple commands must act within 500 ms of speech end, offline, without sending
audio anywhere. Wake word must be user-chosen.

## Decision

sherpa-onnx on ONNX Runtime for VAD (Silero) and open-vocabulary keyword spotting
(Zipformer). Models are downloaded modules.

Amended 2026-11-14: transcription no longer uses sherpa-onnx. The streaming Zipformer
ASR was removed after device tests; Android's recognizer transcribes (ADR-0010).

## Consequences

- One native runtime, Apache-2.0, streaming partials for early intent commit.
- Several MB of native code in the APK; verified in spike PIN-2.

## Alternatives

- Porcupine: free tier ended 30 Jun 2026; paid tier too costly for open source.
- whisper.cpp: no native streaming, higher latency.
- SpeechRecognizer only: needs Google services, takes the mic per utterance.
