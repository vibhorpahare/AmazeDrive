package com.amazedrive.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context

object PairedDeviceProvider {
    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<BluetoothDevice> {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices?.toList() ?: emptyList()
    }
}
