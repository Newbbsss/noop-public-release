import SwiftUI
import StrandDesign

/// In-app bug report (Android 8.6.145+ parity): describe what broke, share strap diagnostics,
/// open GitHub Issues on Newbbsss/noop-public-release with label `user-bug`. No Tailscale.
struct BugReportView: View {
    @EnvironmentObject private var live: LiveState

    @State private var whatHappens = ""
    @State private var expected = ""
    @State private var shareItems: [Any] = []
    @State private var showShare = false
    @State private var busy = false
    @State private var toast: String?

    var body: some View {
        ScreenScaffold(
            title: "Bug report",
            subtitle: "Photos + diagnostics Â· no Tailscale needed"
        ) {
            VStack(alignment: .leading, spacing: NoopMetrics.sectionGap) {
                Text("Describe what broke. Share the diagnostics zip, then open GitHub Issues (label user-bug) or email. Nothing uploads silently.")
                    .font(StrandFont.subhead)
                    .foregroundStyle(StrandPalette.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(alignment: .leading, spacing: 8) {
                    Text("What happens").strandOverline()
                    TextField("e.g. Sleep today/yesterday empty; older nights OK", text: $whatHappens, axis: .vertical)
                        .lineLimit(4...8)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityLabel("What happens")
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("What you expected (optional)").strandOverline()
                    TextField("Optional", text: $expected, axis: .vertical)
                        .lineLimit(2...4)
                        .textFieldStyle(.roundedBorder)
                        .accessibilityLabel("What you expected")
                }

                NoopButton(
                    busy ? "Packingâ€¦" : "Share report (diagnostics)",
                    systemImage: "square.and.arrow.up",
                    kind: .primary
                ) {
                    packAndShare()
                }
                .disabled(busy || whatHappens.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .opacity(whatHappens.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? 0.5 : 1)

                NoopButton("Open GitHub Issues (user-bug)", systemImage: "ladybug", kind: .secondary) {
                    if let url = Self.userBugIssueURL(
                        whatHappens: whatHappens.isEmpty ? "(describe after attaching zip)" : whatHappens,
                        expected: expected.isEmpty ? nil : expected,
                        version: Self.appVersionLabel,
                        platform: Self.platformName,
                        osVersion: ProcessInfo.processInfo.operatingSystemVersionString
                    ) {
                        PlatformOpen.url(url)
                    }
                }

                Text("Agents check GitHub issues labeled user-bug on Newbbsss/noop-public-release every few wakes. No Tailscale required.")
                    .font(StrandFont.footnote)
                    .foregroundStyle(StrandPalette.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)

                if let toast {
                    Text(toast)
                        .font(StrandFont.footnote)
                        .foregroundStyle(StrandPalette.statusWarning)
                }
            }
        }
        .sheet(isPresented: $showShare) {
            ActivityShareSheet(items: shareItems)
        }
    }

    private func packAndShare() {
        busy = true
        toast = nil
        Task {
            let stamp = ISO8601DateFormatter().string(from: Date())
                .replacingOccurrences(of: ":", with: "-")
            let what = whatHappens.trimmingCharacters(in: .whitespacesAndNewlines)
            let exp = expected.trimmingCharacters(in: .whitespacesAndNewlines)
            let readme = """
            NOOP user bug report
            version=\(Self.appVersionLabel)
            platform=\(Self.platformName) \(ProcessInfo.processInfo.operatingSystemVersionString)

            WHAT HAPPENS
            \(what)

            \(exp.isEmpty ? "" : "EXPECTED\n\(exp)\n")
            Attach this file (+ screenshots) on a GitHub issue labeled user-bug:
            https://github.com/Newbbsss/noop-public-release/issues
            """
            let strap = live.exportableLogText()
            let dir = FileManager.default.temporaryDirectory
            let url = dir.appendingPathComponent("noop-user-bug-\(stamp).txt")
            let body = readme + "\n\n--- STRAP LOG ---\n\n" + strap
            do {
                try body.write(to: url, atomically: true, encoding: .utf8)
                await MainActor.run {
                    shareItems = [url]
                    showShare = true
                    busy = false
                }
            } catch {
                await MainActor.run {
                    toast = "Couldn't build the report. Try Test Centre â†’ Report."
                    busy = false
                }
            }
        }
    }

    /// Gilbert fork issue composer â€” mirrors Android `userBugIssueUrl`.
    static func userBugIssueURL(
        whatHappens: String,
        expected: String?,
        version: String,
        platform: String,
        osVersion: String
    ) -> URL? {
        let title = "[user-bug] \(String(whatHappens.prefix(72)).replacingOccurrences(of: "\n", with: " "))"
        var body = """
        ## What happens
        \(String(whatHappens.prefix(1_500)))
        """
        if let expected, !expected.isEmpty {
            body += "\n\n## Expected\n\(String(expected.prefix(600)))"
        }
        body += """


        ## App
        - version: \(version)
        - platform: \(platform) \(osVersion)

        _Attach diagnostics from in-app Bug report._
        """
        var allowed = CharacterSet.alphanumerics
        allowed.insert(charactersIn: "-._~")
        let enc: (String) -> String = {
            $0.addingPercentEncoding(withAllowedCharacters: allowed) ?? $0
        }
        let q = "labels=\(enc("user-bug"))&title=\(enc(title))&body=\(enc(String(body.prefix(3_500))))"
        return URL(string: "https://github.com/Newbbsss/noop-public-release/issues/new?\(q)")
    }

    private static var appVersionLabel: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "\(v) (\(b))"
    }

    private static var platformName: String {
        #if os(iOS)
        return "iOS"
        #else
        return "macOS"
        #endif
    }
}

#if os(iOS)
import UIKit
private struct ActivityShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
#else
private struct ActivityShareSheet: View {
    let items: [Any]
    var body: some View {
        if let url = items.first as? URL {
            ShareLink(item: url) { Text("Share diagnostics") }
                .padding()
        } else {
            Text("Nothing to share")
        }
    }
}
#endif
