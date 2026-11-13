# PIN-11: Action registry and orchestrator

Status: Approved (maintainer instruction "start building", 2026-11-13; open to revision)
Owner: maintainer
Roadmap phase: 1
Blueprint sections: 6.2, 6.4, 7.6

## Problem

Every voice command and every planner step must change the phone through one
controlled path. Without it, commands could tap things that do not exist, type
into password fields, run sensitive actions unconfirmed, or report success
without checking. This task builds that path as platform-independent Kotlin; the
AccessibilityService that performs the actions on a device is PIN-10.

## Users and scenarios

- As a user, when I say "tap search", the app taps it and knows whether it worked.
- As a user, before the app presses "Pay" or "Delete", it asks me.
- As a user, saying "stop" halts the action immediately.

## Scope

- In: screen snapshot model, action types, validation, sensitivity policy,
  verification rules, the orchestrator (validate, gate, record, execute with
  fallback strategy, verify, cancel), interfaces for device control, screen
  source, confirmation and history recording, fakes and unit tests.
- Out: the AccessibilityService implementation (PIN-10), JSON tool schemas for
  the planner (PIN-40), the history database (PIN-14), screenshots, `find`,
  `wait`, overlay actions.

## Acceptance criteria

- AC-1: WHEN an action names a node that is not in the current snapshot THE orchestrator SHALL reject it without calling the device.
- AC-2: WHEN an action would type into a password field THE orchestrator SHALL reject it.
- AC-3: WHEN an action targets a point outside the window or uses an out-of-range swipe distance THE orchestrator SHALL reject it.
- AC-4: WHEN an action is classified sensitive THE orchestrator SHALL ask the confirmer first and SHALL NOT touch the device if the answer is no.
- AC-5: WHEN an action is attempted THE orchestrator SHALL record the attempt before the device is called.
- AC-6: WHEN the primary strategy fails or its result does not verify THE orchestrator SHALL try the fallback strategy once, and report failure with the reasons if both fail.
- AC-7: WHEN an action succeeds THE result SHALL include the strategy used and the screen after the action.
- AC-8: WHEN the calling coroutine is cancelled THE orchestrator SHALL stop without further attempts and propagate the cancellation.
- AC-9: WHEN two actions are submitted concurrently THE orchestrator SHALL run them one at a time.

## Budgets touched

None measured here (no device). Orchestrator overhead is pure computation over at
most 200 nodes.

## Privacy and safety

No data stored or sent. Password-field protection and the confirmation gate are
implemented here.

## Open questions

- Q1: Which apps count as sensitive by default? Proposed: Google Wallet, Google Pay, PhonePe, Paytm, plus security and accessibility screens in Settings; user-editable later.
