package io.github.jagadeeshm007.pin.models

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/** A response body starting at the requested byte (206) or at zero (200). */
class RangeResponse(val code: Int, val contentLength: Long, val body: InputStream)

/** Opens [url] from [fromByte]. The Android implementation is [HttpUrlSource]. */
fun interface RangeSource {
    suspend fun open(url: String, fromByte: Long): RangeResponse
}

/** Outcome of one file download. */
sealed interface DownloadResult {
    data object Done : DownloadResult

    data class Failed(val reason: String) : DownloadResult
}

/**
 * Downloads one catalog file into `target` (spec PIN-20 AC-1 to AC-4, AC-6). Bytes go to
 * `target.part`, which survives failures and cancellation for resuming. The file is
 * renamed into place only after its size and SHA-256 match.
 */
class FileDownloader(
    private val source: RangeSource,
    private val maxAttemptsPerUrl: Int = 3,
    private val backoffMs: Long = 1_000,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** Downloads [file] to [target], calling [onProgress] with bytes on disk. */
    suspend fun download(file: ModelFile, target: File, onProgress: (Long) -> Unit): DownloadResult {
        if (target.exists() && target.length() == file.sizeBytes) {
            onProgress(file.sizeBytes)
            return DownloadResult.Done
        }
        val part = File(target.parentFile, "${target.name}.part")
        withContext(io) {
            target.parentFile?.mkdirs()
            if (part.length() > file.sizeBytes) part.delete()
        }
        var lastReason = "no URL"
        for (url in file.urls) {
            for (attempt in 1..maxAttemptsPerUrl) {
                val outcome = try {
                    fetch(url, file, part, onProgress)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Outcome.Retry("$url: ${e.message}")
                }
                when (outcome) {
                    Outcome.Complete -> return finish(file, part, target)

                    is Outcome.Retry -> {
                        lastReason = outcome.reason
                        if (attempt < maxAttemptsPerUrl) delay(backoffMs * attempt)
                    }
                }
            }
        }
        return DownloadResult.Failed(lastReason)
    }

    private sealed interface Outcome {
        data object Complete : Outcome

        data class Retry(val reason: String) : Outcome
    }

    private suspend fun fetch(url: String, file: ModelFile, part: File, onProgress: (Long) -> Unit): Outcome {
        val from = withContext(io) { part.length() }
        if (from == file.sizeBytes) return Outcome.Complete
        val response = source.open(url, from)
        if (response.code != HTTP_OK && response.code != HTTP_PARTIAL) {
            response.body.close()
            return Outcome.Retry("$url: HTTP ${response.code}")
        }
        val append = response.code == HTTP_PARTIAL && from > 0
        withContext(io) {
            response.body.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(BUFFER)
                    var written = if (append) from else 0L
                    while (true) {
                        ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (written + n > file.sizeBytes) throw IOException("more bytes than expected")
                        out.write(buffer, 0, n)
                        written += n
                        onProgress(written)
                    }
                }
            }
        }
        val size = withContext(io) { part.length() }
        return if (size == file.sizeBytes) Outcome.Complete else Outcome.Retry("$url: stopped at $size bytes")
    }

    private suspend fun finish(file: ModelFile, part: File, target: File): DownloadResult = withContext(io) {
        val actual = sha256(part)
        if (actual != file.sha256) {
            part.delete()
            return@withContext DownloadResult.Failed("${file.name}: SHA-256 mismatch (got $actual)")
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) return@withContext DownloadResult.Failed("${file.name}: could not move into place")
        DownloadResult.Done
    }

    companion object {
        private const val HTTP_OK = 200
        private const val HTTP_PARTIAL = 206
        private const val BUFFER = 64 * 1024

        /** Hex SHA-256 of [file]. */
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
