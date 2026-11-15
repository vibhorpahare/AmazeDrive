package com.amazedrive.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.amazedrive.app.bluetooth.BluetoothPermissions
import com.amazedrive.app.databinding.ActivityMainBinding
import com.amazedrive.app.obd.ConnectionState
import com.amazedrive.app.obd.ObdRepository
import com.amazedrive.app.obd.VehicleData
import com.amazedrive.app.ui.DiagnosticsActivity
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val decimalFormat = DecimalFormat("#")

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!BluetoothPermissions.hasAll(this)) {
            requestPermissionsLauncher.launch(BluetoothPermissions.required())
        }

        binding.diagButton.setOnClickListener {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        }

        lifecycleScope.launch {
            ObdRepository.connectionState.collect { state -> renderConnectionState(state) }
        }
        lifecycleScope.launch {
            ObdRepository.vehicleData.collect { data -> renderVehicleData(data) }
        }
    }

    private fun renderConnectionState(state: ConnectionState) {
        val (text, colorRes) = when (state) {
            is ConnectionState.Disconnected -> getString(R.string.status_disconnected) to R.color.status_error
            is ConnectionState.Connecting -> getString(R.string.status_connecting) to R.color.status_warn
            is ConnectionState.Initializing -> "Initializing…" to R.color.status_warn
            is ConnectionState.Connected -> getString(R.string.status_connected) to R.color.status_ok
            is ConnectionState.Reading -> "Reading" to R.color.status_ok
            is ConnectionState.CommunicationError -> "Error" to R.color.status_error
        }
        binding.connectionStatusText.text = text
        binding.connectionStatusText.setTextColor(getColor(colorRes))
        binding.errorText.text = (state as? ConnectionState.CommunicationError)?.message.orEmpty()
    }

    private fun renderVehicleData(data: VehicleData) {
        binding.speedValue.text = data.speedKmh?.let { decimalFormat.format(it) } ?: "--"
        binding.rpmValue.text = data.rpm?.let { decimalFormat.format(it) } ?: "--"
        binding.coolantValue.text = data.coolantTempC?.let { decimalFormat.format(it) } ?: "--"
        binding.throttleValue.text = data.throttlePercent?.let { decimalFormat.format(it) } ?: "--"
        binding.engineLoadValue.text = data.engineLoadPercent?.let { decimalFormat.format(it) } ?: "--"
        binding.lastUpdateText.text = data.lastUpdateMillis?.let {
            "Updated ${(System.currentTimeMillis() - it) / 1000}s ago"
        } ?: ""
    }
}
