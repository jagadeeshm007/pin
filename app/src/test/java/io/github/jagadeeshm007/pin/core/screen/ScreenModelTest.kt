package io.github.jagadeeshm007.pin.core.screen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ScreenModelTest {
    @Test
    fun boundsGeometry() {
        val b = Bounds(10, 20, 110, 220)
        assertEquals(100, b.width)
        assertEquals(200, b.height)
        assertEquals(60, b.centerX)
        assertEquals(120, b.centerY)
        assertTrue(b.contains(10, 20))
        assertFalse(b.contains(110, 220))
        assertFalse(b.isEmpty)
        assertTrue(Bounds(5, 5, 5, 9).isEmpty)
    }

    @Test
    fun boundsInsideOther() {
        val window = Bounds(0, 0, 1080, 2400)
        assertTrue(Bounds(0, 0, 1080, 2400).isInside(window))
        assertFalse(Bounds(-1, 0, 100, 100).isInside(window))
    }

    @Test
    fun invalidBoundsRejected() {
        assertThrows<IllegalArgumentException> { Bounds(10, 0, 5, 5) }
    }

    @Test
    fun snapshotLooksUpNodesById() {
        val node = ScreenNode(id = 3, role = "Button", label = "Search", bounds = Bounds(0, 0, 10, 10))
        val snapshot = snapshot(listOf(node))
        assertEquals(node, snapshot.node(3))
        assertNull(snapshot.node(4))
    }

    @Test
    fun duplicateNodeIdsRejected() {
        val a = ScreenNode(id = 1, role = "Button", label = "A", bounds = Bounds(0, 0, 10, 10))
        assertThrows<IllegalArgumentException> { snapshot(listOf(a, a.copy(label = "B"))) }
    }

    @Test
    fun nodeFlagHelpers() {
        val node = ScreenNode(
            id = 1,
            role = "EditText",
            label = "Password",
            bounds = Bounds(0, 0, 10, 10),
            flags = setOf(NodeFlag.EDITABLE, NodeFlag.PASSWORD),
        )
        assertTrue(node.has(NodeFlag.EDITABLE))
        assertFalse(node.has(NodeFlag.CLICKABLE))
    }

    private fun snapshot(nodes: List<ScreenNode>) = ScreenSnapshot(
        packageName = "com.example",
        windowTitle = "Home",
        windowBounds = Bounds(0, 0, 1080, 2400),
        nodes = nodes,
        contentVersion = 1,
    )
}
