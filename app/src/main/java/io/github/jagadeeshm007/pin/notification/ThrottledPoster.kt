package io.github.jagadeeshm007.pin.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Posts at most one update per [minIntervalMs], always ending with the latest one
 * (spec AC-3). Urgent updates post at once and replace anything pending. Not thread
 * safe: call [submit] from one thread (the notifier uses the main thread).
 */
class ThrottledPoster<T>(
    private val scope: CoroutineScope,
    private val minIntervalMs: Long,
    private val now: () -> Long,
    private val post: (T) -> Unit,
) {
    private var lastPostAt: Long? = null
    private var pending: T? = null
    private var scheduled: Job? = null

    /** Offers [item]; posts now or after the remaining window. */
    fun submit(item: T, urgent: Boolean) {
        val last = lastPostAt
        val elapsed = last?.let { now() - it }
        if (urgent || elapsed == null || elapsed >= minIntervalMs) {
            scheduled?.cancel()
            scheduled = null
            pending = null
            postNow(item)
            return
        }
        pending = item
        if (scheduled == null) {
            val wait = minIntervalMs - elapsed
            scheduled = scope.launch {
                delay(wait)
                scheduled = null
                pending?.let {
                    pending = null
                    postNow(it)
                }
            }
        }
    }

    private fun postNow(item: T) {
        lastPostAt = now()
        post(item)
    }
}
