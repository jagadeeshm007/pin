package io.github.jagadeeshm007.pin.notification

import io.github.jagadeeshm007.pin.notification.NotificationAction.CONFIRM
import io.github.jagadeeshm007.pin.notification.NotificationAction.DENY
import io.github.jagadeeshm007.pin.notification.NotificationAction.STOP
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationContentMapperTest {
    private val labels = object : ChipLabels {
        override val listening = "Listening"
        override val heard = "Heard"
        override val thinking = "Thinking"
        override val acting = "Acting"
        override val confirm = "Confirm?"
        override val done = "Done"
        override val stopped = "Stopped"
        override val paused = "Paused"

        override fun step(step: Int, total: Int) = "Step $step/$total"
    }
    private val mapper = NotificationContentMapper(labels)

    @Test
    fun listeningOffersStopOnly() {
        val c = mapper.map(TaskStatus(TaskPhase.LISTENING, "Say 'hey pin'"))
        assertEquals(
            NotificationContent("Say 'hey pin'", "Listening", ProgressSpec.None, listOf(STOP), urgent = false),
            c,
        )
    }

    @Test
    fun heardAndThinkingAreIndeterminate() {
        assertEquals(ProgressSpec.Indeterminate, mapper.map(TaskStatus(TaskPhase.HEARD, "scroll down")).progress)
        val thinking = mapper.map(TaskStatus(TaskPhase.THINKING, "open YouTube"))
        assertEquals(ProgressSpec.Indeterminate, thinking.progress)
        assertEquals("Thinking", thinking.chip)
    }

    @Test
    fun actingShowsStepsDoneSoFar() {
        val c = mapper.map(TaskStatus(TaskPhase.ACTING, "Tap Search", step = 3, totalSteps = 6))
        assertEquals("Step 3/6", c.chip)
        assertEquals(ProgressSpec.Steps(done = 2, total = 6), c.progress)
        assertEquals(listOf(STOP), c.actions)
    }

    @Test
    fun actingWithoutAPlanIsIndeterminate() {
        val c = mapper.map(TaskStatus(TaskPhase.ACTING, "Tap Search"))
        assertEquals("Acting", c.chip)
        assertEquals(ProgressSpec.Indeterminate, c.progress)
    }

    @Test
    fun stepNumbersAreClampedToThePlan() {
        val c = mapper.map(TaskStatus(TaskPhase.ACTING, "x", step = 9, totalSteps = 4))
        assertEquals("Step 4/4", c.chip)
        assertEquals(ProgressSpec.Steps(done = 3, total = 4), c.progress)
    }

    @Test
    fun confirmIsUrgentAndOffersConfirmDenyAndStop() {
        val c = mapper.map(TaskStatus(TaskPhase.CONFIRM, "Send the message?", step = 4, totalSteps = 5))
        assertEquals("Confirm?", c.chip)
        assertEquals(listOf(CONFIRM, DENY, STOP), c.actions)
        assertTrue(c.urgent)
        assertEquals(ProgressSpec.Steps(done = 3, total = 5), c.progress)
    }

    @Test
    fun doneFillsTheBarAndOffersNothing() {
        val c = mapper.map(TaskStatus(TaskPhase.DONE, "Playing Java tutorial", step = 6, totalSteps = 6))
        assertEquals(ProgressSpec.Steps(done = 6, total = 6), c.progress)
        assertEquals(emptyList<NotificationAction>(), c.actions)
        assertTrue(c.urgent)
    }

    @Test
    fun failedIsUrgentWithoutProgress() {
        val c = mapper.map(TaskStatus(TaskPhase.FAILED, "Could not find Search"))
        assertEquals("Stopped", c.chip)
        assertEquals(ProgressSpec.None, c.progress)
        assertTrue(c.urgent)
    }

    @Test
    fun pausedKeepsStop() {
        val c = mapper.map(TaskStatus(TaskPhase.PAUSED, "Microphone in use by a call"))
        assertEquals(listOf(STOP), c.actions)
        assertFalse(c.urgent)
    }
}
