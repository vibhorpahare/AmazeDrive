package com.amazedrive.app.obd

/** Null means unavailable/unsupported by this ECU — never guessed or defaulted. */
data class VehicleData(
    val rpm: Double? = null,
    val speedKmh: Double? = null,
    val coolantTempC: Double? = null,
    val throttlePercent: Double? = null,
    val engineLoadPercent: Double? = null,
    val lastUpdateMillis: Long? = null
)
