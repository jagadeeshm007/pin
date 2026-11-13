package io.github.jagadeeshm007.pin.models

/** One downloadable file; [urls] are tried in order (ADR-0008). */
data class ModelFile(val name: String, val sizeBytes: Long, val sha256: String, val urls: List<String>)

/** A unit the app can use once all its files are present and verified. */
data class ModelModule(val id: String, val title: String, val files: List<ModelFile>) {
    val sizeBytes: Long get() = files.sumOf { it.sizeBytes }
}

/** The models a given app version uses. Changes only with an app release (ADR-0008). */
data class ModelCatalog(val version: String, val modules: List<ModelModule>) {
    val totalBytes: Long get() = modules.sumOf { it.sizeBytes }

    companion object {
        private const val RELEASE = "https://github.com/jagadeeshm007/pin/releases/download/models-v1"
        private const val VAD_FALLBACK = "https://huggingface.co/csukuangfj/vad/resolve/main/silero_vad_v5.onnx"

        private fun file(name: String, size: Long, sha: String, vararg fallbacks: String) =
            ModelFile(name, size, sha, listOf("$RELEASE/$name") + fallbacks)

        /** models-v1: the files measured in spike PIN-2. */
        val V1 = ModelCatalog(
            version = "v1",
            modules = listOf(
                ModelModule(
                    "vad",
                    "Voice detection",
                    listOf(
                        file(
                            "vad-silero-v5.onnx",
                            2_313_101,
                            "6b99cbfd39246b6706f98ec13c7c50c6b299181f2474fa05cbc8046acc274396",
                            VAD_FALLBACK,
                        ),
                    ),
                ),
                ModelModule(
                    "kws",
                    "Wake word",
                    listOf(
                        file(
                            "kws-encoder.int8.onnx",
                            4_000_908,
                            "7d5a25fbc14b423074fe0c58e1be388ccf3f1a815c649980232a0c47f81875e4",
                        ),
                        file(
                            "kws-decoder.onnx",
                            1_063_189,
                            "f61ebd3eed3773a44d088d53dfae92dbb6aec4839f4dcaee2d402414741663a3",
                        ),
                        file(
                            "kws-joiner.int8.onnx",
                            163_380,
                            "eae9da0c7e1e6c6a3f4cc42d167899c388f6c6701b94cb96320e4f55df79624c",
                        ),
                        file(
                            "kws-tokens.txt",
                            5_006,
                            "fd2ded4050a55d2b1578870ba8697d02371980217806b7558bd0a5cc60f3ba53",
                        ),
                    ),
                ),
                ModelModule(
                    "asr-en",
                    "Speech recognition (English)",
                    listOf(
                        file(
                            "asr-en-encoder.int8.onnx",
                            32_809_961,
                            "ed7e1e12bc624276f2e9e7c721a251485e260532d0593954632b166e1f22fde1",
                        ),
                        file(
                            "asr-en-decoder.onnx",
                            2_092_272,
                            "45a7f940ecfb53d89fa270ad11b88b961e53a317203eb24b1c8e95ed208b0f30",
                        ),
                        file(
                            "asr-en-joiner.int8.onnx",
                            259_572,
                            "e085d73b593cf9b0707f370dbd656d58327d3fe36d80d849202ef81df02cb01e",
                        ),
                        file(
                            "asr-en-tokens.txt",
                            5_048,
                            "49e3c2646595fd907228b3c6787069658f67b17377c60aeb8619c4551b2316fb",
                        ),
                    ),
                ),
            ),
        )
    }
}
