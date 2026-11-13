# PIN-15 verification

Device: Nothing A059, Android 17 (API 37), debug build.
Gate: `make check` passed, output tail:

```
test: 184 tests passed
check: passed (ktlint, lint, test, build)
```

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | Collected on service connect and on opening the main screen, off the main thread (`Dispatchers.Default`, store on `Dispatchers.IO`); `files/device/profile.json` written via a temp file | pass |
| AC-2 | Profile: one cutout `[454, 0, 540, 126]`, TOP, PUNCH_HOLE; navigation GESTURE. `adb shell settings get secure navigation_mode` returned 2 (gesture). `DeviceProfileTest` covers the classifiers | pass |
| AC-3 | Profile: `maxGestureStrokes` 20, `maxGestureDurationMs` 60000, read from `GestureDescription` at runtime | pass |
| AC-4 | `QuirkBookTest` (4 cases); device: two scrolls each in YouTube and Settings recorded `node_ok=2` for both | pass; the gesture-rescue path is covered by unit tests only, because YouTube's home feed accepted node scrolls this time |
| AC-5 | Profile fields are hardware and settings only; quirks hold package names and action types; `DeviceProfileTest.jsonHoldsNoUserContentFields` | pass |

Cross-check with adb:

| Field | Pin | adb |
| --- | --- | --- |
| Screen | 1080 x 2392 | `wm size` 1080x2392 |
| Density | 375 | `wm density` physical 420, override 375 (user display size setting) |
| Security patch | 2026-11-01 | `ro.build.version.security_patch` 2026-11-01 |
| Navigation | GESTURE | `navigation_mode` 2 |

## Noticed

- YouTube refused node scroll on its search results page (PIN-10) but accepted it on
  the home feed here. Quirks keyed only by app would mislead; before quirks change the
  strategy order, key them by app and screen (window title or activity).
- Density reflects the user's display size setting, which is what layout uses; the
  physical density is not recorded.
