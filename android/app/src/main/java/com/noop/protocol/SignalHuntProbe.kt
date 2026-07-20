package com.noop.protocol

/**
 * Safe rapid-fire signal-hunt catalog for WHOOP 5/MG (debug / Test Centre / Tools twin).
 *
 * Inventory of candidate command IDs and payloads the strap *might* answer — used to plan
 * bounded probe bursts. This object does **not** talk BLE by itself; callers (debug-only
 * WhoopBleClient / Tools script) must:
 *  - write WITH RESPONSE on fd4b0002
 *  - gap ≥ 80 ms between commands
 *  - restore research toggles OFF after a short listen window
 *  - never bank invented vitals (no SpO₂% from @82, no fake live IMU from cmd-106 ACK)
 *
 * Denylist mirrors whoop_protocol.json destructive opcodes (DFU / firmware / fuel-gauge).
 * Keep in lockstep with Tools/signal_hunt_rapid_fire.py.
 */
object SignalHuntProbe {

    enum class Tier {
        /** Non-destructive GET-style reads — safe for rapid-fire. */
        READ,
        /** Commands NOOP already uses for live streams. */
        KNOWN_STREAM,
        /** Send disable / off first. */
        TOGGLE_OFF,
        /** May enable a stream; always pair with OFF; never auto-bank vitals. */
        RESEARCH,
    }

