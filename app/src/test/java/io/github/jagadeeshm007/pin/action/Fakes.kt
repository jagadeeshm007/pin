package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/** Shared log so tests can assert the order of recorder, confirmer and device calls. */
class CallLog {
    val entries = mutableListOf<String>()

    fun add(entry: String) {
        entries += entry
    }
}

/**
 * Device fake. [outcomes] maps a strategy to (dispatch result, screen afterwards).
 * Unlisted strategies dispatch false.
 */
class FakeDevice(
    private val log: CallLog,
    private val screen: FakeScreen,
    private val outcomes: Map<ExecutionStrategy, Pair<Boolean, ScreenSnapshot?>> = emptyMap(),
) : DeviceController {
    var calls = 0
        private set

    override suspend fun perform(action: DeviceAction, screen: ScreenSnapshot, strategy: ExecutionStrategy): Boolean {
        calls++
        log.add("device:$strategy")
        val (ok, after) = outcomes[strategy] ?: (false to null)
        if (after != null) this.screen.current = after
        return ok
    }
}

class FakeScreen(var current: ScreenSnapshot) : ScreenSource {
    /** When set, awaitChange suspends on it, so a test can cancel mid-wait. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun current(): ScreenSnapshot = current

    /** Screens returned by successive awaitChange calls, as events arrive one by one. */
    val upcoming = ArrayDeque<ScreenSnapshot>()

    /** Timeouts passed to awaitChange, so tests can check per-action limits. */
    val timeouts = mutableListOf<Long>()

    override suspend fun awaitChange(since: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot {
        timeouts += timeoutMs
        gate?.await()
        delay(1)
        upcoming.removeFirstOrNull()?.let { current = it }
        return current
    }
}

class FakeConfirmer(private val log: CallLog, private val answer: Boolean) : Confirmer {
    override suspend fun confirm(action: DeviceAction, reason: String): Boolean {
        log.add("confirm")
        return answer
    }
}

class FakeRecorder(private val log: CallLog) : ActionRecorder {
    val events = mutableListOf<ActionEvent>()

    override suspend fun record(event: ActionEvent) {
        log.add("record:${event::class.simpleName}")
        events += event
    }
}
