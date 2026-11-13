package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Outcome of [ActionOrchestrator.execute]. */
sealed interface ActionResult {
    data class Success(val strategy: ExecutionStrategy, val screen: ScreenSnapshot) : ActionResult

    data class Rejected(val reason: String) : ActionResult

    data object Declined : ActionResult

    data class Failed(val reasons: List<String>) : ActionResult
}

/**
 * The only path from a command or planner step to the device (blueprint 6.2, 6.4, 7.6).
 *
 * For each action: validate against the current screen, ask for confirmation when the
 * policy requires it, then try each strategy in order (record first, perform, wait
 * for the screen to settle, verify). Actions run one at a time. Cancelling the
 * calling coroutine stops everything; [kotlin.coroutines.cancellation.CancellationException]
 * is never caught here.
 */
class ActionOrchestrator(
    private val device: DeviceController,
    private val screen: ScreenSource,
    private val confirmer: Confirmer,
    private val recorder: ActionRecorder,
    private val validator: ActionValidator = ActionValidator(),
    private val policy: SensitivityPolicy = SensitivityPolicy(),
    private val verifier: ActionVerifier = ActionVerifier(),
    private val settleTimeoutMs: Long = DEFAULT_SETTLE_TIMEOUT_MS,
) {
    private val mutex = Mutex()

    /** Runs [action] end to end and reports what happened. */
    suspend fun execute(action: DeviceAction): ActionResult = mutex.withLock {
        var before = screen.current()

        val validation = validator.validate(action, before)
        if (validation is Validation.Invalid) {
            recorder.record(ActionEvent.Rejected(action, validation.reason))
            return@withLock ActionResult.Rejected(validation.reason)
        }

        val sensitivity = policy.assess(action, before)
        if (sensitivity is Sensitivity.Confirm) {
            recorder.record(ActionEvent.ConfirmationRequested(action, sensitivity.reason))
            if (!confirmer.confirm(action, sensitivity.reason)) {
                recorder.record(ActionEvent.Declined(action))
                return@withLock ActionResult.Declined
            }
        }

        val reasons = mutableListOf<String>()
        action.strategies.take(MAX_ATTEMPTS).forEachIndexed { index, strategy ->
            val attempt = index + 1
            recorder.record(ActionEvent.AttemptStarted(action, strategy, attempt, before))
            val failure = attempt(action, strategy, before)
            if (failure == null) {
                val after = screen.current()
                recorder.record(ActionEvent.Succeeded(action, strategy, after))
                return@withLock ActionResult.Success(strategy, after)
            }
            reasons += "$strategy: $failure"
            recorder.record(ActionEvent.AttemptFailed(action, strategy, attempt, failure))
            before = screen.current()
        }
        ActionResult.Failed(reasons)
    }

    /** Returns null on success, or why the attempt failed. */
    private suspend fun attempt(action: DeviceAction, strategy: ExecutionStrategy, before: ScreenSnapshot): String? {
        if (!device.perform(action, before, strategy)) return "device refused the action"
        val after = screen.awaitChange(before, settleTimeoutMs)
        return when (val verification = verifier.verify(action, before, after)) {
            Verification.Passed -> null
            is Verification.Failed -> verification.reason
        }
    }

    companion object {
        const val DEFAULT_SETTLE_TIMEOUT_MS = 800L

        /** Primary plus one fallback (blueprint 6.4). */
        const val MAX_ATTEMPTS = 2
    }
}
