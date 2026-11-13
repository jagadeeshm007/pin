package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.core.screen.Bounds

/** A point on screen in pixels. */
data class GesturePoint(val x: Float, val y: Float)

/** One finger: a polyline, when it starts, and how long it takes. */
data class GestureStroke(val points: List<GesturePoint>, val startMs: Long, val durationMs: Long)

/**
 * Plans gesture strokes as plain data (blueprint 6.2). [GestureDispatcher] turns them
 * into a platform GestureDescription. Limits come from the platform at runtime
 * (`GestureDescription.getMaxGestureDuration` and `getMaxStrokeCount`).
 */
class GesturePlanner(private val window: Bounds, private val maxDurationMs: Long, private val maxStrokes: Int) {
    /** A short press at the point. */
    fun tap(x: Int, y: Int): List<GestureStroke> = listOf(stroke(listOf(point(x, y)), 0, TAP_MS))

    /** Two taps separated by a pause short enough to count as a double tap. */
    fun doubleTap(x: Int, y: Int): List<GestureStroke> {
        val p = listOf(point(x, y))
        val first = stroke(p, 0, DOUBLE_TAP_PRESS_MS)
        val secondStart = minOf(DOUBLE_TAP_PRESS_MS + DOUBLE_TAP_GAP_MS, maxDurationMs - 1)
        return listOf(first, stroke(p, secondStart, DOUBLE_TAP_PRESS_MS)).take(maxOf(1, maxStrokes))
    }

    /** A press held past the long-press timeout. */
    fun longPress(x: Int, y: Int): List<GestureStroke> = listOf(stroke(listOf(point(x, y)), 0, LONG_PRESS_MS))

    /** The finger travels [distance] of [area] in [direction], centred in [area]. */
    fun swipe(direction: Direction, area: Bounds, distance: Double): List<GestureStroke> {
        val inner = shrink(area)
        val cx = inner.centerX.toFloat()
        val cy = inner.centerY.toFloat()
        val half = when (direction) {
            Direction.UP, Direction.DOWN -> area.height * distance.toFloat() / 2
            Direction.LEFT, Direction.RIGHT -> area.width * distance.toFloat() / 2
        }
        val (start, end) = when (direction) {
            Direction.UP -> GesturePoint(cx, cy + half) to GesturePoint(cx, cy - half)
            Direction.DOWN -> GesturePoint(cx, cy - half) to GesturePoint(cx, cy + half)
            Direction.LEFT -> GesturePoint(cx + half, cy) to GesturePoint(cx - half, cy)
            Direction.RIGHT -> GesturePoint(cx - half, cy) to GesturePoint(cx + half, cy)
        }
        return listOf(stroke(listOf(clampTo(start, inner), clampTo(end, inner)), 0, SWIPE_MS))
    }

    /** Scrolls content in [direction]: to see what is below, the finger moves up. */
    fun scroll(direction: Direction, area: Bounds): List<GestureStroke> {
        val finger = when (direction) {
            Direction.DOWN -> Direction.UP
            Direction.UP -> Direction.DOWN
            Direction.RIGHT -> Direction.LEFT
            Direction.LEFT -> Direction.RIGHT
        }
        return swipe(finger, area, SCROLL_DISTANCE)
    }

    /** Two fingers moving apart ([zoomIn]) or together around the centre. */
    fun pinch(zoomIn: Boolean, centerX: Int, centerY: Int): List<GestureStroke> {
        val inner = shrink(window)
        val c = clampTo(GesturePoint(centerX.toFloat(), centerY.toFloat()), inner)
        val near = PINCH_NEAR_PX
        val far = PINCH_FAR_PX
        fun at(offset: Float, sign: Int) = clampTo(GesturePoint(c.x + sign * offset, c.y + sign * offset), inner)
        val (fromOffset, toOffset) = if (zoomIn) near to far else far to near
        val a = stroke(listOf(at(fromOffset, -1), at(toOffset, -1)), 0, PINCH_MS)
        val b = stroke(listOf(at(fromOffset, 1), at(toOffset, 1)), 0, PINCH_MS)
        return listOf(a, b).take(maxOf(1, maxStrokes))
    }

    private fun stroke(points: List<GesturePoint>, startMs: Long, durationMs: Long): GestureStroke {
        val start = startMs.coerceIn(0, maxOf(0, maxDurationMs - 1))
        val duration = durationMs.coerceIn(1, maxOf(1, maxDurationMs - start))
        return GestureStroke(points, start, duration)
    }

    private fun point(x: Int, y: Int) = clampTo(GesturePoint(x.toFloat(), y.toFloat()), window)

    /** Keeps points one pixel inside the edge, where touches are still delivered. */
    private fun shrink(b: Bounds): Bounds = if (b.width > 2 && b.height > 2) {
        Bounds(b.left + 1, b.top + 1, b.right - 1, b.bottom - 1)
    } else {
        b
    }

    private fun clampTo(p: GesturePoint, b: Bounds) = GesturePoint(
        p.x.coerceIn(b.left.toFloat(), (b.right - 1).toFloat()),
        p.y.coerceIn(b.top.toFloat(), (b.bottom - 1).toFloat()),
    )

    companion object {
        const val TAP_MS = 50L
        const val DOUBLE_TAP_PRESS_MS = 40L
        const val DOUBLE_TAP_GAP_MS = 100L
        const val LONG_PRESS_MS = 800L
        const val SWIPE_MS = 300L
        const val PINCH_MS = 400L
        const val SCROLL_DISTANCE = 0.6
        const val PINCH_NEAR_PX = 60f
        const val PINCH_FAR_PX = 300f
    }
}
