# Pin AI-SDLC

How humans and AI agents build Pin. It is spec-driven: intent is written down and
approved before code, and every claim of "done" is backed by evidence. It borrows
the spec, plan and tasks split used by GitHub Spec Kit and Kiro, keeps it to plain
Markdown and Make targets, and adds the checks this project needs (device testing,
performance budgets, privacy).

## Roles

| Role | Does | Approves |
| --- | --- | --- |
| Maintainer (human) | Sets priorities, owns the roadmap | Specs, plans, pull requests, releases |
| Agent | Drafts specs and plans, writes tests and code, gathers evidence | Nothing |
| Contributor (human) | Same as agent, plus device testing | Nothing on their own work |

The agent never pushes, merges, tags or releases. It prints the commands.

## Lifecycle

```
Roadmap item (PIN-n)
   │
   ▼
1 Spec ──── human approval gate ────┐
   │                                │ rejected: revise
   ▼                                │
2 Plan ──── human approval gate ────┘
   │
   ▼
3 Tasks (ordered, each with a test)
   │
   ▼
4 Build: for each task, test first, code, make check
   │
   ▼
5 Verify: every acceptance criterion has evidence
   │
   ▼
6 Review: self-review checklist, pull request, human review, CI green
   │
   ▼
7 Release (human): tag, signed APK, release notes
```

### 1. Spec (`/spec PIN-n`)

File: `docs/specs/PIN-n-<slug>/spec.md` from `templates/spec.md`.
Says what and why, never how. Contains the problem, users, scope and non-scope,
acceptance criteria written as testable statements (WHEN ... THE app SHALL ...),
budgets it touches and privacy impact. Open questions are listed, not guessed.
Gate: the maintainer approves by changing `Status: Draft` to `Status: Approved`.

### 2. Plan (`/plan PIN-n`)

File: `plan.md` from `templates/plan.md`. Says how: components and files touched,
data and contract changes, dependencies (with APK cost), alternatives considered,
risks, test strategy (unit, instrumented, manual device checks), rollout.
A new technology or a reversed decision gets an ADR in `docs/adr/`.
Gate: maintainer approval, same status line.

### 3. Tasks (`/tasks PIN-n`)

File: `tasks.md` from `templates/tasks.md`. Ordered checklist; each task is
small enough for one commit and names the test that proves it. `[P]` marks tasks
that can run in parallel. Each task traces to an acceptance criterion (`AC-n`).

### 4. Build (`/build PIN-n`)

One task at a time: write the failing test, make it pass, refactor, run
`make check`, commit with the task id, tick the box. A task that cannot be done
as planned stops the build; the plan is updated and re-approved if scope changes.

### 5. Verify (`/verify PIN-n`)

File: `verify.md` from `templates/verify.md`. One row per acceptance criterion:
evidence (test name, command output, device and Android version, measurement).
Anything not verified is listed as not verified with the reason.

### 6. Review (`/review PIN-n`)

The agent runs `review-checklist.md` against the diff and fixes what it finds,
then prepares the pull request text and the push command. The maintainer reviews;
CI must be green.

### 7. Release

Maintainer only. See `release.md`.

## Lightweight paths

| Change | Required |
| --- | --- |
| Feature, contract, schema, permission, new dependency | Full lifecycle |
| Bug fix | Spec reduced to: reproduction, expected, actual, acceptance criterion. Plan optional if one file. Regression test required. |
| Spike | `spike` branch, findings in `docs/spikes/PIN-n-<slug>.md`, code is thrown away or rewritten under a feature spec |
| Docs, typo, version bump | Branch, commit, `make check` |

## Artifacts

```
docs/
  architecture/blueprint.md   approved technical blueprint
  adr/NNNN-title.md           decisions, never edited after Accepted (supersede instead)
  roadmap.md                  phases and PIN task ids
  specs/PIN-n-slug/           spec.md, plan.md, tasks.md, verify.md
  spikes/                     spike findings with numbers
  sdlc/                       this process, templates, checklist, budgets, release
```

## Why these gates

- Specs stop the agent from building the wrong thing confidently.
- Plans catch APK size, battery and privacy costs before code exists.
- Tests first keep the agent honest about what "works" means.
- Verify files make "done" checkable by someone who did not watch the work.
- Specs do not enforce anything by themselves; `make check` and CI do.
