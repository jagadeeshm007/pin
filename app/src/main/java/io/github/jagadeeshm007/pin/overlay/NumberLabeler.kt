package io.github.jagadeeshm007.pin.overlay

import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/** One badge: the number shown and the node it stands for. */
data class NumberedNode(val number: Int, val nodeId: Int, val role: String, val bounds: Bounds)

/**
 * The numbers shown on screen. Node ids are snapshot-scoped, so [resolve] finds the
 * same node in a later snapshot by role and bounds, and refuses when it has moved.
 */
data class NumberMap(val entries: List<NumberedNode>) {
    /** The target for [number] on [current], or null when it was not shown or has moved. */
    fun resolve(number: Int, current: ScreenSnapshot): TargetRef? {
        val entry = entries.firstOrNull { it.number == number } ?: return null
        val node = current.nodes.firstOrNull { it.role == entry.role && it.bounds == entry.bounds } ?: return null
        return TargetRef.Node(node.id)
    }
}

/** Chooses and orders the nodes that get number badges ("show numbers", blueprint 6.1). */
class NumberLabeler {
    /** Labels actionable nodes of [screen], top to bottom then left to right. */
    fun label(screen: ScreenSnapshot): NumberMap {
        val candidates = screen.nodes
            .filter { isActionable(it) && !it.bounds.isEmpty }
            .distinctBy { it.bounds }
        val leaves = candidates.filter { outer ->
            candidates.none { inner -> inner !== outer && contains(outer, inner) }
        }
        val ordered = leaves.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left })).take(MAX_LABELS)
        return NumberMap(ordered.mapIndexed { i, n -> NumberedNode(i + 1, n.id, n.role, n.bounds) })
    }

    private fun isActionable(node: ScreenNode): Boolean = node.has(NodeFlag.CLICKABLE) ||
        node.has(NodeFlag.LONG_CLICKABLE) ||
        node.has(NodeFlag.EDITABLE) ||
        node.has(NodeFlag.CHECKABLE)

    private fun contains(outer: ScreenNode, inner: ScreenNode): Boolean =
        inner.bounds.isInside(outer.bounds) && inner.bounds != outer.bounds

    companion object {
        const val MAX_LABELS = 99
    }
}
