package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/**
 * Finds the node a spoken label means (spec PIN-31, blueprint 6.3). Candidates are
 * labelled nodes that are actionable themselves or sit inside an actionable parent
 * (Settings rows: a clickable row with a text child). Tiers, best first:
 * 1 exact normalised label; 2 the spoken words as whole words, in order, in the label;
 * 3 every spoken word within a small edit distance of a label word. Inside a tier the
 * label whose words the spoken words cover most wins ("unread filter" covers 2 of 3);
 * an equal share is a tie, asked about with numbers rather than guessed (the "Voice
 * search" lesson from PIN-10). Pure.
 */
object LabelResolver {
    sealed interface Resolution {
        data class One(val node: ScreenNode, val tier: Int) : Resolution

        data class Many(val nodes: List<ScreenNode>) : Resolution

        data object None : Resolution
    }

    /** Resolves [spoken] on [screen]; only editable nodes when [editable]. */
    fun resolve(screen: ScreenSnapshot, spoken: String, editable: Boolean = false): Resolution {
        val words = CommandGrammar.normalize(spoken).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return Resolution.None
        val wanted = words.joinToString(" ")
        val candidates = candidates(screen, editable)
        for (tier in 1..LAST_TIER) {
            val hits = candidates.filter { (_, label) -> matches(tier, wanted, words, label) }
            if (hits.isEmpty()) continue
            // Share of the label covered by the spoken words; fewer label words is a better fit.
            val fewest = hits.minOf { it.second.count { c -> c == ' ' } }
            val best = hits.filter { it.second.count { c -> c == ' ' } == fewest }.map { it.first }
            return if (best.size == 1) Resolution.One(best[0], tier) else Resolution.Many(best)
        }
        return Resolution.None
    }

    /** The focused editable field, or the only editable field on screen. */
    fun inputField(screen: ScreenSnapshot): ScreenNode? {
        val fields = screen.nodes.filter { it.has(NodeFlag.EDITABLE) && !it.has(NodeFlag.PASSWORD) }
        return fields.firstOrNull { it.has(NodeFlag.FOCUSED) } ?: fields.singleOrNull()
    }

    /** Labelled candidates with their normalised labels. */
    private fun candidates(screen: ScreenSnapshot, editable: Boolean): List<Pair<ScreenNode, String>> {
        val byId = screen.nodes.associateBy { it.id }
        return screen.nodes.asSequence()
            .filter { it.label.isNotBlank() && !it.has(NodeFlag.PASSWORD) }
            .filter { if (editable) it.has(NodeFlag.EDITABLE) else actionable(it) || actionableParent(it, byId) }
            .map { it to CommandGrammar.normalize(it.label) }
            .filter { it.second.isNotEmpty() }
            .toList()
    }

    private fun actionable(n: ScreenNode) = ACTIONABLE.any { n.has(it) }

    private fun actionableParent(n: ScreenNode, byId: Map<Int, ScreenNode>): Boolean =
        n.parentId?.let { byId[it] }?.let { actionable(it) && it.label.isBlank() } == true

    private fun matches(tier: Int, wanted: String, words: List<String>, label: String): Boolean = when (tier) {
        1 -> label == wanted
        2 -> wholeWordsInOrder(words, label.split(' '))
        else -> words.all { w -> label.split(' ').any { EditDistance.within(w, it, EditDistance.allowed(w)) != null } }
    }

    private fun wholeWordsInOrder(words: List<String>, labelWords: List<String>): Boolean {
        var from = 0
        for (w in words) {
            val at = (from until labelWords.size).firstOrNull { labelWords[it] == w } ?: return false
            from = at + 1
        }
        return true
    }

    private val ACTIONABLE = listOf(NodeFlag.CLICKABLE, NodeFlag.LONG_CLICKABLE, NodeFlag.EDITABLE, NodeFlag.CHECKABLE)
    private const val LAST_TIER = 3
}
