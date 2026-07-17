package com.noop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Shared cute life-anim motes for Today / Alarm / Nutrition / Cycle.
 * Token-alpha bloom only (≤0.22 — Design.md anti-glow). Reduce Motion = static poses.
 */

/** Shared lacquer contract — Rest bedtime / Cycle / Fuel / Sip chapter cohesion. */
object LifeChapterLacquer {
    const val SURFACE_ALPHA = 0.50f
    const val CORNER_DP = 16
    const val PAD_V_DP = 11
    const val BORDER_ALPHA = 0.22f
    /** Today Cycle / Fuel / Sip chapter row min height. */
    const val CHAPTER_MIN_HEIGHT_DP = 52
    /** Today Quick Alarm moons quieter than Alarm glance. */
    const val TODAY_MOON_INTENSITY = 0.82f
    const val ALARM_MOON_INTENSITY = 1.0f
    const val FUEL_PEEK_INTENSITY = 0.72f
    /** Nutrition meals hero (louder than Today Fuel peek). */
    const val FUEL_HERO_INTENSITY = 1.0f
    /** Quick meal chip wash under chip row. */
    const val FUEL_CHIP_INTENSITY = 0.55f
    const val SIP_INTENSITY = 0.88f
    /** Cycle stars on Today card (between Fuel peek and Sip). */
    const val CYCLE_INTENSITY = 0.90f
    /** Creatine / supplement reminder strip (quieter than chapter surface). */
    const val SUPP_SURFACE_ALPHA = 0.42f
    /** Sip / meal / supplement one-shot burst settle (Reduce Motion = 0). */
    const val SIP_BURST_MS = 640
    /** Frost wash on Alarm glance vs Today Quick Alarm. */
    const val ALARM_GLANCE_WASH = 1.05f
    const val TODAY_ALARM_WASH = 0.95f
    /** Optical-lock hairline fill (token alpha only). */
    const val OPTICAL_LOCK_FILL = 0.55f
    const val OPTICAL_LOCK_TRACK = 0.18f
    /** Optical-lock climb settle (Reduce Motion = 0). */
    const val OPTICAL_LOCK_SETTLE_MS = 320
    /** Beats-toward-feel RMSSD hairline (need ≥3 intervals). */
    const val RR_FEEL_FILL = 0.50f
    const val RR_FEEL_TRACK = 0.16f
    const val RR_FEEL_NEED = 3
    /** Alarm Arm / Extras frost wash (glance = ALARM_GLANCE_WASH). */
    const val ALARM_ARM_WASH = 1.15f
    const val ALARM_EXTRAS_WASH = 1.10f
    /** Wake-settings WindowCard frost (between Today Quick and Alarm glance). */
    const val ALARM_WINDOW_WASH = 1.00f
    /** Soft wake-span track fill / track (Arm + WindowCard). */
    const val WAKE_SPAN_FILL = 0.42f
    const val WAKE_SPAN_TRACK = 0.16f
    const val WAKE_SPAN_H_DP = 6
    /** Live BodyConsole frost wash (Effort world). */
    const val LIVE_BODY_WASH = 1.08f
    /** WindowCard moons match Today Quick (quieter than Alarm glance). */
    const val ALARM_WINDOW_MOON_INTENSITY = 0.82f
    /** Armed status pill border + next-fire caption alpha. */
    const val STATUS_PILL_BORDER = 0.50f
    const val STATUS_PILL_WASH = 0.14f
    const val ARMED_CAPTION_ALPHA = 0.78f
    /** Rest bridge line under Alarm glance clock. */
    const val BRIDGE_CAPTION_ALPHA = 0.62f
    /** Arm / Off pill settle (Reduce Motion = 0). */
    const val ARM_SETTLE_MS = 280
    /** Glance bedtime / wake clocks (Alarm page — dual-region Bedtime | Wake). */
    const val ALARM_GLANCE_CLOCK_SP = 34f
    /** Today Quick dual-region clocks. */
    const val TODAY_ALARM_CLOCK_SP = 26f
    /** Dual-region divider alpha (Bedtime | Wake). */
    const val ALARM_DUAL_DIVIDER_ALPHA = 0.22f
    /** Status pill — Armed / Off / May drift (shared). */
    const val ALARM_STATUS_ARMED = "Armed"
    const val ALARM_STATUS_OFF = "Off"
    const val ALARM_STATUS_MAY_DRIFT = "May drift"
    /** Armed next-fire foot prefix (optional; bare soonest is default). */
    const val ALARM_NEXT_FIRE_PREFIX = "Next"
    /** Today Effort brief (cloud portal · vessel + Key Metrics). */
    const val EFFORT_SHEET_TITLE = "Effort"
    const val EFFORT_SHEET_BODY =
        "How hard today has been on your body from open BLE heart-rate work. Vessel and Key Metrics share this number."
    const val EFFORT_SHEET_SCALE_OVERLINE = "Two scales, same day"
    const val EFFORT_SHEET_SCALE_BODY =
        "NOOP Effort is 0–100. WHOOP Day Strain is 0–21. Compare only after ×100/21."
    const val EFFORT_SHEET_AXIS_21 = "Showing the 0–21 axis right now."
    const val EFFORT_SHEET_AXIS_100 = "Showing the 0–100 axis right now."
    const val EFFORT_SHEET_WHOOP_APP =
        "Number on the vessel is from the WHOOP app (not NOOP open BLE)."
    const val EFFORT_SHEET_HOW = "How Effort is calculated"
    /** Today Charge cloud portal. */
    const val CHARGE_CLOUD_TITLE = "What shaped your Charge"
    const val CHARGE_CLOUD_HOW = "How Charge is calculated"
    const val CHARGE_CLOUD_HOW_CAPTION = "The method behind the score, not today's values."
    /** Today Rest cloud brief (Key Metrics · vessel still deep-links Sleep). */
    const val REST_CLOUD_TITLE = "Rest"
    const val REST_CLOUD_BODY =
        "Night readiness from sleep you banked on-device. Higher is better — use this for can I train. Open Sleep for hours and stages."
    const val REST_CLOUD_OPEN_SLEEP = "Open Sleep"
    const val REST_CLOUD_HOW = "How Rest is calculated"
    /** Today day nav shared cue (SHIP #25) — chevrons removed; swipe + tap date. */
    const val TODAY_DAY_NAV_CUE = "Swipe the page · or tap the date"
    /** Empty Today brand whisper (SHIP #34). */
    const val TODAY_EMPTY_BRAND = "NOOP"
    /** Compare Rest head vs Sleep tab (SHIP #31). */
    const val COMPARE_REST_VS_SLEEP = "Rest % here · Sleep tab has hours & stages"
    /** Sleep chapter hierarchy (SHIP #54). */
    const val SLEEP_CHAPTER_SUBTITLE = "Tonight first · then ledger · trends · tools."
    /** SHIP #69 — Sleep night-first vs Today day-first. */
    const val SLEEP_VS_TODAY_EMOTION =
        "Sleep is night-first. Today is day-first. Same body — different chapter."
    /** SHIP #90 — Ready checklist one-liner. */
    const val SLEEP_READY_CHECKLIST =
        "Ready: strap on · permissions · first night · optional Cycle."
    /** Alarm schedule vs Sleep (SHIP #65). */
    const val ALARM_VS_SLEEP_SCHEDULE =
        "Sleep tracks the night you banked. Alarm is tomorrow’s wake window — not the same schedule."
    /** Exact-alarm asked with Sleep context (SHIP #67). */
    const val ALARM_EXACT_SLEEP_CONTEXT =
        "Exact alarms let Soft wake protect deep sleep on this phone — Sleep → Alarm."
    /** Today floating-cloud explain epic. */
    const val TODAY_CLOUD_EPIC = "Floating cloud explain"
    /** Under-vessel polarity (SHIP #19) — soft Charge cycle caption wins when present. */
    const val VESSEL_POLARITY_HIGHER = "higher is better"
    const val VESSEL_POLARITY_EFFORT = "load · lower is easier"
    /** Vessel fill direction (SHIP #20) — liquid fills bottom→top. */
    const val VESSEL_FILL_HINT = "fills up"
    /** Combined underCaptions (SHIP #20/#22/#23). */
    const val VESSEL_UNDER_CHARGE = "higher is better · fills up"
    const val VESSEL_UNDER_EFFORT = "load · lower is easier · fills up · WHOOP calls this Strain"
    const val VESSEL_UNDER_REST = "higher is better · night readiness · Sleep tab"
    /** Past-day empty vessel (SHIP #26) — not today's "wear / awaiting strap". */
    const val VESSEL_PAST_DAY_EMPTY = "No score this day"
    /** Sessions under vessels (SHIP #27). */
    const val SESSIONS_EFFORT_OVERLINE = "These add to Effort"
    /** Compare purpose (SHIP #28). */
    const val COMPARE_PURPOSE = "Checks if our scores track the WHOOP app"
    const val COMPARE_FOOTNOTE_SHORT = "LEFT = NOOP · RIGHT = WHOOP app"
    /** Stress head has no Today vessel twin (SHIP #30). */
    const val COMPARE_STRESS_NO_VESSEL = "No Stress vessel — open Health"
    /** Today HR trend vs Health live (SHIP 145 leftover). */
    const val TODAY_HR_TREND_OVERLINE = "Day trend · live HR on Health"
    /** Compare dual-scale (SHIP #35/#36/#40). */
    const val COMPARE_DUAL_SCALE_OVERLINE = "DUAL SCALE · 0–100 shared · Strain ×100/21 for the blue mark"
    const val COMPARE_DUAL_SCALE_EXAMPLE = "14.7/21 ≈ 70 on shared %"
    const val COMPARE_NOT_SAME_SCALE = "Not the same scale"
    /** Today Weight tile entry sheet. */
    const val WEIGHT_ENTRY_TITLE = "Weight"
    const val WEIGHT_ENTRY_BODY =
        "Saves to your profile for Effort and Fuel. Your typed weight updates Key Metrics right away; a newer Health day can replace it later."
    const val WEIGHT_ENTRY_HEALTH_PREFIX = "Latest from Health ·"
    const val WEIGHT_ENTRY_SAVE = "Save weight"
    const val WEIGHT_ENTRY_OPEN_HEALTH = "Open Health"
    /** Shared optical-lock lead — Live / Health / Today (type-40 counts append). */
    const val OPTICAL_LOCK_LEAD = "Optical lock… R-R usually after ~30–60s on MG"
    /** RRStrip own-caption lead while awaiting intervals (shorter than OPTICAL_LOCK_LEAD). */
    const val OPTICAL_LOCK_WAITING_LEAD = "Optical lock · waiting for R-R"
    /** Sleep glance Wake-edit frost (matches ALARM_ARM_WASH). */
    const val ALARM_WAKE_EDIT_WASH = 1.15f
    /** ExplanationCard inset alpha vs SURFACE_ALPHA. */
    const val ALARM_EXPLAIN_SURFACE = 0.76f
    /** Last-night Sleep peek frost on Alarm glance. */
    const val ALARM_SLEEP_PEEK_WASH = 0.85f
    const val ALARM_SLEEP_PEEK_CORNER_DP = 14
    /** Exact-alarm early warn border / wash. */
    const val ALARM_EXACT_WARN_BORDER = 0.45f
    const val ALARM_EXACT_WARN_WASH = 0.08f
    /** Dual-buzz footnote surface vs SURFACE_ALPHA. */
    const val ALARM_DUAL_BUZZ_SURFACE = 0.72f
    /** Why-alarm empty paragraph surface vs SURFACE_ALPHA. */
    const val ALARM_WHY_SURFACE = 0.68f
    /** Live / Health LOCK chip overline. */
    const val OPTICAL_LOCK_CHIP_LABEL = "LOCK"
    /** Health State pill while awaiting type-40 R-R. */
    const val OPTICAL_LOCK_STATE = "LOCK…"
    /** Today empty-chips optical lead (SpO₂/BP when banked). */
    const val TODAY_OPTICAL_EMPTY_LEAD =
        "Live HR · optical lock for RMSSD"
    /** Signal Trust short lead (no type-40 counts). */
    const val OPTICAL_LOCK_TRUST_LEAD = "Optical lock"
    /** Shared optical / R-R feel hairline height (dp). */
    const val HAIRLINE_H_DP = 3
    /** Health / Live RMSSD chip overline (idle). */
    const val RMSSD_CHIP_LABEL = "RMSSD"
    /** Health State pill while streaming live HR. */
    const val HEALTH_STREAMING_STATE = "STREAMING"
    /** Health State pill when idle. */
    const val HEALTH_IDLE_STATE = "IDLE"
    /** WindowCard bedtime overline when Armed without exact. */
    const val ALARM_BED_NEEDS_EXACT = "Bedtime · needs exact alarm"
    /** WindowCard bedtime overline default. */
    const val ALARM_BED_RECOMMENDED = "Recommended bedtime"
    /** Alarm glance / WindowCard bedtime overline glyph. */
    const val ALARM_BEDTIME_OVERLINE = "BEDTIME"
    /** Sleep glance Wake-edit overline. */
    const val ALARM_WAKE_UP_OVERLINE = "WAKE UP"
    /** Strap Test-buzz button label. */
    const val ALARM_TEST_BUZZ_LABEL = "Test buzz"
    /** Buzz wall-clock button label. */
    const val ALARM_BUZZ_TIME_LABEL = "Buzz wall-clock time"
    /** Today Health chip when live feel RMSSD is ready. */
    const val LIVE_RMSSD_CHIP_LABEL = "Live RMSSD"
    /** Today / Health banked HRV chip label. */
    const val HRV_RMSSD_CHIP_LABEL = "HRV · RMSSD"
    /** Health section trailing when BPM is R-R-derived. */
    const val RR_DERIVED_TRAILING = "from R-R"
    /** Live Proof / datastream R-R tile label. */
    const val RR_PROOF_LABEL = "R-R"
    /** Signal Trust detail while type-40 has no intervals yet. */
    const val RR_NEEDS_FRAMES_DETAIL = "Needs interval frames"
    /** Sleep|Alarm + Wake settings chapter overlines. */
    const val ALARM_GLANCE_OVERLINE = "Glance"
    const val ALARM_ARM_OVERLINE = "Arm"
    const val ALARM_EXTRAS_OVERLINE = "Extras"
    const val ALARM_CUES_OVERLINE = "Cues"
    const val ALARM_STRAP_OVERLINE = "Band"
    const val ALARM_WAKE_OVERLINE = "Wake"
    const val ALARM_PLAN_OVERLINE = "Plan"
    /** Plan card overline (Wake settings). */
    const val ALARM_SLEEP_PLAN_OVERLINE = "Your sleep plan"
    /** ExplanationCard overline. */
    const val ALARM_HOW_IT_WORKS_OVERLINE = "How it works"
    /** Custom alarms chapter overline. */
    const val ALARM_EXACT_TIME_OVERLINE = "Exact time"
    /** Extras tertiary chip labels (Sleep glance). */
    const val ALARM_TURN_BACK_LABEL = "Turn-back"
    const val ALARM_WAKE_RESTED_LABEL = "Wake rested"
    const val ALARM_WIND_DOWN_LABEL = "Wind-down"
    /** Wake settings master switch label. */
    const val ALARM_WAKE_ME_UP_LABEL = "Wake me up"
    /** Sleep session edit dialog wake row overline. */
    const val ALARM_WAKE_UP_SHORT_OVERLINE = "Wake-up"
    /** Sleep session edit dialog bedtime row overline. */
    const val ALARM_EDIT_BEDTIME_OVERLINE = "Bedtime"
    /** RRStrip empty-state hairline height (dp) — quieter than HAIRLINE_H_DP. */
    const val RR_STRIP_PLACEHOLDER_H_DP = 2
    /** Optical-lock / type-40 trail unit (frames count). */
    const val TYPE40_UNIT_LABEL = "type-40"
    /** Optical-lock / type-40 trail when intervals present. */
    const val RR_WITH_LABEL = "with R-R"
    /** Wake settings Custom chapter overline. */
    const val ALARM_CUSTOM_OVERLINE = "Custom"
    /** Wake settings / WindDown Evening chapter overline. */
    const val ALARM_EVENING_OVERLINE = "Evening"
    /** Strap wake-alarm Morning chapter overline. */
    const val ALARM_MORNING_OVERLINE = "Morning"
    /** Sleep → Alarm home title (Automations door + legacy scaffold). */
    const val ALARM_HOME_TITLE = "Alarm"
    /** @deprecated Wake settings merged into Sleep → Alarm — alias of [ALARM_HOME_TITLE]. */
    const val ALARM_WAKE_SETTINGS_TITLE = ALARM_HOME_TITLE
    /** Soft-window companion strap buzz toggle. */
    const val ALARM_BUZZ_STRAP_LABEL = "Buzz connected strap"
    /** Firmware strap wake-alarm card title. */
    const val ALARM_STRAP_WAKE_TITLE = "Strap wake-alarm"
    /** Wind-down nudge card title. */
    const val ALARM_WIND_DOWN_NUDGE_TITLE = "Wind-down nudge"
    /** Turn-back watch-window row label. */
    const val ALARM_WATCH_AFTER_WAKE_LABEL = "Watch after wake"
    /** Wake-rested Charge threshold row label. */
    const val ALARM_CHARGE_THRESHOLD_LABEL = "Charge threshold"
    /** Wake-rested sleep-need row label. */
    const val ALARM_SLEEP_NEED_MET_LABEL = "Sleep need met"
    /** Exact-settings / Automations jump CTA. */
    const val ALARM_OPEN_LABEL = "Open"
    /** Wake me up toggle help. */
    const val ALARM_WAKE_ME_UP_HELP =
        "NOOP sets a guaranteed phone alarm and can use an early HR-based cue inside your chosen window. It does not diagnose sleep stages."
    /** Turn-back toggle help. */
    const val ALARM_TURN_BACK_HELP =
        "After you wake, if your heart rate rises then falls again (likely dozing), NOOP cues you once more. Coarse HR heuristic — not sleep-stage detection."
    /** Watch-after-wake row footnote. */
    const val ALARM_WATCH_AFTER_HELP = "How long to keep watching live HR."
    /** Turn-back HR-drop row title. */
    const val ALARM_HR_DROP_LABEL = "HR drop to cue"
    /** Turn-back phone-cue toggle. */
    const val ALARM_PHONE_CUE_LABEL = "Phone cue too"
    /** Turn-back phone-cue help. */
    const val ALARM_PHONE_CUE_HELP =
        "Also fire a phone notification when turn-back triggers (strap buzz always tries)."
    /** Wake-rested toggle help. */
    const val ALARM_WAKE_RESTED_HELP =
        "Inside your window, wake early once sleep need looks met or Charge is already green. Hard deadline still stands."
    /** Strap firmware wake-time row. */
    const val ALARM_WAKE_AT_LABEL = "Wake at"
    /** Wind-down remind toggle. */
    const val ALARM_WIND_DOWN_REMIND_LABEL = "Remind me to wind down"
    /** ExplanationCard subhead. */
    const val ALARM_HOW_SMART_WAKE_TITLE = "How smart wake works"
    /** Custom-alarm rename dialog title. */
    const val ALARM_RENAME_TITLE = "Rename alarm"
    /** Wake-window TimeChip a11y. */
    const val ALARM_EARLIEST_WAKE_A11Y = "Earliest wake time"
    /** Turn-back watch stepper a11y. */
    const val ALARM_SHORTER_WATCH_A11Y = "Shorter watch"
    const val ALARM_LONGER_WATCH_A11Y = "Longer watch"
    /** Health / Live HR card while connected with no BPM yet. */
    const val HEALTH_AWAITING_SAMPLE =
        "Strap connected — heart rate usually appears in a few seconds."
    /** Health / Live HR card when strap not streaming. */
    const val HEALTH_AWAITING_STRAP = "Wear your strap"
    /** Stress Monitor one-liner above the hero (#177). */
    const val STRESS_PLAIN_DEFINITION =
        "Stress here means how activated your body looks vs your usual calm — not mood or anxiety."
    /** Settings toast after hiding the Cycle bottom tab (#213). */
    const val CYCLE_TAB_HIDDEN_TOAST = "Cycle is under More → For your body"
    /** Health Cycle awareness turn-off confirm (#247). */
    const val CYCLE_AWARENESS_OFF_TITLE = "Turn off cycle awareness?"
    const val CYCLE_AWARENESS_OFF_BODY =
        "Period log stays; phase card hides until you opt in again."
    /** Live / HrvSnapshot primary CTA. */
    const val HRV_TAKE_READING_LABEL = "Take an HRV reading"
    /** Turn-back HR-drop stepper a11y. */
    const val ALARM_LESS_SENSITIVE_A11Y = "Less sensitive"
    const val ALARM_MORE_SENSITIVE_A11Y = "More sensitive"
    /** Wake-rested Charge / sleep-need stepper a11y. */
    const val ALARM_LOWER_THRESHOLD_A11Y = "Lower threshold"
    const val ALARM_HIGHER_THRESHOLD_A11Y = "Higher threshold"
    const val ALARM_LOWER_PERCENT_A11Y = "Lower percent"
    const val ALARM_HIGHER_PERCENT_A11Y = "Higher percent"
    /** Soft-window length stepper a11y. */
    const val ALARM_SHORTEN_WINDOW_A11Y = "Shorten window"
    const val ALARM_LENGTHEN_WINDOW_A11Y = "Lengthen window"
    /** Strap firmware wake toggle (distinct from soft-window companion buzz). */
    const val ALARM_STRAP_FIRMWARE_LABEL = "Wake me with a strap buzz"
    /** Custom-alarm remove affordance + confirm. */
    const val ALARM_REMOVE_LABEL = "Remove"
    /** Custom-alarm rename field label. */
    const val ALARM_RENAME_FIELD_LABEL = "Label"
    /** Shared Save / Cancel / Keep for alarm dialogs. */
    const val ALARM_SAVE_LABEL = "Save"
    const val ALARM_CANCEL_LABEL = "Cancel"
    const val ALARM_KEEP_LABEL = "Keep"
    /** Connected strap generation names (buzz help / OEM copy). */
    const val STRAP_WHOOP_5_MG = "WHOOP 5/MG"
    const val STRAP_WHOOP_4 = "WHOOP 4.0"
    /** HrvSnapshot scaffold title + subtitle. */
    const val HRV_READING_TITLE = "HRV Reading"
    const val HRV_READING_SUBTITLE = "A still, seated snapshot of your heart-rate variability"
    /** HrvSnapshot phase + bond StatePills. */
    const val HRV_PHASE_READY = "Ready"
    const val HRV_PHASE_CAPTURING = "Capturing"
    const val HRV_PHASE_DONE = "Reading complete"
    const val HRV_STRAP_LIVE = "Strap live"
    const val HRV_STRAP_OFF = "Not connected"
    /** HrvSnapshot close a11y. */
    const val HRV_CLOSE_A11Y = "Close HRV reading"
    /** HrvSnapshot dial unit prefix (MS · RMSSD). */
    const val HRV_MS_UNIT = "MS"
    /** HrvSnapshot Save / Saved / Cancel / again CTAs. */
    const val HRV_SAVE_LABEL = "Save"
    const val HRV_SAVED_LABEL = "Saved"
    const val HRV_CANCEL_LABEL = "Cancel"
    const val HRV_TAKE_ANOTHER_LABEL = "Take another reading"
    /** HrvSnapshot result card. */
    const val HRV_YOUR_READING_OVERLINE = "Your reading"
    const val HRV_SDNN_LABEL = "SDNN"
    const val HRV_MEAN_HR_LABEL = "Mean HR"
    const val HRV_BEATS_LABEL = "Beats"
    const val HRV_MS_CAPTION = "ms"
    const val HRV_BPM_CAPTION = "bpm"
    const val HRV_USED_CAPTION = "used"
    /** Sleep Alarm open cue (legacy glance; unified Alarm embeds full editor). */
    const val ALARM_WAKE_OPEN_CUE = "Sleep → Alarm · turn-back · custom · wind-down"
    /** Open Sleep → Alarm a11y (Today Quick Alarm). */
    const val ALARM_OPEN_WAKE_A11Y = "Open Sleep Alarm"
    /** Soft-window ±15m steppers on Sleep Alarm wake edit. */
    const val ALARM_SHORTEN_15M_LABEL = "−15m"
    const val ALARM_LENGTHEN_15M_LABEL = "+15m"
    /** Live physiology stack overline. */
    const val LIVE_PHYSIOLOGY_OVERLINE = "Live Physiology"
    /** Live Signal Trust connection detail when encrypted bond is up. */
    const val LIVE_CONTROLS_UNLOCKED = "Controls unlocked"
    /** Live Signal Trust rail header. */
    const val LIVE_SIGNAL_TRUST_TITLE = "Signal Trust"
    const val LIVE_SIGNAL_TRUST_OVERLINE = "Proof that the console is current"
    /** Signal Trust tile titles / shared values. */
    const val LIVE_TRUST_HR_TITLE = "Heart rate"
    const val LIVE_TRUST_MISSING = "Missing"
    const val LIVE_TRUST_STREAMING_NOW = "Streaming now"
    const val LIVE_TRUST_NO_ACTIVE_STREAM = "No active stream"
    const val LIVE_TRUST_CONNECTION = "Connection"
    const val LIVE_TRUST_ENCRYPTED = "Encrypted"
    const val LIVE_TRUST_PARTIAL = "Partial"
    const val LIVE_TRUST_OFFLINE = "Offline"
    const val LIVE_TRUST_HISTORY_SYNC = "History sync"
    const val LIVE_TRUST_BATTERY = "Battery"
    const val LIVE_TRUST_WEAR_STATE = "Wear state"
    const val LIVE_TRUST_ON_WRIST = "On wrist"
    const val LIVE_TRUST_OFF_WRIST = "Off wrist"
    /** Live physiology Event proof tile. */
    const val LIVE_PROOF_EVENT_LABEL = "Event"
    /** Sleep Alarm / Sleep tracker travel honesty. */
    const val ALARM_TRAVEL_CLOCK_NOTE =
        "Wake times use this phone’s local clock. After travel or a clock change, confirm the wake day before arming."
    const val ALARM_TRAVEL_NIGHT_NOTE =
        "Nights follow this phone’s local wake day. After travel or DST, a night may look long or short — check the date before editing."
    /** SHIP #92 — one quiet DST cue (same surface as travel note). */
    const val SLEEP_DST_NIGHT_NOTE =
        "DST: tonight may look long or short on the clock — local wake day is still correct."
    /** Sleep chapter — journal prompt + scaffold. */
    const val SLEEP_GOOD_MORNING = "Good morning!"
    const val SLEEP_JOURNAL_PROMPT_BODY =
        "Your night data is in. Logging how you felt helps NOOP learn what drives your best recovery."
    const val SLEEP_OPEN_JOURNAL = "Open Journal"
    const val SLEEP_MAYBE_LATER = "Maybe later"
    // SLEEP_CHAPTER_SUBTITLE lives with REST/Alarm ship cues above.
    /** Sleep marks card + Tools mark verbs. */
    const val SLEEP_MARKS_TITLE = "Sleep marks"
    const val SLEEP_MARKS_OVERLINE = "Tap to log"
    const val SLEEP_GOING_TO_SLEEP = "Going to sleep"
    const val SLEEP_IM_AWAKE = "I'm awake"
    const val SLEEP_TOOLS_OVERLINE = "Nap · Sources · Bed · Wake"
    const val SLEEP_MARK_BED = "Mark bed"
    const val SLEEP_MARK_WAKE = "Mark wake"
    /** Alarm glance → Sleep tracker peek. */
    const val SLEEP_LAST_NIGHT_TRACKER = "Last night · Sleep tracker"
    /** Stage empty / breakdown chrome. */
    const val SLEEP_NO_STAGE_DATA = "No stage data for this night."
    const val SLEEP_NO_STAGE_HISTORY_HINT =
        "This is not an empty Sleep history — ◀/▶ may reach nights with stages, and the ledger/trends stay below when you have history."
    const val SLEEP_NO_STAGE_BREAKDOWN = "No stage breakdown for this night."
    /** Strap offline while browsing banked nights (honest — not inventing live Rest). */
    const val SLEEP_STRAP_OFFLINE_BANK =
        "Strap offline — showing banked nights. Live Rest waits on reconnect."
    /** Date picker chose a wake-day with no stored night. */
    const val SLEEP_NO_NIGHT_FOR_DATE = "No night stored for that wake day."
    /** At the oldest banked night (history exists — not a sync failure). */
    const val SLEEP_OLDEST_IN_BANK = "Oldest night in the bank."
    const val SLEEP_STAGE_BREAKDOWN_TITLE = "Stage breakdown"
    const val SLEEP_STAGE_AWAKE = "Awake"
    const val SLEEP_STAGE_LIGHT = "Light"
    const val SLEEP_STAGE_DEEP = "Deep"
    const val SLEEP_STAGE_REM = "REM"
    /** Why-this-sleep + nap about. */
    const val SLEEP_WHY_THIS = "Why this sleep?"
    const val SLEEP_ABOUT_MAIN = "About your main sleep"
    const val SLEEP_ABOUT_NAP = "About this nap"
    /** Sleep window row labels. */
    const val SLEEP_ASLEEP_LABEL = "Asleep"
    const val SLEEP_WOKE_LABEL = "Woke"
    /** Delete session confirm (Cancel shares ALARM_CANCEL_LABEL). */
    const val SLEEP_DELETE_LABEL = "Delete"
    /** Stage timeline headline overlines + tap hint. */
    const val SLEEP_HOURS_OF_SLEEP = "HOURS OF SLEEP"
    const val SLEEP_RESTORATIVE_SLEEP = "RESTORATIVE SLEEP"
    const val SLEEP_TAP_STAGE_HINT = "Tap a stage to highlight it on the chart"
    /** Live HR vessel overline while pack is charging. */
    const val LIVE_HR_CHARGING_OVERLINE = "Heart Rate · charging"
    /** Live link-state chrome (Console / datastream / Signal Trust short). */
    const val LIVE_LINK_STREAMING = "Streaming"
    const val LIVE_LINK_CONNECTED = "Connected"
    const val LIVE_LINK_DISCONNECTED = "Disconnected"
    const val LIVE_LINK_SCANNING = "Scanning"
    const val LIVE_LINK_SCANNING_ELLIPSIS = "Scanning…"
    const val LIVE_LINK_OFFLINE = "Offline"
    /** Settings / More / Profile / Test Centre chapter. */
    const val SETTINGS_TITLE = "Settings"
    const val SETTINGS_SUBTITLE = "Your numbers, your strap, and how NOOP works. All on this phone."
    const val SETTINGS_PROFILE_TITLE = "Profile"
    const val SETTINGS_PROFILE_BLURB =
        "Your age, size and zones — the same numbers Fitness Age and Charge lean on. Keep them honest; they never leave this phone."
    const val SETTINGS_UNITS_TITLE = "Units & time"
    const val SETTINGS_UNITS_BLURB =
        "How you like distances, weight, Effort scale and the clock. Stored data stays SI — this only changes what you see. Next to Appearance below."
    const val SETTINGS_STRAP_TITLE = "Strap"
    const val SETTINGS_STRAP_BLURB =
        "Pair your WHOOP here over Bluetooth — no WHOOP app login, no cloud in the middle."
    const val SETTINGS_STRAP_STREAMING = "Streaming"
    const val SETTINGS_STRAP_SYNCING = "Syncing"
    const val SETTINGS_STRAP_READY = "Ready"
    const val SETTINGS_STRAP_AWAITING = "Awaiting"
    const val SETTINGS_STRAP_SCANNING =
        "Looking for your WHOOP… radio link or the official app may be holding it."
    const val SETTINGS_STRAP_PAIRED =
        "Your strap is paired and sending data. Open Live for a real-time heart rate."
    const val SETTINGS_STRAP_HANDSHAKE =
        "Connected. Finishing the secure pairing handshake…"
    const val SETTINGS_STRAP_BONDED_IDLE =
        "Previously paired but not currently connected. Re-scan to reconnect."
    const val SETTINGS_STRAP_NONE =
        "No strap linked. Put your WHOOP nearby and tap Re-scan."
    const val SETTINGS_STRAP_ALONGSIDE_NOTE =
        "Alongside WHOOP app: open HR, R-R and battery only — no private bond."
    const val SETTINGS_CONNECTIONS_TITLE = "Connections"
    const val SETTINGS_CONNECTIONS_BLURB =
        "Strap, Health Connect and alerts in one place — the pipes that keep Today honest."
    const val SETTINGS_BACKUP_NEVER_BANNER =
        "No backup yet — export a .noopbak when you have a minute."
    const val SETTINGS_BACKUP_NEVER_CTA = "Export backup"
    /** Quiet reconnect policy one-liner near Live connection status (#267). */
    const val SETTINGS_RECONNECT_POLICY =
        "Drops auto-retry with backoff. Pause stops retries."
    const val SETTINGS_POWER_TITLE = "Power saving"
    const val SETTINGS_POWER_BLURB =
        "When the strap battery is low and draining, ease the load. Off until you want it; never while charging."
    const val SETTINGS_POWER_WAITING_SOC =
        "Waiting for strap battery · sync stays every 15 min until SoC is known"
    const val SETTINGS_POWER_CHARGING_PAUSED =
        "Charging · power saving paused (never eases while charging)"
    const val MORE_TITLE = "More"
    const val MORE_SUBTITLE = "Cycle · Lab Book · train · settings"
    const val MORE_SEARCH_PLACEHOLDER = "Search More"
    const val MORE_BODY_PIN_TITLE = "For your body"
    const val MORE_BODY_PIN_BLURB =
        "Cycle calendar, Lab Book (cuff BP), and step training — one tap each."
    const val TEST_CENTRE_TITLE = "Test Centre"
    const val TEST_CENTRE_SUBTITLE =
        "Turn on a test for the thing that's wrong, wear the strap, then tap Report. Everything stays on this phone."
    const val GOALS_OPEN_TEST_CENTRE = "Open Test Centre"
    const val GOALS_OPEN_SETTINGS = "Open Settings"
    const val APPLE_HEALTH_EMPTY_TITLE = "Nothing imported yet"
    const val APPLE_HEALTH_OPEN_DATA_SOURCES = "Open Data Sources"
}

