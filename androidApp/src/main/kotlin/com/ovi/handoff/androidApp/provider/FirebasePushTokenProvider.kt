package com.ovi.handoff.androidApp.provider

import com.google.firebase.messaging.FirebaseMessaging
import com.ovi.handoff.mobile.domain.provider.PushTokenProvider
import kotlinx.coroutines.tasks.await

class FirebasePushTokenProvider : PushTokenProvider {
    override suspend fun getToken(): String? {
        return try {
            FirebaseMessaging.getInstance().token.await()
        } catch (e: Exception) {
            null
        }
    }
}
