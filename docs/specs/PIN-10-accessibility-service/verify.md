# PIN-10 verification

Commit: 36c8127 (plus this file)
Device: Nothing A059, Android 17 (API 37), arm64, 7.6 GB RAM, debug build installed with adb.
Gate: `make check` passed, output tail:

```
test: 113 tests passed
build: 16M app/build/outputs/apk/debug/app-debug.apk
check: passed (ktlint, lint, test, build)
```

Device checks were driven with the debug receiver:

```bash
adb shell am broadcast -n io.github.jagadeeshm007.pin.debug/io.github.jagadeeshm007.pin.debug.DebugCommandReceiver \
  -a io.github.jagadeeshm007.pin.debug.COMMAND --es cmd "'tap Network and internet'"
```

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | Service off: main screen showed "Accessibility service is off" and the settings button. Service on: Pin's own snapshot of its main screen showed "Accessibility service is on" and no button. (`uiautomator dump` returned a stale tree while the service was active; do not use it for checks.) | pass |
| AC-2 | `SnapshotBuilderTest` (10 cases); device dumps of Settings (36 nodes), YouTube (16 to 53), System UI (105) | pass |
| AC-3 | Content version rose on every action (for example 66 to 76 to 87); every `awaitChange` returned within its timeout | pass |
| AC-4 | Table below: every action type succeeded and verified in Settings and YouTube; pinch in Google Maps | pass |
| AC-5 | `GesturePlannerTest` (13 cases); every gesture on the device completed | pass |
| AC-6 | Merged release manifest has 0 occurrences of `DebugCommandReceiver`, debug has 1; receiver requires `android.permission.DUMP` | pass (permission enforcement by the platform, not separately tested) |
| AC-7 | `AccessibilityScreenSource.current` runs on `Dispatchers.Default`; event handler only increments a counter | pass by design; main-thread time per event not measured |

## Device run

| Command | App | Result | Strategy | Elapsed |
| --- | --- | --- | --- | --- |
| home | any | success | SYSTEM | 453 to 1160 ms |
| open com.android.settings | launcher | success | SYSTEM | 609 to 693 ms |
| scroll down / up | Settings | success | NODE_ACTION | 261 / 329 ms |
| tap Network and internet | Settings | success | NODE_ACTION (row via clickable ancestor) | 290 ms |
| back | Settings | success | SYSTEM | 203 to 481 ms |
| swipe up | Settings | success | GESTURE | 575 ms |
| type Search=bluetooth | Settings search | success | NODE_ACTION | 197 ms |
| doubletap Connected devices | Settings | success | GESTURE | 371 ms |
| open com.google.android.youtube | launcher | success | SYSTEM | 464 to 948 ms |
| tap Share this video | YouTube | declined by confirmation gate, device not touched | none | 3 ms |
| typego Search YouTube=Java tutorials | YouTube | success, results shown | NODE_ACTION | 247 ms |
| scroll down / up | YouTube results | success after node action refused | GESTURE (fallback) | 484 to 1192 ms |
| longpress (result row) | YouTube | success after node action refused | GESTURE (fallback) | 1053 ms |
| pinch out / in | Google Maps | success | GESTURE | 1205 / 1255 ms |

## Bugs found on the device and fixed

1. Open app reported failure: the first event came from the launcher before the app
   appeared. The orchestrator now re-verifies on each change and waits up to 3 s for
   launches (`df9c3b0`, tests `keepsWaitingWhileTheScreenIsStillChanging`,
   `stopsWaitingOnceTheScreenGoesQuiet`, `appLaunchesGetALongerSettleTime`).
2. Submitted YouTube search reported failure: YouTube stays in the same window and shows
   the query as a label. The verifier accepts the query shown after a content change
   (`ef21090`, tests `submittedSearchPassesWhenTheQueryIsShownInTheSameWindow`,
   `submittedSearchFailsWhenNothingChanged`).
3. Debug harness partial label match: "tap Search" matched "Voice search" and opened
   YouTube voice search (it was closed immediately). The harness now uses exact labels
   only (`36c8127`). Proper matching with disambiguation is PIN-31.

## Budgets

| Budget | Target | Measured | How |
| --- | --- | --- | --- |
| Main thread work per accessibility event | < 4 ms | not measured | needs a Perfetto trace (PIN-51) |

## Not verified

- Other devices and Android versions (PIN-4 covers the device matrix).
- Flutter, WebView and Compose-without-semantics apps (PIN-4).
- Main-thread time per event (PIN-51).

## Noticed

- Action latency often exceeds the 500 ms target for local commands (home up to 1.1 s,
  launches up to 1.7 s, gesture fallbacks about 1 s). Most of it is the settle wait and
  re-snapshotting; tuning belongs to PIN-51 and the voice fast path (PIN-30).
- YouTube refuses node scroll and long-click actions; the gesture fallback handled both.
