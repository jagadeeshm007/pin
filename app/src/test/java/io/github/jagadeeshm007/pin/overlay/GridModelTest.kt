package io.github.jagadeeshm007.pin.overlay

import io.github.jagadeeshm007.pin.core.screen.Bounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GridModelTest {
    private val window = Bounds(0, 0, 1080, 2400)

    @Test
    fun splitsIntoNineNumberedCellsRowByRow() {
        val grid = GridModel(window)
        assertEquals((1..9).toList(), grid.cells.map { it.number })
        assertEquals(Bounds(0, 0, 360, 800), grid.cells[0].bounds)
        assertEquals(Bounds(720, 1600, 1080, 2400), grid.cells[8].bounds)
    }

    @Test
    fun cellsCoverTheAreaWithoutGapsWhenSizesDoNotDivideEvenly() {
        val grid = GridModel(Bounds(0, 0, 1000, 1000))
        assertEquals(1000, grid.cells.filter { it.number in 1..3 }.sumOf { it.bounds.width })
        assertEquals(1000, grid.cells.filter { it.number % 3 == 1 }.sumOf { it.bounds.height })
    }

    @Test
    fun refiningACellGivesNineSmallerCellsInsideIt() {
        val refined = GridModel(window).refine(5)!!
        val parent = Bounds(360, 800, 720, 1600)
        assertEquals(parent, refined.area)
        refined.cells.forEach { assertTrue(it.bounds.isInside(parent)) }
    }

    @Test
    fun stopsRefiningBelowTheMinimumCellSize() {
        var grid = GridModel(window)
        while (grid.canRefine) grid = grid.refine(1)!!
        assertFalse(grid.canRefine)
        assertTrue(grid.cells.first().bounds.width < GridModel.MIN_CELL_PX * 3)
        assertNull(grid.refine(1))
    }

    @Test
    fun unknownCellIsRejected() {
        assertNull(GridModel(window).cell(10))
        assertNull(GridModel(window).refine(0))
    }

    @Test
    fun cellCentreIsTheTapPoint() {
        val cell = GridModel(window).cell(1)!!
        assertEquals(180 to 400, cell.center)
    }
}
