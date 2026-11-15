package com.amazedrive.app.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.amazedrive.app.bluetooth.BluetoothTransport
import com.amazedrive.app.bluetooth.ClassicBluetoothTransport
import com.amazedrive.app.elm327.Elm327Client
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Owns the single OBD session: one transport, one ELM327 client, one poll loop.
 * Sequential PID reads only — never poll faster than this loop allows.
 */
object ObdRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sessionJob: Job? = null
    private var transport: BluetoothTransport? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _vehicleData = MutableStateFlow(VehicleData())
    val vehicleData: StateFlow<VehicleData> = _vehicleData.asStateFlow()

    private val _supportedPids = MutableStateFlow<Set<String>>(emptySet())
    val supportedPids: StateFlow<Set<String>> = _supportedPids.asStateFlow()

    private val _rawLog = MutableStateFlow<List<String>>(emptyList())
    val rawLog: StateFlow<List<String>> = _rawLog.asStateFlow()

    private val pollablePids = listOf(
        ObdPid.RPM, ObdPid.SPEED, ObdPid.COOLANT_TEMP, ObdPid.THROTTLE, ObdPid.ENGINE_LOAD
    )

    private fun log(line: String) {
        _rawLog.value = (_rawLog.value + line).takeLast(200)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        disconnect()
        sessionJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting
            val t = ClassicBluetoothTransport(device)
            transport = t
            try {
                t.connect()
            } catch (e: IOException) {
                log("connect -> ERROR: ${e.message}")
                _connectionState.value = ConnectionState.CommunicationError(e.message ?: "connect failed")
                return@launch
            }

            _connectionState.value = ConnectionState.Initializing
            val client = Elm327Client(t)
            val initOk = client.initialize { line -> log(line) }
            if (!initOk) {
                _connectionState.value = ConnectionState.CommunicationError("ELM327 init failed")
                return@launch
            }

            val supportedRaw = try {
                client.sendCommand("0100")
            } catch (e: IOException) {
                log("0100 -> ERROR: ${e.message}")
                _connectionState.value = ConnectionState.CommunicationError(e.message ?: "PID discovery failed")
                return@launch
            }
            log("0100 -> $supportedRaw")
            _supportedPids.value = ObdResponseParser.parseSupportedPids(supportedRaw)

            _connectionState.value = ConnectionState.Connected
            pollLoop(client)
        }
    }

    private suspend fun pollLoop(client: Elm327Client) {
        val job = sessionJob
        while (scope.isActive && job?.isActive == true) {
            _connectionState.value = ConnectionState.Reading
            var rpm: Double? = null
            var speed: Double? = null
            var coolant: Double? = null
            var throttle: Double? = null
            var load: Double? = null

            for (pid in pollablePids) {
                if (pid.pid.uppercase() !in _supportedPids.value) continue
                val response = try {
                    client.sendCommand(pid.command)
                } catch (e: IOException) {
                    log("${pid.command} -> ERROR: ${e.message}")
                    _connectionState.value = ConnectionState.CommunicationError(e.message ?: "read failed")
                    return
                }
                log("${pid.command} -> $response")
                val value = ObdResponseParser.parsePidValue(pid, response)
                when (pid) {
                    ObdPid.RPM -> rpm = value
                    ObdPid.SPEED -> speed = value
                    ObdPid.COOLANT_TEMP -> coolant = value
                    ObdPid.THROTTLE -> throttle = value
                    ObdPid.ENGINE_LOAD -> load = value
                }
            }

            _vehicleData.value = VehicleData(
                rpm = rpm,
                speedKmh = speed,
                coolantTempC = coolant,
                throttlePercent = throttle,
                engineLoadPercent = load,
                lastUpdateMillis = System.currentTimeMillis()
            )

            delay(1000)
        }
    }

    fun disconnect() {
        sessionJob?.cancel()
        sessionJob = null
        transport?.disconnect()
        transport = null
        _connectionState.value = ConnectionState.Disconnected
    }
}
