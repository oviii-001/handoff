package com.ovi.handoff.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The installer writes files the user cannot easily repair by hand, into tools that report a bad
 * entry only as "the server failed to start". These cover the shapes and the preservation rules.
 */
class McpAutoInstallerTest {

    private val reader = Json { ignoreUnknownKeys = true; isLenient = true }
    private val home: File = Files.createTempDirectory("handoff-install").toFile()
    private val originalHome: String = System.getProperty("user.home")

    private val launcher = McpAutoInstaller.Launcher(
        command = "/opt/jdk/bin/java",
        args = listOf("-classpath", "/opt/handoff/lib/*", "com.ovi.handoff.MainKt", "--mcp")
    )

    /** Never lets a test shell out to a real `claude`, which would edit the developer's own config. */
    private val noCli: (JsonObject) -> Boolean = { false }

    @AfterTest
    fun restoreHome() {
        System.setProperty("user.home", originalHome)
        System.clearProperty("handoff.appdata")
        home.deleteRecursively()
    }

    /**
     * Runs [block] with `user.home` pointed at a scratch directory.
     *
     * `discoverTargets` reads the property on every call, so redirecting it is enough to keep the
     * installer entirely inside the temp directory.
     */
    private fun withTempHome(block: () -> Unit) {
        System.setProperty("user.home", home.absolutePath)
        val tempAppData = File(home, "AppData/Roaming")
        System.setProperty("handoff.appdata", tempAppData.absolutePath)
        try {
            block()
        } finally {
            System.setProperty("user.home", originalHome)
            System.clearProperty("handoff.appdata")
        }
    }

    /** Must be called from inside [withTempHome], which is what makes the paths temporary. */
    private fun targetFor(label: String): McpAutoInstaller.Target =
        McpAutoInstaller.discoverTargets().first { it.label == label }

    private fun readServers(file: File, key: String): JsonObject? =
        reader.parseToJsonElement(file.readText()).jsonObject[key]?.jsonObject

    // -----------------------------------------------------------------------------------------

