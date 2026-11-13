# 0001: Own spec-driven AI-SDLC instead of an external kit

Status: Accepted
Date: 2026-11-13

## Context

Agents write most of the code. Without written intent they build the wrong thing
confidently; without evidence, "done" cannot be checked. A general kit (Bearing)
was tried first; it brought a strict OS sandbox that fails on this machine,
tooling for stacks we do not use, and process steps sized for a team.

## Decision

Use a small in-repo process: spec, plan, tasks, build (test first), verify,
review, with human approval of spec and plan. Instructions in `AGENTS.md`
(vendor neutral) imported by `CLAUDE.md`. Stage prompts as Claude Code slash
commands in `.claude/commands/`. Enforcement through `make check` and CI, not prose.

## Consequences

- Any agent (Claude Code, Codex, Cursor) can follow it; nothing to install.
- The maintainer owns two approval gates per feature.
- We maintain the templates ourselves.

## Alternatives

- Bearing: too broad, sandbox incompatible with this machine.
- GitHub Spec Kit: good shape, but its command set and branch-per-spec flow are heavier than one maintainer needs.
- No process: fastest at first, unreviewable at scale.
