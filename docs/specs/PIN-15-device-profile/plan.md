# PIN-15 plan

Status: Approved (maintainer request, 2026-11-13)
Spec: ./spec.md

| Pure (JVM tests) | Android |
| --- | --- |
| `DeviceProfile` (serializable data), `NavigationMode.classify(tappableBottomPx, gestureBottomPx)`, `CutoutSide.of(rect, screen)`, `RamClass.of(bytes)` | `DeviceProfileCollector`: Build, WindowManager metrics and insets, DisplayCutout, ActivityManager memory, GestureDescription limits |
| `QuirkBook`: counts per (package, action type) of node-ok, node-failed-gesture-ok; JSON round trip | `ProfileStore`: files/device/profile.json and quirks.json, on `Dispatchers.IO` |
| `QuirkObserver : ActionRecorder` decorator that feeds results to the book | wiring in service connect and `MainActivity`; debug `profile` and `quirks` commands |

Navigation mode: with gesture navigation the tappable-element bottom inset is 0 while the
mandatory-gesture bottom inset is not; with three buttons the tappable inset equals the
navigation bar. No hidden settings are read.

No new dependencies.

## Test strategy

- Unit: `DeviceProfileTest` (classifiers, JSON round trip), `QuirkBookTest`.
- Device: collect on the Nothing A059, compare with `adb shell wm size`, `wm density`, `dumpsys window` cutouts and the navigation setting; run YouTube scroll and Settings scroll, read quirks.
