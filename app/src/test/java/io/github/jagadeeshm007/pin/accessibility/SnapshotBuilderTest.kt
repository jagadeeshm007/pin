package io.github.jagadeeshm007.pin.accessibility

import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnapshotBuilderTest {
    private val window = Bounds(0, 0, 1080, 2400)
    private val builder = SnapshotBuilder()

    private fun build(root: FakeRawNode, max: Int = SnapshotBuilder.DEFAULT_MAX_NODES) =
        builder.build(root, "com.example", "Home", window, contentVersion = 7, maxNodes = max)

    @Test
    fun keepsLabelledAndActionableNodesOnly() {
        val root = FakeRawNode(
            bounds = window,
            children = listOf(
                FakeRawNode(className = "android.widget.FrameLayout", bounds = Bounds(0, 0, 1080, 100)),
                FakeRawNode(className = "android.widget.TextView", text = "Hello", bounds = Bounds(0, 100, 500, 200)),
                FakeRawNode(
                    className = "android.widget.ImageButton",
                    isClickable = true,
                    bounds = Bounds(0, 200, 100, 300),
                ),
            ),
        )
        val snapshot = build(root)
        assertEquals(listOf("TextView", "ImageButton"), snapshot.nodes.map { it.role })
        assertEquals(7, snapshot.contentVersion)
        assertEquals("com.example", snapshot.packageName)
    }

    @Test
    fun labelPrefersDescriptionThenTextThenHintThenViewId() {
        val root = FakeRawNode(
            bounds = window,
            children = listOf(
                FakeRawNode(contentDescription = "Search", text = "ignored", bounds = Bounds(0, 0, 10, 10)),
                FakeRawNode(text = "Videos", bounds = Bounds(0, 10, 10, 20)),
                FakeRawNode(hintText = "Search YouTube", isEditable = true, bounds = Bounds(0, 20, 10, 30)),
                FakeRawNode(viewId = "com.example:id/play_button", isClickable = true, bounds = Bounds(0, 30, 10, 40)),
                FakeRawNode(isClickable = true, bounds = Bounds(0, 40, 10, 50)),
            ),
        )
        assertEquals(
            listOf("Search", "Videos", "Search YouTube", "play button", ""),
            build(root).nodes.map {
                it.label
            },
        )
    }

    @Test
    fun idsAreUniqueAndParentsPointAtKeptAncestors() {
        val root = FakeRawNode(
            bounds = window,
            children = listOf(
                FakeRawNode(
                    isScrollable = true,
                    contentDescription = "List",
                    bounds = Bounds(0, 0, 1080, 2000),
                    children = listOf(
                        FakeRawNode(
                            bounds = Bounds(0, 0, 1080, 200),
                            children = listOf(
                                FakeRawNode(text = "Row", isClickable = true, bounds = Bounds(0, 0, 1080, 200)),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val nodes = build(root).nodes
        assertEquals(nodes.map { it.id }.distinct(), nodes.map { it.id })
        val list = nodes.single { it.label == "List" }
        val row = nodes.single { it.label == "Row" }
        assertEquals(list.id, row.parentId)
        assertNull(list.parentId)
    }

    @Test
    fun invisibleSubtreesAndEmptyBoundsAreSkipped() {
        val root = FakeRawNode(
            bounds = window,
            children = listOf(
                FakeRawNode(isVisible = false, children = listOf(FakeRawNode(text = "hidden child"))),
                FakeRawNode(text = "zero size", bounds = Bounds(5, 5, 5, 5)),
                FakeRawNode(text = "below window", bounds = Bounds(0, 2500, 100, 2600)),
                FakeRawNode(text = "shown"),
            ),
        )
        assertEquals(listOf("shown"), build(root).nodes.map { it.label })
    }

    @Test
    fun partlyOffscreenNodesAreClippedToTheWindow() {
        val root =
            FakeRawNode(
                bounds = window,
                children = listOf(FakeRawNode(text = "row", bounds = Bounds(0, 2300, 1080, 2500))),
            )
        assertEquals(Bounds(0, 2300, 1080, 2400), build(root).nodes.single().bounds)
    }

    @Test
    fun passwordFieldsAreFlaggedAndTheirTextIsDropped() {
        val root = FakeRawNode(
            bounds = window,
            children = listOf(
                FakeRawNode(hintText = "Password", text = "hunter2", isEditable = true, isPassword = true),
            ),
        )
        val node = build(root).nodes.single()
        assertTrue(node.has(NodeFlag.PASSWORD))
        assertNull(node.text)
        assertEquals("Password", node.label)
    }

    @Test
    fun editableTextIsKeptForVerification() {
        val root =
            FakeRawNode(
                bounds = window,
                children = listOf(FakeRawNode(text = "Java", hintText = "Search", isEditable = true)),
            )
        val node = build(root).nodes.single()
        assertEquals("Java", node.text)
        assertEquals("Search", node.label)
    }

    @Test
    fun flagsAreMapped() {
        val root = FakeRawNode(
            bounds = window,
            children = listOf(
                FakeRawNode(
                    text = "Wi-Fi",
                    isClickable = true,
                    isLongClickable = true,
                    isCheckable = true,
                    isChecked = true,
                    isFocused = true,
                ),
            ),
        )
        assertEquals(
            setOf(
                NodeFlag.CLICKABLE,
                NodeFlag.LONG_CLICKABLE,
                NodeFlag.CHECKABLE,
                NodeFlag.CHECKED,
                NodeFlag.FOCUSED,
                NodeFlag.ENABLED,
            ),
            build(root).nodes.single().flags,
        )
    }

    @Test
    fun nodeCountIsCapped() {
        val root =
            FakeRawNode(
                bounds = window,
                children = List(500) {
                    FakeRawNode(text = "item $it", bounds = Bounds(0, 0, 10, 10))
                },
            )
        assertEquals(50, build(root, max = 50).nodes.size)
    }

    @Test
    fun visitedChildrenAreReleased() {
        val child = FakeRawNode(text = "a")
        val root = FakeRawNode(bounds = window, children = listOf(child))
        build(root)
        assertEquals(1, child.released)
        assertEquals(0, root.released)
    }
}
