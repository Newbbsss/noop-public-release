package com.noop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.noop.data.StrengthPlanStore
import com.noop.data.WorkoutRow
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

// MARK: - Strength trainer (imported lifting + plans)

@Composable
internal fun StrengthTrainerSection(rows: List<WorkoutRow>, effectiveRange: WorkoutRange) {
    val summary = remember(rows) { strengthSummary(rows) }
    val heat = remember(rows) { muscleHeatFromRows(rows) }
    val topMuscles = remember(heat) { heat.rankedLabels() }
    val hue = DomainTheme.Effort.color

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = "Strength Trainer",
            overline = "Lifting · not cardio Effort",
            trailing = when {
                summary.sessions > 0 -> "${summary.sessions} · ${effectiveRange.caption}"
                else -> "Plans below"
            },
        )
        NoopCard(tint = null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(hue.copy(alpha = 0.16f))
                            .semantics { contentDescription = "Strength Trainer" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            tint = hue,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (summary.sessions > 0) "Imported lifting" else "No lifting imports yet",
                            style = NoopType.headline,
                            color = Palette.textPrimary,
                        )
                        Text(
                            if (summary.sessions > 0) {
                                buildString {
                                    append("Volume stays separate from cardio Effort unless a session has real HR.")
                                    summary.lastSessionLabel?.let { append(" · Last: $it") }
                                }
                            } else {
                                "Import Hevy CSV or Liftosaur JSON in Data Sources — or build a plan below and log a session. Muscle heat needs exercise names in the log."
                            },
                            style = NoopType.footnote,
                            color = Palette.textSecondary,
                        )
                    }
                }
                CardDivider()
                Row(modifier = Modifier.fillMaxWidth()) {
                    MiniStat("Sessions", "${summary.sessions}", Modifier.weight(1f), tint = hue)
                    MiniStat("Volume", summary.volumeLabel, Modifier.weight(1f), tint = Palette.metricAmber)
                    MiniStat("Sets", summary.setsLabel, Modifier.weight(1f))
                    MiniStat(
                        "Minutes",
                        if (summary.minutes > 0) "${summary.minutes}" else "—",
                        Modifier.weight(1f),
                    )
                }
                if (summary.sessions > 0) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        MiniStat("Moves", summary.exercisesLabel, Modifier.weight(1f))
                        MiniStat("Avg vol", summary.avgVolumeLabel, Modifier.weight(1f), tint = Palette.metricAmber)
                        MiniStat("Avg min", summary.avgMinutesLabel, Modifier.weight(1f))
                        MiniStat(
                            "Top",
                            topMuscles.firstOrNull()?.replaceFirstChar { it.titlecase(Locale.US) } ?: "—",
                            Modifier.weight(1f),
                            tint = hue,
                        )
                    }
                }
                StrengthMuscleMap(summary, heat)
                if (summary.sessions == 0) {
                    Text(
                        "Silhouette stays quiet until a lifting import or a logged Strength session with exercise names.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                } else {
                    if (topMuscles.isNotEmpty()) {
                        Text(
                            "Heat emphasis · " + topMuscles.joinToString(" · ") {
                                it.replaceFirstChar { c -> c.titlecase(Locale.US) }
                            },
                            style = NoopType.footnote,
                            color = Palette.textSecondary,
                        )
                    }
                    StrengthMuscleLegend(heat)
                    Text(
                        "Recovery: heavy volume days often need an easier next day. Heat is inferred from exercise names — not EMG.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                }
            }
        }
    }
}

internal data class MuscleHeat(
    val chest: Float = 0f,
    val back: Float = 0f,
    val shoulders: Float = 0f,
    val arms: Float = 0f,
    val core: Float = 0f,
    val quads: Float = 0f,
    val hammies: Float = 0f,
    val glutes: Float = 0f,
    val calves: Float = 0f,
) {
    fun rankedLabels(limit: Int = 3): List<String> =
        listOf(
            "chest" to chest,
            "back" to back,
            "shoulders" to shoulders,
            "arms" to arms,
            "core" to core,
            "quads" to quads,
            "hamstrings" to hammies,
            "glutes" to glutes,
            "calves" to calves,
        ).filter { it.second > 0.05f }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }

    fun a11ySummary(): String {
        val ranked = rankedLabels(4)
        return if (ranked.isEmpty()) "No muscle heat yet"
        else "Inferred heat: " + ranked.joinToString(", ")
    }
}

