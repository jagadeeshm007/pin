# 0008: Model hosting and fallback

Status: Accepted (maintainer, 2026-11-13)
Date: 2026-11-13

## Context

Speech models (about 42 MB) are downloaded on first launch (ADR-0006). The host sees
every install's download requests and can remove files. The files measured in PIN-2 are
the "mobile" int8 variants, which upstream publishes only inside large archives on
GitHub (107.6 MB archive for a 35 MB recognition model). Hugging Face has an identical
copy of the voice detection model only.

## Decision

- **Primary host:** GitHub Releases on `jagadeeshm007/pin`, release tag `models-v1`,
  holding the exact files measured in PIN-2, renamed for clarity.
- **Fallback:** only where an identical file exists elsewhere. Today that is the voice
  detection model on Hugging Face (`csukuangfj/vad`, same SHA-256). Speech recognition
  and wake word have no fallback; if the primary fails the app shows "retry later".
- **Integrity:** a catalog in the APK lists each file's name, size, SHA-256 and URLs. A
  file is used only when its hash matches. The catalog changes only with an app release.
- **Licence:** the models are Apache-2.0 (sherpa-onnx, icefall); the release notes
  credit the upstream authors.

## Files (models-v1)

| File | Bytes | SHA-256 | Fallback |
| --- | --- | --- | --- |
| asr-en-encoder.int8.onnx | 32809961 | ed7e1e12bc624276f2e9e7c721a251485e260532d0593954632b166e1f22fde1 | none |
| asr-en-decoder.onnx | 2092272 | 45a7f940ecfb53d89fa270ad11b88b961e53a317203eb24b1c8e95ed208b0f30 | none |
| asr-en-joiner.int8.onnx | 259572 | e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e | none |
| asr-en-tokens.txt | 5048 | 49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb | none |
| kws-encoder.int8.onnx | 4000908 | 7d5a25fbc14b423074fe0c58e1be388ccf3f1a815c649980232a0c47f81875e4 | none |
| kws-decoder.onnx | 1063189 | f61ebd3eed3773a44d088d53dfae92dbb6aec4839f4dcaee2d402414741663a3 | none |
| kws-joiner.int8.onnx | 163380 | eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c | none |
| kws-tokens.txt | 5006 | fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53 | none |
| vad-silero-v5.onnx | 2313101 | 6b99cbfd39246b6706f98ec13c7c50c6b299181f2474fa05cbc8046acc274396 | huggingface.co/csukuangfj/vad |

Total 42,712,437 bytes.

## Consequences

- One host to keep alive; the maintainer uploads models with each model change.
- A GitHub outage blocks first-time speech setup (local commands also need speech).
- The wake word model crashes in PIN-2; if PIN-22 replaces it, a `models-v2` release follows.

## Alternatives

- Upstream archives as fallback: about 3 times the mobile data and a bzip2 dependency.
- A different model as fallback: untested, would behave differently from the primary.
