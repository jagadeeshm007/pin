package io.github.jagadeeshm007.pin.accessibility

import android.graphics.Rect
import io.github.jagadeeshm007.pin.action.ScreenSource
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Snapshots the active window off the main thread (spec AC-7). */
class AccessibilityScreenSource(
    private val service: PinAccessibilityService,
    private val builder: SnapshotBuilder = SnapshotBuilder(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ScreenSource {
    override suspend fun current(): ScreenSnapshot = withContext(dispatcher) { capture() }

    override suspend fun awaitChange(since: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot {
        val changed = withTimeoutOrNull(timeoutMs) {
            service.contentVersion.first { it > since.contentVersion }
        }
        // Events arrive in bursts; let the burst finish before reading the tree.
        if (changed != null) delay(SETTLE_MS)
        return current()
    }

    private fun capture(): ScreenSnapshot {
        val version = service.contentVersion.value
        val root = service.rootInActiveWindow
            ?: return ScreenSnapshot("", "", Bounds(0, 0, 0, 0), emptyList(), version)
        try {
            val window = service.windows.firstOrNull { it.isActive }
            val rect = Rect()
            if (window != null) window.getBoundsInScreen(rect) else root.getBoundsInScreen(rect)
            return builder.build(
                root = AccessibilityRawNode(root),
                packageName = root.packageName?.toString().orEmpty(),
                windowTitle = window?.title?.toString().orEmpty(),
                windowBounds = rect.toBounds(),
                contentVersion = version,
            )
        } finally {
            AccessibilityRawNode.releaseNode(root)
        }
    }

    companion object {
        const val SETTLE_MS = 120L
    }
}
