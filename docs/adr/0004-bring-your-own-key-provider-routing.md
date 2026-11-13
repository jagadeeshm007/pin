# 0004: User-chosen provider with app-owned model routing

Status: Accepted
Date: 2026-11-13

## Context

The project ships no API key and has no backend. Users differ in which provider
they pay for. Model names change often.

## Decision

The user picks one provider (Claude, OpenAI, Gemini, OpenRouter) and enters their
own key. The app keeps a model table per provider with roles planner, vision and
fast, refreshed from a hosted JSON file at most daily. Adapters are hand-written
over OkHttp; no vendor SDKs. History is stored in a provider-neutral form.

## Consequences

- No server costs, no key custody.
- Four adapters to maintain; contract tests on recorded responses catch drift.
- Advanced users may override the model per role.

## Alternatives

- Single provider: simpler but excludes users.
- Vendor SDKs: several MB and four update cadences.
