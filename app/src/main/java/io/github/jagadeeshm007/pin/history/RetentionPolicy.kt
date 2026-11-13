package io.github.jagadeeshm007.pin.history

/** What retention needs to know about a session. Open sessions have no [endedAt]. */
data class SessionAge(val id: Long, val endedAt: Long?, val pinned: Boolean)

/**
 * Decides which sessions to delete (ADR-0005). A session is kept while it is open,
 * pinned, or ended within the last [days] days.
 */
class RetentionPolicy(val days: Int = DEFAULT_DAYS) {
    init {
        require(days in ALLOWED_DAYS) { "retention must be one of $ALLOWED_DAYS, was $days" }
    }

    /** Ids of [sessions] to delete at time [now] (epoch millis). */
    fun expired(sessions: List<SessionAge>, now: Long): List<Long> {
        if (days == FOREVER) return emptyList()
        val cutoff = now - days * DAY_MS
        return sessions.filter { !it.pinned && it.endedAt != null && it.endedAt < cutoff }.map { it.id }
    }

    companion object {
        const val DEFAULT_DAYS = 7
        const val FOREVER = 0
        val ALLOWED_DAYS = listOf(1, 3, 7, 14, 30, FOREVER)
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
