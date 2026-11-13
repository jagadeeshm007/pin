package io.github.jagadeeshm007.pin.models

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class ModelManagerTest {
    @TempDir
    lateinit var dir: File

    private fun bytes(n: Int, seed: Int) = ByteArray(n) { (it * seed).toByte() }
    private fun sha(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private val a = bytes(1_000, 3)
    private val b = bytes(2_000, 5)
    private val c = bytes(500, 7)
    private val catalog = ModelCatalog(
        "t",
        listOf(
            ModelModule("one", "One", listOf(ModelFile("a", 1_000, sha(a), listOf("https://h/a")))),
            ModelModule(
                "two",
                "Two",
                listOf(
                    ModelFile("b", 2_000, sha(b), listOf("https://h/b")),
                    ModelFile("c", 500, sha(c), listOf("https://h/c")),
                ),
            ),
        ),
    )
    private val served = mutableMapOf("https://h/a" to a, "https://h/b" to b, "https://h/c" to c)
    private val source = RangeSource { url, from ->
        val data = served[url] ?: throw IOException("404 $url")
        val code = if (from > 0) 206 else 200
        RangeResponse(code, data.size - from, ByteArrayInputStream(data.copyOfRange(from.toInt(), data.size)))
    }

    private fun manager() = ModelManager(catalog, dir, FileDownloader(source, maxAttemptsPerUrl = 1, backoffMs = 1))

    @Test
    fun startsMissingAndBecomesReadyAfterDownload() = runTest {
        val m = manager()
        m.refresh()
        assertEquals(mapOf("one" to ModuleState.Missing, "two" to ModuleState.Missing), m.states.value)
        assertEquals(false, m.allReady)
        m.downloadAll()
        assertEquals(mapOf("one" to ModuleState.Ready, "two" to ModuleState.Ready), m.states.value)
        assertEquals(3_500L, m.progress.value.doneBytes)
        assertEquals(3_500L, m.progress.value.totalBytes)
        assertTrue(m.allReady)
        assertEquals(File(dir, "b"), m.fileOf("b"))
    }

    @Test
    fun oneFailingFileFailsOnlyItsModule() = runTest {
        served.remove("https://h/c")
        val m = manager()
        m.downloadAll()
        assertEquals(ModuleState.Ready, m.states.value["one"])
        val two = m.states.value["two"]
        assertTrue(two is ModuleState.Failed, "was $two")
    }

    @Test
    fun refreshSeesFilesAlreadyOnDisk() = runTest {
        File(dir, "a").writeBytes(a)
        val m = manager()
        m.refresh()
        assertEquals(ModuleState.Ready, m.states.value["one"])
        assertEquals(1_000L, m.progress.value.doneBytes)
    }

    @Test
    fun verifyCatchesAFileChangedOnDisk() = runTest {
        val m = manager()
        m.downloadAll()
        File(dir, "b").writeBytes(bytes(2_000, 9))
        m.verify()
        assertTrue(m.states.value["two"] is ModuleState.Failed)
        assertTrue(!File(dir, "b").exists(), "the bad file is removed so a retry downloads it again")
    }

    @Test
    fun wipeRemovesEverything() = runTest {
        val m = manager()
        m.downloadAll()
        m.wipe()
        assertEquals(ModuleState.Missing, m.states.value["one"])
        assertEquals(0L, m.progress.value.doneBytes)
    }

    @Test
    fun refreshDoesNotWaitForARunningDownload() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = RangeSource { url, from ->
            gate.await()
            source.open(url, from)
        }
        val m = ModelManager(catalog, dir, FileDownloader(slow, maxAttemptsPerUrl = 1, backoffMs = 1))
        val download = launch { m.downloadAll() }
        testScheduler.runCurrent()
        m.refresh()
        assertEquals(ModuleState.Downloading, m.states.value["one"])
        gate.complete(Unit)
        download.join()
        assertTrue(m.allReady)
    }
}
