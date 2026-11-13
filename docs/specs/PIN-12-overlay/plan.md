# PIN-12 plan

Status: Approved (maintainer instruction "start building", 2026-11-13)
Spec: ./spec.md

## Approach

| Pure (JVM tests) | Android |
| --- | --- |
| `NumberLabeler`: picks and orders actionable nodes, drops nodes that contain a smaller labelled node, caps at 99, returns `NumberMap` | `OverlayWindow`: one full-screen `TYPE_ACCESSIBILITY_OVERLAY` window, `FLAG_NOT_TOUCHABLE`, `FLAG_NOT_FOCUSABLE`, `FLAG_LAYOUT_NO_LIMITS` |
| `GridModel`: 3 by 3 cells over bounds, refine, minimum cell size | `OverlayView`: a single custom `View` drawing pill, badges or grid on a `Canvas` |
| `OverlayState` enum with text resources | `OverlayController`: main-thread facade owned by the service |

One custom view instead of many child views keeps layout cost at one draw pass.
Number choice and grid choice produce ordinary `DeviceAction.Tap` values, so the
orchestrator still validates, gates and verifies them.

## Changes

| Path | Change |
| --- | --- |
| `overlay/NumberLabeler.kt`, `overlay/GridModel.kt`, `overlay/OverlayState.kt` | pure logic |
| `overlay/OverlayView.kt`, `overlay/OverlayController.kt` | drawing and window |
| `accessibility/PinAccessibilityService.kt` | owns the controller, removes it on unbind |
| `res/values/strings.xml`, `res/values/colors.xml` | pill texts, colours |
| `src/debug/.../DebugCommand*.kt` | `overlay <state>`, `numbers`, `tapnum <n>`, `grid`, `gridpick <n>`, `hide` |

## Dependencies

None.

## Risks

| Risk | Mitigation |
| --- | --- |
| Overlay blocks touches | `FLAG_NOT_TOUCHABLE`, checked on device by tapping through it |
| Too many badges on dense screens | cap at 99 and drop containers that hold a labelled child |

## Test strategy

- Unit: `NumberLabelerTest`, `GridModelTest`.
- Device: show each pill state, numbers on Settings, tap a number, grid pick and refine, confirm taps pass through the overlay.