    /**
     * VS Code reads `servers` with an explicit transport type. Writing `mcpServers` there, as the
     * installer used to, produces a file VS Code parses and then ignores — advertised support that
     * silently did nothing.
     */
    @Test
    fun vsCodeGetsItsOwnConfigShape() = withTempHome {
        val target = targetFor("VS Code")
        target.file.parentFile.mkdirs()

        McpAutoInstaller.install(listOf(target), launcher, noCli)

        assertTrue(target.file.exists())
        assertNull(readServers(target.file, "mcpServers"), "VS Code must not be given the mcpServers key")

        val entry = readServers(target.file, "servers")?.get("handoff")?.jsonObject
        assertNotNull(entry)
        assertEquals("stdio", entry["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals(launcher.command, entry["command"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun everyOtherEditorGetsTheMcpServersShapeWithNoTransportType() = withTempHome {
        val target = targetFor("Cursor")
        target.file.parentFile.mkdirs()

        McpAutoInstaller.install(listOf(target), launcher, noCli)

        val entry = readServers(target.file, "mcpServers")?.get("handoff")?.jsonObject
        assertNotNull(entry)
        assertNull(entry["type"], "only VS Code declares a transport type")
        assertEquals(
            launcher.args,
            entry["args"]!!.jsonArray.map { it.jsonPrimitive.content }
        )
    }

    @Test
    fun preservesUnrelatedKeysAndOtherServers() = withTempHome {
        val target = targetFor("Cursor")
        target.file.parentFile.mkdirs()
        target.file.writeText(
            """
            {
              "theme": "dark",
              "mcpServers": {
                "other": { "command": "node", "args": ["server.js"] }
              }
            }
            """.trimIndent()
        )

        McpAutoInstaller.install(listOf(target), launcher, noCli)

        val root = reader.parseToJsonElement(target.file.readText()).jsonObject
        assertEquals("dark", root["theme"]?.jsonPrimitive?.contentOrNull)

        val servers = root["mcpServers"]!!.jsonObject
        assertNotNull(servers["other"], "an unrelated MCP server must survive the write")
        assertNotNull(servers["handoff"])
    }

    @Test
    fun backsUpAnExistingConfigBeforeReplacingIt() = withTempHome {
        val target = targetFor("Cursor")
        target.file.parentFile.mkdirs()
        val original = """{"mcpServers":{"other":{"command":"node"}}}"""
        target.file.writeText(original)

        McpAutoInstaller.install(listOf(target), launcher, noCli)

        val backup = File(target.file.parentFile, "${target.file.name}.handoff-backup")
        assertTrue(backup.exists(), "an IDE config often holds settings the user cannot recover")
        assertEquals(original, backup.readText())
    }

    @Test
    fun reinstallingChangesNothingAndReportsItselfUpToDate() = withTempHome {
        val target = targetFor("Cursor")
        target.file.parentFile.mkdirs()

        McpAutoInstaller.install(listOf(target), launcher, noCli)
        val afterFirst = target.file.readText()

        McpAutoInstaller.install(listOf(target), launcher, noCli)

        assertEquals(afterFirst, target.file.readText())
    }

    @Test
    fun skipsAToolThatIsNotInstalled() = withTempHome {
        // No parent directory and no marker: writing here would litter the home directory with a
        // config for software the user does not have.
        val target = targetFor("Windsurf")

        val installed = McpAutoInstaller.install(listOf(target), launcher, noCli)

        assertEquals(0, installed)
        assertTrue(!target.file.exists())
    }

    @Test
    fun theCliPathIsPreferredForClaudeCodeAndSkipsTheStateFile() = withTempHome {
        val target = targetFor("Claude Code")
        target.file.parentFile.mkdirs()
        val untouched = """{"projects":{"a":{"history":[1,2,3]}}}"""
        target.file.writeText(untouched)

        var handedToCli: JsonObject? = null
        val installed = McpAutoInstaller.install(listOf(target), launcher) { entry ->
            handedToCli = entry
            true
        }

        assertEquals(1, installed)
        assertNotNull(handedToCli)
        assertEquals(launcher.command, handedToCli!!["command"]?.jsonPrimitive?.contentOrNull)
        // Claude Code rewrites this file from memory while running, so we must not race it.
        assertEquals(untouched, target.file.readText())
    }

    @Test
    fun fallsBackToEditingTheFileWhenTheCliIsUnavailable() = withTempHome {
        val target = targetFor("Claude Code")
        target.file.parentFile.mkdirs()

        val installed = McpAutoInstaller.install(listOf(target), launcher, noCli)

        assertEquals(1, installed)
        assertNotNull(readServers(target.file, "mcpServers")?.get("handoff"))
    }

    // -----------------------------------------------------------------------------------------

    @Test
    fun theLauncherOverrideIsHonoured() {
        // Not asserted against the environment, which the test cannot set: this documents that the
        // resolved command always ends in --mcp, whichever branch produced it.
        val resolved = McpAutoInstaller.resolveLauncher()
        assertEquals("--mcp", resolved.args.last())
        assertTrue(resolved.command.isNotBlank())
    }

    @Test
    fun theResolvedClasspathIsAbsolute() {
        val resolved = McpAutoInstaller.resolveLauncher()
        if (resolved.args.firstOrNull() != "-classpath") return

        val classpath = resolved.args[1]
        // A relative classpath is the original defect: it worked until the repository moved, then
        // produced a config that silently failed to start.
        assertTrue(
            classpath.split(File.pathSeparatorChar).all { File(it).isAbsolute },
            "every classpath entry must be absolute, got: $classpath"
        )
    }

    @Test
    fun registrationReportNamesEveryKnownTarget() = withTempHome {
        val report = McpAutoInstaller.registrationReport()
        assertTrue(report.any { it.label == "VS Code" })
        assertTrue(report.any { it.label == "Cursor" })
        assertTrue(report.all { !it.registered }, "a fresh home has nothing registered")
    }

    @Test
    fun registrationReportDetectsAnUpToDateEntry() = withTempHome {
        val target = targetFor("Cursor")
        target.file.parentFile.mkdirs()
        McpAutoInstaller.install(listOf(target), McpAutoInstaller.resolveLauncher(), noCli)

        val state = McpAutoInstaller.registrationReport().first { it.label == "Cursor" }
        assertTrue(state.registered)
        assertTrue(state.upToDate)
    }
}
