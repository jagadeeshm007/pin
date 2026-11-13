package io.github.jagadeeshm007.pin.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import io.github.jagadeeshm007.pin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Posts the task notification (blueprint 7.8). On Android 16+ it is a Live Update:
 * ProgressStyle (or BigTextStyle while idle-listening), a status bar chip, and a
 * promotion request. Older versions get a standard ongoing progress notification with
 * the same text and buttons. Confirm and Deny appear while [broker] has a question.
 */
class TaskNotifier(private val context: Context, private val broker: ConfirmationBroker, scope: CoroutineScope) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val mapper = NotificationContentMapper(ResourceChipLabels(context))
    private val poster = ThrottledPoster<Pair<NotificationContent, Long?>>(
        scope,
        THROTTLE_MS,
        { SystemClock.elapsedRealtime() },
    ) { (content, token) -> post(content, token) }
    private var last: TaskStatus? = null

    init {
        createChannel()
        scope.launch {
            broker.pending.collect { question ->
                val base = last
                if (question != null) {
                    val status = TaskStatus(TaskPhase.CONFIRM, question.reason, base?.step ?: 0, base?.totalSteps ?: 0)
                    poster.submit(mapper.map(status) to question.token, urgent = true)
                } else if (base != null) {
                    poster.submit(mapper.map(base) to null, urgent = true)
                }
            }
        }
    }

    /** Shows [status]. Call on the main thread. */
    @MainThread
    fun show(status: TaskStatus) {
        last = status
        val content = mapper.map(status)
        poster.submit(content to broker.pending.value?.token, content.urgent)
    }

    /** Removes the notification. */
    @MainThread
    fun clear() {
        last = null
        manager.cancel(NOTIFICATION_ID)
    }

    /** True when the system allows promoted Live Updates for this app. */
    val canPromote: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA && manager.canPostPromotedNotifications()

    private fun post(content: NotificationContent, token: Long?) {
        if (!manager.areNotificationsEnabled()) return
        manager.notify(NOTIFICATION_ID, build(content, token))
    }

    private fun build(content: NotificationContent, token: Long?): Notification {
        val finished = content.actions.isEmpty()
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(content.text)
            .setOngoing(!finished)
            .setOnlyAlertOnce(!content.popUp)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(content))
        if (finished) builder.setTimeoutAfter(FINISHED_TIMEOUT_MS)
        content.actions.forEach { builder.addAction(action(it, token)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            liveUpdate(builder, content, promote = !finished)
        } else {
            legacy(builder, content)
        }
        return builder.build()
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun liveUpdate(builder: Notification.Builder, content: NotificationContent, promote: Boolean) {
        builder.setShortCriticalText(content.chip)
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            builder.setRequestPromotedOngoing(promote)
        } else {
            // Android 16.0 has no builder method; the documented extra makes the same request.
            builder.addExtras(Bundle().apply { putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, promote) })
        }
        when (val p = content.progress) {
            ProgressSpec.None -> builder.setStyle(Notification.BigTextStyle().bigText(content.text))

            ProgressSpec.Indeterminate -> builder.setStyle(Notification.ProgressStyle().setProgressIndeterminate(true))

            is ProgressSpec.Steps -> builder.setStyle(
                Notification.ProgressStyle()
                    .setProgressSegments(List(p.total) { Notification.ProgressStyle.Segment(1) })
                    .setProgress(p.done),
            )
        }
    }

    private fun legacy(builder: Notification.Builder, content: NotificationContent) {
        builder.setSubText(content.chip)
        when (val p = content.progress) {
            ProgressSpec.None -> Unit
            ProgressSpec.Indeterminate -> builder.setProgress(0, 0, true)
            is ProgressSpec.Steps -> builder.setProgress(p.total, p.done, false)
        }
    }

    private fun publicVersion(content: NotificationContent): Notification = Notification.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_stat_pin)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.notification_public_text))
        .setSubText(content.chip)
        .build()

    private fun action(kind: NotificationAction, token: Long?): Notification.Action {
        val (label, request) = when (kind) {
            NotificationAction.STOP -> R.string.action_stop to REQUEST_STOP
            NotificationAction.CONFIRM -> R.string.action_confirm to REQUEST_CONFIRM
            NotificationAction.DENY -> R.string.action_deny to REQUEST_DENY
        }
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(kind.name)
            .putExtra(NotificationActionReceiver.EXTRA_TOKEN, token ?: NO_TOKEN)
        val pending = PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_pin)
        return Notification.Action.Builder(icon, context.getString(label), pending).build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "task_progress"
        const val NOTIFICATION_ID = 1
        const val THROTTLE_MS = 500L
        const val FINISHED_TIMEOUT_MS = 10_000L
        const val NO_TOKEN = -1L
        private const val REQUEST_STOP = 1
        private const val REQUEST_CONFIRM = 2
        private const val REQUEST_DENY = 3
    }
}

/** Chip texts from resources. */
private class ResourceChipLabels(private val context: Context) : ChipLabels {
    override val listening get() = context.getString(R.string.chip_listening)
    override val heard get() = context.getString(R.string.chip_heard)
    override val thinking get() = context.getString(R.string.chip_thinking)
    override val acting get() = context.getString(R.string.chip_acting)
    override val confirm get() = context.getString(R.string.chip_confirm)
    override val done get() = context.getString(R.string.chip_done)
    override val stopped get() = context.getString(R.string.chip_stopped)
    override val paused get() = context.getString(R.string.chip_paused)

    override fun step(step: Int, total: Int): String = context.getString(R.string.chip_step, step, total)
}
