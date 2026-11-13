# PIN-11 plan: Action registry and orchestrator

Status: Approved (maintainer instruction "start building", 2026-11-13)
Spec: ./spec.md

## Approach

Pure Kotlin in `io.github.jagadeeshm007.pin.core.screen` and
`io.github.jagadeeshm007.pin.action`. No Android imports, so everything runs in
JVM unit tests.

```
execute(action)
  mutex.lock
  before = screen.current()
  validator.validate(action, before)      -> Rejected
  policy.assess(action, before)           -> confirmer.confirm(...) else Declined
  for strategy in strategiesFor(action):  (at most 2)
      recorder.record(Attempt)             (before the device call)
      ok = controller.perform(action, before, strategy)
      if !ok: note reason, continue
      after = screen.awaitChange(before, settleTimeoutMs)
      verifier.verify(action, before, after) -> Success(strategy, after)
      else: note reason, before = screen.current(), continue
  recorder.record(Finished); Failed(reasons)
```

## Changes

| Path | Change |
| --- | --- |
| `core/screen/ScreenModel.kt` | `Bounds`, `NodeFlag`, `ScreenNode`, `ScreenSnapshot` |
| `action/DeviceAction.kt` | Sealed action types, `Direction`, `GlobalNav`, `TargetRef`, `ExecutionStrategy`, `strategiesFor` |
| `action/ActionValidator.kt` | Rules for AC-1 to AC-3 |
| `action/SensitivityPolicy.kt` | Keyword and package rules for AC-4 |
| `action/ActionVerifier.kt` | Post-action checks per action type |
| `action/Ports.kt` | `DeviceController`, `ScreenSource`, `Confirmer`, `ActionRecorder` |
| `action/ActionOrchestrator.kt` | The flow above, `ActionResult` |
| tests | One test class per file plus fakes |

## Dependencies

None new. Uses kotlinx-coroutines (already present).

## Alternatives considered

- Validation inside each executor: rejected, the rules must be identical for voice and planner.
- Sensitivity in the planner prompt only: rejected by blueprint 7.6; prompts can be ignored.

## Risks

| Risk | Mitigation |
| --- | --- |
| Node ids differ between snapshots | Verification compares content version, window and package, not ids; typing checks any editable node's text |
| Keyword list misses a sensitive control | Package rules plus later user-editable list (Q1) |

## Test strategy

Unit tests only (JUnit Jupiter, coroutines-test) with fakes for the four ports.
Device behaviour is covered when PIN-10 adds the real controller.
