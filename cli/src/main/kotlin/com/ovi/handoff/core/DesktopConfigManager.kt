package com.ovi.handoff.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class DesktopConfig(
    val pairId: String,
    val relayHost: String = "agentapprove-relay.ismamhasanovi.workers.dev"
)

object DesktopConfigManager {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val configDir = File(System.getProperty("user.home"), ".handoff")
    private val configFile = File(configDir, "config.json")

    fun loadConfig(): DesktopConfig {
        if (!configFile.exists()) {
            val newConfig = DesktopConfig(
                pairId = "pair-" + UUID.randomUUID().toString().take(8),
                relayHost = System.getenv("HANDOFF_RELAY_HOST") ?: "agentapprove-relay.ismamhasanovi.workers.dev"
            )
            saveConfig(newConfig)
            return newConfig
        }
        return try {
            json.decodeFromString<DesktopConfig>(configFile.readText())
        } catch (e: Exception) {
            val fallback = DesktopConfig(
                pairId = "pair-" + UUID.randomUUID().toString().take(8),
                relayHost = System.getenv("HANDOFF_RELAY_HOST") ?: "agentapprove-relay.ismamhasanovi.workers.dev"
            )
            saveConfig(fallback)
            fallback
        }
    }

    fun saveConfig(config: DesktopConfig) {
        try {
            configDir.mkdirs()
            configFile.writeText(json.encodeToString(DesktopConfig.serializer(), config))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateNewPairId(): String {
        val current = loadConfig()
        val updated = current.copy(pairId = "pair-" + UUID.randomUUID().toString().take(8))
        saveConfig(updated)
        return updated.pairId
    }

    fun getPairId(): String = System.getenv("HANDOFF_PAIR_ID") ?: loadConfig().pairId

    fun getRelayHost(): String = System.getenv("HANDOFF_RELAY_HOST") ?: loadConfig().relayHost
}