/** Settings Strap status pill verb (Fable 200 #103). */
fun settingsStrapStatusTitle(bonded: Boolean, connected: Boolean): String = when {
    bonded && connected -> LifeChapterLacquer.SETTINGS_STRAP_STREAMING
    connected -> LifeChapterLacquer.SETTINGS_STRAP_SYNCING
    bonded -> LifeChapterLacquer.SETTINGS_STRAP_READY
    else -> LifeChapterLacquer.SETTINGS_STRAP_AWAITING
}

/**
 * Settings Strap detail — scan wins over bond/connect.
 * [alongsideWhoopApp] appends open-HR honesty when fully paired (no private-bond claim).
 */
fun settingsStrapStatusDetail(
    bonded: Boolean,
    connected: Boolean,
    scanning: Boolean,
    alongsideWhoopApp: Boolean = false,
): String {
    val base = when {
        scanning -> LifeChapterLacquer.SETTINGS_STRAP_SCANNING
        bonded && connected -> LifeChapterLacquer.SETTINGS_STRAP_PAIRED
        connected -> LifeChapterLacquer.SETTINGS_STRAP_HANDSHAKE
        bonded -> LifeChapterLacquer.SETTINGS_STRAP_BONDED_IDLE
        else -> LifeChapterLacquer.SETTINGS_STRAP_NONE
    }
    return if (alongsideWhoopApp && bonded && connected && !scanning) {
        "$base ${LifeChapterLacquer.SETTINGS_STRAP_ALONGSIDE_NOTE}"
    } else {
        base
    }
}

