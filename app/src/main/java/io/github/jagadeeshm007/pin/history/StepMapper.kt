package io.github.jagadeeshm007.pin.history

import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/** What kind of step a history row records. Stored by name; never rename. */
enum class StepKind { REJECTED, CONFIRMATION_REQUESTED, DECLINED, ATTEMPT_STARTED, ATTEMPT_FAILED, SUCCEEDED }

/** One step ready to store; [screen] is saved as a file when present. */
data class StepRecord(
    val kind: StepKind,
    val action: String,
    val strategy: String? = null,
    val attempt: Int? = null,
    val reason: String? = null,
    val screen: ScreenSnapshot? = null,
)

/** Turns orchestrator events into history steps. */
object StepMapper {
    const val MAX_ACTION_CHARS = 500

    /** The step for [event]. Screens are kept before an attempt and after success. */
    fun map(event: ActionEvent): StepRecord {
        val action = event.action.toString().take(MAX_ACTION_CHARS)
        return when (event) {
            is ActionEvent.Rejected -> StepRecord(StepKind.REJECTED, action, reason = event.reason)

            is ActionEvent.ConfirmationRequested ->
                StepRecord(StepKind.CONFIRMATION_REQUESTED, action, reason = event.reason)

            is ActionEvent.Declined -> StepRecord(StepKind.DECLINED, action)

            is ActionEvent.AttemptStarted -> StepRecord(
                StepKind.ATTEMPT_STARTED,
                action,
                strategy = event.strategy.name,
                attempt = event.attempt,
                screen = event.screen,
            )

            is ActionEvent.AttemptFailed -> StepRecord(
                StepKind.ATTEMPT_FAILED,
                action,
                strategy = event.strategy.name,
                attempt = event.attempt,
                reason = event.reason,
            )

            is ActionEvent.Succeeded ->
                StepRecord(StepKind.SUCCEEDED, action, strategy = event.strategy.name, screen = event.screen)
        }
    }
}
