# PIN-31: Fuzzy target resolution and numbers fallback

Status: Draft
Owner: maintainer
Roadmap phase: 3
Blueprint sections: 6.1 (overlay), 6.3 (target resolution)

## Problem

"Pin, tap search" works only when a visible node is labelled exactly "search". Real
labels are longer ("Search YouTube", "Search settings"), the recognizer adds or drops
words, and content descriptions carry extra state ("All filter, , selected"). Today any
of these ends as "Nothing labelled ... on screen" and the user has no way forward except
"show numbers" by hand.

## Users and scenarios

- As a user, I say "Pin, tap search" on a screen whose button reads "Search YouTube" and
  it is tapped.
- As a user, I say "Pin, tap settings" when two things could be meant, and Pin shows
  numbers on the candidates and asks me to say one; "Pin, three" taps it.
- As a user, I say "Pin, tap nothing like this" and Pin says it found nothing, without
  tapping anything.

## Scope

- In:
  - Resolution order over visible actionable nodes (clickable, long-clickable, editable,
    checkable, or with a clickable ancestor as `NodeLocator` already allows):
    1. exact normalised label;
    2. the spoken words all present in the label, in order, as whole words
       ("search" in "Search YouTube"; "network" in "Network and internet");
    3. token overlap: every spoken word matches a label word within a bounded edit
       distance (1 for words up to 4 letters, 2 above), to absorb recognizer slips
       ("notifcations");
    No match by substring inside a word (the "Voice search" lesson from PIN-10 still
    holds: "search" must be a whole word, and a shorter label wins over a longer one).
  - One best candidate runs. Two or more candidates at the same tier with no single
    shortest label is a tie: Pin shows numbers on just those candidates, says "Say a
    number" on the overlay, and records the task as `ambiguous`.
  - A bare number after the prefix ("Pin, three", "Pin, 3") taps that number while
    numbers are shown, for 15 s after they appeared.
  - No candidate: overlay "Nothing like '<label>' on screen", task `failed`, nothing
    tapped (as today).
  - The same resolver serves long press, double tap and the debug `tap <label>`.
- Out: resolving by role or position ("the second button"), learning from corrections
  (Phase 7 memory), fuzzy app names for "open" (separate task if needed), the LLM route.

## Acceptance criteria

- AC-1: WHEN the spoken label matches exactly one visible actionable node by tier 1, 2 or
  3 THE app SHALL act on it and record the tier in the task summary.
- AC-2: WHEN two or more nodes tie THE app SHALL show numbers on those nodes only, show
  "Say a number", record the task as `ambiguous`, and tap nothing.
- AC-3: WHEN numbers are shown and the user says a bare number within 15 s THE app
  SHALL tap that node; after 15 s or after any other command the numbers are cleared.
- AC-4: WHEN nothing matches THE app SHALL tap nothing and show the not-found text.
- AC-5: THE resolver SHALL pass a fixture test of at least 40 cases built from real
  snapshots (YouTube, Settings, WhatsApp labels), including the "Voice search" case.
- AC-6: Resolution over a 200-node snapshot SHALL take under 5 ms (unit-test timing).

## Budgets touched

| Budget | Target |
| --- | --- |
| Speech end to local action | < 500 ms p95 (resolution adds under 5 ms) |

## Privacy and safety

Reads only the current snapshot, already held in memory. Labels of candidates are not
logged. Password fields are never candidates. Every action still passes the orchestrator
and the sensitivity gate.

## Open questions

- Q1: should a tie prefer the node nearest the top of the screen instead of asking?
  Proposed: no, ask with numbers; guessing is what PIN-10 found dangerous.
