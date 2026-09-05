package com.ovi.handoff.adapter

import java.io.OutputStream
import java.io.PrintStream

/**
 * Protects the JSON-RPC stream from anything else that writes to stdout.
 *
 * On a stdio transport, stdout *is* the protocol. A single stray line — a `println` left in our own
 * code, a warning from a dependency, a JVM message such as an illegal-access or GC notice — lands
 * in the middle of the frame stream, and the client's parser cannot recover: every subsequent
 * response is misaligned, so the server appears to hang forever rather than fail. Auditing our own
 * code for `println` does not close this, because most of the risk comes from code we do not own.
 *
 * [claimStdout] takes the real stdout away and points `System.out` at stderr, so any such write is
 * merely noise in the IDE's log pane instead of stream corruption. Only the returned stream, held
 * by [JsonRpcTransport], can still reach the client.
 */
internal object StdioGuard {

    @Volatile
    private var claimed: PrintStream? = null

    /**
     * Returns the real stdout and redirects `System.out` to stderr.
     *
     * Idempotent: a second call returns the same stream rather than capturing the already-redirected
     * one, which would silently route every response into stderr and make the server mute.
     */
    fun claimStdout(): OutputStream {
        claimed?.let { return it }
        synchronized(this) {
            claimed?.let { return it }

            val real = System.out
            System.setOut(System.err)
            claimed = real
            return real
        }
    }
}
