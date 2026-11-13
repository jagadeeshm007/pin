package io.github.jagadeeshm007.pin.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import io.github.jagadeeshm007.pin.overlay.OverlayController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pin's accessibility service. Event handling only bumps [contentVersion] so the
 * main thread stays free (budget: under 4 ms per event); snapshots and actions run
 * elsewhere through [AccessibilityScreenSource] and [AccessibilityDeviceController].
 */
class PinAccessibilityService : AccessibilityService() {
    private val version = MutableStateFlow(0L)

    /** Increases on every window or content event the service receives. */
    val contentVersion: StateFlow<Long> = version.asStateFlow()

    /** The overlay window; use only on the main thread. Null while disconnected. */
    var overlay: OverlayController? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        AccessibilityRegistry.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        version.update { it + 1 }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        AccessibilityRegistry.detach(this)
        overlay?.remove()
        overlay = null
    }
}

/** Where the rest of the app finds the running service, if the user enabled it. */
object AccessibilityRegistry {
    private val current = MutableStateFlow<PinAccessibilityService?>(null)

    /** The connected service, or null when it is off. */
    val service: StateFlow<PinAccessibilityService?> = current.asStateFlow()

    internal fun attach(service: PinAccessibilityService) {
        current.value = service
    }

    internal fun detach(service: PinAccessibilityService) {
        current.compareAndSet(service, null)
    }
}
