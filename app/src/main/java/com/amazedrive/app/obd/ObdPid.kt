package com.amazedrive.app.obd

enum class ObdPid(val pid: String, val label: String) {
    ENGINE_LOAD("04", "Engine Load"),
    COOLANT_TEMP("05", "Coolant Temp"),
    RPM("0C", "Engine RPM"),
    SPEED("0D", "Vehicle Speed"),
    THROTTLE("11", "Throttle Position");

    val command: String get() = "01$pid"
}
