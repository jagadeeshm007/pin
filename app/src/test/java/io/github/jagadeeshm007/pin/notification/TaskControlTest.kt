package io.github.jagadeeshm007.pin.notification

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskControlTest {
    @Test
    fun stopReachesASubscriberOnAnotherThread() = runBlocking {
        val stopped = async(Dispatchers.Default) {
            val work = async { awaitCancellation() }
            val watcher = launch {
                TaskControl.stopRequests.first()
                work.cancel()
            }
            try {
                work.await()
                false
            } catch (_: kotlinx.coroutines.CancellationException) {
                true
            } finally {
                watcher.cancel()
            }
        }
        // Give the watcher time to subscribe, as on the device.
        kotlinx.coroutines.delay(200)
        TaskControl.requestStop()
        assertTrue(withTimeout(2_000) { stopped.await() })
    }
}