/** Power-saving armed/easing line — honest when SoC unknown or charging. */
fun settingsPowerSavingStatus(
    easingNow: Boolean,
    batteryPct: Double?,
    charging: Boolean?,
    thresholdPct: Int,
): String = when {
    batteryPct == null -> LifeChapterLacquer.SETTINGS_POWER_WAITING_SOC
    charging == true -> LifeChapterLacquer.SETTINGS_POWER_CHARGING_PAUSED
    easingNow ->
        "Easing now · strap ${batteryPct.toInt()}% discharging · sync every 45 min"
    else ->
        "Armed · eases when strap ≤${thresholdPct}% and not charging"
}

/** Sleep stage display name from lacquer (awake/light/deep/rem keys). */
fun sleepStageLabel(key: String): String = when (key.lowercase()) {
    "awake" -> LifeChapterLacquer.SLEEP_STAGE_AWAKE
    "light" -> LifeChapterLacquer.SLEEP_STAGE_LIGHT
    "deep" -> LifeChapterLacquer.SLEEP_STAGE_DEEP
    "rem" -> LifeChapterLacquer.SLEEP_STAGE_REM
    else -> key.replaceFirstChar { it.uppercase() }
}

/** Stages section empty lead when last night has no stages but history exists. */
fun sleepNoStageDataWithHistoryHint(): String =
    "${LifeChapterLacquer.SLEEP_NO_STAGE_DATA} ◀ older nights may still show stages and the ledger."

/** Efficiency footnote under stage chart (TIB vs on-device). */
fun sleepEfficiencyDisclaimer(fromTib: Boolean): String = if (fromTib) {
    "Efficiency = asleep ÷ time in bed (no stage SE stored) — not WHOOP sleep efficiency."
} else {
    "Efficiency = asleep ÷ time in bed from on-device stages — not WHOOP sleep efficiency."
}

/** Ledger efficiency note (shared honesty). */
fun sleepEfficiencyLedgerNote(): String =
    "Efficiency uses asleep ÷ time in bed — on-device stages when shown above."

/** Typical-duration footnote under Hours / Restorative. */
fun sleepTypicallyCaption(durationLabel: String): String = "typically $durationLabel"

/** Live HR vessel overline (charging vs idle). */
fun liveHrOverline(charging: Boolean): String =
    if (charging) LifeChapterLacquer.LIVE_HR_CHARGING_OVERLINE
    else LifeChapterLacquer.LIVE_TRUST_HR_TITLE

/** Live short link pill for bond/scan/offline chrome. */
fun liveShortLinkLabel(bonded: Boolean, connected: Boolean, scanning: Boolean): String = when {
    bonded -> "Bonded"
    connected -> LifeChapterLacquer.LIVE_LINK_CONNECTED
    scanning -> LifeChapterLacquer.LIVE_LINK_SCANNING
    else -> LifeChapterLacquer.LIVE_LINK_OFFLINE
}

