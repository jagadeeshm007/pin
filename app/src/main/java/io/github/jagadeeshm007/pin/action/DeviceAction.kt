package io.github.jagadeeshm007.pin.action

/** Where an action lands: a node in the current snapshot, or a screen point. */
sealed interface TargetRef {
    /** A node id from the snapshot the action is validated against. */
    data class Node(val id: Int) : TargetRef

    /** Absolute screen coordinates in pixels. */
    data class Point(val x: Int, val y: Int) : TargetRef
}

/** Finger travel direction for swipes; content direction for scrolls. */
enum class Direction { UP, DOWN, LEFT, RIGHT }

/** System navigation the AccessibilityService can perform. */
enum class GlobalNav { BACK, HOME, RECENTS, NOTIFICATIONS, QUICK_SETTINGS }

/** How the device layer carries out an action. */
enum class ExecutionStrategy {
    /** An AccessibilityNodeInfo action such as ACTION_CLICK or ACTION_SET_TEXT. */
    NODE_ACTION,

    /** A dispatched gesture built from strokes. */
    GESTURE,

    /** A global action or an intent. */
    SYSTEM,
}

/**
 * The closed set of things the agent may do to the phone. Nothing outside this
 * hierarchy can reach the device (blueprint 6.2).
 */
sealed interface DeviceAction {
    /** Strategies in the order they are tried; the second is the fallback. */
    val strategies: List<ExecutionStrategy>

    data class Tap(val target: TargetRef) : DeviceAction {
        override val strategies = nodeThenGesture(target)
    }

    data class DoubleTap(val target: TargetRef) : DeviceAction {
        override val strategies = listOf(ExecutionStrategy.GESTURE)
    }

    data class LongPress(val target: TargetRef) : DeviceAction {
        override val strategies = nodeThenGesture(target)
    }

    /** [distance] is a fraction of the container (or window) size, in (0, 1]. */
    data class Swipe(val direction: Direction, val distance: Double = DEFAULT_DISTANCE, val containerId: Int? = null) :
        DeviceAction {
        override val strategies = listOf(ExecutionStrategy.GESTURE)
    }

    /** Scrolls the given container, or the largest scrollable node when null. */
    data class Scroll(val direction: Direction, val containerId: Int? = null) : DeviceAction {
        override val strategies = listOf(ExecutionStrategy.NODE_ACTION, ExecutionStrategy.GESTURE)
    }

    /** Pinch around [center], or the window center when null. */
    data class Pinch(val zoomIn: Boolean, val center: TargetRef? = null) : DeviceAction {
        override val strategies = listOf(ExecutionStrategy.GESTURE)
    }

    data class Global(val nav: GlobalNav) : DeviceAction {
        override val strategies = listOf(ExecutionStrategy.SYSTEM)
    }

    data class OpenApp(val packageName: String) : DeviceAction {
        override val strategies = listOf(ExecutionStrategy.SYSTEM)
    }

    /** Sets the text of an editable node; [submit] presses the IME action afterwards. */
    data class TypeText(val targetId: Int, val text: String, val submit: Boolean = false) : DeviceAction {
        override val strategies = listOf(ExecutionStrategy.NODE_ACTION)
    }

    companion object {
        const val DEFAULT_DISTANCE = 0.6

        private fun nodeThenGesture(target: TargetRef): List<ExecutionStrategy> = when (target) {
            is TargetRef.Node -> listOf(ExecutionStrategy.NODE_ACTION, ExecutionStrategy.GESTURE)
            is TargetRef.Point -> listOf(ExecutionStrategy.GESTURE)
        }
    }
}
