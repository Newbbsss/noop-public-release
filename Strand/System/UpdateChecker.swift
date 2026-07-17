import Foundation
import WhoopProtocol

/// User-initiated "Check for updates": one call to the project's PUBLIC releases API (GitHub),
/// made ONLY when the user taps the button. No background polling and no auto-update â€” it just reads the latest version
/// number and compares it to the installed one; nothing about the user is sent. (Uses the
/// network-client entitlement, which is otherwise only for the opt-in, off-by-default AI Coach.)
@MainActor
final class UpdateChecker: ObservableObject {

    enum State: Equatable {
        case idle
        case checking
        case upToDate(version: String)
        case available(version: String, url: URL, notes: String)
        case failed
    }

    @Published var state: State = .idle

    /// Public release: GitHub Releases on Newbbsss/noop-public-release.
    /// Upstream ryanbr/noop is a different lineage â€” do not use it for this fork's prompts.
    private static let githubEndpoint = URL(string: "https://api.github.com/repos/Newbbsss/noop-public-release/releases/latest")!
    private static let storeCatalogs: [URL] = []

    /// Upstream AltStore / SideStore source (ryanbr IPA). Gilbert fork has no IPA row yet.
    static let altStoreSourceURL =
        "https://raw.githubusercontent.com/ryanbr/noop/main/altstore-source.json"

    /// Plain-text invite for friends already on the same Tailscale tailnet.
    static var friendsTailnetShareText: String {
        """
        NOOP — Friends network invite (private pipe only).

        1. Join the same home Wi-Fi — or a Tailscale tailnet — as your friend.
        2. Android: install from GitHub Releases:
           https://github.com/Newbbsss/noop-public-release/releases/latest
        3. iPhone: add this AltStore / SideStore source, then install NOOP:
           \(altStoreSourceURL)
           (AltStore: https://altstore.io — free Apple ID, re-signs every 7 days.)

        App updates come from GitHub — Friends is not the update channel.
        Fully offline once installed. No WHOOP cloud. Not affiliated with WHOOP.
        """
    }

    func check(currentVersion: String) {
        guard state != .checking else { return }
        state = .checking
        Task {
            if let storeResult = await Self.checkStore(currentVersion: currentVersion) {
                state = storeResult
                return
            }
            do {
                var req = URLRequest(url: Self.githubEndpoint, timeoutInterval: 12)
                req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
                let (data, resp) = try await URLSession.shared.data(for: req)
                guard (resp as? HTTPURLResponse)?.statusCode == 200,
                      let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let tag = json["tag_name"] as? String,
                      let urlString = json["html_url"] as? String,
                      let url = URL(string: urlString) else {
                    state = .failed
                    return
                }
                let latest = tag.hasPrefix("v") ? String(tag.dropFirst()) : tag
                let notes = Self.cleanNotes(json["body"] as? String ?? "")
                state = VersionCheck.isNewer(latest, than: currentVersion)
                    ? .available(version: latest, url: url, notes: notes)
                    : .upToDate(version: latest)
            } catch {
                state = .failed
            }
        }
    }

    /// Best-effort GitHub Releases lookup for the iOS/macOS shell (package id still Android-oriented
    /// in the catalog; we match `com.noop.whoop` as the shipped lineage until an IPA row exists).
    private static func checkStore(currentVersion: String) async -> State? {
        for catalog in storeCatalogs {
            do {
                var req = URLRequest(url: catalog, timeoutInterval: 8)
                req.setValue("application/json", forHTTPHeaderField: "Accept")
                let (data, resp) = try await URLSession.shared.data(for: req)
                guard (resp as? HTTPURLResponse)?.statusCode == 200,
                      let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                      let apps = root["apps"] as? [[String: Any]],
                      let app = apps.first(where: { ($0["id"] as? String) == "com.noop.whoop" }),
                      let versionName = app["versionName"] as? String else { continue }
                let notes = cleanNotes(app["changelog"] as? String ?? "")
                let apkRel = app["apk"] as? String ?? ""
                let base = catalog.deletingLastPathComponent().absoluteString
                let url = URL(string: apkRel.hasPrefix("http") ? apkRel : base + apkRel)
                    ?? catalog
                return VersionCheck.isNewer(versionName, than: currentVersion)
                    ? .available(version: versionName, url: url, notes: notes)
                    : .upToDate(version: versionName)
            } catch {
                continue
            }
        }
        return nil
    }

    /// Turn a GitHub release body into a short, readable "what's new" for an inline preview: drop the
    /// "Downloads"/footer boilerplate, strip the heaviest markdown markers, and cap the length.
    static func cleanNotes(_ body: String) -> String {
        var s = body.components(separatedBy: "Downloads").first ?? body
        for marker in ["**", "## ", "# "] { s = s.replacingOccurrences(of: marker, with: "") }
        s = s.trimmingCharacters(in: .whitespacesAndNewlines)
        if s.count > 700 { s = String(s.prefix(700)).trimmingCharacters(in: .whitespacesAndNewlines) + "â€¦" }
        return s
    }
}
