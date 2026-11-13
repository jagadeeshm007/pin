package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/** Performs actions on the device. The AccessibilityService implements it (PIN-10). */
interface DeviceController {
    /**
     * Carries out [action] with [strategy], resolving node ids against [screen].
     * Returns false when the platform refused or could not dispatch the action.
     */
    suspend fun perform(action: DeviceAction, screen: ScreenSnapshot, strategy: ExecutionStrategy): Boolean
}

/** Supplies screen snapshots. */
interface ScreenSource {
    /** The latest snapshot. */
    suspend fun current(): ScreenSnapshot

    /**
     * Waits until the screen differs from [since] or [timeoutMs] passes, then returns
     * the latest snapshot (which may equal [since] on timeout).
     */
    suspend fun awaitChange(since: ScreenSnapshot, timeoutMs: Long): ScreenSnapshot
}

/** Asks the user to approve a sensitive action. */
fun interface Confirmer {
    /** Returns true only when the user explicitly approved. */
    suspend fun confirm(action: DeviceAction, reason: String): Boolean
}

/** Receives every orchestrator event for the history store (PIN-14). */
fun interface ActionRecorder {
    suspend fun record(event: ActionEvent)
}

/** What the history store records about an action. */
sealed interface ActionEvent {
    val action: DeviceAction

    data class Rejected(override val action: DeviceAction, val reason: String) : ActionEvent

    data class ConfirmationRequested(override val action: DeviceAction, val reason: String) : ActionEvent

    data class Declined(override val action: DeviceAction) : ActionEvent

    /** Written before the device is touched (AGENTS.md: history before action). */
    data class AttemptStarted(
        override val action: DeviceAction,
        val strategy: ExecutionStrategy,
        val attempt: Int,
        val screen: ScreenSnapshot,
    ) : ActionEvent

    data class AttemptFailed(
        override val action: DeviceAction,
        val strategy: ExecutionStrategy,
        val attempt: Int,
        val reason: String,
    ) : ActionEvent

    data class Succeeded(
        override val action: DeviceAction,
        val strategy: ExecutionStrategy,
        val screen: ScreenSnapshot,
    ) : ActionEvent
}
