package io.github.jagadeeshm007.pin.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Spec PIN-30 AC-3: at least 95 % of the corpus right, no conversation line turned into a command. */
class CommandCorpusTest {
    private val rows = javaClass.getResource("/command-corpus.tsv")!!.readText().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line -> line.split('\t').let { it[0] to it[1] } }

    @Test
    fun corpusIsLargeEnough() {
        assertTrue(rows.size >= 150, "corpus has ${rows.size} lines")
    }

    @Test
    fun atLeast95PercentAreRight() {
        val wrong = rows.filter { (utterance, expected) -> CommandGrammar.parse(utterance).canonical() != expected }
        val right = rows.size - wrong.size
        assertTrue(
            right * 100 >= rows.size * 95,
            "wrong: " + wrong.joinToString { (u, e) -> "'$u' -> ${CommandGrammar.parse(u).canonical()} (want $e)" },
        )
    }

    @Test
    fun noConversationLineBecomesACommand() {
        val falsePositives = rows.filter { it.second == "none" }.filter { CommandGrammar.parse(it.first) != null }
        assertEquals(emptyList<Pair<String, String>>(), falsePositives)
    }
}
