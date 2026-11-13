# PIN-20 plan

Status: Approved (2026-11-13)
Spec: ./spec.md

## Design

| Pure (JVM tests) | Android |
| --- | --- |
| `ModelCatalog` (modules VAD, KWS, ASR; files with size, SHA-256, URLs from ADR-0008) | `HttpUrlSource : RangeSource` over `HttpURLConnection` (HTTPS only, redirects followed, 20 s timeouts) |
| `RangeSource` interface: open(url, fromByte) returns code, length, stream | Setup section in `MainActivity` |
| `FileDownloader`: `.part` file, Range resume, 200 restart, SHA-256 while streaming (re-hash existing part first), size check, atomic rename, fallback list, retries with back-off, progress callback, cancellation keeps part | `PinApp.models` (manager), install dir `files/models/v1/` |
| `ModelManager`: state per module (missing, downloading, ready, failed), total and done bytes as `StateFlow`, `downloadAll`, `verify` | Debug commands `models`, `models download`, `models verify`, `models wipe` |

No new dependency: `HttpURLConnection` and `MessageDigest` are in the platform.

## Test strategy

- Unit: `FileDownloaderTest` with an in-memory `RangeSource` (fresh, resume, 200 restart,
  hash mismatch, fallback, retries exhausted, cancellation), `ModelCatalogTest` (totals,
  unique names, HTTPS only), `ModelManagerTest` (states, progress).
- Device (after the maintainer publishes `models-v1`): fresh download on the phone,
  interrupt by turning off Wi-Fi mid-download, resume, verify, and a tampered file test.
