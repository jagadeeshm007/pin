package io.github.jagadeeshm007.pin.notification

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThrottledPosterTest {
    private val posted = mutableListOf<Pair<Long, String>>()

    private fun TestScope.poster() = ThrottledPoster<String>(backgroundScope, 500, { testScheduler.currentTime }) {
        posted +=
            testScheduler.currentTime to it
    }

    @Test
    fun firstUpdatePostsImmediately() = runTest {
        poster().submit("a", urgent = false)
        runCurrent()
        assertEquals(listOf(0L to "a"), posted)
    }

    @Test
    fun burstPostsFirstThenOnlyTheLatestAfterTheWindow() = runTest {
        val p = poster()
        p.submit("a", false)
        advanceTimeBy(100)
        p.submit("b", false)
        advanceTimeBy(100)
        p.submit("c", false)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(0L to "a", 500L to "c"), posted)
    }

    @Test
    fun urgentUpdatePostsAtOnceAndDropsThePendingOne() = runTest {
        val p = poster()
        p.submit("a", false)
        advanceTimeBy(100)
        p.submit("b", false)
        advanceTimeBy(100)
        p.submit("confirm", urgent = true)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(0L to "a", 200L to "confirm"), posted)
    }

    @Test
    fun updateAfterTheWindowPostsImmediately() = runTest {
        val p = poster()
        p.submit("a", false)
        advanceTimeBy(600)
        p.submit("b", false)
        runCurrent()
        assertEquals(listOf(0L to "a", 600L to "b"), posted)
    }

    @Test
    fun nothingIsPostedTwice() = runTest {
        val p = poster()
        p.submit("a", false)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, posted.size)
    }
}
