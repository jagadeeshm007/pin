# PIN-30: Command grammar and intent router with early commit

Status: Approved (maintainer, plan approval 2026-11-14)
Owner: maintainer
Roadmap phase: 3
Blueprint sections: 2.2 (IntentRouter, LocalCommandEngine), 6.2, 9.1, 9.3

## Problem

Pin now hears and transcribes (PIN-21) but does nothing with the text: every final
transcript is only recorded as a "heard" task. The user wants to say "scroll down" or
"open YouTube" and have the phone do it, offline and fast, without an LLM.

## Users and scenarios

- As a user with listening on, I say "scroll down", "go back" or "open YouTube" and
  the phone does it within half a second of my finishing.
- As a user, I say "tap Search" on a screen with a Search button and it is tapped.
- As a user, I say "stop" while something runs and it stops.
- As a user, I say something that is not a command and nothing happens on the phone;
  the overlay tells me it was not understood.

## Scope

- In:
  - Spoken prefix: until the wake word works (PIN-22) an utterance is a command only
    when it starts with "Pin" ("Pin, scroll down"). Everything else is ignored as
    conversation (maintainer decision Q1, 2026-11-14). The prefix check is one
    function so PIN-22 can replace it.
  - A grammar that parses the rest of the utterance into one registry action
    (`DeviceAction`) or a control command. The whole remainder must be the command,
    after removing fillers ("please", "can you", "the"). No substring matches.
  - Commands:
    - scroll up / down / left / right; page up / down
    - swipe up / down / left / right
    - go back, back; go home, home; recent apps; notifications; quick settings
    - open <app name>: matched against launcher app labels on the phone
      (case and punctuation insensitive, exact after normalising)
    - tap / click / press <label>: exact label of a visible node (the existing exact
      `NodeLocator` rule; fuzzy matching is PIN-31)
    - long press <label>, double tap <label>
    - type <text>: into the focused editable field
    - zoom in / zoom out (pinch at the window centre)
    - show numbers, hide numbers, show grid, tap number <n>
    - stop, cancel: stop the running task (the existing `TaskControl` signal)
  - IntentRouter in the main process: final transcript to grammar; a match runs
    through `ActionOrchestrator` and history like any other action; no match shows
    "Not a command" on the overlay and records the task as unmatched.
  - Early commit: for commands without a free-text argument (scroll, swipe, back,
    home, recents, notifications, quick settings, zoom, stop), a partial that matches
    and repeats unchanged in two consecutive partials runs at once; the final of that
    utterance is then ignored.
  - A command corpus test (utterance to expected action) of at least 150 lines.
- Out: fuzzy targets and numbers fallback on no match (PIN-31), LLM routing of
  unmatched commands (PIN-40+), wake word (PIN-22), TTS and earcons (PIN-24),
  volume (not in the action registry yet), languages other than English.

## Acceptance criteria

- AC-1: WHEN a final transcript is a grammar command THE app SHALL run the matching
  action through the orchestrator and record it in history as a task with route
  "local".
- AC-2: WHEN a transcript starts with "Pin" but the rest is not a command THE app SHALL
  not touch the phone, SHALL show "Not a command" on the overlay and SHALL record the
  task as unmatched. WHEN it does not start with "Pin" THE app SHALL not touch the
  phone and SHALL record it as heard, as today.
- AC-3: THE grammar SHALL map at least 95 % of the corpus correctly and SHALL map no
  corpus non-command (conversation lines) to an action.
- AC-4: WHEN "open <app>" names a launcher app label THE app SHALL open it; WHEN no
  label matches THE app SHALL treat it as AC-2.
- AC-5: WHEN a prefixed argument-free command is stable in two partials THE app SHALL run it
  before the final arrives and SHALL not run it a second time for the same utterance.
- AC-6: WHEN "stop" or "cancel" is heard while a task runs THE task SHALL stop.
- AC-7: Speech end to action dispatched SHALL be under 500 ms p95 on the A059 for
  early-commit commands (budget row "Speech end to local action").

## Budgets touched

| Budget | Target |
| --- | --- |
| Speech end to local action | < 500 ms p95 |
| Grammar parse time | record (expected well under 5 ms) |

## Privacy and safety

- Reads transcripts (already in history) and launcher app labels (already allowed by
  the manifest `<queries>`). Nothing is sent anywhere.
- Every action still passes the orchestrator's validation and sensitivity gate, so
  "tap Pay" asks for confirmation as today.
- Without a wake word, speech from a TV or nearby people could match a command; the
  "Pin" prefix and the whole-utterance rule make that unlikely until PIN-22.

## Open questions

- Q1 (answered 2026-11-14): require the spoken prefix "Pin" until PIN-22.
- Q2: how the recognizer spells "Pin". First device run (speaker into mic): kept as
  "pin" 3 times, once as "pins", dropped 3 times, and 3 utterances were missed. So the
  prefix accepts close spellings (pin, pins, pen, bin, ping) and "Pin" said alone arms
  the next utterance for 5 s. To be re-measured with a human voice.
