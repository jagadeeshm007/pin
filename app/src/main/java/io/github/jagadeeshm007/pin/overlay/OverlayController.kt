package io.github.jagadeeshm007.pin.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.annotation.MainThread
import io.github.jagadeeshm007.pin.R

/**
 * Owns Pin's overlay window. The window is TYPE_ACCESSIBILITY_OVERLAY (no extra
 * permission) and never takes touches or focus, so the app underneath keeps working
 * and gestures pass straight through (spec AC-1). Call only on the main thread.
 */
class OverlayController(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: OverlayView? = null

    /** The marks currently shown, so a chosen number or cell can be resolved. */
    var numbers: NumberMap? = null
        private set
    var grid: GridModel? = null
        private set

    /** Shows [state] in the pill, with an optional detail such as the heard command. */
    @MainThread
    fun showState(state: OverlayState, detail: String? = null) {
        val text = if (state == OverlayState.IDLE) {
            null
        } else {
            val base = service.getString(state.text)
            detail?.let { service.getString(R.string.overlay_with_detail, base, it) } ?: base
        }
        ensureView().setPill(text, state.kind)
        removeIfEmpty()
    }

    /** Shows number badges for [map]. */
    @MainThread
    fun showNumbers(map: NumberMap) {
        numbers = map
        grid = null
        ensureView().setNumbers(map)
    }

    /** Shows [model] as a grid. */
    @MainThread
    fun showGrid(model: GridModel) {
        grid = model
        numbers = null
        ensureView().setGrid(model)
    }

    /** Removes numbers and grid, keeping the pill. */
    @MainThread
    fun clearMarks() {
        numbers = null
        grid = null
        view?.setNumbers(null)
        view?.setGrid(null)
        removeIfEmpty()
    }

    /** Removes the window entirely. */
    @MainThread
    fun remove() {
        numbers = null
        grid = null
        view?.let { windowManager.removeViewImmediate(it) }
        view = null
    }

    private fun ensureView(): OverlayView = view ?: OverlayView(service).also {
        windowManager.addView(it, layoutParams())
        view = it
    }

    private fun removeIfEmpty() {
        if (view?.hasContent == false) remove()
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "Pin overlay"
    }
}
