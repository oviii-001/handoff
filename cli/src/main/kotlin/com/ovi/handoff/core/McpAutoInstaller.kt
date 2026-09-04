package com.ovi.handoff.core

import kotlinx.serialization.json.*
import java.io.File

object McpAutoInstaller {
    
    fun install() {
        val (execCommand, execArgs) = getExecutableCommand()
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
        // Antigravity IDE root configuration
        configFiles.add(File(home, ".gemini/antigravity-ide/mcp_config.json"))

        var installedAny = false

        for (file in configFiles) {
            if (file.exists()) {
                println("Found MCP config at: ${file.absolutePath}")
                if (injectHandoffConfig(file, execCommand, execArgs)) {
                    installedAny = true
                }
            } else {
                if (file.parentFile?.exists() == true) {
                    println("Creating MCP config at: ${file.absolutePath}")
                    file.writeText("{}")
                    if (injectHandoffConfig(file, execCommand, execArgs)) {
                        installedAny = true
                    }
                }
            }
        }

        if (!installedAny) {
            println("Could not find any standard MCP configuration files.")
            println("To install manually, add the following to your MCP config:")
            println(generateHandoffConfigSnippet(execCommand, execArgs))
        } else {
            println("Installation complete! Please restart your AI Agent/IDE.")
        }
    }

    private fun getExecutableCommand(): Pair<String, List<String>> {
        val currentDir = File(".").canonicalFile
        val rootDir = if (currentDir.name == "cli") currentDir.parentFile else currentDir
        val libDir = File(rootDir, "cli/build/install/cli/lib").absolutePath
        val cp = "$libDir/*"
        return Pair("java", listOf("-classpath", cp, "com.ovi.handoff.MainKt", "--mcp"))
    }

    private fun injectHandoffConfig(file: File, command: String, args: List<String>): Boolean {
        return try {
            val content = file.readText()
            val jsonElement = if (content.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(content).jsonObject
            
            val mcpServers = jsonElement["mcpServers"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
            
            val handoffConfig = buildJsonObject {
                put("command", command)
                put("args", buildJsonArray {
                    args.forEach { add(it) }
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

    private fun generateHandoffConfigSnippet(command: String, args: List<String>): String {
        val formattedArgs = args.joinToString(", ") { "\"$it\"" }
        val escapedCommand = command.replace("\\", "\\\\")
        return """
            "mcpServers": {
                "handoff": {
                    "command": "$escapedCommand",
                    "args": [$formattedArgs]
                }
            }
        """.trimIndent()
    }
}