/** Infer relative muscle emphasis from free-text notes / sport labels — honest "inferred", not measured. */
internal fun muscleHeatFromRows(rows: List<WorkoutRow>): MuscleHeat {
    val lifting = rows.filter {
        WorkoutEditing.classify(it.source) == WorkoutSource.LIFTING ||
            WorkoutEditing.displaySport(it.sport).contains("strength", ignoreCase = true)
    }
    if (lifting.isEmpty()) return MuscleHeat()
    var chest = 0f; var back = 0f; var shoulders = 0f; var arms = 0f
    var core = 0f; var quads = 0f; var hammies = 0f; var glutes = 0f; var calves = 0f
    for (row in lifting) {
        val t = (row.notes.orEmpty() + " " + row.sport).lowercase(Locale.US)
        fun hit(vararg keys: String): Boolean = keys.any { t.contains(it) }
        if (hit("bench", "chest", "pec", "push-up", "pushup", "fly", "dip")) chest += 1f
        if (hit("row", "pull-up", "pullup", "lat", "deadlift", "back", "chin-up")) back += 1f
        if (hit("shoulder", "ohp", "overhead press", "lateral raise", "delt", "face pull")) shoulders += 1f
        if (hit("curl", "tricep", "bicep", "arm", "skull", "pushdown")) arms += 1f
        if (hit("core", "ab", "plank", "crunch", "dead bug")) core += 1f
        if (hit("squat", "leg press", "lunge", "quad", "leg extension", "split squat")) quads += 1f
        if (hit("hamstring", "rdl", "leg curl", "good morning", "nordic")) hammies += 1f
        if (hit("glute", "hip thrust", "bridge", "kickback")) glutes += 1f
        if (hit("calf", "calves")) calves += 1f
    }
    val max = listOf(chest, back, shoulders, arms, core, quads, hammies, glutes, calves)
        .maxOrNull()?.coerceAtLeast(0.01f) ?: 1f
    fun n(v: Float) = (v / max).coerceIn(0f, 1f)
    return MuscleHeat(
        n(chest), n(back), n(shoulders), n(arms), n(core),
        n(quads), n(hammies), n(glutes), n(calves),
    )
}

