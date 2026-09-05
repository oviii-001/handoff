package com.ovi.handoff.mobile.domain.settings

import kotlinx.coroutines.flow.Flow

/**
 * User preferences that actually affect behaviour.
 *
 * The settings screen previously held these in a `MutableStateFlow` inside its ViewModel and wired
 * them to nothing: toggling "require biometrics for critical actions" changed a boolean that no code
 * read, while the approval screen unconditionally demanded biometrics anyway. A settings screen that
 * reports state it does not control is worse than no settings screen.
 */
public data class HandoffSettings(
    val pushNotificationsEnabled: Boolean = true,
    /** Whether the notification shade offers Approve and Deny actions. */
    val notificationActionsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    /** Require device biometrics before approving a critical request. */
    val biometricsForCritical: Boolean = true,
    /** Also require biometrics for approve-from-notification, where no screen is shown. */
    val biometricsForShadeActions: Boolean = true,
    /** Minutes after which an untouched request is auto-denied. Zero leaves it to the request's own deadline. */
    val autoDenyMinutes: Int = 0
)

public interface SettingsRepository {
    public fun observe(): Flow<HandoffSettings>
    public suspend fun current(): HandoffSettings
    public suspend fun setPushNotificationsEnabled(enabled: Boolean)
    public suspend fun setNotificationActionsEnabled(enabled: Boolean)
    public suspend fun setVibrationEnabled(enabled: Boolean)
    public suspend fun setBiometricsForCritical(enabled: Boolean)
    public suspend fun setBiometricsForShadeActions(enabled: Boolean)
    public suspend fun setAutoDenyMinutes(minutes: Int)
}
