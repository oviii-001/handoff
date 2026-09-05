package com.ovi.handoff.mobile.domain.usecase

/**
 * Intentionally empty.
 *
 * Decision building, signing and sending now live in [SubmitDecisionUseCase]. Two callers used to
 * assemble their own unsigned `PermissionDecision` through the old use case here, which is how they
 * drifted apart on device id and both ended up sending an empty signature.
 *
 * This file can be deleted; it remains only so the change does not depend on a file removal.
 */
