package io.github.jagadeeshm007.pin.core.screen

/** Screen rectangle in pixels; right and bottom are exclusive. */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    init {
        require(right >= left && bottom >= top) { "Invalid bounds $left,$top,$right,$bottom" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
    val isEmpty: Boolean get() = width == 0 || height == 0

    /** True when the point lies within these bounds. */
    fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom

    /** True when these bounds lie entirely within [other]. */
    fun isInside(other: Bounds): Boolean =
        left >= other.left && top >= other.top && right <= other.right && bottom <= other.bottom
}

/** Properties of an accessibility node that matter to actions and safety. */
enum class NodeFlag { CLICKABLE, LONG_CLICKABLE, SCROLLABLE, EDITABLE, CHECKABLE, CHECKED, FOCUSED, ENABLED, PASSWORD }

/**
 * One node of the pruned accessibility tree. [id] is valid only within the snapshot
 * that contains it. [label] is what a user would call it; [text] is the editable or
 * displayed text, kept separate so typing can be verified.
 */
data class ScreenNode(
    val id: Int,
    val role: String,
    val label: String,
    val bounds: Bounds,
    val flags: Set<NodeFlag> = emptySet(),
    val text: String? = null,
    val parentId: Int? = null,
) {
    /** True when the node carries [flag]. */
    fun has(flag: NodeFlag): Boolean = flag in flags
}

/**
 * The screen as the agent sees it at one moment. [contentVersion] increases each
 * time the accessibility service observes a content or window change, so two
 * snapshots can be compared without diffing every node.
 */
data class ScreenSnapshot(
    val packageName: String,
    val windowTitle: String,
    val windowBounds: Bounds,
    val nodes: List<ScreenNode>,
    val contentVersion: Long,
) {
    private val byId: Map<Int, ScreenNode> = nodes.associateBy { it.id }

    init {
        require(byId.size == nodes.size) { "Duplicate node ids in snapshot" }
    }

    /** The node with [id], or null when it is not on this screen. */
    fun node(id: Int): ScreenNode? = byId[id]
}
