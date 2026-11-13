# 0002: Kotlin with Views, no Compose, Hilt or Retrofit

Status: Accepted
Date: 2026-11-13

## Context

The APK budget is under 12 MB and most of it goes to the native speech runtime.
The UI is small: setup, settings, history viewer, an overlay. Cold start must be
under 1.5 s.

## Decision

Kotlin, Views with Material Components and view binding, hand-wired dependencies
in the Application class, OkHttp plus kotlinx.serialization for HTTP, Room,
DataStore, Tink. minSdk 30, targetSdk 36.

## Consequences

- Smaller APK and faster start than Compose plus Hilt.
- More boilerplate in UI code; acceptable at this UI size.
- Revisit Compose if the UI grows beyond about ten screens.

## Alternatives

- Compose: about 1 to 1.5 MB after R8 and slower cold start.
- Hilt: annotation processing and runtime cost for a graph of about 20 objects.
- Retrofit: adds little over OkHttp for four hand-written adapters.
