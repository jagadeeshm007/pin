package io.github.jagadeeshm007.pin.speech

/** What the audio thread should do with the recognizer after one window. */
sealed interface SegmentAction {
    /** An utterance started: create a stream and feed [samples] (pre-roll plus this window). */
    class Begin(val samples: FloatArray) : SegmentAction

    /** Feed [samples] to the current stream. */
    class Feed(val samples: FloatArray) : SegmentAction

    /** The utterance is over: finish the stream and read the final text. */
    data class End(val reason: EndReason) : SegmentAction
}

enum class EndReason { SILENCE, TOO_LONG }

/**
 * Turns per-window voice detection into utterances (spec PIN-21 AC-3, AC-4). The
 * recognizer runs only between Begin and End, so silence costs only voice detection.
 * Windows are [WINDOW] samples at 16 kHz (32 ms). Not thread safe: one audio thread.
 */
class SpeechSegmenter(
    private val preRollWindows: Int = DEFAULT_PRE_ROLL_WINDOWS,
    private val endSilenceWindows: Int = DEFAULT_END_SILENCE_WINDOWS,
    private val maxWindows: Int = DEFAULT_MAX_WINDOWS,
) {
    private val preRoll = ArrayDeque<FloatArray>()
    private var active = false
    private var silentRun = 0
    private var length = 0

    /** Processes one window; [speech] is the voice detection decision for it. */
    fun push(window: FloatArray, speech: Boolean): List<SegmentAction> {
        if (!active) {
            if (!speech) {
                remember(window)
                return emptyList()
            }
            active = true
            silentRun = 0
            length = 1
            val samples = (preRoll.toList() + listOf(window)).let { parts ->
                FloatArray(parts.sumOf { it.size }).also { out ->
                    var at = 0
                    parts.forEach { p ->
                        p.copyInto(out, at)
                        at += p.size
                    }
                }
            }
            preRoll.clear()
            return listOf(SegmentAction.Begin(samples))
        }
        length++
        silentRun = if (speech) 0 else silentRun + 1
        val feed = SegmentAction.Feed(window)
        return when {
            silentRun >= endSilenceWindows -> finish(feed, EndReason.SILENCE)
            length >= maxWindows -> finish(feed, EndReason.TOO_LONG)
            else -> listOf(feed)
        }
    }

    /** Drops any utterance in progress (stop, pause). */
    fun reset() {
        active = false
        silentRun = 0
        length = 0
        preRoll.clear()
    }

    private fun finish(feed: SegmentAction, reason: EndReason): List<SegmentAction> {
        reset()
        return listOf(feed, SegmentAction.End(reason))
    }

    private fun remember(window: FloatArray) {
        if (preRollWindows == 0) return
        preRoll.addLast(window)
        while (preRoll.size > preRollWindows) preRoll.removeFirst()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val WINDOW = 512

        /** About 640 ms before speech; 320 ms clipped first words on device (PIN-21). */
        const val DEFAULT_PRE_ROLL_WINDOWS = 20

        /** About 600 ms of silence ends an utterance. */
        const val DEFAULT_END_SILENCE_WINDOWS = 19

        /** About 15 s caps an utterance. */
        const val DEFAULT_MAX_WINDOWS = 469
    }
}
