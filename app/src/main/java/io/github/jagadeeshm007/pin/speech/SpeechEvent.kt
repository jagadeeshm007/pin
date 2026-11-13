package io.github.jagadeeshm007.pin.speech

/** State of the listening session. */
enum class ListenState { LOADING, LISTENING, PAUSED, ERROR, STOPPED }

/**
 * What the speech process tells the main process (ADR-0007). Text and numbers only;
 * audio never leaves the speech process (spec PIN-21 AC-7).
 */
sealed interface SpeechEvent {
    data class State(val state: ListenState, val detail: String? = null) : SpeechEvent

    data class Partial(val text: String) : SpeechEvent

    data class Final(val text: String, val audioMs: Long, val decodeMs: Long) : SpeechEvent

    /** Encodes for a message bundle. */
    fun toFields(): Map<String, String> = when (this) {
        is State -> buildMap {
            put(TYPE, "state")
            put("state", state.name)
            detail?.let { put("detail", it) }
        }

        is Partial -> mapOf(TYPE to "partial", "text" to text)

        is Final -> mapOf(TYPE to "final", "text" to text, "audioMs" to "$audioMs", "decodeMs" to "$decodeMs")
    }

    companion object {
        private const val TYPE = "type"

        /** Decodes fields from [toFields]; returns null for anything unknown or malformed. */
        fun fromFields(f: Map<String, String>): SpeechEvent? = when (f[TYPE]) {
            "state" -> ListenState.entries.firstOrNull { it.name == f["state"] }?.let { State(it, f["detail"]) }

            "partial" -> f["text"]?.let { Partial(it) }

            "final" -> {
                val text = f["text"]
                val audio = f["audioMs"]?.toLongOrNull()
                val decode = f["decodeMs"]?.toLongOrNull()
                if (text != null && audio != null && decode != null) Final(text, audio, decode) else null
            }

            else -> null
        }
    }
}
