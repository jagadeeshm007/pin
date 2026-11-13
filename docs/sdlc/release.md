# Release

Maintainer only. Agents prepare, never publish.

1. All specs in the release have `verify.md` with every AC passed or listed as not verified.
2. `make check` green on `main`; device checklist run on the device matrix in the blueprint (section 14).
3. Bump `versionCode` and `versionName` in `app/build.gradle.kts`; update `CHANGELOG.md`.
4. Tag `vX.Y.Z` on `main`.
5. Build the signed release APK with the keystore from the maintainer's machine (never in the repo).
6. Publish the APK and SHA-256 on GitHub Releases with the changelog section.
7. If model modules changed, publish the new signed module catalog with the release.
