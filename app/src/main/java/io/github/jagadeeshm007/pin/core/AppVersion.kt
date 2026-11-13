package io.github.jagadeeshm007.pin.core

/** Formats the version shown to users and attached to exported history. */
object AppVersion {
    /** Returns "name (code)", or "unknown (code)" when the name is missing or blank. */
    fun label(versionName: String?, versionCode: Long): String {
        val name = versionName?.takeIf { it.isNotBlank() } ?: "unknown"
        return "$name ($versionCode)"
    }
}
