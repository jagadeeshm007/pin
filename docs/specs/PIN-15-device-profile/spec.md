# PIN-15: Device profile and per-app quirks

Status: Approved (maintainer request 2026-11-13, "add an optimization step on launch that understands the device"; open to revision)
Owner: maintainer
Roadmap phase: 1
Blueprint sections: 6.1, 6.2, 12 (OEM differences), 15 (fragmentation risk)

## Problem

Android phones differ in screen shape, cutouts, navigation mode, OEM behaviour and
gesture limits, and apps differ in which accessibility actions they honour (YouTube
refused node scroll and long press on the Nothing A059). Pin guesses today. It should
measure the device once, keep the result, and learn per-app quirks as it works.

## Scope

- In: a `DeviceProfile` (OS version, patch level, maker, model, screen size and density,
  orientation, display cutouts, status and navigation bar insets, navigation mode, gesture
  limits, RAM class, CPU cores, ABI); collected on first launch and on each service
  connect; saved as JSON in app storage; a quirk record per app and action type from
  orchestrator results (node action refused or unverified, gesture succeeded);
  debug commands to show both.
- Out: changing strategy order from quirks (follow-up once enough data exists),
  sending the profile anywhere, UI.

## Acceptance criteria

- AC-1: WHEN the app first opens or the service connects THE app SHALL collect the profile off the main thread and save it, replacing an older one.
- AC-2: THE profile SHALL list each display cutout with its bounds and side, and SHALL classify navigation as gesture, three-button or unknown using public APIs only.
- AC-3: THE profile SHALL hold the platform gesture limits read at runtime.
- AC-4: WHEN an action's node strategy fails and its gesture fallback succeeds THE quirk record SHALL count it for that app and action type; WHEN a node strategy succeeds it SHALL count that too.
- AC-5: THE profile and quirks SHALL contain no user content (no screen text, no account names).

## Privacy

The profile describes hardware and system settings only and stays on the device.
Quirks store package names and action types, nothing from the screen.
