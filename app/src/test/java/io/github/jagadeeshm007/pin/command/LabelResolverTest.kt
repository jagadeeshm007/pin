package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.command.LabelResolver.Resolution
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LabelResolverTest {
    private fun snapshot(vararg nodes: ScreenNode) =
        ScreenSnapshot("t", "", Bounds(0, 0, 1080, 2400), nodes.toList(), 0)

    private fun node(id: Int, label: String, flags: Set<NodeFlag> = setOf(NodeFlag.CLICKABLE), parent: Int? = null) =
        ScreenNode(id, "View", label, Bounds(0, id * 100, 1080, id * 100 + 90), flags, parentId = parent)

    @Test
    fun exactBeatsContainsBeatsFuzzy() {
        val s = snapshot(node(1, "Search YouTube"), node(2, "Search"), node(3, "Serch"))
        assertEquals(Resolution.One(s.nodes[1], 1), LabelResolver.resolve(s, "search"))
    }

    @Test
    fun wholeWordsOnlyAndAnEqualShareIsATie() {
        val s = snapshot(node(1, "Voice search"), node(2, "Search YouTube"), node(3, "Researching"))
        // "search" is one of two words in both; never guess between them (PIN-10 lesson).
        assertEquals(Resolution.Many(listOf(s.nodes[0], s.nodes[1])), LabelResolver.resolve(s, "search"))
        assertEquals(Resolution.None, LabelResolver.resolve(snapshot(node(1, "Researching")), "search"))
    }

    @Test
    fun theLabelCoveredMostWins() {
        val s = snapshot(node(1, "Close minimised player"), node(2, "Minimised player"))
        assertEquals(Resolution.One(s.nodes[1], 1), LabelResolver.resolve(s, "minimised player"))
        assertEquals(Resolution.One(s.nodes[0], 2), LabelResolver.resolve(s, "close player"))
        assertEquals(Resolution.One(s.nodes[1], 2), LabelResolver.resolve(s, "player"))
    }

    @Test
    fun ampersandReadsAsAnd() {
        val s = snapshot(node(1, "Sound & vibration"))
        assertEquals(Resolution.One(s.nodes[0], 1), LabelResolver.resolve(s, "sound and vibration"))
    }

    @Test
    fun labelledChildOfAClickableRowCounts() {
        val row = node(1, "", setOf(NodeFlag.CLICKABLE))
        val text = node(2, "Network and internet", emptySet(), parent = 1)
        assertEquals(Resolution.One(text, 2), LabelResolver.resolve(snapshot(row, text), "network"))
    }

    @Test
    fun plainTextWithoutAnActionableParentIsNotACandidate() {
        assertEquals(Resolution.None, LabelResolver.resolve(snapshot(node(1, "Just a heading", emptySet())), "heading"))
    }

    @Test
    fun passwordFieldsAreNeverCandidates() {
        val pw = node(1, "Password", setOf(NodeFlag.EDITABLE, NodeFlag.PASSWORD))
        assertEquals(Resolution.None, LabelResolver.resolve(snapshot(pw), "password"))
    }

    @Test
    fun editableOnlyWhenAsked() {
        val s = snapshot(node(1, "Search", setOf(NodeFlag.CLICKABLE)), node(2, "Search", setOf(NodeFlag.EDITABLE)))
        assertEquals(Resolution.One(s.nodes[1], 1), LabelResolver.resolve(s, "search", editable = true))
    }

    @Test
    fun equalShortestLabelsAreATie() {
        val s = snapshot(node(1, "All filter"), node(2, "Work filter"), node(3, "Unread filter"))
        val r = LabelResolver.resolve(s, "filter")
        assertTrue(r is Resolution.Many && r.nodes.size == 3, r.toString())
    }

    @Test
    fun fuzzyDistanceDependsOnWordLength() {
        assertEquals(1, EditDistance.allowed("apps"))
        assertEquals(2, EditDistance.allowed("notifications"))
        assertEquals(1, EditDistance.within("apps", "app", 1))
        assertEquals(null, EditDistance.within("abc", "xyz", 1))
    }

    @Test
    fun boundedDistanceStopsEarly() {
        assertEquals(null, EditDistance.within("notifications", "battery", 2))
        assertEquals(1, EditDistance.within("notifcations", "notifications", 2))
        assertEquals(0, EditDistance.within("same", "same", 1))
    }

    @Test
    fun twoHundredNodesUnderFiveMilliseconds() {
        val s = snapshot(*Array(200) { node(it + 1, "Row number ${it + 1} with some words") })
        LabelResolver.resolve(s, "warm up")
        val start = System.nanoTime()
        repeat(10) { LabelResolver.resolve(s, "sum words") }
        val perCallMs = (System.nanoTime() - start) / 10 / 1_000_000.0
        assertTrue(perCallMs < 5.0, "took $perCallMs ms")
    }
}
