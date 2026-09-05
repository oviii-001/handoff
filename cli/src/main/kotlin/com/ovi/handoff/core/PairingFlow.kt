package com.ovi.handoff.core

import com.ovi.handoff.shared.protocol.PairHello
import com.ovi.handoff.shared.protocol.Protocol
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * `handoff --pair`: shows the pairing code and stays until the phone has actually paired.
 *
 * The previous version printed a QR code and exited immediately, which quietly broke pairing for
 * anyone whose IDE was not already running. The relay assigns a pair room to the first *desktop*
 * socket that presents the secret; until that happens the room is unclaimed and a phone scanning the
 * code is refused with `401 Pair not yet claimed by a desktop`. So the common case — open a
 * terminal, run `--pair`, scan — could not work, and neither side said why: the terminal had already
 * exited and the phone showed a generic failure.
 *
 * Holding the socket for the duration of pairing fixes that by construction, and turns pairing into
 * something with visible progress and a definite end.
 */
public object PairingFlow {

    private const val DIVIDER = "=================================================="

    /** How long to wait for the socket before reporting that the relay is not cooperating. */
    private const val CONNECT_TIMEOUT_MS = 12_000L

    /** How long to hold the code open for the user to scan. */
    private const val PAIR_TIMEOUT_MS = 300_000L

    public fun run(): Unit = runBlocking {
        val config = DesktopConfigManager.loadConfig()
        val keyStore = KeyStoreManager(File(System.getProperty("user.home"), ".handoff/keys"))
        val keyPair = keyStore.getOrGenerateKeyPair()
        val publicKey = KeyStoreManager.encodePublicKey(keyPair.public)

        val paired = CompletableDeferred<PairHello>()

        val client = RelayClient(
            relayHost = config.relayHost,
            pairId = config.pairId,
            pairSecret = config.pairSecret,
            keyStoreManager = keyStore,
            privateKey = keyPair.private,
            onPairHello = { hello -> paired.complete(hello) }
        )

        client.use {
            // Opened before the code is shown so the room is already claimed by the time anyone can
            // scan it. This single line is the fix for "I scanned it and nothing happened".
            client.start()

            printCode(config, publicKey)

            if (!awaitConnection(client)) {
                reportConnectionFailure(config, client)
                return@runBlocking
            }

            println("Relay      : connected")
            println()
            println("Waiting for your phone to scan the code...   (Ctrl-C to stop)")

            val hello = withTimeoutOrNull(PAIR_TIMEOUT_MS) { paired.await() }
            println()
            if (hello == null) {
                println("No phone paired within ${PAIR_TIMEOUT_MS / 60_000} minutes.")
                println("The code above is still valid. Run `handoff --pair` again when you are ready.")
                println(DIVIDER)
                return@runBlocking
            }

            println("Paired with ${hello.deviceId} (${hello.algorithm} signing key).")
            println()
            println("Your phone can now approve actions from your AI agent.")
            println("Next: run `handoff --install` to register HandOff with your IDE, then restart it.")
            println(DIVIDER)
        }
    }

    // -------------------------------------------------------------------------------------

    private fun printCode(config: DesktopConfig, publicKey: String) {
        // The link carries the relay token as well as the key. Without the token the phone cannot
        // authenticate to the relay, which is what stops a guessed pair id from being enough to
        // approve commands.
        val pairUrl = buildString {
            append("handoff://pair")
            append("?v=").append(Protocol.VERSION)
            append("&pairId=").append(config.pairId)
            append("&host=").append(config.relayHost)
            append("&pubKey=").append(publicKey)
            append("&token=").append(config.pairSecret)
        }

        val pin = (100_000..999_999).random().toString()
        val formattedPin = "${pin.take(3)} ${pin.takeLast(3)}"

        // Register PIN with the relay so mobile users can type 6 digits instead of 200+ char URL
        RelayEndpoint.registerPin(config.relayHost, pin, pairUrl)

        println(DIVIDER)
        println(" HandOff Pairing")
        println(DIVIDER)
        println("Pairing Code: $formattedPin  (6-digit instant PIN)")
        println("Pair ID     : ${config.pairId}")
        println("Relay       : ${config.relayHost}")
        println("Protocol    : ${Protocol.VERSION}")
        println()
        println("Scan this QR code with the HandOff mobile app:")
        TerminalQrGenerator.printQrCode(pairUrl)
        println("Or enter the 6-digit code in the app's manual tab:")
        println("  ->  $formattedPin  <-")
        println()
        println("Direct Pairing Link (auto-detected if on clipboard):")
        println("  $pairUrl")
        println()

        // Check for connected Android device via ADB for zero-touch auto-pair
        tryAdbAutoPair(pairUrl)

        println("Treat that link and code like a password: it authorizes a device to approve your agent's actions.")
        println("Run `handoff --rotate-pair` to invalidate it.")
        println()
    }

