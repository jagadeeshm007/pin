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
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
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

    /** When numbers were shown for an ambiguous label; a bare number answers within [ASK_WINDOW_MS]. */
    private var askedAt = 0L

    /** What a run ended as, for the history task. */
    private data class Outcome(val status: String, val summary: String, val done: Boolean)

    /** Runs [command], heard as [text], as a task of [sessionId]; returns the task status. */
    suspend fun run(command: Command, text: String, sessionId: Long): String = oneAtATime.withLock {
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
        // Outcomes the user must hear about stay on the pill; success returns to idle.
        val tell = outcome.status in TOLD
        service.overlay?.showState(if (tell) OverlayState.HEARD else OverlayState.IDLE, outcome.summary.takeIf { tell })
        app.notifier.show(TaskStatus(if (outcome.done) TaskPhase.DONE else TaskPhase.FAILED, outcome.summary))
        outcome.status
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
        if (command !is Command.PickNumber && command !is Command.TapNumber) forgetAsk()
        marks(command)?.let { return it }
        val action = when (command) {
            is Command.Act -> command.action

            is Command.OnLabel -> when (val found = onLabel(command)) {
                is Found.Action -> found.action
                is Found.Several -> return ask(found.nodes)
                Found.Nothing -> return failed(text(R.string.command_no_label, command.label))
            }

            is Command.PickNumber -> {
                val fresh = SystemClock.elapsedRealtime() - askedAt <= ASK_WINDOW_MS
                val target = numbered(command.number).takeIf { fresh }
                    ?: return failed(text(R.string.command_no_numbers))
                DeviceAction.Tap(target)
            }

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

    private sealed interface Found {
        data class Action(val action: DeviceAction) : Found

        data class Several(val nodes: List<ScreenNode>) : Found

        data object Nothing : Found
    }

    private suspend fun onLabel(command: Command.OnLabel): Found {
        val node = when (val r = LabelResolver.resolve(screen.current(), command.label)) {
            is LabelResolver.Resolution.One -> r.node
            is LabelResolver.Resolution.Many -> return Found.Several(r.nodes)
            LabelResolver.Resolution.None -> return Found.Nothing
        }
        val target = TargetRef.Node(node.id)
        return Found.Action(
            when (command.kind) {
                Command.LabelKind.TAP -> DeviceAction.Tap(target)
                Command.LabelKind.LONG_PRESS -> DeviceAction.LongPress(target)
                Command.LabelKind.DOUBLE_TAP -> DeviceAction.DoubleTap(target)
            },
        )
    }

    /** Several nodes fit: number just those and ask (spec PIN-31 AC-2). */
    private fun ask(nodes: List<ScreenNode>): Outcome {
        service.overlay?.showNumbers(NumberLabeler().labelNodes(nodes))
        askedAt = SystemClock.elapsedRealtime()
        return Outcome(AMBIGUOUS, text(R.string.command_say_a_number), done = false)
    }

    /** Any command other than a number ends the question and clears its numbers. */
    private fun forgetAsk() {
        if (askedAt == 0L) return
        askedAt = 0L
        service.overlay?.clearMarks()
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
        const val AMBIGUOUS = "ambiguous"
        const val ASK_WINDOW_MS = 15_000L
        private val TOLD = setOf(UNMATCHED, AMBIGUOUS, "failed")
        private const val TAG = "PinCommand"
    }
}
