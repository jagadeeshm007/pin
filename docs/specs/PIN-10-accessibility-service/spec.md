# PIN-10: AccessibilityService, screen snapshots and device controller

Status: Approved (maintainer instruction "start building", 2026-11-13; open to revision)
Owner: maintainer
Roadmap phase: 1
Blueprint sections: 6.1, 6.2, 6.4, 12
Depends on: PIN-11

## Problem

The orchestrator from PIN-11 can decide what to do but cannot see or touch the
phone. This task adds the accessibility service that reads the screen into a
`ScreenSnapshot` and performs actions, plus a debug-only way to drive it from
`adb` so it can be tested on real devices before voice exists.

## Scope

- In: service declaration and config (`isAccessibilityTool`, gestures, window
  content); snapshot builder with pruning; content version from accessibility
  events; `ScreenSource` and `DeviceController` implementations (node actions,
  gestures, global actions, app launch, set text with IME enter); service
  status and a shortcut to accessibility settings on the main screen; a
  debug-build command receiver for `adb`.
- Out: overlay (PIN-12), voice (Phase 2), fuzzy target matching (PIN-31), the
  fixture app (moved to a later task; real apps are used for now), screenshots.

## Acceptance criteria

- AC-1: WHEN the service is enabled THE app SHALL show it as enabled; WHEN it is not THE app SHALL offer a button to accessibility settings.
- AC-2: WHEN a snapshot is taken THE builder SHALL keep only visible nodes that are actionable or carry a label, at most 200, with unique ids, and SHALL mark password fields.
- AC-3: WHEN an accessibility event arrives THE content version SHALL increase, and `awaitChange` SHALL return within its timeout.
- AC-4: WHEN tap, long press, scroll, swipe, back, home, open app and type text are run through the orchestrator on a device THEY SHALL succeed and verify on Settings and one third-party app.
- AC-5: WHEN a gesture is built THE strokes SHALL stay inside the window and within the platform's maximum duration and stroke count.
- AC-6: WHEN the debug receiver gets a command from a caller without the DUMP permission THE system SHALL not deliver it; the receiver SHALL NOT exist in release builds.
- AC-7: Taking a snapshot SHALL NOT run on the main thread.

## Budgets touched

Main thread work per accessibility event < 4 ms (event handler only increments a counter).

## Privacy and safety

Screen content is read only on request and kept in memory. The debug receiver
declines all sensitive actions (no confirmer UI yet) and is absent from release.

## Open questions

- Q1: The user must enable the service in Settings; on sideloaded installs Android 13+ also needs "Allow restricted settings". Installing with `adb` avoids that.
