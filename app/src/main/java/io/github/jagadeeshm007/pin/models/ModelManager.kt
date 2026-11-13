package io.github.jagadeeshm007.pin.models

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Where a module stands. */
sealed interface ModuleState {
    data object Missing : ModuleState

    data object Downloading : ModuleState

    data object Ready : ModuleState

    data class Failed(val reason: String) : ModuleState
}

/** Bytes on disk out of the catalog total. */
data class DownloadProgress(val doneBytes: Long, val totalBytes: Long)

/**
 * Tracks and downloads the catalog's modules into [dir] (spec PIN-20 AC-5). A module is
 * ready when every file is present at its catalog size; full hash checks run on download
 * and when [verify] is called, not on every start.
 */
class ModelManager(
    private val catalog: ModelCatalog,
    private val dir: File,
    private val downloader: FileDownloader,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val fileBytes = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _states = MutableStateFlow(catalog.modules.associate { it.id to (ModuleState.Missing as ModuleState) })
    private val _progress = MutableStateFlow(DownloadProgress(0, catalog.totalBytes))

    val states: StateFlow<Map<String, ModuleState>> = _states.asStateFlow()
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    /** True when every module is ready. */
    val allReady: Boolean get() = _states.value.values.all { it == ModuleState.Ready }

    /** The installed path of catalog file [name]. */
    fun fileOf(name: String): File = File(dir, name)

    /** Re-reads what is on disk. Returns at once while a download or verify is running. */
    suspend fun refresh() {
        if (!mutex.tryLock()) return
        try {
            rescan()
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun rescan() {
        withContext(io) {
            catalog.modules.forEach { module ->
                module.files.forEach { fileBytes[it.name] = onDisk(it) }
                setState(module.id, if (module.files.all { complete(it) }) ModuleState.Ready else ModuleState.Missing)
            }
            publishProgress()
        }
    }

    /** Downloads every module that is not ready, one file at a time. */
    suspend fun downloadAll() = mutex.withLock {
        catalog.modules.forEach { module ->
            if (_states.value[module.id] == ModuleState.Ready && module.files.all { complete(it) }) return@forEach
            setState(module.id, ModuleState.Downloading)
            var failure: String? = null
            for (file in module.files) {
                val result = downloader.download(file, fileOf(file.name)) { bytes ->
                    fileBytes[file.name] = bytes
                    publishProgress()
                }
                if (result is DownloadResult.Failed) {
                    failure = result.reason
                    break
                }
            }
            setState(module.id, failure?.let { ModuleState.Failed(it) } ?: ModuleState.Ready)
        }
    }

    /** Re-hashes installed files; a changed file is deleted and its module fails. */
    suspend fun verify() = mutex.withLock {
        withContext(io) {
            catalog.modules.forEach { module ->
                val bad = module.files.filter { f ->
                    val file = fileOf(f.name)
                    file.exists() && FileDownloader.sha256(file) != f.sha256
                }
                bad.forEach {
                    fileOf(it.name).delete()
                    fileBytes[it.name] = 0
                }
                when {
                    bad.isNotEmpty() -> setState(module.id, ModuleState.Failed("${bad.first().name}: SHA-256 mismatch"))
                    module.files.all { complete(it) } -> setState(module.id, ModuleState.Ready)
                    else -> setState(module.id, ModuleState.Missing)
                }
            }
            publishProgress()
        }
    }

    /** Deletes every model file and partial download. */
    suspend fun wipe() = mutex.withLock {
        withContext(io) {
            dir.deleteRecursively()
            fileBytes.clear()
            catalog.modules.forEach { setState(it.id, ModuleState.Missing) }
            publishProgress()
        }
    }

    private fun complete(f: ModelFile): Boolean = fileOf(f.name).length() == f.sizeBytes

    private fun onDisk(f: ModelFile): Long {
        val done = fileOf(f.name)
        if (done.exists()) return done.length()
        return File(dir, "${f.name}.part").length()
    }

    private fun setState(id: String, state: ModuleState) = _states.update { it + (id to state) }

    private fun publishProgress() {
        val done = catalog.modules.flatMap { it.files }.sumOf { (fileBytes[it.name] ?: 0L).coerceAtMost(it.sizeBytes) }
        _progress.value = DownloadProgress(done, catalog.totalBytes)
    }
}
