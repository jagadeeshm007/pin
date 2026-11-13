# PIN-13: Task notification (Live Update) and notification confirmations

Status: Approved (maintainer instruction "start building", 2026-11-13; open to revision)
Owner: maintainer
Roadmap phase: 1
Blueprint sections: 7.8 (Live Updates), 7.6 (confirmation), 11 (visible indicator)
Depends on: PIN-11

## Problem

The user needs to follow a task without opening Pin, from the lock screen or the
status bar, and to stop it or answer a confirmation from there. On Android 16+ this
is a Live Update; older versions get a standard progress notification.

## Scope

- In: notification channel; mapping from task status to title, chip text, progress
  and actions; Live Update (`ProgressStyle`, promoted ongoing, short critical text)
  with a legacy fallback; update throttling (at most one per 500 ms, urgent states
  immediately); Stop, Confirm and Deny actions; a confirmation broker that backs a
  `Confirmer`, with a timeout that defaults to deny; notification permission request
  in the main screen; debug commands.
- In (added): a persistent confirmation card in the accessibility overlay.
- Out: owning the notification from the microphone foreground service (PIN-21),
  voice confirmation (PIN-30).

## Acceptance criteria

- AC-1: WHEN the status changes THE notification SHALL show the mapped title, chip and progress (steps as segments, indeterminate while planning).
- AC-2: WHEN running on Android 16+ with promotion allowed THE notification SHALL request promotion and use `ProgressStyle`; otherwise it SHALL fall back to a standard progress notification with the same text and actions.
- AC-3: WHEN updates arrive faster than every 500 ms THE notifier SHALL post at most one per 500 ms, always ending with the latest status; CONFIRM, DONE and FAILED SHALL post immediately.
- AC-4: WHEN a sensitive action needs confirmation THE notification SHALL show Confirm and Deny; THE answer SHALL resolve the pending request; no answer within the timeout SHALL count as deny.
- AC-5: WHEN Stop is tapped THE stop signal SHALL be emitted.
- Amended 2026-11-14 (PIN-21): WHILE a voice session runs THE notification SHALL also carry Stop listening and stay ongoing after a task ends; Confirm, Deny and Stop stay the three actions during a question.
- AC-6: Action intents SHALL be explicit, immutable and delivered to a non-exported receiver.
- AC-7 (added 2026-11-13, maintainer request): WHILE a confirmation is pending THE app SHALL show a card in its own overlay with the question and Confirm, Deny and Stop, which stays until answered, stopped or timed out; touches outside the card SHALL reach the app underneath. Reason: system pop-ups close after a few seconds and only appear for high-importance channels.

## Budgets touched

None.

## Privacy and safety

The notification shows the task text and target labels on the lock screen.
Visibility is set to private so the lock screen shows a redacted version by default.

## Open questions

- Q1: Should the lock screen show full task text? Assumed no (private visibility).
