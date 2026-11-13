package io.github.jagadeeshm007.pin.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import io.github.jagadeeshm007.pin.PinApp
import io.github.jagadeeshm007.pin.device.DeviceProfileCollector
import io.github.jagadeeshm007.pin.overlay.ConfirmCardController
import io.github.jagadeeshm007.pin.overlay.OverlayController
import io.github.jagadeeshm007.pin.speech.SpeechBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private var confirmCard: ConfirmCardController? = null
    private val scope = MainScope()

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        val broker = (applicationContext as PinApp).confirmations
        val card = ConfirmCardController(this, broker).also { confirmCard = it }
        scope.launch { broker.pending.collect { card.render(it) } }
        scope.launch { RetentionRunner.runWhileConnected(applicationContext as PinApp) }
        scope.launch { refreshProfile(applicationContext as PinApp) }
        scope.launch { SpeechBridge(applicationContext as PinApp) { overlay }.run() }
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

    /** Collects the device profile and loads quirks (PIN-15). */
    private suspend fun refreshProfile(app: PinApp) {
        val profile =
            withContext(Dispatchers.Default) { DeviceProfileCollector(this@PinAccessibilityService).collect() }
        app.profiles.saveProfile(profile)
        app.quirks = app.profiles.loadQuirks()
    }

    private fun disconnect() {
        AccessibilityRegistry.detach(this)
        overlay?.remove()
        overlay = null
        confirmCard?.remove()
        confirmCard = null
        scope.cancel()
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
