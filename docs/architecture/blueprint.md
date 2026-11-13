# Technical Blueprint: Android Voice-Controlled AI Agent

Status: approved 2026-11-13.
Research date: 13 Nov 2026. Platform facts below were checked against developer.android.com, Play Console Help and vendor docs on that date. Sources are listed at the end.

---

## 0. Executive summary

**What we are building.** An Android app that owns the microphone during a user-started session, detects a wake word on device, transcribes the command on device, executes simple commands locally in well under 100 ms, and hands complex instructions to an LLM planner that works through a fixed registry of validated actions on the accessibility tree, verifying each step.

**Four findings that shape everything else.**

1. **The AccessibilityService is the only viable control surface.** Gesture injection, node inspection, text entry and global navigation all come from it. Nothing else is available to a user-installed app. The product is therefore an accessibility service first, and every distribution question follows from that.
2. **Distribution is open source, not Play.** The project ships as a source repository and a sideloadable APK. That removes the Play policy constraint on autonomous Accessibility API use for us. The policy still matters to anyone who forks the project for Play, so Section 13 keeps a short compliance note, but nothing in the architecture is shaped by Play review.
3. **Rust is not justified for the MVP.** The heavy compute (VAD, wake word, ASR) already runs in C++ inside ONNX Runtime. Kotlin glue costs microseconds per 100 ms audio frame. Rust is reserved for a later, measured decision (a portable audio front-end or a custom micro-runtime to shrink the APK). The interfaces are designed so that swap costs nothing.
4. **Accessibility tree first, screenshots second, on-device LLM later.** The tree is cheaper, faster and more private than vision. Screenshots are a fallback for canvas or poorly labeled apps. On-device LLM planners (Gemini Nano via ML Kit, Gemma via LiteRT-LM) are not ready for a background agent on the general device population; the planner interface is provider-neutral so they can be added when they are.

**Recommended stack in one line.** Kotlin app and AccessibilityService; sherpa-onnx (C++ on ONNX Runtime) for VAD, keyword spotting and streaming ASR; Kotlin deterministic command engine; provider-neutral planner where the user picks a provider (Claude, OpenAI, Gemini, OpenRouter) and supplies their own key while the app owns model routing; system TextToSpeech for feedback; models downloaded after install rather than bundled; every session, step, screenshot and planner reasoning summary persisted locally and viewable in the app, with a configurable retention window (default 7 days).

**Confirmed by you**

- No API key ships with the app. The user chooses a provider and enters their own key. The app decides which model and settings to use per provider (model routing).
- Open-source project. Play Store compliance is not a design input.
- Full history preservation: session threads, screenshots and planner reasoning are stored and viewable; auto-delete everything older than a configurable window, default 7 days.
- Small APK: anything heavy (speech models, language packs, optional components) is offered as a download on first launch, not bundled.
- Task visualization: the session notification is an Android 16 Live Update (`Notification.ProgressStyle`, status bar chip) showing the current task, step progress and state, with a plain progress notification on older versions.

**Decisions still open** (assumptions used below are marked):

| Decision | Assumption used in this blueprint |
|---|---|
| Spoken languages | English first; the ASR engine is swappable, so other languages are a model change, not a code change |
| Minimum Android version | minSdk 30 (Android 11) so `takeScreenshot` and the current microphone rules are uniform |
| Device baseline | Mid-range 2023 phone (4 to 6 GB RAM, Snapdragon 6 or 7 series class) as the performance floor |
| Open-source license | Apache-2.0 assumed (compatible with sherpa-onnx and ONNX Runtime) |
| Team Rust experience | Unknown; the plan avoids depending on it |

---

## 1. Product feasibility and technical challenges

### 1.1 Feasible, with evidence

Google's own Voice Access app proves the category: wake-free voice control, "show numbers", "show labels", grid selection, all built on the AccessibilityService. Everything in the product vision maps to an existing, documented API:

| Requirement | Mechanism | API level |
|---|---|---|
| Tap, double tap, long press, swipe, scroll, pinch | `AccessibilityService.dispatchGesture` with `GestureDescription` strokes; node actions `ACTION_CLICK`, `ACTION_LONG_CLICK`, `ACTION_SCROLL_*` | 24+ (gesture), 16+ (node actions) |
| Back, home, recents, notifications, quick settings, lock, screenshot | `performGlobalAction` | 16+, screenshot 28+, dpad 33+ |
| Read visible content | `getRootInActiveWindow`, `getWindows`, `AccessibilityNodeInfo` tree | 16+, windows 21+ |
| Type text | `ACTION_SET_TEXT`; service as IME via `FLAG_INPUT_METHOD_EDITOR` for stubborn fields | 21+, IME 33+ |
| Visual fallback | `takeScreenshot` (rate-limited to 1 per 333 ms) | 30+ |
| App launch | `PackageManager.getLaunchIntentForPackage` + `startActivity` from the service | all |
| Continuous listening | `AudioRecord` inside a foreground service of type `microphone` | 30+ rules uniform |
| Visible listening indicator | System microphone indicator (automatic) plus our own `TYPE_ACCESSIBILITY_OVERLAY` window (no extra permission) | 22+ |

### 1.2 The hard problems, ranked

1. **Provider heterogeneity.** Four cloud providers with different tool-calling formats, reasoning exposure and vision support must sit behind one planner contract without lowest-common-denominator quality. Covered in Section 7.
2. **Latency budget for the fast path.** End of speech to gesture under 500 ms requires on-device streaming ASR with partial results and early intent commit. Cloud ASR cannot meet it reliably.
3. **Accessibility tree quality.** Flutter apps without semantics, Unity games, custom canvases and some WebViews expose little or nothing. We need a labeling overlay (numbers and grid) and a screenshot fallback.
4. **Long-session reliability.** Microphone contention (phone calls, Assistant, other recorders), OEM background killers (Xiaomi, Huawei, Oppo), thermal throttling and Doze. Requires a foreground service, recording-config callbacks and explicit degradation states.
5. **Planner correctness and safety.** LLMs mis-identify targets. Every planner action goes through schema validation, a target-existence check, a sensitivity gate and post-action verification.
6. **Battery.** Always-on microphone plus a continuously running encoder would drain. The design gates everything behind a VAD that costs well under 1 ms per 32 ms frame.
7. **Device fragmentation.** Gesture dispatch is refused while another accessibility service is mid-gesture or while TalkBack touch exploration is active. Some OEM system UIs block injection. Secure windows refuse screenshots.

---

## 2. Recommended architecture