/** Custom-alarms card title with optional on-count. */
fun alarmCustomAlarmsTitle(enabledCount: Int, total: Int): String = buildString {
    append("Custom alarms")
    if (total > 0) {
        append(" · ")
        append(if (enabledCount == total) "$enabledCount on" else "$enabledCount of $total on")
    }
}

/** Custom-alarms card footnote — soft window vs customs vs strap. */
fun alarmCustomAlarmsHelp(): String =
    "Soft wake window finds a lighter moment · customs are exact phone times · strap buzz is separate. " +
        "One soft window + optional custom times — not many competing masters."

/** Exact-alarm permission warn when enabled customs cannot fire. */
fun alarmCustomExactOffCaption(): String =
    "Exact-alarm access is off, so enabled custom alarms cannot fire. Open system settings to restore them."

/** Empty custom-alarms body. */
fun alarmCustomEmptyCaption(): String =
    "No customs yet. Soft window covers nights in; add one for a hard weekend or travel time."

/** At-capacity custom-alarms footnote. */
fun alarmCustomLimitCaption(max: Int): String = "Limit $max custom alarms"

/** Default label for a newly added custom alarm. */
fun alarmDefaultCustomLabel(index: Int): String = "Alarm $index"

/** Remove-custom confirm title. */
fun alarmRemoveConfirmTitle(label: String): String = "Remove $label?"

/** Remove-custom confirm body. */
fun alarmRemoveConfirmBody(): String =
    "This deletes the fixed phone alarm. Soft wake window is unchanged."

/** Strap-firmware toggle help (vs soft-window companion buzz). */
fun alarmStrapFirmwareHelp(): String =
    "Standalone firmware alarm on the strap (works with NOOP closed). Different from “${LifeChapterLacquer.ALARM_BUZZ_STRAP_LABEL}” above, which only follows the phone soft window while NOOP is open."

/** Tip while strap firmware wake is off. */
fun alarmStrapFirmwareOffTip(): String =
    "Turn on to set weekdays and per-day wake times on the strap."

/** Strap firmware Wake-at TimeChip a11y. */
fun alarmStrapWakeTimeA11y(): String = "Strap alarm wake time"

/** Strap firmware armed / connect honesty (4.0 vs 5/MG). */
fun alarmStrapArmedCaption(whoop5: Boolean, bonded: Boolean): String = when {
    whoop5 && bonded ->
        "Armed on the strap itself with the acknowledged 5/MG command. Keep the phone alarm on as backup for anything you truly can't miss."
    whoop5 ->
        "Connect your strap to arm this; it's set on the strap's own firmware alarm. Keep the phone alarm on as backup."
    bonded ->
        "Armed on the strap itself, so it can buzz at your wake time even if your phone is asleep or NOOP is closed. Sends the exact alarm command the official app sends, confirmed buzzing on a real WHOOP 4.0 (community wire capture + on-device test, #535). Keep a backup alarm for anything you truly can't miss."
    else ->
        "Connect your strap to arm this; it's set on the strap's own firmware alarm. Confirmed working on WHOOP 4.0; still experimental on 5.0 and MG. Keep a backup alarm for anything you truly can't miss."
}

/** ExplanationCard body under How smart wake. */
fun alarmHowSmartWakeBody(): String =
    "Inside the window, a rise from the lowest stable heart-rate readings may cue an early wake. " +
        "That is a coarse HR cue, not sleep-stage detection. If the strap is not streaming, only the " +
        "guaranteed end-of-window alarm fires."

/** Sleep WindowCard wake TimeChip a11y. */
fun alarmWakeUpTimeA11y(): String = "Wake up time"

/** HrvSnapshot methodology overline. */
fun hrvHowMeasuredOverline(): String = "How this is measured"

/** HrvSnapshot methodology body (Task Force RMSSD honesty). */
fun hrvMethodologyBody(): String =
    "A 60-second snapshot of your beat-to-beat (R-R) intervals from the strap, cleaned " +
        "(range and ectopic-beat filtering) before computing RMSSD the same way your " +
        "overnight HRV is computed."

/** HrvSnapshot not-bonded hint. */
fun hrvNeedsRrStreamCaption(): String =
    "An HRV reading needs the live R-R stream. Open the Live screen and connect your strap, " +
        "then come back."

/** Live Signal Trust when encrypted + R-R locked. */
fun liveRrLockedTrustSummary(): String =
    "Encrypted stream · live R-R locked · deep controls available."

/** Live connection-mode detail when R-R locked. */
fun liveRrLockedModeDetail(): String = "Full strap stream · R-R locked."

/** Live Signal Trust while encrypted but still awaiting optical R-R lock. */
fun liveEncryptedAwaitTrustSummary(lockPct: Int?): String =
    "Encrypted stream · ${opticalLockTrustBrief(lockPct)} · deep controls available."

/** Live Signal Trust while HR streams before R-R lock (MG often 30–60s). */
fun liveHrFlowingTrustSummary(lockPct: Int?): String =
    "Live HR flowing · ${opticalLockTrustBrief(lockPct)} (often 30–60s on MG)."

/** Live connection-mode detail while encrypted but still awaiting R-R. */
fun liveEncryptedAwaitModeDetail(lockPct: Int?): String =
    "Full strap stream · ${opticalLockTrustBrief(lockPct)}…"

/** Live connection-mode detail while HR is live during optical wait. */
fun liveHrOpticalModeDetail(lockPct: Int?): String =
    "Heart rate live · ${opticalLockTrustBrief(lockPct)}."

/** Live Signal Trust while HR flows without encrypted bond. */
fun livePartialBondTrustSummary(): String =
    "Live heart rate is flowing; full strap controls need an encrypted bond."

/** Live Signal Trust while radio is up but stream not trusted yet. */
fun liveAwaitingStreamTrustSummary(): String =
    LifeChapterLacquer.HEALTH_AWAITING_SAMPLE

/** Live Signal Trust empty-state (offline). */
fun liveOfflineTrustSummary(): String =
    LifeChapterLacquer.HEALTH_AWAITING_STRAP

/** Stress EMPTY + live: honest banking progress toward first daytime tip (#167). */
fun stressBankingQuietHrProgress(samples: Int, need: Int = 75): String {
    val n = samples.coerceIn(0, need)
    return "Banking quiet HR · ~$n of $need for first tip"
}

/** Live connection-mode detail while HR stream is trusted/active. */
fun liveHrActiveModeDetail(): String = "Heart rate stream is active."

/** Live connection-mode detail while radio is up but untrusted. */
fun liveRadioUntrustedModeDetail(): String =
    "Radio connected, stream not yet trusted."

/** Live connection-mode detail while offline. */
fun liveNoStreamModeDetail(): String = "No live stream."

/** Signal Trust connection detail when bond is not full (ring vs open HR). */
fun liveConnectionBondDetail(ringStreaming: Boolean): String =
    if (ringStreaming) "Live stream, no WHOOP bond" else "Standard HR is not a full bond"

/** Buzz-strap / OEM copy: name the connected generation. */
fun alarmStrapGenerationName(whoop5: Boolean): String =
    if (whoop5) LifeChapterLacquer.STRAP_WHOOP_5_MG else LifeChapterLacquer.STRAP_WHOOP_4

/** HrvSnapshot dial unit while capturing / done (MS · RMSSD). */
fun hrvMsRmssdUnit(): String =
    "${LifeChapterLacquer.HRV_MS_UNIT} ${LifeChapterLacquer.RMSSD_CHIP_LABEL}"

/** HrvSnapshot dial sub-line during capture. */
fun hrvCapturingSub(secondsLeft: Int, beatCount: Int): String =
    "${secondsLeft}s left · $beatCount beats"

/** HrvSnapshot dial a11y while capturing. */
fun hrvDialCapturingA11y(value: String, sub: String): String =
    "${LifeChapterLacquer.HRV_PHASE_CAPTURING}. $value milliseconds ${LifeChapterLacquer.RMSSD_CHIP_LABEL} so far. $sub."

/** HrvSnapshot primary CTA by phase. */
fun hrvPrimaryLabel(capturing: Boolean, done: Boolean): String = when {
    capturing -> LifeChapterLacquer.HRV_CANCEL_LABEL
    done -> LifeChapterLacquer.HRV_TAKE_ANOTHER_LABEL
    else -> LifeChapterLacquer.HRV_TAKE_READING_LABEL
}

/** HrvSnapshot instruction under the dial. */
fun hrvInstruction(capturing: Boolean, done: Boolean, bonded: Boolean, failed: Boolean): String = when {
    capturing -> "Sit still, breathe normally. Keep your wrist relaxed and steady."
    done && failed -> hrvNotEnoughBeatsLead()
    done -> "Done. Save this reading to keep it in your trends."
    bonded -> "Sit still and breathe normally. Tap below to take a 60-second reading."
    else -> "Connect your strap on the Live screen to take a reading."
}

/** Shared lead when filtering left too few clean beats. */
fun hrvNotEnoughBeatsLead(): String = "Not enough clean beats - sit still and try again."

/** HrvSnapshot result body when filtering left too few beats. */
fun hrvInsufficientBeatsBody(nClean: Int, nInput: Int, need: Int): String =
    "${hrvNotEnoughBeatsLead()} $nClean of $nInput beats survived filtering (need $need)."

/** Sleep Alarm / Today — open Sleep → Alarm TalkBack. */
fun alarmOpenWakeSettingsA11y(
    customSummary: String? = null,
    nextCustomLabel: String? = null,
    extrasSummary: String? = null,
): String = buildString {
    append(LifeChapterLacquer.ALARM_OPEN_WAKE_A11Y)
    append('.')
    if (customSummary != null) append(" $customSummary.")
    if (nextCustomLabel != null) append(" $nextCustomLabel.")
    if (extrasSummary != null) append(" $extrasSummary.")
}

/** Soonest enabled custom alarm cue under Extras. */
fun alarmNextCustomCue(timeLabel: String): String = "next $timeLabel"

/** Live / Health RMSSD chip value with shared ms caption. */
fun liveRmssdMsValue(rmssdMs: Int): String =
    "$rmssdMs ${LifeChapterLacquer.HRV_MS_CAPTION}"

/** Live proof tile — last R-R interval with shared ms caption. */
fun liveRrIntervalMsValue(ms: Int): String =
    "$ms ${LifeChapterLacquer.HRV_MS_CAPTION}"

/** Shared Sip strip a11y — Today + Nutrition hydration jump (litres). */
fun hydrationSipA11y(totalMl: Double, goalMl: Int): String =
    "Hydration ${"%.1f".format(totalMl / 1000.0)} of ${"%.1f".format(goalMl / 1000.0)} litres. Sip adds 250 millilitres."

/** Goal-met footnote under Sip litres — Today + Nutrition. */
fun hydrationGoalMetCaption(): String = "Goal met · keep sipping if you like"

/**
 * Today Fuel peek primary line — kcal · P/C/F · meal count (Nutrition cohesion).
 */
fun fuelPeekLine(
    dayKcal: Int,
    mealCount: Int,
    proteinG: Int,
    carbsG: Int,
    fatG: Int,
): String = when {
    mealCount <= 0 -> "Log meals · on-device"
    proteinG > 0 || carbsG > 0 || fatG > 0 -> {
        val bits = buildList {
            add("$dayKcal kcal")
            if (proteinG > 0) add("${proteinG}g P")
            if (carbsG > 0) add("${carbsG}g C")
            if (fatG > 0) add("${fatG}g F")
            add("$mealCount")
        }
        bits.joinToString(" · ")
    }
    else -> "$dayKcal kcal · $mealCount logged"
}

/** Today Fuel peek contentDescription. */
fun fuelPeekA11y(dayKcal: Int, mealCount: Int, proteinG: Int, carbsG: Int, fatG: Int): String {
    if (mealCount <= 0) return "Fuel · log meals on-device. Opens Nutrition."
    val macros = buildList {
        if (proteinG > 0) add("${proteinG}g protein")
        if (carbsG > 0) add("${carbsG}g carbs")
        if (fatG > 0) add("${fatG}g fat")
    }
    return buildString {
        append("Fuel $dayKcal kilocalories")
        if (macros.isNotEmpty()) {
            append(" · ")
            append(macros.joinToString(" · "))
        }
        append(" · $mealCount logged. Opens Nutrition.")
    }
}

/** Cycle soft physiology bit — +fuel / easy · display soft (never edits banked Charge). */
fun cycleSoftBitCaption(
    needsMoreFuel: Boolean,
    takeItEasy: Boolean,
    recoveryCapacityFactor: Double?,
): String = when {
    needsMoreFuel -> " · +fuel"
    takeItEasy && recoveryCapacityFactor != null ->
        " · easy · display soft ×${"%.2f".format(recoveryCapacityFactor)}"
    else -> ""
}

/** Cycle card a11y — phase · day · soft cue. */
fun cycleCardA11y(phaseLabel: String, dayBit: String, softBit: String): String =
    "Cycle $phaseLabel$dayBit$softBit. Opens cycle calendar."

