# PIN-13 verification

Commit: 63294aa (plus this file)
Device: Nothing A059, Android 17 (API 37), debug build. Notification permission granted; `POST_PROMOTED_NOTIFICATIONS` granted.
Gate: `make check` passed, output tail:

```
test: 160 tests passed
check: passed (ktlint, lint, test, build)
```

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | `NotificationContentMapperTest` (10 cases); device: `notify step 3 6 Tap Search` posted text "Tap Search", chip "Step 3/6", ProgressStyle with 6 segments and progress 2 (`dumpsys notification`, screenshots of chip and shade reviewed) | pass |
| AC-2 | Device: flags `ONGOING_EVENT|ONLY_ALERT_ONCE|PROMOTED_ONGOING`, `android.requestPromotedOngoing=true`, `canPostPromotedNotifications()` true, status bar chip shown. Android 16.0 path uses `EXTRA_REQUEST_PROMOTED_ONGOING`; legacy path (API 30 to 35) not run on a device | pass on 17; legacy not run |
| AC-3 | `ThrottledPosterTest` (5 cases) | pass |
| AC-4 | `ConfirmationBrokerTest` (6 cases); device from the notification: Confirm gave "confirmed", Deny gave "denied", silence for 15 s gave "denied" | pass |
| AC-5 | `TaskControlTest`; device: Stop on the notification (opened by tapping the "Confirm?" chip) cancelled a pending question ("stopped"); receiver logged `notification action STOP` | pass |
| AC-6 | Intents are explicit (`NotificationActionReceiver` class), `FLAG_IMMUTABLE`; receiver `exported=false` in the manifest | pass by inspection |
| AC-7 | Device: card "Pin needs your OK" stayed visible 8 s and more (screenshot reviewed); Stop, Confirm and Deny on the card gave "stopped", "confirmed", "denied"; card window removed after each answer (0 "Pin confirmation" windows) | pass; "touches outside reach the app" by design (`FLAG_NOT_TOUCH_MODAL`), not separately tested |

## Bugs found on the device and fixed

1. `setRequestPromotedOngoing` needs API 36.1; lint caught it. 16.1+ calls the method,
   16.0 sets the documented extra.
2. The notifier was created lazily, so in a fresh process no one watched the
   confirmation broker and questions never reached the notification. It is now
   created in `PinApp.onCreate`.
3. Later questions did not pop up: the channel is default importance, and Android shows
   pop-up cards only for high importance (and closes them after a few seconds). At the
   maintainer's request, questions now also show as a persistent card in Pin's overlay
   until answered (AC-7). Each question still sets `onlyAlertOnce=false`.

## Test harness notes

- Broadcasts to the debug receiver run one at a time, so a second command waits for
  the first. Stop is therefore tested on a pending `ask` question, which returns at once.
- Three test taps at fixed coordinates missed after the screen changed and opened
  WhatsApp once and Clock twice. Nothing was changed in those apps. Taps by label or on
  the persistent card avoid this.

## Not verified

- Legacy notification on Android 11 to 15 (no device).
- Main-thread cost of notification updates.

## Noticed

- Tapping the status bar chip opens the notification with its buttons (maintainer's
  suggestion); a good path to keep in the onboarding tips.
- Channel importance cannot change after creation; if pop-ups are wanted later, a new
  high-importance channel is needed.
