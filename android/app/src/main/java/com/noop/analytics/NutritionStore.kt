package com.noop.analytics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Local-only nutrition log (meals + supplements). SharedPreferences JSON — no cloud.
 * Pattern mirrors hydration: day-keyed totals + discrete entries for the Nutrition surface.
 */
object NutritionStore {

    val mutationSeq = kotlinx.coroutines.flow.MutableStateFlow(0)

    private const val PREFS = "noop.nutrition.v1"
    private const val KEY_MEALS = "meals"
    private const val KEY_SUPPS = "supplements"
    private val DAY: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    data class MealEntry(
        val id: String,
        val day: String,
        val label: String,
        val kcal: Int,
        val proteinG: Int = 0,
        val carbsG: Int = 0,
        val fatG: Int = 0,
        val createdAtMs: Long,
    )

    data class SupplementEntry(
        val id: String,
        val day: String,
        val name: String,
        val dose: String,
        val taken: Boolean,
        val createdAtMs: Long,
    )

    /** Built-in supplement catalog (documented like Sleep hydration quick-adds). */
    val SUPPLEMENT_CATALOG: List<Pair<String, String>> = listOf(
        "Creatine" to "5 g",
        "Magnesium" to "200–400 mg",
        "Vitamin D" to "as labeled",
        "Omega-3" to "as labeled",
        "Electrolytes" to "1 serving",
        "Protein powder" to "1 scoop",
        "Caffeine" to "as logged",
        "Multivitamin" to "1 serving",
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun todayKey(): String = LocalDate.now().format(DAY)

    fun loadMeals(context: Context): List<MealEntry> {
        val raw = prefs(context).getString(KEY_MEALS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        MealEntry(
                            id = o.getString("id"),
                            day = o.getString("day"),
                            label = o.optString("label", "Meal"),
                            kcal = o.optInt("kcal", 0),
                            proteinG = o.optInt("proteinG", 0),
                            carbsG = o.optInt("carbsG", 0),
                            fatG = o.optInt("fatG", 0),
                            createdAtMs = o.optLong("createdAtMs", 0L),
                        ),
                    )
                }
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    fun loadSupplements(context: Context): List<SupplementEntry> {
        val raw = prefs(context).getString(KEY_SUPPS, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        SupplementEntry(
                            id = o.getString("id"),
                            day = o.getString("day"),
                            name = o.getString("name"),
                            dose = o.optString("dose", ""),
                            taken = o.optBoolean("taken", true),
                            createdAtMs = o.optLong("createdAtMs", 0L),
                        ),
                    )
                }
            }.sortedByDescending { it.createdAtMs }
        }.getOrDefault(emptyList())
    }

    private fun saveMeals(context: Context, meals: List<MealEntry>) {
        val arr = JSONArray()
        for (m in meals) {
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("day", m.day)
                    .put("label", m.label)
                    .put("kcal", m.kcal)
                    .put("proteinG", m.proteinG)
                    .put("carbsG", m.carbsG)
                    .put("fatG", m.fatG)
                    .put("createdAtMs", m.createdAtMs),
            )
        }
        prefs(context).edit().putString(KEY_MEALS, arr.toString()).apply()
        mutationSeq.value += 1
    }

    private fun saveSupplements(context: Context, rows: List<SupplementEntry>) {
        val arr = JSONArray()
        for (s in rows) {
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("day", s.day)
                    .put("name", s.name)
                    .put("dose", s.dose)
                    .put("taken", s.taken)
                    .put("createdAtMs", s.createdAtMs),
            )
        }
        prefs(context).edit().putString(KEY_SUPPS, arr.toString()).apply()
        mutationSeq.value += 1
    }

    fun logMeal(
        context: Context,
        label: String,
        kcal: Int,
        proteinG: Int = 0,
        carbsG: Int = 0,
        fatG: Int = 0,
        day: String = todayKey(),
    ): MealEntry {
        val entry = MealEntry(
            id = UUID.randomUUID().toString(),
            day = day,
            label = label.ifBlank { "Meal" },
            kcal = kcal.coerceAtLeast(0),
            proteinG = proteinG.coerceAtLeast(0),
            carbsG = carbsG.coerceAtLeast(0),
            fatG = fatG.coerceAtLeast(0),
            createdAtMs = System.currentTimeMillis(),
        )
        val next = loadMeals(context).toMutableList()
        next.add(0, entry)
        // Keep last 90 days of meals.
        val cutoff = LocalDate.now().minusDays(90).format(DAY)
        saveMeals(context, next.filter { it.day >= cutoff })
        return entry
    }

    fun removeMeal(context: Context, id: String) {
        saveMeals(context, loadMeals(context).filterNot { it.id == id })
    }

    /** Undo the most recently logged meal (any day) — Fable DO NOW meals polish. */
    fun undoLastMeal(context: Context): MealEntry? {
        val all = loadMeals(context)
        val last = all.maxByOrNull { it.createdAtMs } ?: return null
        saveMeals(context, all.filterNot { it.id == last.id })
        return last
    }

    fun logSupplement(
        context: Context,
        name: String,
        dose: String,
        taken: Boolean = true,
        day: String = todayKey(),
    ): SupplementEntry {
        val entry = SupplementEntry(
            id = UUID.randomUUID().toString(),
            day = day,
            name = name.ifBlank { "Supplement" },
            dose = dose,
            taken = taken,
            createdAtMs = System.currentTimeMillis(),
        )
        val next = loadSupplements(context).toMutableList()
        next.add(0, entry)
        val cutoff = LocalDate.now().minusDays(90).format(DAY)
        saveSupplements(context, next.filter { it.day >= cutoff })
        return entry
    }

    fun removeSupplement(context: Context, id: String) {
        saveSupplements(context, loadSupplements(context).filterNot { it.id == id })
    }

    fun mealsForDay(context: Context, day: String = todayKey()): List<MealEntry> =
        loadMeals(context).filter { it.day == day }

    fun supplementsForDay(context: Context, day: String = todayKey()): List<SupplementEntry> =
        loadSupplements(context).filter { it.day == day }

    fun dayKcal(context: Context, day: String = todayKey()): Int =
        mealsForDay(context, day).sumOf { it.kcal }

    /** Optional macros totals for today (0 when none logged). */
    data class DayMacros(val proteinG: Int, val carbsG: Int, val fatG: Int)

    fun dayMacros(context: Context, day: String = todayKey()): DayMacros {
        val meals = mealsForDay(context, day)
        return DayMacros(
            proteinG = meals.sumOf { it.proteinG },
            carbsG = meals.sumOf { it.carbsG },
            fatG = meals.sumOf { it.fatG },
        )
    }

    /**
     * Last [days] local-day protein totals (g), oldest → newest. Empty days are 0 so a sparkline
     * always has a full window when any macros have been logged recently.
     */
    fun proteinSeriesLastDays(context: Context, days: Int = 7): List<Double> {
        val n = days.coerceAtLeast(1)
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { offset ->
            val key = today.minusDays(offset.toLong()).format(DAY)
            dayMacros(context, key).proteinG.toDouble()
        }
    }

    /** Last [days] carbs (g), oldest → newest. */
    fun carbsSeriesLastDays(context: Context, days: Int = 7): List<Double> {
        val n = days.coerceAtLeast(1)
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { offset ->
            val key = today.minusDays(offset.toLong()).format(DAY)
            dayMacros(context, key).carbsG.toDouble()
        }
    }

    /** Last [days] fat (g), oldest → newest. */
    fun fatSeriesLastDays(context: Context, days: Int = 7): List<Double> {
        val n = days.coerceAtLeast(1)
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { offset ->
            val key = today.minusDays(offset.toLong()).format(DAY)
            dayMacros(context, key).fatG.toDouble()
        }
    }

    /** Last [days] meal kcal totals, oldest → newest. */
    fun kcalSeriesLastDays(context: Context, days: Int = 7): List<Double> {
        val n = days.coerceAtLeast(1)
        val today = LocalDate.now()
        return (n - 1 downTo 0).map { offset ->
            val key = today.minusDays(offset.toLong()).format(DAY)
            dayKcal(context, key).toDouble()
        }
    }
}
