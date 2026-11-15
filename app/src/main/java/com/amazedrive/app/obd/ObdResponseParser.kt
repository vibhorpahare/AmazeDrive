package com.amazedrive.app.obd

object ObdResponseParser {

    /**
     * Extracts hex data bytes from a cleaned ELM327 response. Handles both spaced
     * ("41 0C 1A F8") and unspaced ("410C1AF8") forms — ATS0 (spaces off) is part of our
     * init sequence, so adapters normally reply unspaced. Non-hex tokens (e.g. "SEARCHING...")
     * are dropped whole rather than letting their hex-looking letters (A/B/C/D/E/F) corrupt the byte stream.
     */
    fun extractBytes(raw: String): List<Int>? {
        val hexOnly = raw.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it.all { c -> c.isDigit() || c in 'A'..'F' || c in 'a'..'f' } }
            .joinToString("")
        val evenLength = hexOnly.length - (hexOnly.length % 2)
        if (evenLength < 2) return null
        return (0 until evenLength step 2).map { hexOnly.substring(it, it + 2).toInt(16) }
    }

    fun isNoData(raw: String): Boolean =
        raw.contains("NO DATA", ignoreCase = true) || raw.contains("UNABLE TO CONNECT", ignoreCase = true)

    fun isError(raw: String): Boolean =
        raw.contains("ERROR", ignoreCase = true) || raw.contains("?") || raw.contains("BUS INIT", ignoreCase = true)

    /** Parses a 0100 response's 4-byte bitmask into supported PID hex codes, e.g. {"04","05","0C","0D","11"}. */
    fun parseSupportedPids(raw: String): Set<String> {
        val bytes = extractBytes(raw) ?: return emptySet()
        val dataBytes = if (bytes.size >= 6 && bytes[0] == 0x41 && bytes[1] == 0x00) bytes.drop(2) else bytes
        if (dataBytes.size < 4) return emptySet()
        val bitmask = dataBytes.take(4)
        val supported = mutableSetOf<String>()
        var pidNumber = 1
        for (byte in bitmask) {
            for (bitIndex in 7 downTo 0) {
                if ((byte shr bitIndex) and 0x1 == 1) {
                    supported.add(pidNumber.toString(16).padStart(2, '0').uppercase())
                }
                pidNumber++
            }
        }
        return supported
    }

    fun parsePidValue(pid: ObdPid, raw: String): Double? {
        if (isNoData(raw) || isError(raw)) return null
        val bytes = extractBytes(raw) ?: return null
        val data = if (bytes.size >= 2 && bytes[0] == 0x41) bytes.drop(2) else bytes
        return when (pid) {
            ObdPid.RPM -> {
                if (data.size < 2) null else ((data[0] * 256) + data[1]) / 4.0
            }
            ObdPid.SPEED -> data.getOrNull(0)?.toDouble()
            ObdPid.COOLANT_TEMP -> data.getOrNull(0)?.let { it - 40.0 }
            ObdPid.THROTTLE -> data.getOrNull(0)?.let { it * 100.0 / 255.0 }
            ObdPid.ENGINE_LOAD -> data.getOrNull(0)?.let { it * 100.0 / 255.0 }
        }
    }
}
