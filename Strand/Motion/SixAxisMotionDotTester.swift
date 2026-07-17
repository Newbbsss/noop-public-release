import SwiftUI
import StrandDesign

/// Settings / Test Centre 6-axis motion tester: a moving dot driven by **band IMU only**.
/// Twin of Android `SixAxisMotionDotTester`.
///
/// Preference: strap live type-51 when present → else strap offload 1244-B (labeled **not live**).
/// Never falls back to phone CoreMotion.
struct SixAxisMotionDotTester: View {
    @ObservedObject var ble: BLEManager
    var compact: Bool = false

    private var driver: SixAxisSample? {
        SixAxisMotionLabels.preferStrapDriver(ble.latestStrapImu)
    }

    private var live: Bool { SixAxisMotionLabels.isLiveDriver(driver) }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !compact {
                Text(
                    "Band IMU only — move the strap. Live type-51 drives the dot when it streams; " +
                    "historical offload is shown as not-live. Phone sensors are not used."
                )
                .font(StrandFont.caption)
                .foregroundStyle(StrandPalette.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
            }

            sourceBadge

            MotionPad(sample: driver, live: live)

            axisReadout

            Text(
                "Honesty: TOGGLE_IMU_MODE (cmd 106) can ACK without streaming. " +
                "R22 flag ACK ≠ live types 51–56. Historical 1244-B offload is real 6-axis, not live. " +
                "No phone fallback."
            )
            .font(StrandFont.caption)
            .foregroundStyle(StrandPalette.textTertiary)
            .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .contain)
    }

    private var sourceBadge: some View {
        let title = driver.map { SixAxisMotionLabels.sourceTitle($0.source) }
            ?? SixAxisMotionLabels.waitingTitle
        let detail = driver.map { SixAxisMotionLabels.sourceDetail($0.source) }
            ?? SixAxisMotionLabels.waitingDetail
        let tint: Color = {
            switch driver?.source {
            case .strapLive: return StrandPalette.statusPositive
            case .strapOffload: return StrandPalette.statusWarning
            case nil: return StrandPalette.textTertiary
            }
        }()
        return VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 8) {
                Circle().fill(tint).frame(width: 8, height: 8)
                Text(title).font(StrandFont.subhead).foregroundStyle(StrandPalette.textPrimary)
            }
            Text(detail)
                .font(StrandFont.caption)
                .foregroundStyle(StrandPalette.textTertiary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private var axisReadout: some View {
        Group {
            if let s = driver {
                Text(
                    String(
                        format: "a %.2f/%.2f/%.2f g · g %.0f/%.0f/%.0f °/s · |a| %.2f g",
                        s.ax, s.ay, s.az, s.gx, s.gy, s.gz, s.accelMagG
                    )
                )
                .font(StrandFont.caption)
                .foregroundStyle(StrandPalette.textSecondary)
                .monospacedDigit()
            } else {
                Text("Axes awaiting band sample")
                    .font(StrandFont.caption)
                    .foregroundStyle(StrandPalette.textTertiary)
            }
        }
    }
}

private struct MotionPad: View {
    let sample: SixAxisSample?
    let live: Bool

    var body: some View {
        let waiting = sample == nil
        let targetX = sample.map { max(-1, min(1, $0.ax / 1.2)) } ?? 0
        let targetY = sample.map { max(-1, min(1, -$0.ay / 1.2)) } ?? 0

        GeometryReader { geo in
            ZStack {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(StrandPalette.surfaceInset)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16, style: .continuous)
                            .strokeBorder(StrandPalette.hairline, lineWidth: 1)
                    )

                // Crosshair
                Path { p in
                    p.move(to: CGPoint(x: 0, y: geo.size.height / 2))
                    p.addLine(to: CGPoint(x: geo.size.width, y: geo.size.height / 2))
                    p.move(to: CGPoint(x: geo.size.width / 2, y: 0))
                    p.addLine(to: CGPoint(x: geo.size.width / 2, y: geo.size.height))
                }
                .stroke(style: StrokeStyle(lineWidth: 1, dash: [8, 8]))
                .foregroundStyle(Color.white.opacity(0.12))

                Circle()
                    .fill(Color.white.opacity(0.08))
                    .frame(width: 8, height: 8)

                if waiting {
                    Text("No band IMU yet\nWaiting for type-51 live or 1244-B offload")
                        .font(StrandFont.caption)
                        .foregroundStyle(StrandPalette.textTertiary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                } else {
                    Circle()
                        .fill(live ? StrandPalette.statusPositive : StrandPalette.statusWarning)
                        .frame(width: 18, height: 18)
                        .offset(
                            x: CGFloat(targetX) * geo.size.width * 0.42,
                            y: CGFloat(targetY) * geo.size.height * 0.42
                        )
                        .animation(live ? .easeOut(duration: 0.09) : .none, value: targetX)
                        .animation(live ? .easeOut(duration: 0.09) : .none, value: targetY)
                }
            }
        }
        .aspectRatio(1.35, contentMode: .fit)
        .accessibilityLabel("Six-axis motion pad")
    }
}
