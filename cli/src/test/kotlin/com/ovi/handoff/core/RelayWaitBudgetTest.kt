package com.ovi.handoff.core

import com.ovi.handoff.shared.protocol.AckPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelayWaitBudgetTest {

    private fun ack(
        delivered: Boolean? = null,
        pushQueued: Boolean? = null,
        status: String = "stored"
    ) = AckPayload(requestId = "r", status = status, delivered = delivered, pushQueued = pushQueued)

    // -----------------------------------------------------------------------------------------
    // Deadline
    // -----------------------------------------------------------------------------------------

    @Test
    fun usesTheRequestsOwnDeadline() {
        val now = 1_000_000L
        assertEquals(60_000L, RelayWaitBudget.forDeadline(now + 60_000, now))
    }

    @Test
    fun fallsBackToTheDefaultWhenNoDeadlineIsCarried() {
        assertEquals(RelayWaitBudget.DEFAULT_WAIT_MS, RelayWaitBudget.forDeadline(null, 0))
    }

    @Test
    fun clampsADeadlineThatHasAlreadyPassed() {
        // Still a positive window: an approval sent a moment before its deadline should get a
        // chance rather than failing before the phone can even render it.
        val now = 1_000_000L
        assertEquals(RelayWaitBudget.MIN_WAIT_MS, RelayWaitBudget.forDeadline(now - 5_000, now))
    }

    @Test
    fun clampsAnAbsurdlyDistantDeadline() {
        assertEquals(RelayWaitBudget.MAX_WAIT_MS, RelayWaitBudget.forDeadline(Long.MAX_VALUE / 2, 0))
    }

    // -----------------------------------------------------------------------------------------
    // Ack
    // -----------------------------------------------------------------------------------------

    /**
     * The compatibility rule that keeps this change from breaking working setups: a relay too old to
     * report delivery says nothing, and nothing must mean "keep waiting", not "give up".
     */
    @Test
    fun anOlderRelayThatReportsNothingKeepsTheFullWait() {
        assertNull(RelayWaitBudget.graceAfterAck(ack()))
    }

    @Test
    fun aDeliveredRequestKeepsTheFullWait() {
        assertNull(RelayWaitBudget.graceAfterAck(ack(delivered = true, pushQueued = false)))
    }

    @Test
    fun aPushedRequestKeepsTheFullWait() {
        // The phone is asleep but wakeable, so the user still gets their chance to decide.
        assertNull(RelayWaitBudget.graceAfterAck(ack(delivered = false, pushQueued = true)))
    }

    @Test
    fun anUnreachablePhoneShortensTheWait() {
        assertEquals(
            RelayWaitBudget.UNREACHABLE_GRACE_MS,
            RelayWaitBudget.graceAfterAck(ack(delivered = false, pushQueued = false))
        )
    }

    @Test
    fun aPartialReportIsStillActedOn() {
        // Only one field known and it is false: enough to conclude nothing reached the phone.
        assertEquals(
            RelayWaitBudget.UNREACHABLE_GRACE_MS,
            RelayWaitBudget.graceAfterAck(ack(delivered = false, pushQueued = null))
        )
    }

    @Test
    fun theGraceWindowIsShortEnoughToBeUsefulAndLongEnoughToRecover() {
        // Guards the intent rather than the number: a value outside this range means the setting
        // stopped doing its job in one direction or the other.
        assert(RelayWaitBudget.UNREACHABLE_GRACE_MS in 5_000..60_000)
        assert(RelayWaitBudget.UNREACHABLE_GRACE_MS < RelayWaitBudget.DEFAULT_WAIT_MS)
    }
}
