package io.github.jagadeeshm007.pin.speech

import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File

/** Silero voice detection from sherpa-onnx (ADR-0003); about 0.6 % of one core (PIN-2). */
class VoiceDetector private constructor(private val vad: Vad) {
    /** Probability that [window] (512 samples at 16 kHz) contains speech. */
    fun speechProbability(window: FloatArray): Float = vad.compute(window)

    fun release() = vad.release()

    companion object {
        const val MODEL = "vad-silero-v5.onnx"

        /** Loads the model from [dir]; throws IllegalArgumentException when it is missing. */
        fun load(dir: File): VoiceDetector {
            val file = File(dir, MODEL)
            require(file.isFile) { "missing model file $MODEL" }
            return VoiceDetector(
                Vad(
                    null,
                    VadModelConfig(
                        sileroVadModelConfig = SileroVadModelConfig(model = file.path),
                        sampleRate = SpeechSegmenter.SAMPLE_RATE,
                        numThreads = 1,
                    ),
                ),
            )
        }
    }
}
