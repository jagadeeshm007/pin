# PIN-21 verification

Commit: main at 12b9ea1 plus the `SpeechClient` rebind fix on `fix/PIN-21-speech-rebind`
Device: Nothing A059, Android 17 (API 37), arm64, debug build installed with adb.
Transcription: Android's on-device recognizer, `en-IN` pack (ADR-0010).
Gate: `make check` passed, output tail:

```
build: 27M app/build/outputs/apk/debug/app-debug.apk
check: passed (ktlint, lint, test, build)
```

Speech was produced by the phone's own speaker with the debug `say` command
(`scripts/pin-debug "say <text>"`), not by a person. Start and Stop were real screen taps
(`adb shell input tap`) unless noted.

| AC | Evidence | Result |
| --- | --- | --- |
| AC-1 | `ListeningService` ran in `io.github.jagadeeshm007.pin.debug:speech`, `isForeground=true`, `types=0x80` (microphone). Microphone released (`dumpsys audio` "rec stop") 142 ms after Stop on the screen and 131 ms after Stop listening in the notification. | pass after the notification fix (see Defects) |
| AC-2 | With `vad-silero-v5.onnx` moved away: Voice models card "Not downloaded yet: 7.5 MB", Listening card "Download the voice models first.", Start listening `enabled=false`. File restored afterwards. | pass |
| AC-3 | The recognizer starts only on a segmenter Begin: in 60 s of listening, "recognizer ready" was logged 8 times, once per VAD speech stretch; 5 s windows with 0 speech windows started nothing. `SpeechSegmenterTest` (7 cases). | pass |
| AC-4 | Screenshot 2.8 s into "open YouTube and play the second video about Java tutorials": overlay pill "Heard: open YouTube and play" (partial). After it ended: pill and card show the full sentence; history task 90 'open YouTube and play the second video about Java tutorials'. Three start, say, stop rounds gave tasks 87, 88, 89. | pass after the rebind fix (see Defects) |
| AC-5 | Not reproduced from adb; see Not verified. Pin registers `AudioRecordingCallback` and maps `isClientSilenced` to PAUSED. | not verified |
| AC-6 | `kill -9` of the `:speech` process while listening: main process kept its PID (18242), a scroll ran as task 86, the main screen showed "Not listening"; the next Start reconnected and recorded task 90. | pass |
| AC-7 | Only `SpeechEvent` State, Partial and Final cross the `Messenger` as string fields (`SpeechEventTest`, 3 cases). Audio is written only to the recognizer pipe from the speech process. | pass by design |

## Phrase run (speaker into mic)

| Said | Heard |
| --- | --- |
| scroll down please | scroll down please |
| open the settings app | open the settings app |
| open YouTube | open YouTube |
| scroll up | scroll up |
| open YouTube and play the second video about Java tutorials | same |
| tap search | search |
| go back | nothing (VAD found no speech) |

## Defects found and fixed

- After Stop, or after the speech process died, the main process never reconnected:
  Android reports `onBindingDied` and the binding stays dead, but `SpeechClient` kept
  `bound = true`. Transcripts after the first Stop were heard but never recorded.
  Fixed in `SpeechClient.onBindingDied` (unbind, rebind on the next start); verified by
  the three rounds and the kill test above.

- The speech service and the task notifier share notification id 1. After a task
  finished, the notifier replaced the session notification with the task result: no
  actions, not ongoing, 10 s timeout, while the microphone stayed on. Fixed: while a
  session runs, every task notification keeps Stop listening and stays ongoing
  (`NotificationContentMapperTest`, 4 new cases). Device: after `scroll down` while
  listening, id 1 showed "success via GESTURE", `ONGOING_EVENT`, Stop listening, still
  there 12 s later; tapping Stop listening released the microphone 192 ms later. With
  listening off, a task notification was not ongoing and was gone after 11 s.

## Budgets

| Budget | Target | Measured | How |
| --- | --- | --- | --- |
| Idle listening CPU (VAD only) | < 2 % of one core | 3.8 % of one core over 60 s, but the room was not quiet (8 speech stretches) | `/proc/<pid>/stat` utime+stime delta |
| Speech process memory | record | 69.5 MB PSS while listening | `dumpsys meminfo <pid>` |
| Speech end to final transcript | record | 22 to 60 ms after the segmenter's end | `PinSpeech` "final after" log |

## Not verified

- AC-5: needs a phone call or a recording app holding the mic during a session.
  `RECOGNIZE_SPEECH` and `VOICE_COMMAND` intents from adb did not take the microphone.
- Idle CPU in a quiet room.
- A human voice; all speech above came from the phone's speaker.
