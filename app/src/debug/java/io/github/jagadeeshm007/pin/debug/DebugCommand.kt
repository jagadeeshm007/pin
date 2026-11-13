package io.github.jagadeeshm007.pin.debug

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.action.GlobalNav
import io.github.jagadeeshm007.pin.notification.TaskPhase
import io.github.jagadeeshm007.pin.notification.TaskStatus
import io.github.jagadeeshm007.pin.overlay.OverlayState

/** A command typed over adb in debug builds. Labels are resolved against the screen later. */
sealed interface DebugCommand {
    data object Dump : DebugCommand

    /** An action that needs no screen lookup. */
    data class Direct(val action: DeviceAction) : DebugCommand

    /** An action on the node whose label matches [label]. */
    data class OnLabel(val kind: Kind, val label: String, val text: String = "", val submit: Boolean = false) :
        DebugCommand

    enum class Kind { TAP, DOUBLE_TAP, LONG_PRESS, TYPE }

    /** Shows the pill in [state], with an optional detail. */
    data class Overlay(val state: OverlayState, val detail: String?) : DebugCommand

    /** Hides the pill and any numbers or grid. */
    data object Hide : DebugCommand

    /** Shows number badges on the current screen. */
    data object Numbers : DebugCommand

    /** Taps the node shown with [number]. */
    data class TapNumber(val number: Int) : DebugCommand

    /** Shows a 3 by 3 grid over the window. */
    data object Grid : DebugCommand

    /** Taps the centre of grid cell [number]. */
    data class GridPick(val number: Int) : DebugCommand

    /** Replaces the grid with a finer grid over cell [number]. */
    data class GridZoom(val number: Int) : DebugCommand

    /** Posts [status] to the task notification. */
    data class Notify(val status: TaskStatus) : DebugCommand

    /** Removes the task notification. */
    data object NotifyClear : DebugCommand

    /** Starts a confirmation in the background and returns at once. */
    data class Ask(val question: String) : DebugCommand

    /** Reports how the last [Ask] was answered. */
    data object LastAnswer : DebugCommand

    companion object {
        /** Parses one command line; returns null with nothing to run. */
        fun parse(line: String): DebugCommand? {
            val trimmed = line.trim()
            val verb = trimmed.substringBefore(' ').lowercase()
            val rest = trimmed.substringAfter(' ', "").trim()
            return when (verb) {
                "dump" -> Dump

                "overlay" -> overlay(rest)

                "hide" -> Hide

                "numbers" -> Numbers

                "tapnum" -> rest.toIntOrNull()?.let { TapNumber(it) }

                "grid" -> Grid

                "gridpick" -> rest.toIntOrNull()?.let { GridPick(it) }

                "gridzoom" -> rest.toIntOrNull()?.let { GridZoom(it) }

                "notify" -> notify(rest)

                "ask" -> rest.takeIf { it.isNotEmpty() }?.let { Ask(it) }

                "answer" -> LastAnswer

                "tap" -> label(Kind.TAP, rest)

                "doubletap" -> label(Kind.DOUBLE_TAP, rest)

                "longpress" -> label(Kind.LONG_PRESS, rest)

                "type", "typego" -> typing(rest, submit = verb == "typego")

                "scroll" -> direction(rest)?.let { Direct(DeviceAction.Scroll(it)) }

                "swipe" -> direction(rest)?.let { Direct(DeviceAction.Swipe(it)) }

                "pinch" -> when (rest.lowercase()) {
                    "out" -> Direct(DeviceAction.Pinch(zoomIn = true))
                    "in" -> Direct(DeviceAction.Pinch(zoomIn = false))
                    else -> null
                }

                "open" -> rest.takeIf { it.isNotEmpty() }?.let { Direct(DeviceAction.OpenApp(it)) }

                "back" -> Direct(DeviceAction.Global(GlobalNav.BACK))

                "home" -> Direct(DeviceAction.Global(GlobalNav.HOME))

                "recents" -> Direct(DeviceAction.Global(GlobalNav.RECENTS))

                "notifications" -> Direct(DeviceAction.Global(GlobalNav.NOTIFICATIONS))

                "quicksettings" -> Direct(DeviceAction.Global(GlobalNav.QUICK_SETTINGS))

                else -> null
            }
        }

        private fun label(kind: Kind, rest: String) = rest.takeIf { it.isNotEmpty() }?.let { OnLabel(kind, it) }

        private fun typing(rest: String, submit: Boolean): DebugCommand? {
            val label = rest.substringBefore('=', "").trim()
            val text = rest.substringAfter('=', "")
            if (label.isEmpty() || text.isEmpty()) return null
            return OnLabel(Kind.TYPE, label, text, submit)
        }

        private fun notify(rest: String): DebugCommand? {
            val words = rest.split(' ').filter { it.isNotEmpty() }
            val first = words.firstOrNull()?.lowercase() ?: return null
            if (first == "clear") return NotifyClear
            if (first == "step") {
                val step = words.getOrNull(1)?.toIntOrNull() ?: return null
                val total = words.getOrNull(2)?.toIntOrNull() ?: return null
                return Notify(TaskStatus(TaskPhase.ACTING, words.drop(3).joinToString(" "), step, total))
            }
            val phase = TaskPhase.entries.firstOrNull { it.name.equals(first, ignoreCase = true) } ?: return null
            return Notify(TaskStatus(phase, words.drop(1).joinToString(" ")))
        }

        private fun overlay(rest: String): DebugCommand? {
            val name = rest.substringBefore(' ')
            val state = OverlayState.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
            val detail = rest.substringAfter(' ', "").trim().ifEmpty { null }
            return Overlay(state, detail)
        }

        private fun direction(rest: String): Direction? = Direction.entries.firstOrNull { it.name.equals(rest, true) }
    }
}
