package io.github.jagadeeshm007.pin.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpeechSegmenterTest {
    private val window = SpeechSegmenter.WINDOW
    private fun w(value: Float) = FloatArray(window) { value }

    private fun SpeechSegmenter.feed(speech: Boolean, times: Int, value: Float = 0f): List<SegmentAction> =
        (1..times).flatMap { push(w(value), speech) }

    @Test
    fun silenceProducesNothing() {
        val s = SpeechSegmenter()
        assertTrue(s.feed(speech = false, times = 100).isEmpty())
    }

    @Test
    fun speechStartsWithPreRollThenFeedsThenEndsAfterSilence() {
        val s = SpeechSegmenter(preRollWindows = 3, endSilenceWindows = 5)
        s.feed(speech = false, times = 10, value = 0.1f)
        val start = s.push(w(0.9f), speech = true)
        val begin = start.single() as SegmentAction.Begin
        assertEquals(4 * window, begin.samples.size, "3 windows of pre-roll plus the first speech window")
        assertEquals(0.1f, begin.samples.first())
        assertEquals(0.9f, begin.samples.last())

        val talking = s.feed(speech = true, times = 4)
        assertEquals(4, talking.count { it is SegmentAction.Feed })

        val pause = s.feed(speech = false, times = 4)
        assertEquals(4, pause.count { it is SegmentAction.Feed }, "short pauses stay in the utterance")

        val end = s.push(w(0f), speech = false)
        assertEquals(listOf(SegmentAction.Feed::class, SegmentAction.End::class), end.map { it::class })
        assertEquals(EndReason.SILENCE, (end.last() as SegmentAction.End).reason)
    }

    @Test
    fun speechAfterAShortPauseKeepsTheSameUtterance() {
        val s = SpeechSegmenter(preRollWindows = 0, endSilenceWindows = 5)
        s.feed(true, 3)
        s.feed(false, 4)
        val again = s.feed(true, 2)
        assertTrue(again.none { it is SegmentAction.End || it is SegmentAction.Begin })
    }

    @Test
    fun longUtteranceIsCut() {
        val s = SpeechSegmenter(preRollWindows = 0, maxWindows = 10)
        val actions = s.feed(true, 12)
        val end = actions.filterIsInstance<SegmentAction.End>().single()
        assertEquals(EndReason.TOO_LONG, end.reason)
    }

    @Test
    fun aNewUtteranceCanStartAfterAnEnd() {
        val s = SpeechSegmenter(preRollWindows = 0, endSilenceWindows = 2)
        s.feed(true, 2)
        s.feed(false, 2)
        val next = s.feed(true, 1)
        assertTrue(next.single() is SegmentAction.Begin)
    }

    @Test
    fun resetDropsAnUtteranceInProgress() {
        val s = SpeechSegmenter(preRollWindows = 0)
        s.feed(true, 3)
        s.reset()
        assertTrue(s.feed(false, 50).isEmpty())
    }

    @Test
    fun defaultsMatchTheSpec() {
        // 600 ms of silence ends an utterance; 640 ms of pre-roll; 15 s cap.
        assertEquals(19, SpeechSegmenter.DEFAULT_END_SILENCE_WINDOWS)
        assertEquals(20, SpeechSegmenter.DEFAULT_PRE_ROLL_WINDOWS)
        assertEquals(469, SpeechSegmenter.DEFAULT_MAX_WINDOWS)
    }
}
