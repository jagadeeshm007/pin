package io.github.jagadeeshm007.pin.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AndroidTranscriberTest {
    @Test
    fun `prefers en-US when installed`() {
        assertEquals("en-US", AndroidTranscriber.pickLanguage(listOf("en-IN", "en-US")))
    }

    @Test
    fun `falls back to the installed English pack`() {
        assertEquals("en-IN", AndroidTranscriber.pickLanguage(listOf("hi-IN", "en-IN")))
    }

    @Test
    fun `keeps en-US when no English pack is installed`() {
        assertEquals("en-US", AndroidTranscriber.pickLanguage(listOf("hi-IN")))
        assertEquals("en-US", AndroidTranscriber.pickLanguage(emptyList()))
    }
}
