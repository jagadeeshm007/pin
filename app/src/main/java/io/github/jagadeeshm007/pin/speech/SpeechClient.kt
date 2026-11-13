package io.github.jagadeeshm007.pin.speech

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Main-process side of the speech link (ADR-0007): binds to [ListeningService] and turns
 * its messages into flows. If the speech process dies, [state] becomes STOPPED and device
 * control keeps running (spec PIN-21 AC-6).
 */
class SpeechClient(private val context: Context) {
    private val _state = MutableStateFlow(ListenState.STOPPED to (null as String?))
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = EVENT_BUFFER)
    private var service: Messenger? = null
    private var bound = false

    /** Latest session state and detail. */
    val state: StateFlow<Pair<ListenState, String?>> = _state.asStateFlow()

    /** Every event from the speech process, including partial and final text. */
    val events: SharedFlow<SpeechEvent> = _events.asSharedFlow()

    private val inbox = Messenger(
        Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == ListeningService.MSG_EVENT) {
                val fields = msg.data.keySet().associateWith { msg.data.getString(it).orEmpty() }
                SpeechEvent.fromFields(fields)?.let { event ->
                    if (event is SpeechEvent.State) Log.d(TAG, "state ${event.state}")
                    if (event is SpeechEvent.State) _state.value = event.state to event.detail
                    _events.tryEmit(event)
                }
            }
            true
        },
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "connected to speech process")
            val messenger = Messenger(binder)
            service = messenger
            send(messenger, ListeningService.MSG_REGISTER)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _state.value = ListenState.STOPPED to "speech process ended"
        }
    }

    /** Starts listening (call from a user action) and connects to the session. */
    fun start() {
        ListeningService.start(context)
        connect()
    }

    /** Stops listening. */
    fun stop() {
        ListeningService.stop(context)
    }

    /** Binds to a running session without starting one. */
    fun connect() {
        if (bound) return
        bound = context.bindService(Intent(context, ListeningService::class.java), connection, 0)
        Log.d(TAG, "bind requested: $bound")
    }

    private fun send(target: Messenger, what: Int) {
        try {
            target.send(Message.obtain(null, what).apply { replyTo = inbox })
        } catch (_: RemoteException) {
            service = null
        }
    }

    private companion object {
        const val EVENT_BUFFER = 32
        const val TAG = "PinSpeechClient"
    }
}
