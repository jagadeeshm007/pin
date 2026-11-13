package io.github.jagadeeshm007.pin.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.jagadeeshm007.pin.PinApp
import io.github.jagadeeshm007.pin.accessibility.AccessibilityDeviceController
import io.github.jagadeeshm007.pin.accessibility.AccessibilityRegistry
import io.github.jagadeeshm007.pin.accessibility.AccessibilityScreenSource
import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.action.ActionOrchestrator
import io.github.jagadeeshm007.pin.action.ActionRecorder
import io.github.jagadeeshm007.pin.action.ActionResult
import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import io.github.jagadeeshm007.pin.device.QuirkObserver
import io.github.jagadeeshm007.pin.notification.BrokerConfirmer
import io.github.jagadeeshm007.pin.notification.TaskControl
import io.github.jagadeeshm007.pin.notification.TaskPhase
import io.github.jagadeeshm007.pin.notification.TaskStatus
import io.github.jagadeeshm007.pin.overlay.GridModel
import io.github.jagadeeshm007.pin.overlay.NumberLabeler
import io.github.jagadeeshm007.pin.overlay.OverlayState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/**
 * Debug builds only. Runs one command through the real orchestrator so device
 * behaviour can be tested from adb before voice exists. Sensitive actions are always
 * answered through the task notification (no answer in 15 s means no).
 *
 * adb shell am broadcast -n io.github.jagadeeshm007.pin.debug/io.github.jagadeeshm007.pin.debug.DebugCommandReceiver \
 *   -a io.github.jagadeeshm007.pin.debug.COMMAND --es cmd "tap Search"
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val line = intent.getStringExtra(EXTRA_CMD).orEmpty()
        appContext = context.applicationContext as PinApp
        val pending = goAsync()
        scope.launch {
            val output = try {
                withTimeout(TIMEOUT_MS) { runUntilStopped(line) }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                "error: timed out after $TIMEOUT_MS ms (${e.message})"
            }
            pending.resultData = output
            pending.finish()
        }
    }

    /** Runs [line], cancelling it when the notification's Stop is pressed. */
    private suspend fun runUntilStopped(line: String): String = coroutineScope {
        val work = async { run(line) }
        val stop = launch {
            TaskControl.stopRequests.first()
            work.cancel()
        }
        try {
            work.await()
        } catch (e: CancellationException) {
            if (!isActive) throw e
            withContext(NonCancellable + Dispatchers.Main) {
                (AccessibilityRegistry.service.value?.applicationContext as? PinApp)?.notifier?.show(
                    TaskStatus(TaskPhase.FAILED, "Stopped by user"),
                )
                AccessibilityRegistry.service.value?.overlay?.showState(OverlayState.IDLE)
            }
            "stopped: Stop pressed in the notification"
        } finally {
            stop.cancel()
        }
    }

    private suspend fun run(line: String): String {
        val parsed = DebugCommand.parse(line)
        if (parsed is DebugCommand.Models) {
            val app = AccessibilityRegistry.service.value?.applicationContext as? PinApp ?: appContext
                ?: return "error: app not ready"
            return models(app, parsed.op)
        }
        val service = AccessibilityRegistry.service.value ?: return "error: accessibility service is off"
        val command = parsed ?: return "error: cannot parse '$line'"
        val screen = AccessibilityScreenSource(service)
        val overlay = service.overlay ?: return "error: overlay not ready"

        val app = service.applicationContext as PinApp
        val events = mutableListOf<ActionEvent>()
        val quirkObserver = QuirkObserver(app.quirks)
        var history: ActionRecorder? = null
        val orchestrator = ActionOrchestrator(
            device = AccessibilityDeviceController(service),
            screen = screen,
            confirmer = BrokerConfirmer(app.confirmations) { reason ->
                scope.launch(Dispatchers.Main) { overlay.showState(OverlayState.CONFIRM, reason) }
            },
            recorder = { event ->
                events += event
                history?.record(event)
                quirkObserver.record(event)
            },
        )
        val action: DeviceAction = when (command) {
            DebugCommand.Dump -> return dump(screen.current())

            is DebugCommand.Overlay -> return onMain {
                overlay.showState(command.state, command.detail)
                "overlay: ${command.state}${command.detail?.let { " ($it)" } ?: ""}"
            }

            DebugCommand.Hide -> return onMain {
                overlay.clearMarks()
                overlay.showState(OverlayState.IDLE)
                "overlay: hidden"
            }

            DebugCommand.Numbers -> {
                val map = NumberLabeler().label(screen.current())
                onMain { overlay.showNumbers(map) }
                return "numbers: ${map.entries.size} shown\n" +
                    map.entries.joinToString("\n") { "  ${it.number} -> node ${it.nodeId} ${it.role} ${it.bounds}" }
            }

            is DebugCommand.TapNumber -> {
                val map = onMain { overlay.numbers } ?: return "error: no numbers shown"
                val target = map.resolve(command.number, screen.current())
                    ?: return "error: number ${command.number} was not shown or has moved"
                onMain { overlay.clearMarks() }
                DeviceAction.Tap(target)
            }

            DebugCommand.Grid -> {
                val grid = GridModel(screen.current().windowBounds)
                onMain { overlay.showGrid(grid) }
                return "grid: ${grid.cells.size} cells over ${grid.area}"
            }

            is DebugCommand.GridZoom -> {
                val grid = onMain { overlay.grid } ?: return "error: no grid shown"
                val finer = grid.refine(command.number) ?: return "error: cell ${command.number} cannot be refined"
                onMain { overlay.showGrid(finer) }
                return "grid: ${finer.cells.size} cells over ${finer.area}"
            }

            is DebugCommand.GridPick -> {
                val grid = onMain { overlay.grid } ?: return "error: no grid shown"
                val cell = grid.cell(command.number) ?: return "error: no cell ${command.number}"
                onMain { overlay.clearMarks() }
                DeviceAction.Tap(TargetRef.Point(cell.center.first, cell.center.second))
            }

            is DebugCommand.Notify -> return onMain {
                app.notifier.show(command.status)
                "notify: ${command.status} (promotion allowed: ${app.notifier.canPromote})"
            }

            DebugCommand.NotifyClear -> return onMain {
                app.notifier.clear()
                "notify: cleared"
            }

            is DebugCommand.Ask -> {
                lastAnswer = "waiting"
                scope.launch {
                    // Broadcasts reach this receiver one at a time, so Stop is tested on a
                    // pending question rather than on a blocking command.
                    val question = async { app.confirmations.request(command.question) }
                    val watcher = launch {
                        TaskControl.stopRequests.first()
                        question.cancel()
                    }
                    lastAnswer = try {
                        if (question.await()) "confirmed" else "denied"
                    } catch (e: CancellationException) {
                        "stopped"
                    } finally {
                        watcher.cancel()
                    }
                }
                return "ask: '${command.question}' pending"
            }

            DebugCommand.LastAnswer -> return "answer: $lastAnswer"

            is DebugCommand.History -> return app.history.recentTasks(command.limit).joinToString("\n") {
                "task ${it.id} session ${it.sessionId} ${it.status} steps=${it.stepCount} '${it.utterance}'"
            }.ifEmpty { "history: empty" }

            is DebugCommand.Steps -> return app.history.steps(command.taskId).joinToString("\n") {
                "${it.seq} ${it.kind} ${it.strategy ?: "-"} ${it.reason ?: ""} screen=${it.screenFile ?: "-"}"
            }.ifEmpty { "steps: none for task ${command.taskId}" }

            DebugCommand.Profile -> return app.profiles.loadProfile()?.toJson() ?: "profile: not collected yet"

            DebugCommand.Quirks -> return app.quirks.all().entries.joinToString("\n") { (k, v) ->
                "$k node_ok=${v.nodeOk} gesture_rescued=${v.gestureRescued}"
            }.ifEmpty { "quirks: none yet" }

            DebugCommand.Retention -> {
                val deleted = app.history.applyRetention(app.retention)
                return "retention: $deleted session(s) deleted, ${app.history.fileBytes()} bytes of snapshots kept"
            }

            is DebugCommand.Models -> return "unreachable"

            is DebugCommand.Direct -> command.action

            is DebugCommand.OnLabel -> resolve(command, screen.current())
                ?: return "error: no node labelled '${command.label}'"
        }
        onMain {
            overlay.showState(OverlayState.ACTING, action.javaClass.simpleName)
            app.notifier.show(TaskStatus(TaskPhase.ACTING, action.toString(), 1, 1))
        }
        val taskId = app.history.startTask(debugSession(app), line, "debug")
        history = app.history.recorder(taskId)
        val started = System.nanoTime()
        val result = try {
            orchestrator.execute(action)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { app.history.endTask(taskId, "stopped", null) }
            throw e
        }
        app.history.endTask(taskId, statusOf(result), describeResult(result))
        app.profiles.saveQuirks(app.quirks)
        val ms = (System.nanoTime() - started) / NANOS_PER_MS
        onMain {
            overlay.showState(OverlayState.IDLE)
            val phase = if (result is ActionResult.Success) TaskPhase.DONE else TaskPhase.FAILED
            app.notifier.show(TaskStatus(phase, describeResult(result), 1, 1))
        }
        val trail = events.joinToString("\n") { "  ${describe(it)}" }
        return "task: $taskId\naction: $action\nresult: ${describeResult(result)}\nelapsed: $ms ms\nevents:\n$trail"
    }

    /** Model commands need no accessibility service. Download runs in the app scope and returns at once. */
    private suspend fun models(app: PinApp, op: DebugCommand.ModelOp): String {
        val m = app.models
        when (op) {
            DebugCommand.ModelOp.STATUS -> m.refresh()
            DebugCommand.ModelOp.DOWNLOAD -> app.appScope.launch { m.downloadAll() }
            DebugCommand.ModelOp.VERIFY -> m.verify()
            DebugCommand.ModelOp.WIPE -> m.wipe()
        }
        val p = m.progress.value
        return "models: ${p.doneBytes}/${p.totalBytes} bytes\n" +
            m.states.value.entries.joinToString("\n") { (id, state) -> "  $id: $state" }
    }

    private fun statusOf(r: ActionResult): String = when (r) {
        is ActionResult.Success -> "success"
        is ActionResult.Failed -> "failed"
        is ActionResult.Rejected -> "rejected"
        ActionResult.Declined -> "declined"
    }

    /** One debug session per app process, so debug runs group together in history. */
    private suspend fun debugSession(app: PinApp): Long =
        debugSessionId ?: app.history.startSession("debug").also { debugSessionId = it }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    private fun resolve(command: DebugCommand.OnLabel, screen: ScreenSnapshot): DeviceAction? {
        val wantEditable = command.kind == DebugCommand.Kind.TYPE
        val candidates = screen.nodes.filter { !wantEditable || it.has(NodeFlag.EDITABLE) }
        // Exact labels only: a partial match sent "Search" to "Voice search" on YouTube,
        // which opened the microphone. Fuzzy matching with disambiguation is PIN-31.
        val node = candidates.firstOrNull { it.label.equals(command.label, ignoreCase = true) } ?: return null
        val target = TargetRef.Node(node.id)
        return when (command.kind) {
            DebugCommand.Kind.TAP -> DeviceAction.Tap(target)
            DebugCommand.Kind.DOUBLE_TAP -> DeviceAction.DoubleTap(target)
            DebugCommand.Kind.LONG_PRESS -> DeviceAction.LongPress(target)
            DebugCommand.Kind.TYPE -> DeviceAction.TypeText(node.id, command.text, command.submit)
        }
    }

    private fun dump(screen: ScreenSnapshot): String {
        val header = "package=${screen.packageName} title='${screen.windowTitle}' window=${screen.windowBounds} " +
            "version=${screen.contentVersion} nodes=${screen.nodes.size}"
        return (listOf(header) + screen.nodes.map(::describeNode)).joinToString("\n")
    }

    private fun describeNode(n: ScreenNode): String {
        val flags = n.flags.filter { it != NodeFlag.ENABLED }.joinToString(",") { it.name.lowercase() }
        val b = n.bounds
        return "${n.id} ${n.role} '${n.label}' [$flags] ${b.left},${b.top},${b.right},${b.bottom}"
    }

    private fun describe(e: ActionEvent): String = when (e) {
        is ActionEvent.AttemptStarted -> "attempt ${e.attempt} ${e.strategy}"
        is ActionEvent.AttemptFailed -> "attempt ${e.attempt} ${e.strategy} failed: ${e.reason}"
        is ActionEvent.Succeeded -> "succeeded with ${e.strategy}"
        is ActionEvent.Rejected -> "rejected: ${e.reason}"
        is ActionEvent.ConfirmationRequested -> "confirmation requested: ${e.reason}"
        is ActionEvent.Declined -> "declined, or no answer in time"
    }

    private fun describeResult(r: ActionResult): String = when (r) {
        is ActionResult.Success -> "success via ${r.strategy}"
        is ActionResult.Failed -> "failed: ${r.reasons.joinToString("; ")}"
        is ActionResult.Rejected -> "rejected: ${r.reason}"
        ActionResult.Declined -> "declined"
    }

    companion object {
        const val EXTRA_CMD = "cmd"
        const val TIMEOUT_MS = 30_000L
        private const val NANOS_PER_MS = 1_000_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Volatile
        private var lastAnswer = "none"

        @Volatile
        private var debugSessionId: Long? = null

        @Volatile
        private var appContext: PinApp? = null
    }
}
