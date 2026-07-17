import Foundation

/// Pure session gate for charging overlay / battery UI — mirrors Android `ChargingOverlayPolicy`.
///
/// Latch one continuous charge visit so SoC flicker and BLE reconnect flaps do not re-open or
/// re-ding. Pass `linkConnected=false` while GATT is down to hold the latch across flaps.
enum ChargingOverlayPolicy {
    /// Same band as Android `BatteryAlertPolicy.CHARGING_REARM_DROP` (typically 3%).
    static let sessionRearmDrop = 3

    struct Session: Equatable {
        var active: Bool
        var anchorPct: Int?
        var openOverlay: Bool
        var closeOverlay: Bool
        var clearDismissed: Bool
    }

    static func evaluate(
        charging: Bool?,
        pct: Int?,
        wasActive: Bool,
        anchorPct: Int?,
        dismissed: Bool,
        linkConnected: Bool = true
    ) -> Session {
        _ = dismissed // reserved for callers that gate openOverlay themselves
        if !linkConnected {
            if wasActive {
                return Session(active: true, anchorPct: anchorPct,
                               openOverlay: false, closeOverlay: false, clearDismissed: false)
            }
            return Session(active: false, anchorPct: nil,
                           openOverlay: false, closeOverlay: false, clearDismissed: false)
        }
        if charging == false {
            return Session(active: false, anchorPct: nil,
                           openOverlay: false, closeOverlay: wasActive, clearDismissed: true)
        }
        if wasActive, let anchor = anchorPct, let p = pct, p <= anchor - sessionRearmDrop {
            return Session(active: false, anchorPct: nil,
                           openOverlay: false, closeOverlay: true, clearDismissed: true)
        }
        if charging == true && !wasActive {
            return Session(active: true, anchorPct: pct,
                           openOverlay: true, closeOverlay: false, clearDismissed: true)
        }
        if charging == true && wasActive {
            let newAnchor: Int?
            if let p = pct {
                if let a = anchorPct { newAnchor = max(a, p) } else { newAnchor = p }
            } else {
                newAnchor = anchorPct
            }
            return Session(active: true, anchorPct: newAnchor,
                           openOverlay: false, closeOverlay: false, clearDismissed: false)
        }
        if wasActive {
            return Session(active: true, anchorPct: anchorPct,
                           openOverlay: false, closeOverlay: false, clearDismissed: false)
        }
        return Session(active: false, anchorPct: nil,
                       openOverlay: false, closeOverlay: false, clearDismissed: false)
    }
}
