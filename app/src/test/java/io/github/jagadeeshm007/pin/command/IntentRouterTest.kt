package io.github.jagadeeshm007.pin.command

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.Direction
import io.github.jagadeeshm007.pin.command.IntentRouter.Route
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IntentRouterTest {
    private var now = 0L
    private val router = IntentRouter(clock = { now })
    private val scrollDown = Command.Act(DeviceAction.Scroll(Direction.DOWN))

    @Test
    fun twoEqualPartialsRunEarlyAndTheFinalIsSkipped() {
        assertEquals(Route.Nothing, router.onPartial("pin scroll"))
        assertEquals(Route.Nothing, router.onPartial("pin scroll down"))
        assertEquals(Route.Run(scrollDown, "Pin scroll down", early = true), router.onPartial("Pin scroll down"))
        assertEquals(Route.AlreadyRun, router.onFinal("Pin, scroll down please"))
    }

    @Test
    fun onePartialWaitsForTheFinal() {
        router.onPartial("pin scroll down")
        assertEquals(Route.Run(scrollDown, "pin scroll down", early = false), router.onFinal("pin scroll down"))
    }

    @Test
    fun commandsWithArgumentsNeverRunEarly() {
        repeat(3) { assertEquals(Route.Nothing, router.onPartial("pin open YouTube")) }
        assertEquals(
            Route.Run(Command.OpenApp("youtube"), "pin open YouTube", early = false),
            router.onFinal("pin open YouTube"),
        )
    }

    @Test
    fun speechWithoutThePrefixIsOnlyHeard() {
        repeat(2) { assertEquals(Route.Nothing, router.onPartial("scroll down")) }
        assertEquals(Route.Heard("scroll down"), router.onFinal("scroll down"))
    }

    @Test
    fun prefixedNonCommandIsUnmatched() {
        assertEquals(Route.Unmatched("pin make me a sandwich"), router.onFinal("pin make me a sandwich"))
    }

    @Test
    fun prefixAloneArmsTheNextUtteranceForFiveSeconds() {
        assertEquals(Route.Armed, router.onFinal("Pin."))
        now = 4_000
        assertEquals(Route.Run(scrollDown, "scroll down", early = false), router.onFinal("scroll down"))
        assertEquals(Route.Heard("scroll down"), router.onFinal("scroll down"))
    }

    @Test
    fun armingExpires() {
        router.onFinal("pin")
        now = 5_001
        assertEquals(Route.Heard("go back"), router.onFinal("go back"))
    }

    @Test
    fun armedPartialsCanRunEarly() {
        router.onFinal("pin")
        router.onPartial("scroll down")
        assertEquals(Route.Run(scrollDown, "scroll down", early = true), router.onPartial("scroll down"))
        assertEquals(Route.AlreadyRun, router.onFinal("scroll down"))
    }

    @Test
    fun anAskedQuestionTakesTheNextUtteranceWithoutThePrefix() {
        // "click story" showed numbers; the user answered "click one" (device report, PIN-31).
        router.arm(15_000)
        assertEquals(Route.Run(Command.PickNumber(1), "one", early = false), router.onFinal("one"))
        router.arm(15_000)
        now = 15_001
        assertEquals(Route.Heard("one"), router.onFinal("one"))
    }

    @Test
    fun aNewUtteranceResetsWhenTheFinalWasLost() {
        // The recognizer can fail after an early commit and send no final.
        router.onPartial("pin go back")
        router.onPartial("pin go back")
        router.onPartial("pin")
        router.onPartial("pin scroll down")
        assertEquals(Route.Run(scrollDown, "pin scroll down", early = true), router.onPartial("pin scroll down"))
    }
}
