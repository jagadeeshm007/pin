package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/** Outcome of checking an action against the current screen. */
sealed interface Validation {
    data object Valid : Validation

    data class Invalid(val reason: String) : Validation
}

/** Rejects actions that cannot or must not run on the current screen (spec AC-1 to AC-3). */
class ActionValidator {
    /** Checks [action] against [screen]; never touches the device. */
    fun validate(action: DeviceAction, screen: ScreenSnapshot): Validation {
        val reason = when (action) {
            is DeviceAction.Tap -> target(action.target, screen)
            is DeviceAction.DoubleTap -> target(action.target, screen)
            is DeviceAction.LongPress -> target(action.target, screen)
            is DeviceAction.Swipe -> swipe(action, screen)
            is DeviceAction.Scroll -> action.containerId?.let { container(it, screen, requireScrollable = true) }
            is DeviceAction.Pinch -> action.center?.let { target(it, screen) }
            is DeviceAction.Global -> null
            is DeviceAction.OpenApp -> packageName(action.packageName)
            is DeviceAction.TypeText -> typeText(action, screen)
        }
        return reason?.let { Validation.Invalid(it) } ?: Validation.Valid
    }

    private fun target(target: TargetRef, screen: ScreenSnapshot): String? = when (target) {
        is TargetRef.Node -> visibleNode(target.id, screen).second

        is TargetRef.Point ->
            if (screen.windowBounds.contains(target.x, target.y)) {
                null
            } else {
                "point ${target.x},${target.y} is outside the window"
            }
    }

    private fun visibleNode(id: Int, screen: ScreenSnapshot): Pair<ScreenNode?, String?> {
        val node = screen.node(id) ?: return null to "node $id is not on screen"
        if (node.bounds.isEmpty || !node.bounds.isInside(screen.windowBounds)) {
            return node to "node $id is outside the window"
        }
        return node to null
    }

    private fun container(id: Int, screen: ScreenSnapshot, requireScrollable: Boolean): String? {
        val (node, reason) = visibleNode(id, screen)
        if (reason != null) return reason
        if (requireScrollable && node?.has(NodeFlag.SCROLLABLE) != true) return "node $id is not scrollable"
        return null
    }

    private fun swipe(action: DeviceAction.Swipe, screen: ScreenSnapshot): String? {
        if (action.distance <= 0.0 || action.distance > 1.0) {
            return "swipe distance ${action.distance} must be in (0, 1]"
        }
        return action.containerId?.let { container(it, screen, requireScrollable = false) }
    }

    private fun typeText(action: DeviceAction.TypeText, screen: ScreenSnapshot): String? {
        val (node, reason) = visibleNode(action.targetId, screen)
        return when {
            reason != null -> reason
            node == null -> "node ${action.targetId} is not on screen"
            node.has(NodeFlag.PASSWORD) -> "node ${action.targetId} is a password field"
            !node.has(NodeFlag.EDITABLE) -> "node ${action.targetId} is not editable"
            action.text.isBlank() -> "text is empty"
            action.text.length > MAX_TEXT_LENGTH -> "text is too long (${action.text.length} > $MAX_TEXT_LENGTH)"
            else -> null
        }
    }

    private fun packageName(name: String): String? =
        if (PACKAGE_PATTERN.matches(name)) null else "invalid package name '$name'"

    companion object {
        const val MAX_TEXT_LENGTH = 2000
        private val PACKAGE_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    }
}