    private fun tryAdbAutoPair(pairUrl: String) {
        runCatching {
            val localAppData = System.getenv("LOCALAPPDATA") ?: ""
            val androidHome = System.getenv("ANDROID_HOME") ?: ""
            val adbCandidates = listOf(
                "adb",
                if (localAppData.isNotBlank()) File(localAppData, "Android/Sdk/platform-tools/adb.exe").takeIf { it.exists() }?.absolutePath else null,
                if (androidHome.isNotBlank()) File(androidHome, "platform-tools/adb").takeIf { it.exists() }?.absolutePath else null,
                if (androidHome.isNotBlank()) File(androidHome, "platform-tools/adb.exe").takeIf { it.exists() }?.absolutePath else null
            ).filterNotNull()

            for (adb in adbCandidates) {
                val proc = ProcessBuilder(adb, "devices").redirectErrorStream(true).start()
                val out = proc.inputStream.bufferedReader().readText()
                proc.waitFor()
                val deviceLines = out.lines().filter { it.contains("\tdevice") }
                if (deviceLines.isNotEmpty()) {
                    val deviceSerial = deviceLines.first().substringBefore("\t").trim()
                    println("--------------------------------------------------")
                    println("📱 Detected connected Android device ($deviceSerial via ADB)")
                    println("   Auto-dispatching pairing intent directly to device...")
                    val amProc = ProcessBuilder(
                        adb, "-s", deviceSerial, "shell", "am", "start", "-a", "android.intent.action.VIEW", "-d", pairUrl
                    ).redirectErrorStream(true).start()
                    amProc.waitFor()
                    println("   Zero-touch pairing dispatched! Check your phone.")
                    println("--------------------------------------------------")
                    println()
                    return
                }
            }
        }
    }

    private suspend fun awaitConnection(client: RelayClient): Boolean {
        val deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (client.isConnected) return true
            delay(250)
        }
        return false
    }

    /**
     * Explains a failed claim in terms of what to do about it.
     *
     * A rejected WebSocket upgrade surfaces as an opaque client exception, so the reason is fetched
     * over plain HTTP instead. The distinction matters: an unreachable relay and a room already
     * claimed by another machine look identical from the socket but need completely different fixes.
     */
    private fun reportConnectionFailure(config: DesktopConfig, client: RelayClient) {
        println("Relay      : COULD NOT CONNECT")
        println()

        when (val health = RelayEndpoint.health(config.relayHost)) {
            is RelayEndpoint.Health.Down -> {
                println("The relay at ${config.relayHost} is not reachable (${health.reason}).")
                println("Check your network. If you self-host the relay, confirm HANDOFF_RELAY_HOST is correct.")
            }

            is RelayEndpoint.Health.Unexpected -> {
                println("The relay answered HTTP ${health.statusCode} instead of a health check.")
                println("If you self-host, confirm the worker deployed successfully.")
            }

            is RelayEndpoint.Health.Up -> {
                when (val status = RelayEndpoint.pairStatus(config.relayHost, config.pairId, config.pairSecret)) {
                    is RelayEndpoint.PairStatus.TokenRejected -> {
                        println("The relay refused this desktop's pairing token: ${status.message}")
                        println()
                        println("Pair id ${config.pairId} is claimed by a different machine or an older secret.")
                        println("Run `handoff --rotate-pair` to take a fresh pair id, then pair again.")
                    }

                    else -> {
                        println("The relay is up but the WebSocket did not connect.")
                        client.lastConnectionError?.let { println("Last socket error: $it") }
                        println("A proxy or firewall that blocks WebSocket upgrades is the usual cause.")
                    }
                }
            }
        }
        println(DIVIDER)
    }
}
