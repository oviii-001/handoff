package com.ovi.handoff.mobile.domain.usecase

import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.security.DecisionSigner
import com.ovi.handoff.shared.crypto.Canonical
import com.ovi.handoff.shared.model.PermissionDecision
import com.ovi.handoff.shared.model.PermissionRequest
import java.time.Instant
import java.util.UUID

/**
 * Builds, signs and sends a decision.
 *
 * Centralised on purpose. The approval screen and the notification action receiver each assembled
 * their own `PermissionDecision`, which is how one came to report `deviceId = "pixel-9-hardware"`
 * and the other `"android-device"`, and how both came to send `signature = ""`. Signing has exactly
 * one implementation now, so neither caller can skip it.
 */
public class SubmitDecisionUseCase(
    private val relayRepository: RelayRepository,
    private val signer: DecisionSigner
) {

    public suspend operator fun invoke(
        pairId: String,
        request: PermissionRequest,
        verdict: String,
        feedback: String? = null,
        selectedOptions: List<String>? = null
    ): Result<Unit> {
        val unsigned = PermissionDecision(
            requestId = request.id,
            decision = verdict,
            issuedAt = Instant.now().toString(),
            nonce = UUID.randomUUID().toString(),
            deviceId = signer.deviceId(),
            // Binds this answer to this exact request. A decision without it could be replayed
            // against a different, more dangerous request that shares nothing but an id.
            requestHash = Canonical.requestHash(request),
            signature = "",
            feedback = feedback?.takeIf { it.isNotBlank() },
            selectedOptions = selectedOptions?.takeIf { it.isNotEmpty() }
        )

        val signature = signer.sign(Canonical.decisionBytes(unsigned))
            ?: return Result.failure(
                IllegalStateException(
                    "This device could not sign the decision, so it was not sent. Re-pair to create a new signing key."
                )
            )

        return relayRepository.sendDecision(pairId, unsigned.copy(signature = signature))
    }
}
