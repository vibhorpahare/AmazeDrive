package com.amazedrive.app.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amazedrive.app.bluetooth.PairedDeviceProvider
import com.amazedrive.app.databinding.ActivityDiagnosticsBinding
import com.amazedrive.app.obd.ConnectionState
import com.amazedrive.app.obd.ObdRepository
import kotlinx.coroutines.launch

/** Temporary Phase-2 screen: pick a paired adapter, connect, watch raw ELM327 traffic. */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagnosticsBinding
    private var pairedDevices: List<BluetoothDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagnosticsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshDevices()

        binding.refreshDevicesButton.setOnClickListener { refreshDevices() }
        binding.connectButton.setOnClickListener { connectToSelected() }
        binding.disconnectButton.setOnClickListener { ObdRepository.disconnect() }

        lifecycleScope.launch {
            ObdRepository.connectionState.collect { state ->
                binding.diagStatusText.text = describeState(state)
            }
        }
        lifecycleScope.launch {
            ObdRepository.rawLog.collect { lines ->
                binding.rawLogText.text = lines.joinToString("\n")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun refreshDevices() {
        pairedDevices = PairedDeviceProvider.pairedDevices(this)
        val names = if (pairedDevices.isEmpty()) {
            listOf("No paired devices found")
        } else {
            pairedDevices.map { "${it.name ?: "Unknown"} (${it.address})" }
        }
        binding.deviceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
    }

    private fun connectToSelected() {
        val device = pairedDevices.getOrNull(binding.deviceSpinner.selectedItemPosition) ?: return
        ObdRepository.connect(device)
    }

    private fun describeState(state: ConnectionState): String = when (state) {
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Initializing -> "Initializing ELM327…"
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Reading -> "Reading data…"
        is ConnectionState.CommunicationError -> "Error: ${state.message}"
    }
}
