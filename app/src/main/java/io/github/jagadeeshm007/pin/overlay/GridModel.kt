package io.github.jagadeeshm007.pin.overlay

import io.github.jagadeeshm007.pin.core.screen.Bounds

/** One numbered grid cell. */
data class GridCell(val number: Int, val bounds: Bounds) {
    /** Where a tap on this cell lands. */
    val center: Pair<Int, Int> get() = bounds.centerX to bounds.centerY
}

/**
 * A 3 by 3 grid over [area] for targets without usable labels ("show grid", blueprint
 * 6.1). Choosing a cell either taps its centre or refines into a smaller grid.
 */
class GridModel(val area: Bounds) {
    val cells: List<GridCell> = buildCells()

    /** True while cells are large enough to split again. */
    val canRefine: Boolean get() = cells.all {
        it.bounds.width >= MIN_CELL_PX * SIDE &&
            it.bounds.height >= MIN_CELL_PX * SIDE
    }

    /** The cell numbered [number], or null. */
    fun cell(number: Int): GridCell? = cells.firstOrNull { it.number == number }

    /** A grid over cell [number], or null when the cell is unknown or too small to split. */
    fun refine(number: Int): GridModel? {
        if (!canRefine) return null
        return cell(number)?.let { GridModel(it.bounds) }
    }

    private fun buildCells(): List<GridCell> {
        val xs = edges(area.left, area.right)
        val ys = edges(area.top, area.bottom)
        return (0 until SIDE).flatMap { row ->
            (0 until SIDE).map { col ->
                GridCell(row * SIDE + col + 1, Bounds(xs[col], ys[row], xs[col + 1], ys[row + 1]))
            }
        }
    }

    /** Four edges splitting [start, end) into three parts that cover it exactly. */
    private fun edges(start: Int, end: Int): List<Int> = (0..SIDE).map { start + (end - start) * it / SIDE }

    companion object {
        const val SIDE = 3

        /** Below this a finger-sized target cannot be split further. */
        const val MIN_CELL_PX = 48
    }
}
