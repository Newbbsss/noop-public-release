package com.noop.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import com.noop.R

/**
 * In-app language override (System / English / Español / Français / Deutsch).
 * Uses AndroidX per-app locales so [values-es]/[values-fr]/[values-de] packs apply without
 * changing the phone’s system language. Mirrors [AppearancePrefs] persistence shape.
 *
 * Requires [MainActivity] to extend AppCompatActivity (and an AppCompat theme) —
 * [AppCompatDelegate.setApplicationLocales] is a no-op from ComponentActivity because
 * AppCompat never registers an activity delegate / LocaleManager context.
 */
enum class AppLanguage(val storageValue: String, val languageTag: String?) {
    SYSTEM("system", null),
    ENGLISH("en", "en"),
    SPANISH("es", "es"),
    FRENCH("fr", "fr"),
    GERMAN("de", "de");

    fun label(ctx: Context): String = when (this) {
        SYSTEM -> ctx.getString(R.string.settings_language_system)
        ENGLISH -> ctx.getString(R.string.settings_language_english)
        SPANISH -> ctx.getString(R.string.settings_language_spanish)
        FRENCH -> ctx.getString(R.string.settings_language_french)
        GERMAN -> ctx.getString(R.string.settings_language_german)
    }

    companion object {
        fun fromStorage(raw: String?): AppLanguage =
            entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
    }
}

object LocalePrefs {
    private const val FILE = "noop_prefs"
    private const val KEY = "app.language"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var language by mutableStateOf(AppLanguage.SYSTEM)
        private set

    fun load(ctx: Context) {
        language = AppLanguage.fromStorage(prefs(ctx).getString(KEY, AppLanguage.SYSTEM.storageValue))
        apply(language)
    }

    fun set(ctx: Context, value: AppLanguage) {
        language = value
        prefs(ctx).edit().putString(KEY, value.storageValue).apply()
        apply(value)
    }

    /** Apply the stored (or given) language via AppCompat per-app locales. */
    fun apply(value: AppLanguage = language) {
        val locales = if (value.languageTag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(value.languageTag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
