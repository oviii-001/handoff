package com.ovi.handoff.mobile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ovi.handoff.shared.model.*

@Entity(tableName = "permission_requests")
data class PermissionRequestEntity(
    @PrimaryKey val id: String,
    val protocolVersion: String,
    val agentId: String,
    val agentName: String,
    val sessionId: String,
    val permissionType: String,
    val permissionCommand: String?,
    val permissionDescription: String?,
    val riskLevel: String,
    val riskReasons: String,
    val options: String,
    val createdAt: String,
    val expiresAt: String,
    val isPending: Boolean
)

fun PermissionRequest.toEntity() = PermissionRequestEntity(
    id = id,
    protocolVersion = protocolVersion,
    agentId = agent.id,
    agentName = agent.name,
    sessionId = session.id,
    permissionType = permission.type,
    permissionCommand = permission.command,
    permissionDescription = permission.description,
    riskLevel = risk.level,
    riskReasons = risk.reasons.joinToString(","),
    options = options.joinToString(","),
    createdAt = createdAt,
    expiresAt = expiresAt,
    isPending = true
)

fun PermissionRequestEntity.toDomain() = PermissionRequest(
    id = id,
    protocolVersion = protocolVersion,
    agent = AgentInfo(id = agentId, name = agentName),
    session = SessionInfo(id = sessionId),
    permission = PermissionInfo(type = permissionType, command = permissionCommand, description = permissionDescription),
    risk = RiskInfo(level = riskLevel, reasons = riskReasons.split(",").filter { it.isNotEmpty() }),
    options = options.split(",").filter { it.isNotEmpty() },
    createdAt = createdAt,
    expiresAt = expiresAt
)
