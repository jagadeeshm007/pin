package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.accessibility.ScrollChoice.Use
import io.github.jagadeeshm007.pin.action.Direction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrollChoiceTest {
    @Test
    fun usesTheDirectionalActionWhenOffered() {
        assertEquals(
            Use.DIRECTIONAL,
            ScrollChoice.pick(Direction.DOWN, setOf(Direction.DOWN, Direction.UP), "RecyclerView", 0, 0),
        )
    }

    @Test
    fun neverTurnsDownIntoForwardOnANodeWithOtherDirections() {
        // WhatsApp's tab pager offered sideways scrolling; "scroll down" moved to the next tab.
        assertNull(ScrollChoice.pick(Direction.DOWN, setOf(Direction.LEFT, Direction.RIGHT), "ViewPager", 0, 0))
    }

    @Test
    fun forwardOnlyPagersAreHorizontal() {
        assertNull(ScrollChoice.pick(Direction.DOWN, emptySet(), "androidx.viewpager.widget.ViewPager", 0, 0))
        assertEquals(Use.FORWARD_BACKWARD, ScrollChoice.pick(Direction.RIGHT, emptySet(), "ViewPager", 0, 0))
        assertNull(ScrollChoice.pick(Direction.UP, emptySet(), "android.widget.HorizontalScrollView", 0, 0))
    }

    @Test
    fun oneRowCollectionsAreHorizontal() {
        assertNull(ScrollChoice.pick(Direction.DOWN, emptySet(), "RecyclerView", 1, 8))
        assertEquals(Use.FORWARD_BACKWARD, ScrollChoice.pick(Direction.DOWN, emptySet(), "RecyclerView", 20, 1))
    }

    @Test
    fun plainListsScrollVertically() {
        assertEquals(Use.FORWARD_BACKWARD, ScrollChoice.pick(Direction.DOWN, emptySet(), "ListView", 0, 0))
        assertNull(ScrollChoice.pick(Direction.LEFT, emptySet(), "ListView", 0, 0))
    }

    @Test
    fun horizontalRoleCheck() {
        assertTrue(ScrollChoice.looksHorizontal("ViewPager"))
        assertFalse(ScrollChoice.looksHorizontal("RecyclerView"))
    }
}