    data class Candidate(
        val cmd: Int,
        val name: String,
        val tier: Tier,
        val payload: ByteArray,
        val note: String,
        val expect: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Candidate) return false
            return cmd == other.cmd && name == other.name &&
                tier == other.tier && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var r = cmd
            r = 31 * r + name.hashCode()
            r = 31 * r + tier.hashCode()
            r = 31 * r + payload.contentHashCode()
            return r
        }
    }

    /** Opcodes that must never be sent by a signal hunt. Keep lockstep with Tools script. */
    val DENYLIST: Map<Int, String> = mapOf(
        25 to "FORCE_TRIM",
        32 to "POWER_CYCLE_STRAP",
        36 to "START_FIRMWARE_LOAD",
        37 to "LOAD_FIRMWARE_DATA",
        38 to "PROCESS_FIRMWARE_IMAGE",
        45 to "ENTER_BLE_DFU",
        83 to "VERIFY_FIRMWARE_IMAGE",
        99 to "RESET_FUEL_GAUGE",
        142 to "START_FIRMWARE_LOAD_NEW",
        143 to "LOAD_FIRMWARE_DATA_NEW",
        144 to "PROCESS_FIRMWARE_IMAGE_NEW",
    )

    /**
     * Dense GET_FF_VALUE (128) name sweep — Lane 1 BLE RE.
     * Official R22 first, then IPA/Lane3 hypotheses (GET only; never invent SpO₂%).
     * Keep lockstep with Tools/signal_hunt_rapid_fire.py FF_READ_NAMES.
     * IPA 5.61.0 does not embed FF names as plaintext — see WHOOP_IPA_MG_INTERCEPT_2026-07-18.md
     */
    val FF_READ_NAMES: List<String> = listOf(
        // Whoop5Config.enableR22Sequence (MG GET_FF SUCCESS catalog)
        "enable_r22_packets",
        "enable_r22_v2_packets",
        "enable_r22_v3_packets",
        "enable_r22_v4_packets",
        "enable_r22_v5_packets",
        "enable_r22_v6_packets",
        "enable_r22_v8_packets",
        "make_hrfm_visible",
        "disable_pip_r26_packets",
        "wear_detect_bias",
        "hr_ch_switching",
        "ir_hw_switching",
        "enable_passive_strap_fit_gen5",
        "enable_sig11_during_sleep",
        "dorset_inhibit_wpt",
        // IPA Fake FF key + Lane3 console / Lane2 extras (GET only)
        "sigproc_wear_detect",
        "enable_raw_data_w_ecg",
        "max_collection_backlog",
        "enable_sig12",
        "enable_maverick_model",
        // Hypotheses / device-config adjacent (GET only)
        "whoop_live_hr_in_adv_ind_pkt",
        "enable_r22_v1_packets",
        "enable_r22_v7_packets",
        "enable_r20_packets",
        "enable_r21_packets",
        "enable_r26_packets",
        "enable_optical_data",
        "enable_imu_stream",
        "enable_labrador",
        "toggle_persistent_r20",
        "toggle_persistent_r21",
        "research_packet_enable",
    )

    private fun p(vararg bytes: Int): ByteArray =
        ByteArray(bytes.size) { i -> bytes[i].toByte() }

    /** Curated safe candidate list (read + known + research/off pairs). */
    val CANDIDATES: List<Candidate> = listOf(
        Candidate(3, "TOGGLE_REALTIME_HR", Tier.KNOWN_STREAM, p(0x01),
            "Arm type-40 / 2A37 dense HR+R-R", "type-40 + ACK"),
        Candidate(3, "TOGGLE_REALTIME_HR_OFF", Tier.TOGGLE_OFF, p(0x00),
            "Disarm realtime after burst", "ACK; type-40 stops"),
        Candidate(63, "SEND_R10_R11_REALTIME_OFF", Tier.TOGGLE_OFF, p(0x00),
            "Keep type-43 flood off", "ACK"),
        Candidate(63, "SEND_R10_R11_REALTIME_ON", Tier.RESEARCH, p(0x01),
            "Bounded raw flood only", "type-43; stop with 00"),
        Candidate(7, "REPORT_VERSION_INFO", Tier.READ, p(0x00), "FW block", "COMMAND_RESPONSE"),
        Candidate(11, "GET_CLOCK", Tier.READ, byteArrayOf(), "Empty payload on 5/MG", "RTC"),
        Candidate(26, "GET_BATTERY_LEVEL", Tier.READ, p(0x00), "Bond-safe", "batt %"),
        Candidate(34, "GET_DATA_RANGE", Tier.READ, byteArrayOf(), "History window", "unix range"),
        Candidate(40, "GET_LED_DRIVE", Tier.READ, p(0x00), "AFE LED", "response?"),
        Candidate(42, "GET_TIA_GAIN", Tier.READ, p(0x00), "AFE TIA", "response?"),
        Candidate(44, "GET_BIAS_OFFSET", Tier.READ, p(0x00), "AFE bias", "response?"),
        Candidate(62, "GET_AFE_PARAMETERS", Tier.READ, p(0x00), "AFE blob", "response?"),
        Candidate(67, "GET_ALARM_TIME", Tier.READ, p(0x00), "Alarm", "payload"),
        Candidate(80, "GET_ALL_HAPTICS_PATTERN", Tier.READ, p(0x00), "Haptics", "patterns"),
        Candidate(84, "GET_BODY_LOCATION_AND_STATUS", Tier.READ, p(0x00), "Wear/location", "status"),
        Candidate(98, "GET_EXTENDED_BATTERY_INFO", Tier.READ, p(0x00), "mV detail", "extended"),
        Candidate(121, "GET_DEVICE_CONFIG_VALUE", Tier.READ, p(0x01), "b3=01 probe", "CONFIG?"),
        Candidate(128, "GET_FF_VALUE", Tier.READ, p(0x01), "Feature-flag read", "FF echo"),
        Candidate(132, "GET_RESEARCH_PACKET", Tier.READ, p(0x00), "Research cfg", "response?"),
        Candidate(141, "GET_ADVERTISING_NAME", Tier.READ, p(0x00), "Adv name", "name"),
        Candidate(145, "GET_HELLO", Tier.READ, p(0x01), "Hello + fw", "hello"),
        Candidate(151, "GET_BATTERY_PACK_INFO", Tier.READ, p(0x00), "MG pack", "pack"),
        Candidate(81, "START_RAW_DATA", Tier.RESEARCH, p(0x00), "Raw window start", "raw/43"),
        Candidate(82, "STOP_RAW_DATA", Tier.TOGGLE_OFF, p(0x00), "Raw window end", "ACK"),
        // IPA twins: ToggleHistoricalIMUData / ToggleRealtimeIMUStream / Optical* (often FAILURE on MG)
        Candidate(105, "TOGGLE_IMU_MODE_HISTORICAL", Tier.RESEARCH, p(0x01), "IPA ToggleHistoricalIMUData", "ACK≠stream"),
        Candidate(106, "TOGGLE_IMU_MODE", Tier.RESEARCH, p(0x01), "IPA ToggleRealtimeIMUStream", "watch 51–56"),
        Candidate(106, "TOGGLE_IMU_MODE_OFF", Tier.TOGGLE_OFF, p(0x00), "IMU off", "ACK"),
        Candidate(107, "ENABLE_OPTICAL_DATA", Tier.RESEARCH, p(0x01), "IPA ToggleHistoricalOpticalData", "watch 0x2F"),
        Candidate(107, "ENABLE_OPTICAL_DATA_OFF", Tier.TOGGLE_OFF, p(0x00), "Optical off", "ACK"),
        Candidate(108, "TOGGLE_OPTICAL_MODE", Tier.RESEARCH, p(0x01), "IPA ToggleRealtimeOpticalData", "new types?"),
        Candidate(108, "TOGGLE_OPTICAL_MODE_OFF", Tier.TOGGLE_OFF, p(0x00), "Optical restore", "ACK"),
        Candidate(153, "TOGGLE_PERSISTENT_R20", Tier.RESEARCH, p(0x01), "R20 research?", "new frames?"),
        Candidate(153, "TOGGLE_PERSISTENT_R20_OFF", Tier.TOGGLE_OFF, p(0x00), "R20 off", "ACK"),
        Candidate(154, "TOGGLE_PERSISTENT_R21", Tier.RESEARCH, p(0x01), "R21 research?", "new frames?"),
        Candidate(154, "TOGGLE_PERSISTENT_R21_OFF", Tier.TOGGLE_OFF, p(0x00), "R21 off", "ACK"),
    )

    fun isDenied(cmd: Int): Boolean = cmd in DENYLIST

    /** Read-only burst — default rapid-fire set. */
    fun readBurst(): List<Candidate> = CANDIDATES.filter { it.tier == Tier.READ }

    /**
     * Research burst: each RESEARCH candidate followed by its matching *_OFF when present.
     * Caller must still enforce ≤8 s listen windows and capture RAW_GATT.
     */
    fun researchBurstPaired(): List<Candidate> {
        val out = mutableListOf<Candidate>()
        for (c in CANDIDATES) {
            if (c.tier != Tier.RESEARCH) continue
            if (isDenied(c.cmd)) continue
            out += c
            val off = CANDIDATES.firstOrNull {
                it.tier == Tier.TOGGLE_OFF && it.cmd == c.cmd && it.name.endsWith("_OFF")
            }
            if (off != null) out += off
        }
        return out.distinctBy { it.name }
    }

    /** Build a puffin COMMAND frame for a candidate (dry-run / debug write). */
    fun frame(candidate: Candidate, seq: Int): ByteArray {
        require(!isDenied(candidate.cmd)) { "denied cmd ${candidate.cmd}" }
        return Framing.puffinCommandFrame(
            cmd = candidate.cmd,
            seq = seq,
            payload = candidate.payload,
        )
    }

    /**
     * GET_FF_VALUE body: b3=0x01 + 40-byte name (value byte 0). Matches Whoop5Config layout
     * without writing a flag.
     */
    fun getFfFrame(flagName: String, seq: Int): ByteArray {
        val body = Whoop5Config.payloadBody(flagName, 0x00)
        return Framing.puffinCommandFrame(
            cmd = 128, // GET_FF_VALUE
            seq = seq,
            payload = byteArrayOf(0x01) + body,
        )
    }
}
