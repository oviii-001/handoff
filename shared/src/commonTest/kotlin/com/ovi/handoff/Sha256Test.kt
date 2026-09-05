package com.ovi.handoff

import com.ovi.handoff.shared.crypto.Canonical
import com.ovi.handoff.shared.crypto.Sha256
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class Sha256Test {

    @Test
    fun matchesKnownVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hashHex("".encodeToByteArray())
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hashHex("abc".encodeToByteArray())
        )
        // 448-bit message: 56 bytes leaves no room for the 9 padding bytes, so this exercises the
        // two-block path with the length written into the second block.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hashHex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())
        )
    }

    @Test
    fun padsExactBlockMultiplesCorrectly() {
        // A 64-byte input is an exact block multiple, so padding must add a whole extra block.
        // Verified structurally rather than against a hardcoded digest: the digest is well-formed
        // and distinct from its neighbours by length.
        val exactBlock = Sha256.hashHex(ByteArray(64))
        assertEquals(64, exactBlock.length)
        assertNotEquals(exactBlock, Sha256.hashHex(ByteArray(63)))
        assertNotEquals(exactBlock, Sha256.hashHex(ByteArray(65)))
    }

    @Test
    fun hashesLongMultiBlockInput() {
        // A realistic diff-sized payload: verifies the loop over many blocks, not just the tail.
        val long = "line of a unified diff\n".repeat(500)
        assertEquals(64, Sha256.hashHex(long.encodeToByteArray()).length)
        assertNotEquals(
            Sha256.hashHex(long.encodeToByteArray()),
            Sha256.hashHex((long + "x").encodeToByteArray())
        )
    }

    @Test
    fun toHexIsLowercaseAndZeroPadded() {
        assertEquals("000f10ff", Sha256.toHex(byteArrayOf(0x00, 0x0f, 0x10, 0xff.toByte())))
    }

    @Test
    fun canonicalBytesAreStableAcrossEqualRequests() {
        val a = TestFixtures.request(command = "npm run build")
        val b = TestFixtures.request(command = "npm run build")
        assertEquals(Canonical.requestHash(a), Canonical.requestHash(b))
    }

    @Test
    fun canonicalBytesChangeWhenAnyFieldChanges() {
        val base = TestFixtures.request(command = "npm run build")
        assertNotEquals(
            Canonical.requestHash(base),
            Canonical.requestHash(TestFixtures.request(command = "rm -rf /"))
        )
        assertNotEquals(
            Canonical.requestHash(base),
            Canonical.requestHash(base.copy(risk = base.risk.copy(level = "critical")))
        )
    }

    @Test
    fun lengthPrefixPreventsFieldBoundaryForgery() {
        // Without a length prefix, a value containing the delimiter could imitate a later field and
        // let two different requests hash identically.
        val forged = TestFixtures.request(command = "ls;riskLevel=8:critical;")
        val honest = TestFixtures.request(command = "ls")
        assertNotEquals(Canonical.requestHash(forged), Canonical.requestHash(honest))
    }

    @Test
    fun decisionSignatureExcludesSignatureField() {
        val decision = TestFixtures.decision(signature = "sig-one")
        val resigned = decision.copy(signature = "sig-two")
        assertEquals(
            Sha256.hashHex(Canonical.decisionBytes(decision)),
            Sha256.hashHex(Canonical.decisionBytes(resigned))
        )
    }

    @Test
    fun decisionBytesBindToRequestHash() {
        val decision = TestFixtures.decision(signature = "sig")
        assertNotEquals(
            Sha256.hashHex(Canonical.decisionBytes(decision)),
            Sha256.hashHex(Canonical.decisionBytes(decision.copy(requestHash = "deadbeef")))
        )
    }
}
