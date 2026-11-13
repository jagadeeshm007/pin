package io.github.jagadeeshm007.pin.device

import android.accessibilityservice.GestureDescription
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import io.github.jagadeeshm007.pin.accessibility.toBounds
import io.github.jagadeeshm007.pin.core.screen.Bounds

/**
 * Reads the device profile from public APIs (spec PIN-15). [context] must be able to
 * reach the window manager for metrics: an activity or the accessibility service.
 */
class DeviceProfileCollector(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    /** Collects the profile now. Cheap; safe off the main thread. */
    fun collect(): DeviceProfile {
        val metrics = context.getSystemService(WindowManager::class.java).currentWindowMetrics
        val insets = metrics.windowInsets
        val screen = metrics.bounds.toBounds()
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        return DeviceProfile(
            collectedAt = clock(),
            sdk = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE,
            securityPatch = Build.VERSION.SECURITY_PATCH,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            screenWidthPx = screen.width,
            screenHeightPx = screen.height,
            densityDpi = context.resources.displayMetrics.densityDpi,
            statusBarPx = insets.getInsets(WindowInsets.Type.statusBars()).top,
            navigationBarPx = insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
            navigationMode = NavigationMode.classify(
                tappableBottomPx = insets.getInsets(WindowInsets.Type.tappableElement()).bottom,
                gestureBottomPx = insets.getInsets(WindowInsets.Type.mandatorySystemGestures()).bottom,
            ),
            cutouts = cutouts(insets, screen),
            maxGestureStrokes = GestureDescription.getMaxStrokeCount(),
            maxGestureDurationMs = GestureDescription.getMaxGestureDuration(),
            ramBytes = memory.totalMem,
            ramClass = RamClass.of(memory.totalMem),
            cpuCores = Runtime.getRuntime().availableProcessors(),
        )
    }

    private fun cutouts(insets: WindowInsets, screen: Bounds): List<Cutout> =
        insets.displayCutout?.boundingRects.orEmpty().filter { !it.isEmpty }.map { rect ->
            val b = rect.toBounds()
            Cutout(listOf(b.left, b.top, b.right, b.bottom), CutoutSide.of(b, screen), CutoutShape.of(b, screen))
        }
}
