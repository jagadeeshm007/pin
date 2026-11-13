package io.github.jagadeeshm007.pin.notification

import io.github.jagadeeshm007.pin.action.Confirmer
import io.github.jagadeeshm007.pin.action.DeviceAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

/** A question waiting for the user. [token] ties a button press to this question. */
data class PendingConfirmation(val token: Long, val reason: String)

/**
 * Holds one confirmation at a time (blueprint 7.6). Answers must carry the current
 * token, so a press on an old notification cannot approve a new question. No answer
 * within [timeoutMs] counts as no.
 */
class ConfirmationBroker(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
    private val mutex = Mutex()
    private val tokens = AtomicLong(0)
    private val current = MutableStateFlow<Pending?>(null)

    /** The question shown to the user, or null. */
    val pending: StateFlow<PendingConfirmation?> get() = view

    private val view = MutableStateFlow<PendingConfirmation?>(null)

    /** Asks [reason] and suspends until answered, timed out or cancelled. */
    suspend fun request(reason: String): Boolean = mutex.withLock {
        val entry = Pending(PendingConfirmation(tokens.incrementAndGet(), reason), CompletableDeferred())
        publish(entry)
        try {
            withTimeoutOrNull(timeoutMs) { entry.answer.await() } ?: false
        } finally {
            publish(null)
        }
    }

    /** Answers the question with [token]; returns false when it is not the current one. */
    fun answer(token: Long, yes: Boolean): Boolean {
        val entry = current.value ?: return false
        if (entry.question.token != token) return false
        return entry.answer.complete(yes)
    }

    private fun publish(entry: Pending?) {
        current.value = entry
        view.value = entry?.question
    }

    private class Pending(val question: PendingConfirmation, val answer: CompletableDeferred<Boolean>)

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}

/** A [Confirmer] that shows the question via [announce] and waits on [broker]. */
class BrokerConfirmer(private val broker: ConfirmationBroker, private val announce: (String) -> Unit) : Confirmer {
    override suspend fun confirm(action: DeviceAction, reason: String): Boolean {
        announce(reason)
        return broker.request(reason)
    }
}
