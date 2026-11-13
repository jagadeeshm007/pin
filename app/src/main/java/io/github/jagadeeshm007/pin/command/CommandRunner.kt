package io.github.jagadeeshm007.pin.command

import android.os.SystemClock
import android.util.Log
import io.github.jagadeeshm007.pin.PinApp
import io.github.jagadeeshm007.pin.R
import io.github.jagadeeshm007.pin.accessibility.AccessibilityDeviceController
import io.github.jagadeeshm007.pin.accessibility.AccessibilityScreenSource
import io.github.jagadeeshm007.pin.accessibility.PinAccessibilityService
import io.github.jagadeeshm007.pin.action.ActionOrchestrator
import io.github.jagadeeshm007.pin.action.ActionResult
import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.device.QuirkObserver
import io.github.jagadeeshm007.pin.notification.BrokerConfirmer
import io.github.jagadeeshm007.pin.notification.TaskControl
import io.github.jagadeeshm007.pin.notification.TaskPhase
import io.github.jagadeeshm007.pin.notification.TaskStatus
import io.github.jagadeeshm007.pin.overlay.GridModel
import io.github.jagadeeshm007.pin.overlay.NumberLabeler
import io.github.jagadeeshm007.pin.overlay.OverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs spoken commands on the phone (spec PIN-30), one at a time, through the
 * [ActionOrchestrator] like every other action. Each run is a history task with route
 * "local". Notification Stop cancels the run. Call from the main thread.
 */
class CommandRunner(
    private val service: PinAccessibilityService,
    private val app: PinApp,
    private val apps: AppCatalog,
) {
    private val screen = AccessibilityScreenSource(service)
    private val quirks = QuirkObserver(app.quirks)
    private val oneAtATime = Mutex()

    /** What a run ended as, for the history task. */
    private data class Outcome(val status: String, val summary: String, val done: Boolean)

    /** Runs [command], heard as [text], as a task of [sessionId]. */
    suspend fun run(command: Command, text: String, sessionId: Long) = oneAtATime.withLock {
        Log.d(TAG, "dispatch ${command.javaClass.simpleName} at ${SystemClock.elapsedRealtime()}")
        val task = app.history.startTask(sessionId, text, ROUTE)
        val outcome = try {
            untilStopped { perform(command, task) }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { app.history.endTask(task, "stopped", null) }
            throw e
        }
        app.history.endTask(task, outcome.status, outcome.summary)
        app.profiles.saveQuirks(app.quirks)
        service.overlay?.showState(
            if (outcome.status ==
                UNMATCHED
            ) {
                OverlayState.HEARD
            } else {
                OverlayState.IDLE
            },
            outcome.summary.takeIf { outcome.status == UNMATCHED },
        )
        app.notifier.show(TaskStatus(if (outcome.done) TaskPhase.DONE else TaskPhase.FAILED, outcome.summary))
    }

    private suspend fun untilStopped(block: suspend () -> Outcome): Outcome = coroutineScope {
        val work = async { block() }
        val stop = launch {
            TaskControl.stopRequests.first()
            work.cancel()
        }
        try {
            work.await()
        } catch (e: CancellationException) {
            if (!isActive) throw e
            Outcome("stopped", text(R.string.command_stopped), done = false)
        } finally {
            stop.cancel()
        }
    }

    private suspend fun perform(command: Command, task: Long): Outcome {
        marks(command)?.let { return it }
        val action = when (command) {
            is Command.Act -> command.action

            is Command.OnLabel -> onLabel(command) ?: return failed(text(R.string.command_no_label, command.label))

            is Command.TypeText -> {
                val field = LabelResolver.inputField(screen.current()) ?: return failed(text(R.string.command_no_field))
                DeviceAction.TypeText(field.id, command.text)
            }

            is Command.OpenApp -> {
                val pkg = withContext(Dispatchers.IO) { apps.find(command.name) }
                    ?: return Outcome(UNMATCHED, text(R.string.command_not_a_command), done = false)
                DeviceAction.OpenApp(pkg)
            }

            is Command.TapNumber -> DeviceAction.Tap(
                numbered(command.number) ?: return failed(text(R.string.command_no_number, command.number)),
            )

            else -> return failed(command.toString())
        }
        return execute(action, task)
    }

    /** Commands that only change the overlay, or stop; null for the rest. */
    private suspend fun marks(command: Command): Outcome? {
        val overlay = service.overlay
        val summary = when (command) {
            Command.ShowNumbers -> {
                overlay?.showNumbers(NumberLabeler().label(screen.current()))
                R.string.command_numbers_shown
            }

            Command.HideNumbers -> {
                overlay?.clearMarks()
                R.string.command_numbers_hidden
            }

            Command.ShowGrid -> {
                overlay?.showGrid(GridModel(screen.current().windowBounds))
                R.string.command_grid_shown
            }

            Command.Stop -> {
                TaskControl.requestStop()
                R.string.command_stopped
            }

            else -> return null
        }
        return Outcome("success", text(summary), done = true)
    }

    private fun text(id: Int, vararg args: Any): String = service.getString(id, *args)

    private suspend fun onLabel(command: Command.OnLabel): DeviceAction? {
        val node = LabelResolver.find(screen.current(), command.label) ?: return null
        val target = TargetRef.Node(node.id)
        return when (command.kind) {
            Command.LabelKind.TAP -> DeviceAction.Tap(target)
            Command.LabelKind.LONG_PRESS -> DeviceAction.LongPress(target)
            Command.LabelKind.DOUBLE_TAP -> DeviceAction.DoubleTap(target)
        }
    }

    /** A cell of the shown grid, or a shown number; clears the marks when found. */
    private suspend fun numbered(n: Int): TargetRef? {
        val overlay = service.overlay ?: return null
        val target = overlay.grid?.cell(n)?.center?.let { (x, y) -> TargetRef.Point(x, y) }
            ?: overlay.numbers?.resolve(n, screen.current())
        if (target != null) overlay.clearMarks()
        return target
    }

    private suspend fun execute(action: DeviceAction, task: Long): Outcome {
        val overlay = service.overlay
        overlay?.showState(OverlayState.ACTING, action.javaClass.simpleName)
        app.notifier.show(TaskStatus(TaskPhase.ACTING, action.toString(), 1, 1))
        val history = app.history.recorder(task)
        val orchestrator = ActionOrchestrator(
            device = AccessibilityDeviceController(service),
            screen = screen,
            confirmer = BrokerConfirmer(app.confirmations) { reason ->
                service.overlay?.showState(OverlayState.CONFIRM, reason)
            },
            recorder = { event ->
                history.record(event)
                quirks.record(event)
            },
        )
        return when (val r = orchestrator.execute(action)) {
            is ActionResult.Success -> Outcome("success", "success via ${r.strategy}", done = true)
            is ActionResult.Failed -> failed("failed: ${r.reasons.joinToString("; ")}")
            is ActionResult.Rejected -> Outcome("rejected", "rejected: ${r.reason}", done = false)
            ActionResult.Declined -> Outcome("declined", "declined", done = false)
        }
    }

    private fun failed(summary: String) = Outcome("failed", summary, done = false)

    companion object {
        const val ROUTE = "local"
        const val UNMATCHED = "unmatched"
        private const val TAG = "PinCommand"
    }
}
