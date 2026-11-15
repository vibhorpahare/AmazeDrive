package com.amazedrive.app.bluetooth

/**
 * Abstraction over the physical link to the OBD adapter.
 * ClassicBluetoothTransport implements this for SPP adapters; a BLE/GATT
 * implementation can be added later without touching ELM327/OBD layers.
 */
interface BluetoothTransport {
    val isConnected: Boolean
    suspend fun connect()
    fun disconnect()
    suspend fun write(data: ByteArray)
    suspend fun readUntil(terminator: Char, timeoutMs: Long): String
}
