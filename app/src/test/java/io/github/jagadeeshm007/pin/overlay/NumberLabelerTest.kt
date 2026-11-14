package io.github.jagadeeshm007.pin.overlay

import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NumberLabelerTest {
    private val labeler = NumberLabeler()
    private val window = Bounds(0, 0, 1080, 2400)

    private fun node(id: Int, b: Bounds, vararg flags: NodeFlag, role: String = "View", label: String = "n$id") =
        ScreenNode(id, role, label, b, flags.toSet() + NodeFlag.ENABLED)

    private fun screen(nodes: List<ScreenNode>, version: Long = 1) = ScreenSnapshot("app", "t", window, nodes, version)

    @Test
    fun numbersActionableNodesTopToBottomThenLeftToRight() {
        val nodes = listOf(
            node(1, Bounds(600, 500, 700, 600), NodeFlag.CLICKABLE),
            node(2, Bounds(0, 100, 100, 200), NodeFlag.CLICKABLE),
            node(3, Bounds(0, 500, 100, 600), NodeFlag.EDITABLE),
            node(4, Bounds(200, 100, 300, 200), NodeFlag.CHECKABLE),
            node(5, Bounds(0, 800, 1080, 900)),
            node(6, Bounds(0, 0, 1080, 2400), NodeFlag.SCROLLABLE),
        )
        val map = labeler.label(screen(nodes))
        assertEquals(listOf(2, 4, 3, 1), map.entries.map { it.nodeId })
        assertEquals(listOf(1, 2, 3, 4), map.entries.map { it.number })
    }

    @Test
    fun labelNodesNumbersJustTheGivenNodesInScreenOrder() {
        val a = node(1, Bounds(0, 500, 100, 600), NodeFlag.CLICKABLE)
        val b = node(2, Bounds(0, 100, 100, 200), NodeFlag.CLICKABLE)
        val map = NumberLabeler().labelNodes(listOf(a, b))
        assertEquals(listOf(2 to 1, 1 to 2), map.entries.map { it.nodeId to it.number })
    }

    @Test
    fun containerHoldingAnotherCandidateIsSkipped() {
        val nodes = listOf(
            node(1, Bounds(0, 300, 1080, 900), NodeFlag.CLICKABLE),
            node(2, Bounds(900, 400, 1000, 500), NodeFlag.CLICKABLE),
            node(3, Bounds(0, 1000, 1080, 1100), NodeFlag.CLICKABLE),
        )
        assertEquals(listOf(2, 3), labeler.label(screen(nodes)).entries.map { it.nodeId })
    }

    @Test
    fun duplicateBoundsAreLabelledOnce() {
        val nodes = listOf(
            node(1, Bounds(0, 0, 100, 100), NodeFlag.CLICKABLE),
            node(2, Bounds(0, 0, 100, 100), NodeFlag.LONG_CLICKABLE),
        )
        assertEquals(listOf(1), labeler.label(screen(nodes)).entries.map { it.nodeId })
    }

    @Test
    fun atMost99Labels() {
        val nodes = (1..150).map { node(it, Bounds(0, it * 10, 50, it * 10 + 5), NodeFlag.CLICKABLE) }
        val map = labeler.label(screen(nodes))
        assertEquals(99, map.entries.size)
        assertEquals(99, map.entries.last().number)
    }

    @Test
    fun resolvesNumbersAgainstALaterSnapshotOfTheSameScreen() {
        val first = screen(listOf(node(7, Bounds(0, 100, 100, 200), NodeFlag.CLICKABLE, role = "Button")))
        val map = labeler.label(first)
        val later = screen(listOf(node(3, Bounds(0, 100, 100, 200), NodeFlag.CLICKABLE, role = "Button")), version = 9)
        assertEquals(TargetRef.Node(3), map.resolve(1, later))
    }

    @Test
    fun rejectsUnknownNumbersAndMovedTargets() {
        val map = labeler.label(screen(listOf(node(1, Bounds(0, 100, 100, 200), NodeFlag.CLICKABLE))))
        assertNull(map.resolve(2, screen(emptyList())))
        val moved = screen(listOf(node(1, Bounds(0, 300, 100, 400), NodeFlag.CLICKABLE)))
        assertNull(map.resolve(1, moved))
    }
}
