package io.github.jagadeeshm007.pin.action

import io.github.jagadeeshm007.pin.action.Fixtures.LIST
import io.github.jagadeeshm007.pin.action.Fixtures.PAY
import io.github.jagadeeshm007.pin.action.Fixtures.QUERY
import io.github.jagadeeshm007.pin.action.Fixtures.SEARCH
import io.github.jagadeeshm007.pin.core.screen.Bounds
import io.github.jagadeeshm007.pin.core.screen.NodeFlag
import io.github.jagadeeshm007.pin.core.screen.ScreenNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SensitivityPolicyTest {
    private val policy = SensitivityPolicy()

    @Test
    fun ordinaryActionsNeedNoConfirmation() {
        val screen = Fixtures.screen()
        listOf(
            DeviceAction.Tap(TargetRef.Node(SEARCH)),
            DeviceAction.Scroll(Direction.DOWN, LIST),
            DeviceAction.TypeText(QUERY, "Java tutorials"),
            DeviceAction.Global(GlobalNav.BACK),
            DeviceAction.OpenApp("com.whatsapp"),
        ).forEach { assertEquals(Sensitivity.None, policy.assess(it, screen), "for $it") }
    }

    @Test
    fun tappingPayAsksFirst() {
        val result = policy.assess(DeviceAction.Tap(TargetRef.Node(PAY)), Fixtures.screen())
        assertConfirm(result, "Pay now")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Send", "Send message", "Delete", "Delete chat", "Remove account", "Transfer", "Buy now",
            "Place order", "Uninstall", "Factory reset", "Erase all data", "Allow", "Confirm payment", "Post", "Publish",
        ],
    )
    fun sensitiveLabelsAskFirst(label: String) {
        val screen = screenWithButton(label)
        assertConfirm(policy.assess(DeviceAction.Tap(TargetRef.Node(BUTTON)), screen), label)
    }

    @ParameterizedTest
    @ValueSource(strings = ["Sender settings", "Paypal help", "Postcode", "Allowance", "Ordered list", "Buyer guide"])
    fun wordsInsideOtherWordsDoNotTrigger(label: String) {
        val screen = screenWithButton(label)
        assertEquals(Sensitivity.None, policy.assess(DeviceAction.Tap(TargetRef.Node(BUTTON)), screen))
    }

    @Test
    fun longPressOnSensitiveTargetAsksFirst() {
        assertConfirm(policy.assess(DeviceAction.LongPress(TargetRef.Node(PAY)), Fixtures.screen()), "Pay now")
    }

    @Test
    fun anyInputInPaymentAppAsksFirst() {
        val screen = Fixtures.screen(packageName = "com.phonepe.app")
        assertConfirm(policy.assess(DeviceAction.Tap(TargetRef.Node(SEARCH)), screen), "com.phonepe.app")
        assertConfirm(policy.assess(DeviceAction.TypeText(QUERY, "500"), screen), "com.phonepe.app")
    }

    @Test
    fun navigationInPaymentAppIsAllowed() {
        val screen = Fixtures.screen(packageName = "com.phonepe.app")
        assertEquals(Sensitivity.None, policy.assess(DeviceAction.Global(GlobalNav.BACK), screen))
        assertEquals(Sensitivity.None, policy.assess(DeviceAction.Scroll(Direction.DOWN), screen))
    }

    @Test
    fun securitySettingsAskFirst() {
        val screen = Fixtures.screen(packageName = "com.android.settings", windowTitle = "Security & privacy")
        assertConfirm(policy.assess(DeviceAction.Tap(TargetRef.Node(SEARCH)), screen), "Security")
    }

    @Test
    fun ordinarySettingsDoNotAsk() {
        val screen = Fixtures.screen(packageName = "com.android.settings", windowTitle = "Network & internet")
        assertEquals(Sensitivity.None, policy.assess(DeviceAction.Tap(TargetRef.Node(SEARCH)), screen))
    }

    @Test
    fun customPackageListIsUsed() {
        val custom = SensitivityPolicy(sensitivePackages = setOf("com.example.bank"))
        val screen = Fixtures.screen(packageName = "com.example.bank")
        assertConfirm(custom.assess(DeviceAction.Tap(TargetRef.Node(SEARCH)), screen), "com.example.bank")
    }

    private fun assertConfirm(result: Sensitivity, reasonContains: String) {
        assertTrue(result is Sensitivity.Confirm, "expected Confirm, got $result")
        val reason = (result as Sensitivity.Confirm).reason
        assertTrue(reason.contains(reasonContains), "reason '$reason' should mention '$reasonContains'")
    }

    private fun screenWithButton(label: String) = Fixtures.screen().let {
        it.copy(
            nodes =
                it.nodes + ScreenNode(BUTTON, "Button", label, Bounds(0, 400, 200, 500), setOf(NodeFlag.CLICKABLE)),
        )
    }

    private companion object {
        const val BUTTON = 50
    }
}
