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

## Budgets

| Budget | Target | Measured | How |
| --- | --- | --- | --- |
| Speech end to local action | < 500 ms p95 | dispatch 275 to 335 ms before speech end (n = 5) | `PinSpeech` "end at" and `PinCommand` "dispatch at", both `elapsedRealtime` |
| Grammar parse time | record | not measured separately; whole route plus dispatch fits in the early-commit window | |

## Not verified

- A human voice. The phone's own speaker gave 5 of 10 recognised commands; the misses are
  the recognizer merging "Pin scroll" into "pins grown". Whether a person sees the same
  needs a run by the maintainer.
- p95 over 20 trials; only 5 runs reached dispatch.
- "type <text>", "double tap", "long press", "show grid" and "tap number" on device; they
  are covered by unit tests and reuse the debug-tested actions.
