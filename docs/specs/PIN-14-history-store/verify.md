# PIN-14 verification

Commit: 8fc6e30 (plus this file)
Device: Nothing A059, Android 17, debug build.
Gate: `make check` passed, output tail:

```
test: 173 tests passed
check: passed (ktlint, lint, test, build)
```

Instrumented: `HistoryStoreTest` on the device, 3 tests, 0 failures.

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | `HistoryRecorder` writes inside `record`, which the orchestrator awaits before the device call (PIN-11 AC-5); `HistoryStoreTest.recorderStoresEveryEventInOrderWithScreens`; device: 6 real actions recorded as tasks 1 to 6, each with ATTEMPT_STARTED then SUCCEEDED | pass |
| AC-2 | `SnapshotCodecTest` (4 cases, including password text absent from the file); device test reads a stored snapshot back with password text null; device files 286 to 1,070 bytes each | pass |
| AC-3 | `RetentionPolicyTest` (3 cases); `HistoryStoreTest.retentionDeletesExpiredSessionsWithFilesAndKeepsPinnedAndOpen`; device log "retention: 0 session(s) deleted" on service connect | pass |
| AC-4 | Same device test: rows gone (cascade), directory gone; mutation check below | pass |
| AC-5 | Every `HistoryStore` method runs in `withContext(Dispatchers.IO)`; the database opens on first use | pass by design |
| AC-6 | `app/schemas/io.github.jagadeeshm007.pin.history.HistoryDatabase/1.json` committed; the device test opens the schema | pass |

Mutation check: disabling file deletion in `HistoryStore` made the retention device
test fail (1 of 3); restored.

## Budgets

| Budget | Target | Measured | How |
| --- | --- | --- | --- |
| History per step without screenshot | < 100 KB | about 1 KB per snapshot file plus one row | `ls -l files/history/1` on device |
| APK size (release, unsigned) | record delta | 1,643,749 to 1,764,878 bytes (+118 KB for Room and kotlinx.serialization) | `stat` on the release APK |

## Incident during testing

Running `connectedDebugAndroidTest` uninstalls the app afterwards, which reset the
accessibility switch and notification permission on the maintainer's phone. The
`test-device` target now passes `android.injected.androidTest.leaveApksInstalledAfterRun=true`.

## Not verified

- Retention running a second time after 24 hours of continuous connection.
- Very large screens (only synthetic 200-node test).

## Noticed

- The debug session never ends, so debug history is never removed by retention.
- The `pin` adb helper prints the first line twice (a quirk of its `sed`), not a Pin bug.
