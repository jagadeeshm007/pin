# PIN-13 plan

Status: Approved (maintainer instruction "start building", 2026-11-13)
Spec: ./spec.md

## Approach

| Pure (JVM tests) | Android |
| --- | --- |
| `TaskStatus`, `TaskPhase`, `NotificationContent`, `NotificationContentMapper` | `TaskNotifier`: channel, `Notification.Builder`, `ProgressStyle` on API 36+, legacy progress otherwise |
| `ThrottledPoster`: coroutine-based, 500 ms window, urgent bypass, last-write-wins | `NotificationActionReceiver` (not exported) |
| `ConfirmationBroker`: one pending request, token-checked answers, timeout means deny; `BrokerConfirmer : Confirmer` | `PinApp` creates notifier, broker and stop signal; `MainActivity` requests `POST_NOTIFICATIONS` |

Live Update requirements (developer.android.com, Android 16): `ProgressStyle`,
`setOngoing(true)`, `setRequestPromotedOngoing(true)`, a content title, the normal
permission `POST_PROMOTED_NOTIFICATIONS`, compile and target SDK 36. The system may
still decline promotion; `canPostPromotedNotifications()` is checked and the same
builder path then renders as a normal notification.

## Changes

| Path | Change |
| --- | --- |
| `notification/*` | the classes above |
| `AndroidManifest.xml` | two permissions, receiver |
| `res/drawable/ic_stat_pin.xml`, strings | icon, texts |
| `PinApp.kt`, `MainActivity.kt` | wiring, permission request |
| `src/debug/...` | `notify` commands; actions use `BrokerConfirmer` |

## Test strategy

- Unit: `NotificationContentMapperTest`, `ThrottledPosterTest`, `ConfirmationBrokerTest`.
- Device: each phase posted, chip visible in status bar, promotion state read, Confirm and Deny from the shade answer a pending confirmation, Stop emits.
