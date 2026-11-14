# PIN-31 plan

Spec: ./spec.md

## Design

| File | Change | Test |
| --- | --- | --- |
| `command/LabelResolver.kt` | `resolve(screen, spoken, editable)` returns `One(node, tier)`, `Many(nodes)` or `None`. Candidates: labelled nodes that are actionable or a labelled child of an actionable parent; never password fields. Tier 1 exact normalised label; tier 2 spoken words as whole words in order in the label; tier 3 every spoken word within a bounded edit distance of a label word. Inside a tier the label with the fewest words (the one the spoken words cover most) wins; an equal share is `Many`. | `LabelResolverTest`, `LabelFixtureTest` over `label-fixtures.tsv` (40+ real-label cases), timing on 200 nodes |
| `command/EditDistance.kt` | Bounded Levenshtein with early exit. | `LabelResolverTest` |
| `command/Command.kt`, `CommandGrammar.kt` | `PickNumber(n)` for a bare number after the prefix. | corpus |
| `overlay/NumberLabeler.kt` | `labelNodes(nodes)` shared by `label(screen)`. | existing tests |
| `command/CommandRunner.kt` | `Many` shows numbers on the candidates only, overlay "Say a number", task `ambiguous`; `PickNumber` taps within 15 s; other commands clear the numbers. | device |
| `debug/DebugCommandReceiver.kt` | `tap <label>` uses the resolver. | device |

No new dependency, schema or permission.

## Risks

- Tier 3 could match a wrong short word; bounded by distance 1 for words of 4 letters
  or fewer and by the shortest-label rule, and the fixture test pins the known traps.
- Numbers shown on candidates reuse the existing overlay, so no new drawing code.

## Test strategy

Unit tests for the resolver and grammar; fixture file from recorded YouTube, Settings and
WhatsApp labels; device run per `verify.md`.
