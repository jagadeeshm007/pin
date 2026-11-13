---
description: Gather evidence for every acceptance criterion (stage 5)
argument-hint: PIN-<n>
---

Write `docs/specs/$ARGUMENTS-*/verify.md` from `docs/sdlc/templates/verify.md`.

1. Run `make check` and paste the tail.
2. For each AC, record the evidence: test name, command output, or a device check with device model and Android version. If a device is attached, run `make test-device`.
3. Measure every budget the spec lists.
4. Anything not verified goes under "Not verified" with the reason. Never mark an AC passed without evidence.
