package io.github.jagadeeshm007.pin.device

import io.github.jagadeeshm007.pin.action.ActionEvent
import io.github.jagadeeshm007.pin.action.ActionRecorder
import io.github.jagadeeshm007.pin.action.ExecutionStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How an app has treated one action type: direct node action worked, or only the gesture did. */
@Serializable
data class QuirkStats(val nodeOk: Int = 0, val gestureRescued: Int = 0)

/**
 * Per-app record of which strategy works (spec PIN-15 AC-4). Keys are
 * "<package>|<action type>". Thread safe.
 */
class QuirkBook(initial: Map<String, QuirkStats> = emptyMap()) {
    private val stats = initial.toMutableMap()

    /** Adds one outcome: [nodeOk] true when the node action verified, false when only the gesture did. */
    @Synchronized
    fun add(packageName: String, actionType: String, nodeOk: Boolean) {
        val key = key(packageName, actionType)
        val s = stats[key] ?: QuirkStats()
        stats[key] = if (nodeOk) s.copy(nodeOk = s.nodeOk + 1) else s.copy(gestureRescued = s.gestureRescued + 1)
    }

    /** Stats for one app and action type. */
    @Synchronized
    fun stats(packageName: String, actionType: String): QuirkStats = stats[key(packageName, actionType)] ?: QuirkStats()

    /** True when the gesture has rescued this action more often than the node action worked. */
    fun prefersGesture(packageName: String, actionType: String): Boolean =
        stats(packageName, actionType).let { it.gestureRescued > it.nodeOk }

    /** A copy of every entry. */
    @Synchronized
    fun all(): Map<String, QuirkStats> = stats.toMap()

    /** Serialises the book. */
    fun toJson(): String = json.encodeToString(Book.serializer(), Book(all()))

    @Serializable
    private data class Book(val entries: Map<String, QuirkStats>)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads a book written by [toJson]. */
        fun fromJson(text: String): QuirkBook = QuirkBook(json.decodeFromString(Book.serializer(), text).entries)

        private fun key(packageName: String, actionType: String) = "$packageName|$actionType"
    }
}

/**
 * Watches orchestrator events and feeds outcomes to [book]. Only actions that have a
 * node strategy are counted. The orchestrator runs one action at a time, so one
 * in-flight action is tracked.
 */
class QuirkObserver(private val book: QuirkBook) : ActionRecorder {
    private var packageName: String? = null
    private var nodeFailed = false

    override suspend fun record(event: ActionEvent) {
        if (ExecutionStrategy.NODE_ACTION !in event.action.strategies) return
        val type = event.action.javaClass.simpleName
        when (event) {
            is ActionEvent.AttemptStarted -> {
                if (event.attempt == 1) {
                    packageName = event.screen.packageName
                    nodeFailed = false
                }
            }

            is ActionEvent.AttemptFailed -> if (event.strategy == ExecutionStrategy.NODE_ACTION) nodeFailed = true

            is ActionEvent.Succeeded -> {
                val pkg = packageName ?: return
                when {
                    event.strategy == ExecutionStrategy.NODE_ACTION -> book.add(pkg, type, nodeOk = true)
                    event.strategy == ExecutionStrategy.GESTURE && nodeFailed -> book.add(pkg, type, nodeOk = false)
                }
            }

            else -> Unit
        }
    }
}
