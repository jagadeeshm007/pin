package io.github.jagadeeshm007.pin.accessibility

import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import io.github.jagadeeshm007.pin.core.screen.Bounds

/** [RawNode] over a platform node. Lives only for one snapshot or one action. */
class AccessibilityRawNode(val info: AccessibilityNodeInfo) : RawNode {
    override val className: String? get() = info.className?.toString()
    override val text: String? get() = info.text?.toString()
    override val contentDescription: String? get() = info.contentDescription?.toString()
    override val hintText: String? get() = info.hintText?.toString()
    override val viewId: String? get() = info.viewIdResourceName
    override val bounds: Bounds get() = Rect().also { info.getBoundsInScreen(it) }.toBounds()
    override val isVisible: Boolean get() = info.isVisibleToUser
    override val isClickable: Boolean get() = info.isClickable
    override val isLongClickable: Boolean get() = info.isLongClickable
    override val isScrollable: Boolean get() = info.isScrollable
    override val isEditable: Boolean get() = info.isEditable
    override val isCheckable: Boolean get() = info.isCheckable
    override val isChecked: Boolean get() = info.isChecked
    override val isFocused: Boolean get() = info.isFocused
    override val isEnabled: Boolean get() = info.isEnabled
    override val isPassword: Boolean get() = info.isPassword
    override val childCount: Int get() = info.childCount

    override fun child(index: Int): RawNode? = info.getChild(index)?.let(::AccessibilityRawNode)

    override fun release() = releaseNode(info)

    companion object {
        /** recycle() matters below API 33 and is a no-op from 33 (android rules); deprecated there. */
        fun releaseNode(info: AccessibilityNodeInfo) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) info.recycle()
        }
    }
}

/** Converts a platform rect, tolerating the inverted rects some apps report. */
fun Rect.toBounds(): Bounds = Bounds(left, top, maxOf(left, right), maxOf(top, bottom))
