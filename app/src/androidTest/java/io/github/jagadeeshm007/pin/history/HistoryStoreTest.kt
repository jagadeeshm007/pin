package io.github.jagadeeshm007.pin.history

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.ExecutionStrategy
import io.github.jagadeeshm007.pin.action.TargetRef
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HistoryStoreTest {
    private val day = 24 * 60 * 60 * 1000L
    private var now = 100 * day
    private lateinit var db: HistoryDatabase
    private lateinit var root: File
    private lateinit var store: HistoryStore

    private val screen = ScreenSnapshot(
        "com.android.settings",
        "Settings",
        Bounds(0, 0, 1080, 2392),
        listOf(
            ScreenNode(1, "Button", "Search", Bounds(0, 0, 100, 100), setOf(NodeFlag.CLICKABLE)),
            ScreenNode(2, "EditText", "Password", Bounds(0, 100, 100, 200), setOf(NodeFlag.PASSWORD), "hunter2"),
        ),
        7,
    )
    private val tap = DeviceAction.Tap(TargetRef.Node(1))

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).build()
        root = File(context.cacheDir, "history-test-${System.nanoTime()}")
        store = HistoryStore(db, root, clock = { now })
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun recorderStoresEveryEventInOrderWithScreens() = runBlocking {
        val session = store.startSession("test")
        val task = store.startTask(session, "tap search", "debug")
        val recorder = store.recorder(task)
        recorder.record(ActionEvent.AttemptStarted(tap, ExecutionStrategy.NODE_ACTION, 1, screen))
        recorder.record(ActionEvent.AttemptFailed(tap, ExecutionStrategy.NODE_ACTION, 1, "no change"))
        recorder.record(ActionEvent.AttemptStarted(tap, ExecutionStrategy.GESTURE, 2, screen))
        recorder.record(ActionEvent.Succeeded(tap, ExecutionStrategy.GESTURE, screen))
        store.endTask(task, "success", "done")

        val steps = store.steps(task)
        assertEquals(listOf(1, 2, 3, 4), steps.map { it.seq })
        assertEquals(
            listOf("ATTEMPT_STARTED", "ATTEMPT_FAILED", "ATTEMPT_STARTED", "SUCCEEDED"),
            steps.map { it.kind },
        )
        assertEquals(listOf(true, false, true, true), steps.map { it.screenFile != null })
        val restored = store.screenOf(steps[0])!!
        assertEquals("Settings", restored.windowTitle)
        assertNull("password text must not be stored", restored.nodes.single { it.id == 2 }.text)
        assertEquals(1, store.recentTasks().single().let { if (it.stepCount == 4) 1 else 0 })
    }

    @Test
    fun retentionDeletesExpiredSessionsWithFilesAndKeepsPinnedAndOpen() = runBlocking {
        val old = sessionWithScreen("old")
        val pinned = sessionWithScreen("pinned").also { store.setPinned(it, true) }
        val open = store.startSession("open")
        store.endSession(old)
        store.endSession(pinned)
        now += 8 * day
        val recent = sessionWithScreen("recent").also { store.endSession(it) }

        val deleted = store.applyRetention(RetentionPolicy(7))

        assertEquals(1, deleted)
        assertFalse(File(root, old.toString()).exists())
        assertTrue(File(root, pinned.toString()).exists())
        assertTrue(File(root, recent.toString()).exists())
        assertNull(db.dao().session(old))
        assertTrue(db.dao().session(pinned) != null && db.dao().session(open) != null)
        assertTrue("tasks and steps cascade", store.recentTasks().none { it.sessionId == old })
    }

    @Test
    fun aStepWithAScreenStaysUnderTheBudget() = runBlocking {
        val session = sessionWithScreen("size")
        val bytes = store.fileBytes()
        assertTrue("one snapshot took $bytes bytes", bytes in 1..100_000)
        assertEquals(1, store.recentTasks().count { it.sessionId == session })
    }

    private suspend fun sessionWithScreen(label: String): Long {
        val session = store.startSession(label)
        val task = store.startTask(session, label, "debug")
        store.recorder(task).record(ActionEvent.AttemptStarted(tap, ExecutionStrategy.NODE_ACTION, 1, screen))
        return session
    }
}
