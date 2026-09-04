package com.ovi.handoff.mobile.domain.provider

interface PushTokenProvider {
    suspend fun getToken(): String?
}
