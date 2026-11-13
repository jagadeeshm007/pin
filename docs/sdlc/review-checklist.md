# Review checklist

The agent runs this against its own diff before preparing a pull request, and
writes each failed item into the report.

## Scope and traceability

- [ ] Every changed file maps to a task in `tasks.md`; nothing else changed.
- [ ] Every acceptance criterion has evidence in `verify.md`.

## Correctness

- [ ] Errors are handled once, with context; `CancellationException` is rethrown.
- [ ] Cancellation (`stop`) reaches every new coroutine, gesture and network call.
- [ ] No work on the main thread beyond UI and accessibility bookkeeping.
- [ ] Android version differences are guarded (`Build.VERSION.SDK_INT`) and tested on the lowest supported version when they matter.

## Device control and safety

- [ ] New device actions go through the action registry and orchestrator, with verification.
- [ ] Sensitive actions (pay, send, delete, security settings) pass the confirmation gate.
- [ ] Nothing types into or stores password fields.

## Privacy and secrets

- [ ] No key, token or transcript in logs.
- [ ] New stored data has a retention rule and appears in the history viewer or is documented as not shown.
- [ ] Nothing new leaves the device unless the spec says so.

## Performance and size

- [ ] Budgets in `budgets.md` touched by the change were measured.
- [ ] No new dependency without an ADR or plan line; APK delta recorded.

## Tests

- [ ] Tests fail without the change (checked once by breaking the code).
- [ ] No sleeps, no real network in unit tests; provider calls use recorded responses.

## Docs

- [ ] ADR added or superseded if a decision changed.
- [ ] README or blueprint updated if user-visible behaviour or architecture changed.
- [ ] No em dashes; no AI attribution.
