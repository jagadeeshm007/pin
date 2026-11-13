package io.github.jagadeeshm007.pin

import android.app.Application
import io.github.jagadeeshm007.pin.history.HistoryDatabase
import io.github.jagadeeshm007.pin.history.HistoryStore
import io.github.jagadeeshm007.pin.history.RetentionPolicy
import io.github.jagadeeshm007.pin.notification.ConfirmationBroker
import io.github.jagadeeshm007.pin.notification.TaskNotifier
import kotlinx.coroutines.MainScope
import java.io.File

/** Application entry point; hand-wired dependencies are created here (ADR-0002). */
class PinApp : Application() {
    private val mainScope = MainScope()

    /** The one place confirmations wait for an answer. */
    val confirmations: ConfirmationBroker by lazy { ConfirmationBroker() }

    /** Local history (ADR-0005). The database opens on first use, off the main thread. */
    val history: HistoryStore by lazy { HistoryStore(HistoryDatabase.open(this), File(filesDir, HistoryStore.DIR)) }

    /** Retention window; a settings screen will change it (PIN-50). */
    val retention: RetentionPolicy = RetentionPolicy()

    /** The task notification; use on the main thread. */
    lateinit var notifier: TaskNotifier
        private set

    override fun onCreate() {
        super.onCreate()
        // Created at start so a confirmation always reaches the notification, even when
        // nothing has shown a status yet in this process (found on device, PIN-13).
        notifier = TaskNotifier(this, confirmations, mainScope)
    }
}
