package com.amazedrive.app.elm327

import com.amazedrive.app.bluetooth.BluetoothTransport
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Serializes commands onto the transport so responses never overlap.
 * One command is ever in flight at a time.
 */
class Elm327Client(private val transport: BluetoothTransport) {

    private val mutex = Mutex()

    /** Sends one AT/OBD command and waits for the '>' prompt that ends every ELM327 response. */
    suspend fun sendCommand(command: String, timeoutMs: Long = 5000): String = mutex.withLock {
        transport.write("$command\r".toByteArray(Charsets.US_ASCII))
        val raw = transport.readUntil('>', timeoutMs)
        clean(raw)
    }

    private fun clean(raw: String): String = raw
        .replace("\r", "\n")
        .split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .trim()

    /**
     * Runs the standard init sequence. Adapters don't all support the same AT commands,
     * so a rejected ("?") response is logged and init continues rather than aborting.
     */
    suspend fun initialize(onLog: (String) -> Unit = {}): Boolean {
        val initCommands = listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")
        for (cmd in initCommands) {
            val response = try {
                sendCommand(cmd, timeoutMs = if (cmd == "ATZ") 8000 else 3000)
            } catch (e: IOException) {
                onLog("$cmd -> ERROR: ${e.message}")
                return false
            }
            onLog("$cmd -> $response")
        }
        return true
    }
}
