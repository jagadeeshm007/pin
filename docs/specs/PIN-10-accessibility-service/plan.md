# PIN-10 plan

Status: Approved (maintainer instruction "start building", 2026-11-13)
Spec: ./spec.md

## Approach

Keep Android code thin and push logic into pure, unit-tested classes.

| Pure (JVM tests) | Android adapter |
| --- | --- |
| `RawNode` interface + `SnapshotBuilder` (pruning, labels, flags, ids, cap) | `AccessibilityRawNode` wraps `AccessibilityNodeInfo` |
| `NodeLocator` (find the live node matching a snapshot node by role and bounds) | uses a fresh root at perform time, so no `AccessibilityNodeInfo` outlives a call |
| `GesturePlanner` (strokes for tap, double tap, long press, swipe, pinch, scroll fallback) | `GestureDispatcher` turns strokes into `GestureDescription` and awaits the callback |
| `scrollActionFor(direction)` | `AccessibilityDeviceController` |
| | `AccessibilityScreenSource` (version counter as `StateFlow`, snapshots on `Dispatchers.Default`) |
| | `PinAccessibilityService`, `AccessibilityRegistry` |
| | `DebugCommandReceiver` in `src/debug`, protected by `android.permission.DUMP` |

## Changes

| Path | Change |
| --- | --- |
| `res/xml/accessibility_service_config.xml` | events, `canRetrieveWindowContent`, `canPerformGestures`, `isAccessibilityTool`, interactive windows |
| `AndroidManifest.xml` | service with `BIND_ACCESSIBILITY_SERVICE`, `<queries>` for launcher intents |
| `accessibility/*` | the classes above |
| `ui/MainActivity` + layout | service status and settings button |
| `src/debug/...` | receiver, debug manifest |

## Dependencies

None new.

## Risks

| Risk | Mitigation |
| --- | --- |
| Node not found at perform time (screen moved) | Return false so the orchestrator falls back to a gesture at the snapshot bounds |
| Tap on a non-clickable label | Walk up to the nearest clickable ancestor |
| OEM restrictions on gestures | Measured on the Nothing A059 now, more devices in PIN-4 |

## Test strategy

- Unit: `SnapshotBuilderTest`, `NodeLocatorTest`, `GesturePlannerTest`.
- Device (manual, scripted with `adb`): Nothing A059, Android 17. Commands in `verify.md`.
