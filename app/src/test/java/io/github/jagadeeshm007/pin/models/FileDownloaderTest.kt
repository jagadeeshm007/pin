package io.github.jagadeeshm007.pin.models

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class FileDownloaderTest {
    @TempDir
    lateinit var dir: File

    private val payload = ByteArray(10_000) { (it * 7).toByte() }
    private val primary = "https://primary.example/f.bin"
    private val fallback = "https://fallback.example/f.bin"
    private fun entry(urls: List<String> = listOf(primary), bytes: ByteArray = payload) =
        ModelFile("f.bin", bytes.size.toLong(), sha256(bytes), urls)

    /** Serves [payload]; can honour or ignore Range, fail, or cut the stream short. */
    private class FakeSource(private val data: ByteArray) : RangeSource {
        val requests = mutableListOf<Pair<String, Long>>()
        var ignoreRange = false
        val failingUrls = mutableSetOf<String>()
        var cutAfter: Int? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun open(url: String, fromByte: Long): RangeResponse {
            requests += url to fromByte
            gate?.await()
            if (url in failingUrls) throw IOException("unreachable $url")
            val start = if (ignoreRange) 0 else fromByte.toInt()
            val code = if (ignoreRange || fromByte == 0L) 200 else 206
            var body = data.copyOfRange(start, data.size)
            cutAfter?.let { n ->
                body = body.copyOfRange(0, minOf(n, body.size))
                cutAfter = null
                return RangeResponse(code, (data.size - start).toLong(), Truncating(body))
            }
            return RangeResponse(code, body.size.toLong(), ByteArrayInputStream(body))
        }

        /** Delivers [bytes] then fails, like a dropped connection. */
        private class Truncating(bytes: ByteArray) : InputStream() {
            private val inner = ByteArrayInputStream(bytes)

            override fun read(): Int = inner.read().takeIf { it >= 0 } ?: throw IOException("connection reset")

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val n = inner.read(b, off, len)
                if (n < 0) throw IOException("connection reset")
                return n
            }
        }
    }

    private fun downloader(source: RangeSource) = FileDownloader(source, maxAttemptsPerUrl = 2, backoffMs = 1)

    @Test
    fun downloadsVerifiesAndRenames() = runTest {
        val target = File(dir, "f.bin")
        val result = downloader(FakeSource(payload)).download(entry(), target) {}
        assertEquals(DownloadResult.Done, result)
        assertArrayEquals(payload, target.readBytes())
        assertFalse(File(dir, "f.bin.part").exists())
    }

    @Test
    fun resumesFromAPartialFile() = runTest {
        File(dir, "f.bin.part").writeBytes(payload.copyOfRange(0, 4_000))
        val source = FakeSource(payload)
        val result = downloader(source).download(entry(), File(dir, "f.bin")) {}
        assertEquals(DownloadResult.Done, result)
        assertEquals(primary to 4_000L, source.requests.single())
        assertArrayEquals(payload, File(dir, "f.bin").readBytes())
    }

    @Test
    fun droppedConnectionIsRetriedFromWhereItStopped() = runTest {
        val source = FakeSource(payload).apply { cutAfter = 3_000 }
        val result = downloader(source).download(entry(), File(dir, "f.bin")) {}
        assertEquals(DownloadResult.Done, result)
        assertEquals(listOf(primary to 0L, primary to 3_000L), source.requests)
    }

    @Test
    fun restartsWhenTheServerIgnoresRange() = runTest {
        File(dir, "f.bin.part").writeBytes(payload.copyOfRange(0, 4_000))
        val source = FakeSource(payload).apply { ignoreRange = true }
        val result = downloader(source).download(entry(), File(dir, "f.bin")) {}
        assertEquals(DownloadResult.Done, result)
        assertArrayEquals(payload, File(dir, "f.bin").readBytes())
    }

    @Test
    fun hashMismatchDeletesTheFileAndFails() = runTest {
        val tampered = payload.copyOf().also { it[100] = (it[100] + 1).toByte() }
        val result = downloader(FakeSource(tampered)).download(entry(), File(dir, "f.bin")) {}
        assertTrue(result is DownloadResult.Failed)
        assertTrue((result as DownloadResult.Failed).reason.contains("SHA-256"))
        assertFalse(File(dir, "f.bin").exists())
        assertFalse(File(dir, "f.bin.part").exists())
    }

    @Test
    fun oversizedPartIsDiscarded() = runTest {
        File(dir, "f.bin.part").writeBytes(ByteArray(20_000))
        val result = downloader(FakeSource(payload)).download(entry(), File(dir, "f.bin")) {}
        assertEquals(DownloadResult.Done, result)
    }

    @Test
    fun usesTheFallbackWhenThePrimaryFails() = runTest {
        val source = FakeSource(payload).apply { failingUrls += primary }
        val result = downloader(source).download(entry(listOf(primary, fallback)), File(dir, "f.bin")) {}
        assertEquals(DownloadResult.Done, result)
        assertEquals(listOf(primary, primary, fallback), source.requests.map { it.first })
    }

    @Test
    fun failsAfterEveryUrlIsExhausted() = runTest {
        val source = FakeSource(payload).apply { failingUrls += primary }
        val result = downloader(source).download(entry(), File(dir, "f.bin")) {}
        assertTrue(result is DownloadResult.Failed)
        assertEquals(2, source.requests.size)
    }

    @Test
    fun alreadyCompleteFileIsNotDownloadedAgain() = runTest {
        File(dir, "f.bin").writeBytes(payload)
        val source = FakeSource(payload)
        assertEquals(DownloadResult.Done, downloader(source).download(entry(), File(dir, "f.bin")) {})
        assertTrue(source.requests.isEmpty())
    }

    @Test
    fun reportsProgressUpToTheFullSize() = runTest {
        var last = 0L
        downloader(FakeSource(payload)).download(entry(), File(dir, "f.bin")) { last = it }
        assertEquals(payload.size.toLong(), last)
    }

    @Test
    fun cancellationKeepsThePartialFile() = runTest {
        File(dir, "f.bin.part").writeBytes(payload.copyOfRange(0, 1_000))
        val gate = CompletableDeferred<Unit>()
        val source = FakeSource(payload).apply { this.gate = gate }
        val job = async { downloader(source).download(entry(), File(dir, "f.bin")) {} }
        testScheduler.runCurrent()
        job.cancel()
        testScheduler.runCurrent()
        assertTrue(File(dir, "f.bin.part").exists())
        assertEquals(1_000L, File(dir, "f.bin.part").length())
    }

    private fun sha256(b: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }
}
