package io.github.jagadeeshm007.pin.history

import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.action.ActionRecorder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Pin's local history (ADR-0005, blueprint 7.7): sessions, tasks and steps in Room,
 * screen snapshots as gzipped files under [root]/<sessionId>/. All work runs on [io].
 */
class HistoryStore(
    db: HistoryDatabase,
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dao = db.dao()

    /** Starts a session and returns its id. */
    suspend fun startSession(label: String): Long = withContext(io) {
        dao.insertSession(SessionEntity(startedAt = clock(), label = label))
    }

    /** Ends a session; ended sessions become eligible for retention. */
    suspend fun endSession(id: Long) = withContext(io) { dao.endSession(id, clock()) }

    /** Pins or unpins a session; pinned sessions are never deleted by retention. */
    suspend fun setPinned(id: Long, pinned: Boolean) = withContext(io) { dao.setPinned(id, pinned) }

    /** Starts a task in [sessionId] and returns its id. */
    suspend fun startTask(sessionId: Long, utterance: String, route: String): Long = withContext(io) {
        dao.insertTask(
            TaskEntity(
                sessionId = sessionId,
                utterance = utterance,
                route = route,
                status = "running",
                startedAt = clock(),
            ),
        )
    }

    /** Ends a task with [status] such as "success", "failed", "declined" or "stopped". */
    suspend fun endTask(taskId: Long, status: String, summary: String?) =
        withContext(io) { dao.endTask(taskId, status, clock(), summary) }

    /** Stores one step for [taskId], with its screen file when the step has one. Returns the step id. */
    suspend fun recordStep(taskId: Long, record: StepRecord): Long = withContext(io) {
        val stepId = dao.insertStep(
            StepEntity(
                taskId = taskId,
                seq = dao.lastSeq(taskId) + 1,
                kind = record.kind.name,
                at = clock(),
                action = record.action,
                strategy = record.strategy,
                attempt = record.attempt,
                reason = record.reason,
            ),
        )
        record.screen?.let { screen ->
            val sessionId = dao.sessionOf(taskId) ?: return@let
            val relative = "$sessionId/$stepId.json.gz"
            val file = File(root, relative)
            file.parentFile?.mkdirs()
            file.outputStream().use { SnapshotCodec.write(screen, it) }
            dao.setScreenFile(stepId, relative)
        }
        stepId
    }

    /** A recorder that writes each orchestrator event into [taskId] before returning. */
    fun recorder(taskId: Long): ActionRecorder =
        ActionRecorder { event: ActionEvent -> recordStep(taskId, StepMapper.map(event)) }

    /** Deletes sessions expired under [policy], rows and files. Returns how many were deleted. */
    suspend fun applyRetention(policy: RetentionPolicy): Int = withContext(io) {
        val ages = dao.sessionAges().map { SessionAge(it.id, it.endedAt, it.pinned) }
        val expired = policy.expired(ages, clock())
        deleteSessionsNow(expired)
        expired.size
    }

    /** Deletes [ids] and their files at once, regardless of pins. */
    suspend fun deleteSessions(ids: List<Long>) = withContext(io) { deleteSessionsNow(ids) }

    /** Recent tasks, newest first. */
    suspend fun recentTasks(limit: Int = 20): List<TaskSummary> = withContext(io) { dao.recentTasks(limit) }

    /** Steps of a task, in order. */
    suspend fun steps(taskId: Long): List<StepEntity> = withContext(io) { dao.steps(taskId) }

    /** Reads a step's screen file, if any. */
    suspend fun screenOf(step: StepEntity) = withContext(io) {
        step.screenFile?.let { File(root, it) }?.takeIf { it.exists() }?.inputStream()?.use { SnapshotCodec.read(it) }
    }

    /** Bytes used by snapshot files. */
    suspend fun fileBytes(): Long = withContext(io) { root.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

    private suspend fun deleteSessionsNow(ids: List<Long>) {
        if (ids.isEmpty()) return
        // Rows first: if file deletion is interrupted, orphan files are harmless and
        // are removed with their directory next time; orphan rows would point at nothing.
        // One statement; foreign keys cascade to tasks and steps.
        dao.deleteSessions(ids)
        ids.forEach { File(root, it.toString()).deleteRecursively() }
    }

    companion object {
        /** Directory name under the app's files dir. */
        const val DIR = "history"
    }
}
