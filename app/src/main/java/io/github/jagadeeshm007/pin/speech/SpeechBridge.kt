package io.github.jagadeeshm007.pin.speech

import io.github.jagadeeshm007.pin.PinApp
import io.github.jagadeeshm007.pin.notification.TaskPhase
import io.github.jagadeeshm007.pin.notification.TaskStatus
import io.github.jagadeeshm007.pin.overlay.OverlayController
import io.github.jagadeeshm007.pin.overlay.OverlayState

/**
 * Main-process consumer of speech events (spec PIN-21 AC-4): shows them on the overlay and
 * notification and records every final transcript in history. Acting on transcripts is
 * PIN-30. Runs on the main thread inside the accessibility service's scope.
 */
class SpeechBridge(private val app: PinApp, private val overlay: () -> OverlayController?) {
    private var sessionId: Long? = null

    /** Collects events until the scope is cancelled. */
    suspend fun run() {
        app.speech.connect()
        app.speech.events.collect { handle(it) }
    }

    private suspend fun handle(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.State -> onState(event)

            is SpeechEvent.Partial -> overlay()?.showState(OverlayState.HEARD, event.text)

            is SpeechEvent.Final -> {
                overlay()?.showState(OverlayState.HEARD, event.text)
                app.notifier.show(TaskStatus(TaskPhase.HEARD, event.text))
                val session = sessionId ?: app.history.startSession("voice").also { sessionId = it }
                val task = app.history.startTask(session, event.text, "voice")
                app.history.endTask(task, "heard", "audio ${event.audioMs} ms, decode ${event.decodeMs} ms")
            }
        }
    }

    private suspend fun onState(event: SpeechEvent.State) {
        when (event.state) {
            ListenState.LOADING, ListenState.LISTENING -> overlay()?.showState(OverlayState.LISTENING)

            ListenState.PAUSED -> overlay()?.showState(OverlayState.PAUSED, event.detail)

            ListenState.ERROR -> overlay()?.showState(OverlayState.ERROR, event.detail)

            ListenState.STOPPED -> {
                overlay()?.showState(OverlayState.IDLE)
                sessionId?.let { app.history.endSession(it) }
                sessionId = null
            }
        }
    }
}