### 2.1 Component diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  Processes: main (service, overlay, history) + :speech (ADR-0007)            │
│                                                                              │
│  ┌───────────────┐   ┌────────────────────────────────────────────────────┐  │
│  │ Settings UI   │   │ VoiceAccessibilityService (AccessibilityService)   │  │
│  │ (Activity)    │   │   - node tree access, gestures, global actions     │  │
│  │ Quick tile    │   │   - overlay window (listening state, numbers, grid)│  │
│  │ Notification  │   │   - window/content change events -> ScreenModel    │  │
│  └──────┬────────┘   └───────────────┬────────────────────────────────────┘  │
│         │ start/stop session         │ observe / act                         │
│         ▼                            ▼                                       │
│  ┌──────────────────┐     ┌──────────────────────┐    ┌────────────────────┐ │
│  │ ListeningService │     │  ActionOrchestrator  │◄───│  LocalCommandEngine│ │
│  │ (FGS, microphone)│     │  - action registry   │    │  (deterministic    │ │
│  │  owns AudioRecord│     │  - validation        │    │   grammar, fuzzy   │ │
│  └──────┬───────────┘     │  - sensitivity gate  │    │   target match)    │ │
│         │ PCM 16k frames  │  - execute + verify  │    └─────────▲──────────┘ │
│         ▼                 │  - cancellation      │              │ transcript │
│  ┌──────────────────┐     └──────────▲───────────┘              │ (partial + │
│  │ VoicePipeline    │                │ tool calls               │  final)    │
│  │  VAD -> KWS ->   │     ┌──────────┴───────────┐    ┌─────────┴──────────┐ │
│  │  streaming ASR   │────►│  AgentLoop           │◄───│ IntentRouter       │ │
│  │  (sherpa-onnx /  │     │  observe-plan-act-   │    │ local? -> engine   │ │
│  │   ONNX Runtime)  │     │  verify, step budget │    │ complex? -> agent  │ │
│  └──────────────────┘     └──────────┬───────────┘    └────────────────────┘ │
│                                      │ PlannerRequest / PlannerResponse       │
│                           ┌──────────▼───────────┐                           │
│                           │ ModelRouter          │ picks model + params per  │
│                           │  (provider chosen by │ provider, capability flags│
│                           │   user, own key)     │ fallback chain            │
│                           └──────────┬───────────┘                           │
│                           ┌──────────▼───────────┐                           │
│                           │ Planner (interface)  │                           │
│                           │  ClaudeAdapter       │  Anthropic Messages API   │
│                           │  OpenAiAdapter       │  OpenAI Responses API     │
│                           │  GeminiAdapter       │  Gemini API               │
│                           │  OpenRouterAdapter   │  OpenAI-compatible        │
│                           │  (on-device later)   │                           │
│                           └──────────┬───────────┘                           │
│                                      │ every request, response, reasoning     │
│                                      ▼                                       │
│  ┌──────────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ Feedback (TTS,   │  │ ModelStore   │  │ SecretStore  │  │ HistoryStore │  │
│  │ earcons, overlay)│  │ (download,   │  │ (Keystore-   │  │ (Room DB +   │  │
│  │                  │  │  verify,mmap)│  │  wrapped keys│  │  files, 7-day│  │
│  │                  │  │              │  │  per provider│  │  retention)  │  │
│  └──────────────────┘  └──────────────┘  └──────────────┘  └──────────────┘  │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Component responsibilities

| Component | Responsibility | Must not |
|---|---|---|
| VoiceAccessibilityService | Sole owner of node access, gesture dispatch, global actions, overlay. Publishes a `ScreenModel` (current window, package, pruned node index) on window and content events. | Run inference or network. Block the main thread beyond event bookkeeping. |
| ListeningService (FGS, type microphone) | Owns the single `AudioRecord` (16 kHz mono, `VOICE_RECOGNITION` source). Runs the audio thread at `THREAD_PRIORITY_URGENT_AUDIO`. Handles recording-config callbacks (silenced, device change), calls, thermal, battery. Shows the notification with a Stop action. | Be started from the background. Capture when the session is off. |
| VoicePipeline | VAD gate, keyword spotting, streaming ASR with partials, endpointing. Emits `Transcript(partial|final, text, confidence, timestamps)`. | Know anything about UI. |
| IntentRouter | Decides fast path vs agent path using the local grammar and a confidence threshold. Supports early commit on stable partials. | Call the LLM for anything the grammar handles. |
| LocalCommandEngine | Grammar for navigation, scrolling, swipes, app launch, tap-by-label, numbers overlay, typing, zoom, volume, stop/cancel. Target resolution by fuzzy match over the `ScreenModel`. | Execute anything itself; it emits registry actions. |
| ActionOrchestrator | The only path to the device. Validates action schema and target existence, applies the sensitivity gate, executes via the service, waits for the screen to settle, verifies, reports `ActionResult`. Honors a `CancellationToken`. | Accept free-form code or coordinates outside screen bounds. |
| AgentLoop | observe -> plan -> act -> verify loop with step and time budgets, retry policy, per-task memory, confirmation prompts, final spoken summary. | Bypass the orchestrator. |
| ModelRouter | Holds the per-provider model table the project maintains (default planner model, fast model, vision-capable model, parameters), capability flags, and a fallback chain. The user picks only the provider; the router picks the model for each call type. Advanced users can override the model per provider. | Call a provider the user has not configured. |
| Planner | Provider-neutral interface: `plan(request): PlannerResponse` where the response is a list of typed tool calls or a question or done, plus an optional reasoning summary. One adapter per provider. | Return anything but registry tool calls. |
| Feedback | TTS (system engine), earcons, overlay states (idle, wake detected, listening, thinking, acting, needs confirmation, error). | Speak while the user is speaking. |
| ModuleManager (includes ModelStore) | Declares the module catalog (required and optional), drives the first-launch setup flow, downloads over HTTPS with resume and SHA-256 verification into app-private storage, memory-maps models for load, reports installed versions and disk use. | Bundle large models in the APK. Download executable code. |
| TaskNotification | Owns the single ongoing notification required by the microphone FGS. On Android 16+ it is a Live Update (`ProgressStyle`, promoted ongoing, status bar chip) that mirrors the agent state machine: listening, heard "...", planning, step 3 of 6 "tap Search", awaiting confirmation, done or failed. On older versions it is a standard ongoing notification with a progress bar and the same text. Always carries Stop and, when relevant, Confirm and Deny actions. | Be dismissible while the session is on. |
| SecretStore | One user-supplied API key per provider, encrypted with an Android Keystore key; never logged, never included in history exports. | Put keys in SharedPreferences plaintext or in the APK. |
| HistoryStore | Persists sessions, tasks, steps, transcripts, screen snapshots, screenshots, planner requests and responses, reasoning summaries, verification results and metrics. Serves the in-app history viewer. Runs the retention job (default: delete everything older than 7 days, configurable, with per-session pin to keep). | Store API keys or password-field contents. Upload anything. |

---

## 3. Technology stack comparison

### 3.1 Application language

| Option | Fit | Notes |
|---|---|---|
| **Kotlin** (chosen) | Required | AccessibilityService, foreground services, AudioRecord, overlays and permissions are framework APIs. Coroutines give structured cancellation for the agent loop. |
| Java | Possible | No advantage over Kotlin. |
| Flutter / RN / KMP shared UI | Rejected | Adds 5 to 10 MB and a runtime between us and the framework APIs that are the whole product. |

UI: the app has one settings screen, a first-run flow and an overlay. Compose adds roughly 1 to 1.5 MB after R8 and a measurable cold-start cost. Recommendation: Views plus Material Components for the MVP, revisit if the UI grows. The overlay is a plain `View` tree in a `TYPE_ACCESSIBILITY_OVERLAY` window.

### 3.2 Native code: Rust, C/C++, Go

| Option | Where it would run | Measured value | Cost | Verdict |
|---|---|---|---|---|
| C++ via sherpa-onnx and ONNX Runtime | VAD, KWS, ASR inference | All the heavy compute; already optimized, already has Kotlin bindings, one ORT instance | Binary size (see 3.4) | **Use**, as a dependency, not as code we write |
| Rust audio front-end (ring buffer, resampling, framing) | Audio thread | Avoids JVM GC jitter on the audio thread | Toolchain (cargo-ndk, 16 KB alignment, UniFFI or JNI), second language in the build, team skill | **Defer.** 16 kHz mono at 100 ms frames is 1,600 samples per frame; a Kotlin `ShortArray` copy through one JNI call per frame is microseconds. GC pauses on a dedicated thread do not cause dropouts because `AudioRecord` buffers 500 ms or more. Revisit if measurements show jitter or if a portable core (iOS) becomes a goal. |
| Rust command parser | Intent routing | None; string matching over 25 to 60 patterns is microseconds in Kotlin | Same toolchain cost | **No** |
| Rust custom micro-runtime (hand-written int8 CNN for wake word, no ORT) | Ultra-light variant | Drops ORT (several MB) when ASR is delegated to Google's recognizer | Significant engineering, no ecosystem | **Later option** for an APK-minimal variant (Section 3.6) |
| Go | Nothing on device | gomobile adds a 5 MB+ runtime and a GC; no role in the audio or UI path | | **No.** The project has no backend, so Go has no place in it. |

If Rust is adopted later: UniFFI serializes every call through a byte buffer, which is fine for control calls and wrong for per-frame audio; use UniFFI for the control API and a raw JNI entry point that accepts a direct `ByteBuffer` for audio frames. Build with cargo-ndk, NDK r28+, and `-C link-arg=-Wl,-z,max-page-size=16384` to satisfy Play's 16 KB page requirement.

### 3.3 Voice activity detection

| Option | Size | Cost | Verdict |
|---|---|---|---|
| **Silero VAD v5** (ONNX) | ~2 MB | Sub-millisecond per 32 ms chunk on CPU | **Use**; shipped inside sherpa-onnx |
| TEN VAD | ~0.3 MB | Similar, newer, less field history | Alternative, same interface |
| WebRTC VAD (energy + GMM) | tiny | Poor in noise | No |

### 3.4 Wake word

| Option | Custom phrase | Size | Idle CPU | Licensing | Verdict |
|---|---|---|---|---|---|
| **sherpa-onnx keyword spotting** (Zipformer, 3.3 M params, open vocabulary) | Any phrase, no training | ~4 MB model | A few percent of one core while speech is present; near zero when VAD-gated | Apache-2.0 | **Use.** Same runtime as VAD and ASR; user can pick any wake phrase. |
| openWakeWord | Requires training a per-phrase model (notebook, synthetic data) | ~1 to 2 MB per phrase + shared ~1 MB | Lower than KWS (~1 to 2 percent) | Apache-2.0 | Fallback if KWS false accepts are too high after tuning |
| Picovoice Porcupine | Console-trained | small | low | **Free tier ends 30 Jun 2026; paid tier starts at $899 per month** | No |
| Android `HotwordDetectionService` | | | | Requires privileged permissions; not available to user-installed apps | No |

Gating rule: KWS runs only on frames the VAD marks as speech. That is what keeps idle cost near zero.

### 3.5 Speech recognition

| Option | Streaming partials | Offline | Size on device | Latency to first partial | Languages | Mic ownership | Verdict |
|---|---|---|---|---|---|---|---|
| **sherpa-onnx streaming Zipformer int8** | Yes | Yes | ~40 to 70 MB (downloaded) | ~150 to 300 ms on mobile, RTF well under 0.3 | English and bilingual models available; multilingual Zipformer, Paraformer, SenseVoice via same runtime | We own the mic | **Primary.** Deterministic, no GMS dependency, partials enable early intent. |
| Android `SpeechRecognizer` on-device (API 31+ `createOnDeviceSpeechRecognizer`) | Partials yes, control limited | Yes, if Google speech services installed | 0 MB in APK | Good | Many | Google's process takes the mic; our stream is silenced during recognition; documentation says the API "is not intended to be used for continuous recognition" | **Secondary engine** behind the same interface; zero download path for GMS devices |
| whisper.cpp (tiny/base) | No native streaming (sliding window) | Yes | 75 to 142 MB | Higher | 99 | We own | No for commands; latency and no partials |
| Moonshine / Parakeet via sherpa-onnx | Chunked | Yes | 50 to 200 MB | Good | English-centric | We own | Candidates for a later accuracy upgrade through the same runtime |
| Cloud ASR | Yes | No | 0 | Network bound, 300 ms+ added | Many | We own | No for the fast path; privacy cost |

### 3.6 Two packaging variants

| | Variant A: self-contained (recommended) | Variant B: ultra-light |
|---|---|---|
| VAD + wake word | sherpa-onnx on ONNX Runtime | Same, or custom micro-runtime later |
| ASR | sherpa-onnx Zipformer, downloaded | Android on-device SpeechRecognizer |
| Works without Google speech services | Yes | No |
| Mic owner | Single (us) | Handoff to Google's process per utterance |
| APK (arm64, estimate, to verify in spike) | 10 to 15 MB | 4 to 8 MB |
| First-run download | 50 to 80 MB | ~6 MB (VAD + KWS) |
| Latency control | Full | Limited by recognizer endpointing |
| Recommendation | MVP | Add behind the `AsrEngine` interface in Phase 5 |

### 3.7 On-device inference runtime

| Option | Verdict |
|---|---|
| **ONNX Runtime** (via sherpa-onnx) | Required by the speech stack; XNNPACK CPU path is enough for these model sizes |
| LiteRT | Better GPU delegate on Snapdragon, but we have no LiteRT models in the speech path; adding it means a second runtime. Only relevant if LiteRT-LM is adopted for an on-device planner later. |
| NNAPI | Deprecated from Android 15; do not depend on it |

### 3.8 LLM planner providers

The user selects one provider and pastes their own key. The app owns the model choice per provider (Section 7.4). All four cloud providers are first-class adapters in the MVP.

| Provider | Wire API | Tool calling | Vision | Reasoning exposure | Prompt caching | Notes |
|---|---|---|---|---|---|---|
| **Claude** | Anthropic Messages API | Strict JSON Schema tools, structured outputs | Yes | Summarized thinking via `display: "summarized"`; raw chain of thought is never returned | Explicit breakpoints, verifiable via usage fields | Server-side fallbacks on refusal; forced tool choice not available on current models |
| **OpenAI** | Responses API | Strict function tools | Yes | Reasoning summaries on reasoning models | Automatic prefix caching | Keep one adapter on Responses; do not also maintain Chat Completions |
| **Gemini** | Gemini API (`generateContent`) | Function declarations with schema | Yes | Thought summaries on thinking models | Context caching for large stable prefixes | Function-calling mode can force tool output |
| **OpenRouter** | OpenAI-compatible Chat Completions | Function tools (varies by routed model) | Varies by model | Varies | Varies | Gives access to many models with one key; the router restricts to models with verified tool support |
| Gemini Nano via ML Kit Prompt API | On device | Yes, requires Gemini Nano 4 (Pixel 10/11 class, select OEMs), AICore developer preview | Yes | None | n/a | **No background execution support documented; battery quotas enforced.** Add behind the interface when that changes |
| Gemma 4 E2B via LiteRT-LM | On device | Prompt-engineered | Limited | None | n/a | Needs roughly 3 GB free RAM; experimental adapter only |
| llama.cpp | On device | Prompt-engineered | Limited | None | n/a | Same RAM problem; no |

### 3.9 Text to speech

System `TextToSpeech` (0 MB, offline voices on most devices). Android 17 background audio hardening requires a foreground service with while-in-use capability for background playback; the microphone FGS satisfies that.

### 3.10 Networking and serialization

OkHttp plus kotlinx.serialization, with one hand-written adapter per provider over raw HTTP and server-sent events. Four vendor SDKs would each bring their own JSON library and HTTP stack; together they would add several megabytes and four update cadences. The wire formats we use (tools, messages, streaming) are small and stable enough to own. Each adapter has a contract test against recorded responses.

### 3.11 Local persistence

Room (SQLite) for sessions, tasks, steps and metrics; app-private files for screenshots and large screen snapshots; WorkManager for the retention job. Room adds roughly 300 KB after R8 and gives us migrations and a query layer for the history viewer.

---

## 4. Final recommended stack

| Layer | Choice | Justification |
|---|---|---|
| Language | Kotlin 2.x, coroutines, minSdk 30, targetSdk 36 now, 37 in Phase 5 | Framework APIs; Play requires 36 since Aug 2026 |
| Build | AGP 9, Gradle version catalogs, R8 full mode, AAB with ABI splits (arm64-v8a primary, armeabi-v7a optional), resource shrinking, 16 KB page alignment | Size and Play requirements |
| UI | Views + Material Components; accessibility overlay window | Smallest, fastest start |
| Audio | `AudioRecord`, 16 kHz mono PCM16, `VOICE_RECOGNITION`, dedicated thread | Standard, well supported |
| Speech | sherpa-onnx AAR (Silero VAD, Zipformer KWS, streaming Zipformer ASR) | One runtime, Apache-2.0, streaming |
| Command engine | Kotlin grammar + fuzzy target matcher | Deterministic, microsecond cost |
| Planner | Provider-neutral interface; adapters for Claude, OpenAI, Gemini, OpenRouter; ModelRouter picks the model | User chooses provider, app chooses model; no vendor SDKs |
| Feedback | System TTS + earcons | Zero size |
| Storage | DataStore for settings, Keystore-wrapped per-provider keys, Room for history, app-private files for models and screenshots, WorkManager retention job | Standard, small |
| Testing | JUnit 5, Robolectric for grammar, instrumented tests on a fixture app, Macrobenchmark, Perfetto | See Section 14 |

Dependency rule: every library must justify its APK and startup cost in the dependency table kept in the repo, with the measured delta.

---

## 5. Voice processing and wake-word architecture

### 5.1 Pipeline

```
AudioRecord (16 kHz, 20 ms reads)
   │
   ▼
Ring buffer (2 s)  ──► VAD (Silero, 32 ms chunks)
                          │ speech?
                 no ──────┤──────── yes
                 │        ▼
            (sleep)   state == IDLE ? ──► KWS (Zipformer, open vocab)
                                              │ wake phrase hit
                                              ▼
                                   state = COMMAND (earcon + overlay)
                                   ASR stream fed from ring buffer
                                   starting 300 ms before wake end
                                              │ partials every ~100 ms
                                              ▼
                                   IntentRouter (early commit on stable partial)
                                              │ endpoint (VAD silence 400 to 700 ms, adaptive)
                                              ▼
                                   final transcript -> route
                                   state = IDLE after task or 8 s silence
```

### 5.2 Design rules

- **One microphone owner.** Only `ListeningService` opens `AudioRecord`. Engines receive frames; they never capture.
- **VAD gates everything.** KWS and ASR consume CPU only while speech is present. Idle cost is VAD plus the read loop.
- **Follow-on mode.** After a task completes, keep COMMAND state for a short window (configurable, default 6 s) so the user can chain commands without repeating the wake word. The overlay shows this state clearly.
- **Barge-in.** The word "stop" (and configured synonyms) is matched on partials in every state and cancels the current task and TTS within one frame.
- **Endpointing.** Adaptive: short commands finalize after 400 ms of silence; if the partial ends in a preposition or looks incomplete, extend to 700 ms.
- **Noise and failure handling.** Low ASR confidence triggers a short TTS clarification ("Did you say scroll down?") only for actions; never for stop. Three consecutive failed recognitions pause the earcon and show a hint.
- **Mic loss.** `AudioRecordingCallback.isClientSilenced` moves the pipeline to PAUSED (phone call, another privacy-sensitive recorder). Resume automatically when unsilenced.
- **Pre-roll.** Feed ASR from the ring buffer starting before the wake phrase ended so the first word of the command is never clipped.

### 5.3 Model loading

Models are memory-mapped from app-private storage at session start (VAD and KWS immediately, ASR warmed in the background within the first second). Session stop releases ASR; VAD and KWS stay resident only while the session is on.

### 5.4 First-launch module setup

The APK contains code and the native speech runtime only. Everything heavy is a module the user downloads on first launch.

| Module | Contents | Size (estimate, verify in spike) | Required |
|---|---|---|---|
| `core-speech` | Silero VAD + Zipformer keyword spotting model | ~6 MB | Yes |
| `asr-en` | Streaming Zipformer int8, English | ~45 to 70 MB | Yes for the default engine; skipped if the user picks the Android on-device recognizer (Variant B) |
| `asr-<lang>` | Additional language packs | 40 to 120 MB each | Optional |
| `asr-en-hq` | Larger, more accurate English model | ~150 MB | Optional |
| `tts-voices` | None; system TTS is used. Listed so the setup screen can deep-link to system voice downloads if none is installed offline | 0 | Informational |

Setup flow: first launch -> explain what the app does and why it needs accessibility and the microphone -> module screen listing required and optional modules with sizes, total, and a Wi-Fi recommendation -> download with resume, progress in a Live Update notification, SHA-256 verification against a signed catalog -> accessibility and microphone permission steps -> pick provider and paste key -> choose wake phrase. The app is usable for local commands as soon as `core-speech` and an ASR engine are present. Missing optional modules appear as "download to enable" in settings.

Catalog: a signed JSON file hosted with the releases (GitHub Releases or a mirror) listing module ids, versions, URLs, sizes and hashes. The app checks it at most daily and offers updates; nothing updates silently. Users can also side-load a module file for offline installs.

Why not download native code too: the sherpa-onnx and ONNX Runtime libraries (several MB) stay in the APK. Android 17 requires natively loaded files to be read-only and discourages dynamic code loading, F-Droid forbids it, and it is a supply-chain risk. Models are data, not code, so they are safe to download.

---

## 6. Android interaction and gesture architecture

### 6.1 Screen model

On `TYPE_WINDOW_STATE_CHANGED` and throttled `TYPE_WINDOW_CONTENT_CHANGED` (coalesced to at most every 150 ms), rebuild a `ScreenModel` off the main thread:

- Window list with package, title, type, focus.
- Node index: depth-first walk of the active window, keeping nodes that are visible and either actionable (clickable, long-clickable, scrollable, editable, checkable) or carry text, content description, hint, tooltip or pane title. Each entry gets a stable short id, role, label, state flags, bounds, and a parent chain for disambiguation.
- Cap at roughly 200 entries; large lists are summarized with first and last visible items.
- Password fields (`isPassword`) are flagged and never typed into by the planner.

Known gaps and mitigations: Compose and Flutter apps expose semantics only when developers set them; WebViews are slow across JNI and sometimes incomplete. Mitigations are the numbers overlay, the grid overlay, and the screenshot fallback.

### 6.2 Action registry (the only way to touch the device)

| Action | Implementation | Verification |
|---|---|---|
| `tap(target)` | Prefer `ACTION_CLICK` on the node; fall back to `dispatchGesture` at node center if the node reports no click action or the click returns false | New window, node state change, or content change within 300 to 800 ms |
| `double_tap(target|point)` | Two 50 ms strokes separated by 100 ms | Content change |
| `long_press(target|point)` | `ACTION_LONG_CLICK` then 600 ms stroke fallback | Context menu or window change |
| `swipe(direction, distance, container?)` | Single stroke, 250 to 400 ms, within the container's bounds; distance as fraction of container size | Bounds or content change |
| `scroll(direction, container?)` | `ACTION_SCROLL_FORWARD/BACKWARD` on the nearest scrollable; fallback to swipe | Scroll event or bounds diff |
| `pinch(zoom_in|zoom_out, center?)` | Two strokes converging or diverging over 400 ms | Content change |
| `back`, `home`, `recents`, `notifications`, `quick_settings` | `performGlobalAction` | Window change |
| `open_app(package)` | Launch intent with `FLAG_ACTIVITY_NEW_TASK`; respects Android 10+ background activity start rules because the accessibility service is exempt from them when it holds the overlay focus; otherwise fall back to a notification trampoline | Foreground package equals target |
| `type(target, text, submit?)` | Focus, `ACTION_SET_TEXT`; fallback: service-as-IME commit text (API 33+); optional `IME_ACTION_SEARCH` via `ACTION_IME_ENTER` | Node text equals input |
| `find(text)` | Pure query over `ScreenModel`; may trigger scroll-and-search up to N pages | Returns ids |
| `wait(condition, timeout)` | Window title, package, node presence | |
| `show_numbers` / `show_grid` / `hide_overlay` | Overlay | |
| `screenshot()` | `takeScreenshot`, downscale to 720 px wide, JPEG 70; only if the user enabled visual mode | |
| `speak(text)`, `ask_user(question)`, `confirm(action)` | Feedback | User reply |
| `done(summary)` | Ends the task | |

Gesture constraints to respect: `GestureDescription.getMaxStrokeCount()` and `getMaxGestureDuration()` are queried at runtime (not hardcoded); a new gesture while one is in progress cancels the first; dispatch fails if the user is touching the screen; secure windows refuse screenshots; some system UI surfaces refuse injection.

### 6.3 Target resolution

Local commands name targets by label ("tap search", "open the menu"). Resolution order: exact label match, normalized match (case, punctuation, articles), token overlap, then Levenshtein within a bounded edit distance, all restricted to visible actionable nodes. Ties or no match lead to the numbers overlay ("say a number") rather than a guess.

### 6.4 Verification and recovery

After every action: wait for a settle signal (an accessibility event or 300 ms), re-snapshot, compute a diff against the pre-action model, and judge success per the table above. On failure: one alternative strategy (node action vs gesture), then report failure to the caller (the command engine speaks it; the agent loop re-plans). Hard limits: 2 retries per action, 15 steps per task, 90 s per task.

---

## 7. AI agent and tool-calling architecture

### 7.1 Loop

```
user utterance (complex)
   │
   ▼
AgentLoop.start(task, CancellationToken)
   │  observe: ScreenModel -> compact text (<= ~4k tokens), optional screenshot
   ▼
Planner.plan(request)  ──► PlannerResponse = ToolCalls[] | AskUser | Done
   │
   ▼
for each tool call (max 3 per step):
   validate schema -> validate target exists -> sensitivity gate -> execute -> verify
   on verify failure: stop the batch, re-observe, re-plan with the failure attached
   │
   ▼
loop until Done, AskUser, budget exhausted, or cancellation
   │
   ▼
speak summary; return to COMMAND or IDLE
```

### 7.2 Planner request contents

- System prompt (stable, cached): role, the action registry as strict tool schemas, safety rules, output conventions.
- Task: the utterance, the app in focus, elapsed steps.
- Observation: the compact screen model (ids, roles, labels, states, bounds quantized to a 100-unit grid), window title, whether the previous action verified.
- Short-term memory: the last 5 steps and outcomes as plain text.
- Optional screenshot block when visual mode is on and the tree is sparse.

The planner never sees raw audio, full-resolution screenshots by default, or password field contents.

### 7.3 Provider neutrality

A `Planner` interface with request and response types owned by us: `ToolCall(name, argsJson)`, `AskUser(text)`, `Done(summary)`, plus `reasoningSummary: String?` and `usage` on every response. Each adapter maps our types to its wire format and back. The registry's JSON Schemas are the single source of truth; adapters translate them (Claude and OpenAI take JSON Schema nearly verbatim; Gemini needs its schema dialect; OpenRouter passes OpenAI format through). Conversation history is kept in our neutral form and re-rendered per provider on each call, so switching provider mid-session is possible and the history viewer is provider-agnostic.

Adapter contract tests replay recorded responses (tool call, text, refusal, rate limit, malformed JSON, stream cut mid-way) and assert identical neutral output. Adding a provider means one adapter class, one row in the model table and one settings entry.

### 7.4 Model routing

The user chooses only the provider. The project maintains a model table per provider with three roles. The table is versioned in the repo and can be refreshed from a hosted JSON file the app fetches at most once a day, so model updates do not require an app release.

| Role | Used for | Selection rule |
|---|---|---|
| `planner` | Step planning in the agent loop | Strongest model with reliable tool calling at acceptable latency; low reasoning effort setting where the provider has one |
| `vision` | Steps where a screenshot is attached | Must support images; may equal `planner` |
| `fast` | Clarification questions, summaries, disambiguation of a target label among candidates | Cheapest model with tool calling |

Initial table (to be measured in the Phase 4 eval and revised on data; on OpenRouter the router only offers models whose tool-calling support is verified):

| Provider | `planner` | `vision` | `fast` |
|---|---|---|---|
| Claude | `claude-opus-5-5`, effort `low` | same | `claude-haiku-4-5` |
| OpenAI | current flagship reasoning model at low effort | same | current small model |
| Gemini | current Pro or Flash thinking model | same | current Flash-Lite model |
| OpenRouter | user-visible list filtered by tool support; default to the Claude or OpenAI flagship entry | same | cheapest verified entry |

Routing rules: a per-task fallback chain within the same provider (planner -> fast on repeated 5xx or rate limit, with a spoken notice); per-call timeout 20 s; retries with jitter on 429 and 5xx only; capability checks before attaching an image. Users may override any cell under an advanced setting; the default path never asks them to pick a model.

### 7.5 Provider adapter notes

Claude
- Tools with `strict: true`, `additionalProperties: false`, `required`; `tool_choice` on `auto` (forced choice is rejected on current models); system prompt instructs tool-call-only responses.
- Thinking cannot be disabled on Opus 5.5; `output_config.effort` is the latency lever. Request `thinking: {type: "adaptive", display: "summarized"}` so a reasoning summary exists for the history viewer.
- Prompt caching: tools and system prompt first and unchanged; a `cache_control` breakpoint after them; observation last; verify with `cache_read_input_tokens`.
- Server-side fallbacks (`fallbacks: "default"` with the `server-side-fallback-2026-07-01` beta) so a safety refusal is routed; check `stop_reason` for `refusal` and tell the user.
- Append the full `response.content` back on every turn; never edit earlier turns (preserved thinking).
- Stream the response so the first tool call can start while the rest arrives, only when the batch is independent.

OpenAI
- Responses API with strict function tools and `parallel_tool_calls` on; reasoning effort low; request reasoning summaries where the model supports them.

Gemini
- `generateContent` with function declarations; function-calling mode `ANY` restricted to the registry names when a tool call is mandatory; thinking budget low; capture thought summaries.

OpenRouter
- OpenAI-compatible Chat Completions; send the app name and URL headers OpenRouter asks for; tolerate providers that return tool calls as text by validating and rejecting with a re-plan.

All adapters
- Every request body (keys redacted), response, reasoning summary, token usage and latency is written to HistoryStore before the tool calls are executed, so a crash mid-step leaves a complete record.
- Keys live in SecretStore only; adapters receive them per call and never log them.

### 7.6 Safety and confirmation

- **Sensitivity gate** in the orchestrator, not in the prompt: a tool call is held for spoken confirmation when any of these hold: the foreground package is in a sensitive category (banking, payments, wallets, password managers, device admin), the target label matches a pattern list (pay, send, transfer, buy, confirm order, delete, remove, factory reset, uninstall, grant, allow), the target is a Settings security or accessibility page, or the planner itself requested confirmation.
- **Never**: type into password fields; accept a tool not in the registry; accept coordinates outside the active window; run more than one task at a time; continue after the user says stop.
- **Confirmation is voice plus overlay**: "Confirm sending the message? Say yes or no." Timeout defaults to no.
- **Audit trail**: every task is fully recorded in HistoryStore (Section 7.7) and viewable in the app.

### 7.7 Session history and persistence

Everything the agent hears, sees, thinks and does is recorded locally and shown in a history viewer. Nothing is uploaded.

Data model (Room):

```
Session      id, startedAt, endedAt, wakePhrase, provider, pinned
Task         id, sessionId, utterance, transcriptConfidence, route (local|agent),
             status (success|failed|cancelled|needs_user), startedAt, endedAt,
             finalSummary, totalTokensIn, totalTokensOut, estimatedCost
Step         id, taskId, index, kind (observe|plan|act|verify|ask|confirm),
             startedAt, durationMs
Observation  stepId, packageName, windowTitle, screenModelFileRef (pruned tree text),
             screenshotFileRef?, nodeCount
PlannerCall  stepId, provider, model, role (planner|vision|fast),
             requestFileRef (keys redacted), responseFileRef,
             reasoningSummary, toolCallsJson, tokensIn, tokensOut, cachedTokens,
             latencyMs, stopReason, error?
Action       stepId, toolName, argsJson, target (id, label, bounds),
             strategy (nodeAction|gesture), gatedBySensitivity, confirmedByUser?
Verification stepId, expected, observedDiffSummary, passed, retryIndex
Transcript   taskId, partialsJsonl (timestamped), finalText
Metric       sessionId, name, value, at
```

Files: screenshots as JPEG in `files/history/<sessionId>/`, screen model text and planner bodies as gzip JSON. Typical cost per agent step is 30 to 80 KB without a screenshot and 150 to 250 KB with one; a heavy day of use stays under 50 MB.

Viewer: list of sessions -> tasks -> step timeline showing the transcript, each observation (tree summary, screenshot thumbnail with the tapped target highlighted), the planner's reasoning summary and tool calls, the action taken, and the verification result. Filters by status and provider. Per-task actions: pin (exempt from retention), share as a redacted zip, delete.

Chain of thought: providers return reasoning summaries, not raw chains of thought (Claude never exposes the raw chain). The viewer stores and shows whatever the provider returns, labeled as a summary, together with the exact tool calls and our own verification reasoning, which is deterministic and fully logged.

Retention: a WorkManager periodic job (daily, charging or idle preferred) deletes sessions whose `endedAt` is older than the retention window. Default 7 days; options 1, 3, 7, 14, 30 days, forever; pinned sessions are exempt; screenshots can have a shorter window than text (default: same). A storage settings row shows current usage and a "delete all history" button. Deleting a session removes its rows and files in one transaction.

Privacy rules: API keys and password-field contents are never written; the record of a screenshot step is kept even if visual mode is later disabled; exports redact keys and let the user exclude screenshots.

### 7.8 Task visualization with Live Updates

The microphone foreground service already requires an ongoing notification. On Android 16 and later that notification becomes a Live Update so the user can follow the agent without opening the app, from the lock screen or the status bar chip.

- Build with `Notification.ProgressStyle`; declare `android.permission.POST_PROMOTED_NOTIFICATIONS` (normal permission, no prompt); call `setRequestPromotedOngoing(true)` and `setOngoing(true)`. Use `setShortCriticalText` for the chip text ("Listening", "Step 3/6", "Confirm?").
- Mapping from the agent state machine:

| State | Chip | Title | Progress |
|---|---|---|---|
| Idle, session on | mic glyph | "Listening for '<wake phrase>'" | indeterminate off |
| Heard command | "Heard" | the transcript | indeterminate on |
| Planning | "Thinking" | the task | indeterminate on |
| Acting | "Step n/N" | the current action in words ("Tap Search") | segments = planned steps, points = verified steps, tracker icon moves |
| Awaiting confirmation | "Confirm?" | what will happen | paused; Confirm and Deny actions |
| Done | "Done" | the summary | full; auto-collapses to idle after 10 s |
| Failed or cancelled | "Stopped" | the reason | full in error color; collapses to idle |
| Paused (mic lost, call) | "Paused" | why | indeterminate off |

- Updates are throttled to at most one per 500 ms to stay within notification rate limits; the planned step count comes from the planner's batch, re-segmented when a re-plan happens.
- On Android 15 and below the same builder falls back to a standard ongoing notification with `setProgress` and the same text and actions.
- The overlay remains the primary in-app indicator; the Live Update is for the lock screen and when another app is in front, which is almost always during a task.

Fact check for the implementer: Live Updates require compileSdk and targetSdk 36 and the ProgressStyle, BigTextStyle or CallStyle template; promotion is a request the system may decline on some OEM skins, so the fallback path is also the Android 16 path when promotion is denied.

---

## 8. Native integration strategy

MVP: no first-party native code. sherpa-onnx ships prebuilt `.so` files and a Kotlin API; audio frames cross JNI once per 100 ms as a `FloatArray` or `ShortArray`.

If Rust is introduced later (decision criteria: measured audio-thread jitter, a cross-platform core requirement, or the APK-minimal variant):

- Crate `voicecore` built with cargo-ndk for arm64-v8a (and armeabi-v7a if kept), NDK r28+, 16 KB page alignment flags, `panic = "abort"`, LTO, `opt-level = "z"` for size or `3` for speed, measured.
- Control API via UniFFI (session start/stop, config, callbacks). Audio API via a raw JNI function taking a direct `ByteBuffer` to avoid copies and serialization.
- Rust owns the audio thread; Kotlin receives transcripts through a callback interface on a background dispatcher.
- Android 17 requires natively loaded libraries to be read-only files; libraries inside the APK satisfy this; never extract and `System.load` from writable storage.

C/C++ written by us: none planned. If a custom ORT minimal build is needed for size, it is a build configuration task, not new code.

---

## 9. Data flows

### 9.1 Simple command: "Scroll down"

```
t=0      user finishes saying "scroll down"
t+0      ASR partial "scroll down" is stable for 2 frames -> IntentRouter early-commits
         (grammar match, confidence > threshold)
t+5 ms   LocalCommandEngine resolves container: nearest scrollable node under focus,
         else largest visible scrollable
t+10 ms  Orchestrator: ACTION_SCROLL_FORWARD on node
t+30 ms  system performs scroll; TYPE_VIEW_SCROLLED arrives
t+60 ms  verify: scroll event seen -> success, short earcon, no TTS
         (fallback if action returns false: swipe gesture 60% of container height, 300 ms)
```
Target: speech end to action dispatched under 100 ms when early commit applies, under 500 ms otherwise (dominated by endpointing).

### 9.2 Complex workflow: "Open YouTube and play the second video about Java tutorials"

```
1  Router: not a grammar match -> AgentLoop
2  Observe: launcher screen model
3  Plan (user's provider, planner role, low effort): [open_app("com.google.android.youtube"), wait(package==youtube, 5s)]
   -> PlannerCall row written: request, response, reasoning summary, tokens, latency
4  Execute + verify: foreground package is YouTube
5  Observe: YouTube home, search icon id 12
6  Plan: [tap(12), wait(editable focused)]
7  Verify: search field focused
8  Plan: [type(field, "Java tutorials", submit=true), wait(content changed)]
9  Observe: results list, items with titles and channel names
10 Plan: [tap(second result title id)]
11 Sensitivity gate: not sensitive -> execute
12 Verify: window title changed, player visible -> Done("Playing <title>")
13 TTS: "Playing <title>." State -> COMMAND follow-on window
```
Expected: 4 planner round trips, 6 to 10 s total at cloud latency. Steps 3 and 6 can be merged by the planner when it is confident; the orchestrator still verifies each action.

### 9.3 Interruption

At any point "stop" on a partial transcript sets the `CancellationToken`: in-flight gesture is cancelled, planner HTTP call is cancelled, TTS stops, overlay returns to COMMAND state. Target under 150 ms.

---

## 10. Memory, CPU, battery, latency, APK optimization

### 10.1 Targets (mid-range 2023 baseline, verified on the device matrix)

| Metric | Target | How measured |
|---|---|---|
| Idle listening CPU (VAD only) | < 2 percent of one core | `simpleperf`, `top` over 10 min |
| Active command CPU (KWS + ASR) | < 60 percent of one core, bursts only | Perfetto trace |
| Process PSS, session on, ASR warm | < 150 MB | `dumpsys meminfo` |
| Process PSS, session off | < 40 MB | same |
| Battery, listening idle, screen off | < 2 percent per hour | Battery Historian over 4 h |
| Wake phrase to earcon | < 300 ms | timestamped logs |
| Speech end to local action | < 500 ms p95; < 100 ms with early commit | same |
| Planner step (cloud) | < 2.5 s p50, < 5 s p95 | same |
| Stop to all activity halted | < 150 ms | same |
| Cold start to listening | < 1.5 s | Macrobenchmark |
| APK download size (arm64) | < 12 MB (models are first-launch modules) | `bundletool` or release APK size |
| First-launch required download | `core-speech` + `asr-en` under 80 MB, resumable | ModuleManager logs |
| History storage per agent step | < 100 KB without screenshot, < 300 KB with | HistoryStore metrics |
| Retention job | Deletes a 7-day backlog in < 2 s, runs only when idle or charging | WorkManager logs |
| Jank from accessibility events | 0 main-thread work > 4 ms per event | Perfetto |

### 10.2 Tactics

- Memory: memory-map models; single ORT session per model; release ASR on session stop; avoid retaining `AccessibilityNodeInfo` beyond snapshot building; bounded ring buffers; no bitmaps kept after screenshot upload.
- CPU: VAD gating; coalesce content-changed events; build the screen model off main; cap tree depth and node count; use int8 models; set ORT intra-op threads to 2.
- Battery: no wake locks beyond the FGS; no periodic work when the session is off; pause ASR warmth after 60 s of no speech; auto-pause on low battery (configurable) and on `THERMAL_STATUS_SEVERE`.
- Latency: early intent commit; pre-roll; keep ASR warm during a session; keep an HTTP/2 connection warm to the planner during a task; stream planner output.
- APK: arm64 only in the primary split; R8 full mode; strip ORT to required ops if size is over budget; all models and language packs are first-launch modules (Section 5.4); no Compose for MVP; audit transitive dependencies monthly. Expected APK after modularization: 8 to 12 MB arm64, dominated by the native speech runtime.
- Android 17 memory limiter: budget below the limits and watch `ApplicationExitInfo` for `MemoryLimiter` descriptions.

---

## 11. Permissions, privacy, security, safety

| Permission or capability | Why | When asked |
|---|---|---|
| `RECORD_AUDIO` (runtime) | Microphone | First session start |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE` | Listening service type (required from Android 14) | Install |
| `POST_NOTIFICATIONS` (runtime, 33+) | Session notification with Stop | First session start |
| `POST_PROMOTED_NOTIFICATIONS` (normal, 36+) | Live Update promotion for the task notification | Install |
| Accessibility service enablement | Everything in Section 6 | Guided in onboarding; on Android 13+ sideloaded installs need "Allow restricted settings" unless installed via adb |
| `INTERNET` | Planner and model download | Install |
| `QUERY_ALL_PACKAGES` or `<queries>` | App launch by name | Prefer `<queries>` with launcher intent; `QUERY_ALL_PACKAGES` needs Play justification |

Not requested: `SYSTEM_ALERT_WINDOW` (accessibility overlay suffices), `MediaProjection` (accessibility screenshot suffices), location, contacts, SMS.

Privacy posture:
- Audio never leaves the device. Only final transcripts go to the planner the user configured, and only for complex commands.
- Screen content goes to the planner only during an agent task, pruned, and never from password fields. Screenshots only when visual mode is explicitly on.
- A persistent notification and the system mic indicator show listening; the overlay shows state; stop is one tap or one word.
- All history is local, viewable, exportable by the user and auto-deleted after the retention window (Section 7.7).
- The user's provider keys are stored Keystore-wrapped and sent only to that provider's documented endpoint; the app has no backend of its own. TLS with certificate transparency (default from targetSdk 37).
- The open-source build is reproducible so users can verify that the binary matches the source.

---

## 12. Background execution and lifecycle

- The AccessibilityService process is kept alive by the system while the service is enabled. The listening FGS runs in the same process; no IPC.
- The FGS of type `microphone` must be started while the app is visible or from a user interaction (Activity button, notification action, quick settings tile). Starting it from the background is refused on Android 12+ for while-in-use types. The session therefore always begins with a user action.
- Android 10+ audio sharing: if our service's UI (the overlay) is on top, both we and another app receive audio; otherwise two ordinary capturers cannot share and the most recent wins. We subscribe to `AudioRecordingCallback` and show PAUSED when silenced, resuming automatically.
- Phone calls: pause on `MODE_IN_CALL`, resume after.
- Screen off: keep listening by default (user setting), with the battery and thermal pauses above.
- Process death: the service restarts with the system; the FGS does not auto-restart, by design (microphone must be a user decision). The Live Update notification and tile reflect the real state; a stale "Step 3/6" is never left behind because the notification is owned by the FGS and dies with it.
- OEM background killers: detect the manufacturer and show a one-time guide to disable battery optimization for the app on vendors known to kill foreground services.
- Android 17: background audio (TTS) is permitted because the microphone FGS has while-in-use capability; app memory limits apply per device RAM.

---

## 13. Open-source distribution

### 13.1 How the project ships

- Public repository (Apache-2.0 assumed) with reproducible release builds, signed APKs on GitHub Releases, and an F-Droid-compatible build recipe as a stretch goal (F-Droid requires no proprietary dependencies; the speech stack qualifies, but model downloads from a third-party host need a documented, user-consented source).
- One build flavor. `isAccessibilityTool` is `true`: voice access is an assistive technology, and the flag is what lets the service see views marked `accessibilityDataSensitive` on the user's own device.
- No backend. Users bring their own provider key; the app talks only to the provider endpoint and to the model download host.
- Developer-facing extension points documented in the repo: planner adapters, action registry entries, ASR and wake-word engines, app-specific skill recipes.

### 13.2 Platform facts that still apply to sideloaded installs

- **Android 13+ restricted settings**: apps installed by a browser or file manager cannot enable an accessibility service until the user taps "Allow restricted settings" in App info. Apps installed via adb are not restricted. Onboarding explains both paths.
- **Developer verification**: protections began 30 Sep 2026 in Brazil, Indonesia, Singapore and Thailand on certified devices and expand globally in 2027. Release builds should be signed by a registered developer so installs are not blocked. A free limited-distribution account covers up to 20 devices without ID or fee; a full account removes the cap. adb installs are exempt. Document this in the README.
- **Android 17 Advanced Protection Mode** (user opt-in): revokes accessibility permission for apps not declared as accessibility tools. Our declaration keeps the app working, but users with Advanced Protection on should be told what the mode does.
- **Play Protect**: Google says an app that "is not an accessibility tool" yet sets `isAccessibilityTool=true` can be blocked by Play Protect. A genuine voice-access tool is within the stated eligibility (voice-based systems for users with motor disabilities). The risk is low; if it ever triggers, the user can allow the app in Play Protect.
- **16 KB pages**: required for apps targeting Android 15+; verify the sherpa-onnx libraries are aligned.
- **Target API**: targetSdk 36 now, 37 once tested on Android 17 devices.

### 13.3 Note for forks that want Google Play

Play's AccessibilityService policy permits autonomous planning and execution through the Accessibility API only for apps approved as accessibility tools, and names voice-based systems for motor disabilities as eligible while excluding "assistants" and "automation tools". A Permission Declaration Form and review are required. Forks targeting Play should position the product as assistive voice access and keep the planner behind a build flag. AppFunctions (Android 16+) and the assistant role are the lower-friction paths for app actions in that context; the registry is designed so an `app_function` action can be added without touching the planner.

---

## 14. Testing, benchmarking, monitoring, reliability

- **Unit**: grammar parsing and target matching (Robolectric, hundreds of utterance fixtures); schema validation; sensitivity gate; screen model pruning on recorded tree dumps.
- **Fixture app**: an instrumented sample app with known layouts (lists, tabs, dialogs, WebView, a Compose screen with and without semantics, a password field, a "sensitive" view) used by instrumented tests to assert every registry action and its verification.
- **Real-app scenarios**: a scripted suite (YouTube search, Settings toggle, WhatsApp find conversation, Chrome navigate) run manually per release on the device matrix with pass/fail and step counts recorded.
- **Speech evals**: a command corpus (300+ utterances, several speakers, noise overlays) for WER and intent accuracy; a 4-hour mixed audio set (podcasts, TV, kitchen noise) for wake word false accepts per hour and false rejects.
- **Planner evals**: 40+ task transcripts with expected final states; measure success rate, steps, tokens, cache hit rate, latency per provider and model; run on every prompt, registry or model-table change; this is what decides the model table rows.
- **Adapter contract tests**: recorded responses per provider (tool call, text, refusal, 429, 5xx, malformed JSON, truncated stream) asserting identical neutral output and correct history records.
- **History tests**: a step with and without screenshot stays within the size budget; retention deletes exactly the expired unpinned sessions and their files; export redacts keys.
- **Performance**: Macrobenchmark for startup; Perfetto traces for event handling; `simpleperf` for CPU; Battery Historian 4-hour runs; `dumpsys meminfo` samples. Thresholds from Section 10 enforced as release gates.
- **Device matrix**: one low (4 GB, entry Snapdragon or MediaTek), one mid (Pixel 7a or Galaxy A5x), one high (Pixel 9 or 10, Galaxy S24 or S25), one OEM with aggressive background policies (Xiaomi), Android 11, 14, 16 and 17 covered.
- **Monitoring**: in-app metrics screen fed from HistoryStore; no crash reporting service. Crashes are written to local history and can be exported by the user as a redacted zip for a GitHub issue.
- **Reliability drills**: incoming call during a task; Assistant invoked during listening; Bluetooth headset connect and disconnect; low memory kill and restore; screen off for an hour; TalkBack enabled concurrently.

---

## 15. Major risks, limitations, alternatives

| Risk | Likelihood | Impact | Mitigation or alternative |
|---|---|---|---|
| Provider API drift (tool formats, model retirements) | Certain over time | Adapter breaks or default model disappears | Contract tests on recorded responses; hosted model table refreshed daily; fallback chain; adapters are small |
| History storage growth on heavy use | Medium | Storage complaints | Per-step size budget, screenshot downscaling, 7-day default retention, storage meter and one-tap delete |
| Users paste keys into the wrong provider or leak via export | Low | Key exposure | Provider-scoped key fields with a format check; exports redact; keys never in history |
| Poor accessibility trees in target apps | High for games and some Flutter apps | Commands fail | Numbers and grid overlays; screenshot fallback with vision model; clear spoken failure |
| Wake word false accepts in noisy rooms | Medium | Annoyance, battery | Threshold tuning on the eval set; openWakeWord fallback; require 2-stage confirmation (KWS then ASR re-check of the phrase) |
| ASR accuracy for accents and names | Medium | Wrong targets | Fuzzy match with confirmation on low confidence; model upgrades through the same runtime; optional cloud ASR for the agent path only, user opt-in |
| Cloud planner latency and cost | Certain | Slow complex tasks | Fast path covers most commands; caching; effort low; batch independent steps; measure cheaper models |
| Mic contention | Medium | Session silently paused | Recording callbacks, PAUSED state, auto-resume, overlay indication |
| OEM kills the FGS | Medium on some vendors | Session ends | Vendor guidance screen; notification reflects state; never auto-restart mic |
| sherpa-onnx binary size over budget | Medium | APK target missed | Minimal ORT build; Variant B for GMS devices |
| Android 17 memory limiter | Low | Process killed | Budgets in Section 10; watch exit info |
| Developer verification blocks sideload installs in 2027 | Certain by region | Personal distribution friction | adb installs; limited-distribution account |
| Planner hallucinated or unsafe action | Medium | Harm | Registry only, strict schemas, target existence check, sensitivity gate, confirmation, step budget, stop word |

Known hard limitations: no injection into secure windows or some system UI; no screenshots of secure content; cannot act while the user is touching the screen; no hotword detection while another privacy-sensitive recorder (a call) holds the mic; on-device planners not viable for the general population yet.

---

## 16. Development roadmap

| Phase | Duration (one experienced engineer) | Deliverables | Exit criteria |
|---|---|---|---|
| 0 Spikes | 2 weeks | (a) sherpa-onnx on the device matrix: APK delta, RAM, idle CPU, wake latency, ASR partial latency; (b) FGS microphone from tile and notification on Android 14, 16, 17; (c) gesture and node-action coverage on YouTube, Settings, WhatsApp, Chrome, one Flutter app; (d) planner prototype on recorded screen models against all four providers: tool-call reliability, latency, cache behavior, reasoning summary availability; (e) `EXECUTE_APP_FUNCTIONS` protection level and assistant role capabilities; (f) per-step history size with and without screenshots | Numbers recorded against Section 10 targets; go or adjust on Variant A |
| 1 Foundation | 3 weeks | Project skeleton, AccessibilityService, ScreenModel, action registry, orchestrator with verification, overlay (states, numbers, grid), TaskNotification with Live Update and legacy fallback, HistoryStore schema and retention job, fixture app and instrumented tests | All registry actions pass on the fixture app on 3 devices; every action leaves a history record; notification states verified on Android 14 and 16 |
| 2 Voice | 3 weeks | Listening FGS, audio thread, VAD, KWS with user-chosen phrase, streaming ASR, endpointing, barge-in, PAUSED handling, ModuleManager with signed catalog, resumable downloads and first-launch setup flow, TTS and earcons | Wake and command latencies within targets; 4-hour battery run passes; fresh install reaches a working local command in under 5 minutes on Wi-Fi |
| 3 Local commands | 2 weeks | Grammar (navigation, scroll, swipe, open app, tap by label, type, zoom, volume, numbers, stop), fuzzy targets, early commit, confirmations | 95 percent intent accuracy on the command corpus; p95 speech end to action under 500 ms |
| 4 Agent | 5 weeks | Planner interface and neutral history form, ModelRouter with model table, four adapters (Claude, OpenAI, Gemini, OpenRouter) with contract tests, provider setup UI with key entry and validation call, AgentLoop, sensitivity gate, confirmations, full step recording, screenshot fallback (opt-in), planner eval harness run against every provider | 80 percent success on the 40-task eval on at least two providers; zero unconfirmed sensitive actions; every step reproducible from history |
| 5 History viewer and hardening | 4 weeks | History viewer (sessions, tasks, step timeline, screenshot with target highlight, reasoning summaries, filters, pin, export, delete), storage meter, retention settings, perf tuning against budgets, OEM guidance, call and Bluetooth drills, targetSdk 37, Variant B engine, onboarding (restricted settings, adb, developer verification) | Release gates green on the device matrix; daily personal use for 2 weeks without a reset; retention job verified over a simulated 30 days |
| 6 Open-source release | 2 weeks | License and contribution docs, reproducible signed release build, GitHub Releases pipeline, architecture docs for extension points, model download host documented, issue templates, security policy | First public tagged release installable from the README instructions |
| 7 Extensibility | ongoing | AppFunctions action, Gemini Nano adapter when background execution is allowed, additional languages, F-Droid recipe, optional Rust core if measurements call for it | Per feature |

Total to the first public release (Phases 0 to 6): roughly 21 weeks for one engineer; two engineers can run Phase 2 alongside Phases 1 and 3, and the viewer alongside Phase 4, and reach it in about 13 weeks.

---

## 17. MVP scope and acceptance criteria

**In scope**: English; first-launch module setup with required and optional downloads; user-chosen wake phrase; local commands listed in Phase 3; numbers and grid overlays; agent planner with provider choice (Claude, OpenAI, Gemini, OpenRouter), user-supplied key and automatic model routing; confirmations; stop word; Live Update task notification (Android 16+) with legacy fallback and quick settings tile; full history recording with viewer, pin, export, delete and configurable retention (default 7 days); settings for wake phrase, follow-on window, screen-off behavior, visual mode, provider and key, retention; local metrics screen; public source repository.

**Out of scope for MVP**: on-device LLM, Variant B engine, languages other than English, Rust core, AppFunctions, F-Droid listing, Wear or TV.

**Acceptance criteria**

1. On the mid and high devices, "scroll down", "go back", "open YouTube", "tap search" execute within 500 ms p95 of speech end, 100 trials each, success at least 97 percent in a quiet room and 90 percent with TV noise at 60 dB.
2. Wake word: at most 1 false accept per 4 hours of mixed background audio; at least 95 percent true accepts across 3 speakers at 1 m.
3. The three showcase tasks ("open YouTube and play the second video about X", "open Settings and turn on Wi-Fi", "open WhatsApp and find the conversation with Y") succeed at least 8 of 10 attempts each, with every step verified and no unconfirmed sensitive action.
4. Saying "stop" halts gestures, planner calls and speech within 150 ms in 20 of 20 trials.
5. A 4-hour idle listening session on the mid device consumes at most 8 percent battery with the screen off and stays under 150 MB PSS.
6. The APK download for arm64 is at most 15 MB; first-run model download at most 80 MB with integrity verification.
7. Incoming call, Bluetooth headset change and Assistant invocation each pause and resume listening correctly, shown on the overlay and notification.
8. No audio or screenshot data leaves the device unless a complex command is being planned, and then only transcripts and pruned tree text (plus a screenshot when visual mode is on) go to the provider the user configured.
9. Switching provider in settings and re-running the three showcase tasks works on all four providers with no code change; a wrong key is reported within 5 s by the validation call.
10. Every agent step in the last 7 days is visible in the history viewer with its screenshot (if taken), reasoning summary, tool calls and verification result; a session older than the window disappears after the next retention run; a pinned session does not.
11. The APK download for arm64 is at most 12 MB; a fresh install offers the module screen on first launch, downloads `core-speech` and `asr-en` with resume after an interrupted connection, verifies hashes, and refuses a tampered file.
12. On an Android 16 device, the status bar chip and lock screen show the task state and step progress throughout the three showcase tasks, and Confirm and Deny from the notification work; on Android 14 the same text and actions appear in a standard progress notification.

---

## 18. Future scalability and extensibility

- **Providers**: new planner adapters are one class plus one model-table row; the hosted model table lets defaults change without a release; on-device planners slot in when background execution and device coverage allow.
- **History as a dataset**: the recorded steps (observation, plan, action, verification) are exactly the format needed to build an offline eval set or to fine-tune a small local model later, with user consent and export.
- **Actions**: the registry is data-driven; AppFunctions becomes one more action type; app-specific skills (recipes for known apps) can be shipped as JSON and selected by package.
- **Speech**: engines sit behind `AsrEngine` and `WakeWordEngine`; languages are model packs in the ModelStore.
- **Native core**: if adopted, the Rust `voicecore` can be reused on iOS or desktop since it has no Android dependencies beyond the audio input callback.
- **Multi-device**: the planner protocol is stateless per step, so a companion desktop or watch trigger is a transport change.
- **Learning**: the audit log plus user corrections ("no, the other one") form a local dataset for improving target resolution heuristics without sending data anywhere.

---

## Verification of this blueprint

Before implementation starts, Phase 0 spikes validate the assumptions with numbers on real devices: APK and RAM deltas for sherpa-onnx, wake and ASR latencies, FGS start paths on Android 14 to 17, gesture coverage on the target apps, planner cache hit rate and step latency, and the AppFunctions permission level. Any target in Section 10 that a spike shows is unreachable on the mid device triggers a documented revision of this plan (Variant B, model swap or target change) rather than silent slippage.

---

## Sources

Android platform
- AccessibilityService reference: https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- GestureDescription reference: https://developer.android.com/reference/android/accessibilityservice/GestureDescription
- Sharing audio input (Android 10+ rules): https://developer.android.com/media/platform/sharing-audio-input
- Foreground service types and changes: https://developer.android.com/develop/background-work/services/fgs/service-types and https://developer.android.com/develop/background-work/services/fgs/changes
- Android 12 FGS launch restrictions: https://developer.android.com/about/versions/12/foreground-services
- SpeechRecognizer reference: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Media projection consent changes: https://developer.android.com/media/grow/media-projection
- Android 17 behavior changes: https://developer.android.com/about/versions/17/behavior-changes-all and https://developer.android.com/about/versions/17/behavior-changes-17
- Enhancing Android security (accessibilityDataSensitive, isAccessibilityTool enforcement), 11 Dec 2025: https://developer.android.com/blog/posts/enhancing-android-security-stop-malware-from-snooping-on-your-app-data
- Android 17 Advanced Protection and accessibility: https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860/
- Android 13 restricted settings: https://www.esper.io/blog/android-13-sideloading-restriction-harder-malware-abuse-accessibility-apis
- Developer verification: https://developer.android.com/developer-verification
- 16 KB page sizes: https://developer.android.com/guide/practices/page-sizes
- AppFunctions overview: https://developer.android.com/ai/appfunctions
- Voice Access commands (reference product): https://support.google.com/accessibility/android/answer/6151854
- Live Updates and progress-centric notifications (Android 16): https://developer.android.com/develop/ui/views/notifications/live-update and https://developer.android.com/about/versions/16/features/progress-centric-notifications

Google Play policy
- Use of the AccessibilityService API: https://support.google.com/googleplay/android-developer/answer/10964491
- Policy announcement 29 Oct 2026: https://support.google.com/googleplay/android-developer/answer/18100766
- Target API level requirements: https://support.google.com/googleplay/android-developer/answer/11926878

Speech and inference
- sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx and keyword spotting docs https://k2-fsa.github.io/sherpa/onnx/kws/index.html
- Streaming Zipformer models: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-transducer/zipformer-transducer-models.html
- On-device streaming ASR comparison (2026): https://dev.to/voxrtio/on-device-streaming-asr-in-2026-five-approaches-and-when-to-pick-what-5h1n
- openWakeWord: https://pypi.org/project/openwakeword/
- Silero VAD on Android: https://soniqo.audio/guides/vad/android
- Picovoice pricing and free tier end: https://picovoice.ai/pricing and https://community.home-assistant.io/t/fyi-picovoice-confirmed-free-tier-accesskeys-will-stop-working-after-june-30-2026/1012744
- ML Kit GenAI Prompt API and function calling: https://developers.google.com/ml-kit/genai/prompt/android and https://developers.google.com/ml-kit/genai/prompt/android/function-calling
- LiteRT-LM: https://developers.googleblog.com/en/blazing-fast-on-device-genai-with-litert-lm/
- UniFFI design principles: https://mozilla.github.io/uniffi-rs/internals/design_principles.html
