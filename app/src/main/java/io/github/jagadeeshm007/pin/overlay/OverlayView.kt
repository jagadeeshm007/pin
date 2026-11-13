package io.github.jagadeeshm007.pin.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import io.github.jagadeeshm007.pin.R

/**
 * Draws the whole overlay in one pass: the state pill, and either number badges or
 * a grid. Coordinates are screen pixels because the window covers the full display.
 */
class OverlayView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density

    private var pillText: String? = null
    private var pillKind = OverlayState.Kind.NEUTRAL
    private var numbers: NumberMap? = null
    private var grid: GridModel? = null

    private val pillBackground = fill(R.color.overlay_pill_background)
    private val pillTextPaint = text(R.color.overlay_pill_text, PILL_TEXT_SP)
    private val dot = fill(R.color.overlay_dot_neutral)
    private val badgeBackground = fill(R.color.overlay_badge_background)
    private val badgeOutline = stroke(R.color.overlay_badge_outline, 1f)
    private val badgeText = text(R.color.overlay_badge_text, BADGE_TEXT_SP).apply { isFakeBoldText = true }
    private val gridLine = stroke(R.color.overlay_grid_line, 2f)
    private val gridShade = fill(R.color.overlay_grid_shade)
    private val gridText = text(R.color.overlay_grid_text, GRID_TEXT_SP).apply { isFakeBoldText = true }

    /** Sets the pill; null hides it. */
    fun setPill(text: String?, kind: OverlayState.Kind) {
        pillText = text
        pillKind = kind
        dot.color = ContextCompat.getColor(context, dotColor(kind))
        invalidate()
    }

    /** Shows number badges, replacing any grid. */
    fun setNumbers(map: NumberMap?) {
        numbers = map
        if (map != null) grid = null
        invalidate()
    }

    /** Shows a grid, replacing any numbers. */
    fun setGrid(model: GridModel?) {
        grid = model
        if (model != null) numbers = null
        invalidate()
    }

    /** True when anything is visible. */
    val hasContent: Boolean get() = pillText != null || numbers != null || grid != null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        grid?.let { drawGrid(canvas, it) }
        numbers?.let { drawNumbers(canvas, it) }
        pillText?.let { drawPill(canvas, it) }
    }

    private fun drawPill(canvas: Canvas, label: String) {
        val padH = PILL_PAD_H_DP * density
        val dotR = DOT_RADIUS_DP * density
        val textW = pillTextPaint.measureText(label)
        val height = PILL_HEIGHT_DP * density
        val width = padH * 2 + dotR * 2 + DOT_GAP_DP * density + textW
        val left = (width(canvas) - width) / 2
        val top = PILL_TOP_DP * density
        val rect = RectF(left, top, left + width, top + height)
        canvas.drawRoundRect(rect, height / 2, height / 2, pillBackground)
        canvas.drawCircle(left + padH + dotR, rect.centerY(), dotR, dot)
        val baseline = rect.centerY() - (pillTextPaint.descent() + pillTextPaint.ascent()) / 2
        canvas.drawText(label, left + padH + dotR * 2 + DOT_GAP_DP * density, baseline, pillTextPaint)
    }

    private fun drawNumbers(canvas: Canvas, map: NumberMap) {
        val pad = BADGE_PAD_DP * density
        val minW = BADGE_MIN_WIDTH_DP * density
        val h = BADGE_HEIGHT_DP * density
        map.entries.forEach { e ->
            val label = e.number.toString()
            val w = maxOf(minW, badgeText.measureText(label) + pad * 2)
            val left = e.bounds.left.toFloat()
            val top = e.bounds.top.toFloat()
            val rect = RectF(left, top, left + w, top + h)
            canvas.drawRoundRect(rect, h / 4, h / 4, badgeBackground)
            canvas.drawRoundRect(rect, h / 4, h / 4, badgeOutline)
            val baseline = rect.centerY() - (badgeText.descent() + badgeText.ascent()) / 2
            canvas.drawText(label, rect.centerX() - badgeText.measureText(label) / 2, baseline, badgeText)
        }
    }

    private fun drawGrid(canvas: Canvas, model: GridModel) {
        val a = model.area
        canvas.drawRect(a.left.toFloat(), a.top.toFloat(), a.right.toFloat(), a.bottom.toFloat(), gridShade)
        model.cells.forEach { c ->
            val b = c.bounds
            canvas.drawRect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), gridLine)
            val label = c.number.toString()
            val baseline = b.centerY - (gridText.descent() + gridText.ascent()) / 2
            canvas.drawText(label, b.centerX - gridText.measureText(label) / 2, baseline, gridText)
        }
    }

    private fun width(canvas: Canvas): Float = canvas.width.toFloat()

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = ContextCompat.getColor(context, color)
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, widthDp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = ContextCompat.getColor(context, color)
        style = Paint.Style.STROKE
        strokeWidth = widthDp * density
    }

    private fun text(color: Int, sizeSp: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = ContextCompat.getColor(context, color)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics)
    }

    private fun dotColor(kind: OverlayState.Kind): Int = when (kind) {
        OverlayState.Kind.NEUTRAL -> R.color.overlay_dot_neutral
        OverlayState.Kind.ACTIVE -> R.color.overlay_dot_active
        OverlayState.Kind.BUSY -> R.color.overlay_dot_busy
        OverlayState.Kind.ATTENTION -> R.color.overlay_dot_attention
    }

    private companion object {
        const val PILL_TOP_DP = 40f
        const val PILL_HEIGHT_DP = 36f
        const val PILL_PAD_H_DP = 14f
        const val PILL_TEXT_SP = 15f
        const val DOT_RADIUS_DP = 5f
        const val DOT_GAP_DP = 8f
        const val BADGE_HEIGHT_DP = 22f
        const val BADGE_MIN_WIDTH_DP = 22f
        const val BADGE_PAD_DP = 5f
        const val BADGE_TEXT_SP = 13f
        const val GRID_TEXT_SP = 28f
    }
}
