package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.action.ExecutionStrategy.GESTURE
import io.github.jagadeeshm007.pin.action.ExecutionStrategy.NODE_ACTION
import io.github.jagadeeshm007.pin.action.Fixtures.PASSWORD
import io.github.jagadeeshm007.pin.action.Fixtures.PAY
import io.github.jagadeeshm007.pin.action.Fixtures.SEARCH
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionOrchestratorTest {
    private val log = CallLog()
    private val before: ScreenSnapshot = Fixtures.screen(contentVersion = 1)
    private val changed: ScreenSnapshot = Fixtures.screen(contentVersion = 2)
    private val screen: FakeScreen = FakeScreen(before)
    private val recorder = FakeRecorder(log)
    private val tapSearch = DeviceAction.Tap(TargetRef.Node(SEARCH))

    private fun orchestrator(
        outcomes: Map<ExecutionStrategy, Pair<Boolean, ScreenSnapshot?>>,
        confirm: Boolean = true,
    ): Pair<ActionOrchestrator, FakeDevice> {
        val device = FakeDevice(log, screen, outcomes)
        return ActionOrchestrator(device, screen, FakeConfirmer(log, confirm), recorder) to device
    }

    @Test
    fun primaryStrategySucceeds() = runTest {
        val (orch, _) = orchestrator(mapOf(NODE_ACTION to (true to changed)))
        val result = orch.execute(tapSearch)
        assertEquals(ActionResult.Success(NODE_ACTION, changed), result)
        assertEquals(listOf("record:AttemptStarted", "device:NODE_ACTION", "record:Succeeded"), log.entries)
    }

    @Test
    fun fallsBackWhenPrimaryIsRefused() = runTest {
        val (orch, device) = orchestrator(mapOf(NODE_ACTION to (false to null), GESTURE to (true to changed)))
        val result = orch.execute(tapSearch)
        assertEquals(ActionResult.Success(GESTURE, changed), result)
        assertEquals(2, device.calls)
    }

    @Test
    fun fallsBackWhenPrimaryDoesNotVerify() = runTest {
        val (orch, _) = orchestrator(mapOf(NODE_ACTION to (true to null), GESTURE to (true to changed)))
        val result = orch.execute(tapSearch)
        assertEquals(ActionResult.Success(GESTURE, changed), result)
        val failed = recorder.events.filterIsInstance<ActionEvent.AttemptFailed>().single()
        assertTrue(failed.reason.contains("no change"))
    }

    @Test
    fun reportsFailureWithReasonsWhenAllStrategiesFail() = runTest {
        val (orch, device) = orchestrator(mapOf(NODE_ACTION to (false to null), GESTURE to (true to null)))
        val result = orch.execute(tapSearch)
        assertTrue(result is ActionResult.Failed)
        val reasons = (result as ActionResult.Failed).reasons
        assertEquals(2, reasons.size)
        assertTrue(reasons[0].contains("refused"))
        assertTrue(reasons[1].contains("no change"))
        assertEquals(2, device.calls)
    }

    @Test
    fun invalidActionNeverReachesTheDevice() = runTest {
        val (orch, device) = orchestrator(mapOf(NODE_ACTION to (true to changed)))
        val result = orch.execute(DeviceAction.TypeText(PASSWORD, "secret"))
        assertTrue(result is ActionResult.Rejected)
        assertEquals(0, device.calls)
        assertEquals(listOf("record:Rejected"), log.entries)
    }

    @Test
    fun declinedSensitiveActionNeverReachesTheDevice() = runTest {
        val (orch, device) = orchestrator(mapOf(NODE_ACTION to (true to changed)), confirm = false)
        val result = orch.execute(DeviceAction.Tap(TargetRef.Node(PAY)))
        assertEquals(ActionResult.Declined, result)
        assertEquals(0, device.calls)
        assertEquals(listOf("record:ConfirmationRequested", "confirm", "record:Declined"), log.entries)
    }

    @Test
    fun confirmedSensitiveActionRunsAfterConfirmation() = runTest {
        val (orch, _) = orchestrator(mapOf(NODE_ACTION to (true to changed)), confirm = true)
        val result = orch.execute(DeviceAction.Tap(TargetRef.Node(PAY)))
        assertTrue(result is ActionResult.Success)
        assertEquals(
            listOf(
                "record:ConfirmationRequested",
                "confirm",
                "record:AttemptStarted",
                "device:NODE_ACTION",
                "record:Succeeded",
            ),
            log.entries,
        )
    }

    @Test
    fun cancellationStopsFurtherAttempts() = runTest {
        val gate = CompletableDeferred<Unit>()
        screen.gate = gate
        val (orch, device) = orchestrator(mapOf(NODE_ACTION to (true to null), GESTURE to (true to changed)))
        var result: ActionResult? = null
        val job = launch { result = orch.execute(tapSearch) }
        runCurrent()
        assertEquals(1, device.calls)
        job.cancel()
        gate.complete(Unit)
        job.join()
        assertEquals(1, device.calls)
        assertEquals(null, result)
        assertTrue(job.isCancelled)
    }

    @Test
    fun actionsRunOneAtATime() = runTest {
        val gate = CompletableDeferred<Unit>()
        screen.gate = gate
        val (orch, _) = orchestrator(mapOf(NODE_ACTION to (true to changed)))
        val first = async { orch.execute(tapSearch) }
        val second = async { orch.execute(DeviceAction.Global(GlobalNav.BACK)) }
        runCurrent()
        assertEquals(listOf("record:AttemptStarted", "device:NODE_ACTION"), log.entries)
        screen.gate = null
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals("device:NODE_ACTION", log.entries[1])
        assertTrue(log.entries.indexOf("record:Succeeded") < log.entries.lastIndexOf("record:AttemptStarted"))
    }
}
