package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import io.github.jagadeeshm007.pin.core.screen.ScreenSnapshot

/** A small screen used across action tests: a search button, a list, a field, a password field. */
object Fixtures {
    const val SEARCH = 1
    const val LIST = 2
    const val QUERY = 3
    const val PASSWORD = 4
    const val PAY = 5
    const val OFFSCREEN = 6

    val window = Bounds(0, 0, 1080, 2400)

    fun screen(
        packageName: String = "com.google.android.youtube",
        windowTitle: String = "Home",
        contentVersion: Long = 1,
        queryText: String? = null,
    ): ScreenSnapshot = ScreenSnapshot(
        packageName = packageName,
        windowTitle = windowTitle,
        windowBounds = window,
        contentVersion = contentVersion,
        nodes = listOf(
            ScreenNode(SEARCH, "Button", "Search", Bounds(900, 100, 1000, 200), setOf(NodeFlag.CLICKABLE)),
            ScreenNode(LIST, "RecyclerView", "Videos", Bounds(0, 300, 1080, 2300), setOf(NodeFlag.SCROLLABLE)),
            ScreenNode(
                QUERY,
                "EditText",
                "Search YouTube",
                Bounds(0, 100, 880, 200),
                setOf(NodeFlag.EDITABLE),
                queryText,
            ),
            ScreenNode(
                PASSWORD,
                "EditText",
                "Password",
                Bounds(0, 220, 880, 280),
                setOf(NodeFlag.EDITABLE, NodeFlag.PASSWORD),
            ),
            ScreenNode(PAY, "Button", "Pay now", Bounds(0, 2300, 1080, 2400), setOf(NodeFlag.CLICKABLE)),
            ScreenNode(OFFSCREEN, "Button", "Hidden", Bounds(0, 2500, 100, 2600), setOf(NodeFlag.CLICKABLE)),
        ),
    )
}
