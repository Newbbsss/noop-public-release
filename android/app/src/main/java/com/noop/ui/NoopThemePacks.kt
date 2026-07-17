package com.noop.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Named visual packs on top of Light/Dark/System. Append to [all] → Settings picks it up.
 * Keep [id] stable (persisted). Prefer distinct surface + accent pairs.
 */
data class ThemePack(
    val id: String,
    val label: String,
    val blurb: String,
    val dark: PaletteTokens,
    val light: PaletteTokens,
    /** Translucent frosted-glass bottom nav crescents. */
    val frostedNav: Boolean = false,
    /** Soft aura hairline on frosted cards. */
    val cardAura: Boolean = false,
    /** Sparse petal/leaf motif overlay on liquid sky (nature packs). */
    val natureMotif: Boolean = false,
    val swatch: Color = dark.accent,
)

object ThemePacks {
    val TitaniumGold = ThemePack(
        id = "titanium_gold",
        label = "Titanium Gold",
        blurb = "Warm dark surfaces with NOOP's gold accent — the default look.",
        dark = DarkTokens,
        light = LightTokens,
        swatch = DarkTokens.accent,
    )

    val OceanGlass = ThemePack(
        id = "ocean_glass",
        label = "Ocean Glass",
        blurb = "Cool navy with frosted glass nav.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF071018),
            surfaceRaised = Color(0xFF0E1A24),
            surfaceInset = Color(0xFF132230),
            accent = Color(0xFF5EC8D8),
            accentHover = Color(0xFF7AD4E0),
            accentMuted = Color(0xFF2A6A78),
            restColor = Color(0xFF4AA8C8),
            gold = Color(0xFF5EC8D8),
            goldLight = Color(0xFF7AD4E0),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD0DCE0),
            surfaceRaised = Color(0xFFECEAE6),
            accent = Color(0xFF1A7A8C),
            gold = Color(0xFF1A7A8C),
        ),
        frostedNav = true,
        swatch = Color(0xFF5EC8D8),
    )

    val EmberNight = ThemePack(
        id = "ember_night",
        label = "Ember Night",
        blurb = "Charcoal ground, copper ember — not cream paper.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF0E0B09),
            surfaceRaised = Color(0xFF181410),
            surfaceInset = Color(0xFF221A14),
            accent = Color(0xFFD4783C),
            accentHover = Color(0xFFE69055),
            accentMuted = Color(0xFF7A3E1C),
            effortColor = Color(0xFFD48848),
            gold = Color(0xFFD4783C),
            goldLight = Color(0xFFE69055),
            textPrimary = Color(0xFFF2E8DE),
            textSecondary = Color(0xFFB8A090),
        ),
        light = LightTokens.copy(
            // Cool warm-gray paper (avoid cream + terracotta AI cluster).
            surfaceBase = Color(0xFFD2CEC9),
            surfaceRaised = Color(0xFFE8E4E0),
            surfaceInset = Color(0xFFE0DAD4),
            accent = Color(0xFFA04A1C),
            accentHover = Color(0xFFB85A28),
            gold = Color(0xFFA04A1C),
            textPrimary = Color(0xFF1C1410),
            textSecondary = Color(0xFF5A4A40),
        ),
        swatch = Color(0xFFD4783C),
    )

    val ForestQuiet = ThemePack(
        id = "forest_quiet",
        label = "Forest Quiet",
        blurb = "Deep moss, calm recovery green.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF0A120E),
            surfaceRaised = Color(0xFF121A16),
            accent = Color(0xFF7CB89A),
            accentHover = Color(0xFF96C8AE),
            accentMuted = Color(0xFF3A6A52),
            chargeColor = Color(0xFF7CB89A),
            gold = Color(0xFF7CB89A),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD0D6D2),
            accent = Color(0xFF2F6B4F),
            gold = Color(0xFF2F6B4F),
        ),
        natureMotif = true,
        swatch = Color(0xFF7CB89A),
    )

    val MeadowBloom = ThemePack(
        id = "meadow_bloom",
        label = "Meadow Bloom",
        blurb = "Soft clover and wildflower light.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF0C1410),
            surfaceRaised = Color(0xFF152018),
            accent = Color(0xFF9CCB7A),
            accentHover = Color(0xFFB4DC96),
            accentMuted = Color(0xFF4A7040),
            restColor = Color(0xFF8EB8D0),
            gold = Color(0xFF9CCB7A),
            goldLight = Color(0xFFC8E8A8),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD2D8CE),
            accent = Color(0xFF4A8A38),
            gold = Color(0xFF4A8A38),
        ),
        cardAura = true,
        natureMotif = true,
        swatch = Color(0xFF9CCB7A),
    )

    val CherryGrove = ThemePack(
        id = "cherry_grove",
        label = "Cherry Grove",
        blurb = "Petal pink under canopy green.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF120E12),
            surfaceRaised = Color(0xFF1C161A),
            accent = Color(0xFFE8A0B8),
            accentHover = Color(0xFFF0B8C8),
            accentMuted = Color(0xFF7A4058),
            restColor = Color(0xFFE8A0B8),
            gold = Color(0xFFE8A0B8),
            goldLight = Color(0xFFF5C8D8),
            chargeColor = Color(0xFF7CB89A),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFDED4D8),
            accent = Color(0xFFB85A78),
            gold = Color(0xFFB85A78),
        ),
        cardAura = true,
        natureMotif = true,
        swatch = Color(0xFFE8A0B8),
    )

    val WillowMist = ThemePack(
        id = "willow_mist",
        label = "Willow Mist",
        blurb = "Silver willow leaves in fog.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF0C1010),
            surfaceRaised = Color(0xFF161C1A),
            accent = Color(0xFFA8C4B8),
            accentHover = Color(0xFFC0D8CC),
            accentMuted = Color(0xFF4A6058),
            gold = Color(0xFFA8C4B8),
            goldLight = Color(0xFFD0E4DC),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD0D6D2),
            accent = Color(0xFF3A6A58),
            gold = Color(0xFF3A6A58),
        ),
        frostedNav = true,
        natureMotif = true,
        swatch = Color(0xFFA8C4B8),
    )

    val ArcticSteel = ThemePack(
        id = "arctic_steel",
        label = "Arctic Steel",
        blurb = "Cold steel, frosted nav.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF0A0C10),
            surfaceRaised = Color(0xFF141820),
            accent = Color(0xFFA8B4C4),
            accentHover = Color(0xFFC0CAD6),
            accentMuted = Color(0xFF4A5566),
            textPrimary = Color(0xFFE8ECF2),
            gold = Color(0xFFA8B4C4),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD2D6DC),
            accent = Color(0xFF3A4555),
            gold = Color(0xFF3A4555),
        ),
        frostedNav = true,
        swatch = Color(0xFFA8B4C4),
    )

    val SunsetTrack = ThemePack(
        id = "sunset_track",
        label = "Sunset Track",
        blurb = "Dusk amber on ink — no purple rest.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF0C0B10),
            surfaceRaised = Color(0xFF16141C),
            surfaceInset = Color(0xFF1E1A24),
            accent = Color(0xFFE09A4A),
            accentHover = Color(0xFFECB068),
            accentMuted = Color(0xFF7A4A20),
            // Rest stays cool teal (not lavender/purple-slop).
            restColor = Color(0xFF5A9AAA),
            restBright = Color(0xFF78B4C4),
            gold = Color(0xFFE09A4A),
            goldLight = Color(0xFFECB068),
            textPrimary = Color(0xFFF0EAE2),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD2D0CE),
            surfaceRaised = Color(0xFFF8F7F5),
            accent = Color(0xFF9A5520),
            gold = Color(0xFF9A5520),
            restColor = Color(0xFF2A6A78),
        ),
        cardAura = true,
        swatch = Color(0xFFE09A4A),
    )

    val MidnightIndigo = ThemePack(
        id = "midnight_indigo",
        label = "Midnight Indigo",
        blurb = "Deep slate surfaces with a cool steel-blue accent.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF070A0F),
            surfaceRaised = Color(0xFF0F141C),
            surfaceInset = Color(0xFF161C28),
            // Cobalt-steel, not neon indigo/violet.
            accent = Color(0xFF5A8EC8),
            accentHover = Color(0xFF78A8D8),
            accentMuted = Color(0xFF2A4A6A),
            chargeColor = Color(0xFF5A8EC8),
            gold = Color(0xFF5A8EC8),
            goldLight = Color(0xFF78A8D8),
            textPrimary = Color(0xFFE6ECF4),
            textSecondary = Color(0xFF9AA8BC),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD0D4DA),
            surfaceRaised = Color(0xFFF8FAFC),
            accent = Color(0xFF2A5580),
            gold = Color(0xFF2A5580),
        ),
        swatch = Color(0xFF5A8EC8),
    )

    val SandInk = ThemePack(
        id = "sand_ink",
        label = "Sand & Ink",
        blurb = "Warm sand paper, ink text.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF14110E),
            surfaceRaised = Color(0xFF1E1A16),
            accent = Color(0xFFD4B896),
            textPrimary = Color(0xFFF5EDE3),
            gold = Color(0xFFD4B896),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFD6CEC2),
            surfaceRaised = Color(0xFFFAF6F0),
            accent = Color(0xFF8A6040),
            textPrimary = Color(0xFF1A1612),
            gold = Color(0xFF8A6040),
        ),
        swatch = Color(0xFFD4B896),
    )

    val NeonPulse = ThemePack(
        id = "neon_pulse",
        label = "Neon Pulse",
        blurb = "High-energy cyan with card aura.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF05080C),
            surfaceRaised = Color(0xFF0C1218),
            accent = Color(0xFF2EE6C5),
            accentHover = Color(0xFF5AF0D4),
            effortColor = Color(0xFF2EC8E6),
            gold = Color(0xFF2EE6C5),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFCEDAD6),
            accent = Color(0xFF0A8A78),
            gold = Color(0xFF0A8A78),
        ),
        frostedNav = true,
        cardAura = true,
        swatch = Color(0xFF2EE6C5),
    )

    val PinkBlossom = ThemePack(
        id = "pink_blossom",
        label = "Pink Blossom",
        blurb = "Soft floral pink, frosted glass nav.",
        dark = DarkTokens.copy(
            surfaceBase = Color(0xFF140E12),
            surfaceRaised = Color(0xFF1E141A),
            surfaceInset = Color(0xFF281820),
            surfaceOverlay = Color(0xFF241820),
            accent = Color(0xFFE8A0B8),
            accentHover = Color(0xFFF0B8CC),
            accentMuted = Color(0xFF8A5068),
            chargeColor = Color(0xFFE8A0B8),
            restColor = Color(0xFFD090B0),
            restBright = Color(0xFFE8B0C8),
            hairline = Color(0xFF4A3040),
            hairlineStrong = Color(0xFF5A3A4A),
            textPrimary = Color(0xFFF8F0F4),
            textSecondary = Color(0xFFC8A8B8),
            gold = Color(0xFFE8A0B8),
            goldLight = Color(0xFFF0B8CC),
            metricRose = Color(0xFFE8A0B8),
        ),
        light = LightTokens.copy(
            surfaceBase = Color(0xFFDED4D8),
            surfaceRaised = Color(0xFFFFFBFC),
            surfaceInset = Color(0xFFF5E8EE),
            accent = Color(0xFFC45A80),
            accentHover = Color(0xFFD07090),
            accentMuted = Color(0xFFE8B0C4),
            textPrimary = Color(0xFF2A1820),
            textSecondary = Color(0xFF6A4858),
            // Solid muted rose hairline — translucent 0x33… vanished on frost cards (#373).
            hairline = Color(0xFFD8B0C0),
            hairlineStrong = Color(0xFFC090A4),
            gold = Color(0xFFC45A80),
            metricRose = Color(0xFFC45A80),
        ),
        frostedNav = true,
        cardAura = true,
        swatch = Color(0xFFE8A0B8),
    )

    /** Append new packs here — Settings + frosted nav read this list. */
    val all: List<ThemePack> = listOf(
        TitaniumGold,
        OceanGlass,
        EmberNight,
        ForestQuiet,
        MeadowBloom,
        CherryGrove,
        WillowMist,
        ArcticSteel,
        SunsetTrack,
        MidnightIndigo,
        SandInk,
        NeonPulse,
        PinkBlossom,
    )

    fun byId(id: String?): ThemePack =
        all.firstOrNull { it.id == id } ?: TitaniumGold
}

object ThemePackPrefs {
    private const val FILE = "noop_prefs"
    private const val KEY = "theme.pack"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var packId by mutableStateOf(ThemePacks.TitaniumGold.id)
        private set

    val current: ThemePack get() = ThemePacks.byId(packId)

    fun load(ctx: Context) {
        packId = prefs(ctx).getString(KEY, ThemePacks.TitaniumGold.id)
            ?: ThemePacks.TitaniumGold.id
    }

    fun set(ctx: Context, pack: ThemePack) {
        packId = pack.id
        prefs(ctx).edit().putString(KEY, pack.id).apply()
    }
}
