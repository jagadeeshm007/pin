package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/** Whether an action may run directly or needs the user's confirmation first. */
sealed interface Sensitivity {
    data object None : Sensitivity

    data class Confirm(val reason: String) : Sensitivity
}

/**
 * Decides which actions need explicit confirmation (blueprint 7.6). This gate lives
 * in code so no prompt or command can skip it.
 *
 * Rules, in order:
 * 1. Any input (tap, long press, typing) in a sensitive app.
 * 2. Any input on a Settings screen about security, privacy, accessibility or device admin.
 * 3. Tapping or long-pressing a node whose label contains a sensitive word.
 * Navigation, scrolling, swiping and opening apps never need confirmation.
 */
class SensitivityPolicy(private val sensitivePackages: Set<String> = DEFAULT_SENSITIVE_PACKAGES) {
    /** Assesses [action] on [screen]. */
    fun assess(action: DeviceAction, screen: ScreenSnapshot): Sensitivity {
        if (!isInput(action)) return Sensitivity.None
        if (screen.packageName in sensitivePackages) {
            return Sensitivity.Confirm("input in sensitive app ${screen.packageName}")
        }
        if (screen.packageName == SETTINGS_PACKAGE && SENSITIVE_SETTINGS.containsMatchIn(screen.windowTitle)) {
            return Sensitivity.Confirm("change on settings screen '${screen.windowTitle}'")
        }
        val label = targetLabel(action, screen) ?: return Sensitivity.None
        return if (SENSITIVE_WORDS.containsMatchIn(label)) {
            Sensitivity.Confirm("target '$label' looks sensitive")
        } else {
            Sensitivity.None
        }
    }

    private fun isInput(action: DeviceAction): Boolean = when (action) {
        is DeviceAction.Tap, is DeviceAction.DoubleTap, is DeviceAction.LongPress, is DeviceAction.TypeText -> true

        is DeviceAction.Swipe, is DeviceAction.Scroll, is DeviceAction.Pinch,
        is DeviceAction.Global, is DeviceAction.OpenApp,
        -> false
    }

    private fun targetLabel(action: DeviceAction, screen: ScreenSnapshot): String? {
        val target = when (action) {
            is DeviceAction.Tap -> action.target
            is DeviceAction.DoubleTap -> action.target
            is DeviceAction.LongPress -> action.target
            else -> return null
        }
        return (target as? TargetRef.Node)?.let { screen.node(it.id)?.label }
    }

    companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"

        /** Payment and wallet apps; user-editable later (spec Q1). */
        val DEFAULT_SENSITIVE_PACKAGES = setOf(
            "com.google.android.apps.walletnfcrel",
            "com.google.android.apps.nbu.paisa.user",
            "com.phonepe.app",
            "net.one97.paytm",
        )

        private val SENSITIVE_WORDS = Regex(
            "\\b(pay|payment|send|transfer|buy|purchase|order|checkout|delete|remove|uninstall|reset|erase|wipe|" +
                "allow|grant|confirm|post|publish|share|subscribe|unsubscribe|block|report|sign out|log out)\\b",
            RegexOption.IGNORE_CASE,
        )

        private val SENSITIVE_SETTINGS = Regex(
            "security|privacy|accessibility|device admin|password|lock screen|biometric",
            RegexOption.IGNORE_CASE,
        )
    }
}
