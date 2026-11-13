package io.github.jagadeeshm007.pin.speech

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Transcribes utterances with Android's speech recognizer (maintainer decision, PIN-21),
 * preferring the on-device service. Pin keeps the microphone: each utterance is streamed
 * to the recognizer through a pipe (`EXTRA_AUDIO_SOURCE`, Android 13+), so the system never
 * opens the mic and voice detection stays in Pin (ADR-0007). Calls may come from the audio
 * thread; recognizer calls are moved to the main thread as Android requires. Needs API 33
 * for the audio pipe and the language check.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class AndroidTranscriber(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (text: String, decodeMs: Long) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "pin-asr-pipe") }
    private var recognizer: SpeechRecognizer? = null
    private var sink: FileOutputStream? = null
    private var endedAt = 0L

    /** Latest partial text; Google's on-device service sends the final text only here. */
    private var lastPartial: String? = null

    /** Our copy of the pipe's read end; held until the recognizer finishes with it. */
    private var readSide: ParcelFileDescriptor? = null

    /** Language sent to the recognizer; replaced by an installed English pack once known. */
    @Volatile
    private var language = PREFERRED_LANGUAGE

    /** True when an on-device recognizer is available (checked once at start). */
    val onDevice: Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    init {
        // Created up front so the language check finishes before the first utterance.
        main.post { if (recognizer == null) recognizer = create() }
    }

    /** Starts an utterance with [samples] (pre-roll plus the first speech window). */
    fun begin(samples: FloatArray) {
        val pipe = ParcelFileDescriptor.createPipe()
        // The stream owns the write end; closing it is the recognizer's end of audio.
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        val readSide = pipe[0]
        writer.execute {
            sink?.closeQuietly()
            sink = out
        }
        main.post { listen(readSide) }
        // Leading silence keeps the first word (the same fix as the sherpa warm-up).
        feed(FloatArray(LEAD_SILENCE_SAMPLES))
        feed(samples)
    }

    /** Streams more audio of the current utterance. */
    fun feed(samples: FloatArray) {
        val bytes = toPcm16(samples)
        writer.execute {
            try {
                sink?.write(bytes)
            } catch (e: IOException) {
                Log.d(TAG, "pipe closed: ${e.javaClass.simpleName}")
            }
        }
    }

    /** Ends the utterance; the final text arrives through [onFinal]. */
    fun end() {
        writer.execute {
            endedAt = SystemClock.elapsedRealtime()
            sink?.closeQuietly()
            sink = null
        }
    }

    /** Drops the utterance in progress. */
    fun cancel() {
        writer.execute {
            sink?.closeQuietly()
            sink = null
        }
        main.post { recognizer?.cancel() }
    }

    fun release() {
        cancel()
        main.post {
            recognizer?.destroy()
            recognizer = null
        }
        writer.shutdown()
    }

    private fun listen(readSide: ParcelFileDescriptor) {
        val r = recognizer ?: create().also { recognizer = it }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, SpeechSegmenter.SAMPLE_RATE)
        }
        this.readSide?.close()
        this.readSide = readSide
        lastPartial = null
        // startListening is asynchronous; the read side is closed in done(), after the
        // recognizer reports (closing it here gave ERROR_CLIENT on the Nothing A059).
        r.startListening(intent)
    }

    private fun create(): SpeechRecognizer {
        val r = if (onDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        r.setRecognitionListener(
            object : RecognitionListener {
                override fun onPartialResults(partial: Bundle?) {
                    best(partial)?.let {
                        lastPartial = it
                        onPartial(it)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val ms = if (endedAt > 0) SystemClock.elapsedRealtime() - endedAt else 0
                    // com.google.android.as returns an empty bundle here (device test, PIN-21).
                    val text = best(results) ?: lastPartial
                    done()
                    onFinal(text.orEmpty(), ms)
                }

                override fun onError(error: Int) {
                    done()
                    onFailure(errorName(error))
                }

                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "recognizer ready")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "recognizer heard speech")
                }

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    Log.d(TAG, "recognizer end of speech")
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        Log.d(TAG, "recognizer created, onDevice=$onDevice")
        r.checkRecognitionSupport(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            writer,
            object : RecognitionSupportCallback {
                override fun onSupportResult(support: RecognitionSupport) {
                    language = pickLanguage(support.installedOnDeviceLanguages)
                    Log.d(TAG, "installed languages ${support.installedOnDeviceLanguages}, using $language")
                }

                override fun onError(error: Int) {
                    Log.d(TAG, "support check: ${errorName(error)}")
                }
            },
        )
        return r
    }

    private fun done() {
        lastPartial = null
        readSide?.close()
        readSide = null
    }

    private fun best(b: Bundle?): String? =
        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

    private fun FileOutputStream.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
            // Already closed by the recognizer side.
        }
    }

    companion object {
        private const val TAG = "PinSpeech"
        private const val PREFERRED_LANGUAGE = "en-US"
        private const val LEAD_SILENCE_SAMPLES = SpeechSegmenter.SAMPLE_RATE / 2

        /**
         * The preferred language if its pack is installed, else the first installed English
         * pack (the Nothing A059 has only en-IN and refuses en-US), else the preferred one.
         */
        internal fun pickLanguage(installed: List<String>): String = when {
            installed.any { it.equals(PREFERRED_LANGUAGE, ignoreCase = true) } -> PREFERRED_LANGUAGE
            else -> installed.firstOrNull { it.lowercase().startsWith("en") } ?: PREFERRED_LANGUAGE
        }

        private fun toPcm16(samples: FloatArray): ByteArray {
            val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            samples.forEach { buf.putShort((it.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort()) }
            return buf.array()
        }

        private fun errorName(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH -> "no match"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
            SpeechRecognizer.ERROR_AUDIO -> "audio error"
            SpeechRecognizer.ERROR_CLIENT -> "client error"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "language not supported"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "language pack not downloaded"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "insufficient permissions"
            else -> "error $code"
        }
    }
}
