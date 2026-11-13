package io.github.jagadeeshm007.pin.speech

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/**
 * Voice detection and streaming recognition from sherpa-onnx (ADR-0003), loaded from the
 * downloaded model folder (ADR-0008). Lives only in the `:speech` process (ADR-0007).
 * Settings from spike PIN-2. Use from one thread.
 */
class SpeechEngine private constructor(private val vad: Vad, private val recognizer: OnlineRecognizer) {
    private var stream: OnlineStream? = null
    private var lastPartial = ""

    /** Probability that [window] (512 samples) contains speech. */
    fun speechProbability(window: FloatArray): Float = vad.compute(window)

    /** Starts an utterance with [samples]; returns a new partial or null. */
    fun begin(samples: FloatArray): String? {
        stream?.release()
        stream = recognizer.createStream().also { fresh ->
            // A fresh stream drops its first second or more (PIN-2); 1 s of silence first
            // recovered "scroll down please" from "UL" (device test, PIN-21).
            fresh.acceptWaveform(FloatArray(WARMUP_SAMPLES), SpeechSegmenter.SAMPLE_RATE)
            while (recognizer.isReady(fresh)) recognizer.decode(fresh)
        }
        lastPartial = ""
        return feed(samples)
    }

    /** Feeds [samples]; returns the partial text when it changed. */
    fun feed(samples: FloatArray): String? {
        val s = stream ?: return null
        s.acceptWaveform(samples, SpeechSegmenter.SAMPLE_RATE)
        while (recognizer.isReady(s)) recognizer.decode(s)
        val text = recognizer.getResult(s).text.trim()
        if (text == lastPartial) return null
        lastPartial = text
        return text
    }

    /** Ends the utterance and returns its final text (may be empty). */
    fun finish(): String {
        val s = stream ?: return ""
        s.acceptWaveform(FloatArray(TAIL_SAMPLES), SpeechSegmenter.SAMPLE_RATE)
        s.inputFinished()
        while (recognizer.isReady(s)) recognizer.decode(s)
        val text = recognizer.getResult(s).text.trim()
        s.release()
        stream = null
        return text
    }

    /** Drops any utterance in progress. */
    fun cancel() {
        stream?.release()
        stream = null
    }

    fun release() {
        cancel()
        vad.release()
        recognizer.release()
    }

    companion object {
        private const val TAIL_SAMPLES = SpeechSegmenter.SAMPLE_RATE * 3 / 10
        private const val ASR_THREADS = 2
        private const val WARMUP_SAMPLES = SpeechSegmenter.SAMPLE_RATE

        /** Model file names from the catalog (ADR-0008). */
        val REQUIRED = listOf(
            "vad-silero-v5.onnx",
            "asr-en-encoder.int8.onnx",
            "asr-en-decoder.onnx",
            "asr-en-joiner.int8.onnx",
            "asr-en-tokens.txt",
        )

        /** Loads both models from [dir]; throws if a file is missing. Takes about 0.5 to 4 s. */
        fun load(dir: File): SpeechEngine {
            REQUIRED.forEach { require(File(dir, it).isFile) { "missing model file $it" } }
            val vad = Vad(
                null,
                VadModelConfig(
                    sileroVadModelConfig = SileroVadModelConfig(model = File(dir, "vad-silero-v5.onnx").path),
                    sampleRate = SpeechSegmenter.SAMPLE_RATE,
                    numThreads = 1,
                ),
            )
            val recognizer = OnlineRecognizer(
                null,
                OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SpeechSegmenter.SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = File(dir, "asr-en-encoder.int8.onnx").path,
                            decoder = File(dir, "asr-en-decoder.onnx").path,
                            joiner = File(dir, "asr-en-joiner.int8.onnx").path,
                        ),
                        tokens = File(dir, "asr-en-tokens.txt").path,
                        numThreads = ASR_THREADS,
                        modelType = "zipformer",
                    ),
                    enableEndpoint = false,
                ),
            )
            return SpeechEngine(vad, recognizer)
        }
    }
}
