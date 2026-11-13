# PIN-12: Overlay (state pill, numbers, grid)

Status: Approved (maintainer instruction "start building", 2026-11-13; open to revision)
Owner: maintainer
Roadmap phase: 1
Blueprint sections: 2.2 (VoiceAccessibilityService overlay), 6.1 (numbers and grid fallback), 11 (visible listening indicator)
Depends on: PIN-10

## Problem

The user must always see what Pin is doing, and must be able to act on things that
have no usable label. Voice Access solves both with an on-screen indicator, number
badges and a grid. Pin needs the same, drawn by its accessibility service without
the "display over other apps" permission.

## Scope

- In: an accessibility overlay window that never takes touches; a state pill
  (listening, heard, thinking, acting, confirm, paused, error, idle hidden); number
  badges for actionable nodes with a number-to-node map; a 3 by 3 grid that refines
  into a smaller grid; debug commands to drive it.
- Out: voice input for numbers (PIN-30), animations, theming beyond day and night.

## Acceptance criteria

- AC-1: WHEN the overlay shows THE window SHALL be of type accessibility overlay and SHALL NOT receive touches or focus.
- AC-2: WHEN numbers are requested THE app SHALL label every visible actionable node with a unique number from 1, ordered top to bottom then left to right, at most 99, skipping nodes fully covered by a smaller labelled node.
- AC-3: WHEN a number is chosen THE app SHALL resolve it to the node it labelled in that snapshot, and SHALL reject numbers it did not show.
- AC-4: WHEN the grid is requested THE app SHALL split the window into 9 numbered cells; WHEN a cell is chosen it SHALL either tap its centre or refine into 9 smaller cells, down to a minimum cell size.
- AC-5: WHEN the state changes THE pill SHALL show the state's text within one frame, and SHALL be hidden in idle.
- AC-6: All overlay drawing SHALL happen on the main thread and none of it SHALL block the accessibility event handler.

## Budgets touched

Main thread per accessibility event (unchanged: overlay updates are separate).

## Privacy and safety

The overlay shows labels already on screen. It is the visible listening indicator
required by blueprint section 11 together with the notification (PIN-13).

## Open questions

- Q1: Pill position: top centre below the status bar, as assumed here.
