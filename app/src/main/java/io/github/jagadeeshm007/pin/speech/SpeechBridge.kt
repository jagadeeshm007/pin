package io.github.jagadeeshm007.pin.speech

import android.os.SystemClock
import io.github.jagadeeshm007.pin.PinApp
import io.github.jagadeeshm007.pin.R
import io.github.jagadeeshm007.pin.command.Command
import io.github.jagadeeshm007.pin.command.CommandRunner
import io.github.jagadeeshm007.pin.command.IntentRouter
import io.github.jagadeeshm007.pin.command.IntentRouter.Route
import io.github.jagadeeshm007.pin.notification.TaskControl
import io.github.jagadeeshm007.pin.notification.TaskPhase
import io.github.jagadeeshm007.pin.notification.TaskStatus
import io.github.jagadeeshm007.pin.overlay.OverlayController
import io.github.jagadeeshm007.pin.overlay.OverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Main-process consumer of speech events (specs PIN-21 AC-4, PIN-30): shows them on the
 * overlay and notification, turns commands into runs of [runner], and records everything
 * else in history. Runs on the main thread inside the accessibility service's scope.
 */
class SpeechBridge(
    private val app: PinApp,
    private val overlay: () -> OverlayController?,
    private val runner: CommandRunner,
    private val scope: CoroutineScope,
) {
    private val router = IntentRouter(clock = SystemClock::elapsedRealtime)
    private var sessionId: Long? = null

    /** Collects events until the scope is cancelled. */
    suspend fun run() {
        app.speech.connect()
        app.speech.events.collect { handle(it) }
    }

    /** Treats [text] as a final transcript (debug `hear`, spec PIN-30). */
    suspend fun hear(text: String) = handle(SpeechEvent.Final(text, 0, 0))

    private suspend fun handle(event: SpeechEvent) {
        when (event) {
            is SpeechEvent.State -> onState(event)

            is SpeechEvent.Partial -> {
                overlay()?.showState(OverlayState.HEARD, event.text)
                route(router.onPartial(event.text))
            }

            is SpeechEvent.Final -> {
                overlay()?.showState(OverlayState.HEARD, event.text)
                route(router.onFinal(event.text), event)
            }
        }
    }

    private suspend fun route(route: Route, final: SpeechEvent.Final? = null) {
        when (route) {
            is Route.Run -> {
                // Stop must not wait behind the run it is meant to stop.
                if (route.command == Command.Stop) TaskControl.requestStop()
                val session = session()
                scope.launch {
                    // After "Say a number" the answer needs no prefix (PIN-31 device report).
                    if (runner.run(route.command, route.text, session) == CommandRunner.AMBIGUOUS) {
                        router.arm(CommandRunner.ASK_WINDOW_MS)
                    }
                }
            }

            is Route.Unmatched -> {
                runner.dismissAsk()
                overlay()?.showState(OverlayState.HEARD, app.getString(R.string.command_not_a_command))
                record(route.text, CommandRunner.UNMATCHED, final)
            }

            is Route.Heard -> {
                runner.dismissAsk()
                app.notifier.show(TaskStatus(TaskPhase.HEARD, route.text))
                record(route.text, "heard", final)
            }

            Route.Armed -> overlay()?.showState(OverlayState.LISTENING, app.getString(R.string.command_armed))

            Route.AlreadyRun, Route.Nothing -> Unit
        }
    }

    private suspend fun record(text: String, status: String, final: SpeechEvent.Final?) {
        val task = app.history.startTask(session(), text, "voice")
        app.history.endTask(task, status, final?.let { "audio ${it.audioMs} ms, decode ${it.decodeMs} ms" })
    }

    private suspend fun session(): Long = sessionId ?: app.history.startSession("voice").also { sessionId = it }

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
