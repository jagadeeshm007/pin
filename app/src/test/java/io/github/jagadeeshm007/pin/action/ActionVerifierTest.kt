package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.action.Fixtures.LIST
import io.github.jagadeeshm007.pin.action.Fixtures.QUERY
import io.github.jagadeeshm007.pin.action.Fixtures.SEARCH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionVerifierTest {
    private val verifier = ActionVerifier()
    private val before = Fixtures.screen(contentVersion = 1)
    private val unchanged = Fixtures.screen(contentVersion = 1)
    private val changed = Fixtures.screen(contentVersion = 2)

    @Test
    fun tapPassesWhenContentChanges() {
        assertEquals(Verification.Passed, verifier.verify(DeviceAction.Tap(TargetRef.Node(SEARCH)), before, changed))
    }

    @Test
    fun tapPassesWhenWindowChanges() {
        val newWindow = Fixtures.screen(windowTitle = "Search", contentVersion = 1)
        assertEquals(Verification.Passed, verifier.verify(DeviceAction.Tap(TargetRef.Node(SEARCH)), before, newWindow))
    }

    @Test
    fun tapFailsWhenNothingChanges() {
        assertFailed(verifier.verify(DeviceAction.Tap(TargetRef.Node(SEARCH)), before, unchanged), "no change")
    }

    @Test
    fun scrollAndGesturesNeedAChange() {
        listOf(
            DeviceAction.Scroll(Direction.DOWN, LIST),
            DeviceAction.Swipe(Direction.LEFT),
            DeviceAction.Pinch(zoomIn = true),
            DeviceAction.DoubleTap(TargetRef.Node(LIST)),
            DeviceAction.LongPress(TargetRef.Node(SEARCH)),
            DeviceAction.Global(GlobalNav.BACK),
        ).forEach {
            assertEquals(Verification.Passed, verifier.verify(it, before, changed), "for $it")
            assertTrue(verifier.verify(it, before, unchanged) is Verification.Failed, "for $it")
        }
    }

    @Test
    fun openAppPassesOnlyWhenThatAppIsInFront() {
        val action = DeviceAction.OpenApp("com.whatsapp")
        assertEquals(
            Verification.Passed,
            verifier.verify(action, before, Fixtures.screen(packageName = "com.whatsapp")),
        )
        assertFailed(verifier.verify(action, before, changed), "com.whatsapp")
    }

    @Test
    fun typingPassesWhenAnEditableFieldHoldsTheText() {
        val action = DeviceAction.TypeText(QUERY, "Java tutorials")
        val after = Fixtures.screen(contentVersion = 2, queryText = "Java tutorials")
        assertEquals(Verification.Passed, verifier.verify(action, before, after))
    }

    @Test
    fun typingFailsWhenTextIsMissing() {
        val action = DeviceAction.TypeText(QUERY, "Java tutorials")
        val after = Fixtures.screen(contentVersion = 2, queryText = "Java")
        assertFailed(verifier.verify(action, before, after), "text")
    }

    @Test
    fun typingWithSubmitPassesWhenTheScreenMovesOn() {
        val action = DeviceAction.TypeText(QUERY, "Java tutorials", submit = true)
        val results = Fixtures.screen(windowTitle = "Results", contentVersion = 3)
        assertEquals(Verification.Passed, verifier.verify(action, before, results))
    }

    private fun assertFailed(result: Verification, reasonContains: String) {
        assertTrue(result is Verification.Failed, "expected Failed, got $result")
        val reason = (result as Verification.Failed).reason
        assertTrue(reason.contains(reasonContains), "reason '$reason' should mention '$reasonContains'")
    }
}
