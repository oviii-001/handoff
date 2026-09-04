package com.ovi.handoff.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ovi.handoff.MainActivity
import com.ovi.handoff.androidApp.worker.SyncRequestsWorker
import timber.log.Timber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository

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
        
        // Trigger WorkManager to fetch the actual request from WebSocket and save to Room
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<SyncRequestsWorker>()
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        
        // This is triggered when the app is in the background or killed
        // We only expect data payloads to wake up the app and trigger a sync, or show a local notification
        
        val requestId = message.data["requestId"]
        if (requestId != null) {
            showNotification(requestId)
        }
    }

    private fun showNotification(requestId: String) {
        val channelId = "agentapprove_requests"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Permission Requests",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // Pass requestId so the activity knows what to load
            putExtra("requestId", requestId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            // We need an icon, assuming there's a default one or we'll use standard Android one for now
            .setSmallIcon(android.R.drawable.ic_dialog_alert) 
            .setContentTitle("Agent Permission Request")
            .setContentText("A new permission request is pending approval.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(requestId.hashCode(), notification)
    }
}
