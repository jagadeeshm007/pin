# PIN-30 plan

Spec: ./spec.md

## Design

New package `io.github.jagadeeshm007.pin.command`:

| File | Responsibility | Test |
| --- | --- | --- |
| `Command.kt` | Sealed `Command`: `Act(DeviceAction)`, `OnLabel(kind, label)`, `TypeText(text)`, `OpenApp(name)`, `ShowNumbers`, `HideNumbers`, `ShowGrid`, `TapNumber(n)`, `Stop`. `earlyCommit` is true only for commands without a free-text argument. | corpus |
| `CommandGrammar.kt` | Pure parser: normalise (lowercase, punctuation, fillers, number words), then whole-string patterns. | `CommandGrammarTest`, `CommandCorpusTest` (`src/test/resources/command-corpus.tsv`, 150+ lines incl. conversation) |
| `CommandPrefix.kt` | Returns the text after a spoken "Pin" (accepted spellings), "" when the utterance is only "Pin", else null. Replaced by the wake word in PIN-22. | `CommandPrefixTest` |
| `IntentRouter.kt` | Per-utterance state machine: early commit on two equal partials, dedupe of the final, "Pin" alone arms the next utterance for 5 s. | `IntentRouterTest` |
| `AppCatalog.kt` | Launcher apps (`queryIntentActivities` MAIN/LAUNCHER, allowed by `<queries>`), loaded off the main thread, cached, refreshed on a miss. Pure normalised exact label match. | `AppCatalogTest` |
| `LabelResolver.kt` | Exact visible label to node (moved from `DebugCommandReceiver.resolve`). | existing behaviour, device |
| `CommandRunner.kt` | Runs a `Command` in the accessibility service through `ActionOrchestrator` (wired as in the debug receiver), records route `local`, drives overlay and notification, stops on `TaskControl`. | device |

Existing code:
- `SpeechBridge`: Partial and Final go through `IntentRouter`; results go to `CommandRunner`,
  the overlay ("Not a command") or history ("heard", as today).
- `ListeningService`: log the segmenter end in `elapsedRealtime` for the timing budget.
- `DebugCommandReceiver`: uses `LabelResolver`; new `hear <text>` feeds a final transcript.

No new dependency, schema or permission.

## Risks

- First-word clipping drops the prefix (measured, spec Q2). Mitigated by lenient
  spellings and arming; re-measured with a human voice.
- Early commit could act on a prefix of a longer command; limited to argument-free
  commands that need two equal partials.

## Test strategy

Unit tests for every pure part; device run per `verify.md` with the speaker and `hear`.
