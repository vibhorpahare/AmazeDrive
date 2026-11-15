package com.amazedrive.app.obd

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    object Initializing : ConnectionState()
    object Connected : ConnectionState()
    object Reading : ConnectionState()
    data class CommunicationError(val message: String) : ConnectionState()
}
