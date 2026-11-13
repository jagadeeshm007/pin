package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.core.screen.Bounds

/**
 * The parts of an accessibility node the agent reads. An interface so snapshot and
 * locator logic run in JVM tests; [AccessibilityRawNode] adapts the platform class.
 */
interface RawNode {
    val className: String?
    val text: String?
    val contentDescription: String?
    val hintText: String?
    val viewId: String?
    val bounds: Bounds
    val isVisible: Boolean
    val isClickable: Boolean
    val isLongClickable: Boolean
    val isScrollable: Boolean
    val isEditable: Boolean
    val isCheckable: Boolean
    val isChecked: Boolean
    val isFocused: Boolean
    val isEnabled: Boolean
    val isPassword: Boolean
    val childCount: Int

    /** The child at [index], or null when it disappeared. The caller releases it. */
    fun child(index: Int): RawNode?

    /** Frees platform resources for a node obtained from [child]. */
    fun release()
}
