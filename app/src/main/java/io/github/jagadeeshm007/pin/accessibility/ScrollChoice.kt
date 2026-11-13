package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.action.Direction

/**
 * Decides whether a scrollable node can scroll in a direction, and with which action.
 * Forward and backward have no axis: on a sideways pager "forward" is the next page, so
 * "scroll down" there switched WhatsApp tabs (device report, PIN-30). They are used only
 * when the node's axis, guessed from its class and collection shape, matches.
 */
object ScrollChoice {
    enum class Use { DIRECTIONAL, FORWARD_BACKWARD }

    /**
     * How to scroll [direction] on a node offering the directional actions [offered], of
     * class [className], with a collection of [rows] by [columns] (0 when unknown); null
     * when this node does not scroll that way.
     */
    fun pick(direction: Direction, offered: Set<Direction>, className: String, rows: Int, columns: Int): Use? {
        if (offered.isNotEmpty()) return Use.DIRECTIONAL.takeIf { direction in offered }
        val horizontal = looksHorizontal(className) || (rows == 1 && columns > 1)
        val sideways = direction == Direction.LEFT || direction == Direction.RIGHT
        return Use.FORWARD_BACKWARD.takeIf { horizontal == sideways }
    }

    /** Classes that scroll sideways. */
    fun looksHorizontal(className: String): Boolean = HORIZONTAL.any { className.contains(it, ignoreCase = true) }

    private val HORIZONTAL = listOf("ViewPager", "HorizontalScrollView", "Carousel", "TabLayout")
}