/**
 * Shared Armed / Off / May drift pill label — Today Quick + Alarm glance.
 * Armed state stays one word so the pill reads at a glance.
 */
fun alarmArmStatusLabel(enabled: Boolean, canExact: Boolean): String = when {
    !enabled -> LifeChapterLacquer.ALARM_STATUS_OFF
    !canExact -> LifeChapterLacquer.ALARM_STATUS_MAY_DRIFT
    else -> LifeChapterLacquer.ALARM_STATUS_ARMED
}

/**
 * Shared Armed / Off / may-drift pill color — Rest when honest, Effort when drifting.
 */
fun alarmArmStatusColor(enabled: Boolean, canExact: Boolean): Color = when {
    !enabled -> Palette.textTertiary
    !canExact -> Palette.effortColor
    else -> Palette.restColor
}

/**
 * Under-clock bedtime caption — next fire / Moon quiet / Exact-off (Today + Alarm).
 * Armed: bare [soonest] only (aim line no longer repeats it).
 */
fun alarmBedtimeFootCaption(
    enabled: Boolean,
    canExact: Boolean,
    soonest: String?,
): String? = when {
    enabled && !canExact -> "Exact off · Settings"
    enabled && soonest != null -> soonest
    !enabled -> alarmMoonQuietCaption()
    else -> null
}

/** Off-state bedtime foot — WindowCard / Alarm glance / Today. */
fun alarmMoonQuietCaption(): String = "Moon quiet · Arm bedtime"

/** Wake settings live subtitle — Off / may-drift / Armed (short). Never "Armed" when exact is off. */
fun alarmWakeLiveSubtitle(enabled: Boolean, canExact: Boolean): String = when {
    !enabled -> "Off · Rest quiet"
    !canExact -> "May drift · allow exact alarms"
    else -> LifeChapterLacquer.ALARM_STATUS_ARMED
}

/** Deadline honesty line under Alarm glance. */
fun alarmDeadlineCue(enabled: Boolean, canExact: Boolean, deadlineLabel: String): String = when {
    enabled && canExact -> "By $deadlineLabel"
    enabled -> LifeChapterLacquer.ALARM_STATUS_MAY_DRIFT
    else -> "Arm for deadline"
}

/**
 * Shared aim line under bedtime clock — Today Quick + Alarm glance.
 * Aim + window only; next fire lives in [alarmBedtimeFootCaption] when Armed.
 * [soonest] kept for call-site compatibility / TalkBack — not appended here.
 */
fun alarmGlanceAimCaption(
    aimLabel: String,
    windowMinutes: Int,
    @Suppress("UNUSED_PARAMETER") soonest: String?,
): String = buildString {
    append(aimLabel)
    append(" · ±")
    append(windowMinutes)
    append("m")
}

/** WindowCard / Plan aim · wake range — Wake settings cohesion. */
fun alarmWindowAimCaption(
    aimLabel: String,
    wakeStartLabel: String,
    wakeEndLabel: String,
): String = "$aimLabel · $wakeStartLabel → $wakeEndLabel"

/** Wake settings Arm card plan line — honest when exact alarms off; Rest tone when armed. */
fun alarmArmedPlanCaption(canExact: Boolean): String = when {
    !canExact -> "May drift · allow exact"
    else -> "Quiet until wake — window + phone deadline set."
}

/** Exact-alarm early warn — Wake settings / Sleep glance before Arm. */
fun alarmExactEarlyWarn(): String =
    "Allow exact alarms so wake can protect deep sleep — else may drift."

/** Alarms vs notifications vs Cycle — one glossary line. */
fun alarmTaxonomyGlossary(): String =
    "Alarms wake you · Notifications are system alerts · Cycle reminders are separate."

/**
 * Alarm Arm strap honesty line — glance Arm panel.
 * [whoop5] only matters when buzz is on and strap can buzz.
 */
fun alarmStrapStatusCaption(
    canExact: Boolean,
    connected: Boolean,
    canBuzz: Boolean,
    buzzWhoop: Boolean,
    whoop5: Boolean,
): String {
    val strapLine = when {
        !connected -> "Strap offline · phone still fires"
        !canBuzz && buzzWhoop -> "Buzz on · pair motor · Test ≠ arm"
        !canBuzz -> "Live HR · Test may pair"
        buzzWhoop && whoop5 -> "Buzz armed · MG experimental"
        buzzWhoop -> "Buzz armed · phone backs up"
        else -> "Buzz off · phone deadline"
    }
    return buildString {
        if (!canExact) append("Exact off · may drift. ")
        append(strapLine)
    }
}

/** Dual buzz models footnote — Wake settings Extras. */
fun alarmDualBuzzCaption(): String =
    "Soft-window buzz (“${LifeChapterLacquer.ALARM_BUZZ_STRAP_LABEL}”) needs NOOP open. " +
        "Firmware (“${LifeChapterLacquer.ALARM_STRAP_WAKE_TITLE}”) can fire closed. Phone deadline backs both."

/** Plan card foot — aim · earliest wake · night count. */
fun alarmPlanFootCaption(
    aimLabel: String,
    wakeLabel: String,
    nightCount: Int,
): String = "$aimLabel · $wakeLabel earliest · $nightCount nights"

/** WindowCard / bedtime clock TalkBack — status · bed · aim. */
fun alarmBedtimeClockA11y(
    statusLabel: String,
    bedLabel: String,
    aimCaption: String,
): String = "$statusLabel. Bedtime $bedLabel. $aimCaption."

/** RRStrip short wait when parent owns opticalLockCaption. */
fun rrOpticalAwaitCaption(): String = "Waiting for R-R intervals · optical lock…"

/** Live / Health LOCK chip TalkBack. */
fun opticalLockChipA11y(lockPct: Int?): String =
    lockPct?.let { "Optical lock $it percent" } ?: "Optical lock in progress"

/** Proof / Trust value — "67% lock" / "lock…" / null when idle. */
fun opticalLockPctLabel(lockPct: Int?, awaiting: Boolean = true): String? = when {
    !awaiting -> null
    lockPct != null -> "$lockPct% lock"
    else -> "lock…"
}

/** Datastream MiniStat compact — "67%" / "lock…" / null. */
fun opticalLockCompactLabel(lockPct: Int?, awaiting: Boolean = true): String? = when {
    !awaiting -> null
    lockPct != null -> "$lockPct%"
    else -> "lock…"
}

/** Signal Trust R-R value while locking — "67% lock" / "Locking…". */
fun opticalLockTrustValue(lockPct: Int?): String =
    lockPct?.let { "$it% lock" } ?: "Locking…"

/** Trust / connection-mode phrase — "optical lock 67%" or "optical lock for R-R". */
fun opticalLockTrustBrief(lockPct: Int?): String =
    lockPct?.let { "optical lock $it%" } ?: "optical lock for R-R"

/** RrFeelProgressHairline TalkBack. */
fun rrFeelHairlineA11y(
    beatCount: Int,
    need: Int = LifeChapterLacquer.RR_FEEL_NEED,
): String = "$beatCount of $need beats for feel RMSSD"

/** RRStrip feel-ready line — beats · last ms · feel RMSSD. */
fun rrFeelReadyCaption(beatCount: Int, lastMs: List<Int>, feelRmssdMs: Double): String =
    "$beatCount beats · " + lastMs.takeLast(5).joinToString(" · ") +
        " ms · feel RMSSD ${feelRmssdMs.roundToInt()}"

/** Live datastream while RAW packets arrive before type-40. */
fun type40RawAwaitCaption(rawPackets: Int): String =
    "WHOOP packets · $rawPackets RAW this link · waiting type-40"

/** Wake settings exact-timing protect row. */
fun alarmExactProtectCaption(): String =
    "Exact off — wake may drift until allowed."

/** OEM deadline tip title / body / a11y. */
fun alarmOemTipTitle(): String = "Phone deadline reliability"

fun alarmOemTipBody(): String =
    "Keep exact alarms + notifications on. Unrestrict NOOP if OEM battery delay mornings."

fun alarmOemTipA11y(): String =
    "${alarmOemTipTitle()}. Opens exact alarm settings."

/** Wake-off empty paragraph — why an alarm helps Sleep recovery. */
fun alarmWhyHelpsCaption(): String =
    "A gentle wake window protects deep sleep; the phone deadline keeps mornings honest. " +
        LifeChapterLacquer.ALARM_EXACT_SLEEP_CONTEXT

/** After exact permission granted but wake still Off. */
fun alarmExactAllowedReArmCaption(): String =
    "Exact allowed · Arm to schedule"

/** Armed strap soft-window honesty under Arm. */
fun alarmStrapSoftWindowCaption(): String =
    "Buzz at earliest · phone nearby for soft window"

/** Phone deadline vs strap reconnect honesty. */
fun alarmDeadlineSurviveCaption(): String =
    "Deadline survives reboot · strap needs reconnect"

/** Test-buzz pair mode line — Exclusive / Alongside / offline. */
fun alarmPairModeCaption(
    connected: Boolean,
    alongside: Boolean,
    whoop5: Boolean = false,
): String = when {
    !connected && whoop5 -> "Reconnect your strap, then tap Test buzz."
    !connected -> "Connect strap to test buzz"
    alongside -> "Alongside WHOOP · Test may share link"
    else -> "Exclusive NOOP · Test uses this link"
}

/** Notifications-off deadline warn. */
fun alarmNotifsOffCaption(): String =
    "Notifications off — deadline cannot sound"

/** Open notification settings a11y — Armed / notifs-off row. */
fun alarmOpenNotifSettingsA11y(): String = "Open notification settings"

/** Buzz wall-clock vs wake-arm one-liner (Automations / Settings). */
fun alarmBuzzWallClockHint(): String =
    "Wall-clock haptics on the strap — not wake arm."

/** Wake-rested plain threshold line (Charge OR sleep-need %; hard deadline stands). */
fun alarmWakeRestedPlain(chargeThreshold: Int, sleepNeedPct: Int): String =
    "Early buzz only if Charge ≥ $chargeThreshold OR sleep ≥ $sleepNeedPct%; hard deadline still fires."

/** Sleep empty — title. */
fun sleepEmptyTitle(syncing: Boolean): String =
    if (syncing) "Pulling nights from the strap…" else "No nights here yet"

/** Sleep empty — stepped next actions (never invents stages). */
fun sleepEmptySteps(syncing: Boolean): String =
    if (syncing) {
        "Stages and Rest fill in when the offload finishes."
    } else {
        "1. Wear overnight → 2. Sync → 3. Optional: Import. NOOP never invents a night."
    }

/** Sleep empty — wear primary cue. */
fun sleepEmptyWearCue(): String = "Wear overnight so Rest can score"

/** Sleep empty — Import CTA. */
fun sleepEmptyImportCta(): String = "Import WHOOP export"

/** Sleep empty — Alarm secondary CTA. */
fun sleepEmptyAlarmCta(): String = "Set Alarm"

/** Wind-down fire-time footnote. */
fun alarmWindDownFireCaption(enabled: Boolean, fireLabel: String, leadMinutes: Int): String =
    if (enabled) {
        "Around $fireLabel (wake − need − ${leadMinutes}m)"
    } else {
        "On: nudge around $fireLabel from earliest wake"
    }

/** Exact-settings open a11y — Sleep glance / Today / WindowCard / Exact warn. */
fun alarmOpenExactSettingsA11y(): String = "Open exact alarm settings"

/** Toast after Arm denied without exact permission. */
fun alarmExactThenArmToast(): String =
    "Allow exact alarms so wake can protect deep sleep, then Arm again."

/** WindowCard bedtime overline — needs exact vs recommended. */
fun alarmBedOverline(enabled: Boolean, canExact: Boolean): String = when {
    enabled && !canExact -> LifeChapterLacquer.ALARM_BED_NEEDS_EXACT
    else -> LifeChapterLacquer.ALARM_BED_RECOMMENDED
}

/** WindowCard — open Settings to guarantee deadline. */
fun alarmWindowGuaranteeCaption(deadlineLabel: String): String =
    "Settings to guarantee $deadlineLabel"

/** WindowCard — backup phone deadline honesty. */
fun alarmWindowBackupCaption(deadlineLabel: String): String =
    "Backup at $deadlineLabel if Bluetooth drops"

/** Arm / Off toggle TalkBack — Today Quick + Alarm glance. */
fun alarmArmToggleA11y(enabled: Boolean): String =
    if (enabled) "Turn off wake alarm" else "Arm wake alarm"

/** Strap buzz toggle TalkBack. */
fun alarmStrapBuzzToggleA11y(on: Boolean): String =
    if (on) "Strap buzz on. Tap to turn off." else "Strap buzz off. Tap to arm strap buzz."

/** Test buzz TalkBack. */
fun alarmTestBuzzA11y(): String = "Test one-shot strap buzz. Not wake arm."

/** Buzz-the-time TalkBack. */
fun alarmBuzzTimeA11y(): String = "Buzz wall-clock time on strap. Not wake arm."

