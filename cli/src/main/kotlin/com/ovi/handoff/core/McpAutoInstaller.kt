package com.ovi.handoff.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Registers HandOff as an MCP server in the IDEs that support it.
 *
 * Two problems made the original unreliable. It derived the launch command from the *current working
 * directory* (`./cli/build/install/cli/lib/...`), so the generated config broke the moment the repo
 * moved or `--install` was run from anywhere else. And it only wrote Claude Desktop and Gemini
 * configs, while the README advertised Cursor and Claude Code support that was never implemented.
 *
 * The launch command is now derived from the classpath of the running process, and every write goes
 * through a backup and an atomic replace so a half-written config cannot break the user's IDE.
 */
public object McpAutoInstaller {

    public fun install() {
        val launcher = resolveLauncher()
        val targets = discoverTargets()

        println("Launch command:")
        println("  ${launcher.command} ${launcher.args.joinToString(" ")}")
        println()

        var installed = 0
        var skipped = 0

        for (target in targets) {
            when {
                target.file.exists() -> {
                    if (inject(target, launcher)) installed++ else skipped++
                }
                // Only create a config for a tool that is actually installed, inferred from the
                // presence of its parent directory. Writing configs for absent tools litters $HOME.
                target.file.parentFile?.exists() == true || target.createParentIfToolPresent() -> {
                    println("Creating ${target.label} config at ${target.file.absolutePath}")
                    if (inject(target, launcher)) installed++ else skipped++
                }
                else -> {
                    skipped++
                }
            }
        }

        println()
        if (installed == 0) {
            println("No MCP configuration files were found.")
            println("Add this to your agent's MCP config manually:")
            println()
            println(manualSnippet(launcher))
        } else {
            println("Updated $installed configuration file(s). Restart your IDE or agent to pick it up.")
            if (skipped > 0) {
                println("Skipped $skipped location(s) whose tool does not appear to be installed.")
            }
        }
    }

    // -------------------------------------------------------------------------------------
    // Launcher resolution
    // -------------------------------------------------------------------------------------

    public class Launcher(public val command: String, public val args: List<String>)

    /**
     * Builds the command an IDE should run to start the MCP server.
     *
     * Resolution order, most specific first:
     *  1. `HANDOFF_LAUNCHER`, for packaged installs and for anyone wrapping the daemon.
     *  2. The start script generated next to our own jar, which handles the classpath itself.
     *  3. This process's own `java` binary and classpath, which is correct by construction whatever
     *     the layout, unlike a path guessed from the working directory.
     */
    public fun resolveLauncher(): Launcher {
        System.getenv("HANDOFF_LAUNCHER")?.takeIf { it.isNotBlank() }?.let { override ->
            return Launcher(override, listOf("--mcp"))
        }

        startScriptNextToJar()?.let { script ->
            return Launcher(script.absolutePath, listOf("--mcp"))
        }

        val javaBinary = File(System.getProperty("java.home"), if (isWindows()) "bin/java.exe" else "bin/java")
        val javaPath = if (javaBinary.exists()) javaBinary.absolutePath else "java"

        return Launcher(
            javaPath,
            listOf("-classpath", System.getProperty("java.class.path"), "com.ovi.handoff.MainKt", "--mcp")
        )
    }

