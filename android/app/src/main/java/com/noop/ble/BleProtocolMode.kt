package com.noop.ble

import com.noop.protocol.DeviceFamily

/**
 * User override for which WHOOP command framing to use after GATT discovery.
 * Auto prefers the 5/MG service when both UUIDs are present (MG often also exposes the
 * legacy 4.0 service — preferring 4 first was sending WHOOP-4 buzz opcodes to MG).
 */
enum class BleProtocolMode(val storageKey: String, val label: String) {
    AUTO("auto", "Auto"),
    WHOOP4("whoop4", "WHOOP 4.0"),
    WHOOP5("whoop5", "5.0 / MG");

    companion object {
        fun fromStorage(raw: String?): BleProtocolMode =
            entries.firstOrNull { it.storageKey == raw } ?: AUTO
    }
}

/**
 * Pure GATT family resolver — unit-testable, no Android types.
 *
 * Dual-service peripherals are treated as 5/MG under Auto (classic 4.0 never advertises the
 * 5 UUID). Forced modes honour the user pick when that service exists; otherwise fall back
 * to the only available service and the caller should log a warning.
 */
fun resolveGattCommandFamily(
    hasWhoop4: Boolean,
    hasWhoop5: Boolean,
    mode: BleProtocolMode,
): DeviceFamily? {
    if (!hasWhoop4 && !hasWhoop5) return null
    return when (mode) {
        BleProtocolMode.WHOOP4 -> when {
            hasWhoop4 -> DeviceFamily.WHOOP4
            else -> DeviceFamily.WHOOP5
        }
        BleProtocolMode.WHOOP5 -> when {
            hasWhoop5 -> DeviceFamily.WHOOP5
            else -> DeviceFamily.WHOOP4
        }
        BleProtocolMode.AUTO -> when {
            // Dual-service = MG/5.0 generation (classic 4.0 never exposes the 5 UUID).
            // Preferring 4 first sent WHOOP-4 buzz framing to Gilbert's MG.
            hasWhoop5 && hasWhoop4 -> DeviceFamily.WHOOP5
            hasWhoop5 -> DeviceFamily.WHOOP5
            hasWhoop4 -> DeviceFamily.WHOOP4
            else -> null
        }
    }
}
