# PIN-30 verification

Commit: `feat/PIN-30-command-grammar` (this file's commit)
Device: Nothing A059, Android 17 (API 37), debug build installed with adb.
Gate: `make check` passed (250 unit tests), output tail:

```
check: passed (ktlint, lint, test, build)
```

Two device paths were used:
- `scripts/pin-debug "hear <text>"` feeds a final transcript straight into the router.
- `scripts/pin-debug "say <text>"` speaks through the phone's speaker with listening on
  (partials, early commit and the real recognizer).

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | `hear`: "Pin scroll down" and "Pin, scroll up" scrolled Settings; "Pin tap Network and internet" opened the subpage; "Pin go back", "Pin go home" navigated; each a `success` task with 2 steps (tasks 103 to 110). | pass |
| AC-2 | "Pin make me a sandwich": task 106 `unmatched`, phone untouched, overlay "Not a command". "scroll down" without the prefix: task 105 `heard`, nothing moved. | pass |
| AC-3 | `CommandCorpusTest`: 211 lines (34 conversation lines), at least 95 % mapped right, no conversation line mapped to a command. | pass |
| AC-4 | "Pin open YouTube" opened `com.google.android.youtube`; "Pin open door" was `unmatched` (task 111). `AppCatalogTest` (4 cases). | pass |
| AC-5 | Speaker run, 10 x "Pin scroll down" in Settings: the 5 recognised runs each dispatched once from two equal partials; the finals were skipped. `IntentRouterTest` (9 cases). | pass |
| AC-6 | A pending question (`ask Send the message?`) then `hear Pin stop`: answer `stopped`. | pass |
| AC-7 | Speaker run: dispatch came 275 to 335 ms before the segmenter's end of speech (5 of 5 runs), so speech end to dispatch is below 0 ms. | pass |

## Speaker run (recognizer output for "Pin scroll down")

| Heard | Count | Route |
| --- | --- | --- |
| pin scroll down | 5 | ran |
| pins grown down | 3 | unmatched |
| pins roll down | 1 | unmatched |
| scroll down (prefix lost) | 1 | heard |

## Maintainer voice run (2026-11-14)

History of the maintainer's own speech: "pin open YouTube" ran (task 126); "tab search"
was unmatched (tasks 127, 128); "Pin" then "scroll down" ran twice through arming
(tasks 129, 130); "pin stop" ran (task 131).

Defects found and fixed:
- "scroll down" in WhatsApp switched to the next tab. The largest scrollable was the tab
  `ViewPager`; it offers no "scroll down", and the code fell back to "scroll forward",
  which on a pager means the next page. Now a container is used only when it moves in
  the asked direction (`ScrollChoiceTest`, 6 cases), trying scrollables largest first.
  Device: in WhatsApp "Pin scroll down" scrolled the chat list (rows changed, filter bar
  scrolled away) and the pager stayed on Chats; Settings scroll down and up still work.
- The recognizer wrote "tap" as "tab"; "tab" is now accepted (corpus lines added).

## Budgets

| Budget | Target | Measured | How |
| --- | --- | --- | --- |
| Speech end to local action | < 500 ms p95 | dispatch 275 to 335 ms before speech end (n = 5) | `PinSpeech` "end at" and `PinCommand` "dispatch at", both `elapsedRealtime` |
| Grammar parse time | record | not measured separately; whole route plus dispatch fits in the early-commit window | |

## Not verified

- More human-voice runs: the maintainer's first run worked for open, scroll and stop; how
  often the prefix is lost for a person is not measured yet.
- p95 over 20 trials; only 5 runs reached dispatch.
- "type <text>", "double tap", "long press", "show grid" and "tap number" on device; they
  are covered by unit tests and reuse the debug-tested actions.
