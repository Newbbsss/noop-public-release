package com.noop.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * User-authored strength workouts (sets / reps / muscle groups).
 * Local-only; never invents HR or cardio Effort. Volume is note-based until a session is logged.
 */
class StrengthPlanStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Exercise(
        val id: Long,
        val name: String,
        val muscleGroup: String,
        val sets: Int,
        val reps: Int,
        val weightKg: Double? = null,
        val notes: String = "",
    ) {
        val volumeKg: Double
            get() = (weightKg ?: 0.0) * sets.coerceAtLeast(0) * reps.coerceAtLeast(0)

        fun summaryLine(): String {
            val w = weightKg?.let { " · ${formatKg(it)} kg" }.orEmpty()
            return "$name · $sets×$reps$w · $muscleGroup"
        }
    }

    data class Plan(
        val id: Long,
        val name: String,
        val exercises: List<Exercise>,
        val createdAtMs: Long = System.currentTimeMillis(),
        val updatedAtMs: Long = System.currentTimeMillis(),
        val lastUsedAtMs: Long = 0L,
        val notes: String = "",
    ) {
        val totalSets: Int get() = exercises.sumOf { it.sets.coerceAtLeast(0) }
        val totalMoves: Int get() = exercises.size
        val estimatedVolumeKg: Double get() = exercises.sumOf { it.volumeKg }
        val primaryMuscle: String
            get() = exercises.groupingBy { it.muscleGroup }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key
                ?: "full"

        fun sessionNotes(): String {
            val lines = exercises.joinToString(" · ") {
                val w = it.weightKg?.let { kg -> " @${formatKg(kg)}kg" }.orEmpty()
                "${it.name} ${it.sets}×${it.reps}$w"
            }
            val vol = estimatedVolumeKg.takeIf { it > 0 }?.let { " · volume load ${formatKg(it)} kg" }.orEmpty()
            return "Strength plan \"$name\" · ${exercises.size} exercises · $totalSets sets$vol · $lines"
                .take(480)
        }
    }

    fun loadPlans(): List<Plan> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val ex = o.optJSONArray("exercises") ?: JSONArray()
                    val exercises = buildList {
                        for (j in 0 until ex.length()) {
                            val e = ex.getJSONObject(j)
                            add(
                                Exercise(
                                    id = e.optLong("id"),
                                    name = e.optString("name").ifBlank { "Exercise" },
                                    muscleGroup = normalizeMuscle(e.optString("muscleGroup", "full")),
                                    sets = e.optInt("sets", 3).coerceIn(1, 20),
                                    reps = e.optInt("reps", 8).coerceIn(1, 50),
                                    weightKg = e.optDouble("weightKg").takeIf { !it.isNaN() && it > 0 },
                                    notes = e.optString("notes"),
                                ),
                            )
                        }
                    }
                    add(
                        Plan(
                            id = o.optLong("id"),
                            name = o.optString("name").ifBlank { "Workout" },
                            exercises = exercises,
                            createdAtMs = o.optLong("createdAtMs"),
                            updatedAtMs = o.optLong("updatedAtMs"),
                            lastUsedAtMs = o.optLong("lastUsedAtMs"),
                            notes = o.optString("notes"),
                        ),
                    )
                }
            }.sortedWith(
                compareByDescending<Plan> { it.lastUsedAtMs.coerceAtLeast(it.updatedAtMs) }
                    .thenByDescending { it.updatedAtMs },
            )
        }.getOrDefault(emptyList())
    }

    fun savePlans(plans: List<Plan>) {
        val arr = JSONArray()
        for (p in plans.takeLast(40)) {
            val ex = JSONArray()
            for (e in p.exercises.take(40)) {
                ex.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("name", e.name.trim().take(80))
                        .put("muscleGroup", normalizeMuscle(e.muscleGroup))
                        .put("sets", e.sets.coerceIn(1, 20))
                        .put("reps", e.reps.coerceIn(1, 50))
                        .put("weightKg", e.weightKg ?: JSONObject.NULL)
                        .put("notes", e.notes.take(120)),
                )
            }
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name.trim().take(60))
                    .put("exercises", ex)
                    .put("createdAtMs", p.createdAtMs)
                    .put("updatedAtMs", p.updatedAtMs)
                    .put("lastUsedAtMs", p.lastUsedAtMs)
                    .put("notes", p.notes.take(200)),
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun upsert(plan: Plan) {
        val all = loadPlans().toMutableList()
        val idx = all.indexOfFirst { it.id == plan.id }
        val stamped = plan.copy(updatedAtMs = System.currentTimeMillis())
        if (idx >= 0) all[idx] = stamped else all.add(stamped)
        savePlans(all)
    }

    fun delete(id: Long) {
        savePlans(loadPlans().filterNot { it.id == id })
    }

    fun duplicate(id: Long): Plan? {
        val src = loadPlans().firstOrNull { it.id == id } ?: return null
        val now = System.currentTimeMillis()
        val copy = src.copy(
            id = now,
            name = "${src.name} copy".take(60),
            createdAtMs = now,
            updatedAtMs = now,
            lastUsedAtMs = 0L,
            exercises = src.exercises.mapIndexed { i, e -> e.copy(id = now + 1 + i) },
        )
        upsert(copy)
        return copy
    }

    fun markUsed(id: Long) {
        val all = loadPlans().toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx < 0) return
        all[idx] = all[idx].copy(lastUsedAtMs = System.currentTimeMillis(), updatedAtMs = System.currentTimeMillis())
        savePlans(all)
    }

    companion object {
        private const val PREFS = "noop_strength_plans"
        private const val KEY = "plans_json"

        val MUSCLE_GROUPS = listOf(
            "chest", "back", "shoulders", "arms", "core",
            "quads", "hamstrings", "glutes", "calves", "full",
        )

        val PLAN_NAME_PRESETS = listOf("Push day", "Pull day", "Legs", "Full body", "Upper", "Lower")

        val EXERCISE_PRESETS: Map<String, List<String>> = mapOf(
            "chest" to listOf("Bench press", "Incline press", "Chest fly", "Push-up"),
            "back" to listOf("Barbell row", "Lat pulldown", "Pull-up", "Seated row"),
            "shoulders" to listOf("OHP", "Lateral raise", "Face pull", "Arnold press"),
            "arms" to listOf("Curl", "Tricep pushdown", "Hammer curl", "Skull crusher"),
            "core" to listOf("Plank", "Cable crunch", "Hanging knee raise", "Dead bug"),
            "quads" to listOf("Back squat", "Leg press", "Lunge", "Leg extension"),
            "hamstrings" to listOf("RDL", "Leg curl", "Good morning", "Nordic curl"),
            "glutes" to listOf("Hip thrust", "Glute bridge", "Cable kickback", "Step-up"),
            "calves" to listOf("Standing calf raise", "Seated calf raise"),
            "full" to listOf("Deadlift", "Clean", "Thruster", "Farmer carry"),
        )

        fun normalizeMuscle(raw: String): String {
            val t = raw.trim().lowercase()
            return when {
                t in MUSCLE_GROUPS -> t
                t.startsWith("ham") -> "hamstrings"
                t == "legs" || t == "leg" -> "quads"
                t == "abs" || t == "ab" -> "core"
                t == "delt" || t == "delts" -> "shoulders"
                t == "bicep" || t == "tricep" || t == "biceps" || t == "triceps" -> "arms"
                else -> "full"
            }
        }

        fun starterPlan(name: String): Plan {
            val now = System.currentTimeMillis()
            val muscle = when (name.lowercase()) {
                "push day", "upper" -> "chest"
                "pull day" -> "back"
                "legs", "lower" -> "quads"
                else -> "full"
            }
            val names = EXERCISE_PRESETS[muscle].orEmpty().take(3).ifEmpty { listOf("Compound lift") }
            return Plan(
                id = now,
                name = name,
                exercises = names.mapIndexed { i, n ->
                    Exercise(
                        id = now + 1 + i,
                        name = n,
                        muscleGroup = muscle,
                        sets = 3,
                        reps = if (muscle == "core") 12 else 8,
                    )
                },
                createdAtMs = now,
                updatedAtMs = now,
            )
        }

        fun formatKg(v: Double): String =
            if (v >= 100) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v).trimEnd('0').trimEnd('.')

        @Volatile private var instance: StrengthPlanStore? = null
        fun from(context: Context): StrengthPlanStore {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: StrengthPlanStore(app).also { instance = it }
            }
        }
    }
}
