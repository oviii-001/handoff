package com.ovi.handoff.core

import com.ovi.handoff.shared.model.AgentInfo
import com.ovi.handoff.shared.model.PermissionInfo
import com.ovi.handoff.shared.model.PermissionType
import com.ovi.handoff.shared.model.RiskInfo
import com.ovi.handoff.shared.model.RiskLevel
import com.ovi.handoff.shared.model.isApproval
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.Instant
import kotlin.system.exitProcess

/**
 * `handoff --exec <command>`: asks the phone before running a local command.
 *
 * The headline fix here is that approvals now work at all. This compared the decision against the
 * literal string `"approve"`, while the phone sends `"approve_once"`, so every single approval was
 * executed as a denial. Decision strings are now interpreted through
 * [com.ovi.handoff.shared.model.DecisionType].
 */
public object CommandWrapper {

    public fun execute(commandArgs: List<String>) {
        if (commandArgs.isEmpty()) {
            System.err.println("Error: no command provided to --exec")
            exitProcess(2)
        }

        val invocation = Invocation.from(commandArgs)
        val pairId = DesktopConfigManager.getPairId()
        val cwd = System.getProperty("user.dir")
        val risk = assessRisk(invocation.display)

        println("==================================================")
        println(" HandOff terminal authorization")
        println("==================================================")
        println("Command : ${invocation.display}")
        println("Mode    : ${invocation.modeDescription}")
        println("Folder  : $cwd")
        println("Pair ID : $pairId")
        println("Risk    : ${risk.level.uppercase()}")
        println("Status  : waiting for authorization on your phone...")

        val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
        val keyPair = keyStore.getOrGenerateKeyPair()

        val outcome = RelayClient(
            relayHost = DesktopConfigManager.getRelayHost(),
            pairId = pairId,
            pairSecret = DesktopConfigManager.getPairSecret(),
            keyStoreManager = keyStore,
            privateKey = keyPair.private
        ).use { client ->
            runBlocking {
                client.sendRequestAndWaitForDecision(
                    RequestFactory.build(
                        pairId = pairId,
                        agent = AgentInfo(id = "terminal", name = "Terminal", version = "2.0.0"),
                        permission = PermissionInfo(
                            type = PermissionType.TERMINAL,
                            command = invocation.display,
                            description = "Run a command in $cwd",
                            cwd = cwd
                        ),
                        risk = risk,
                        options = listOf("approve", "deny"),
                        workspacePath = cwd
                    )
                )
            }
        }

        val decision = outcome.decisionOrNull()
        if (decision == null) {
            // Each unhappy path now says which one it was and how to fix it, instead of collapsing
            // "you never paired a phone" and "you ignored the prompt" into one timeout message.
            System.err.println("Not authorized. ${outcome.explain()}")
            exitProcess(1)
        }

        if (!decision.isApproval()) {
            val note = decision.feedback?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
            System.err.println("Denied on your phone at ${Instant.now()}$note. Nothing was run.")
            exitProcess(1)
        }

        println("Approved. Running...")
        println("==================================================")
        exitProcess(runLocally(invocation))
    }

    /**
     * How the arguments after `--exec` should be run.
     *
     * A single argument is a shell command line the user already quoted, so it goes through the
     * shell. Several arguments are an argv vector and are executed directly: re-joining them with
     * spaces and handing that to `bash -c`, as this used to, silently loses the quoting, so
     * `--exec rm "my file.txt"` would delete two different files.
     */
    private class Invocation(
        val argv: List<String>,
        val useShell: Boolean,
        val display: String,
        val modeDescription: String
    ) {
        companion object {
            fun from(args: List<String>): Invocation {
                if (args.size == 1) {
                    return Invocation(
                        argv = args,
                        useShell = true,
                        display = args.single(),
                        modeDescription = "shell command line"
                    )
                }
                return Invocation(
                    argv = args,
                    useShell = false,
                    display = args.joinToString(" ") { if (it.contains(' ')) "\"$it\"" else it },
                    modeDescription = "direct execution, no shell (quoting preserved)"
                )
            }
        }
    }

    private fun runLocally(invocation: Invocation): Int {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val commandLine = when {
            !invocation.useShell -> invocation.argv
            isWindows -> listOf("cmd.exe", "/c", invocation.argv.single())
            else -> listOf(shellPath(), "-c", invocation.argv.single())
        }

        return try {
            ProcessBuilder(commandLine)
                .inheritIO()
                .start()
                .waitFor()
        } catch (cause: Exception) {
            System.err.println("Could not run the command: ${cause.message}")
            126
        }
    }

    private fun shellPath(): String = System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: "/bin/sh"

    /**
     * Classifies a command so the approval card can say *why* it is risky.
     *
     * Matching is token-based rather than substring-based: `"rm "` as a substring both misses
     * `rm-rf`-style aliases and fires on innocent text such as a filename containing "rm ".
     */
    internal fun assessRisk(command: String): RiskInfo {
        val tokens = CommandTokenizer.tokenize(command)
        val executables = CommandTokenizer.executables(tokens).map { it.substringAfterLast('/').lowercase() }
        val lowered = command.lowercase()
        val reasons = mutableListOf<String>()
        var level = RiskLevel.MEDIUM

        val recursiveForce = tokens.any { it == "-rf" || it == "-fr" || (it.startsWith("-") && it.contains('r') && it.contains('f')) }
        if ("rm" in executables && recursiveForce) {
            reasons += "Recursive forced delete"
            level = RiskLevel.CRITICAL
        }
        if (executables.any { exe -> DESTRUCTIVE_EXECUTABLES.any { exe == it || exe.startsWith("$it.") } }) {
            reasons += "Runs a command that can destroy a filesystem or device"
            level = RiskLevel.CRITICAL
        }
        if (DESTRUCTIVE_PHRASES.any { lowered.contains(it) }) {
            reasons += "Contains a destructive database or system operation"
            level = RiskLevel.CRITICAL
        }
        if (tokens.any { it == "|" || it == ";" || it == "&" }) {
            reasons += "Chains multiple commands, so approving it authorizes all of them"
            if (level != RiskLevel.CRITICAL) level = RiskLevel.HIGH
        }
        if (executables.any { it in ELEVATED_EXECUTABLES }) {
            reasons += "Changes permissions, ownership, or runs with elevated privileges"
            if (level != RiskLevel.CRITICAL) level = RiskLevel.HIGH
        }
        if ("rm" in executables || "del" in executables) {
            reasons += "Deletes files"
            if (level == RiskLevel.MEDIUM) level = RiskLevel.HIGH
        }

        if (reasons.isEmpty()) {
            reasons += "Executed from your terminal through HandOff"
        }
        return RiskInfo(level = level, reasons = reasons)
    }

    private val DESTRUCTIVE_EXECUTABLES = setOf("mkfs", "fdisk", "dd", "shutdown", "reboot", "diskpart", "format")
    private val ELEVATED_EXECUTABLES = setOf("sudo", "doas", "su", "chmod", "chown", "icacls", "takeown")
    private val DESTRUCTIVE_PHRASES = listOf("drop table", "drop database", "truncate table", "--no-preserve-root")
}
