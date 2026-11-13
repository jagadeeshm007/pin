package io.github.jagadeeshm007.pin.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Dispatches planned strokes and suspends until the system reports the outcome. */
class GestureDispatcher(private val service: AccessibilityService) {
    /** Returns true when the gesture completed, false when it was refused or cancelled. */
    suspend fun dispatch(strokes: List<GestureStroke>): Boolean = withContext(Dispatchers.Main) {
        if (strokes.isEmpty()) return@withContext false
        val builder = GestureDescription.Builder()
        strokes.forEach { builder.addStroke(it.toStrokeDescription()) }
        val gesture = builder.build()
        suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            if (!service.dispatchGesture(gesture, callback, null) && cont.isActive) cont.resume(false)
        }
    }

    private fun GestureStroke.toStrokeDescription(): GestureDescription.StrokeDescription {
        val path = Path()
        path.moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { path.lineTo(it.x, it.y) }
        return GestureDescription.StrokeDescription(path, startMs, durationMs)
    }
}