/** Connect-strap toast — Test buzz / Buzz time. */
fun alarmConnectStrapToast(): String = "Connect the strap first."

/** Turn-back Extras toggle TalkBack. */
fun alarmTurnBackA11y(on: Boolean): String =
    if (on) "Turn-back on. Tap to turn off." else "Turn-back off. Tap to watch post-wake HR drop."

/** Wake rested Extras toggle TalkBack. */
fun alarmWakeRestedA11y(on: Boolean): String =
    if (on) "Wake when rested on. Tap to turn off."
    else "Wake when rested off. Tap to enable early Charge wake."

/** Wind-down Extras toggle TalkBack. */
fun alarmWindDownToggleA11y(on: Boolean): String =
    if (on) "Wind-down on. Tap to turn off." else "Wind-down off. Tap to enable evening nudge."

/** Extras Turn-back / Wake-rested foot when either is on. */
fun alarmExtrasCueFoot(turnBack: Boolean, wakeRested: Boolean): String = buildString {
    if (turnBack) append("Turn-back on. ")
    if (wakeRested) append("Wake-rested on. ")
    append("Deadline still stands.")
}

/** Wake Off strap readiness under Arm panel. */
fun alarmStrapOffCaption(connected: Boolean, canBuzz: Boolean): String = when {
    !connected -> "Strap offline · phone fires when Armed"
    !canBuzz -> "Live HR · Test may pair"
    else -> "Strap ready · phone deadline when Armed"
}

/** Health hero subtitle when HR is derived from R-R. */
fun rrEstimatedHrCaption(): String = "Estimated from R-R interval"

/** Live Proof mean R-R while bank has intervals. */
fun rrProofMeanCaption(meanMs: Int): String =
    "~$meanMs ${LifeChapterLacquer.HRV_MS_CAPTION}"

/** Live / Health LOCK chip big % readout. */
fun opticalLockChipPct(lockPct: Int): String = "$lockPct%"

/** Today Health empty-chips honesty (no optical path). Short — no manifesto clutter. */
fun todayVitalsHonestyCaption(hasHr: Boolean): String = when {
    hasHr -> "Live HR · SpO₂/BP when banked"
    else -> "Wear strap for live vitals"
}

/** RRStrip climb + trailing ms while 1..<need beats. */
fun rrFeelClimbWithMsCaption(beatCount: Int, lastMs: List<Int>): String {
    val climb = rrFeelClimbCaption(beatCount)
    return if (beatCount >= 2 && lastMs.isNotEmpty()) {
        climb + " · " + lastMs.takeLast(5).joinToString(" · ") + " ${LifeChapterLacquer.HRV_MS_CAPTION}"
    } else {
        climb
    }
}

/** Sleep glance bedtime heading TalkBack. */
fun alarmGlanceBedA11y(
    statusLabel: String,
    bedLabel: String,
    aimLabel: String,
    wakeLabel: String,
    soonest: String?,
): String = buildString {
    append(statusLabel)
    append(". Recommended bedtime ")
    append(bedLabel)
    append(". Aim for ")
    append(aimLabel)
    append(". Wake up ")
    append(wakeLabel)
    if (soonest != null) {
        append(". ")
        append(soonest)
    }
}

/** Test-buzz result toast — offline reconnect / pair prompt / sent. */
fun alarmTestBuzzToast(
    canBuzz: Boolean,
    whoop5: Boolean,
    connected: Boolean = true,
): String = when {
    whoop5 && !connected -> "Reconnect your strap, then tap Test buzz."
    !connected -> alarmConnectStrapToast()
    !canBuzz -> "Accept the Bluetooth pairing prompt, then tap Test buzz again."
    whoop5 -> "One-shot buzz sent (not wake arm). Watch for a buzz on the strap."
    else -> "One-shot buzz sent (not wake arm). Watch for a buzz on the strap. Phone deadline still fires alone."
}

/** Map engineering Buzz statusNote lines to short user copy (bond / framing / offline). */
fun alarmTestBuzzStatusCaption(note: String): String = when {
    note.contains("not connected", ignoreCase = true) ->
        "Reconnect your strap, then tap Test buzz."
    note.contains("framing", ignoreCase = true) ||
        note.contains("Command protocol", ignoreCase = true) ->
        "Reconnect your strap, then tap Test buzz."
    note.contains("full bond", ignoreCase = true) ||
        note.contains("pairing prompt", ignoreCase = true) ->
        "Accept the Bluetooth pairing prompt, then tap Test buzz again."
    note.contains("Alongside", ignoreCase = true) && note.contains("bond", ignoreCase = true) ->
        "Reconnect your strap, then tap Test buzz."
    note.contains("Strap accepted buzz", ignoreCase = true) ->
        "Strap accepted the buzz. If the wrist is quiet, reconnect exclusive and try again."
    note.contains("Strap rejected buzz", ignoreCase = true) ->
        "Strap didn't buzz — reconnect and try Test buzz again."
    else -> note
}

/** Buzz-the-time success toast (not wake arm). */
fun alarmBuzzTimeToast(): String = "Buzzing wall-clock time on strap (not wake arm)."

/** Extras Off soft-wake honesty (Sleep glance). */
fun alarmSoftWakeOffCaption(): String =
    "Soft window + phone deadline keep mornings honest."

/** Custom alarms foot under Extras — soft window vs custom soonest. */
fun alarmCustomSoonestFoot(customSummary: String, nextCustomLabel: String?): String = buildString {
    append(customSummary)
    if (nextCustomLabel != null) append(" · $nextCustomLabel")
}

/** Window stepper clamp toast — Sleep glance ±. */
fun alarmWindowRangeToast(minMin: Int, maxMin: Int): String =
    "Window is $minMin–$maxMin min"

/** Strap buzz toggle button label. */
fun alarmStrapBuzzLabel(on: Boolean): String =
    if (on) "Strap buzz on" else "Strap buzz"

/** Arm / Turn-off primary button label. */
fun alarmArmButtonLabel(enabled: Boolean): String =
    if (enabled) "Turn off" else "Arm wake"

/** Signal Trust / Stress RMSSD ms readout. */
fun rrRmssdMsCaption(rmssdMs: Double): String =
    "${LifeChapterLacquer.RMSSD_CHIP_LABEL} ${rmssdMs.roundToInt()} ${LifeChapterLacquer.HRV_MS_CAPTION}"

/** Plan card empty — need personal bedtime cue. */
fun alarmPlanEmptyCaption(): String =
    "Record three nights for a personal bedtime cue."

/** Plan card schedule honesty under aim foot. */
fun alarmScheduleCueCaption(): String =
    "Schedule cue, not a health target."

/** Extras → Charge vessel jump (Sleep glance). */
fun alarmOpenChargeOnTodayLabel(): String = "Open Charge on Today"

/** Today Quick Alarm primary — shorter than [alarmArmButtonLabel]. */
fun alarmQuickArmButtonLabel(enabled: Boolean): String =
    if (enabled) "Turn off" else "Arm"

/** Custom alarms add CTA — empty list vs has rows. */
fun alarmAddCustomAlarmLabel(empty: Boolean): String =
    if (empty) "Add a fixed time" else "Add alarm"

/** Wake settings Arm — window start row title. */
fun alarmWakeWindowStartsLabel(): String = "Wake window starts"

/** Wake settings Arm — window start help. */
fun alarmWakeWindowStartsHelp(): String =
    "An early cue is possible from here when live HR changes. The deadline alarm is always kept."

/** Wake settings Arm — window length row title. */
fun alarmWindowLengthLabel(): String = "Window length"

/** Wake settings Arm — window length help. */
fun alarmWindowLengthHelp(): String =
    "The phone alarm fires at the end if the strap or HR stream does not wake you first."

/** Arm wake-span TalkBack — earliest → deadline · N min. */
fun alarmWakeSpanA11y(startLabel: String, endLabel: String, windowMinutes: Int): String =
    "Wake window $startLabel to $endLabel · $windowMinutes minutes"

/** Arm wake-span overline under clocks. */
fun alarmWakeSpanOverline(): String = "SOFT WINDOW"

/** Arm window stepper foot — minutes · by deadline. */
fun alarmWindowByDeadlineCaption(windowMinutes: Int, deadlineLabel: String): String =
    "$windowMinutes min · by $deadlineLabel"

/** Earliest clock column overline. */
fun alarmEarliestOverline(): String = "EARLIEST"

/** Deadline clock column overline. */
fun alarmDeadlineOverline(): String = "DEADLINE"

/** Evening / Wind-down card headline. */
fun alarmWakeAlarmHeadline(): String = "Wake alarm"

/** Turn-back tonight preview after hard deadline. */
fun alarmTurnBackTonightCaption(watchMin: Int, deadlineLabel: String): String =
    "Tonight: watch live HR for $watchMin min after the $deadlineLabel deadline."

/** Turn-back HR-drop row footnote. */
fun alarmHrDropHelp(dropBpm: Int): String =
    "$dropBpm bpm below your post-wake high."

/** Wake-rested Charge threshold footnote. */
fun alarmChargeThresholdHelp(threshold: Int): String =
    "Wake early if overnight Charge ≥ $threshold (green Charge vessel on Today). Hard deadline still stands."

/** Wake-rested Charge vessel cue under threshold. */
fun alarmChargeVesselCue(): String =
    "See Today’s Charge vessel for the live banked score."

/** Wake-rested sleep-need row footnote. */
fun alarmSleepNeedMetHelp(pct: Int): String =
    "$pct% of your recent average night."

/** Soft-window companion strap-buzz help (bonded vs not). */
fun alarmBuzzStrapHelp(bonded: Boolean, strapName: String): String =
    if (bonded)
        "Companion to the phone window — buzzes your $strapName at earliest wake while NOOP is open. Separate from ${LifeChapterLacquer.ALARM_STRAP_WAKE_TITLE} below (firmware, works closed)."
    else
        "Companion to the phone window once a WHOOP 4 / 5 / MG is connected. Separate from ${LifeChapterLacquer.ALARM_STRAP_WAKE_TITLE} below. Phone deadline still backs you up."

/** Wind-down remind toggle help. */
fun alarmWindDownRemindHelp(): String =
    "A gentle evening notification, timed from your wake time and usual sleep need, so you can settle in time. It's a suggestion, not an alarm."

/**
 * Shared optical-lock caption for Live / Health / Today — type-40 frames + with-R-R + lock%.
 * Membership-free honesty only; never invents SpO₂/BP.
 */
fun opticalLockCaption(
    type40Frames: Int,
    type40WithRr: Int,
    lockPct: Int?,
    lead: String = LifeChapterLacquer.OPTICAL_LOCK_LEAD,
): String = buildString {
    append(lead)
    if (type40Frames > 0) {
        append(" · ")
        append(type40Frames)
        append(" ")
        append(LifeChapterLacquer.TYPE40_UNIT_LABEL)
        if (type40WithRr > 0) {
            append(" · ")
            append(type40WithRr)
            append(" ")
            append(LifeChapterLacquer.RR_WITH_LABEL)
        }
        lockPct?.let { pct ->
            append(" · ")
            append(pct)
            append("% lock")
        }
    } else {
        lockPct?.let { pct ->
            append(" · ")
            append(pct)
            append("% lock")
        }
    }
}

/** Climb caption while R-R beats approach feel RMSSD (Health / Today / RRStrip). */
fun rrFeelClimbCaption(
    beatCount: Int,
    need: Int = LifeChapterLacquer.RR_FEEL_NEED,
): String = when {
    beatCount <= 0 -> "Waiting for R-R intervals."
    beatCount == 1 -> "First interval · need ≥$need for feel RMSSD"
    beatCount < need -> "$beatCount of $need beats · feel RMSSD soon"
    else -> "$beatCount beats · feel RMSSD ready"
}

/** Live datastream type-40 honesty line (frames · with R-R · lock% · optional feel). */
fun type40SessionCaption(
    type40Frames: Int,
    type40WithRr: Int,
    lockPct: Int?,
    feelRmssdMs: Double?,
): String = buildString {
    append("Type-40 · ")
    append(type40Frames)
    append(" frames · ")
    append(type40WithRr)
    append(" ")
    append(LifeChapterLacquer.RR_WITH_LABEL)
    lockPct?.let { pct ->
        append(" · ")
        append(pct)
        append("% lock")
    }
    feelRmssdMs?.let { ms ->
        append(" · live ${LifeChapterLacquer.RMSSD_CHIP_LABEL} ")
        append(ms.roundToInt())
        append(" ${LifeChapterLacquer.HRV_MS_CAPTION}")
    }
    append(" (no membership)")
}

/**
 * Thin cyan progress while type-40 R-R lock climbs (MG often 30–60s).
 * [lockPct] 0–100; null / empty R-R bank only. Token alpha — no glow.
 * Settles with [OPTICAL_LOCK_SETTLE_MS] so climbs feel continuous (Reduce Motion = snap).
 */
