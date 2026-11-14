package io.github.jagadeeshm007.pin.command

/** Bounded Levenshtein distance for recognizer slips (spec PIN-31, tier 3). */
object EditDistance {
    /** Edits allowed for a spoken [word]: 1 for short words, 2 for longer ones. */
    fun allowed(word: String): Int = if (word.length <= SHORT_WORD) 1 else 2

    /** The distance between [a] and [b] when it is at most [bound], else null. */
    fun within(a: String, b: String, bound: Int): Int? {
        if (kotlin.math.abs(a.length - b.length) > bound) return null
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            var rowMin = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                if (current[j] < rowMin) rowMin = current[j]
            }
            if (rowMin > bound) return null
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length].takeIf { it <= bound }
    }

    private const val SHORT_WORD = 4
}
