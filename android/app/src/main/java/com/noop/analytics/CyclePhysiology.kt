package com.noop.analytics

/**
 * Cycle-aware physiology soft effects (opt-in only). Evidence-light, awareness context —
 * not medical advice, not clinical claims.
 *
 * Formula (from [PeriodCalendar.modifiersFor]):
 * - **BMR / calorie goal bump** when `bmrFactor > 1.0`:
 *   `adjustedBmr = round(baseBmrKcal * bmrFactor)`
 *   `adjustedGoal = round(baseGoalKcal * bmrFactor)`
 *   Typical luteal bump ≈ +5% (`bmrFactor = 1.05`); peri-ovulatory ≈ +2%.
 * - **Charge / recovery soft effect** when `recoveryCapacityFactor < 1.0`:
 *   `softCapacity = clamp(charge * recoveryCapacityFactor, 0, 100)`
 *   Shown as a cycle-aware capacity read beside the banked Charge score (banked score unchanged).
 *   Typical luteal soft capacity ≈ ×0.94; menstrual ≈ ×0.97.
 *
 * Never invents SpO2/BP. Never edits sleep staging. Gated on cycle tracking opt-in.
 */
object CyclePhysiology {

    data class SoftEffect(
        val phase: PeriodCalendar.CalendarPhase,
        val bmrFactor: Double,
        val recoveryCapacityFactor: Double,
        val bmrNote: String,
        val recoveryNote: String,
        /** True when BMR/goal should auto-bump (more fuel). */
        val needsMoreFuel: Boolean,
        /** True when Charge soft capacity should show (take it easy). */
        val takeItEasy: Boolean,
        val scienceCite: String,
    )

    fun softEffect(phase: PeriodCalendar.CalendarPhase): SoftEffect {
        val m = PeriodCalendar.modifiersFor(phase)
        return SoftEffect(
            phase = phase,
            bmrFactor = m.bmrFactor,
            recoveryCapacityFactor = m.recoveryCapacityFactor,
            bmrNote = m.bmrNote,
            recoveryNote = m.recoveryNote,
            needsMoreFuel = m.bmrFactor > 1.001,
            takeItEasy = m.recoveryCapacityFactor < 0.999,
            scienceCite = m.scienceCite,
        )
    }

    fun softEffectFromSnapshot(snapshot: PeriodCalendar.Snapshot?): SoftEffect? {
        if (snapshot == null || !snapshot.enabled) return null
        if (snapshot.phase == PeriodCalendar.CalendarPhase.UNKNOWN ||
            snapshot.phase == PeriodCalendar.CalendarPhase.LEARNING
        ) {
            return null
        }
        return softEffect(snapshot.phase)
    }

    /** Adjusted BMR kcal for display / goal (cycle-aware, modest). */
    fun adjustedBmrKcal(baseBmrKcal: Double, effect: SoftEffect?): Double {
        if (effect == null || !effect.needsMoreFuel) return baseBmrKcal
        return (baseBmrKcal * effect.bmrFactor).coerceAtLeast(0.0)
    }

    /** Adjusted daily calorie goal (same factor as BMR when more fuel). */
    fun adjustedGoalKcal(baseGoalKcal: Double, effect: SoftEffect?): Double {
        if (effect == null || !effect.needsMoreFuel) return baseGoalKcal
        return (baseGoalKcal * effect.bmrFactor).coerceAtLeast(0.0)
    }

    /**
     * Soft cycle-aware Charge capacity — does **not** rewrite the banked Charge score.
     * Returns null when no soft effect applies.
     */
    fun softChargeCapacity(bankedCharge: Double?, effect: SoftEffect?): Double? {
        if (bankedCharge == null || effect == null || !effect.takeItEasy) return null
        return (bankedCharge * effect.recoveryCapacityFactor).coerceIn(0.0, 100.0)
    }

    /** Harris–Benedict revised BMR (kcal/day) from profile — same coeffs as [Calories]. */
    fun baseBmrKcal(sex: String, weightKg: Double, heightCm: Double, age: Double): Double {
        val w = if (weightKg > 0) weightKg else 70.0
        val h = if (heightCm > 0) heightCm else 170.0
        val a = if (age > 0) age else 30.0
        val c = Calories.resolveCoeffs(sex)
        val heightM = h / 100.0
        return (c.restingAlpha + c.restingWeight * w + c.restingHeight * heightM - c.restingAge * a)
            .coerceAtLeast(0.0)
    }
}
