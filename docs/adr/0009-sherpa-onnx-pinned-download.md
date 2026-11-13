# 0009: Fetch sherpa-onnx as a pinned download at build time

Status: Accepted (maintainer, 2026-11-13)
Date: 2026-11-13

## Context

sherpa-onnx (ADR-0003) is not on Maven Central. Upstream publishes Android libraries
only as GitHub release assets. JitPack offers a copy built from source by JitPack, not
the official binary. PIN-2 measured `sherpa-onnx-static-link-onnxruntime-2.0.1.aar`
(38.6 MB, ONNX Runtime linked in).

## Decision

A Gradle task downloads the official release asset into `build/third-party/` and checks
its SHA-256 before the build uses it. The URL and hash live in `app/build.gradle.kts`
next to the dependency.

- URL: `https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-static-link-onnxruntime-2.0.1.aar`
- SHA-256: `07e042d97f3a6bd5b7fe74d4eef0513dc01a714b16c15a903250095aba43a575`
  (matches the digest GitHub publishes for the asset)
- A mismatch fails the build. A cached file with the right hash is reused offline.
- Only `arm64-v8a` native code is packaged (`abiFilters`), compressed
  (`jniLibs.useLegacyPackaging = true`), as measured in PIN-2.

## Consequences

- No large file in git; CI and every developer build the same binary.
- The first build needs network access to github.com.
- Upgrading means changing version, URL and hash together, in its own commit.

## Alternatives

- JitPack: not the official binary; variant uncertain.
- Git LFS: every clone pulls 38.6 MB; contributors need LFS.
