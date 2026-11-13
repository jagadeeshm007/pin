---
paths:
  - "app/src/**"
---

# Code rules

- Validate at the boundary: provider responses, tool-call arguments, downloaded
  catalogs and settings are parsed into typed models once, where they enter.
- Errors carry context and are handled once, at the edge that reports to the user.
- Logs are structured and never contain keys, transcripts, screen text or
  screenshots. Debug-only verbose logs are stripped from release by R8.
- No premature abstraction or caching; measure first against `docs/sdlc/budgets.md`.
- Comments explain why. Public functions get a one-line KDoc.
- A file over 400 lines or a function over 40 lines is split.
- TODOs carry a task id: `// TODO(PIN-23): ...`.
