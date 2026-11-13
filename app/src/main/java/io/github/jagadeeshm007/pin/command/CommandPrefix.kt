package io.github.jagadeeshm007.pin.command

/**
 * The spoken "Pin" that marks speech as a command until the wake word works (spec PIN-30
 * Q1; replaced in PIN-22). Accepts the spellings the recognizer produced on device (Q2).
 */
object CommandPrefix {
    private val SPELLINGS = setOf("pin", "pins", "pen", "bin", "ping")
    private val PATTERN =
        Regex("^\\W*(?:hey\\W+)?(\\w+)\\b\\W*(.*)$", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    /**
     * The text after the prefix, with its case kept; "" when the utterance is only the
     * prefix (it arms the next one); null when there is no prefix.
     */
    fun strip(text: String): String? {
        val m = PATTERN.find(text) ?: return null
        if (m.groupValues[1].lowercase() !in SPELLINGS) return null
        return m.groupValues[2].trim()
    }
}
