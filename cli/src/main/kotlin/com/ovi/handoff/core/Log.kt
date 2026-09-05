package com.ovi.handoff.core

import java.io.File
import java.time.Instant

/**
 * Diagnostic log for the daemon.
 *
 * Everything used to go to `System.err` only. That is technically visible, but each IDE buries MCP
 * server stderr somewhere different and several truncate it, so in practice a user reporting
 * "it just hangs" had no artefact to send and no way to find one. Every line is now mirrored to
 * `~/.handoff/logs/handoff.log`, which is a single documented path.
 *
 * The file is size-capped and keeps exactly one previous generation. An MCP server can run for days
 * inside an IDE, so an uncapped log is a slow disk leak.
 */
public object Log {

    private const val MAX_BYTES = 1_000_000L

    private val logDir = File(System.getProperty("user.home"), ".handoff/logs")
    private val logFile = File(logDir, "handoff.log")
    private val previousFile = File(logDir, "handoff.log.1")

    private val lock = Any()

    /**
     * Whether the log file is written at all.
     *
     * Left off for short-lived foreground commands: `--status` and `--pair` print to the terminal
     * the user is already looking at, and creating a log directory for them would be surprising.
     */
    @Volatile
    private var fileEnabled: Boolean = false

    /** Where the file lives, for `--doctor` and for support instructions. */
    public fun logFilePath(): String = logFile.absolutePath

    public fun enableFileLogging() {
        fileEnabled = true
        info("--- session started (pid ${ProcessHandle.current().pid()}) ---")
    }

    public fun info(message: String) {
        write("INFO", message)
    }

    public fun warn(message: String) {
        write("WARN", message)
    }

    public fun error(message: String, cause: Throwable? = null) {
        write("ERROR", if (cause == null) message else "$message: ${cause.message}")
    }

    private fun write(level: String, message: String) {
        val line = "[Handoff] $message"
        // stderr keeps the shape the IDE panes already show; the file gets the timestamp and level
        // that make an after-the-fact report readable.
        System.err.println(line)
        if (!fileEnabled) return

        synchronized(lock) {
            runCatching {
                logDir.mkdirs()
                rotateIfNeeded()
                logFile.appendText("${Instant.now()} $level $message${System.lineSeparator()}")
            }.onFailure {
                // A log that cannot be written must never take the daemon down with it, and must
                // not recurse back into this function.
                fileEnabled = false
                System.err.println("[Handoff] Disabling file logging: ${it.message}")
            }
        }
    }

    private fun rotateIfNeeded() {
        if (!logFile.exists() || logFile.length() < MAX_BYTES) return
        previousFile.delete()
        if (!logFile.renameTo(previousFile)) {
            logFile.delete()
        }
    }
}
