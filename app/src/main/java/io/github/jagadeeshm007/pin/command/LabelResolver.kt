package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/**
 * Finds a node by its visible label. Exact after normalising (case and punctuation) only:
 * a partial match sent "Search" to "Voice search" on YouTube, which opened the microphone.
 * Fuzzy matching with disambiguation is PIN-31.
 */
object LabelResolver {
    /** First node labelled [label]; only editable nodes when [editable]. */
    fun find(screen: ScreenSnapshot, label: String, editable: Boolean = false): ScreenNode? {
        val wanted = CommandGrammar.normalize(label)
        if (wanted.isEmpty()) return null
        return screen.nodes.firstOrNull {
            (!editable || it.has(NodeFlag.EDITABLE)) && CommandGrammar.normalize(it.label) == wanted
        }
    }

    /** The focused editable field, or the only editable field on screen. */
    fun inputField(screen: ScreenSnapshot): ScreenNode? {
        val editable = screen.nodes.filter { it.has(NodeFlag.EDITABLE) && !it.has(NodeFlag.PASSWORD) }
        return editable.firstOrNull { it.has(NodeFlag.FOCUSED) } ?: editable.singleOrNull()
    }
}
