package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandGrammarTest {
    @Test
    fun wholeUtteranceOnly() {
        assertNull(CommandGrammar.parse("scroll down and then go back"))
        assertNull(CommandGrammar.parse("I said go back"))
    }

    @Test
    fun typeKeepsTheSpokenCase() {
        assertEquals(Command.TypeText("Java Tutorials"), CommandGrammar.parse("type Java Tutorials"))
    }

    @Test
    fun openDropsTrailingApp() {
        assertEquals(Command.OpenApp("settings"), CommandGrammar.parse("Open the Settings app."))
    }

    @Test
    fun argumentFreeCommandsCommitEarly() {
        assertTrue(CommandGrammar.parse("scroll down")!!.earlyCommit)
        assertTrue(CommandGrammar.parse("go back")!!.earlyCommit)
        assertTrue(CommandGrammar.parse("stop")!!.earlyCommit)
        assertEquals(Command.Act(DeviceAction.Scroll(Direction.DOWN)), CommandGrammar.parse("page down"))
    }

    @Test
    fun commandsWithArgumentsWaitForTheFinal() {
        listOf("open YouTube", "tap search", "type hello", "tap number 1").forEach {
            assertFalse(CommandGrammar.parse(it)!!.earlyCommit, it)
        }
    }

    @Test
    fun aBareNumberPicksAShownNumber() {
        assertEquals(Command.PickNumber(3), CommandGrammar.parse("three"))
        assertEquals(Command.PickNumber(12), CommandGrammar.parse("12."))
        assertEquals(Command.TapNumber(7), CommandGrammar.parse("number seven"))
        assertEquals(Command.TapNumber(1), CommandGrammar.parse("click one"))
        assertEquals(Command.TapNumber(2), CommandGrammar.parse("tap two please"))
        assertFalse(CommandGrammar.parse("three")!!.earlyCommit)
        assertNull(CommandGrammar.parse("three cats"))
    }

    @Test
    fun normalizeIgnoresCaseAndPunctuation() {
        assertEquals("wi fi", CommandGrammar.normalize("Wi-Fi!"))
        assertEquals("network and internet", CommandGrammar.normalize("  Network and   internet. "))
    }
}
