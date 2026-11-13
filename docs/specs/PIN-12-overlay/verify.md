# PIN-12 verification

Commit: d610c6d (plus this file)
Device: Nothing A059, Android 17, debug build.
Gate: `make check` passed, output tail:

```
test: 132 tests passed
check: passed (ktlint, lint, test, build)
```

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | Window type `TYPE_ACCESSIBILITY_OVERLAY` with `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE`; with the grid shown, `gridpick 7` dispatched a gesture tap at 420,1461 through the overlay and opened Settings > Customisation | pass |
| AC-2 | `NumberLabelerTest` (6 cases); device: 15 badges on the Settings home screen, numbered top to bottom, rows labelled and their text children skipped (screenshot reviewed) | pass |
| AC-3 | `NumberLabelerTest.resolvesNumbersAgainstALaterSnapshotOfTheSameScreen`, `rejectsUnknownNumbersAndMovedTargets`; device: `tapnum 9` opened Apps (344 ms), `tapnum 99` returned "not shown or has moved" | pass |
| AC-4 | `GridModelTest` (6 cases); device: `grid` drew 9 cells, `gridzoom 5` drew 9 cells over 360,797 to 720,1594 (screenshot reviewed), `gridpick 7` tapped the cell centre | pass |
| AC-5 | Device: pill showed "Heard: scroll down" and "Listening" with the right dot colours; `hide` removed the window (0 "Pin overlay" windows in `dumpsys window`) | pass; "within one frame" not measured |
| AC-6 | All `OverlayController` calls are `@MainThread` and the receiver switches to `Dispatchers.Main`; the event handler is unchanged | pass by design |

Mutation check: removing the container rule in `NumberLabeler` failed
`containerHoldingAnotherCandidateIsSkipped`; breaking the grid edge maths failed
`cellsCoverTheAreaWithoutGapsWhenSizesDoNotDivideEvenly`. Both restored.

## Bug found on the device and fixed

The overlay colours were inverted (light pill, dark badges) because Android 17
applied forced dark to the window, whose service context counts as a light theme.
Setting `isForceDarkAllowed = false` on the view was not enough; creating the view
with a dark theme that sets `android:forceDarkAllowed=false` fixed it (`d610c6d`).

## Not verified

- Frame timing of pill updates.
- Overlay on other devices and in landscape (PIN-4).

## Noticed

- T1 and T2 tests were written in the same step as their code, not run red first;
  the mutation check above stands in for that.
