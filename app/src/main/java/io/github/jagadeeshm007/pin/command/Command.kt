package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.action.DeviceAction

/**
 * One spoken command after parsing (spec PIN-30). Commands that name something on the
 * screen or an app are resolved when they run, against the screen of that moment.
 */
sealed interface Command {
    /**
     * True when the command has no free-text argument, so a stable partial transcript
     * can run it before the final arrives (spec PIN-30 AC-5).
     */
    val earlyCommit: Boolean get() = false

    /** An action that needs nothing from the screen: scroll, swipe, back, zoom. */
    data class Act(val action: DeviceAction) : Command {
        override val earlyCommit get() = true
    }

    enum class LabelKind { TAP, LONG_PRESS, DOUBLE_TAP }

    /** A node named by its visible label. */
    data class OnLabel(val kind: LabelKind, val label: String) : Command

    /** Text for the focused editable field, with the spoken case kept. */
    data class TypeText(val text: String) : Command

    /** A launcher app by its label. */
    data class OpenApp(val name: String) : Command

    data object ShowNumbers : Command {
        override val earlyCommit get() = true
    }

    data object HideNumbers : Command {
        override val earlyCommit get() = true
    }

    data object ShowGrid : Command {
        override val earlyCommit get() = true
    }

    data class TapNumber(val number: Int) : Command

    /** A bare number, answering "Say a number" (spec PIN-31). */
    data class PickNumber(val number: Int) : Command

    /** Stops the running task. */
    data object Stop : Command {
        override val earlyCommit get() = true
    }
}
