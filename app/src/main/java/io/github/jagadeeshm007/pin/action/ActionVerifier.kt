package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/** Result of checking the screen after an action. */
sealed interface Verification {
    data object Passed : Verification

    data class Failed(val reason: String) : Verification
}

/**
 * Judges whether an action had its intended effect by comparing the screen before
 * and after (blueprint 6.4). Node ids are not stable across snapshots, so checks
 * use the content version, the window and the package, and for typing any editable
 * field's text.
 */
class ActionVerifier {
    /** Compares [before] and [after] for [action]. */
    fun verify(action: DeviceAction, before: ScreenSnapshot, after: ScreenSnapshot): Verification = when (action) {
        is DeviceAction.OpenApp ->
            if (after.packageName == action.packageName) {
                Verification.Passed
            } else {
                Verification.Failed("${action.packageName} is not in front (found ${after.packageName})")
            }

        is DeviceAction.TypeText -> typed(action, before, after)

        else -> anyChange(before, after)
    }

    private fun typed(action: DeviceAction.TypeText, before: ScreenSnapshot, after: ScreenSnapshot): Verification {
        val textPresent = after.nodes.any { it.has(NodeFlag.EDITABLE) && it.text == action.text }
        val movedOn =
            action.submit && (after.windowTitle != before.windowTitle || after.packageName != before.packageName)
        return when {
            textPresent || movedOn -> Verification.Passed
            action.submit -> Verification.Failed("typed text not found and the screen did not move on")
            else -> Verification.Failed("typed text not found in any editable field")
        }
    }

    private fun anyChange(before: ScreenSnapshot, after: ScreenSnapshot): Verification {
        val changed = after.contentVersion != before.contentVersion ||
            after.windowTitle != before.windowTitle ||
            after.packageName != before.packageName
        return if (changed) Verification.Passed else Verification.Failed("no change on screen after the action")
    }
}
