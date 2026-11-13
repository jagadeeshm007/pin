# Security

## Reporting

Report vulnerabilities privately through GitHub Security Advisories on this
repository, not in a public issue. Include steps to reproduce, impact and the
version. Expect an acknowledgement within a few days.

## What matters most in this app

Pin holds powerful permissions (accessibility, microphone) and the user's LLM
provider keys. Every change must keep these true:

1. Device actions only through the validated action registry; the planner never
   runs code or arbitrary intents.
2. Sensitive actions require confirmation, enforced in code, not in prompts.
3. Provider keys are encrypted with an Android Keystore-backed key, never logged,
   never exported, never backed up.
4. Audio stays on the device. Screen content goes only to the provider the user
   chose, only during an agent task, and never from password fields.
5. Screen content and transcripts are treated as untrusted input to the planner
   (prompt injection).
6. No exported component without a permission; no cleartext traffic.
7. Downloaded models are verified against a signed catalog; native code is never
   downloaded.
