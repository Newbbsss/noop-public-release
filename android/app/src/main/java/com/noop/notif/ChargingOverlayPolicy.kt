package com.noop.notif

/**
 * Pure session gate for the AirPods-style full-screen charging overlay.
 *
 * Mirrors [BatteryAlertPolicy.evaluateChargingStarted]: the strap's charge bit and SoC-inference
 * flicker true→null/false between percent ticks. Treating every flicker as a new "plug-in" replays
 * the ding + animation on every +1%. Latch the session until an explicit unplug or a genuine
 * multi-point discharge.
 *
 * BLE flaps (connect/disconnect while the puck stays docked) clear [LiveState.charging] to null and
 * briefly invent `false` from SoC noise after reconnect. Those must NOT end the session or re-open
 * the overlay — pass [linkConnected]=false while the GATT link is down so we hold the latch.
 */
internal object ChargingOverlayPolicy {
    /** Same band as [BatteryAlertPolicy.CHARGING_REARM_DROP]. */
    const val SESSION_REARM_DROP = BatteryAlertPolicy.CHARGING_REARM_DROP

    data class Session(
        /** True while we consider this one continuous charge visit. */
        val active: Boolean,
        /** SoC when the session opened (or last rising refresh); used for discharge re-arm. */
        val anchorPct: Int?,
        /** Fire the full-screen overlay once at session open (caller also gates on app foreground). */
        val openOverlay: Boolean,
        /** Clear any visible overlay (unplug / session end). */
        val closeOverlay: Boolean,
        /** Reset "user dismissed this charge" when the session truly ends. */
        val clearDismissed: Boolean,
    )

    /**
     * @param charging      LiveState.charging (null = unknown / flicker / link drop)
     * @param pct           rounded battery percent, if known
     * @param wasActive     prior session active flag
     * @param anchorPct     prior anchor
     * @param dismissed     user dismissed overlay this session
     * @param linkConnected GATT link up; false during BLE flaps — hold session, never treat as unplug
     */
    fun evaluate(
        charging: Boolean?,
        pct: Int?,
        wasActive: Boolean,
        anchorPct: Int?,
        dismissed: Boolean,
        linkConnected: Boolean = true,
    ): Session {
        // Link down: hold an in-progress charge session across reconnect flaps. Never open or close.
        if (!linkConnected) {
            if (wasActive) {
                return Session(
                    active = true,
                    anchorPct = anchorPct,
                    openOverlay = false,
                    closeOverlay = false,
                    clearDismissed = false,
                )
            }
            return Session(
                active = false,
                anchorPct = null,
                openOverlay = false,
                closeOverlay = false,
                clearDismissed = false,
            )
        }
        // Explicit off-charger ends the session (only while linked — unplug while docked-link-up).
        if (charging == false) {
            return Session(
                active = false,
                anchorPct = null,
                openOverlay = false,
                closeOverlay = wasActive,
                clearDismissed = true,
            )
        }
        // Genuine discharge while bit is unknown also ends the session (unplugged mid-flicker).
        if (wasActive && anchorPct != null && pct != null && pct <= anchorPct - SESSION_REARM_DROP) {
            return Session(
                active = false,
                anchorPct = null,
                openOverlay = false,
                closeOverlay = true,
                clearDismissed = true,
            )
        }
        if (charging == true && !wasActive) {
            return Session(
                active = true,
                anchorPct = pct,
                openOverlay = true, // new plug-in always opens once; [dismissed] is for this session only
                closeOverlay = false,
                clearDismissed = true,
            )
        }
        if (charging == true && wasActive) {
            // Still charging: raise the water-mark so a later real discharge can re-arm; never re-open.
            val newAnchor = when {
                pct == null -> anchorPct
                anchorPct == null -> pct
                pct > anchorPct -> pct
                else -> anchorPct
            }
            return Session(
                active = true,
                anchorPct = newAnchor,
                openOverlay = false,
                closeOverlay = false,
                clearDismissed = false,
            )
        }
        // null charging while session active: hold (don't clear dismissed, don't re-open).
        if (wasActive) {
            return Session(
                active = true,
                anchorPct = anchorPct,
                openOverlay = false,
                closeOverlay = false,
                clearDismissed = false,
            )
        }
        return Session(
            active = false,
            anchorPct = null,
            openOverlay = false,
            closeOverlay = false,
            clearDismissed = false,
        )
    }
}
