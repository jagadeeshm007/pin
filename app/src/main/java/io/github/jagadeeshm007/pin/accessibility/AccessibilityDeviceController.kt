package io.github.jagadeeshm007.pin.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.DeviceController
import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.action.ExecutionStrategy
import io.github.jagadeeshm007.pin.action.GlobalNav
import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Carries out validated actions with the accessibility APIs. Only the orchestrator
 * calls it (agent safety rules). Node actions re-find their node in a fresh tree;
 * gestures use snapshot bounds; system actions use global actions or intents.
 */
class AccessibilityDeviceController(
    private val service: PinAccessibilityService,
    private val gestures: GestureDispatcher = GestureDispatcher(service),
    private val locator: NodeLocator = NodeLocator(),
) : DeviceController {
    override suspend fun perform(action: DeviceAction, screen: ScreenSnapshot, strategy: ExecutionStrategy): Boolean =
        when (strategy) {
            ExecutionStrategy.NODE_ACTION -> withContext(Dispatchers.Default) { nodeAction(action, screen) }
            ExecutionStrategy.GESTURE -> gesture(action, screen)
            ExecutionStrategy.SYSTEM -> withContext(Dispatchers.Main) { system(action) }
        }

    private fun nodeAction(action: DeviceAction, screen: ScreenSnapshot): Boolean = when (action) {
        is DeviceAction.Tap -> nodeTarget(action.target, screen)?.let { act(it, screen, true) { n -> n.click() } }

        is DeviceAction.LongPress ->
            nodeTarget(action.target, screen)?.let { act(it, screen, true) { n -> n.longClick() } }

        // Largest first; a container that does not move this way is skipped (ScrollChoice).
        is DeviceAction.Scroll -> scrollables(action.containerId, screen).any {
            act(it, screen, false) { n -> n.scroll(action.direction) }
        }

        is DeviceAction.TypeText ->
            screen.node(action.targetId)?.let { act(it, screen, false) { n -> n.setText(action.text, action.submit) } }

        else -> null
    } ?: false

    private suspend fun gesture(action: DeviceAction, screen: ScreenSnapshot): Boolean {
        val planner = GesturePlanner(
            window = displayBounds(),
            maxDurationMs = GestureDescription.getMaxGestureDuration(),
            maxStrokes = GestureDescription.getMaxStrokeCount(),
        )
        val strokes = when (action) {
            is DeviceAction.Tap -> point(action.target, screen)?.let { (x, y) -> planner.tap(x, y) }

            is DeviceAction.DoubleTap -> point(action.target, screen)?.let { (x, y) -> planner.doubleTap(x, y) }

            is DeviceAction.LongPress -> point(action.target, screen)?.let { (x, y) -> planner.longPress(x, y) }

            is DeviceAction.Swipe ->
                planner.swipe(action.direction, areaOf(action.containerId, screen), action.distance)

            is DeviceAction.Scroll ->
                planner.scroll(
                    action.direction,
                    gestureArea(action.containerId, action.direction, screen),
                )

            is DeviceAction.Pinch -> {
                val (x, y) = action.center?.let { point(it, screen) }
                    ?: (screen.windowBounds.centerX to screen.windowBounds.centerY)
                planner.pinch(action.zoomIn, x, y)
            }

            else -> null
        } ?: return false
        return gestures.dispatch(strokes)
    }

    private fun system(action: DeviceAction): Boolean = when (action) {
        is DeviceAction.Global -> service.performGlobalAction(globalAction(action.nav))
        is DeviceAction.OpenApp -> openApp(action.packageName)
        else -> false
    }

    private fun openApp(packageName: String): Boolean {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        service.startActivity(intent)
        return true
    }

    /** Finds the live node for [target] in a fresh tree, runs [block], releases everything. */
    private fun act(
        target: ScreenNode,
        screen: ScreenSnapshot,
        clickableAncestor: Boolean,
        block: (AccessibilityNodeInfo) -> Boolean,
    ): Boolean {
        val root = service.rootInActiveWindow ?: return false
        try {
            val found = locator.find(AccessibilityRawNode(root), target, screen.windowBounds, clickableAncestor)
                as? AccessibilityRawNode ?: return false
            try {
                return block(found.info)
            } finally {
                if (found.info !== root) found.release()
            }
        } finally {
            AccessibilityRawNode.releaseNode(root)
        }
    }

    private fun nodeTarget(target: TargetRef, screen: ScreenSnapshot): ScreenNode? =
        (target as? TargetRef.Node)?.let { screen.node(it.id) }

    private fun point(target: TargetRef, screen: ScreenSnapshot): Pair<Int, Int>? = when (target) {
        is TargetRef.Point -> target.x to target.y
        is TargetRef.Node -> screen.node(target.id)?.bounds?.let { it.centerX to it.centerY }
    }

    private fun areaOf(containerId: Int?, screen: ScreenSnapshot): Bounds =
        containerId?.let { screen.node(it)?.bounds } ?: screen.windowBounds

    /** The named container, or every scrollable node, largest first. */
    private fun scrollables(containerId: Int?, screen: ScreenSnapshot): List<ScreenNode> =
        containerId?.let { listOfNotNull(screen.node(it)) }
            ?: screen.nodes.filter { it.has(NodeFlag.SCROLLABLE) }.sortedByDescending {
                it.bounds.width *
                    it.bounds.height
            }

    /** Where to swipe: the largest scrollable on the right axis, else the window. */
    private fun gestureArea(containerId: Int?, direction: Direction, screen: ScreenSnapshot): Bounds {
        val sideways = direction == Direction.LEFT || direction == Direction.RIGHT
        return scrollables(containerId, screen)
            .firstOrNull { containerId != null || ScrollChoice.looksHorizontal(it.role) == sideways }
            ?.bounds ?: screen.windowBounds
    }

    private fun displayBounds(): Bounds {
        val wm = service.getSystemService(WindowManager::class.java)
        return wm.currentWindowMetrics.bounds.toBounds()
    }

    private fun globalAction(nav: GlobalNav): Int = when (nav) {
        GlobalNav.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
        GlobalNav.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
        GlobalNav.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        GlobalNav.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        GlobalNav.QUICK_SETTINGS -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
    }
}

