package com.amazedrive.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val CONNECT_TIMEOUT_MS = 6000L
// SDP previously reported this adapter's SPP service on channel 2 (scn=2); connecting directly
// on that channel skips the SDP service-search/DLCI-allocation sequence that hangs on this stack.
private const val FALLBACK_RFCOMM_CHANNEL = 2

/** Bluetooth Classic (SPP) transport. Only valid for ELM327 adapters that expose a serial port profile. */
class ClassicBluetoothTransport(private val device: BluetoothDevice) : BluetoothTransport {

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    override val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    override suspend fun connect() = withContext(Dispatchers.IO) {
        // SDP-based createRfcommSocketToServiceRecord() is known to hang indefinitely against
        // cheap ELM327 clones (confirmed on this adapter: the native stack gets stuck allocating
        // the RFCOMM port after SDP resolves the channel, and never returns or throws). Go
        // straight to the conventional fixed RFCOMM channel used by these clones, bypassing SDP.
        val socket = createFixedChannelSocket(device, FALLBACK_RFCOMM_CHANNEL)
        if (!tryConnect(socket)) {
            throw IOException("Could not connect on RFCOMM channel $FALLBACK_RFCOMM_CHANNEL")
        }
        bind(socket)
    }

    /** Races socket.connect() against a timeout; on timeout, closes the socket to unblock the connect thread. */
    private suspend fun tryConnect(s: BluetoothSocket): Boolean = coroutineScope {
        val connectJob = async(Dispatchers.IO) {
            try {
                s.connect()
                true
            } catch (e: IOException) {
                false
            }
        }
        val result = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { connectJob.await() }
        if (result == null) {
            try { s.close() } catch (_: IOException) {}
            connectJob.cancel()
            false
        } else {
            result
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun createFixedChannelSocket(device: BluetoothDevice, channel: Int): BluetoothSocket {
        val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
        return method.invoke(device, channel) as BluetoothSocket
    }

    private fun bind(s: BluetoothSocket) {
        socket = s
        input = s.inputStream
        output = s.outputStream
    }

    override fun disconnect() {
        try { input?.close() } catch (_: IOException) {}
        try { output?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        input = null
        output = null
    }

    override suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        val out = output ?: throw IOException("Not connected")
        out.write(data)
        out.flush()
    }

    override suspend fun readUntil(terminator: Char, timeoutMs: Long): String = withTimeout(timeoutMs) {
        withContext(Dispatchers.IO) {
            val inStream = input ?: throw IOException("Not connected")
            val buffer = StringBuilder()
            while (true) {
                val b = inStream.read()
                if (b == -1) throw IOException("Stream closed")
                val c = b.toChar()
                if (c == terminator) break
                buffer.append(c)
            }
            buffer.toString()
        }
    }
}
