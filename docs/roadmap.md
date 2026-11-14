# Roadmap

Phases from the blueprint (section 16). Each line is one task id; each feature
task goes through the lifecycle in `docs/sdlc/README.md`. Status: todo, spec,
plan, build, verify, review, done.

## Phase 0: Spikes (findings in `docs/spikes/`)

| Id | Task | Status |
| --- | --- | --- |
| PIN-1 | Repository and AI-SDLC setup | done |
| PIN-2 | sherpa-onnx on the device matrix: APK delta, RAM, idle CPU, wake and ASR latency, 16 KB alignment | done (Nothing A059; findings in docs/spikes/PIN-2-sherpa-onnx.md; KWS crash and missing first words open) |
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
| PIN-13 | Task notification: Live Update with legacy fallback, persistent confirmation card | review |
| PIN-14 | History store schema, file layout, retention | review |
| PIN-15 | Device profile on first launch: Android version and OEM skin, screen size and density, display cutouts (notch, punch-hole) and insets, navigation mode, gesture limits, RAM and CPU class, per-app quirks learned from fallbacks; used by gestures, overlay placement and the planner (maintainer request 2026-11-13) | review |

## Phase 2: Voice

| Id | Task | Status |
| --- | --- | --- |
| PIN-20 | Module manager: pinned catalog, resumable verified downloads, first-launch setup card | review |
| PIN-21 | Listening service and audio thread, mic loss handling | review |
| PIN-22 | VAD gate and keyword spotting with user-chosen wake phrase | todo |
| PIN-23 | Endpointing, pre-roll and barge-in stop on the Android recognizer (ADR-0010) | todo |
| PIN-24 | TTS and earcons | todo |

## Phase 3: Local commands

| Id | Task | Status |
| --- | --- | --- |
| PIN-30 | Command grammar and intent router with early commit | review |
| PIN-31 | Fuzzy target resolution and numbers fallback | in progress |

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

## Phase 6: Open-source release

| Id | Task | Status |
| --- | --- | --- |
| PIN-60 | Reproducible signed release, GitHub Releases, contributor docs | todo |

## Phase 7: Pin as a device agent (direction set 2026-11-14, after Phase 6)

Maintainer direction: Pin should work like a coding agent does for a repository, but for
the phone. Each row becomes a spec with its own ADR before any code. Order is a first
guess. Detail: `docs/architecture/direction-phase-7.md`.

| Id | Task | Status |
| --- | --- | --- |
| PIN-70 | Think, plan, act, re-plan loop: explicit plan steps, stuck and error detection, re-plan with the failure attached, bounded retries | todo |
| PIN-71 | Long-running tasks: "keep scrolling reels until I say stop", pacing rules per app, pause on screen-off and calls, stop word wins | todo |
| PIN-72 | Data-loss guardrails: a deny list of destructive targets (reset, delete, format, uninstall, factory reset, sign out) that no route may trigger, on top of the confirmation gate | todo |
| PIN-73 | Visible hand: an overlay cursor that shows tapping, holding, dragging and scrolling before and while Pin acts | todo |
| PIN-74 | Tool registry: typed, permissioned tools beyond the screen (files and media read and write, contacts, SMS and calls, app intents, share sheets), each a separate runtime permission the user grants | todo |
| PIN-75 | Safe script runner: an embedded interpreter (JavaScript via QuickJS or Lua, ADR needed) whose only powers are Pin's registry tools; loops and conditions allowed; no network, no file system beyond tool calls, time and step budgets; dry run shown for anything that writes or sends | todo |
| PIN-76 | User memory: remembered preferences, routines and app habits, stored locally, visible and editable, with the same retention controls as history | todo |
| PIN-77 | MCP client and connectors: Pin connects to MCP servers the user adds (remote over HTTP, local over the user's own network), lists their tools and resources, and exposes them to the planner through the same registry and permission gates | todo |
| PIN-78 | Skills: user-added instruction packs (Markdown with optional tool allowlists), loaded per task like other harnesses do, shareable as files, never containing keys | todo |
| PIN-79 | Deep Android reach: an audit of every API an app may use without root (intents, content providers, companion device, app functions, notifications listener, media projection, roles) and a registry tool for each that passes the privacy review | todo |

## Phase 8: Google and connectivity (after Phase 7)

| Id | Task | Status |
| --- | --- | --- |
| PIN-80 | Google account sign-in with per-scope consent (the user's own OAuth client; no key ships) | todo |
| PIN-81 | Google tools: Calendar, Gmail, Tasks, Keep, Drive, Contacts behind the Phase 7 tool registry | todo |
| PIN-82 | Connectivity tools: Wi-Fi connect (suggestion and panel APIs), Bluetooth pairing and handoff within what Android allows apps | todo |
