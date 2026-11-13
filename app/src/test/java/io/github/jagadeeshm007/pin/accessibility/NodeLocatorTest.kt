package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class NodeLocatorTest {
    private val window = Bounds(0, 0, 1080, 2400)
    private val locator = NodeLocator()

    private val button = FakeRawNode(
        className = "android.widget.Button",
        text = "Search",
        isClickable = true,
        bounds = Bounds(900, 100, 1000, 200),
    )
    private val label =
        FakeRawNode(className = "android.widget.TextView", text = "Title", bounds = Bounds(0, 300, 500, 400))
    private val row = FakeRawNode(
        className = "android.widget.LinearLayout",
        isClickable = true,
        bounds = Bounds(0, 280, 1080, 420),
        children = listOf(label),
    )
    private val root = FakeRawNode(bounds = window, children = listOf(button, row))

    @Test
    fun findsNodeByRoleAndBounds() {
        val target = ScreenNode(1, "Button", "Search", Bounds(900, 100, 1000, 200))
        assertSame(button, locator.find(root, target, window, clickableAncestor = false))
    }

    @Test
    fun returnsNullWhenTheScreenMoved() {
        val target = ScreenNode(1, "Button", "Search", Bounds(900, 150, 1000, 250))
        assertNull(locator.find(root, target, window, clickableAncestor = false))
    }

    @Test
    fun climbsToClickableAncestorForTaps() {
        val target = ScreenNode(2, "TextView", "Title", Bounds(0, 300, 500, 400))
        assertSame(row, locator.find(root, target, window, clickableAncestor = true))
        assertSame(label, locator.find(root, target, window, clickableAncestor = false))
    }

    @Test
    fun matchesClippedBounds() {
        val tall =
            FakeRawNode(className = "android.widget.Button", isClickable = true, bounds = Bounds(0, 2300, 1080, 2600))
        val r = FakeRawNode(bounds = window, children = listOf(tall))
        val target = ScreenNode(1, "Button", "", Bounds(0, 2300, 1080, 2400))
        assertSame(tall, locator.find(r, target, window, clickableAncestor = false))
    }

    @Test
    fun releasesNodesItDoesNotReturn() {
        val found = locator.find(root, ScreenNode(2, "TextView", "Title", Bounds(0, 300, 500, 400)), window, false)
        assertSame(label, found)
        assertEquals(0, label.released, "the returned node is the caller's to release")
        assertEquals(1, button.released, "a visited sibling that did not match")
        assertEquals(1, row.released, "an ancestor on the path")
        assertEquals(0, root.released, "the root belongs to the caller")
    }
}
