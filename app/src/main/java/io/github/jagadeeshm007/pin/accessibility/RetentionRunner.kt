package io.github.jagadeeshm007.pin.accessibility

import android.util.Log
import io.github.jagadeeshm007.pin.PinApp
import kotlinx.coroutines.delay

/**
 * Applies history retention when the service connects and then once a day while it
 * stays connected (PIN-14 spec Q1: no WorkManager). Runs in the service's scope, so
 * it stops when the service does.
 */
object RetentionRunner {
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val TAG = "PinHistory"

    /** Never returns while the scope is active. */
    suspend fun runWhileConnected(app: PinApp) {
        while (true) {
            val deleted = app.history.applyRetention(app.retention)
            Log.d(TAG, "retention: $deleted session(s) deleted")
            delay(DAY_MS)
        }
    }
}
