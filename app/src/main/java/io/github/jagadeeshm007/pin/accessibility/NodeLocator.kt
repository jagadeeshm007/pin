package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.ScreenNode

/**
 * Finds the live node that a snapshot node describes, in a fresh tree, by role and
 * window-clipped bounds. Re-finding at perform time means no platform node is held
 * between calls (android rules). Returns null when the screen has moved, so the
 * orchestrator falls back to a gesture.
 */
class NodeLocator {
    /**
     * Searches below [root]. With [clickableAncestor], returns the nearest clickable
     * node on the path to the match (a tap on a label inside a row taps the row).
     * The caller releases the returned node unless it is [root].
     */
    fun find(root: RawNode, target: ScreenNode, window: Bounds, clickableAncestor: Boolean): RawNode? {
        val path = ArrayDeque<RawNode>()
        val found = search(root, target, window, path, depth = 0) ?: return null
        val chosen = if (clickableAncestor && !found.isClickable) {
            path.lastOrNull { it.isClickable } ?: found
        } else {
            found
        }
        (path + found).filter { it !== chosen && it !== root }.forEach { it.release() }
        return chosen
    }

    /** Depth-first search; [path] holds the ancestors of the returned match, root first. */
    private fun search(
        node: RawNode,
        target: ScreenNode,
        window: Bounds,
        path: ArrayDeque<RawNode>,
        depth: Int,
    ): RawNode? {
        if (depth > SnapshotBuilder.MAX_DEPTH || !node.isVisible) return null
        if (matches(node, target, window)) return node
        path.addLast(node)
        for (i in 0 until node.childCount) {
            val child = node.child(i) ?: continue
            val hit = search(child, target, window, path, depth + 1)
            if (hit != null) return hit
            child.release()
        }
        path.removeLast()
        return null
    }

    private fun matches(node: RawNode, target: ScreenNode, window: Bounds): Boolean {
        val role = node.className?.substringAfterLast('.') ?: "View"
        return role == target.role && clip(node.bounds, window) == target.bounds
    }

    private fun clip(b: Bounds, w: Bounds): Bounds? {
        val l = maxOf(b.left, w.left)
        val t = maxOf(b.top, w.top)
        val r = minOf(b.right, w.right)
        val btm = minOf(b.bottom, w.bottom)
        return if (r > l && btm > t) Bounds(l, t, r, btm) else null
    }
}
