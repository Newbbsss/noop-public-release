import SwiftUI
import StrandDesign
import WhoopStore
import Foundation

/// Slim Strength Trainer section for Workouts — plans store + Log as manual session notes.
/// Dense Android muscle-heat dial stays Android-first; this is the portable core.
struct StrengthTrainerSection: View {
    @ObservedObject private var store = StrengthPlanStore.shared
    @EnvironmentObject var repo: Repository
    var onLogged: ((String) -> Void)? = nil

    @State private var confirmDelete: StrengthPlanStore.Plan?
    @State private var showAddPreset = false

    var body: some View {
        VStack(alignment: .leading, spacing: NoopMetrics.space3) {
            SectionHeader("Strength Trainer")
            Text("Lifting · not cardio Effort. Plans stay on this device.")
                .font(StrandFont.caption)
                .foregroundStyle(StrandPalette.textTertiary)
                .fixedSize(horizontal: false, vertical: true)

            if store.plans.isEmpty {
                NoopCard {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("No strength plans yet")
                            .font(StrandFont.subhead)
                            .foregroundStyle(StrandPalette.textPrimary)
                        Text("Add a Push / Pull / Legs starter, then Log to save a manual session with the set list as notes.")
                            .font(StrandFont.caption)
                            .foregroundStyle(StrandPalette.textTertiary)
                            .fixedSize(horizontal: false, vertical: true)
                        Button("Add starter plan") { showAddPreset = true }
                            .buttonStyle(.borderedProminent)
                            .tint(StrandPalette.accent)
                    }
                }
            } else {
                ForEach(store.plans) { plan in
                    planCard(plan)
                }
                Button("Add starter plan") { showAddPreset = true }
                    .font(StrandFont.caption)
                    .foregroundStyle(StrandPalette.accent)
            }
        }
        .confirmationDialog("Delete plan?", isPresented: Binding(
            get: { confirmDelete != nil },
            set: { if !$0 { confirmDelete = nil } }
        ), titleVisibility: .visible) {
            Button("Delete", role: .destructive) {
                if let p = confirmDelete { store.delete(id: p.id) }
                confirmDelete = nil
            }
            Button("Cancel", role: .cancel) { confirmDelete = nil }
        } message: {
            Text(confirmDelete.map { "Remove \"\($0.name)\" from this device." } ?? "")
        }
        .confirmationDialog("Starter plan", isPresented: $showAddPreset, titleVisibility: .visible) {
            ForEach(StrengthPlanStore.planNamePresets, id: \.self) { name in
                Button(name) {
                    store.upsert(StrengthPlanStore.starterPlan(name: name))
                }
            }
            Button("Cancel", role: .cancel) {}
        }
    }

    private func planCard(_ plan: StrengthPlanStore.Plan) -> some View {
        NoopCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text(plan.name)
                        .font(StrandFont.subhead)
                        .foregroundStyle(StrandPalette.textPrimary)
                    Spacer()
                    Text("\(plan.totalMoves) moves · \(plan.totalSets) sets")
                        .font(StrandFont.caption)
                        .foregroundStyle(StrandPalette.textTertiary)
                }
                ForEach(plan.exercises.prefix(4)) { ex in
                    Text(ex.summaryLine())
                        .font(StrandFont.caption)
                        .foregroundStyle(StrandPalette.textSecondary)
                        .lineLimit(1)
                }
                if plan.exercises.count > 4 {
                    Text("+\(plan.exercises.count - 4) more")
                        .font(StrandFont.caption)
                        .foregroundStyle(StrandPalette.textTertiary)
                }
                HStack(spacing: 12) {
                    Button("Log session") { logPlan(plan) }
                        .buttonStyle(.borderedProminent)
                        .tint(StrandPalette.accent)
                        .frame(minHeight: 44)
                    Button("Duplicate") { _ = store.duplicate(id: plan.id) }
                        .font(StrandFont.caption)
                    Button("Delete", role: .destructive) { confirmDelete = plan }
                        .font(StrandFont.caption)
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Strength plan \(plan.name), \(plan.totalMoves) exercises")
    }

    private func logPlan(_ plan: StrengthPlanStore.Plan) {
        let now = Int(Date().timeIntervalSince1970)
        let duration = max(plan.totalSets * 90, 600)
        let row = WorkoutRow(
            startTs: now - duration,
            endTs: now,
            sport: "Strength Training",
            source: "manual",
            durationS: Double(duration),
            energyKcal: nil,
            avgHr: nil,
            maxHr: nil,
            strain: nil,
            distanceM: nil,
            zonesJSON: nil,
            notes: plan.sessionNotes()
        )
        Task {
            await repo.saveManualWorkout(row, replacing: nil)
            store.markUsed(id: plan.id)
            onLogged?("Logged \(plan.name) as a manual Strength session")
        }
    }
}
