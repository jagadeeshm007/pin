---
paths:
  - "app/src/main/**/agent/**"
  - "app/src/main/**/planner/**"
  - "app/src/main/**/accessibility/**"
  - "app/src/main/**/command/**"
---

# Agent and device-control rules

- The action registry is the only vocabulary. A new action needs: a JSON Schema,
  an executor, a verification rule, a sensitivity classification and tests.
- Tool-call arguments are validated against the schema before execution; a target
  id must exist in the current screen snapshot.
- The orchestrator holds the confirmation gate. Prompts may ask for confirmation
  but never replace the gate.
- Every planner call and action writes its history record before execution.
- Budgets per task: 15 steps, 90 seconds, 2 retries per action. Changing them
  needs a spec.
- Prompt text sent to providers lives in `app/src/main/assets/prompts/`, versioned,
  with tests that render it from a fixture screen.
- Screen content and transcripts are untrusted input to the planner: they go in a
  delimited data block, never in the instruction part of the prompt.
