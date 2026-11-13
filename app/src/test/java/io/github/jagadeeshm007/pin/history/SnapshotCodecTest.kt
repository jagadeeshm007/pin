package io.github.jagadeeshm007.pin.history

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class SnapshotCodecTest {
    private val snapshot = ScreenSnapshot(
        packageName = "com.android.settings",
        windowTitle = "Settings",
        windowBounds = Bounds(0, 0, 1080, 2392),
        contentVersion = 42,
        nodes = listOf(
            ScreenNode(1, "Button", "Search", Bounds(900, 100, 1000, 200), setOf(NodeFlag.CLICKABLE, NodeFlag.ENABLED)),
            ScreenNode(2, "EditText", "Query", Bounds(0, 100, 880, 200), setOf(NodeFlag.EDITABLE), "bluetooth", 1),
        ),
    )

    @Test
    fun roundTripsThroughCompressedJson() {
        val bytes = ByteArrayOutputStream().also { SnapshotCodec.write(snapshot, it) }.toByteArray()
        val back = SnapshotCodec.read(ByteArrayInputStream(bytes))
        assertEquals(snapshot, back)
    }

    @Test
    fun outputIsGzipped() {
        val bytes = ByteArrayOutputStream().also { SnapshotCodec.write(snapshot, it) }.toByteArray()
        assertEquals(0x1f, bytes[0].toInt() and 0xff)
        assertEquals(0x8b, bytes[1].toInt() and 0xff)
    }

    @Test
    fun passwordTextNeverReachesTheFile() {
        val secret = snapshot.copy(
            nodes = listOf(
                ScreenNode(3, "EditText", "Password", Bounds(0, 0, 10, 10), setOf(NodeFlag.PASSWORD), "hunter2"),
            ),
        )
        val bytes = ByteArrayOutputStream().also { SnapshotCodec.write(secret, it) }.toByteArray()
        val json = java.util.zip.GZIPInputStream(ByteArrayInputStream(bytes)).readBytes().decodeToString()
        assertFalse(json.contains("hunter2"))
        assertNull(SnapshotCodec.read(ByteArrayInputStream(bytes)).nodes.single().text)
    }

    @Test
    fun aTypicalScreenStaysWellUnderTheStepBudget() {
        val big = snapshot.copy(
            nodes = (1..200).map {
                ScreenNode(
                    it,
                    "TextView",
                    "Row number $it with some text",
                    Bounds(0, it * 10, 1080, it * 10 + 9),
                    setOf(NodeFlag.CLICKABLE),
                )
            },
        )
        val size = ByteArrayOutputStream().also { SnapshotCodec.write(big, it) }.size()
        assertTrue(size < 20_000, "200 nodes took $size bytes")
    }
}