@Composable
private fun StrengthMuscleLegend(heat: MuscleHeat) {
    val items = listOf(
        "Chest" to heat.chest,
        "Back" to heat.back,
        "Shoulders" to heat.shoulders,
        "Arms" to heat.arms,
        "Core" to heat.core,
        "Quads" to heat.quads,
        "Hammies" to heat.hammies,
        "Glutes" to heat.glutes,
        "Calves" to heat.calves,
    ).filter { it.second > 0.08f }
    if (items.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.take(5).forEach { (label, w) ->
            val a = (0.18f + w * 0.55f).coerceIn(0.18f, 0.75f)
            Text(
                label,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(DomainTheme.Effort.color.copy(alpha = a))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                style = NoopType.footnote,
                color = Palette.textPrimary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StrengthMuscleMap(summary: StrengthTrainerSummary, heat: MuscleHeat) {
    val active = DomainTheme.Effort.color
    val skin = Palette.textPrimary.copy(alpha = 0.22f)
    val outline = Palette.hairline.copy(alpha = 0.85f)
    val base = if (summary.sessions > 0) 0.18f else 0.10f
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .semantics { contentDescription = heat.a11ySummary() },
    ) {
        val scale = min(size.width / 280f, size.height / 240f)
        val cx = size.width / 2f
        fun sx(v: Float) = cx + v * scale
        fun sy(v: Float) = 10f * scale + v * scale
        fun fillFor(weight: Float): Color = active.copy(alpha = base + weight * 0.62f)
        fun oval(x: Float, y: Float, rw: Float, rh: Float, color: Color) {
            drawOval(color, topLeft = Offset(sx(x - rw), sy(y - rh)), size = Size(rw * 2 * scale, rh * 2 * scale))
            drawOval(outline, topLeft = Offset(sx(x - rw), sy(y - rh)), size = Size(rw * 2 * scale, rh * 2 * scale), style = Stroke(1.2f * scale))
        }
        fun limb(x: Float, y: Float, rw: Float, rh: Float, color: Color, round: Float = 12f) {
            val corner = androidx.compose.ui.geometry.CornerRadius(round * scale, round * scale)
            drawRoundRect(color, topLeft = Offset(sx(x - rw), sy(y - rh)), size = Size(rw * 2 * scale, rh * 2 * scale), cornerRadius = corner)
            drawRoundRect(outline, topLeft = Offset(sx(x - rw), sy(y - rh)), size = Size(rw * 2 * scale, rh * 2 * scale), cornerRadius = corner, style = Stroke(1.2f * scale))
        }
        drawOval(
            Color.Black.copy(alpha = 0.18f),
            topLeft = Offset(sx(-36f), sy(198f)),
            size = Size(72f * scale, 10f * scale),
        )
        oval(0f, 16f, 13f, 14f, skin)
        limb(0f, 32f, 6f, 5f, skin, round = 8f)
        oval(0f, 42f, 22f, 10f, fillFor(heat.back))
        oval(-30f, 48f, 13f, 11f, fillFor(heat.shoulders))
        oval(30f, 48f, 13f, 11f, fillFor(heat.shoulders))
        oval(-10f, 62f, 14f, 16f, fillFor(heat.chest))
        oval(10f, 62f, 14f, 16f, fillFor(heat.chest))
        limb(0f, 84f, 16f, 18f, fillFor(heat.core), round = 10f)
        limb(-46f, 68f, 9f, 22f, fillFor(heat.arms), round = 14f)
        limb(46f, 68f, 9f, 22f, fillFor(heat.arms), round = 14f)
        limb(-50f, 98f, 7f, 18f, fillFor(heat.arms * 0.7f), round = 10f)
        limb(50f, 98f, 7f, 18f, fillFor(heat.arms * 0.7f), round = 10f)
        oval(0f, 108f, 22f, 12f, fillFor(heat.glutes))
        limb(-15f, 142f, 12f, 34f, fillFor(heat.quads), round = 16f)
        limb(15f, 142f, 12f, 34f, fillFor(heat.quads), round = 16f)
        limb(-15f, 150f, 9f, 18f, fillFor(heat.hammies), round = 12f)
        limb(15f, 150f, 9f, 18f, fillFor(heat.hammies), round = 12f)
        limb(-15f, 184f, 8f, 18f, fillFor(heat.calves), round = 10f)
        limb(15f, 184f, 8f, 18f, fillFor(heat.calves), round = 10f)
        drawCircle(active.copy(alpha = 0.28f), radius = 4f * scale, center = Offset(sx(0f), sy(66f)))
    }
}

// MARK: - Custom strength plans

@Composable
internal fun CustomStrengthPlansSection(
    onLogPlanSession: (StrengthPlanStore.Plan) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { StrengthPlanStore.from(context) }
    var plans by remember { mutableStateOf(store.loadPlans()) }
    var draftName by remember { mutableStateOf("Push day") }
    var draftMuscle by remember { mutableStateOf("chest") }
    var draftExercise by remember { mutableStateOf("Bench press") }
    var draftSets by remember { mutableIntStateOf(3) }
    var draftReps by remember { mutableIntStateOf(8) }
    var draftWeight by remember { mutableStateOf("") }
    var builderExercises by remember { mutableStateOf<List<StrengthPlanStore.Exercise>>(emptyList()) }
    var liveNote by remember { mutableStateOf<String?>(null) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<StrengthPlanStore.Plan?>(null) }
    var selectedPlanId by remember { mutableStateOf<Long?>(null) }
    val hue = DomainTheme.Effort.color
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = hue,
        unfocusedBorderColor = Palette.hairline,
        focusedTextColor = Palette.textPrimary,
        unfocusedTextColor = Palette.textPrimary,
        cursorColor = hue,
        focusedLabelColor = Palette.textSecondary,
        unfocusedLabelColor = Palette.textTertiary,
    )

    fun refresh() {
        plans = store.loadPlans()
    }

    fun clearDraftAfterSave() {
        builderExercises = emptyList()
        draftWeight = ""
        liveNote = null
    }

    Column(verticalArrangement = Arrangement.spacedBy(Metrics.gap)) {
        SectionHeader(
            title = "My strength plans",
            overline = "Sets · reps · muscles",
            trailing = if (plans.isEmpty()) "None yet" else "${plans.size}",
        )
        NoopCard(tint = null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Templates only — they never invent HR, Effort, or clinical data. Log a plan to create a manual Strength session; Effort appears only when the strap records real HR.",
                    style = NoopType.footnote,
                    color = Palette.textSecondary,
                )

                Text("Plan name", style = NoopType.overline, color = Palette.textSecondary)
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it.take(60) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Strength plan name" },
                    singleLine = true,
                    colors = fieldColors,
                    placeholder = { Text("Push day", color = Palette.textTertiary) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StrengthPlanStore.PLAN_NAME_PRESETS.take(3).forEach { name ->
                        PlanNameChip(name, draftName.equals(name, ignoreCase = true)) {
                            draftName = name
                            val starter = StrengthPlanStore.starterPlan(name)
                            draftMuscle = starter.primaryMuscle
                            draftExercise = starter.exercises.firstOrNull()?.name ?: draftExercise
                            builderExercises = starter.exercises
                            liveNote = "Loaded \"$name\" starter moves — edit, then Save plan."
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    StrengthPlanStore.PLAN_NAME_PRESETS.drop(3).forEach { name ->
                        PlanNameChip(name, draftName.equals(name, ignoreCase = true)) {
                            draftName = name
                            val starter = StrengthPlanStore.starterPlan(name)
                            draftMuscle = starter.primaryMuscle
                            draftExercise = starter.exercises.firstOrNull()?.name ?: draftExercise
                            builderExercises = starter.exercises
                            liveNote = "Loaded \"$name\" starter moves — edit, then Save plan."
                        }
                    }
                }

                Text("Muscle · exercise", style = NoopType.overline, color = Palette.textSecondary)
                StrengthPlanStore.MUSCLE_GROUPS.chunked(5).forEach { chunk ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        chunk.forEach { g ->
                            val on = draftMuscle == g
                            Text(
                                g.replaceFirstChar { it.titlecase(Locale.US) },
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (on) Palette.metricAmber.copy(0.28f) else Palette.surfaceInset)
                                    .clickable {
                                        draftMuscle = g
                                        StrengthPlanStore.EXERCISE_PRESETS[g]?.firstOrNull()?.let { draftExercise = it }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                style = NoopType.footnote,
                                color = Palette.textPrimary,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StrengthPlanStore.EXERCISE_PRESETS[draftMuscle].orEmpty().take(4).forEach { ex ->
                        Text(
                            ex,
                            modifier = Modifier
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (draftExercise == ex) hue.copy(0.22f) else Palette.surfaceInset)
                                .clickable { draftExercise = ex }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            style = NoopType.footnote,
                            color = Palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedTextField(
                    value = draftExercise,
                    onValueChange = { draftExercise = it.take(80) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Exercise name" },
                    singleLine = true,
                    label = { Text("Exercise") },
                    colors = fieldColors,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    StepperChip("Sets", draftSets, 1, 12) { draftSets = it }
                    StepperChip("Reps", draftReps, 1, 30) { draftReps = it }
                    OutlinedTextField(
                        value = draftWeight,
                        onValueChange = { v ->
                            if (v.isEmpty() || v.matches(Regex("""\d{0,3}(\.\d{0,1})?"""))) draftWeight = v
                        },
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Weight kilograms optional" },
                        singleLine = true,
                        label = { Text("kg") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = fieldColors,
                    )
                }
                Text(
                    "Draft move · $draftMuscle · $draftSets×$draftReps" +
                        (draftWeight.toDoubleOrNull()?.let { " · ${StrengthPlanStore.formatKg(it)} kg" } ?: "") +
                        " · $draftExercise",
                    style = NoopType.footnote,
                    color = Palette.textTertiary,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    WetBounceButton(
                        label = "+ Move",
                        tint = Palette.metricAmber,
                        modifier = Modifier.weight(1f),
                    ) {
                        val name = draftExercise.trim().ifBlank { "Exercise" }
                        val weight = draftWeight.toDoubleOrNull()?.takeIf { it > 0 }
                        builderExercises = builderExercises + StrengthPlanStore.Exercise(
                            id = System.currentTimeMillis(),
                            name = name,
                            muscleGroup = draftMuscle,
                            sets = draftSets.coerceIn(1, 20),
                            reps = draftReps.coerceIn(1, 50),
                            weightKg = weight,
                        )
                        liveNote = "Added $name · ${builderExercises.size} move(s) in draft."
                    }
                    WetBounceButton(
                        label = "Save plan",
                        tint = hue,
                        modifier = Modifier.weight(1f),
                    ) {
                        val moves = builderExercises.ifEmpty {
                            listOf(
                                StrengthPlanStore.Exercise(
                                    id = System.currentTimeMillis() + 1,
                                    name = draftExercise.ifBlank { "Exercise" },
                                    muscleGroup = draftMuscle,
                                    sets = draftSets.coerceIn(1, 20),
                                    reps = draftReps.coerceIn(1, 50),
                                    weightKg = draftWeight.toDoubleOrNull()?.takeIf { it > 0 },
                                ),
                            )
                        }
                        val plan = StrengthPlanStore.Plan(
                            id = System.currentTimeMillis(),
                            name = draftName.ifBlank { "Workout" },
                            exercises = moves,
                        )
                        store.upsert(plan)
                        refresh()
                        selectedPlanId = plan.id
                        liveNote = "Saved \"${plan.name}\" · ${plan.totalMoves} moves · ${plan.totalSets} sets."
                        clearDraftAfterSave()
                    }
                }

                if (builderExercises.isNotEmpty()) {
                    CardDivider()
                    Text(
                        "Draft · ${builderExercises.size} moves · ${builderExercises.sumOf { it.sets }} sets",
                        style = NoopType.subhead,
                        color = Palette.textSecondary,
                    )
                    builderExercises.forEachIndexed { idx, ex ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                ex.summaryLine(),
                                style = NoopType.footnote,
                                color = Palette.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(
                                onClick = {
                                    builderExercises = builderExercises.toMutableList().also { it.removeAt(idx) }
                                },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove draft move", tint = Palette.textSecondary)
                            }
                        }
                    }
                }

                liveNote?.let {
                    Text(it, style = NoopType.subhead, color = Palette.textSecondary)
                }

                if (plans.isEmpty()) {
                    CardDivider()
                    Text(
                        "No plans yet · tap a starter (Push / Pull / Legs) or add a move, then Save plan.",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                } else {
                    CardDivider()
                    Text(
                        "Saved plans · tap to expand · Log adds a manual Strength session (45 min window)",
                        style = NoopType.footnote,
                        color = Palette.textTertiary,
                    )
                    plans.forEach { p ->
                        val selected = selectedPlanId == p.id
                        val open = expandedId == p.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        selected -> hue.copy(alpha = 0.14f)
                                        else -> Palette.surfaceInset.copy(alpha = 0.55f)
                                    },
                                )
                                .clickable {
                                    selectedPlanId = p.id
                                    expandedId = if (open) null else p.id
                                }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.name, style = NoopType.subhead, color = Palette.textPrimary)
                                    Text(
                                        "${p.totalMoves} moves · ${p.totalSets} sets · ${p.primaryMuscle}" +
                                            (p.estimatedVolumeKg.takeIf { it > 0 }?.let {
                                                " · ~${StrengthPlanStore.formatKg(it)} kg"
                                            } ?: ""),
                                        style = NoopType.footnote,
                                        color = Palette.textTertiary,
                                    )
                                }
                                Icon(
                                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = if (open) "Collapse plan" else "Expand plan",
                                    tint = Palette.textSecondary,
                                )
                            }
                            if (open) {
                                p.exercises.forEach { ex ->
                                    Text(ex.summaryLine(), style = NoopType.footnote, color = Palette.textSecondary)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        store.markUsed(p.id)
                                        onLogPlanSession(p)
                                        refresh()
                                        liveNote = "Logged \"${p.name}\" · check All Sessions below."
                                    }) { Text("Log session") }
                                    IconButton(
                                        onClick = {
                                            store.duplicate(p.id)
                                            refresh()
                                            liveNote = "Duplicated \"${p.name}\"."
                                        },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate plan", tint = Palette.textSecondary)
                                    }
                                    IconButton(
                                        onClick = { deleteTarget = p },
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete plan", tint = Palette.statusCritical)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }

    deleteTarget?.let { plan ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete plan?") },
            text = {
                Text("Remove \"${plan.name}\" (${plan.totalMoves} moves). Logged sessions stay in All Sessions.")
            },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(plan.id)
                    if (selectedPlanId == plan.id) selectedPlanId = null
                    if (expandedId == plan.id) expandedId = null
                    deleteTarget = null
                    refresh()
                    liveNote = "Deleted \"${plan.name}\"."
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun PlanNameChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        name,
        modifier = Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) DomainTheme.Effort.color.copy(0.28f) else Palette.surfaceInset)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = NoopType.footnote,
        color = Palette.textPrimary,
    )
}

@Composable
private fun StepperChip(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = NoopType.overline, color = Palette.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onChange((value - 1).coerceAtLeast(min)) },
                modifier = Modifier.size(48.dp),
            ) {
                Text("−", style = NoopType.headline, color = Palette.textPrimary)
            }
            Text(
                "$value",
                style = NoopType.number(16f),
                color = Palette.textPrimary,
                modifier = Modifier.width(28.dp),
            )
            IconButton(
                onClick = { onChange((value + 1).coerceAtMost(max)) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase $label", tint = Palette.textPrimary)
            }
        }
    }
}
