package io.github.jagadeeshm007.pin.command

/**
 * Decides what each transcript means (spec PIN-30): run a command, report a prefixed
 * non-command, or just record speech. Argument-free commands run early when two partials
 * in a row parse to the same command; the final of that utterance is then skipped.
 * "Pin" said alone arms the next utterance for [armMs]. Use from one thread.
 */
class IntentRouter(private val clock: () -> Long, private val armMs: Long = ARM_MS) {
    sealed interface Route {
        data class Run(val command: Command, val text: String, val early: Boolean) : Route

        /** Prefixed, but not a command. */
        data class Unmatched(val text: String) : Route

        /** No prefix: speech, not a command. */
        data class Heard(val text: String) : Route

        /** Only the prefix was said; the next utterance is a command. */
        data object Armed : Route

        /** The final of an utterance that already ran early. */
        data object AlreadyRun : Route

        data object Nothing : Route
    }

    private var lastPartial: Command? = null
    private var committed: String? = null
    private var armedAt: Long? = null

    /** Handles a partial transcript of the utterance in progress. */
    fun onPartial(text: String): Route {
        committed?.let { done ->
            // A partial that does not continue the committed text belongs to a new
            // utterance whose predecessor never sent a final.
            if (CommandGrammar.normalize(text).startsWith(done)) return Route.Nothing
            committed = null
            lastPartial = null
        }
        val command = body(text, armed())?.let(CommandGrammar::parse)?.takeIf { it.earlyCommit }
        if (command == null || command != lastPartial) {
            lastPartial = command
            return Route.Nothing
        }
        committed = CommandGrammar.normalize(text)
        return Route.Run(command, text, early = true)
    }

    /** Handles the final transcript; the utterance ends here. */
    fun onFinal(text: String): Route {
        val wasCommitted = committed != null
        val armed = armed()
        committed = null
        lastPartial = null
        armedAt = null
        if (wasCommitted) return Route.AlreadyRun
        val body = body(text, armed) ?: return Route.Heard(text)
        if (body.isEmpty()) {
            armedAt = clock()
            return Route.Armed
        }
        val command = CommandGrammar.parse(body) ?: return Route.Unmatched(text)
        return Route.Run(command, text, early = false)
    }

    private fun armed(): Boolean = armedAt?.let { clock() - it <= armMs } == true

    /** The command part of [text]: after the prefix, or all of it when armed; else null. */
    private fun body(text: String, armed: Boolean): String? = CommandPrefix.strip(text) ?: text.takeIf { armed }

    companion object {
        const val ARM_MS = 5_000L
    }
}
