package io.github.jagadeeshm007.pin.history

import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.ExecutionStrategy
import io.github.jagadeeshm007.pin.action.GlobalNav
import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StepMapperTest {
    private val screen = ScreenSnapshot("app", "t", Bounds(0, 0, 10, 10), emptyList(), 1)
    private val tap = DeviceAction.Tap(TargetRef.Node(3))

    @Test
    fun attemptStartedKeepsStrategyAttemptAndScreen() {
        val r = StepMapper.map(ActionEvent.AttemptStarted(tap, ExecutionStrategy.NODE_ACTION, 1, screen))
        assertEquals(StepKind.ATTEMPT_STARTED, r.kind)
        assertEquals("Tap(target=Node(id=3))", r.action)
        assertEquals("NODE_ACTION", r.strategy)
        assertEquals(1, r.attempt)
        assertEquals(screen, r.screen)
    }

    @Test
    fun succeededKeepsTheScreenAfter() {
        val r = StepMapper.map(ActionEvent.Succeeded(tap, ExecutionStrategy.GESTURE, screen))
        assertEquals(StepKind.SUCCEEDED, r.kind)
        assertEquals(screen, r.screen)
    }

    @Test
    fun failuresAndRefusalsKeepTheirReasonButNoScreen() {
        val cases = listOf(
            ActionEvent.AttemptFailed(tap, ExecutionStrategy.NODE_ACTION, 1, "no change") to StepKind.ATTEMPT_FAILED,
            ActionEvent.Rejected(tap, "node 3 is not on screen") to StepKind.REJECTED,
            ActionEvent.ConfirmationRequested(tap, "looks sensitive") to StepKind.CONFIRMATION_REQUESTED,
        )
        cases.forEach { (event, kind) ->
            val r = StepMapper.map(event)
            assertEquals(kind, r.kind)
            assertTrue(r.reason!!.isNotBlank())
            assertEquals(null, r.screen)
        }
    }

    @Test
    fun declinedHasNoReasonOrScreen() {
        val r = StepMapper.map(ActionEvent.Declined(DeviceAction.Global(GlobalNav.BACK)))
        assertEquals(StepKind.DECLINED, r.kind)
        assertEquals(null, r.reason)
        assertFalse(r.screen != null)
    }

    @Test
    fun typedTextIsKeptButLongTextIsShortened() {
        val long = "a".repeat(5_000)
        val r = StepMapper.map(ActionEvent.Rejected(DeviceAction.TypeText(2, long), "too long"))
        assertTrue(r.action.length <= StepMapper.MAX_ACTION_CHARS)
    }
}
