package io.github.jagadeeshm007.pin.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RetentionPolicyTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test
    fun deletesUnpinnedSessionsThatEndedBeforeTheWindow() {
        val sessions = listOf(
            SessionAge(1, endedAt = now - 8 * day, pinned = false),
            SessionAge(2, endedAt = now - 6 * day, pinned = false),
            SessionAge(3, endedAt = now - 30 * day, pinned = true),
            SessionAge(4, endedAt = null, pinned = false),
            SessionAge(5, endedAt = now - 7 * day - 1, pinned = false),
            SessionAge(6, endedAt = now - 7 * day, pinned = false),
        )
        assertEquals(listOf(1L, 5L), RetentionPolicy(days = 7).expired(sessions, now))
    }

    @Test
    fun keepForeverDeletesNothing() {
        val old = listOf(SessionAge(1, endedAt = 0, pinned = false))
        assertEquals(emptyList<Long>(), RetentionPolicy(days = RetentionPolicy.FOREVER).expired(old, now))
    }

    @Test
    fun onlyAllowedWindowsAreAccepted() {
        RetentionPolicy.ALLOWED_DAYS.forEach { RetentionPolicy(it) }
        assertThrows<IllegalArgumentException> { RetentionPolicy(days = 5) }
    }
}
