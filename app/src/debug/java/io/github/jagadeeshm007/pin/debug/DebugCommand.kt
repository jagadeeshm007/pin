package io.github.jagadeeshm007.pin.debug

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.action.GlobalNav

/** A command typed over adb in debug builds. Labels are resolved against the screen later. */
sealed interface DebugCommand {
    data object Dump : DebugCommand

    /** An action that needs no screen lookup. */
    data class Direct(val action: DeviceAction) : DebugCommand

    /** An action on the node whose label matches [label]. */
    data class OnLabel(val kind: Kind, val label: String, val text: String = "", val submit: Boolean = false) :
        DebugCommand

    enum class Kind { TAP, DOUBLE_TAP, LONG_PRESS, TYPE }

    companion object {
        /** Parses one command line; returns null with nothing to run. */
        fun parse(line: String): DebugCommand? {
            val trimmed = line.trim()
            val verb = trimmed.substringBefore(' ').lowercase()
            val rest = trimmed.substringAfter(' ', "").trim()
            return when (verb) {
                "dump" -> Dump

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

        private fun direction(rest: String): Direction? = Direction.entries.firstOrNull { it.name.equals(rest, true) }
    }
}
