package io.github.jagadeeshm007.pin.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.jagadeeshm007.pin.PinApp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Receives Stop, Confirm and Deny from the notification. Not exported: only Pin's
 * own immutable PendingIntents reach it (spec AC-6).
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PinApp
        val token = intent.getLongExtra(EXTRA_TOKEN, TaskNotifier.NO_TOKEN)
        Log.d(TAG, "notification action ${intent.action}")
        when (intent.action) {
            NotificationAction.STOP.name -> TaskControl.requestStop()
            NotificationAction.CONFIRM.name -> app.confirmations.answer(token, yes = true)
            NotificationAction.DENY.name -> app.confirmations.answer(token, yes = false)
        }
    }

    companion object {
        const val EXTRA_TOKEN = "token"
        private const val TAG = "PinNotification"
    }
}

/** App-wide stop signal; whoever runs a task collects it and cancels. */
object TaskControl {
    private val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once per Stop press. */
    val stopRequests: SharedFlow<Unit> = stops.asSharedFlow()

    /** Asks the running task to stop. */
    fun requestStop() {
        stops.tryEmit(Unit)
    }
}
