package io.github.jagadeeshm007.pin.history

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Writes and reads screen snapshots as gzipped JSON for the history store. The file
 * format is its own set of classes so the core model stays free of serialization.
 * Password text is dropped here as well as in the snapshot builder.
 */
object SnapshotCodec {
    private val json = Json { ignoreUnknownKeys = true }

    /** Writes [snapshot] to [out] and closes the gzip stream. */
    fun write(snapshot: ScreenSnapshot, out: OutputStream) {
        GZIPOutputStream(out).use {
            it.write(json.encodeToString(SnapshotFile.serializer(), snapshot.toFile()).encodeToByteArray())
        }
    }

    /** Reads a snapshot written by [write]. */
    fun read(input: InputStream): ScreenSnapshot {
        val text = GZIPInputStream(input).use { it.readBytes().decodeToString() }
        return json.decodeFromString(SnapshotFile.serializer(), text).toSnapshot()
    }

    @Serializable
    private data class SnapshotFile(
        val v: Int = FORMAT_VERSION,
        val pkg: String,
        val title: String,
        val window: List<Int>,
        val version: Long,
        val nodes: List<NodeFile>,
    )

    @Serializable
    private data class NodeFile(
        val id: Int,
        val role: String,
        val label: String,
        val b: List<Int>,
        val f: List<String> = emptyList(),
        val text: String? = null,
        val parent: Int? = null,
    )

    private fun ScreenSnapshot.toFile() = SnapshotFile(
        pkg = packageName,
        title = windowTitle,
        window = windowBounds.toList(),
        version = contentVersion,
        nodes = nodes.map { n ->
            NodeFile(
                id = n.id,
                role = n.role,
                label = n.label,
                b = n.bounds.toList(),
                f = n.flags.map { it.name }.sorted(),
                text = if (n.has(NodeFlag.PASSWORD)) null else n.text,
                parent = n.parentId,
            )
        },
    )

    private fun SnapshotFile.toSnapshot() = ScreenSnapshot(
        packageName = pkg,
        windowTitle = title,
        windowBounds = window.toBounds(),
        contentVersion = version,
        nodes = nodes.map { n ->
            ScreenNode(
                id = n.id,
                role = n.role,
                label = n.label,
                bounds = n.b.toBounds(),
                flags = n.f.mapNotNull { name -> NodeFlag.entries.firstOrNull { it.name == name } }.toSet(),
                text = n.text,
                parentId = n.parent,
            )
        },
    )

    private fun Bounds.toList() = listOf(left, top, right, bottom)

    private fun List<Int>.toBounds() = Bounds(this[0], this[1], this[2], this[3])

    private const val FORMAT_VERSION = 1
}
