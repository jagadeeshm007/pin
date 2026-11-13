package io.github.jagadeeshm007.pin.speech

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import io.github.jagadeeshm007.pin.R
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * The microphone session (spec PIN-21), in the `:speech` process (ADR-0007). Owns the only
 * AudioRecord in the app. Audio stays here; text and state go to bound clients as
 * [SpeechEvent] messages. Started only from a user action (Android forbids starting a
 * microphone foreground service from the background).
 */
class ListeningService : Service() {
    private val clients = CopyOnWriteArrayList<Messenger>()
    private val main = Handler(Looper.getMainLooper())
    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) {
                MSG_REGISTER -> msg.replyTo?.let { client ->
                    clients += client
                    Log.d(TAG, "client registered, clients=${clients.size}, state=$state")
                    send(client, SpeechEvent.State(state, detail))
                }

                MSG_UNREGISTER -> msg.replyTo?.let { clients -= it }
            }
            true
        },
    )

    @Volatile
    private var running = false

    @Volatile
    private var silenced = false
    private var state = ListenState.STOPPED
    private var detail: String? = null
    private var worker: Thread? = null
    private var recorder: AudioRecord? = null
    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            if (!running) return
            val mine = recorder?.activeRecordingConfiguration ?: return
            silenced = mine.isClientSilenced
            publish(
                if (silenced) ListenState.PAUSED else ListenState.LISTENING,
                if (silenced) "microphone in use" else null,
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder = incoming.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSession()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        if (!running) startSession()
        // Never restart on its own: the microphone must be a user decision (blueprint 12).
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSession()
        super.onDestroy()
    }

    private fun startSession() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            publish(ListenState.ERROR, "microphone permission not granted")
            stopSelf()
            return
        }
        running = true
        publish(ListenState.LOADING, null)
        worker = Thread({ runAudio() }, "pin-audio").also { it.start() }
    }

    private fun stopSession() {
        running = false
        worker?.join(STOP_JOIN_MS)
        worker = null
        publish(ListenState.STOPPED, null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("MissingPermission") // checked in startSession
    private fun runAudio() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val engine = try {
            SpeechEngine.load(File(filesDir, MODELS_DIR))
        } catch (e: IllegalArgumentException) {
            publish(ListenState.ERROR, e.message)
            main.post { stopSession() }
            return
        }
        if (!running) {
            // Stop was pressed while models were loading (found on device): never start the mic.
            engine.release()
            return
        }
        val minBuffer = AudioRecord.getMinBufferSize(
            SpeechSegmenter.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SpeechSegmenter.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SpeechSegmenter.SAMPLE_RATE * BUFFER_SECONDS * 2),
        )
        recorder = record
        record.registerAudioRecordingCallback(Executors.newSingleThreadExecutor(), recordingCallback)
        val segmenter = SpeechSegmenter()
        val pcm = ShortArray(SpeechSegmenter.WINDOW)
        var utteranceStart = 0L
        var utteranceSamples = 0L
        var decodeNs = 0L
        var windows = 0
        var peak = 0
        var speechWindows = 0
        var lastStatsAt = SystemClock.elapsedRealtime()
        try {
            record.startRecording()
            if (running) publish(ListenState.LISTENING, null)
            while (running) {
                val n = record.read(pcm, 0, pcm.size)
                if (n != pcm.size) continue
                if (silenced) {
                    segmenter.reset()
                    engine.cancel()
                    continue
                }
                for (v in pcm) peak = maxOf(peak, kotlin.math.abs(v.toInt()))
                val window = FloatArray(n) { pcm[it] / PCM_SCALE }
                val speech = engine.speechProbability(window) > VAD_THRESHOLD
                windows++
                if (speech) speechWindows++
                val now = SystemClock.elapsedRealtime()
                if (now - lastStatsAt >= STATS_EVERY_MS) {
                    Log.d(TAG, "vad: $speechWindows of $windows windows were speech, peak level $peak of 32767")
                    peak = 0
                    windows = 0
                    speechWindows = 0
                    lastStatsAt = now
                }
                for (action in segmenter.push(window, speech)) {
                    val t = System.nanoTime()
                    when (action) {
                        is SegmentAction.Begin -> {
                            utteranceStart = SystemClock.elapsedRealtime()
                            utteranceSamples = action.samples.size.toLong()
                            decodeNs = 0
                            engine.begin(action.samples)?.let { if (it.isNotEmpty()) publish(SpeechEvent.Partial(it)) }
                        }

                        is SegmentAction.Feed -> {
                            utteranceSamples += action.samples.size
                            engine.feed(action.samples)?.let { if (it.isNotEmpty()) publish(SpeechEvent.Partial(it)) }
                        }

                        is SegmentAction.End -> {
                            val text = engine.finish()
                            decodeNs += System.nanoTime() - t
                            val audioMs = utteranceSamples * MS_PER_S / SpeechSegmenter.SAMPLE_RATE
                            Log.d(
                                TAG,
                                "utterance ${action.reason} audio=${audioMs}ms wall=${SystemClock.elapsedRealtime() - utteranceStart}ms",
                            )
                            if (text.isNotEmpty()) publish(SpeechEvent.Final(text, audioMs, decodeNs / NS_PER_MS))
                        }
                    }
                    if (action !is SegmentAction.End) decodeNs += System.nanoTime() - t
                }
            }
        } finally {
            record.unregisterAudioRecordingCallback(recordingCallback)
            runCatching { record.stop() }
            record.release()
            recorder = null
            engine.release()
        }
    }

    private fun publish(newState: ListenState, newDetail: String?) {
        Log.d(TAG, "state $newState (${newDetail ?: "-"}), clients=${clients.size}")
        state = newState
        detail = newDetail
        publish(SpeechEvent.State(newState, newDetail))
    }

    private fun publish(event: SpeechEvent) {
        clients.forEach { send(it, event) }
    }

    private fun send(client: Messenger, event: SpeechEvent) {
        val msg = Message.obtain(null, MSG_EVENT).apply {
            data = Bundle().apply { event.toFields().forEach { (k, v) -> putString(k, v) } }
        }
        try {
            client.send(msg)
        } catch (e: RemoteException) {
            // The client process is gone; drop it. Logged without content.
            Log.d(TAG, "client gone: ${e.javaClass.simpleName}")
            clients -= client
        }
    }

    /** The session notification. Shares channel and id with the main process's task notifier. */
    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
                .apply { setSound(null, null) },
        )
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ListeningService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.listening_no_wake_word))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stat_pin),
                    getString(R.string.listening_stop),
                    stop,
                ).build(),
            )
            .build()
    }

    companion object {
        const val ACTION_START = "io.github.jagadeeshm007.pin.speech.START"
        const val ACTION_STOP = "io.github.jagadeeshm007.pin.speech.STOP"
        const val MSG_REGISTER = 1
        const val MSG_UNREGISTER = 2
        const val MSG_EVENT = 3
        const val MODELS_DIR = "models/v1"
        private const val CHANNEL_ID = "task_progress"
        private const val NOTIFICATION_ID = 1
        private const val VAD_THRESHOLD = 0.5f
        private const val PCM_SCALE = 32768f
        private const val BUFFER_SECONDS = 1
        private const val STOP_JOIN_MS = 500L
        private const val MS_PER_S = 1000L
        private const val NS_PER_MS = 1_000_000L
        private const val TAG = "PinSpeech"
        private const val STATS_EVERY_MS = 5_000L

        /** Starts the session; call only from a user action in the foreground. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, ListeningService::class.java).setAction(ACTION_START))
        }

        /** Stops the session. */
        fun stop(context: Context) {
            context.startService(Intent(context, ListeningService::class.java).setAction(ACTION_STOP))
        }
    }
}
