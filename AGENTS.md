# AGENTS.md

Instructions for every AI coding agent working in this repository (Claude Code,
Codex, Cursor, Gemini CLI, Copilot). Keep this file under 150 lines. Detail lives
in `docs/sdlc/` and `.claude/rules/`.

## What this project is

Pin is an open-source Android app that controls the phone by voice. An
AccessibilityService executes actions; on-device speech (sherpa-onnx) handles the
wake word and transcription; simple commands run locally; complex ones go to an
LLM planner from a provider the user picks (Claude, OpenAI, Gemini, OpenRouter)
with their own key. Every session, step, screenshot and reasoning summary is kept
locally for 7 days by default.

Architecture: `docs/architecture/blueprint.md`. Decisions: `docs/adr/`.
Roadmap and task ids: `docs/roadmap.md`.

## Commands

| Command | What it does |
| --- | --- |
| `make check` | The gate: ktlint, Android lint, unit tests, debug build. CI runs exactly this. |
| `make test` | Unit tests only |
| `make fix` | Apply ktlint formatting |
| `make dev` | Install the debug build on the attached device |
| `make test-device` | Instrumented tests on the attached device (not part of `check`) |
| `make size` | Print the release APK size against the budget |

Run `make check` before saying any change is done.

## How work flows

Every change follows `docs/sdlc/README.md`:

1. **Spec** `docs/specs/<ID>-<slug>/spec.md`: what and why, acceptance criteria. Human approves.
2. **Plan** `plan.md` in the same folder: how, files touched, risks, test strategy. Human approves.
3. **Tasks** `tasks.md`: small ordered steps, each with its test.
4. **Build**: test first, then code, one task at a time, `make check` green after each.
5. **Verify** `verify.md`: each acceptance criterion with its evidence (command output, test name, device result).
6. **Review**: self-review against `docs/sdlc/review-checklist.md`, then a pull request for the human.

Small fixes (one file, no behaviour change to a public contract) may skip spec and plan
but still need a test and a green `make check`.

Task ids are `PIN-<n>` from `docs/roadmap.md`. Branch: `<type>/PIN-<n>-<slug>` with type
`feat`, `fix`, `chore`, `docs`, `spike`. Commits are Conventional with the id at the end:
`feat(voice): add VAD gate [PIN-12]`.

## Ground rules

1. Never commit to `main`. Never push, merge, tag or release; print the command for the human.
2. Never commit or read a secret, API key, keystore or `.env`. Test keys are fakes.
3. Never rewrite history (no amend, rebase, force push, `reset --hard`).
4. Never weaken a check to go green: no suppressions, no skipped tests, no lowered thresholds.
5. Never add a dependency without an ADR or a line in the plan saying why and its APK cost.
6. Never invent an Android API, field or flag. Check the docs or the code first.
7. Stay in scope. Things you notice go under "Noticed" in the report.
8. A check you did not run is "not run", never "passing".
9. No em dashes in any prose. No AI attribution in commits, pull requests or docs.

## Project rules that agents get wrong

- The device is touched only through the action registry and the `ActionOrchestrator`.
  The planner never produces code, raw coordinates outside the window, or intents.
- No inference, file IO or network on the main thread. The AccessibilityService
  callbacks do bookkeeping only.
- One owner of the microphone: `ListeningService`. Engines receive frames.
- No vendor LLM SDKs. Provider adapters use OkHttp and kotlinx.serialization.
- API keys live only in `SecretStore`. Never log them, never put them in history.
- Never type into password fields; never store their contents.
- Models are downloaded modules, never bundled in the APK. Native code stays in the APK.
- Views and Material Components for UI. No Compose, Hilt or Retrofit (ADR-0002).
- Every agent step writes its history record before the action runs.
- Versions live only in `gradle/libs.versions.toml`.

## Definition of done

- Acceptance criteria in the spec each have evidence in `verify.md`.
- `make check` passes; output tail pasted in the report.
- New behaviour has unit tests; device-only behaviour has an instrumented test or a
  written manual check with the device and Android version.
- Performance budgets touched by the change are measured (`docs/sdlc/budgets.md`).
- Docs and ADRs updated when a decision or contract changed.

## Report format

End every task with: `## Changed` (path: what and why), `## Verified` (command and
output tail), `## Not done` (asked, not delivered, why), `## Noticed` (seen, not fixed).
