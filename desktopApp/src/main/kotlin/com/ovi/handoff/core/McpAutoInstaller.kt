package com.ovi.handoff.core

import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Paths

object McpAutoInstaller {
    
    fun install() {
        val jarPath = getJarPath()
        val os = System.getProperty("os.name").lowercase()
        val configFiles = mutableListOf<File>()
        
        val home = System.getProperty("user.home")
        val appData = System.getenv("APPDATA")
        
        // Claude Desktop
        if (os.contains("win") && appData != null) {
            configFiles.add(File(appData, "Claude/claude_desktop_config.json"))
        } else if (os.contains("mac")) {
            configFiles.add(File(home, "Library/Application Support/Claude/claude_desktop_config.json"))
        }

        // Antigravity (Gemini) configuration
        configFiles.add(File(home, ".gemini/config/mcp_config.json"))

        var installedAny = false

        for (file in configFiles) {
            if (file.exists()) {
                println("Found MCP config at: ${file.absolutePath}")
                if (injectHandoffConfig(file, jarPath)) {
                    installedAny = true
                }
            } else {
                // If the directory exists but not the file, create it (mainly for gemini config)
                if (file.parentFile.exists()) {
                    println("Creating MCP config at: ${file.absolutePath}")
                    file.writeText("{}")
                    if (injectHandoffConfig(file, jarPath)) {
                        installedAny = true
                    }
                }
            }
        }

        if (!installedAny) {
            println("Could not find any standard MCP configuration files.")
            println("To install manually, add the following to your MCP config:")
            println(generateHandoffConfigSnippet(jarPath))
        } else {
            println("Installation complete! Please restart your AI Agent/IDE.")
        }
    }

    private fun getJarPath(): String {
        return try {
            val uri = McpAutoInstaller::class.java.protectionDomain.codeSource.location.toURI()
            val file = File(uri)
            if (file.extension == "jar") {
                file.absolutePath
            } else {
                // Not running from a jar (e.g., inside IDE during development)
                File("build/libs/handoff.jar").absolutePath
            }
        } catch (e: Exception) {
            File("handoff.jar").absolutePath
        }
    }

    private fun injectHandoffConfig(file: File, jarPath: String): Boolean {
        return try {
            val content = file.readText()
            val jsonElement = if (content.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(content).jsonObject
            
            val mcpServers = jsonElement["mcpServers"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
            
            val handoffConfig = buildJsonObject {
                put("command", "java")
                put("args", buildJsonArray {
                    add("-jar")
                    add(jarPath.replace("\\", "/"))
                    add("--mcp")
                })
            }
            
            mcpServers["handoff"] = handoffConfig
            
            val updatedConfig = buildJsonObject {
                jsonElement.forEach { (key, value) -> put(key, value) }
                put("mcpServers", JsonObject(mcpServers))
            }
            
            val json = Json { prettyPrint = true }
            file.writeText(json.encodeToString(updatedConfig))
            println("  -> Successfully injected 'handoff' server configuration.")
            true
        } catch (e: Exception) {
            println("  -> Failed to update config: ${e.message}")
            false
        }
    }

    private fun generateHandoffConfigSnippet(jarPath: String): String {
        val normalizedPath = jarPath.replace("\\", "/")
        return """
            "mcpServers": {
                "handoff": {
                    "command": "java",
                    "args": ["-jar", "$normalizedPath", "--mcp"]
                }
            }
        """.trimIndent()
    }
}
