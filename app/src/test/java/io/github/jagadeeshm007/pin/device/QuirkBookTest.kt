package io.github.jagadeeshm007.pin.device

import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.action.ExecutionStrategy.GESTURE
import io.github.jagadeeshm007.pin.action.ExecutionStrategy.NODE_ACTION
import io.github.jagadeeshm007.pin.action.ExecutionStrategy.SYSTEM
import io.github.jagadeeshm007.pin.action.GlobalNav
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QuirkBookTest {
    private fun screen(pkg: String) = ScreenSnapshot(pkg, "t", Bounds(0, 0, 10, 10), emptyList(), 1)
    private val scroll = DeviceAction.Scroll(Direction.DOWN)

    @Test
    fun countsNodeSuccessAndGestureRescuePerAppAndActionType() = runTest {
        val book = QuirkBook()
        val observer = QuirkObserver(book)
        // YouTube: node refused, gesture worked.
        observer.record(ActionEvent.AttemptStarted(scroll, NODE_ACTION, 1, screen("com.google.android.youtube")))
        observer.record(ActionEvent.AttemptFailed(scroll, NODE_ACTION, 1, "refused"))
        observer.record(ActionEvent.AttemptStarted(scroll, GESTURE, 2, screen("com.google.android.youtube")))
        observer.record(ActionEvent.Succeeded(scroll, GESTURE, screen("com.google.android.youtube")))
        // Settings: node worked.
        observer.record(ActionEvent.AttemptStarted(scroll, NODE_ACTION, 1, screen("com.android.settings")))
        observer.record(ActionEvent.Succeeded(scroll, NODE_ACTION, screen("com.android.settings")))

        assertEquals(QuirkStats(nodeOk = 0, gestureRescued = 1), book.stats("com.google.android.youtube", "Scroll"))
        assertEquals(QuirkStats(nodeOk = 1, gestureRescued = 0), book.stats("com.android.settings", "Scroll"))
        assertTrue(book.prefersGesture("com.google.android.youtube", "Scroll"))
    }

    @Test
    fun systemActionsAreNotCounted() = runTest {
        val book = QuirkBook()
        val observer = QuirkObserver(book)
        val back = DeviceAction.Global(GlobalNav.BACK)
        observer.record(ActionEvent.AttemptStarted(back, SYSTEM, 1, screen("a.b")))
        observer.record(ActionEvent.Succeeded(back, SYSTEM, screen("a.b")))
        assertEquals(emptyMap<String, QuirkStats>(), book.all())
    }

    @Test
    fun preferenceNeedsEvidence() {
        val book = QuirkBook()
        assertEquals(false, book.prefersGesture("x.y", "Tap"))
        book.add("x.y", "Tap", nodeOk = true)
        book.add("x.y", "Tap", nodeOk = false)
        assertEquals(false, book.prefersGesture("x.y", "Tap"), "1 of 2 is not a pattern")
        book.add("x.y", "Tap", nodeOk = false)
        assertEquals(true, book.prefersGesture("x.y", "Tap"))
    }

    @Test
    fun roundTripsThroughJson() {
        val book = QuirkBook()
        book.add("com.google.android.youtube", "Scroll", nodeOk = false)
        assertEquals(book.all(), QuirkBook.fromJson(book.toJson()).all())
    }
}
