package io.github.jagadeeshm007.pin.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SpeechEventTest {
    @Test
    fun everyEventRoundTripsThroughFields() {
        listOf(
            SpeechEvent.State(ListenState.LOADING),
            SpeechEvent.State(ListenState.LISTENING),
            SpeechEvent.State(ListenState.PAUSED, "microphone in use"),
            SpeechEvent.State(ListenState.ERROR, "models missing"),
            SpeechEvent.Partial("scroll"),
            SpeechEvent.Final("scroll down", audioMs = 1_200, decodeMs = 45),
        ).forEach { assertEquals(it, SpeechEvent.fromFields(it.toFields()), "for $it") }
    }

    @Test
    fun unknownOrBrokenFieldsAreIgnored() {
        assertNull(SpeechEvent.fromFields(emptyMap()))
        assertNull(SpeechEvent.fromFields(mapOf("type" to "nonsense")))
        assertNull(SpeechEvent.fromFields(mapOf("type" to "state", "state" to "FLYING")))
        assertNull(SpeechEvent.fromFields(mapOf("type" to "final", "text" to "x", "audioMs" to "abc")))
    }

    @Test
    fun fieldsCarryOnlyTextAndNumbers() {
        val fields = SpeechEvent.Final("open settings", 900, 30).toFields()
        assertEquals(setOf("type", "text", "audioMs", "decodeMs"), fields.keys)
    }
}
