# PIN-20: Model downloads and first-launch setup

Status: Approved (maintainer instruction "continue" and ADR-0008, 2026-11-13; open to revision)
Owner: maintainer
Roadmap phase: 2
Blueprint sections: 5.4 (first-launch module setup), 10 (APK size)
ADRs: 0006 (modular distribution), 0008 (model hosting)

## Problem

Speech needs about 42.7 MB of models that are not in the APK. The app must offer them on
first launch, download them reliably on poor networks, refuse tampered files, and know
when voice is ready.

## Scope

- In: a model catalog in the APK (file, size, SHA-256, primary URL, fallback URLs);
  a resumable downloader (HTTP Range, `.part` files, retries, fallback, hash check,
  atomic rename); a module manager with per-module state and overall progress; a setup
  section on the main screen (sizes, Wi-Fi or mobile data note, Download, progress,
  retry); debug commands.
- Out: progress in the Live Update notification (added when the listening service owns
  the notification, PIN-21), optional language packs, model updates (models-v2).

## Acceptance criteria

- AC-1: WHEN a download is interrupted THE next attempt SHALL continue from the bytes already on disk.
- AC-2: WHEN the server ignores the range request (answers 200) THE downloader SHALL restart the file from zero.
- AC-3: WHEN a finished file's size or SHA-256 differs from the catalog THE downloader SHALL delete it and report a failure; a mismatching file is never used.
- AC-4: WHEN the primary URL fails and the catalog lists a fallback THE downloader SHALL try the fallback.
- AC-5: WHEN every file of a module is present and verified THE module SHALL be ready; the app SHALL re-check hashes of installed files only when asked (not on every start).
- AC-6: WHEN the user cancels THE partial files SHALL stay for resuming.
- AC-7: Downloads SHALL run off the main thread over HTTPS only.

## Budgets touched

First-launch required download < 80 MB (catalog total 42.7 MB).

## Privacy

Requests go to github.com (and huggingface.co for the fallback file). Only the file URL
is sent; no identifiers, no cookies.
