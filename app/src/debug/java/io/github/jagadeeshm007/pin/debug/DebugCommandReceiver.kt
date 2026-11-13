package io.github.jagadeeshm007.pin.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.jagadeeshm007.pin.accessibility.AccessibilityDeviceController
import io.github.jagadeeshm007.pin.accessibility.AccessibilityRegistry
import io.github.jagadeeshm007.pin.accessibility.AccessibilityScreenSource
import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.action.ActionOrchestrator
import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Debug builds only. Runs one command through the real orchestrator so device
 * behaviour can be tested from adb before voice exists. Sensitive actions are always
 * declined here because there is no confirmation UI yet.
 *
 * adb shell am broadcast -n io.github.jagadeeshm007.pin.debug/io.github.jagadeeshm007.pin.debug.DebugCommandReceiver \
 *   -a io.github.jagadeeshm007.pin.debug.COMMAND --es cmd "tap Search"
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val line = intent.getStringExtra(EXTRA_CMD).orEmpty()
        val pending = goAsync()
        scope.launch {
            val output = try {
                withTimeout(TIMEOUT_MS) { run(line) }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                "error: timed out after $TIMEOUT_MS ms (${e.message})"
            }
            pending.resultData = output
            pending.finish()
        }
    }

    private suspend fun run(line: String): String {
        val service = AccessibilityRegistry.service.value ?: return "error: accessibility service is off"
        val command = DebugCommand.parse(line) ?: return "error: cannot parse '$line'"
        val screen = AccessibilityScreenSource(service)
        if (command == DebugCommand.Dump) return dump(screen.current())

        val events = mutableListOf<ActionEvent>()
        val orchestrator = ActionOrchestrator(
            device = AccessibilityDeviceController(service),
            screen = screen,
            confirmer = { _, _ -> false },
            recorder = { events += it },
        )
        val action = when (command) {
            is DebugCommand.Direct -> command.action

            is DebugCommand.OnLabel -> resolve(command, screen.current())
                ?: return "error: no node labelled '${command.label}'"

            DebugCommand.Dump -> return "unreachable"
        }
        val started = System.nanoTime()
        val result = orchestrator.execute(action)
        val ms = (System.nanoTime() - started) / NANOS_PER_MS
        val trail = events.joinToString("\n") { "  ${describe(it)}" }
        return "action: $action\nresult: ${describeResult(result)}\nelapsed: $ms ms\nevents:\n$trail"
    }

    private fun resolve(command: DebugCommand.OnLabel, screen: ScreenSnapshot): DeviceAction? {
        val wantEditable = command.kind == DebugCommand.Kind.TYPE
        val candidates = screen.nodes.filter { !wantEditable || it.has(NodeFlag.EDITABLE) }
        val node = candidates.firstOrNull { it.label.equals(command.label, ignoreCase = true) }
            ?: candidates.firstOrNull { it.label.contains(command.label, ignoreCase = true) }
            ?: return null
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
        is ActionEvent.Declined -> "declined (debug receiver never confirms)"
    }

    private fun describeResult(r: io.github.jagadeeshm007.pin.action.ActionResult): String = when (r) {
        is io.github.jagadeeshm007.pin.action.ActionResult.Success -> "success via ${r.strategy}"
        is io.github.jagadeeshm007.pin.action.ActionResult.Failed -> "failed: ${r.reasons.joinToString("; ")}"
        is io.github.jagadeeshm007.pin.action.ActionResult.Rejected -> "rejected: ${r.reason}"
        io.github.jagadeeshm007.pin.action.ActionResult.Declined -> "declined"
    }

    companion object {
        const val EXTRA_CMD = "cmd"
        const val TIMEOUT_MS = 8_000L
        private const val NANOS_PER_MS = 1_000_000L
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