    /** Finds `bin/handoff` or `bin/cli` in an `installDist`-style layout containing our jar. */
    private fun startScriptNextToJar(): File? {
        val location = runCatching {
            McpAutoInstaller::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) }
        }.getOrNull() ?: return null

        if (!location.isFile || !location.name.endsWith(".jar")) return null

        // installDist produces <root>/lib/<jar> alongside <root>/bin/<script>.
        val installRoot = location.parentFile?.takeIf { it.name == "lib" }?.parentFile ?: return null
        val binDir = File(installRoot, "bin").takeIf { it.isDirectory } ?: return null

        val candidates = if (isWindows()) {
            listOf("handoff.bat", "cli.bat")
        } else {
            listOf("handoff", "cli")
        }
        return candidates.map { File(binDir, it) }.firstOrNull { it.isFile }
    }

    // -------------------------------------------------------------------------------------
    // Targets
    // -------------------------------------------------------------------------------------

    private class Target(
        val label: String,
        val file: File,
        /** Directory whose existence proves the tool is installed, when the config dir is missing. */
        val toolMarker: File?
    ) {
        fun createParentIfToolPresent(): Boolean {
            val marker = toolMarker ?: return false
            if (!marker.exists()) return false
            return file.parentFile?.mkdirs() == true
        }
    }

    private fun discoverTargets(): List<Target> {
        val home = File(System.getProperty("user.home"))
        val appData = System.getenv("APPDATA")?.let(::File)
        val targets = mutableListOf<Target>()

        // Claude Desktop
        when {
            isWindows() && appData != null ->
                targets += Target("Claude Desktop", File(appData, "Claude/claude_desktop_config.json"), File(appData, "Claude"))
            isMac() -> targets += Target(
                "Claude Desktop",
                File(home, "Library/Application Support/Claude/claude_desktop_config.json"),
                File(home, "Library/Application Support/Claude")
            )
            else -> targets += Target(
                "Claude Desktop",
                File(home, ".config/Claude/claude_desktop_config.json"),
                File(home, ".config/Claude")
            )
        }

        // Claude Code keeps MCP servers in its own top-level config.
        targets += Target("Claude Code", File(home, ".claude.json"), File(home, ".claude"))

        // Cursor
        targets += Target("Cursor", File(home, ".cursor/mcp.json"), File(home, ".cursor"))

        // Windsurf
        targets += Target(
            "Windsurf",
            File(home, ".codeium/windsurf/mcp_config.json"),
            File(home, ".codeium")
        )

        // Antigravity / Gemini
        targets += Target("Antigravity", File(home, ".gemini/config/mcp_config.json"), File(home, ".gemini"))
        targets += Target("Antigravity IDE", File(home, ".gemini/antigravity-ide/mcp_config.json"), File(home, ".gemini"))

        // VS Code user-level MCP config
        val vsCodeUserDir = when {
            isWindows() && appData != null -> File(appData, "Code/User")
            isMac() -> File(home, "Library/Application Support/Code/User")
            else -> File(home, ".config/Code/User")
        }
        targets += Target("VS Code", File(vsCodeUserDir, "mcp.json"), vsCodeUserDir)

        return targets
    }

    // -------------------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------------------

    private val writer = Json { prettyPrint = true }
    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun inject(target: Target, launcher: Launcher): Boolean {
        return runCatching {
            val existingText = if (target.file.exists()) target.file.readText() else ""
            val root = if (existingText.isBlank()) {
                JsonObject(emptyMap())
            } else {
                reader.parseToJsonElement(existingText).jsonObject
            }

            // VS Code nests servers under "servers"; everyone else uses "mcpServers".
            val serversKey = if (root.containsKey("servers")) "servers" else "mcpServers"
            val servers = root[serversKey]?.jsonObject?.toMutableMap() ?: mutableMapOf()

            val entry = buildJsonObject {
                put("command", launcher.command)
                put("args", buildJsonArray { launcher.args.forEach { add(it) } })
            }

            if (servers["handoff"] == entry) {
                println("  ${target.label}: already up to date.")
                return true
            }

            servers["handoff"] = entry

            val updated = buildJsonObject {
                root.forEach { (key, value) -> if (key != serversKey) put(key, value) }
                put(serversKey, JsonObject(servers))
            }

            // Back up before replacing: an IDE config often holds settings the user cannot recover.
            if (target.file.exists()) {
                target.file.copyTo(File(target.file.parentFile, "${target.file.name}.handoff-backup"), overwrite = true)
            }
            SecureFiles.writeAtomicText(target.file, writer.encodeToString(JsonObject.serializer(), updated))

            println("  ${target.label}: registered 'handoff'.")
            true
        }.getOrElse { cause ->
            println("  ${target.label}: could not update ${target.file.absolutePath} (${cause.message}).")
            false
        }
    }

    private fun manualSnippet(launcher: Launcher): String {
        val args = launcher.args.joinToString(", ") { "\"${it.replace("\\", "\\\\")}\"" }
        val command = launcher.command.replace("\\", "\\\\")
        return """
            {
              "mcpServers": {
                "handoff": {
                  "command": "$command",
                  "args": [$args]
                }
              }
            }
        """.trimIndent()
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun isMac(): Boolean = System.getProperty("os.name").lowercase().let {
        it.contains("mac") || it.contains("darwin")
    }
}
