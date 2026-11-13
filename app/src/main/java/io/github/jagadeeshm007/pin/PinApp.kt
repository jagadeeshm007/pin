package io.github.jagadeeshm007.pin

import android.app.Application
import io.github.jagadeeshm007.pin.notification.ConfirmationBroker
import io.github.jagadeeshm007.pin.notification.TaskNotifier
import kotlinx.coroutines.MainScope

/** Application entry point; hand-wired dependencies are created here (ADR-0002). */
class PinApp : Application() {
    private val mainScope = MainScope()

    /** The one place confirmations wait for an answer. */
    val confirmations: ConfirmationBroker by lazy { ConfirmationBroker() }

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
