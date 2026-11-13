package io.github.jagadeeshm007.pin.notification

import io.github.jagadeeshm007.pin.action.DeviceAction
import io.github.jagadeeshm007.pin.action.GlobalNav
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfirmationBrokerTest {
    private val broker = ConfirmationBroker(timeoutMs = 10_000)

    @Test
    fun yesApproves() = runTest {
        val answer = async { broker.request("Send the message?") }
        runCurrent()
        val pending = broker.pending.value!!
        assertEquals("Send the message?", pending.reason)
        assertTrue(broker.answer(pending.token, yes = true))
        assertTrue(answer.await())
        assertNull(broker.pending.value)
    }

    @Test
    fun noDeclines() = runTest {
        val answer = async { broker.request("Delete?") }
        runCurrent()
        broker.answer(broker.pending.value!!.token, yes = false)
        assertFalse(answer.await())
    }

    @Test
    fun silenceDeclinesAfterTheTimeout() = runTest {
        val answer = async { broker.request("Pay?") }
        advanceTimeBy(10_001)
        assertFalse(answer.await())
        assertNull(broker.pending.value)
    }

    @Test
    fun staleOrWrongTokensAreIgnored() = runTest {
        val answer = async { broker.request("Pay?") }
        runCurrent()
        val token = broker.pending.value!!.token
        assertFalse(broker.answer(token + 1, yes = true))
        assertTrue(answer.isActive)
        broker.answer(token, yes = false)
        assertFalse(answer.await())
        assertFalse(broker.answer(token, yes = true), "an answered request cannot be answered again")
    }

    @Test
    fun cancellingTheRequestClearsIt() = runTest {
        val answer = async { broker.request("Pay?") }
        runCurrent()
        answer.cancel()
        runCurrent()
        assertNull(broker.pending.value)
    }

    @Test
    fun confirmerAnnouncesTheQuestionAndReturnsTheAnswer() = runTest {
        val asked = mutableListOf<String>()
        val confirmer = BrokerConfirmer(broker) { asked += it }
        val answer = async { confirmer.confirm(DeviceAction.Global(GlobalNav.BACK), "target 'Pay' looks sensitive") }
        runCurrent()
        assertEquals(listOf("target 'Pay' looks sensitive"), asked)
        broker.answer(broker.pending.value!!.token, yes = true)
        assertTrue(answer.await())
    }
}
