package com.ovi.handoff.mobile.domain.notification

import com.ovi.handoff.shared.model.PermissionRequest

/**
 * Domain-level contract for notifying the user of pending agent permission requests.
 * Implementations in platform/app layers handle high-priority Android notifications,
 * lock-screen visibility, and direct notification shade action buttons.
 */
interface NotificationNotifier {
    fun postPermissionRequestNotification(request: PermissionRequest, pairId: String)
    fun dismissNotification(requestId: String)
}
