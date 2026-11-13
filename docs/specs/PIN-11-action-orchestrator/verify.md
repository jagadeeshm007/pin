# PIN-11 verification

Commit: 39b4142
Gate: `make check` passed, output tail:

```
test: 72 tests passed
build: 15M app/build/outputs/apk/debug/app-debug.apk
check: passed (ktlint, lint, test, build)
```

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | `ActionValidatorTest.missingNodeRejected`, `offscreenNodeRejected`; `ActionOrchestratorTest.invalidActionNeverReachesTheDevice` | pass |
| AC-2 | `ActionValidatorTest.passwordFieldNeverTyped`; `ActionOrchestratorTest.invalidActionNeverReachesTheDevice` | pass |
| AC-3 | `ActionValidatorTest.pointOutsideWindowRejected`, `swipeDistanceOutOfRangeRejected` | pass |
| AC-4 | `SensitivityPolicyTest` (29 cases); `ActionOrchestratorTest.declinedSensitiveActionNeverReachesTheDevice`, `confirmedSensitiveActionRunsAfterConfirmation` | pass |
| AC-5 | `ActionOrchestratorTest.primaryStrategySucceeds` asserts `record:AttemptStarted` precedes the device call | pass |
| AC-6 | `ActionOrchestratorTest.fallsBackWhenPrimaryIsRefused`, `fallsBackWhenPrimaryDoesNotVerify`, `reportsFailureWithReasonsWhenAllStrategiesFail`; `ActionVerifierTest` | pass |
| AC-7 | `ActionOrchestratorTest.primaryStrategySucceeds` checks strategy and after-screen | pass |
| AC-8 | `ActionOrchestratorTest.cancellationStopsFurtherAttempts` | pass |
| AC-9 | `ActionOrchestratorTest.actionsRunOneAtATime` | pass |

Mutation check: disabling the confirmation gate in `ActionOrchestrator` made
`declinedSensitiveActionNeverReachesTheDevice` and
`confirmedSensitiveActionRunsAfterConfirmation` fail; restored afterwards.

## Budgets

None touched (no device code).

## Not verified

- Behaviour on a real device: needs the AccessibilityService controller (PIN-10).
