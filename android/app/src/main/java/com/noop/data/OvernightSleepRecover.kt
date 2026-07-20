package com.noop.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import android.util.Log
import com.noop.analytics.AnalyticsEngine
import com.noop.analytics.SleepStager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Recover overnight sleep when MAIN never scored a night the band already (partially) banked —
 * e.g. hist wiped after DEBUG offload, sibling id mismatch, or detectSleep matched=0 on a gappy
 * night. One-shot seed sources (first success wins): APK assets, app filesDir copy, app
 * Download, public Download. Never invents SpO₂; stages only from banked raw HR/gravity/sleep-state.
 */
object OvernightSleepRecover {
    private const val TAG = "NoopSleep"
    private const val SEED_NAME = "noop_overnight_recover.db"
    private const val PREFS = "noop_overnight_recover"
    private const val PREF_SEED_DONE = "seed_imported_v2"

    /** @return true when seed rows were imported and/or a sleep session was upserted. */
    suspend fun recoverIfNeeded(context: Context, repo: WhoopRepository, activeStrapId: String): Boolean {
        var changed = importSeedIfPresent(context, repo)
        changed = restageFromBankedRaw(repo, activeStrapId) || changed
        return changed
    }

    private suspend fun importSeedIfPresent(context: Context, repo: WhoopRepository): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(PREF_SEED_DONE, false)) return false

        // Always materialize into private filesDir before SQLite open — shell-pushed / Download
        // paths often fail with SQLITE_CANTOPEN_EACCES even when the file "exists".
        val private = File(context.filesDir, SEED_NAME)
        val materialized = materializeSeedToPrivate(context, private) ?: return false
        val ok = runCatching { importSeedFile(repo, materialized) }.getOrElse {
            Log.w(TAG, "overnight seed import failed for ${materialized.path}: ${it.message}")
            false
        }
        if (ok) {
            prefs.edit().putBoolean(PREF_SEED_DONE, true).apply()
            // Leave aside markers on external copies so we don't keep trying them.
            listOf(
                File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), SEED_NAME),
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    SEED_NAME,
                ),
            ).forEach { ext ->
                if (ext.isFile) {
                    val aside = File(ext.parentFile, "$SEED_NAME.imported")
                    if (!ext.renameTo(aside)) runCatching { ext.delete() }
                }
            }
        }
        return ok
    }

    private fun materializeSeedToPrivate(context: Context, dest: File): File? {
        if (dest.isFile && dest.length() >= 1_000L) return dest
        // 1) APK asset (reliable on MAIN release — no scoped-storage dance).
        val fromAsset = runCatching {
            context.assets.open(SEED_NAME).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            dest.isFile && dest.length() >= 1_000L
        }.getOrDefault(false)
        if (fromAsset) {
            Log.i(TAG, "overnight seed copied from assets (${dest.length()} bytes)")
            return dest
        }
        runCatching { if (dest.exists()) dest.delete() }
        // 2) External / Download copies (adb push) — copy bytes into private dir.
        val candidates = listOf(
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), SEED_NAME),
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                SEED_NAME,
            ),
        )
        for (src in candidates) {
            if (!src.isFile || src.length() < 1_000L) continue
            val copied = runCatching {
                src.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.isFile && dest.length() >= 1_000L
            }.getOrDefault(false)
            if (copied) {
                Log.i(TAG, "overnight seed copied from ${src.path} (${dest.length()} bytes)")
                return dest
            }
            Log.w(TAG, "overnight seed copy failed from ${src.path}")
            runCatching { if (dest.exists()) dest.delete() }
        }
        return null
    }

    private suspend fun importSeedFile(repo: WhoopRepository, seed: File): Boolean {
        val db = SQLiteDatabase.openDatabase(
            seed.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        try {
            val hr = readHr(db)
            val grav = readGravity(db)
            val sleepState = readSleepState(db)
            val sleeps = readSleepSessions(db)
            hr.groupBy { it.deviceId }.forEach { (id, rows) ->
                rows.chunked(2_000).forEach { chunk ->
                    repo.insert(StreamBatch(hr = chunk.map { HrRow(it.ts, it.bpm) }), id)
                }
            }
            grav.groupBy { it.deviceId }.forEach { (id, rows) ->
                rows.chunked(2_000).forEach { chunk ->
                    repo.insert(
                        StreamBatch(gravity = chunk.map { GravityRow(it.ts, it.x, it.y, it.z) }),
                        id,
                    )
                }
            }
            sleepState.groupBy { it.deviceId }.forEach { (id, rows) ->
                rows.chunked(800).forEach { chunk ->
                    repo.insert(
                        StreamBatch(
                            sleepState = chunk.map { SleepStateRow(it.ts, it.state, it.aux82) },
                        ),
                        id,
                    )
                }
            }
            if (sleeps.isNotEmpty()) repo.upsertSleepSessions(sleeps)
            Log.i(
                TAG,
                "overnight seed imported from ${seed.name} hr=${hr.size} grav=${grav.size} " +
                    "sleepState=${sleepState.size} sleeps=${sleeps.size}",
            )
        } finally {
            db.close()
        }
        val aside = File(seed.parentFile, "$SEED_NAME.imported")
        if (!seed.renameTo(aside)) seed.delete()
        return true
    }

    private suspend fun restageFromBankedRaw(repo: WhoopRepository, activeStrapId: String): Boolean {
        val now = System.currentTimeMillis() / 1000L
        val today = localDayString(now)
        val todayStart = localMidnightSec(now)
        val from = todayStart - 6L * 3600L
        val to = minOf(now, todayStart + 14L * 3600L)

        val existing = (
            repo.computedSleepSessionsUnion(activeStrapId, from, to) +
                repo.sleepSessionsUnion(activeStrapId, from, to)
            )
            .filter { localDayString(it.endTs) == today }
            .distinctBy { it.startTs to it.endTs }
        val best = existing.maxByOrNull { it.endTs - it.effectiveStartTs }
        val bestSpan = best?.let { it.endTs - it.effectiveStartTs } ?: 0L

        val hr = repo.hrSamplesUnion(activeStrapId, from, to, limit = 200_000)
        val grav = repo.gravitySamplesUnion(activeStrapId, from, to, limit = 200_000)
        if (hr.size < 80 || grav.size < 20) {
            Log.i(TAG, "recover restage skip: hr=${hr.size} grav=${grav.size} today=$today")
            return false
        }
        val hrLo = hr.minOf { it.ts }
        val hrHi = hr.maxOf { it.ts }
        if (bestSpan >= 3L * 3600L + 30L * 60L &&
            best != null &&
            best.effectiveStartTs <= hrLo + 45L * 60L &&
            best.endTs >= hrHi - 45L * 60L
        ) {
            return false
        }
        if (hrHi - hrLo < 90L * 60L) return false

        val rr = repo.rrIntervalsUnion(activeStrapId, from, to, limit = 200_000)
        val tz = (TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000).toLong()
        val band = siblingSleepState(repo, activeStrapId, from, to)
        val detected = SleepStager.detectSleep(
            hr = hr,
            rr = rr,
            gravity = grav,
            tzOffsetSeconds = tz,
            bandSleepState = band,
        )
        val pick = detected
            .filter { localDayString(it.end) == today || it.end >= todayStart - 3600L }
            .maxByOrNull { it.end - it.start }
            ?: detected.maxByOrNull { it.end - it.start }
            ?: return false
        val pickSpan = pick.end - pick.start
        if (pickSpan < 90L * 60L) return false
        if (best != null && best.userEdited && bestSpan >= pickSpan) return false
        if (best != null && bestSpan >= pickSpan) return false

        val computedId = repo.computedDeviceId(activeStrapId)
        repo.upsertSleepSessions(
            listOf(
                SleepSession(
                    deviceId = computedId,
                    startTs = pick.start,
                    endTs = pick.end,
                    efficiency = pick.efficiency,
                    restingHr = pick.restingHR,
                    avgHrv = pick.avgHRV,
                    stagesJSON = AnalyticsEngine.encodeStages(pick.stages),
                ),
            ),
        )
        Log.i(
            TAG,
            "recover restage upserted ${pickSpan / 60}m under $computedId " +
                "(wasSpan=${bestSpan / 60}m hr=${hr.size} grav=${grav.size})",
        )
        return true
    }

    private suspend fun siblingSleepState(
        repo: WhoopRepository,
        activeStrapId: String,
        from: Long,
        to: Long,
    ): List<Pair<Long, Int>> {
        val ids = repo.importedSourceIdsWithSiblings(activeStrapId)
        val out = ArrayList<Pair<Long, Int>>()
        val seen = HashSet<Long>()
        for (id in ids) {
            for (row in repo.sleepStateSamples(id, from, to, limit = 50_000)) {
                if (seen.add(row.ts)) out.add(row.ts to row.state)
            }
        }
        out.sortBy { it.first }
        return out
    }

    private fun localDayString(tsSec: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(tsSec * 1000L))

    private fun localMidnightSec(nowSec: Long): Long {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = nowSec * 1000L
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    private fun readHr(db: SQLiteDatabase): List<HrSample> {
        val out = ArrayList<HrSample>()
        db.rawQuery("SELECT deviceId,ts,bpm,synced FROM hrSample", null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    HrSample(
                        deviceId = c.getString(0),
                        ts = c.getLong(1),
                        bpm = c.getInt(2),
                        synced = c.getInt(3),
                    ),
                )
            }
        }
        return out
    }

    private fun readGravity(db: SQLiteDatabase): List<GravitySample> {
        val out = ArrayList<GravitySample>()
        db.rawQuery("SELECT deviceId,ts,x,y,z,synced FROM gravitySample", null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    GravitySample(
                        deviceId = c.getString(0),
                        ts = c.getLong(1),
                        x = c.getDouble(2),
                        y = c.getDouble(3),
                        z = c.getDouble(4),
                        synced = c.getInt(5),
                    ),
                )
            }
        }
        return out
    }

    private fun readSleepState(db: SQLiteDatabase): List<SleepStateSampleEntity> {
        val out = ArrayList<SleepStateSampleEntity>()
        val cols = db.rawQuery("PRAGMA table_info(sleepStateSample)", null).use { c ->
            val names = ArrayList<String>()
            while (c.moveToNext()) names.add(c.getString(1))
            names
        }
        val hasAux = "aux82" in cols
        val sql = if (hasAux) {
            "SELECT deviceId,ts,state,aux82 FROM sleepStateSample"
        } else {
            "SELECT deviceId,ts,state FROM sleepStateSample"
        }
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SleepStateSampleEntity(
                        deviceId = c.getString(0),
                        ts = c.getLong(1),
                        state = c.getInt(2),
                        aux82 = if (hasAux && !c.isNull(3)) c.getInt(3) else null,
                    ),
                )
            }
        }
        return out
    }

    private fun readSleepSessions(db: SQLiteDatabase): List<SleepSession> {
        val out = ArrayList<SleepSession>()
        db.rawQuery(
            "SELECT deviceId,startTs,endTs,efficiency,restingHr,avgHrv,stagesJSON," +
                "userEdited,startTsAdjusted,motionJSON,sleepStateJSON FROM sleepSession",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    SleepSession(
                        deviceId = c.getString(0),
                        startTs = c.getLong(1),
                        endTs = c.getLong(2),
                        efficiency = if (c.isNull(3)) null else c.getDouble(3),
                        restingHr = if (c.isNull(4)) null else c.getInt(4),
                        avgHrv = if (c.isNull(5)) null else c.getDouble(5),
                        stagesJSON = c.getString(6),
                        userEdited = c.getInt(7) != 0,
                        startTsAdjusted = if (c.isNull(8)) null else c.getLong(8),
                        motionJSON = c.getString(9),
                        sleepStateJSON = c.getString(10),
                    ),
                )
            }
        }
        return out
    }
}
