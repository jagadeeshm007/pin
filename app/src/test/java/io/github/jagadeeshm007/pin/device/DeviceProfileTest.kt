package io.github.jagadeeshm007.pin.device

import io.github.jagadeeshm007.pin.core.screen.Bounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class DeviceProfileTest {
    private val screen = Bounds(0, 0, 1080, 2392)

    @Test
    fun gestureNavigationHasNoTappableBottomInset() {
        assertEquals(NavigationMode.GESTURE, NavigationMode.classify(tappableBottomPx = 0, gestureBottomPx = 63))
        assertEquals(
            NavigationMode.THREE_BUTTON,
            NavigationMode.classify(tappableBottomPx = 126, gestureBottomPx = 126),
        )
        assertEquals(NavigationMode.UNKNOWN, NavigationMode.classify(tappableBottomPx = 0, gestureBottomPx = 0))
    }

    @Test
    fun cutoutSideFollowsTheEdgeItTouches() {
        assertEquals(CutoutSide.TOP, CutoutSide.of(Bounds(500, 0, 580, 90), screen))
        assertEquals(CutoutSide.BOTTOM, CutoutSide.of(Bounds(500, 2330, 580, 2392), screen))
        assertEquals(CutoutSide.LEFT, CutoutSide.of(Bounds(0, 1000, 60, 1100), screen))
        assertEquals(CutoutSide.RIGHT, CutoutSide.of(Bounds(1020, 1000, 1080, 1100), screen))
    }

    @Test
    fun cutoutShapeIsPunchHoleWhenSmallAndNotchWhenWide() {
        assertEquals(CutoutShape.PUNCH_HOLE, CutoutShape.of(Bounds(500, 0, 580, 90), screen))
        assertEquals(CutoutShape.NOTCH, CutoutShape.of(Bounds(300, 0, 780, 90), screen))
    }

    @Test
    fun ramClassBuckets() {
        val gb = 1024L * 1024 * 1024
        assertEquals(RamClass.LOW, RamClass.of(3 * gb))
        assertEquals(RamClass.MID, RamClass.of(6 * gb))
        assertEquals(RamClass.HIGH, RamClass.of(12 * gb))
    }

    @Test
    fun roundTripsThroughJson() {
        val profile = sample()
        assertEquals(profile, DeviceProfile.fromJson(profile.toJson()))
    }

    @Test
    fun jsonHoldsNoUserContentFields() {
        val json = sample().toJson()
        listOf("account", "email", "label", "text").forEach { assertFalse(json.contains("\"$it\""), it) }
    }

    private fun sample() = DeviceProfile(
        collectedAt = 1_000,
        sdk = 37,
        release = "17",
        securityPatch = "2026-10-05",
        manufacturer = "Nothing",
        model = "A059",
        abi = "arm64-v8a",
        screenWidthPx = 1080,
        screenHeightPx = 2392,
        densityDpi = 420,
        statusBarPx = 126,
        navigationBarPx = 56,
        navigationMode = NavigationMode.GESTURE,
        cutouts = listOf(Cutout(listOf(500, 0, 580, 90), CutoutSide.TOP, CutoutShape.PUNCH_HOLE)),
        maxGestureStrokes = 20,
        maxGestureDurationMs = 60_000,
        ramBytes = 7_789_649_920,
        ramClass = RamClass.MID,
        cpuCores = 8,
    )
}
