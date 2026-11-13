package io.github.jagadeeshm007.pin.overlay

import androidx.annotation.StringRes
import io.github.jagadeeshm007.pin.R

/** What the pill says Pin is doing. [IDLE] hides the pill. */
enum class OverlayState(@param:StringRes val text: Int, val kind: Kind) {
    IDLE(R.string.overlay_idle, Kind.NEUTRAL),
    LISTENING(R.string.overlay_listening, Kind.ACTIVE),
    HEARD(R.string.overlay_heard, Kind.ACTIVE),
    THINKING(R.string.overlay_thinking, Kind.BUSY),
    ACTING(R.string.overlay_acting, Kind.BUSY),
    CONFIRM(R.string.overlay_confirm, Kind.ATTENTION),
    PAUSED(R.string.overlay_paused, Kind.NEUTRAL),
    ERROR(R.string.overlay_error, Kind.ATTENTION),
    ;

    /** Drives the dot colour next to the text. */
    enum class Kind { NEUTRAL, ACTIVE, BUSY, ATTENTION }
}
