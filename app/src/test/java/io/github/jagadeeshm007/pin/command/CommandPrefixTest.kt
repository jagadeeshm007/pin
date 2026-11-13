package io.github.jagadeeshm007.pin.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandPrefixTest {
    @Test
    fun stripsThePrefixAndKeepsTheRest() {
        assertEquals("scroll down", CommandPrefix.strip("Pin, scroll down"))
        assertEquals("open YouTube", CommandPrefix.strip("pin open YouTube"))
        assertEquals("type Hello", CommandPrefix.strip("Hey Pin, type Hello"))
    }

    @Test
    fun acceptsSpellingsSeenOnDevice() {
        // PIN-30 spec Q2: "pins grown down" was heard for "Pin scroll down".
        listOf("pins", "pen", "bin", "ping").forEach { assertEquals("go back", CommandPrefix.strip("$it go back"), it) }
    }

    @Test
    fun prefixAloneArms() {
        assertEquals("", CommandPrefix.strip("Pin"))
        assertEquals("", CommandPrefix.strip("pin."))
    }

    @Test
    fun noPrefixIsNotACommand() {
        assertNull(CommandPrefix.strip("scroll down"))
        assertNull(CommandPrefix.strip("pineapple juice"))
        assertNull(CommandPrefix.strip(""))
    }
}
