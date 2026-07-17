package com.noop.ble

import java.util.UUID

/**
 * Which strap the user is pairing. They pick this before scanning so we look for
 * exactly one device family instead of guessing — a WHOOP 4.0 scan no longer
 * waits forever on a WHOOP 5/MG wrist, and vice versa.
 *
 * This is the user-facing choice; it is deliberately separate from the
 * protocol-layer DeviceFamily (which carries CRC/characteristic detail).
 */
enum class WhoopModel(val displayName: String, val service: UUID) {
    WHOOP3("WHOOP 3.0", WhoopBleClient.WHOOP4_SERVICE),
    WHOOP4("WHOOP 4.0", WhoopBleClient.WHOOP4_SERVICE),
    WHOOP5_MG("WHOOP 5.0 / MG", WhoopBleClient.WHOOP5_SERVICE);

    /** True for 5.0 / MG (shared puffin family + ~12-day pack). */
    val isWhoop5Family: Boolean get() = this == WHOOP5_MG

    /**
     * The OTHER WHOOP family to try when a service-filtered scan for this model finds nothing. A
     * stale/missing persisted preference (after an update or restore) can point the scan at the wrong
     * service so it runs forever with the strap right there; rotating to the other family — and
     * persisting whichever one actually advertises — recovers reconnect automatically. Mirrors macOS
     * `WhoopModel.fallbackScanModel`. (PR#195)
     */
    val fallbackScanModel: WhoopModel
        get() = when (this) {
            WHOOP3 -> WHOOP4
            WHOOP4 -> WHOOP5_MG
            WHOOP5_MG -> WHOOP4
        }

    companion object {
        /**
         * Infer generation from the OS Bluetooth name ("WHOOP 4C…", "WHOOP MG…", "WHOOP 5…").
         * Serials like "WHOOP 4C1594026" are classic 4.0 (starts with "WHOOP 4"). Never invent —
         * returns null when the name is missing or not a WHOOP.
         */
        fun fromBleName(name: String?): WhoopModel? {
            val n = name?.trim().orEmpty()
            if (n.isEmpty()) return null
            if (!n.startsWith("WHOOP", ignoreCase = true)) return null
            // Classic 4.0 advertising / bonded names start with "WHOOP 4" (incl. serial 4C…).
            if (n.startsWith("WHOOP 4", ignoreCase = true)) return WHOOP4
            if (n.startsWith("WHOOP 3", ignoreCase = true)) return WHOOP3
            // Everything else in the WHOOP namespace (MG, 5.0, "WHOOP …") is the 5/MG family.
            return WHOOP5_MG
        }

        /**
         * Strap used for battery rated-life + connect labels: live GATT detection wins, then the
         * in-memory picker, then the last persisted bond — never flip a bonded MG to "WHOOP 4".
         */
        fun resolveForEstimates(
            selected: WhoopModel,
            whoop5Detected: Boolean,
            persisted: WhoopModel?,
        ): WhoopModel = when {
            whoop5Detected -> WHOOP5_MG
            selected.isWhoop5Family -> WHOOP5_MG
            persisted?.isWhoop5Family == true -> WHOOP5_MG
            else -> selected
        }
    }
}
