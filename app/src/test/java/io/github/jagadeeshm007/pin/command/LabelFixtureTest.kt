package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Spec PIN-31 AC-5: real labels from YouTube, Settings and WhatsApp snapshots. */
class LabelFixtureTest {
    private val rows = javaClass.getResource("/label-fixtures.tsv")!!.readText().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }

    @Test
    fun enoughCases() {
        assertTrue(rows.size >= 40, "${rows.size} cases")
    }

    @Test
    fun everyCaseResolvesAsExpected() {
        val wrong = rows.mapNotNull { (labels, spoken, expected) ->
            val got = describe(LabelResolver.resolve(screen(labels.split('|')), spoken))
            if (got != expected) "'$spoken' over [$labels] -> $got (want $expected)" else null
        }
        assertEquals(emptyList<String>(), wrong)
    }

    private fun describe(r: LabelResolver.Resolution) = when (r) {
        is LabelResolver.Resolution.One -> r.node.label
        is LabelResolver.Resolution.Many -> "many:${r.nodes.size}"
        LabelResolver.Resolution.None -> "none"
    }

    companion object {
        /** Every label becomes a clickable node stacked top to bottom. */
        fun screen(labels: List<String>): ScreenSnapshot = ScreenSnapshot(
            packageName = "fixture",
            windowTitle = "",
            windowBounds = Bounds(0, 0, 1080, 2400),
            contentVersion = 0,
            nodes = labels.mapIndexed { i, l ->
                ScreenNode(i + 1, "Button", l, Bounds(0, i * 100, 1080, i * 100 + 90), setOf(NodeFlag.CLICKABLE))
            },
        )
    }
}
