package io.github.jagadeeshm007.pin.notification

/** Where a task is in its life cycle, as the notification shows it. */
enum class TaskPhase { LISTENING, HEARD, THINKING, ACTING, CONFIRM, DONE, FAILED, PAUSED }

/**
 * What the notification should say. [text] is the line the user reads (the heard
 * command, the current step, the question). [step] is 1-based; [totalSteps] is 0
 * when there is no plan yet.
 */
data class TaskStatus(val phase: TaskPhase, val text: String, val step: Int = 0, val totalSteps: Int = 0)

/** Progress bar shape. */
sealed interface ProgressSpec {
    data object None : ProgressSpec

    data object Indeterminate : ProgressSpec

    data class Steps(val done: Int, val total: Int) : ProgressSpec
}

/** Buttons on the notification. */
enum class NotificationAction { STOP, CONFIRM, DENY }

/** Platform-free notification content; [urgent] skips throttling. */
data class NotificationContent(
    val text: String,
    val chip: String,
    val progress: ProgressSpec,
    val actions: List<NotificationAction>,
    val urgent: Boolean,
)

/** Localised chip texts, injected so the mapper stays testable. */
interface ChipLabels {
    val listening: String
    val heard: String
    val thinking: String
    val acting: String
    val confirm: String
    val done: String
    val stopped: String
    val paused: String

    fun step(step: Int, total: Int): String
}

/** Maps a [TaskStatus] to what the notification shows (blueprint 7.8). */
class NotificationContentMapper(private val labels: ChipLabels) {
    /** Content for [status]. */
    fun map(status: TaskStatus): NotificationContent {
        val total = status.totalSteps.coerceAtLeast(0)
        val step = if (total > 0) status.step.coerceIn(1, total) else 0
        val running = if (total > 0) ProgressSpec.Steps(step - 1, total) else ProgressSpec.Indeterminate
        val stop = listOf(NotificationAction.STOP)
        return when (status.phase) {
            TaskPhase.LISTENING -> content(status, labels.listening, ProgressSpec.None, stop)

            TaskPhase.HEARD -> content(status, labels.heard, ProgressSpec.Indeterminate, stop)

            TaskPhase.THINKING -> content(status, labels.thinking, ProgressSpec.Indeterminate, stop)

            TaskPhase.ACTING -> {
                val chip = if (total > 0) labels.step(step, total) else labels.acting
                content(status, chip, running, stop)
            }

            TaskPhase.CONFIRM -> content(
                status,
                labels.confirm,
                if (total > 0) running else ProgressSpec.None,
                listOf(NotificationAction.CONFIRM, NotificationAction.DENY, NotificationAction.STOP),
                urgent = true,
            )

            TaskPhase.DONE -> content(
                status,
                labels.done,
                if (total > 0) ProgressSpec.Steps(total, total) else ProgressSpec.None,
                emptyList(),
                urgent = true,
            )

            TaskPhase.FAILED -> content(status, labels.stopped, ProgressSpec.None, emptyList(), urgent = true)

            TaskPhase.PAUSED -> content(status, labels.paused, ProgressSpec.None, stop)
        }
    }

    private fun content(
        status: TaskStatus,
        chip: String,
        progress: ProgressSpec,
        actions: List<NotificationAction>,
        urgent: Boolean = false,
    ) = NotificationContent(status.text, chip, progress, actions, urgent)
}
