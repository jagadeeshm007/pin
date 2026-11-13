package io.github.jagadeeshm007.pin.history

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

/** A listening session or a debug session. Open while [endedAt] is null. */
@Entity(tableName = "sessions", indices = [Index("ended_at")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val pinned: Boolean = false,
    val label: String,
)

/** One command within a session. [route] is "local", "agent" or "debug". */
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(SessionEntity::class, ["id"], ["session_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("session_id")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val utterance: String,
    val route: String,
    val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val summary: String? = null,
)

/** One orchestrator event within a task. [screenFile] is relative to the history directory. */
@Entity(
    tableName = "steps",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["task_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("task_id")],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    val seq: Int,
    val kind: String,
    val at: Long,
    val action: String,
    val strategy: String? = null,
    val attempt: Int? = null,
    val reason: String? = null,
    @ColumnInfo(name = "screen_file") val screenFile: String? = null,
)

/** Summary row for listing tasks. */
data class TaskSummary(
    val id: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val utterance: String,
    val status: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "step_count") val stepCount: Int,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Insert
    suspend fun insertStep(step: StepEntity): Long

    @Query("UPDATE steps SET screen_file = :file WHERE id = :stepId")
    suspend fun setScreenFile(stepId: Long, file: String)

    @Query("UPDATE sessions SET ended_at = :at WHERE id = :id AND ended_at IS NULL")
    suspend fun endSession(id: Long, at: Long)

    @Query("UPDATE sessions SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("UPDATE tasks SET status = :status, ended_at = :at, summary = :summary WHERE id = :id")
    suspend fun endTask(id: Long, status: String, at: Long, summary: String?)

    @Query("SELECT COALESCE(MAX(seq), 0) FROM steps WHERE task_id = :taskId")
    suspend fun lastSeq(taskId: Long): Int

    @Query("SELECT session_id FROM tasks WHERE id = :taskId")
    suspend fun sessionOf(taskId: Long): Long?

    @Query("SELECT id, ended_at AS endedAt, pinned FROM sessions")
    suspend fun sessionAges(): List<SessionAgeRow>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun session(id: Long): SessionEntity?

    @Query("SELECT * FROM steps WHERE task_id = :taskId ORDER BY seq")
    suspend fun steps(taskId: Long): List<StepEntity>

    @Query(
        "SELECT t.id, t.session_id, t.utterance, t.status, t.started_at, " +
            "(SELECT COUNT(*) FROM steps s WHERE s.task_id = t.id) AS step_count " +
            "FROM tasks t ORDER BY t.started_at DESC, t.id DESC LIMIT :limit",
    )
    suspend fun recentTasks(limit: Int): List<TaskSummary>

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun sessionCount(): Int

    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteSessions(ids: List<Long>)
}

/** Row shape for retention. */
data class SessionAgeRow(val id: Long, val endedAt: Long?, val pinned: Boolean)

@Database(entities = [SessionEntity::class, TaskEntity::class, StepEntity::class], version = 1, exportSchema = true)
abstract class HistoryDatabase : RoomDatabase() {
    abstract fun dao(): HistoryDao

    companion object {
        const val NAME = "history.db"

        /** Opens the app's history database. */
        fun open(context: Context): HistoryDatabase =
            Room.databaseBuilder(context, HistoryDatabase::class.java, NAME).build()
    }
}
