package com.ovi.handoff.core

import com.ovi.handoff.shared.crypto.Canonical
import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.DecisionType
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionType
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.RiskLevel
import com.ovi.handoff.shared.model.isApproval
import java.io.File
import java.security.KeyPairGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CommandWrapperRiskTest {

    @Test
    fun recursiveForcedDeleteIsCritical() {
        val risk = CommandWrapper.assessRisk("rm -rf /")
        assertEquals(RiskLevel.CRITICAL, risk.level)
        assertTrue(risk.reasons.any { it.contains("Recursive", ignoreCase = true) })
    }

    @Test
    fun filesystemAndDatabaseDestructionAreCritical() {
        assertEquals(RiskLevel.CRITICAL, CommandWrapper.assessRisk("mkfs.ext4 /dev/sda1").level)
        assertEquals(RiskLevel.CRITICAL, CommandWrapper.assessRisk("dd if=/dev/zero of=/dev/sda").level)
        assertEquals(RiskLevel.CRITICAL, CommandWrapper.assessRisk("psql -c 'DROP TABLE users'").level)
    }

    @Test
    fun chainedCommandsAreCalledOutBecauseApprovingOneApprovesAll() {
        val risk = CommandWrapper.assessRisk("npm run build && node deploy.js")
        assertEquals(RiskLevel.HIGH, risk.level)
        assertTrue(risk.reasons.any { it.contains("Chains", ignoreCase = true) })
    }

    @Test
    fun elevatedPrivilegesAreHigh() {
        assertEquals(RiskLevel.HIGH, CommandWrapper.assessRisk("sudo systemctl restart nginx").level)
        assertEquals(RiskLevel.HIGH, CommandWrapper.assessRisk("chmod 777 /etc/passwd").level)
    }

    @Test
    fun ordinaryCommandsStayMediumWithAnHonestReason() {
        val risk = CommandWrapper.assessRisk("npm run build")
        assertEquals(RiskLevel.MEDIUM, risk.level)
        assertTrue(risk.reasons.isNotEmpty())
    }

    @Test
    fun aFilenameThatMentionsRmIsNotTreatedAsADelete() {
        // Substring matching on "rm " used to fire on any command whose text happened to contain it.
        val risk = CommandWrapper.assessRisk("""git commit -m "confirm rm handling"""")
        assertEquals(RiskLevel.MEDIUM, risk.level)
    }
}

class DecisionVerificationTest {

    private val keyDir = File(System.getProperty("java.io.tmpdir"), "handoff-test-keys-${System.nanoTime()}")

    private fun request() = RequestFactory.build(
        pairId = "pair-test",
        agent = AgentInfo(id = "cursor", name = "Cursor"),
        permission = PermissionInfo(type = PermissionType.TERMINAL, command = "npm run build"),
        risk = RiskInfo(level = RiskLevel.MEDIUM, reasons = listOf("test")),
        options = listOf("approve", "deny"),
        workspacePath = "/home/dev/handoff"
    )

    @Test
    fun requestFactoryPopulatesEpochTimestampsAndAShortProjectLabel() {
        val request = request()
        assertNotNull(request.createdAtEpochMs)
        assertNotNull(request.expiresAtEpochMs)
        assertTrue(request.expiresAtEpochMs!! > request.createdAtEpochMs!!)

        // The phone shows `project` as a label, so it must be the folder name, not the whole path.
        assertEquals("handoff", request.session.project)
        assertEquals("/home/dev/handoff", request.session.workspace)
    }

    @Test
    fun aGenuineSignatureVerifiesAndATamperedOneDoesNot() {
        val generator = KeyPairGenerator.getInstance("Ed25519")
        val phoneKeys = generator.generateKeyPair()
        val store = KeyStoreManager(keyDir)

        val decision = PermissionDecision(
            requestId = "req-1",
            decision = DecisionType.APPROVE_ONCE,
            issuedAt = "2026-09-04T12:00:00Z",
            nonce = "nonce-1",
            deviceId = "phone",
            requestHash = Canonical.requestHash(request()),
            signature = ""
        )

        val signature = KeyStoreManager.encodeSignature(
            store.sign(Canonical.decisionBytes(decision), phoneKeys.private)
        )
        val signed = decision.copy(signature = signature)

        assertTrue(
            KeyStoreManager.verify(Canonical.decisionBytes(signed), signed.signature, phoneKeys.public)
        )

        // Flipping the verdict after signing must invalidate it. This is the check that was missing
        // entirely: the desktop accepted any decision frame it received.
        val tampered = signed.copy(decision = DecisionType.DENY)
        assertFalse(
            KeyStoreManager.verify(Canonical.decisionBytes(tampered), tampered.signature, phoneKeys.public)
        )

        // So must swapping in a different request's hash.
        val relinked = signed.copy(requestHash = "0".repeat(64))
        assertFalse(
            KeyStoreManager.verify(Canonical.decisionBytes(relinked), relinked.signature, phoneKeys.public)
        )
    }

    @Test
    fun verificationRejectsMissingOrMalformedInputInsteadOfThrowing() {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val data = "payload".toByteArray()

        assertFalse(KeyStoreManager.verify(data, null, keys.public))
        assertFalse(KeyStoreManager.verify(data, "", keys.public))
        assertFalse(KeyStoreManager.verify(data, "not-base64-@@@", keys.public))
        assertFalse(KeyStoreManager.verify(data, "AAAA", null))
    }

    @Test
    fun publicKeyDecodingAcceptsUrlSafeBase64AndRejectsGarbage() {
        val keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val encoded = KeyStoreManager.encodePublicKey(keys.public)

        val decoded = KeyStoreManager.decodePublicKey(encoded)
        assertNotNull(decoded)
        assertTrue(keys.public.encoded.contentEquals(decoded.encoded))

        assertNull(KeyStoreManager.decodePublicKey(null))
        assertNull(KeyStoreManager.decodePublicKey(""))
        assertNull(KeyStoreManager.decodePublicKey("////not-a-key////"))
    }

    @Test
    fun everyApprovalSpellingIsHonoured() {
        // The regression that made `--exec` unusable: the phone sends approve_once.
        assertTrue(
            PermissionDecision(
                requestId = "r", decision = DecisionType.APPROVE_ONCE, issuedAt = "t",
                nonce = "n", deviceId = "d", requestHash = "h", signature = "s"
            ).isApproval()
        )
        assertFalse(
            PermissionDecision(
                requestId = "r", decision = DecisionType.DENY, issuedAt = "t",
                nonce = "n", deviceId = "d", requestHash = "h", signature = "s"
            ).isApproval()
        )
    }
}

class RequestFactoryPathTest {

    @Test
    fun resolvesFileUrisAndBlankInput() {
        assertNull(RequestFactory.resolveWorkspacePath(null))
        assertNull(RequestFactory.resolveWorkspacePath("   "))

        val resolved = RequestFactory.resolveWorkspacePath("file:///tmp")
        assertNotNull(resolved)
        assertFalse(resolved.startsWith("file:"))
    }
}
