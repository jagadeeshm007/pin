# Roadmap

Phases from the blueprint (section 16). Each line is one task id; each feature
task goes through the lifecycle in `docs/sdlc/README.md`. Status: todo, spec,
plan, build, verify, review, done.

## Phase 0: Spikes (findings in `docs/spikes/`)

| Id | Task | Status |
| --- | --- | --- |
| PIN-1 | Repository and AI-SDLC setup | done |
| PIN-2 | sherpa-onnx on the device matrix: APK delta, RAM, idle CPU, wake and ASR latency, 16 KB alignment | todo |
| PIN-3 | Microphone foreground service start paths (activity, notification, tile) on Android 14, 16, 17 | todo |
| PIN-4 | Gesture and node-action coverage on YouTube, Settings, WhatsApp, Chrome, one Flutter app | todo |
| PIN-5 | Planner prototype on recorded screens against Claude, OpenAI, Gemini, OpenRouter | todo |
| PIN-6 | AppFunctions permission level and assistant role capabilities | todo |
| PIN-7 | Live Update promotion behaviour on Pixel and one OEM skin | todo |

## Phase 1: Foundation

| Id | Task | Status |
| --- | --- | --- |
| PIN-10 | AccessibilityService, screen snapshots, device controller, debug harness | review |
| PIN-11 | Action registry and orchestrator with verification and cancellation | review |
| PIN-12 | Overlay: states, numbers, grid | review |
| PIN-13 | Task notification: Live Update with legacy fallback | todo |
| PIN-14 | History store schema, file layout, retention job | todo |

## Phase 2: Voice

| Id | Task | Status |
| --- | --- | --- |
| PIN-20 | Module manager: signed catalog, resumable verified downloads, first-launch setup | todo |
| PIN-21 | Listening service and audio thread, mic loss handling | todo |
| PIN-22 | VAD gate and keyword spotting with user-chosen wake phrase | todo |
| PIN-23 | Streaming ASR, endpointing, pre-roll, barge-in stop | todo |
| PIN-24 | TTS and earcons | todo |

## Phase 3: Local commands

| Id | Task | Status |
| --- | --- | --- |
| PIN-30 | Command grammar and intent router with early commit | todo |
| PIN-31 | Fuzzy target resolution and numbers fallback | todo |

## Phase 4: Agent

| Id | Task | Status |
| --- | --- | --- |
| PIN-40 | Planner contract, neutral history form, model router and model table | todo |
| PIN-41 | Claude adapter | todo |
| PIN-42 | OpenAI adapter | todo |
| PIN-43 | Gemini adapter | todo |
| PIN-44 | OpenRouter adapter | todo |
| PIN-45 | Provider setup UI, SecretStore, key validation | todo |
| PIN-46 | Agent loop, sensitivity gate, confirmations | todo |
| PIN-47 | Screenshot fallback (opt-in visual mode) | todo |
| PIN-48 | Planner eval harness | todo |

## Phase 5: History viewer and hardening

| Id | Task | Status |
| --- | --- | --- |
| PIN-50 | History viewer, pin, export, delete, storage meter, retention settings | todo |
| PIN-51 | Budget measurements and tuning | todo |
| PIN-52 | OEM guidance, call and Bluetooth drills, onboarding | todo |
| PIN-53 | Android SpeechRecognizer engine (Variant B) | todo |

## Phase 6: Open-source release

| Id | Task | Status |
| --- | --- | --- |
| PIN-60 | Reproducible signed release, GitHub Releases, contributor docs | todo |
