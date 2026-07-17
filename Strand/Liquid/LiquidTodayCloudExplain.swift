import SwiftUI
import StrandDesign

/// Forward-grow lacquer cloud portal for Charge / Effort / Rest explain on liquid Today.
/// Twin spirit of Android `TodayCloudExplain` — explain lives inside the bubble; Reduce Motion
/// callers skip this and open the plain ScoringGuide sheet instead.
struct LiquidTodayCloudExplain: View {
    let section: ScoreSection
    let onClose: () -> Void

    var body: some View {
        ZStack {
            // Backdrop wash — Today scales back visually.
            Rectangle()
                .fill(.ultraThinMaterial)
                .overlay(Color.black.opacity(0.35))
                .ignoresSafeArea()
                .onTapGesture(perform: onClose)
                .accessibilityLabel("Dismiss explain")

            VStack(spacing: 0) {
                HStack {
                    Text(section.displayName.uppercased())
                        .font(StrandFont.overline)
                        .tracking(StrandFont.overlineTracking)
                        .foregroundStyle(StrandPalette.textTertiary)
                    Spacer()
                    Button(action: onClose) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(StrandPalette.textTertiary)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Close")
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 8)

                ScoringGuideView(initialSection: section, onClose: onClose)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 28, style: .continuous)
                            .strokeBorder(section.accent.opacity(0.35), lineWidth: 1.2)
                    )
                    .padding(.horizontal, 12)
                    .padding(.bottom, 20)
                    .shadow(color: section.accent.opacity(0.22), radius: 28, y: 12)
            }
        }
        .accessibilityAddTraits(.isModal)
    }
}
