package com.ovi.handoff.mobile.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.ovi.handoff.mobile.domain.settings.HandoffSettings
import com.ovi.handoff.mobile.domain.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Persists the settings the app actually acts on.
 *
 * The settings screen used to keep these in its ViewModel's state and nowhere else, so every toggle
 * reset on process death and none of them changed behaviour. Reads are served from an in-memory
 * [MutableStateFlow] so the UI never blocks on disk, and writes go through [Dispatchers.IO].
 */
public class SettingsRepositoryImpl(
    private val context: Context
) : SettingsRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("handoff_settings", Context.MODE_PRIVATE)
    }

    private val state = MutableStateFlow(HandoffSettings())

    @Volatile
    private var loaded = false

    override fun observe(): Flow<HandoffSettings> = state.asStateFlow()

    override suspend fun current(): HandoffSettings {
        ensureLoaded()
        return state.value
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        withContext(Dispatchers.IO) {
            val defaults = HandoffSettings()
            state.value = HandoffSettings(
                pushNotificationsEnabled = prefs.getBoolean(KEY_PUSH, defaults.pushNotificationsEnabled),
                notificationActionsEnabled = prefs.getBoolean(KEY_ACTIONS, defaults.notificationActionsEnabled),
                vibrationEnabled = prefs.getBoolean(KEY_VIBRATION, defaults.vibrationEnabled),
                biometricsForCritical = prefs.getBoolean(KEY_BIOMETRIC_CRITICAL, defaults.biometricsForCritical),
                biometricsForShadeActions = prefs.getBoolean(KEY_BIOMETRIC_SHADE, defaults.biometricsForShadeActions),
                autoDenyMinutes = prefs.getInt(KEY_AUTO_DENY, defaults.autoDenyMinutes)
            )
            loaded = true
        }
    }

    override suspend fun setPushNotificationsEnabled(enabled: Boolean) =
        update({ it.copy(pushNotificationsEnabled = enabled) }) { putBoolean(KEY_PUSH, enabled) }

    override suspend fun setNotificationActionsEnabled(enabled: Boolean) =
        update({ it.copy(notificationActionsEnabled = enabled) }) { putBoolean(KEY_ACTIONS, enabled) }

    override suspend fun setVibrationEnabled(enabled: Boolean) =
        update({ it.copy(vibrationEnabled = enabled) }) { putBoolean(KEY_VIBRATION, enabled) }

    override suspend fun setBiometricsForCritical(enabled: Boolean) =
        update({ it.copy(biometricsForCritical = enabled) }) { putBoolean(KEY_BIOMETRIC_CRITICAL, enabled) }

    override suspend fun setBiometricsForShadeActions(enabled: Boolean) =
        update({ it.copy(biometricsForShadeActions = enabled) }) { putBoolean(KEY_BIOMETRIC_SHADE, enabled) }

    override suspend fun setAutoDenyMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(0, MAX_AUTO_DENY_MINUTES)
        update({ it.copy(autoDenyMinutes = clamped) }) { putInt(KEY_AUTO_DENY, clamped) }
    }

    private suspend fun update(
        transform: (HandoffSettings) -> HandoffSettings,
        write: SharedPreferences.Editor.() -> Unit
    ) {
        ensureLoaded()
        state.value = transform(state.value)
        withContext(Dispatchers.IO) {
            prefs.edit().apply(write).apply()
        }
    }

    private companion object {
        const val KEY_PUSH = "push_notifications_enabled"
        const val KEY_ACTIONS = "notification_actions_enabled"
        const val KEY_VIBRATION = "vibration_enabled"
        const val KEY_BIOMETRIC_CRITICAL = "biometrics_for_critical"
        const val KEY_BIOMETRIC_SHADE = "biometrics_for_shade_actions"
        const val KEY_AUTO_DENY = "auto_deny_minutes"
        const val MAX_AUTO_DENY_MINUTES = 120
    }
}
