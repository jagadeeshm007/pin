package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.action.Fixtures.LIST
import io.github.jagadeeshm007.pin.action.Fixtures.OFFSCREEN
import io.github.jagadeeshm007.pin.action.Fixtures.PASSWORD
import io.github.jagadeeshm007.pin.action.Fixtures.QUERY
import io.github.jagadeeshm007.pin.action.Fixtures.SEARCH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ActionValidatorTest {
    private val validator = ActionValidator()
    private val screen = Fixtures.screen()

    private fun check(action: DeviceAction) = validator.validate(action, screen)

    @Test
    fun validActionsPass() {
        listOf(
            DeviceAction.Tap(TargetRef.Node(SEARCH)),
            DeviceAction.Tap(TargetRef.Point(540, 1200)),
            DeviceAction.DoubleTap(TargetRef.Node(LIST)),
            DeviceAction.LongPress(TargetRef.Node(SEARCH)),
            DeviceAction.Swipe(Direction.LEFT),
            DeviceAction.Swipe(Direction.UP, distance = 0.3, containerId = LIST),
            DeviceAction.Scroll(Direction.DOWN),
            DeviceAction.Scroll(Direction.DOWN, containerId = LIST),
            DeviceAction.Pinch(zoomIn = true),
            DeviceAction.Global(GlobalNav.BACK),
            DeviceAction.OpenApp("com.google.android.youtube"),
            DeviceAction.TypeText(QUERY, "Java tutorials", submit = true),
        ).forEach { assertEquals(Validation.Valid, check(it), "for $it") }
    }

    @Test
    fun missingNodeRejected() {
        assertInvalid(check(DeviceAction.Tap(TargetRef.Node(99))), "not on screen")
    }

    @Test
    fun offscreenNodeRejected() {
        assertInvalid(check(DeviceAction.Tap(TargetRef.Node(OFFSCREEN))), "outside the window")
    }

    @Test
    fun pointOutsideWindowRejected() {
        assertInvalid(check(DeviceAction.Tap(TargetRef.Point(2000, 10))), "outside the window")
    }

    @Test
    fun passwordFieldNeverTyped() {
        assertInvalid(check(DeviceAction.TypeText(PASSWORD, "hunter2")), "password")
    }

    @Test
    fun typingNeedsEditableField() {
        assertInvalid(check(DeviceAction.TypeText(SEARCH, "x")), "not editable")
    }

    @Test
    fun blankOrHugeTextRejected() {
        assertInvalid(check(DeviceAction.TypeText(QUERY, "  ")), "empty")
        assertInvalid(check(DeviceAction.TypeText(QUERY, "a".repeat(ActionValidator.MAX_TEXT_LENGTH + 1))), "too long")
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, -0.1, 1.01])
    fun swipeDistanceOutOfRangeRejected(distance: Double) {
        assertInvalid(check(DeviceAction.Swipe(Direction.UP, distance = distance)), "distance")
    }

    @Test
    fun scrollContainerMustBeScrollable() {
        assertInvalid(check(DeviceAction.Scroll(Direction.DOWN, containerId = SEARCH)), "not scrollable")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "youtube", "com..x", "com.x;rm", "1com.x"])
    fun badPackageNamesRejected(name: String) {
        assertInvalid(check(DeviceAction.OpenApp(name)), "package")
    }

    private fun assertInvalid(result: Validation, reasonContains: String) {
        assertTrue(result is Validation.Invalid, "expected Invalid, got $result")
        val reason = (result as Validation.Invalid).reason
        assertTrue(reason.contains(reasonContains), "reason '$reason' should mention '$reasonContains'")
    }
}
