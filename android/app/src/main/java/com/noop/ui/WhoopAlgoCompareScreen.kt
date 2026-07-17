package com.noop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noop.analytics.HcNoopAlign
import com.noop.data.DailyMetric
import com.noop.data.ModelEvolutionStore
import com.noop.data.WhoopAppScoreStore
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * First-class product surface: NOOP open-BLE scorers vs official WHOOP **app** labels
 * (Charge / Effort / Rest / Stress — four heads). Ships in FullRelease; not Test Centre chrome.
 *
 * Today also embeds the same card for the daily-driver path; this destination makes the
 * feature discoverable from More without scrolling past the vessel band.
 */
@Composable
fun WhoopAlgoCompareScreen(
    vm: AppViewModel,
    onOpenHealthConnect: () -> Unit = {},
) {
    val context = LocalContext.current
    val days by vm.recentDays.collectAsStateWithLifecycle()
    val dayKey = remember { LocalDate.now().toString() }
    val appScoreStore = remember { WhoopAppScoreStore.from(context) }

    var whoopAppExport by remember { mutableStateOf<DailyMetric?>(null) }
    var whoopAppManual by remember { mutableStateOf(appScoreStore.get(dayKey)) }
    var hcSleepMinForCompare by remember { mutableStateOf<Double?>(null) }
    var showLogWhoopApp by remember { mutableStateOf(false) }

    LaunchedEffect(dayKey, days) {
        appScoreStore.seedFromAssetsIfNeeded()
        whoopAppExport = runCatching {
            vm.repo.days(WhoopAppScoreStore.DEVICE_ID).firstOrNull { it.day == dayKey }
        }.getOrNull()
        whoopAppManual = appScoreStore.get(dayKey)
        if (whoopAppManual == null &&
            whoopAppExport?.recovery == null &&
            whoopAppExport?.strain == null
        ) {
            whoopAppManual = appScoreStore.recentDays(14).firstOrNull {
                it.recoveryPct != null || it.dayStrain021 != null
            }
            if (whoopAppExport == null) {
                whoopAppExport = runCatching {
                    vm.repo.days(WhoopAppScoreStore.DEVICE_ID)
                        .filter { it.recovery != null || it.strain != null }
                        .maxByOrNull { it.day }
                }.getOrNull()
            }
        }
        hcSleepMinForCompare = runCatching {
            val zone = ZoneId.systemDefault()
            val from = LocalDate.parse(dayKey).minusDays(1).atStartOfDay(zone).toEpochSecond()
            val to = LocalDate.parse(dayKey).plusDays(1).atStartOfDay(zone).toEpochSecond()
            val sources = listOf("health-connect", "my-whoop", "whoop-app")
            sources.firstNotNullOfOrNull { src ->
                val nights = vm.repo.sleepSessions(src, from, to)
                nights.mapNotNull { s ->
                    val endDay = Instant.ofEpochSecond(s.endTs).atZone(zone).toLocalDate().toString()
                    if (endDay != dayKey) return@mapNotNull null
                    val fromStages = HcNoopAlign.stagesFromJson(s.stagesJSON)
                    val staged = (fromStages.first ?: 0.0) + (fromStages.second ?: 0.0) + (fromStages.third ?: 0.0)
                    if (staged > 0.0) staged else (s.endTs - s.effectiveStartTs) / 60.0
                }.maxOrNull()
            }
        }.getOrNull()
    }

    val noopDay = days.firstOrNull { it.day == dayKey } ?: days.maxByOrNull { it.day }
    val (whoopSide, _) = whoopAppMetricFromSources(whoopAppExport, whoopAppManual)
    val noopRec = noopDay?.recovery
    val noopStrain = noopDay?.strain
    val noopRestResolved = noopDay?.totalSleepMin?.let { HcNoopAlign.durationAsSleepPerf(it) }

    val rows = buildCompareRows(
        noopRecovery = noopRec,
        noopStrain = noopStrain,
        noopSleepPerf = noopRestResolved,
        noopStress = null,
        whoop = whoopSide,
        whoopStress = null,
        hcSleepMin = hcSleepMinForCompare,
    )
    val alignment = remember(rows, dayKey) { alignmentFromRows(dayKey, rows) }
    val evoStore = remember { ModelEvolutionStore.from(context) }
    val evolutions = remember(alignment.passScore, alignment.pairedHeads) {
        if (alignment.pairedHeads > 0) {
            evoStore.recordPassSample(
                passScore = alignment.passScore,
                nDaysPaired = alignment.pairedHeads,
                notes = alignment.summary,
            )
        }
        evoStore.loadEvolutions()
    }
    val srcNote = when {
        whoopAppManual != null ->
            "WHOOP app column from **${whoopAppManual!!.source}** labels for $dayKey " +
                "(Strain 0–21 · Recovery %). Not bracelet open BLE."
        whoopAppExport?.recovery != null || whoopAppExport?.strain != null ->
            "WHOOP app column from **Data Export** import (device whoop-app). Strain is 0–21 native."
        else ->
            "WHOOP app column empty for $dayKey. Open BLE is NOT the app’s Strain. " +
                "Import WHOOP Data Export, or tap Log WHOOP app scores (type what the app shows)."
    }

    LazyScreenScaffold(
        title = "NOOP vs WHOOP",
        subtitle = "Algo · four heads · app labels only",
        topBackground = { LiquidScreenSky() },
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WhoopScoreCompareCard(
                    dayLabel = dayKey,
                    rows = rows,
                    whoopSourceNote = srcNote,
                    alignment = alignment,
                    evolutions = evolutions,
                    onOpenHealthConnect = onOpenHealthConnect,
                )
                WetBounceButton(
                    label = "Log WHOOP app scores (from app UI)",
                    modifier = Modifier.fillMaxWidth(),
                    tint = Color(0xFF5B9DFF),
                    onClick = { showLogWhoopApp = true },
                )
            }
        }
    }

    if (showLogWhoopApp) {
        WhoopAppScoreLogDialog(
            day = dayKey,
            initial = whoopAppManual,
            onDismiss = { showLogWhoopApp = false },
            onSave = { scores ->
                appScoreStore.put(scores)
                whoopAppManual = scores
                showLogWhoopApp = false
            },
        )
    }
}
