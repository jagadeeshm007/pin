package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.GlobalNav

/** Test-only canonical text for a command, as written in `command-corpus.tsv`. */
fun Command?.canonical(): String = when (this) {
    null -> "none"

    is Command.Act -> when (val a = action) {
        is DeviceAction.Scroll -> "scroll ${a.direction.name.lowercase()}"

        is DeviceAction.Swipe -> "swipe ${a.direction.name.lowercase()}"

        is DeviceAction.Pinch -> if (a.zoomIn) "zoom in" else "zoom out"

        is DeviceAction.Global -> when (a.nav) {
            GlobalNav.BACK -> "back"
            GlobalNav.HOME -> "home"
            GlobalNav.RECENTS -> "recents"
            GlobalNav.NOTIFICATIONS -> "notifications"
            GlobalNav.QUICK_SETTINGS -> "quick settings"
        }

        else -> "act $a"
    }

    is Command.OnLabel -> when (kind) {
        Command.LabelKind.TAP -> "tap $label"
        Command.LabelKind.LONG_PRESS -> "long press $label"
        Command.LabelKind.DOUBLE_TAP -> "double tap $label"
    }

    is Command.TypeText -> "type ${text.lowercase()}"

    is Command.OpenApp -> "open $name"

    Command.ShowNumbers -> "numbers"

    Command.HideNumbers -> "hide numbers"

    Command.ShowGrid -> "grid"

    is Command.TapNumber -> "tap number $number"

    is Command.PickNumber -> "pick $number"

    Command.Stop -> "stop"
}
