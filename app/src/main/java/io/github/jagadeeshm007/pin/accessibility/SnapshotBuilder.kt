package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/**
 * Turns an accessibility tree into a pruned [ScreenSnapshot] (blueprint 6.1).
 * Keeps visible nodes that are actionable or carry a label, clips them to the
 * window, assigns snapshot-scoped ids in depth-first order, and never copies the
 * text of password fields.
 */
class SnapshotBuilder {
    /** Builds a snapshot from [root]; releases every child it visits but not [root]. */
    fun build(
        root: RawNode,
        packageName: String,
        windowTitle: String,
        windowBounds: Bounds,
        contentVersion: Long,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): ScreenSnapshot {
        val out = mutableListOf<ScreenNode>()
        visit(root, parentId = null, depth = 0, windowBounds, maxNodes, out)
        return ScreenSnapshot(packageName, windowTitle, windowBounds, out, contentVersion)
    }

    private fun visit(
        node: RawNode,
        parentId: Int?,
        depth: Int,
        window: Bounds,
        maxNodes: Int,
        out: MutableList<ScreenNode>,
    ) {
        if (out.size >= maxNodes || depth > MAX_DEPTH || !node.isVisible) return
        val clipped = clip(node.bounds, window)
        var nextParent = parentId
        if (clipped != null && keep(node)) {
            val kept = toScreenNode(node, id = out.size + 1, clipped, parentId)
            out += kept
            nextParent = kept.id
        }
        for (i in 0 until node.childCount) {
            val child = node.child(i) ?: continue
            try {
                visit(child, nextParent, depth + 1, window, maxNodes, out)
            } finally {
                child.release()
            }
            if (out.size >= maxNodes) return
        }
    }

    private fun keep(node: RawNode): Boolean = isActionable(node) || label(node).isNotBlank()

    private fun isActionable(node: RawNode): Boolean =
        node.isClickable || node.isLongClickable || node.isScrollable || node.isEditable || node.isCheckable

    private fun toScreenNode(node: RawNode, id: Int, bounds: Bounds, parentId: Int?): ScreenNode = ScreenNode(
        id = id,
        role = node.className?.substringAfterLast('.')?.takeIf { it.isNotBlank() } ?: "View",
        label = label(node),
        bounds = bounds,
        flags = flags(node),
        text = if (node.isEditable && !node.isPassword) node.text else null,
        parentId = parentId,
    )

    private fun label(node: RawNode): String {
        val editable = node.isEditable
        val candidates = sequenceOf(
            node.contentDescription,
            if (editable) node.hintText else node.text,
            if (editable) null else node.hintText,
            node.viewId?.substringAfterLast('/')?.replace('_', ' '),
        )
        val chosen = candidates.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
        return chosen.take(MAX_LABEL_LENGTH)
    }

    private fun flags(node: RawNode): Set<NodeFlag> = buildSet {
        if (node.isClickable) add(NodeFlag.CLICKABLE)
        if (node.isLongClickable) add(NodeFlag.LONG_CLICKABLE)
        if (node.isScrollable) add(NodeFlag.SCROLLABLE)
        if (node.isEditable) add(NodeFlag.EDITABLE)
        if (node.isCheckable) add(NodeFlag.CHECKABLE)
        if (node.isChecked) add(NodeFlag.CHECKED)
        if (node.isFocused) add(NodeFlag.FOCUSED)
        if (node.isEnabled) add(NodeFlag.ENABLED)
        if (node.isPassword) add(NodeFlag.PASSWORD)
    }

    private fun clip(bounds: Bounds, window: Bounds): Bounds? {
        val left = maxOf(bounds.left, window.left)
        val top = maxOf(bounds.top, window.top)
        val right = minOf(bounds.right, window.right)
        val bottom = minOf(bounds.bottom, window.bottom)
        if (right <= left || bottom <= top) return null
        return Bounds(left, top, right, bottom)
    }

    companion object {
        const val DEFAULT_MAX_NODES = 200
        const val MAX_DEPTH = 60
        const val MAX_LABEL_LENGTH = 200
    }
}
