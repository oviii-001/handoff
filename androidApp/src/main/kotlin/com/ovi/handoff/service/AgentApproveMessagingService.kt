package com.ovi.handoff.service

import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ovi.handoff.androidApp.worker.SyncRequestsWorker
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Handles incoming FCM push notifications from the Cloudflare Relay.
 *
 * FCM messages act as wake-up signals for background sync. When a notification arrives,
 * this service registers/updates tokens, schedules [SyncRequestsWorker], and triggers
 * [RelayRepository.connect] to establish websocket synchronization.
 *
 * Local notifications are managed centrally by [HandoffNotificationManager] once requests
 * are synced into the local database and verified.
 */
class AgentApproveMessagingService : FirebaseMessagingService() {

    private val pairingRepository: PairingRepository by inject()
    private val relayRepository: RelayRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Timber.d("FCM Token updated: $token")

        serviceScope.launch {
            val pairId = pairingRepository.getPairId()
            if (pairId != null) {
                relayRepository.registerPushToken(pairId, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Timber.d("FCM push message received: ${message.data}")

        // Trigger WorkManager to fetch the actual request from WebSocket and save to Room
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SyncRequestsWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(workRequest)

        // Also initiate immediate connection if pairId is active
        serviceScope.launch {
            val pairId = pairingRepository.getPairId()
            if (pairId != null) {
                relayRepository.connect(pairId)
            }
        }
    }
}

