package io.github.jagadeeshm007.pin.device

import io.github.jagadeeshm007.pin.core.screen.Bounds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How the user navigates the system. Read from public insets only, never hidden settings. */
@Serializable
enum class NavigationMode {
    GESTURE,
    THREE_BUTTON,
    UNKNOWN,
    ;

    companion object {
        /**
         * With gesture navigation the bottom bar takes no taps but reserves a gesture area;
         * with buttons the tappable inset is the navigation bar itself.
         */
        fun classify(tappableBottomPx: Int, gestureBottomPx: Int): NavigationMode = when {
            tappableBottomPx > 0 -> THREE_BUTTON
            gestureBottomPx > 0 -> GESTURE
            else -> UNKNOWN
        }
    }
}

/** Which screen edge a cutout touches. */
@Serializable
enum class CutoutSide {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    ;

    companion object {
        /** The edge [cutout] is closest to within [screen]. */
        fun of(cutout: Bounds, screen: Bounds): CutoutSide {
            val distances = mapOf(
                TOP to cutout.top - screen.top,
                BOTTOM to screen.bottom - cutout.bottom,
                LEFT to cutout.left - screen.left,
                RIGHT to screen.right - cutout.right,
            )
            return distances.minBy { it.value }.key
        }
    }
}

/** Rough cutout shape: a small hole or a wide notch. */
@Serializable
enum class CutoutShape {
    PUNCH_HOLE,
    NOTCH,
    ;

    companion object {
        private const val NOTCH_SHARE = 0.25

        /** A cutout wider than a quarter of the screen width counts as a notch. */
        fun of(cutout: Bounds, screen: Bounds): CutoutShape =
            if (cutout.width > screen.width * NOTCH_SHARE) NOTCH else PUNCH_HOLE
    }
}

/** Memory class used to size model loading and caches. */
@Serializable
enum class RamClass {
    LOW,
    MID,
    HIGH,
    ;

    companion object {
        private const val GB = 1024L * 1024 * 1024

        /** Under 4 GB is low, under 8 GB mid, else high. */
        fun of(totalBytes: Long): RamClass = when {
            totalBytes < 4 * GB -> LOW
            totalBytes < 8 * GB -> MID
            else -> HIGH
        }
    }
}

/** One display cutout; [bounds] is left, top, right, bottom in screen pixels. */
@Serializable
data class Cutout(val bounds: List<Int>, val side: CutoutSide, val shape: CutoutShape)

/**
 * What Pin knows about this phone (spec PIN-15). Hardware and system settings only; no
 * user content.
 */
@Serializable
data class DeviceProfile(
    val collectedAt: Long,
    val sdk: Int,
    val release: String,
    val securityPatch: String,
    val manufacturer: String,
    val model: String,
    val abi: String,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val densityDpi: Int,
    val statusBarPx: Int,
    val navigationBarPx: Int,
    val navigationMode: NavigationMode,
    val cutouts: List<Cutout>,
    val maxGestureStrokes: Int,
    val maxGestureDurationMs: Long,
    val ramBytes: Long,
    val ramClass: RamClass,
    val cpuCores: Int,
) {
    /** Serialises the profile for storage. */
    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        /** Reads a profile written by [toJson]. */
        fun fromJson(text: String): DeviceProfile = json.decodeFromString(serializer(), text)
    }
}
