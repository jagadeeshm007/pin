# PIN-31 verification

Commit: `feat/PIN-31-fuzzy-targets` (this file's commit)
Device: Nothing A059, Android 17 (API 37), debug build installed with adb.
Gate: `make check` passed, output tail:

```
check: passed (ktlint, lint, test, build)
```

Device checks used `scripts/pin-debug "hear <text>"` (a final transcript fed to the
router) and the debug `tap <label>`.

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | Settings top page: "Pin tap network" opened "Network and internet" (tier 2, label child of a clickable row); "Pin tap notifcations" (misspelt) opened "Notifications" (tier 3). YouTube home: "Pin tap search" opened the search page; no recorder was active afterwards, so Voice search was not tapped. Tasks 149, 150, 153, each `success` with 2 steps. | pass |
| AC-2 | WhatsApp Chats: "Pin tap filter" fits 7 chips; task 155 `ambiguous`, nothing tapped, numbers 1 to 7 drawn on the chips only (screenshot checked), overlay "Say a number". | pass |
| AC-3 | "Pin two" 2 s later tapped chip 2: "Unread filter, , selected" (task 156, success). "Pin two" again 17 s after the numbers appeared: task 157 `failed`, "No numbers shown". | pass |
| AC-4 | "Pin tap zzz" in Settings: task 151 `failed`, title still Settings, overlay "Nothing like 'zzz' on screen". | pass |
| AC-5 | `LabelFixtureTest`: 63 cases from recorded YouTube, Settings and WhatsApp labels, all pass, including "search" on YouTube's search page (Search YouTube, Voice search, Clear search) resolving to a tie, never to Voice search. `LabelResolverTest` 12 cases. | pass |
| AC-6 | `LabelResolverTest.twoHundredNodesUnderFiveMilliseconds`: asserted under 5 ms per call on the CI JVM. | pass |

## Rule change found in the build

The spec first said the shortest label wins a tier. On the real labels that picked "All
filter" out of seven chips and "Voice search" over "Search YouTube", the guesses the
rule was meant to avoid. The tiebreak is now the share of the label the spoken words
cover; an equal share asks. Spec and plan were updated before T1 was committed.

## Maintainer voice run (2026-11-14) and fixes

"click story" (Instagram) showed numbers and asked; the answer "click one" was recorded as
plain speech (task 240 `heard`) and the numbers stayed. Two causes, both fixed on this
branch:
- The answer still needed the "Pin" prefix. After an `ambiguous` outcome the router is now
  armed for the 15 s window, so the next utterance counts as the answer
  (`IntentRouterTest.anAskedQuestionTakesTheNextUtteranceWithoutThePrefix`).
- "click one" with a number word parsed as a label tap; "tap", "tab", "click" or "press"
  plus a number word is now `TapNumber` (grammar test and corpus lines).
- Speech that is not an answer ("is it raining") now clears the numbers as the spec says.

Device, WhatsApp chips: "Pin tap filter" then "click one" selected All (task 253); again
then "two" selected Unread (255); again then "three" selected Favourites (262); again then
"is it raining": `tapnum 99` went from "number 99 was not shown" to "no numbers shown".

## Budgets

| Budget | Target | Measured | How |
| --- | --- | --- | --- |
| Resolution time, 200 nodes | < 5 ms | under 5 ms (asserted) | unit test timing |

## Not verified

- Earlier in the same run four taps failed because Settings was scrolled and YouTube was
  on a video page, so the labels were not on screen; the resolver was right. A spoken
  label for something off screen still fails rather than scrolling to find it.
- The maintainer's own voice through the fixed flow ("click story", then "one").
- Long press and double tap through the fuzzy resolver on device (same code path as tap).
