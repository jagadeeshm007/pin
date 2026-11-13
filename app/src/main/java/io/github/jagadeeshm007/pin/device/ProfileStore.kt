package io.github.jagadeeshm007.pin.device

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Saves the device profile and the quirk book under [dir] (app-private storage). */
class ProfileStore(private val dir: File, private val io: CoroutineDispatcher = Dispatchers.IO) {
    private val profileFile get() = File(dir, "profile.json")
    private val quirksFile get() = File(dir, "quirks.json")

    /** Replaces the stored profile. */
    suspend fun saveProfile(profile: DeviceProfile) = withContext(io) { write(profileFile, profile.toJson()) }

    /** The stored profile, or null before the first collection or if the file is unreadable. */
    suspend fun loadProfile(): DeviceProfile? = withContext(io) {
        profileFile.takeIf { it.exists() }?.let { runCatching { DeviceProfile.fromJson(it.readText()) }.getOrNull() }
    }

    /** Replaces the stored quirks. */
    suspend fun saveQuirks(book: QuirkBook) = withContext(io) { write(quirksFile, book.toJson()) }

    /** The stored quirks, or an empty book. */
    suspend fun loadQuirks(): QuirkBook = withContext(io) {
        quirksFile.takeIf { it.exists() }?.let { runCatching { QuirkBook.fromJson(it.readText()) }.getOrNull() }
            ?: QuirkBook()
    }

    /** Writes through a temp file so a crash never leaves a half-written file. */
    private fun write(file: File, text: String) {
        dir.mkdirs()
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(text)
        check(tmp.renameTo(file)) { "could not replace ${file.name}" }
    }
}