@Composable
fun OpticalLockHairline(
    lockPct: Int?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val pct = lockPct?.coerceIn(0, 100) ?: return
    val reduced = rememberReduceMotion()
    val animated by animateFloatAsState(
        targetValue = pct / 100f,
        animationSpec = if (reduced) {
            tween(0)
        } else {
            tween(LifeChapterLacquer.OPTICAL_LOCK_SETTLE_MS, easing = FastOutSlowInEasing)
        },
        label = "opticalLockClimb",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(LifeChapterLacquer.HAIRLINE_H_DP.dp)
            .semantics { contentDescription = opticalLockChipA11y(pct) },
    ) {
        val trackA = if (Palette.isLight) LifeChapterLacquer.OPTICAL_LOCK_TRACK * 1.55f
            else LifeChapterLacquer.OPTICAL_LOCK_TRACK
        val track = accent.copy(alpha = trackA)
        val fill = accent.copy(alpha = LifeChapterLacquer.OPTICAL_LOCK_FILL)
        val h = size.height
        val r = h / 2f
        drawRoundRect(color = track, cornerRadius = CornerRadius(r, r))
        val w = size.width * animated
        if (w > 0.5f) {
            drawRoundRect(
                color = fill,
                size = Size(w, h),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

/**
 * Soft wake-span track — fill scales with window minutes vs [maxWindow].
 * Earliest sits left, deadline right; token alpha only (no glow).
 */
@Composable
fun WakeWindowSpanBar(
    windowMinutes: Int,
    maxWindow: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    val reduced = rememberReduceMotion()
    val frac = (windowMinutes.toFloat() / maxWindow.coerceAtLeast(1).toFloat()).coerceIn(0.08f, 1f)
    val animated by animateFloatAsState(
        targetValue = frac,
        animationSpec = if (reduced) {
            tween(0)
        } else {
            tween(LifeChapterLacquer.ARM_SETTLE_MS, easing = FastOutSlowInEasing)
        },
        label = "wakeSpanFill",
    )
    val a11y = if (startLabel != null && endLabel != null) {
        alarmWakeSpanA11y(startLabel, endLabel, windowMinutes)
    } else {
        "$windowMinutes minute wake window"
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(LifeChapterLacquer.WAKE_SPAN_H_DP.dp)
            .semantics { contentDescription = a11y },
    ) {
        val trackA = if (Palette.isLight) LifeChapterLacquer.WAKE_SPAN_TRACK * 1.55f
            else LifeChapterLacquer.WAKE_SPAN_TRACK
        val track = accent.copy(alpha = trackA)
        val fill = accent.copy(alpha = LifeChapterLacquer.WAKE_SPAN_FILL)
        val h = size.height
        val r = h / 2f
        drawRoundRect(color = track, cornerRadius = CornerRadius(r, r))
        val w = size.width * animated
        if (w > 0.5f) {
            drawRoundRect(
                color = fill,
                size = Size(w, h),
                cornerRadius = CornerRadius(r, r),
            )
        }
        // End ticks — earliest (left) + deadline (right of fill).
        val tickH = h * 1.35f
        val tickY = (h - tickH) / 2f
        drawRoundRect(
            color = accent.copy(alpha = 0.72f),
            topLeft = Offset(0f, tickY),
            size = Size(h * 0.55f, tickH),
            cornerRadius = CornerRadius(r, r),
        )
        val endX = (w - h * 0.55f).coerceAtLeast(h * 0.55f)
        drawRoundRect(
            color = accent.copy(alpha = 0.85f),
            topLeft = Offset(endX, tickY),
            size = Size(h * 0.55f, tickH),
            cornerRadius = CornerRadius(r, r),
        )
    }
}

/**
 * Thin progress while R-R intervals climb toward feel RMSSD (≥[need] beats).
 * Shown only for 1..<need; hidden when empty or feel-ready. Token alpha — no glow.
 */
@Composable
fun RrFeelProgressHairline(
    beatCount: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    need: Int = LifeChapterLacquer.RR_FEEL_NEED,
) {
    if (beatCount <= 0 || beatCount >= need) return
    val pct = ((100 * beatCount) / need).coerceIn(1, 99)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(LifeChapterLacquer.HAIRLINE_H_DP.dp)
            .semantics { contentDescription = rrFeelHairlineA11y(beatCount, need) },
    ) {
        val trackA = if (Palette.isLight) LifeChapterLacquer.RR_FEEL_TRACK * 1.55f
            else LifeChapterLacquer.RR_FEEL_TRACK
        val track = accent.copy(alpha = trackA)
        val fill = accent.copy(alpha = LifeChapterLacquer.RR_FEEL_FILL)
        val h = size.height
        val r = h / 2f
        drawRoundRect(color = track, cornerRadius = CornerRadius(r, r))
        val w = size.width * (pct / 100f)
        if (w > 0.5f) {
            drawRoundRect(
                color = fill,
                size = Size(w, h),
                cornerRadius = CornerRadius(r, r),
            )
        }
    }
}

/** Mini cyan droplets — shared by Today sip + Nutrition hydration jump. */
@Composable
fun HydrationSipLifeMotes(
    reduced: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    intensity: Float = LifeChapterLacquer.SIP_INTENSITY,
) {
    val inten = intensity.coerceIn(0.55f, 1f)
    val t = lifeT(reduced, label = "hydraSipLife", periodMs = 4_600)
    val drops = remember {
        listOf(
            MoteSpec(0.10f, 0.38f, 0.00f, 3.4f),
            MoteSpec(0.90f, 0.42f, 0.28f, 2.8f),
            MoteSpec(0.48f, 0.16f, 0.52f, 3.6f),
            // Quieter fourth drip — denser sip life without crowding the tube.
            MoteSpec(0.72f, 0.72f, 0.78f, 2.4f),
            MoteSpec(0.28f, 0.78f, 0.92f, 2.2f),
        )
    }
    Canvas(
        modifier = modifier.semantics { contentDescription = "Sip droplets" },
    ) {
        val w = size.width
        val h = size.height
        // Soft cyan wash under droplets — matches Cycle/Fuel lacquer language.
        drawCircle(
            color = accent.copy(alpha = 0.055f * inten),
            radius = minOf(w, h) * 0.44f,
            center = Offset(w * 0.48f, h * 0.48f),
        )
        drops.forEach { m ->
            val local = if (reduced) 0f else ((t + m.phase) % 1f)
            val bob = if (reduced) 0f else sin(local * Math.PI.toFloat() * 2f) * 2.8f * density * inten
            val alpha = if (reduced) {
                (0.14f * inten).coerceAtMost(0.22f)
            } else {
                ((0.09f + 0.11f * (0.5f + 0.5f * sin(local * Math.PI.toFloat() * 2f))) * inten)
                    .coerceIn(0.06f, 0.22f)
            }
            val cx = w * m.nx
            val cy = h * m.ny + bob
            val r = m.sizeDp * density * (0.90f + 0.10f * inten)
            drawCircle(color = accent.copy(alpha = alpha * 0.38f), radius = r * 1.85f, center = Offset(cx, cy))
            val path = Path()
            path.moveTo(cx, cy - r * 1.2f)
            path.quadraticBezierTo(cx + r * 1.0f, cy, cx, cy + r * 1.1f)
            path.quadraticBezierTo(cx - r * 1.0f, cy, cx, cy - r * 1.2f)
            path.close()
            drawPath(path, color = accent.copy(alpha = alpha))
        }
    }
}

/**
 * One-shot sip burst after +250 ml — soft radial spark that settles.
 * [burst] 1 = full flash → animate toward 0; Reduce Motion skips draw.
 */
@Composable
fun HydrationSipBurstSpark(
    burst: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (burst <= 0.01f) return
    Canvas(modifier = modifier) {
        // Sit near the Sip CTA / Log side so the flash reads as feedback, not noise.
        val cx = size.width * 0.78f
        val cy = size.height * 0.38f
        val energy = burst.coerceIn(0f, 1f)
        val r = 4.5f * density * (0.55f + (1f - energy) * 1.1f)
        val a = (0.22f * energy).coerceAtMost(0.22f)
        drawCircle(color = accent.copy(alpha = a * 0.45f), radius = r * 2.4f, center = Offset(cx, cy))
        drawCircle(color = accent.copy(alpha = a), radius = r, center = Offset(cx, cy))
        // Tiny satellite sparks — denser life without exceeding anti-glow.
        drawCircle(
            color = accent.copy(alpha = a * 0.55f),
            radius = r * 0.42f,
            center = Offset(cx - r * 2.2f, cy + r * 0.8f),
        )
        drawCircle(
            color = accent.copy(alpha = a * 0.40f),
            radius = r * 0.35f,
            center = Offset(cx + r * 1.6f, cy - r * 1.1f),
        )
    }
}

/**
 * Soft cyan bloom when hydration goal is met (≥100%). Static under Reduce Motion.
 * Token alpha only — never a neon fill.
 */
@Composable
fun HydrationGoalBloom(
    reduced: Boolean,
    accent: Color,
    met: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!met) return
    val t = lifeT(reduced, label = "hydraGoalBloom", periodMs = 5_800)
    Canvas(
        modifier = modifier.semantics { contentDescription = "Hydration goal met bloom" },
    ) {
        val pulse = if (reduced) 0.70f else 0.62f + 0.20f * sin(t * Math.PI.toFloat() * 2f)
        val a = (0.10f * pulse).coerceAtMost(0.16f)
        drawCircle(
            color = accent.copy(alpha = a),
            radius = minOf(size.width, size.height) * 0.48f * pulse,
            center = Offset(size.width * 0.50f, size.height * 0.52f),
        )
    }
}

/**
 * One-shot Charge-token diamond burst after meal / fuel log.
 * [burst] 1 → 0; Reduce Motion skips draw.
 */
@Composable
fun NutritionMealBurstSpark(
    burst: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (burst <= 0.01f) return
    Canvas(modifier = modifier) {
        val energy = burst.coerceIn(0f, 1f)
        val a = (0.22f * energy).coerceAtMost(0.22f)
        val hubs = listOf(
            Offset(size.width * 0.72f, size.height * 0.36f),
            Offset(size.width * 0.58f, size.height * 0.58f),
            Offset(size.width * 0.84f, size.height * 0.62f),
        )
        hubs.forEachIndexed { i, c ->
            val r = (3.2f + i * 0.6f) * density * (0.55f + (1f - energy) * 1.05f)
            drawCircle(color = accent.copy(alpha = a * 0.38f), radius = r * 1.9f, center = c)
            val path = Path()
            path.moveTo(c.x, c.y - r)
            path.lineTo(c.x + r * 0.75f, c.y)
            path.lineTo(c.x, c.y + r)
            path.lineTo(c.x - r * 0.75f, c.y)
            path.close()
            drawPath(path, color = accent.copy(alpha = a))
        }
    }
}

/**
 * Soft Rest spark burst when a supplement is logged from the catalog.
 */
@Composable
fun SupplementLogBurstSpark(
    burst: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (burst <= 0.01f) return
    Canvas(modifier = modifier) {
        val energy = burst.coerceIn(0f, 1f)
        val a = (0.20f * energy).coerceAtMost(0.22f)
        val cx = size.width * 0.50f
        val cy = size.height * 0.50f
        val r = 5.0f * density * (0.50f + (1f - energy) * 1.2f)
        drawCircle(color = accent.copy(alpha = a * 0.42f), radius = r * 2.2f, center = Offset(cx, cy))
        drawCircle(color = accent.copy(alpha = a), radius = r, center = Offset(cx, cy))
        drawCircle(
            color = accent.copy(alpha = a * 0.50f),
            radius = r * 0.40f,
            center = Offset(cx - r * 1.8f, cy - r * 0.6f),
        )
        drawCircle(
            color = accent.copy(alpha = a * 0.45f),
            radius = r * 0.35f,
            center = Offset(cx + r * 1.6f, cy + r * 0.7f),
        )
    }
}

/** Soft Charge-token meal motes behind Nutrition kcal hero + Today Fuel peek. */
@Composable
fun NutritionMealLifeMotes(
    reduced: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    /** [LifeChapterLacquer.FUEL_HERO_INTENSITY] hero; [FUEL_PEEK_INTENSITY] Today peek. */
    intensity: Float = LifeChapterLacquer.FUEL_HERO_INTENSITY,
) {
    val t = lifeT(reduced, label = "nutritionMealLife", periodMs = 5_400)
    val motes = remember {
        listOf(
            MoteSpec(0.14f, 0.72f, 0.00f, 3.2f),
            MoteSpec(0.42f, 0.22f, 0.28f, 2.6f),
            MoteSpec(0.78f, 0.68f, 0.55f, 3.0f),
            MoteSpec(0.88f, 0.30f, 0.78f, 2.4f),
            // Fifth quiet diamond — denser Fuel life without crowding kcal digits.
            MoteSpec(0.58f, 0.48f, 0.90f, 2.1f),
        )
    }
    val inten = intensity.coerceIn(0.45f, 1f)
    Canvas(
        modifier = modifier.semantics { contentDescription = "Fuel meal motes" },
    ) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = accent.copy(alpha = 0.06f * inten),
            radius = minOf(w, h) * 0.42f,
            center = Offset(w * 0.42f, h * 0.55f),
        )
        motes.forEach { m ->
            val local = if (reduced) 0.35f else ((t + m.phase) % 1f)
            val rise = if (reduced) h * 0.06f * inten else sin(local * Math.PI.toFloat()) * h * 0.10f * inten
            val sway = if (reduced) 0f else cos(local * Math.PI.toFloat() * 2f) * 6.5f * density * inten
            val alpha = if (reduced) {
                (0.18f * inten).coerceAtMost(0.22f)
            } else {
                ((0.10f + 0.18f * sin(local * Math.PI.toFloat())) * inten).coerceIn(0.06f, 0.22f)
            }
            val cx = w * m.nx + sway
            val cy = h * m.ny - rise
            val r = m.sizeDp * density * (0.88f + 0.12f * inten)
            drawCircle(color = accent.copy(alpha = alpha * 0.42f), radius = r * 1.9f, center = Offset(cx, cy))
            val path = Path()
            path.moveTo(cx, cy - r)
            path.lineTo(cx + r * 0.75f, cy)
            path.lineTo(cx, cy + r)
            path.lineTo(cx - r * 0.75f, cy)
            path.close()
            drawPath(path, color = accent.copy(alpha = alpha))
        }
    }
}

