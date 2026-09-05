package com.ovi.handoff.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Registers HandOff as an MCP server in the IDEs that support it.
 *
 * Three things here were wrong in ways the user could not see:
 *
 *  - **The launch command was derived from the working directory**, so the generated config broke as
 *    soon as the repository moved or `--install` was run from elsewhere. It is now derived from the
 *    running process's own installation, and uses a wildcard classpath so a dependency version bump
 *    does not invalidate every config written before it.
 *  - **VS Code was written the wrong shape.** VS Code reads `servers` with an explicit
 *    `"type": "stdio"`; it was given `mcpServers`, which it ignores entirely. The integration was
 *    advertised and silently did nothing. The shape is now a property of each target rather than
 *    guessed from whatever the file already contained.
 *  - **Nothing could be verified afterwards.** [registrationReport] exists so `--doctor` can tell
 *    the user which IDEs actually picked HandOff up.
 */
public object McpAutoInstaller {

    private const val SERVER_KEY = "handoff"

    private val writer = Json { prettyPrint = true }
    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }

    public fun install() {
        val launcher = resolveLauncher()

        println("Launch command:")
        println("  ${launcher.command} ${launcher.args.joinToString(" ")}")
        println()

        install(discoverTargets(), launcher, ::installViaClaudeCli)
    }

    /**
     * The body of [install], with its two side-effecting dependencies passed in.
     *
     * Injected rather than reached for so a test can point the installer at a temporary directory
     * *and* stop it shelling out to a real `claude` binary, which would otherwise rewrite the
     * developer's own MCP configuration while the suite runs.
     */
    internal fun install(
        targets: List<Target>,
        launcher: Launcher,
        cliInstaller: (JsonObject) -> Boolean
    ): Int {
        var installed = 0
        var skipped = 0

        for (target in targets) {
            when {
                target.file.exists() -> if (inject(target, launcher, cliInstaller)) installed++ else skipped++

                // Only create a config for a tool that is actually installed, inferred from the
                // presence of its parent directory. Writing configs for absent tools litters $HOME.
                target.file.parentFile?.exists() == true || target.createParentIfToolPresent() -> {
                    println("Creating ${target.label} config at ${target.file.absolutePath}")
                    if (inject(target, launcher, cliInstaller)) installed++ else skipped++
                }

                else -> skipped++
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
            println("Verify with `handoff --doctor`.")
        }
        return installed
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
     *  2. `java -classpath <installDir>/lib/\*`, derived from the location of our own jar. A wildcard
     *     entry rather than the enumerated jar list, because the enumerated list silently rots the
     *     moment a dependency version changes.
     *  3. This process's own classpath, which is correct by construction whatever the layout.
     *
     * Note what is deliberately *not* preferred: the generated `cli.bat` start script. Launching a
     * stdio server through `cmd.exe` adds a shell whose own output shares the protocol's stdout, and
     * the setup guide has always told users to avoid it. Preferring it here contradicted our own
     * documentation.
     */
    public fun resolveLauncher(): Launcher {
        System.getenv("HANDOFF_LAUNCHER")?.takeIf { it.isNotBlank() }?.let { override ->
            return Launcher(override, listOf("--mcp"))
        }

        val classpath = installLibDir()?.let { lib -> File(lib, "*").path }
            ?: System.getProperty("java.class.path")

        return Launcher(
            javaBinary(),
            listOf("-classpath", classpath, "com.ovi.handoff.MainKt", "--mcp")
        )
    }

    private fun javaBinary(): String {
        val binary = File(System.getProperty("java.home"), if (isWindows()) "bin/java.exe" else "bin/java")
        return if (binary.exists()) binary.absolutePath else "java"
    }

    /** The `lib` directory of an `installDist`-style layout containing our own jar. */
    private fun installLibDir(): File? {
        val location = runCatching {
            McpAutoInstaller::class.java.protectionDomain?.codeSource?.location?.toURI()?.let { File(it) }
        }.getOrNull() ?: return null

        if (!location.isFile || !location.name.endsWith(".jar")) return null
        return location.parentFile?.takeIf { it.name == "lib" && it.isDirectory }
    }

    // -------------------------------------------------------------------------------------
    // Targets
    // -------------------------------------------------------------------------------------

    /**
     * How one editor family spells an MCP server entry.
     *
     * Held as data per target rather than sniffed from the existing file: an empty or absent VS Code
     * config contains no `servers` key to sniff, so sniffing wrote the wrong shape in exactly the
     * case that mattered — the first install.
     */
    internal enum class ConfigFormat(val key: String, val declaresTransportType: Boolean) {
        /** Claude Desktop, Claude Code, Cursor, Windsurf, Antigravity. */
        MCP_SERVERS("mcpServers", false),

        /** VS Code's own `mcp.json`. */
        VSCODE_SERVERS("servers", true)
    }

    internal class Target(
        val label: String,
        val file: File,
        /** Directory whose existence proves the tool is installed, when the config dir is missing. */
        val toolMarker: File?,
        val format: ConfigFormat = ConfigFormat.MCP_SERVERS,
        /** Written by a first-party CLI when one exists, rather than by editing its state file. */
        val preferCli: Boolean = false
    ) {
        fun createParentIfToolPresent(): Boolean {
            val marker = toolMarker ?: return false
            if (!marker.exists()) return false
            return file.parentFile?.mkdirs() == true
        }
    }

    internal fun discoverTargets(): List<Target> {
        val home = File(System.getProperty("user.home"))
        val appData = System.getProperty("handoff.appdata")?.let(::File)
            ?: System.getenv("APPDATA")?.let(::File)
        val targets = mutableListOf<Target>()

        // Claude Desktop
        when {
            isWindows() && appData != null -> targets += Target(
                "Claude Desktop",
                File(appData, "Claude/claude_desktop_config.json"),
                File(appData, "Claude")
            )

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

        // Claude Code keeps MCP servers in its own top-level config, which is also a large live
        // state file it rewrites while running, so its CLI is preferred over editing the file.
        targets += Target("Claude Code", File(home, ".claude.json"), File(home, ".claude"), preferCli = true)

        targets += Target("Cursor", File(home, ".cursor/mcp.json"), File(home, ".cursor"))

        targets += Target(
            "Windsurf",
            File(home, ".codeium/windsurf/mcp_config.json"),
            File(home, ".codeium")
        )

        targets += Target("Antigravity", File(home, ".gemini/config/mcp_config.json"), File(home, ".gemini"))
        targets += Target(
            "Antigravity IDE",
            File(home, ".gemini/antigravity-ide/mcp_config.json"),
            File(home, ".gemini")
        )

        val vsCodeUserDir = when {
            isWindows() && appData != null -> File(appData, "Code/User")
            isMac() -> File(home, "Library/Application Support/Code/User")
            else -> File(home, ".config/Code/User")
        }
        targets += Target("VS Code", File(vsCodeUserDir, "mcp.json"), vsCodeUserDir, ConfigFormat.VSCODE_SERVERS)

        return targets
    }

    // -------------------------------------------------------------------------------------
    // Reporting
    // -------------------------------------------------------------------------------------

    /** What `--doctor` prints: whether each known IDE already runs the command we would write. */
    public data class RegistrationState(
        val label: String,
        val path: String,
        val configExists: Boolean,
        val registered: Boolean,
        val upToDate: Boolean
    )

    public fun registrationReport(): List<RegistrationState> {
        val launcher = resolveLauncher()
        return discoverTargets().map { target ->
            val existing = readServerEntry(target)
            RegistrationState(
                label = target.label,
                path = target.file.absolutePath,
                configExists = target.file.exists(),
                registered = existing != null,
                upToDate = existing != null && existing == entryFor(target, launcher)
            )
        }
    }

    private fun readServerEntry(target: Target): JsonObject? = runCatching {
        if (!target.file.exists()) return null
        val root = reader.parseToJsonElement(target.file.readText()).jsonObject
        // Read both spellings: a file written by an older build, or by hand, may use either.
        (root[target.format.key] ?: root["mcpServers"] ?: root["servers"])
            ?.jsonObject?.get(SERVER_KEY)?.jsonObject
    }.getOrNull()

    // -------------------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------------------

    private fun entryFor(target: Target, launcher: Launcher): JsonObject = buildJsonObject {
        if (target.format.declaresTransportType) put("type", "stdio")
        put("command", launcher.command)
        put("args", buildJsonArray { launcher.args.forEach { add(it) } })
    }

    private fun inject(target: Target, launcher: Launcher, cliInstaller: (JsonObject) -> Boolean): Boolean {
        val entry = entryFor(target, launcher)

        if (target.preferCli && cliInstaller(entry)) {
            println("  ${target.label}: registered 'handoff' via the claude CLI.")
            return true
        }

        return runCatching {
            val existingText = if (target.file.exists()) target.file.readText() else ""
            val root = if (existingText.isBlank()) {
                JsonObject(emptyMap())
            } else {
                reader.parseToJsonElement(existingText).jsonObject
            }

            val serversKey = target.format.key
            val servers = root[serversKey]?.jsonObject?.toMutableMap() ?: mutableMapOf()

            if (servers[SERVER_KEY] == entry) {
                println("  ${target.label}: already up to date.")
                return true
            }

            servers[SERVER_KEY] = entry

            val updated = buildJsonObject {
                root.forEach { (key, value) -> if (key != serversKey) put(key, value) }
                put(serversKey, JsonObject(servers))
            }

            // Back up before replacing: an IDE config often holds settings the user cannot recover.
            if (target.file.exists()) {
                target.file.copyTo(
                    File(target.file.parentFile, "${target.file.name}.handoff-backup"),
                    overwrite = true
                )
            }
            SecureFiles.writeAtomicText(target.file, writer.encodeToString(JsonObject.serializer(), updated))

            println("  ${target.label}: registered 'handoff'.")
            true
        }.getOrElse { cause ->
            println("  ${target.label}: could not update ${target.file.absolutePath} (${cause.message}).")
            false
        }
    }

    /**
     * Registers through `claude mcp add-json` when the CLI is available.
     *
     * `~/.claude.json` is not a config file so much as Claude Code's live state, rewritten from
     * memory while it runs. An atomic replace from here can therefore lose whatever it had buffered,
     * or be lost itself. Letting the tool write its own state removes that race.
     *
     * Runs with stdin closed and a hard timeout, so a CLI that decides to prompt cannot hang
     * `--install` instead.
     */
    private fun installViaClaudeCli(entry: JsonObject): Boolean {
        val binary = findOnPath("claude") ?: return false

        return runCatching {
            val process = ProcessBuilder(
                binary.absolutePath,
                "mcp", "add-json", SERVER_KEY, entry.toString(), "--scope", "user"
            )
                .redirectInput(ProcessBuilder.Redirect.from(nullDevice()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()

            if (!process.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0
        }.getOrDefault(false)
    }

    private fun nullDevice(): File = File(if (isWindows()) "NUL" else "/dev/null")

    private fun findOnPath(name: String): File? {
        val candidates = if (isWindows()) listOf("$name.cmd", "$name.exe", "$name.bat", name) else listOf(name)
        val path = System.getenv("PATH") ?: return null
        for (dir in path.split(File.pathSeparatorChar)) {
            if (dir.isBlank()) continue
            for (candidate in candidates) {
                val file = File(dir, candidate)
                if (file.isFile) return file
            }
        }
        return null
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

            VS Code uses a different shape in its mcp.json:

            {
              "servers": {
                "handoff": {
                  "type": "stdio",
                  "command": "$command",
                  "args": [$args]
                }
              }
            }
        """.trimIndent()
    }

    private const val CLI_TIMEOUT_SECONDS = 20L

    private fun isWindows(): Boolean = System.getProperty("os.name").lowercase().contains("win")

    private fun isMac(): Boolean = System.getProperty("os.name").lowercase().let {
        it.contains("mac") || it.contains("darwin")
    }
}
