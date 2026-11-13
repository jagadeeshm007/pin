package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.action.GlobalNav

/**
 * Parses one utterance (after the spoken prefix) into a [Command]. The whole utterance
 * must be the command: "scroll down and then go back" is not one (spec PIN-30). Pure and
 * thread-safe.
 */
object CommandGrammar {
    /** Command for [utterance], or null when it is not a command. */
    fun parse(utterance: String): Command? {
        val s = clean(normalize(utterance))
        if (s.isEmpty()) return null
        fixed[s]?.let { return it }
        DIRECTED.matchEntire(s)?.let { m ->
            val direction = Direction.valueOf(m.groupValues[2].uppercase())
            return Command.Act(
                if (m.groupValues[1] == "swipe") DeviceAction.Swipe(direction) else DeviceAction.Scroll(direction),
            )
        }
        NUMBER.matchEntire(s)?.let { m -> number(m.groupValues[1])?.let { return Command.TapNumber(it) } }
        TAP_DIGITS.matchEntire(s)?.let { m -> return Command.TapNumber(m.groupValues[1].toInt()) }
        LONG_PRESS.matchEntire(s)?.let { return Command.OnLabel(Command.LabelKind.LONG_PRESS, it.groupValues[1]) }
        DOUBLE_TAP.matchEntire(s)?.let { return Command.OnLabel(Command.LabelKind.DOUBLE_TAP, it.groupValues[1]) }
        TAP.matchEntire(s)?.let { return Command.OnLabel(Command.LabelKind.TAP, it.groupValues[1]) }
        OPEN.matchEntire(s)?.let { return Command.OpenApp(it.groupValues[1]) }
        if (TYPE.matchEntire(s) != null) return typed(utterance)?.let { Command.TypeText(it) }
        return null
    }

    /** Lowercase words separated by single spaces; punctuation becomes a space. */
    fun normalize(text: String): String = text.lowercase().replace(NON_WORD, " ").trim().replace(SPACES, " ")

    private fun clean(normalized: String): String {
        var words = normalized.split(' ').filter { it.isNotEmpty() && it !in DROPPED }
        var changed = true
        while (changed) {
            changed = false
            LEADING.firstOrNull { lead -> words.size > lead.size && words.take(lead.size) == lead }?.let {
                words = words.drop(it.size)
                changed = true
            }
        }
        if (words.size > 1 && words.last() == "please") words = words.dropLast(1)
        return words.joinToString(" ")
    }

    /** The text after the first "type" or "write", with its case kept. */
    private fun typed(original: String): String? =
        TYPED.find(original)?.groupValues?.get(1)?.trim()?.trimEnd('.', '!', '?')?.takeIf { it.isNotBlank() }

    private fun number(word: String): Int? = word.toIntOrNull() ?: NUMBER_WORDS.indexOf(word).takeIf { it >= 0 }

    private val NON_WORD = Regex("[^a-z0-9]+")
    private val SPACES = Regex(" +")
    private val DROPPED = setOf("the", "my")
    private val LEADING = listOf(
        listOf("please"),
        listOf("can", "you"),
        listOf("could", "you"),
        listOf("would", "you"),
        listOf("will", "you"),
    )
    private val DIRECTED = Regex("(scroll|swipe) (up|down|left|right)")
    private val NUMBER = Regex("(?:(?:tap|click) )?number (\\w+)")
    private val TAP_DIGITS = Regex("(?:tap|click) (\\d+)")
    private val LONG_PRESS = Regex("long (?:press|click)(?: on)? (.+)")
    private val DOUBLE_TAP = Regex("double (?:tap|click)(?: on)? (.+)")
    private val TAP = Regex("(?:tap|click|press|select)(?: on)? (.+)")
    private val OPEN = Regex("(?:open|launch|start)(?: app)? (.+?)(?: app)?")
    private val TYPE = Regex("(?:type|write) .+")
    private val TYPED = Regex("(?i)\\b(?:type|write)\\b\\W*(.+)$")
    private val NUMBER_WORDS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
        "nineteen", "twenty",
    )

    private fun nav(nav: GlobalNav) = Command.Act(DeviceAction.Global(nav))

    private val fixed: Map<String, Command> = buildMap {
        listOf("stop", "cancel", "stop it", "never mind", "cancel that").forEach { put(it, Command.Stop) }
        listOf("back", "go back", "navigate back", "press back").forEach { put(it, nav(GlobalNav.BACK)) }
        listOf("home", "go home", "home screen", "go to home", "go to home screen").forEach {
            put(it, nav(GlobalNav.HOME))
        }
        listOf("recents", "recent apps", "show recent apps", "open recents", "open recent apps").forEach {
            put(it, nav(GlobalNav.RECENTS))
        }
        listOf("notifications", "show notifications", "open notifications").forEach {
            put(it, nav(GlobalNav.NOTIFICATIONS))
        }
        listOf("quick settings", "show quick settings", "open quick settings").forEach {
            put(it, nav(GlobalNav.QUICK_SETTINGS))
        }
        put("page down", Command.Act(DeviceAction.Scroll(Direction.DOWN)))
        put("page up", Command.Act(DeviceAction.Scroll(Direction.UP)))
        put("zoom in", Command.Act(DeviceAction.Pinch(zoomIn = true)))
        put("zoom out", Command.Act(DeviceAction.Pinch(zoomIn = false)))
        listOf("show numbers", "numbers").forEach { put(it, Command.ShowNumbers) }
        listOf("hide numbers", "hide", "hide grid").forEach { put(it, Command.HideNumbers) }
        listOf("show grid", "grid").forEach { put(it, Command.ShowGrid) }
    }
}
