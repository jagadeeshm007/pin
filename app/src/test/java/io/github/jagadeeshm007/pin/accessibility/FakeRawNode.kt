package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.core.screen.Bounds

/** In-memory tree for builder and locator tests. */
data class FakeRawNode(
    override val className: String? = "android.view.View",
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val hintText: String? = null,
    override val viewId: String? = null,
    override val bounds: Bounds = Bounds(0, 0, 100, 100),
    override val isVisible: Boolean = true,
    override val isClickable: Boolean = false,
    override val isLongClickable: Boolean = false,
    override val isScrollable: Boolean = false,
    override val isEditable: Boolean = false,
    override val isCheckable: Boolean = false,
    override val isChecked: Boolean = false,
    override val isFocused: Boolean = false,
    override val isEnabled: Boolean = true,
    override val isPassword: Boolean = false,
    val children: List<FakeRawNode> = emptyList(),
) : RawNode {
    var released = 0
        private set

    override val childCount: Int get() = children.size

    override fun child(index: Int): RawNode? = children.getOrNull(index)

    override fun release() {
        released++
    }
}
