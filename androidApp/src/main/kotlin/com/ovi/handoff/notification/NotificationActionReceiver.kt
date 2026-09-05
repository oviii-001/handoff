package com.ovi.handoff.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.settings.SettingsRepository
import com.ovi.handoff.mobile.domain.usecase.SubmitDecisionUseCase
import com.ovi.handoff.shared.model.requiresStrongAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * Handles background actions tapped directly from the Android status-bar notification shade
 * (e.g. "Approve Once" or "Deny") without requiring the user to switch into the app.
 *
 * All decisions are cryptographically signed with the device's private key via [SubmitDecisionUseCase].
 * Shade approvals are blocked if biometrics are required by policy.
 */
public class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val relayRepository: RelayRepository by inject()
    private val submitDecisionUseCase: SubmitDecisionUseCase by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val pairId = intent.getStringExtra(EXTRA_PAIR_ID) ?: return
        val decisionType = intent.getStringExtra(EXTRA_DECISION_TYPE) ?: "deny"

        // Dismiss notification immediately from the status bar
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(requestId.hashCode())

        // Send cryptographic decision to Desktop Daemon via Relay
        scope.launch {
            try {
                val request = relayRepository.getRequest(requestId)
                if (request == null) {
                    Timber.w("Notification action received for unknown request: $requestId")
                    return@launch
                }

                val settings = settingsRepository.current()
                if (!settings.notificationActionsEnabled) {
                    Timber.w("Notification actions are disabled in settings; ignoring action $decisionType for $requestId")
                    return@launch
                }

                // If user wants to approve from shade, check if biometrics are required
                if (decisionType == "approve_once") {
                    val requiresBiometrics = settings.biometricsForShadeActions ||
                        (settings.biometricsForCritical && request.requiresStrongAuth())
                    if (requiresBiometrics) {
                        Timber.i("Shade approval blocked for $requestId: biometrics required. User must open the app.")
                        return@launch
                    }
                }

                val result = submitDecisionUseCase(
                    pairId = pairId,
                    request = request,
                    verdict = decisionType
                )
                if (result.isSuccess) {
                    Timber.d("Notification action $decisionType sent successfully for request $requestId")
                } else {
                    Timber.e("Failed to send notification decision: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error processing notification action")
            }
        }
    }

    public companion object {
        public const val ACTION_APPROVE: String = "com.ovi.handoff.ACTION_APPROVE"
        public const val ACTION_DENY: String = "com.ovi.handoff.ACTION_DENY"
        public const val EXTRA_REQUEST_ID: String = "extra_request_id"
        public const val EXTRA_PAIR_ID: String = "extra_pair_id"
        public const val EXTRA_DECISION_TYPE: String = "extra_decision_type"
    }
}
