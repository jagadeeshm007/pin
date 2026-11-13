---
paths:
  - "**/*.kt"
  - "**/*.kts"
  - "**/AndroidManifest.xml"
  - "**/res/**"
---

# Android and Kotlin rules

## Structure

- Package root `io.github.jagadeeshm007.pin`. Feature packages: `accessibility`,
  `voice`, `command`, `agent`, `planner`, `history`, `modules`, `notification`,
  `settings`, `ui`. Shared types in `core`.
- Dependencies are wired by hand in `PinApp` (a small service locator). No Hilt,
  no Koin (ADR-0002). Constructors take interfaces so tests pass fakes.
- UI is Views and Material Components with view binding. No Compose (ADR-0002).
- One `StateFlow` of UI state per screen controller; the mutable flow is private.

## Concurrency

- Coroutines only in a lifecycle scope, a service scope or an injected scope.
  Never `GlobalScope`. Dispatchers are injected so tests can replace them.
- Never catch `CancellationException`; rethrow it.
- The audio thread is a dedicated thread at `THREAD_PRIORITY_URGENT_AUDIO`,
  not a coroutine dispatcher.
- AccessibilityService callbacks run on the main thread: record the event and
  return. Tree walks and snapshots happen on a background dispatcher.
- Do not keep `AccessibilityNodeInfo` references after the snapshot is built.
  Release nodes through one helper that calls `recycle()` below API 33 (it is a
  no-op from 33).

## Platform

- minSdk 30, targetSdk 36, compileSdk 37. Guard newer APIs with `Build.VERSION.SDK_INT` and
  cover both branches in tests where behaviour differs.
- The microphone foreground service starts only from a user action (activity,
  notification action, quick settings tile). Never from the background.
- Query gesture limits (`GestureDescription.getMaxStrokeCount`,
  `getMaxGestureDuration`) at runtime; never hard-code them.
- No exported component without a permission, except the launcher activity and
  the accessibility service (protected by `BIND_ACCESSIBILITY_SERVICE`).
- Natively loaded libraries come from the APK only; never `System.load` a
  downloaded file.

## Data

- Room is the only database. Schema changes come with a migration and a
  migration test; exported schemas live in `app/schemas/`.
- Settings in DataStore. API keys only through `SecretStore` (Tink AEAD with a
  Keystore-wrapped keyset), excluded from backup.
- Versions only in `gradle/libs.versions.toml`.

## Tests

- Unit tests: JUnit Jupiter, kotlinx-coroutines-test, Turbine, hand-written fakes.
  No sleeps, no real network; provider adapters use recorded JSON fixtures.
- Instrumented tests: JUnit 4 AndroidX runner against the fixture app.