private fun AccessibilityNodeInfo.click(): Boolean = performAction(AccessibilityNodeInfo.ACTION_CLICK)

private fun AccessibilityNodeInfo.longClick(): Boolean = performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)

private val DIRECTIONAL = mapOf(
    Direction.DOWN to AccessibilityAction.ACTION_SCROLL_DOWN,
    Direction.UP to AccessibilityAction.ACTION_SCROLL_UP,
    Direction.LEFT to AccessibilityAction.ACTION_SCROLL_LEFT,
    Direction.RIGHT to AccessibilityAction.ACTION_SCROLL_RIGHT,
)

/** Scrolls [direction] if this node moves that way (see [ScrollChoice]); false if not. */
private fun AccessibilityNodeInfo.scroll(direction: Direction): Boolean {
    val ids = actionList.map { it.id }.toSet()
    val offered = DIRECTIONAL.filterValues { it.id in ids }.keys
    val collection = collectionInfo
    val use = ScrollChoice.pick(
        direction,
        offered,
        className?.toString().orEmpty(),
        collection?.rowCount ?: 0,
        collection?.columnCount ?: 0,
    ) ?: return false
    return when (use) {
        ScrollChoice.Use.DIRECTIONAL -> performAction(DIRECTIONAL.getValue(direction).id)

        ScrollChoice.Use.FORWARD_BACKWARD -> performAction(
            if (direction == Direction.DOWN || direction == Direction.RIGHT) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            },
        )
    }
}

private fun AccessibilityNodeInfo.setText(text: String, submit: Boolean): Boolean {
    performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    val args = Bundle().apply {
        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
    }
    if (!performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
    return !submit || performAction(AccessibilityAction.ACTION_IME_ENTER.id)
}
