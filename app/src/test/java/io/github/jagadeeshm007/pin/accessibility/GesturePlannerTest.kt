package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.core.screen.Bounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class GesturePlannerTest {
    private val window = Bounds(0, 0, 1080, 2400)
    private val planner = GesturePlanner(window, maxDurationMs = 60_000, maxStrokes = 10)

    @Test
    fun tapIsOneShortStrokeAtThePoint() {
        val strokes = planner.tap(100, 200)
        assertEquals(1, strokes.size)
        assertEquals(listOf(GesturePoint(100f, 200f)), strokes[0].points)
        assertEquals(0, strokes[0].startMs)
        assertTrue(strokes[0].durationMs in 1..100)
    }

    @Test
    fun doubleTapIsTwoSeparatedTaps() {
        val (a, b) = planner.doubleTap(50, 60)
        assertTrue(b.startMs >= a.startMs + a.durationMs, "second tap starts after the first ends")
    }

    @Test
    fun longPressHoldsLongerThanTheSystemTimeout() {
        assertTrue(planner.longPress(10, 10).single().durationMs >= 600)
    }

    @ParameterizedTest
    @EnumSource(Direction::class)
    fun swipeMovesTheFingerInTheNamedDirection(direction: Direction) {
        val area = Bounds(0, 300, 1080, 2300)
        val stroke = planner.swipe(direction, area, distance = 0.5).single()
        val start = stroke.points.first()
        val end = stroke.points.last()
        when (direction) {
            Direction.UP -> assertTrue(end.y < start.y)
            Direction.DOWN -> assertTrue(end.y > start.y)
            Direction.LEFT -> assertTrue(end.x < start.x)
            Direction.RIGHT -> assertTrue(end.x > start.x)
        }
        val travelled = maxOf(kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
        val span = if (direction == Direction.UP || direction == Direction.DOWN) area.height else area.width
        assertEquals(span * 0.5f, travelled, 1f)
        stroke.points.forEach { assertTrue(area.contains(it.x.toInt(), it.y.toInt()), "$it outside $area") }
    }

    @Test
    fun scrollingDownMovesTheFingerUp() {
        val stroke = planner.scroll(Direction.DOWN, window).single()
        assertTrue(stroke.points.last().y < stroke.points.first().y)
    }

    @Test
    fun pinchOutSpreadsFingersAndPinchInBringsThemTogether() {
        val (a, b) = planner.pinch(zoomIn = true, centerX = 540, centerY = 1200)
        assertTrue(gap(a.points.last(), b.points.last()) > gap(a.points.first(), b.points.first()))
        val (c, d) = planner.pinch(zoomIn = false, centerX = 540, centerY = 1200)
        assertTrue(gap(c.points.last(), d.points.last()) < gap(c.points.first(), d.points.first()))
    }

    @Test
    fun pinchNearTheEdgeStaysInsideTheWindow() {
        planner.pinch(zoomIn = true, centerX = 5, centerY = 5).flatMap { it.points }.forEach {
            assertTrue(window.contains(it.x.toInt(), it.y.toInt()), "$it outside window")
        }
    }

    @Test
    fun durationsAreCappedByThePlatformLimit() {
        val tight = GesturePlanner(window, maxDurationMs = 100, maxStrokes = 10)
        val all =
            tight.longPress(1, 1) + tight.doubleTap(1, 1) + tight.swipe(Direction.UP, window, 0.5) +
                tight.pinch(true, 500, 500)
        all.forEach { assertTrue(it.startMs + it.durationMs <= 100, "$it exceeds 100 ms") }
    }

    @Test
    fun strokeCountRespectsThePlatformLimit() {
        val single = GesturePlanner(window, maxDurationMs = 60_000, maxStrokes = 1)
        assertEquals(1, single.pinch(true, 500, 500).size)
    }

    private fun gap(p: GesturePoint, q: GesturePoint): Float = kotlin.math.hypot(p.x - q.x, p.y - q.y)
}
