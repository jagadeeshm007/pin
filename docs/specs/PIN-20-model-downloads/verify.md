# PIN-20 verification

Device: Nothing A059, Android 17, debug build, Wi-Fi. Release `models-v1` published by the maintainer (10 assets).
Gate: `make check` passed, output tail:

```
test: 207 tests passed
check: passed (ktlint, lint, test, build)
```

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | `FileDownloaderTest.resumesFromAPartialFile`, `droppedConnectionIsRetriedFromWhereItStopped`; device: the ASR encoder cut to a 10,000,000-byte `.part` was completed and verified; `curl -r` against the release URL returned 206 with 10 bytes; a download interrupted by force-stop resumed by fetching only the missing file (5,048 bytes) | pass |
| AC-2 | `FileDownloaderTest.restartsWhenTheServerIgnoresRange` | pass (unit) |
| AC-3 | `FileDownloaderTest.hashMismatchDeletesTheFileAndFails`; device: one byte changed in `kws-decoder.onnx`, `models verify` reported "SHA-256 mismatch" and deleted the file, the next download restored it | pass |
| AC-4 | `FileDownloaderTest.usesTheFallbackWhenThePrimaryFails`; the Hugging Face VAD file has the same SHA-256 (checked with curl) | pass (unit); fallback not exercised on device |
| AC-5 | `ModelManagerTest` (6 cases); device: all three modules Ready, 42,712,437 bytes, `models verify` passed | pass |
| AC-6 | `FileDownloaderTest.cancellationKeepsThePartialFile` | pass (unit) |
| AC-7 | `HttpUrlSource` rejects non-HTTPS URLs and redirects away from HTTPS; `ModelCatalogTest.everyUrlIsHttps`; downloads run on `Dispatchers.IO` | pass |

Mutation checks: disabling the hash comparison failed `hashMismatchDeletesTheFileAndFails`;
making `refresh` wait for the lock failed `refreshDoesNotWaitForARunningDownload`. Both restored.

## Bugs found and fixed

1. A status refresh waited for a running download (seen as a 30 s timeout on device).
   `refresh` now returns the live state immediately.
2. The `.gitignore` rule `models/` matched the source package `.../pin/models/`, so the
   download code and tests were not committed. The rule is now anchored to `/models/`.
3. The `INTERNET` permission had been dropped in PIN-1; restored with `ACCESS_NETWORK_STATE`.

## Not verified

- The setup card on screen (status, progress bar, mobile data note) was not reviewed visually.
- Download on mobile data and on a slow network.
- Fallback to Hugging Face on device (the primary never failed).

## Budgets

| Budget | Target | Measured |
| --- | --- | --- |
| First-launch required download | < 80 MB | 42,712,437 bytes |