/** Quiet Rest-domain moon crescents + soft floating clouds behind Alarm / Today bedtime chrome. */
@Composable
fun AlarmBedMoonLifeMotes(
    reduced: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    /** 1f = Alarm glance; ~0.82f = Today Quick Alarm (quieter cohesion). */
    intensity: Float = 1f,
) {
    val inten = intensity.coerceIn(0.55f, 1f)
    val t = lifeT(reduced || !active, label = "alarmBedMoon", periodMs = 7_200)
    val cloudT = lifeT(reduced || !active, label = "alarmBedCloud", periodMs = 11_400)
    val moons = remember {
        listOf(
            MoteSpec(0.12f, 0.28f, 0.00f, 5.5f),
            MoteSpec(0.86f, 0.22f, 0.40f, 4.2f),
            MoteSpec(0.70f, 0.78f, 0.72f, 3.6f),
            // Fourth quiet crescent — denser bedtime life on Armed glance.
            MoteSpec(0.38f, 0.52f, 0.88f, 3.0f),
            // Fifth whisper crescent — Alarm densify (Sip/Cycle fifth-mote parity).
            MoteSpec(0.58f, 0.36f, 0.55f, 2.6f),
        )
    }
    val clouds = remember {
        listOf(
            CloudSpec(0.22f, 0.62f, 0.00f, 1.00f),
            CloudSpec(0.78f, 0.48f, 0.38f, 0.82f),
            CloudSpec(0.48f, 0.18f, 0.66f, 0.70f),
        )
    }
    val a11y = if (active) "Bedtime moons · Armed" else "Bedtime moons · Off · Moon quiet"
    Canvas(
        modifier = modifier.semantics { contentDescription = a11y },
    ) {
        val w = size.width
        val h = size.height
        // Theme wash — Rest-tinted lacquer so body text sits on a clearer field.
        drawCircle(
            color = accent.copy(alpha = (if (active) 0.085f else 0.045f) * inten),
            radius = minOf(w, h) * 0.52f,
            center = Offset(w * 0.50f, h * 0.42f),
        )
        clouds.forEach { c ->
            val local = if (reduced || !active) 0.25f else ((cloudT + c.phase) % 1f)
            val drift = if (reduced || !active) 0f else sin(local * Math.PI.toFloat() * 2f) * 10f * density * inten
            val lift = if (reduced || !active) 0f else cos(local * Math.PI.toFloat() * 1.4f) * 4f * density * inten
            val alpha = if (reduced || !active) {
                0.10f * inten
            } else {
                ((0.08f + 0.10f * (0.5f + 0.5f * sin(local * Math.PI.toFloat() * 2f))) * inten)
                    .coerceIn(0.05f, 0.18f)
            }
            drawSoftCloud(
                cx = w * c.nx + drift,
                cy = h * c.ny + lift,
                scale = c.scale * (0.92f + 0.08f * inten),
                color = accent.copy(alpha = alpha),
            )
        }
        moons.forEach { m ->
            val local = if (reduced || !active) 0.30f else ((t + m.phase) % 1f)
            val bob = if (reduced || !active) 0f else sin(local * Math.PI.toFloat() * 2f) * 3.2f * density * inten
            val sway = if (reduced || !active) 0f else cos(local * Math.PI.toFloat() * 1.6f) * 3.5f * density * inten
            val alpha = if (reduced || !active) {
                0.12f * inten
            } else {
                ((0.09f + 0.13f * (0.5f + 0.5f * sin(local * Math.PI.toFloat() * 2f))) * inten)
                    .coerceIn(0.05f, 0.22f)
            }
            val cx = w * m.nx + sway
            val cy = h * m.ny + bob
            val r = m.sizeDp * density * (0.90f + 0.10f * inten)
            val path = Path()
            path.moveTo(cx, cy - r)
            path.cubicTo(cx + r * 1.15f, cy - r * 0.55f, cx + r * 1.15f, cy + r * 0.55f, cx, cy + r)
            path.cubicTo(cx + r * 0.35f, cy + r * 0.55f, cx + r * 0.35f, cy - r * 0.55f, cx, cy - r)
            path.close()
            drawPath(path, color = accent.copy(alpha = alpha))
            drawCircle(color = accent.copy(alpha = alpha * 0.32f), radius = r * 0.52f, center = Offset(cx - r * 0.15f, cy))
        }
    }
}

private data class CloudSpec(val nx: Float, val ny: Float, val phase: Float, val scale: Float)

private fun DrawScope.drawSoftCloud(cx: Float, cy: Float, scale: Float, color: Color) {
    val s = 7.5f * density * scale
    drawCircle(color = color, radius = s * 1.15f, center = Offset(cx - s * 0.85f, cy))
    drawCircle(color = color, radius = s * 1.45f, center = Offset(cx, cy - s * 0.25f))
    drawCircle(color = color, radius = s * 1.05f, center = Offset(cx + s * 0.95f, cy + s * 0.05f))
    drawCircle(color = color.copy(alpha = color.alpha * 0.55f), radius = s * 1.8f, center = Offset(cx, cy))
}

/** Tiny Rest spark when a supplement (e.g. creatine) is already logged today. */
@Composable
fun SupplementTakenLifeSpark(
    reduced: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val t = lifeT(reduced, label = "suppTakenSpark", periodMs = 3_800)
    Canvas(modifier = modifier) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val pulse = if (reduced) 0.55f else 0.48f + 0.28f * sin(t * Math.PI.toFloat() * 2f)
        val r = 3.4f * density * pulse
        drawCircle(color = accent.copy(alpha = (0.16f * pulse).coerceAtMost(0.22f)), radius = r * 2.1f, center = Offset(cx, cy))
        drawCircle(color = accent.copy(alpha = (0.50f * pulse).coerceAtMost(0.55f)), radius = r, center = Offset(cx, cy))
    }
}

/**
 * Cycle four-point stars — spawn → hover → break/fade.
 * Shared by [TodayCycleLifeCard]; [dimmed] for Learning phase.
 */
@Composable
fun CycleStarLifeMotes(
    reduced: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    intensity: Float = LifeChapterLacquer.CYCLE_INTENSITY,
) {
    val inten = intensity.coerceIn(0.55f, 1f)
    val stars = remember {
        listOf(
            StarSpec(spawnNx = 0.18f, spawnNy = 0.78f, phase = 0.00f, drift = 0.92f, sizeDp = 5.5f),
            StarSpec(spawnNx = 0.52f, spawnNy = 0.88f, phase = 0.33f, drift = 1.05f, sizeDp = 4.5f),
            StarSpec(spawnNx = 0.82f, spawnNy = 0.72f, phase = 0.66f, drift = 0.88f, sizeDp = 5.0f),
            StarSpec(spawnNx = 0.36f, spawnNy = 0.62f, phase = 0.18f, drift = 0.78f, sizeDp = 3.2f),
            // Fifth quiet star — denser Cycle life without crowding phase copy.
            StarSpec(spawnNx = 0.68f, spawnNy = 0.42f, phase = 0.48f, drift = 0.70f, sizeDp = 2.8f),
        )
    }
    val t = lifeT(reduced, label = "cycleStarLife", periodMs = 5_200)
    val dim = (if (dimmed) 0.62f else 1f) * inten
    val a11y = if (dimmed) "Cycle stars · Learning" else "Cycle stars"
    Canvas(
        modifier = modifier.semantics { contentDescription = a11y },
    ) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = accent.copy(alpha = 0.065f * dim),
            radius = minOf(w, h) * 0.48f,
            center = Offset(w * 0.55f, h * 0.55f),
        )
        stars.forEach { star ->
            val localT = if (reduced) 0.35f else ((t + star.phase) % 1f)
            // Longer hover plateau, softer break (8.6.117 polish).
            val rise = if (reduced) {
                h * 0.18f * star.drift * inten
            } else {
                when {
                    localT < 0.10f -> (localT / 0.10f) * h * 0.10f * star.drift * inten
                    localT < 0.68f -> {
                        val mid = (localT - 0.10f) / 0.58f
                        h * (0.10f + mid * 0.40f) * star.drift * inten
                    }
                    else -> h * 0.50f * star.drift * inten
                }
            }
            val sway = if (reduced) {
                0f
            } else {
                sin(localT * Math.PI.toFloat() * 2.0f) * 8.5f * density * star.drift * inten
            }
            val alpha = if (reduced) {
                (0.24f * dim).coerceAtMost(0.22f)
            } else {
                when {
                    localT < 0.08f -> (localT / 0.08f) * 0.36f
                    localT < 0.60f -> 0.36f
                    localT < 0.82f -> 0.36f * (1f - (localT - 0.60f) / 0.22f)
                    else -> 0f
                }.coerceIn(0f, 0.36f) * dim
            }
            if (alpha <= 0.01f) return@forEach
            val cx = w * star.spawnNx + sway
            val cy = h * star.spawnNy - rise
            val scale = if (reduced) {
                1f
            } else when {
                localT < 0.10f -> 0.55f + (localT / 0.10f) * 0.45f
                localT < 0.68f -> 1f
                else -> 1f + (localT - 0.68f) / 0.32f * 0.28f
            }
            val bloom = accent.copy(alpha = (alpha * 0.50f).coerceAtMost(0.22f))
            val sz = star.sizeDp * density * (0.90f + 0.10f * inten)
            drawCircle(color = bloom, radius = sz * 2.0f * scale, center = Offset(cx, cy))
            drawFourPointStar(
                center = Offset(cx, cy),
                halfLong = sz * 1.15f * scale,
                halfShort = sz * 0.55f * scale,
                color = accent.copy(alpha = alpha.coerceAtMost(0.42f)),
            )
        }
    }
}

@Composable
private fun lifeT(reduced: Boolean, label: String, periodMs: Int): Float {
    if (reduced) return 0f
    val transition = rememberInfiniteTransition(label = label)
    val v by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "${label}T",
    )
    return v
}

private data class MoteSpec(
    val nx: Float,
    val ny: Float,
    val phase: Float,
    val sizeDp: Float,
)

private data class StarSpec(
    val spawnNx: Float,
    val spawnNy: Float,
    val phase: Float,
    val drift: Float,
    val sizeDp: Float,
)

/** Four-point star = 4 triangles (2 long vertical + 2 short horizontal). */
internal fun DrawScope.drawFourPointStar(
    center: Offset,
    halfLong: Float,
    halfShort: Float,
    color: Color,
) {
    drawStarTriangle(
        tip = Offset(center.x, center.y - halfLong),
        baseL = Offset(center.x - halfShort * 0.35f, center.y),
        baseR = Offset(center.x + halfShort * 0.35f, center.y),
        color = color,
    )
    drawStarTriangle(
        tip = Offset(center.x, center.y + halfLong),
        baseL = Offset(center.x + halfShort * 0.35f, center.y),
        baseR = Offset(center.x - halfShort * 0.35f, center.y),
        color = color,
    )
    drawStarTriangle(
        tip = Offset(center.x - halfShort, center.y),
        baseL = Offset(center.x, center.y + halfLong * 0.28f),
        baseR = Offset(center.x, center.y - halfLong * 0.28f),
        color = color,
    )
    drawStarTriangle(
        tip = Offset(center.x + halfShort, center.y),
        baseL = Offset(center.x, center.y - halfLong * 0.28f),
        baseR = Offset(center.x, center.y + halfLong * 0.28f),
        color = color,
    )
}

private fun DrawScope.drawStarTriangle(
    tip: Offset,
    baseL: Offset,
    baseR: Offset,
    color: Color,
) {
    val path = Path()
    path.moveTo(tip.x, tip.y)
    path.lineTo(baseL.x, baseL.y)
    path.lineTo(baseR.x, baseR.y)
    path.close()
    drawPath(path, color = color)
}
