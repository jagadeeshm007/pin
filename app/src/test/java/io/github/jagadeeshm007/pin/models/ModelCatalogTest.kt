package io.github.jagadeeshm007.pin.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelCatalogTest {
    private val all = ModelCatalog.V1.modules.flatMap { it.files }

    @Test
    fun totalsMatchAdr0008() {
        // ADR-0008 listed 9 files; ADR-0010 removed the 4 asr-en files.
        assertEquals(5, all.size)
        assertEquals(7_545_584L, all.sumOf { it.sizeBytes })
        assertTrue(ModelCatalog.V1.totalBytes < 80L * 1024 * 1024, "first-launch budget")
    }

    @Test
    fun fileNamesAreUniqueAndHashesWellFormed() {
        assertEquals(all.size, all.map { it.name }.toSet().size)
        all.forEach { assertTrue(Regex("^[0-9a-f]{64}$").matches(it.sha256), it.name) }
    }

    @Test
    fun everyUrlIsHttps() {
        all.flatMap { it.urls }.forEach { assertTrue(it.startsWith("https://"), it) }
    }

    @Test
    fun onlyVoiceDetectionHasAFallback() {
        all.forEach { f -> assertEquals(if (f.name == "vad-silero-v5.onnx") 2 else 1, f.urls.size, f.name) }
    }

    @Test
    fun modulesAreVadAndKws() {
        assertEquals(listOf("vad", "kws"), ModelCatalog.V1.modules.map { it.id })
    }
}
