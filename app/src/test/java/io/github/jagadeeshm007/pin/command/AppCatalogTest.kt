package io.github.jagadeeshm007.pin.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppCatalogTest {
    private val apps = listOf(
        LauncherApp("YouTube", "com.google.android.youtube"),
        LauncherApp("Settings", "com.android.settings"),
        LauncherApp("WhatsApp", "com.whatsapp"),
        LauncherApp("Clock", "com.nothing.clock"),
        LauncherApp("Clock", "com.google.android.deskclock"),
    )

    @Test
    fun matchesTheNormalisedLabel() {
        assertEquals("com.google.android.youtube", AppCatalog.match("youtube", apps))
        assertEquals("com.android.settings", AppCatalog.match("Settings!", apps))
    }

    @Test
    fun ignoresSpacesTheRecognizerInserts() {
        assertEquals("com.whatsapp", AppCatalog.match("whats app", apps))
        assertEquals("com.google.android.youtube", AppCatalog.match("you tube", apps))
    }

    @Test
    fun noMatchOrTwoAppsWithTheSameLabelIsNull() {
        assertNull(AppCatalog.match("door", apps))
        assertNull(AppCatalog.match("clock", apps))
    }

    @Test
    fun reloadsOnceOnAMiss() {
        var loads = 0
        var installed = listOf(LauncherApp("Settings", "com.android.settings"))
        val catalog = AppCatalog {
            loads++
            installed
        }
        assertEquals("com.android.settings", catalog.find("settings"))
        installed = installed + LauncherApp("YouTube", "com.google.android.youtube")
        assertEquals("com.google.android.youtube", catalog.find("youtube"))
        assertEquals(2, loads)
        assertEquals("com.google.android.youtube", catalog.find("youtube"))
        assertEquals(2, loads)
    }
}
