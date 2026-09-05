package com.ovi.handoff

import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.isApproval
import com.ovi.handoff.shared.model.isExpiredAt
import com.ovi.handoff.shared.model.remainingMs
import com.ovi.handoff.shared.model.shortWorkspaceName
import com.ovi.handoff.shared.protocol.EnvelopeCodec
import com.ovi.handoff.shared.protocol.FrameType
import com.ovi.handoff.shared.protocol.PairHello
import com.ovi.handoff.shared.protocol.Protocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProtocolTest {

    @Test
    fun requestRoundTripsThroughEnvelope() {
        val request = TestFixtures.request(command = "npx prisma migrate dev")
        val envelope = assertNotNull(EnvelopeCodec.decode(EnvelopeCodec.encodeRequest(request)))

        assertEquals(FrameType.REQUEST, envelope.type)
        assertEquals(Protocol.VERSION, envelope.v)
        assertEquals(request.id, envelope.requestId)
        assertEquals(request, EnvelopeCodec.asRequest(envelope))
    }

    @Test
    fun decisionRoundTripsThroughEnvelope() {
        val decision = TestFixtures.decision(feedback = "use a migration instead")
        val envelope = assertNotNull(EnvelopeCodec.decode(EnvelopeCodec.encodeDecision(decision)))

        assertEquals(FrameType.DECISION, envelope.type)
        assertEquals(decision.requestId, envelope.requestId)
        assertEquals(decision, EnvelopeCodec.asDecision(envelope))
    }

    @Test
    fun pairHelloRoundTrips() {
        val hello = PairHello(deviceId = "pixel-9", publicKey = "cHVia2V5", appVersion = "1.2.0")
        val envelope = assertNotNull(EnvelopeCodec.decode(EnvelopeCodec.encodePairHello(hello)))
        assertEquals(FrameType.PAIR_HELLO, envelope.type)
        assertEquals(hello, EnvelopeCodec.asPairHello(envelope))
    }

    @Test
    fun bareLegacyRequestIsRecognised() {
        // A pre-v2 desktop sends the request object with no envelope wrapper. The frame must still
        // be routable rather than failing to parse, so a mixed rollout degrades instead of breaking.
        val request = TestFixtures.request()
        val bare = EnvelopeCodec.json.encodeToString(
            com.ovi.handoff.shared.model.PermissionRequest.serializer(),
            request
        )
        val envelope = assertNotNull(EnvelopeCodec.decode(bare))

        assertEquals(FrameType.REQUEST, envelope.type)
        assertEquals(Protocol.VERSION_LEGACY, envelope.v)
        assertEquals(request.id, envelope.requestId)
        assertEquals(request, EnvelopeCodec.asRequest(envelope))
    }

    @Test
    fun bareLegacyDecisionIsRecognised() {
        val decision = TestFixtures.decision()
        val bare = EnvelopeCodec.json.encodeToString(
            com.ovi.handoff.shared.model.PermissionDecision.serializer(),
            decision
        )
        val envelope = assertNotNull(EnvelopeCodec.decode(bare))
        assertEquals(FrameType.DECISION, envelope.type)
        assertEquals(decision.requestId, envelope.requestId)
    }

    @Test
    fun nonJsonFrameDecodesToNullInsteadOfThrowing() {
        assertNull(EnvelopeCodec.decode("not json at all"))
        assertNull(EnvelopeCodec.decode(""))
    }

    @Test
    fun unknownPayloadShapeDecodesToNullPayloadInsteadOfThrowing() {
        val envelope = assertNotNull(
            EnvelopeCodec.decode("""{"v":"2.0","type":"decision","payload":{"unexpected":true}}""")
        )
        assertNull(EnvelopeCodec.asDecision(envelope))
    }

    @Test
    fun approvalSetCoversEveryProducerSpelling() {
        // The phone sends approve_once while the terminal wrapper only ever matched "approve", so
        // every approval used to execute as a denial. All spellings must resolve identically.
        assertTrue(DecisionType.isApproval(DecisionType.APPROVE))
        assertTrue(DecisionType.isApproval(DecisionType.APPROVE_ONCE))
        assertTrue(DecisionType.isApproval(DecisionType.APPROVE_ALWAYS))
        assertTrue(DecisionType.isApproval(DecisionType.ANSWER_QUESTION))
        assertTrue(DecisionType.isApproval(DecisionType.PROCEED_PLAN))
        assertTrue(DecisionType.isApproval("APPROVE_ONCE"))

        assertFalse(DecisionType.isApproval(DecisionType.DENY))
        assertFalse(DecisionType.isApproval(DecisionType.CANCEL))
        assertFalse(DecisionType.isApproval(DecisionType.EXPIRED))
        assertFalse(DecisionType.isApproval(""))

        assertTrue(TestFixtures.decision(decision = DecisionType.APPROVE_ONCE).isApproval())
        assertFalse(TestFixtures.decision(decision = DecisionType.DENY).isApproval())
    }

    @Test
    fun expiryUsesEpochMillisNotIsoStringOrdering() {
        val request = TestFixtures.request()
        assertFalse(request.isExpiredAt(TestFixtures.CREATED_AT_MS))
        assertFalse(request.isExpiredAt(TestFixtures.EXPIRES_AT_MS - 1))
        assertTrue(request.isExpiredAt(TestFixtures.EXPIRES_AT_MS))
        assertTrue(request.isExpiredAt(TestFixtures.EXPIRES_AT_MS + 60_000))

        assertEquals(300_000L, request.remainingMs(TestFixtures.CREATED_AT_MS))
        assertEquals(0L, request.remainingMs(TestFixtures.EXPIRES_AT_MS + 5_000))
    }

    @Test
    fun requestWithoutEpochDeadlineNeverExpires() {
        // An older desktop omits the epoch fields; its requests must not be silently discarded.
        val legacy = TestFixtures.request().copy(expiresAtEpochMs = null)
        assertFalse(legacy.isExpiredAt(Long.MAX_VALUE))
        assertNull(legacy.remainingMs(TestFixtures.CREATED_AT_MS))
    }

    @Test
    fun shortWorkspaceNameReducesPathsToBasename() {
        assertEquals("handoff", shortWorkspaceName("/home/dev/code/handoff"))
        assertEquals("handoff", shortWorkspaceName("""C:\Users\dev\Desktop\HandOff\handoff\"""))
        assertEquals("handoff", shortWorkspaceName("file:///home/dev/handoff"))
        assertEquals("handoff", shortWorkspaceName("handoff"))
        assertNull(shortWorkspaceName(null))
        assertNull(shortWorkspaceName("   "))
    }
}
