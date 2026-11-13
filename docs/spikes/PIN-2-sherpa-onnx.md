# PIN-2 spike: sherpa-onnx on the device

Date: 2026-11-13
Device: Nothing A059, Android 17 (API 37), arm64, 7.6 GB RAM.
Library: sherpa-onnx v2.0.1, `sherpa-onnx-static-link-onnxruntime-2.0.1.aar` (official k2-fsa GitHub release).
Models: streaming Zipformer English 20M int8 (2023-02-17, mobile), KWS Zipformer GigaSpeech 3.3M int8 (2024-01-01, mobile), Silero VAD v5.
Code: branch `spike/PIN-2-sherpa-onnx` (debug-only harness `SpeechSpike`, local AAR path; not for merge).
Method: models copied to the app's private `files/models`; recorded test audio fed in 100 ms chunks, timed with `SystemClock`; memory with `Debug.getPss()`.

## Question

Can sherpa-onnx meet Pin's budgets for APK size, memory, CPU and latency on a real phone?

## Results

### Size

| Item | Measured | Budget | Verdict |
| --- | --- | --- | --- |
| Native library, arm64 (`libsherpa-onnx-jni.so`, ONNX Runtime linked in) | 24.2 MB uncompressed, 9.0 MB compressed | | |
| Release APK, native libs stored uncompressed (AGP default) | 25.8 MB | 12 MB | fails |
| Release APK, native libs compressed (`useLegacyPackaging = true`) | 10.6 MB | 12 MB | passes; Android unpacks 24 MB at install |
| 16 KB page alignment | LOAD segments aligned to 0x4000 | required for Android 15+ | passes |
| Required models (VAD + KWS + ASR int8) | 42 MB on device | 80 MB first download | passes |

### Runtime (warm unless noted)

| Item | Measured | Budget | Verdict |
| --- | --- | --- | --- |
| VAD CPU, 512-sample windows | 0.63 to 0.69 % of one core | idle < 2 % | passes |
| ASR real-time factor (2 threads) | 0.035 to 0.040 | well under 1 | passes |
| ASR slowest 100 ms chunk | 13 to 21 ms | must stay under 100 ms | passes |
| Model load, first run after install | VAD 583 ms, KWS 1,731 ms, ASR 2,113 ms | cold start < 1.5 s | fails cold; load models after the UI is up |
| Model load, files cached | VAD 152 to 282 ms, KWS 258 to 470 ms, ASR 363 to 674 ms | | passes |
| Process PSS with VAD + ASR loaded | 154 MB (64 MB before loading) | < 150 MB | marginal |
| Added by KWS | about 16 to 19 MB | | pushes total to about 170 MB |

### Accuracy

- ASR output on the two LibriSpeech test files was correct except that the **first two to three words were missing** ("AFTER EARLY NIGHTFALL", "GOD"). The first partial arrived only after 1.4 to 2.4 s of audio. Setting `modelType = "zipformer"` did not change it.

### Stability

- **The KWS model crashed the process every time**: ONNX Runtime threw "Non-zero status code returned while running Reshape node /downsample/Reshape_1", which aborted the whole app (native `SIGABRT`). Tried with no model type and with `modelType = "zipformer2"`; both crashed. Stopped after two attempts (project rule).
- After repeated crashes Android refused to start the app from a background broadcast until it was opened in the foreground.

## Decisions and changes for the blueprint

1. **Compress native libraries** (`jniLibs.useLegacyPackaging = true`) in release builds. Recorded APK 10.6 MB against a 12 MB budget leaves little room; track it per release.
2. **Run speech in a separate process** (`android:process=":speech"`). A native abort inside ONNX Runtime kills its process; it must not take down the accessibility service, the overlay or history. This changes blueprint section 2.2 (single process) and needs an ADR.
3. **Load models after the first frame**, never during app or service start; show "Preparing voice" in the overlay meanwhile.
4. **Memory**: with VAD + ASR + KWS the process sits near 170 MB. Mitigations to test in PIN-22/23: a separate speech process (moves model memory out of the service process), 1 thread for ASR, unload ASR after 60 s idle (already in blueprint 10.2).

## Open questions (follow-up work)

- KWS crash: try the non-mobile KWS archive, the fp32 encoder, an older sherpa-onnx (1.13.x) or the shared ONNX Runtime AAR. Ticket: part of PIN-22.
- Missing first words: try feeding 0.5 s of leading silence, `enableEndpoint = false`, and a newer English streaming model. Ticket: part of PIN-23.
- "Hey pin" can be written with existing tokens (`▁HE Y ▁P IN`); detection quality is untested until KWS runs.
- Live microphone, idle battery over hours and wake-to-earcon latency need the listening service (PIN-21).
