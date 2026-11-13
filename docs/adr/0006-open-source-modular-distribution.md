# 0006: Open-source distribution with downloadable model modules

Status: Accepted
Date: 2026-11-13

## Context

The project is open source and sideloaded, not on Google Play. Speech models are
50 to 150 MB each.

## Decision

Apache-2.0 licence (assumed until the maintainer confirms). Signed APKs on GitHub
Releases. `isAccessibilityTool=true`. Models and language packs are downloaded on
first launch from a signed catalog with SHA-256 checks; native code stays in the
APK. Task progress uses Android 16 Live Updates with a standard notification
fallback.

## Consequences

- APK under 12 MB; first launch needs a download of about 50 to 80 MB.
- Users on Android 13+ who sideload must allow restricted settings; developer
  verification rules apply to sideloaded installs from 2027.
- Forks targeting Play must handle its AccessibilityService policy themselves.
