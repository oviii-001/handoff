package com.ovi.handoff.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class PermissionDecision(
    val requestId: String,
    val decision: String, // 'approve_once', 'approve_always', 'deny', 'cancel', 'answer_question', 'proceed_plan'
    val issuedAt: String,
    val nonce: String,
    val deviceId: String,
    val signature: String,
    val feedback: String? = null,
    val selectedOptions: List<String>? = null
)
