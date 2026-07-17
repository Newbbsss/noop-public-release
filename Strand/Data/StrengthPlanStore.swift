import Foundation

/// User-authored strength workouts (sets / reps / muscle groups).
/// Twin of Android `StrengthPlanStore` — slim port for iOS parity (8.6.164).
/// Local-only; never invents HR or cardio Effort. Volume is note-based until a session is logged.
final class StrengthPlanStore: ObservableObject {
    static let shared = StrengthPlanStore()

    private let defaults = UserDefaults.standard
    private let key = "noop_strength_plans_json"

    @Published private(set) var plans: [Plan] = []

    struct Exercise: Identifiable, Equatable, Codable {
        var id: Int64
        var name: String
        var muscleGroup: String
        var sets: Int
        var reps: Int
        var weightKg: Double?
        var notes: String

        var volumeKg: Double {
            (weightKg ?? 0) * Double(max(sets, 0)) * Double(max(reps, 0))
        }

        func summaryLine() -> String {
            let w = weightKg.map { " · \(Self.formatKg($0)) kg" } ?? ""
            return "\(name) · \(sets)×\(reps)\(w) · \(muscleGroup)"
        }

        static func formatKg(_ v: Double) -> String {
            if v >= 100 { return "\(Int(v.rounded()))" }
            var s = String(format: "%.1f", v)
            if s.hasSuffix(".0") { s = String(s.dropLast(2)) }
            return s
        }
    }

    struct Plan: Identifiable, Equatable, Codable {
        var id: Int64
        var name: String
        var exercises: [Exercise]
        var createdAtMs: Int64
        var updatedAtMs: Int64
        var lastUsedAtMs: Int64
        var notes: String

        var totalSets: Int { exercises.reduce(0) { $0 + max($1.sets, 0) } }
        var totalMoves: Int { exercises.count }
        var estimatedVolumeKg: Double { exercises.reduce(0) { $0 + $1.volumeKg } }

        func sessionNotes() -> String {
            let lines = exercises.map { e -> String in
                let w = e.weightKg.map { " @\(Exercise.formatKg($0))kg" } ?? ""
                return "\(e.name) \(e.sets)×\(e.reps)\(w)"
            }.joined(separator: " · ")
            let vol = estimatedVolumeKg > 0
                ? " · volume load \(Exercise.formatKg(estimatedVolumeKg)) kg"
                : ""
            let body = "Strength plan \"\(name)\" · \(exercises.count) exercises · \(totalSets) sets\(vol) · \(lines)"
            return String(body.prefix(480))
        }
    }

    static let muscleGroups = [
        "chest", "back", "shoulders", "arms", "core",
        "quads", "hamstrings", "glutes", "calves", "full",
    ]

    static let planNamePresets = ["Push day", "Pull day", "Legs", "Full body", "Upper", "Lower"]

    static let exercisePresets: [String: [String]] = [
        "chest": ["Bench press", "Incline press", "Chest fly", "Push-up"],
        "back": ["Barbell row", "Lat pulldown", "Pull-up", "Seated row"],
        "shoulders": ["OHP", "Lateral raise", "Face pull", "Arnold press"],
        "arms": ["Curl", "Tricep pushdown", "Hammer curl", "Skull crusher"],
        "core": ["Plank", "Cable crunch", "Hanging knee raise", "Dead bug"],
        "quads": ["Back squat", "Leg press", "Lunge", "Leg extension"],
        "hamstrings": ["RDL", "Leg curl", "Good morning", "Nordic curl"],
        "glutes": ["Hip thrust", "Glute bridge", "Cable kickback", "Step-up"],
        "calves": ["Standing calf raise", "Seated calf raise"],
        "full": ["Deadlift", "Clean", "Thruster", "Farmer carry"],
    ]

    private init() { reload() }

    func reload() {
        guard let data = defaults.data(forKey: key),
              let decoded = try? JSONDecoder().decode([Plan].self, from: data) else {
            plans = []
            return
        }
        plans = decoded.sorted {
            max($0.lastUsedAtMs, $0.updatedAtMs) > max($1.lastUsedAtMs, $1.updatedAtMs)
        }
    }

    private func persist() {
        let capped = Array(plans.prefix(40))
        plans = capped
        if let data = try? JSONEncoder().encode(capped) {
            defaults.set(data, forKey: key)
        }
    }

    func upsert(_ plan: Plan) {
        var all = plans
        let stamped = Plan(
            id: plan.id,
            name: plan.name,
            exercises: plan.exercises,
            createdAtMs: plan.createdAtMs,
            updatedAtMs: Int64(Date().timeIntervalSince1970 * 1000),
            lastUsedAtMs: plan.lastUsedAtMs,
            notes: plan.notes
        )
        if let idx = all.firstIndex(where: { $0.id == stamped.id }) {
            all[idx] = stamped
        } else {
            all.append(stamped)
        }
        plans = all
        persist()
        reload()
    }

    func delete(id: Int64) {
        plans = plans.filter { $0.id != id }
        persist()
    }

    @discardableResult
    func duplicate(id: Int64) -> Plan? {
        guard let src = plans.first(where: { $0.id == id }) else { return nil }
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let copy = Plan(
            id: now,
            name: String((src.name + " copy").prefix(60)),
            exercises: src.exercises.enumerated().map { i, e in
                var e2 = e
                e2.id = now + 1 + Int64(i)
                return e2
            },
            createdAtMs: now,
            updatedAtMs: now,
            lastUsedAtMs: 0,
            notes: src.notes
        )
        upsert(copy)
        return copy
    }

    func markUsed(id: Int64) {
        guard let idx = plans.firstIndex(where: { $0.id == id }) else { return }
        var p = plans[idx]
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        p.lastUsedAtMs = now
        p.updatedAtMs = now
        plans[idx] = p
        persist()
        reload()
    }

    static func normalizeMuscle(_ raw: String) -> String {
        let t = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if muscleGroups.contains(t) { return t }
        if t.hasPrefix("ham") { return "hamstrings" }
        if t == "legs" || t == "leg" { return "quads" }
        if t == "abs" || t == "ab" { return "core" }
        if t == "delt" || t == "delts" { return "shoulders" }
        if ["bicep", "tricep", "biceps", "triceps"].contains(t) { return "arms" }
        return "full"
    }

    static func starterPlan(name: String) -> Plan {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let muscle: String = {
            switch name.lowercased() {
            case "push day", "upper": return "chest"
            case "pull day": return "back"
            case "legs", "lower": return "quads"
            default: return "full"
            }
        }()
        let names = (exercisePresets[muscle] ?? []).prefix(3)
        let list = names.isEmpty ? ["Compound lift"] : Array(names)
        return Plan(
            id: now,
            name: name,
            exercises: list.enumerated().map { i, n in
                Exercise(
                    id: now + 1 + Int64(i),
                    name: n,
                    muscleGroup: muscle,
                    sets: 3,
                    reps: muscle == "core" ? 12 : 8,
                    weightKg: nil,
                    notes: ""
                )
            },
            createdAtMs: now,
            updatedAtMs: now,
            lastUsedAtMs: 0,
            notes: ""
        )
    }
}
