package io.github.jagadeeshm007.pin.debug

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.action.GlobalNav
import io.github.jagadeeshm007.pin.debug.DebugCommand.Kind
import io.github.jagadeeshm007.pin.notification.TaskPhase
import io.github.jagadeeshm007.pin.notification.TaskStatus
import io.github.jagadeeshm007.pin.overlay.OverlayState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DebugCommandTest {
    @Test
    fun parsesEveryVerb() {
        val cases = mapOf(
            "dump" to DebugCommand.Dump,
            "tap Search" to DebugCommand.OnLabel(Kind.TAP, "Search"),
            "doubletap Photo" to DebugCommand.OnLabel(Kind.DOUBLE_TAP, "Photo"),
            "longpress Chat with Sam" to DebugCommand.OnLabel(Kind.LONG_PRESS, "Chat with Sam"),
            "type Search=Java tutorials" to DebugCommand.OnLabel(Kind.TYPE, "Search", "Java tutorials", false),
            "typego Search=Java" to DebugCommand.OnLabel(Kind.TYPE, "Search", "Java", true),
            "scroll down" to DebugCommand.Direct(DeviceAction.Scroll(Direction.DOWN)),
            "swipe LEFT" to DebugCommand.Direct(DeviceAction.Swipe(Direction.LEFT)),
            "pinch out" to DebugCommand.Direct(DeviceAction.Pinch(zoomIn = true)),
            "pinch in" to DebugCommand.Direct(DeviceAction.Pinch(zoomIn = false)),
            "open com.android.settings" to DebugCommand.Direct(DeviceAction.OpenApp("com.android.settings")),
            "back" to DebugCommand.Direct(DeviceAction.Global(GlobalNav.BACK)),
            "  home  " to DebugCommand.Direct(DeviceAction.Global(GlobalNav.HOME)),
            "quicksettings" to DebugCommand.Direct(DeviceAction.Global(GlobalNav.QUICK_SETTINGS)),
        )
        cases.forEach { (line, expected) -> assertEquals(expected, DebugCommand.parse(line), "for '$line'") }
    }

    @Test
    fun parsesOverlayCommands() {
        val cases = mapOf(
            "overlay listening" to DebugCommand.Overlay(OverlayState.LISTENING, null),
            "overlay heard scroll down" to DebugCommand.Overlay(OverlayState.HEARD, "scroll down"),
            "overlay IDLE" to DebugCommand.Overlay(OverlayState.IDLE, null),
            "hide" to DebugCommand.Hide,
            "numbers" to DebugCommand.Numbers,
            "tapnum 12" to DebugCommand.TapNumber(12),
            "grid" to DebugCommand.Grid,
            "gridpick 5" to DebugCommand.GridPick(5),
            "gridzoom 3" to DebugCommand.GridZoom(3),
        )
        cases.forEach { (line, expected) -> assertEquals(expected, DebugCommand.parse(line), "for '$line'") }
    }

    @Test
    fun parsesNotificationCommands() {
        val cases = mapOf(
            "notify listening Say hey pin" to DebugCommand.Notify(TaskStatus(TaskPhase.LISTENING, "Say hey pin")),
            "notify thinking" to DebugCommand.Notify(TaskStatus(TaskPhase.THINKING, "")),
            "notify step 2 5 Tap Search" to DebugCommand.Notify(TaskStatus(TaskPhase.ACTING, "Tap Search", 2, 5)),
            "notify done Playing video" to DebugCommand.Notify(TaskStatus(TaskPhase.DONE, "Playing video")),
            "notify clear" to DebugCommand.NotifyClear,
            "ask Delete the chat?" to DebugCommand.Ask("Delete the chat?"),
            "answer" to DebugCommand.LastAnswer,
        )
        cases.forEach { (line, expected) -> assertEquals(expected, DebugCommand.parse(line), "for '$line'") }
    }

    @ParameterizedTest
    @ValueSource(strings = ["notify", "notify flying", "notify step 2", "notify step x 5 a", "ask"])
    fun rejectsBadNotificationCommands(line: String) {
        assertNull(DebugCommand.parse(line))
    }

    @ParameterizedTest
    @ValueSource(strings = ["overlay", "overlay dancing", "tapnum", "tapnum x", "gridpick", "gridzoom 0x"])
    fun rejectsBadOverlayCommands(line: String) {
        assertNull(DebugCommand.parse(line))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "fly", "tap", "type Search", "type =x", "scroll sideways", "pinch", "open"])
    fun rejectsIncompleteCommands(line: String) {
        assertNull(DebugCommand.parse(line))
    }
}
