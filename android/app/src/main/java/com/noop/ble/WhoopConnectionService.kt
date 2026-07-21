package com.noop.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.noop.BuildConfig
import com.noop.NoopApplication
import com.noop.R
import com.noop.alarm.RestedWakeEvaluator
import com.noop.alarm.SleepWindowWatcher
import com.noop.alarm.SmartAlarmScheduler
import com.noop.alarm.SmartAlarmStore
import com.noop.analytics.IllnessWatch
import com.noop.analytics.RestScorer
import com.noop.data.DailyMetric
import com.noop.location.GpsSession
import com.noop.location.LocationTracker
import com.noop.notif.BatteryAlertNotifier
import com.noop.notif.IllnessAlertNotifier
import com.noop.ui.NoopPrefs
import com.noop.ui.appLaunchIntent
import com.noop.widget.WidgetSnapshot
import com.noop.widget.WidgetSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Foreground service that keeps the WHOOP BLE connection alive while the app is backgrounded or
 * closed.
 *
 * Android tears a process down shortly after its last Activity goes away, which is exactly why
 * people on Reddit saw the strap disconnect the moment they closed NOOP. A started foreground
 * service — with an ongoing notification — keeps the process (and therefore the
 * [com.noop.NoopApplication]-owned [WhoopBleClient] and its GATT link) resident, so heart rate
 * keeps streaming and offloads keep landing in the background.
 *
 * It does **not** own or drive the connection: it simply holds the process up and mirrors the
 * client's [LiveState] into the notification. Start/stop is gated by a Settings toggle (see
 * `NoopPrefs.backgroundConnection`) and only ever happens from the foreground (on connect / when
 * the user flips the toggle), so we never trip Android 12+'s background-start restriction.
 *
 * The matching capability on macOS is free: `AppModel` is an app-level `@StateObject` kept alive by
 * the menu-bar extra, so closing the window leaves the strap connected.
 */
/**
 * One tick of the ongoing-notification/widget stream. Carries TWO day rows on purpose (#911):
 * [todayRow] is the naive/unscored today row the notification's Recovery line reads (honest-null until
 * tonight is scored), while [anchorRow] is the widget-only carried anchor (today when scored, else the
 * freshest prior scored day) so the widget describes the same day as Today without the notification ever
 * showing a carried figure as if it were live.
 */
private data class NotifyTick(
    val state: LiveState,
    val todayRow: DailyMetric?,
    val anchorRow: DailyMetric?,
    val illness: String?,
)

class WhoopConnectionService : Service() {

    /** Main-thread scope used only to mirror [LiveState] into the notification. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The single live-state→notification collector. Re-`start`s land here repeatedly (on every
     *  connect, plus any OS restart), so we cancel the old one before launching a new one. */
    private var notifyJob: Job? = null

    /** Watches [GpsSession] and runs the platform location stream while a GPS workout is active. This
     *  is what makes route tracking survive the screen turning off (#215): the collection lives on the
     *  always-on service, not the Activity-scoped ViewModel that Android cancels when it's cleared. */
    private var gpsGateJob: Job? = null

    /** The actual location collector, alive only while a GPS workout is in flight. Cancelled (which
     *  removes the LocationManager updates via the stream's awaitClose) the moment the workout ends. */
    private var gpsJob: Job? = null

    /** Platform-GPS wrapper (no Google Play Services). Lazily built — the service holds a Context. */
    private val locationTracker by lazy { LocationTracker(this) }

    /** Last illness-watch evaluation seen by the collector — clear→raised is the notify edge.
     *  In-memory on purpose: the persisted once-a-day gate (NoopPrefs) handles dedupe across
     *  process restarts and the AppViewModel call site. */
    private var lastIllnessAlert: String? = null

    /** Smart-alarm light-sleep watcher (#207). Feeds the live HR while we're inside the wake window
     *  and, on a lighter-phase reading, advances the GUARANTEED alarm earlier. It can only ever move
     *  the alarm earlier within the window — the hard deadline scheduled via AlarmManager is the floor
     *  of safety, so if BLE drops or no light sleep is found the user is still woken at the window end.
     *  The detector is reset each time we (re)enter a window. */
    private val sleepWatcher = SleepWindowWatcher()
    private var inAlarmWindow = false
    /** Wall-clock when the trough first locked this night (coarse sleep-onset proxy for rested wake). */
    private var restedSleepAnchorMs: Long = 0L

    /** The smart-alarm HR collector, alive for the life of the service. */
    private var alarmJob: Job? = null

    private val ble get() = (application as NoopApplication).ble
    private val repo get() = (application as NoopApplication).repository

    /**
     * Watches the OS Bluetooth radio so turning it off immediately tears down NOOP's orphaned GATT
     * link (#314). Without this there is no ACTION_STATE_CHANGED listener at all, so the radio going off
     * never reaches [WhoopBleClient] — the link stays "connected", the UI keeps showing live HR/buzz/sync
     * that isn't real, and the next write crashes on a dead binder (iOS/macOS are immune because
     * CoreBluetooth's send() is state-guarded). Registered while the FGS is alive (it is the long-lived
     * owner of the connection) and unregistered in [onDestroy]. STATE_TURNING_OFF/OFF → teardown +
     * connected=false; STATE_ON → resume the connection.
     */
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                // Catch TURNING_OFF (the earliest signal) AND OFF — by TURNING_OFF the binder is already
                // on its way down, so tearing down here pre-empts the crash window.
                BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> ble.onBluetoothRadioOff()
                BluetoothAdapter.STATE_ON -> ble.onBluetoothRadioOn()
            }
        }
    }

    /** True once [bluetoothStateReceiver] is registered, so repeat onStartCommands don't double-register
     *  (which would later throw on a single unregister). */
    private var bluetoothReceiverRegistered = false

    /**
     * DEBUG-only: adb can fire SignalHunt / R22 without unlocking the Fold UI.
     * ```
     * adb shell am broadcast -a com.noop.debug.SIGNAL_HUNT --es mode ff|read|research|heartkey|r22|hfs|all
     * ```
     * Refuses to arm when [NoopPrefs.lastDevice] is a stale `WHOOP 5AM…` sibling (worn MG pin only).
     * `heartkey` / `ecg` / `labrador` = GET-only HeartKey (FF + Labrador OFF); never ON / never MAIN auto.
     */
    private var signalHuntReceiverRegistered = false
    private val signalHuntHandler = Handler(Looper.getMainLooper())
    private val signalHuntReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!BuildConfig.DEBUG) return
            if (intent?.action != ACTION_SIGNAL_HUNT) return
            val mode = intent.getStringExtra(EXTRA_SIGNAL_HUNT_MODE)?.lowercase().orEmpty().ifBlank { "all" }
            if (!ble.state.value.connected) {
                Log.w(TAG_SIGNAL_HUNT, "refusing SignalHunt — not connected (mode=$mode)")
                return
            }
            val liveName = ble.state.value.advertisingName
            if (WhoopBleClient.isStaleUnwornSiblingName(liveName.orEmpty())) {
                Log.w(TAG_SIGNAL_HUNT, "refusing SignalHunt — live strap looks like unworn 5AM ($liveName)")
                return
            }
            val saved = NoopPrefs.lastDevice(this@WhoopConnectionService)
            Log.i(
                TAG_SIGNAL_HUNT,
                "SignalHunt mode=$mode addr=${ble.lastDeviceAddress} name=$liveName saved=${saved?.first}/${saved?.second}",
            )
            fun fireAll() {
                ble.fireSignalHuntFfReadSweep()
                signalHuntHandler.postDelayed({ ble.fireSignalHuntReadBurst() }, 3_000L)
                signalHuntHandler.postDelayed({ ble.fireSignalHuntResearchBurst() }, 8_000L)
                signalHuntHandler.postDelayed({ ble.enableWhoop5DeepData() }, 14_000L)
            }
            when (mode) {
                "ff", "get_ff", "128" -> ble.fireSignalHuntFfReadSweep()
                "read" -> ble.fireSignalHuntReadBurst()
                "research", "105", "106", "107", "108" -> ble.fireSignalHuntResearchBurst()
                // HeartKey/Labrador GET-only — not part of fireAll (never Labrador ON).
                "heartkey", "ecg", "labrador", "124", "125", "139" -> ble.fireHeartKeyGetOnlyProbe()
                "r22", "deep" -> ble.enableWhoop5DeepData()
                // EnterHighFreqHistoricalMode parity probe: cmd 96 with u16-LE duration, 97 exit after 90 s.
                "hfs", "highfreq", "96" -> ble.fireSignalHuntHfsProbe()
                else -> fireAll()
            }
        }
    }

    /**
     * DEBUG-only: force a gated [WhoopBleClient.syncNow] hist offload (34→22→23 / type-47) over adb —
     * same MANUAL path as UI “Sync now”. Safe no-op when disconnected / mid-backfill / policy floor.
     * ```
     * adb shell am broadcast -a com.noop.debug.SYNC_NOW -p com.noop.whoop.debug
     * ```
     * Does **not** bypass Backfiller trim-ack safety; when an offload runs, grab+trim wipe is the
     * normal Backfiller path (not a separate destructive clear).
     *
     * Bond wait: auto-gather often fires SYNC_NOW while MG is still in alongside-open-only
     * (`connected=true bonded=false`) a few seconds before CLIENT_HELLO acks. Softening the gate to
     * connected-only would still no-op inside [WhoopBleClient.requestSync] (needs bonded). DEBUG
     * defers briefly for that race; if still unbonded after wait, refuse — re-bond MG in Devices
     * (alongside-only never carries hist).
     */
    private var syncNowReceiverRegistered = false
    private val syncNowHandler = Handler(Looper.getMainLooper())
    private val syncNowReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!BuildConfig.DEBUG) return
            if (intent?.action != ACTION_SYNC_NOW) return
            // Cancel any in-flight defer from a prior broadcast so retries don't stack.
            syncNowHandler.removeCallbacksAndMessages(null)
            trySyncNowAfterBond(attempt = 0)
        }
    }

    /** DEBUG-only: run [WhoopBleClient.syncNow] once bonded, or defer through the CLIENT_HELLO race. */
    private fun trySyncNowAfterBond(attempt: Int) {
        if (!BuildConfig.DEBUG) return
        val s = ble.state.value
        if (!s.connected) {
            Log.w(TAG_SYNC_NOW, "refusing SYNC_NOW — connected=false bonded=${s.bonded}")
            return
        }
        if (!s.bonded) {
            if (attempt < SYNC_NOW_BOND_WAIT_ATTEMPTS) {
                Log.i(
                    TAG_SYNC_NOW,
                    "SYNC_NOW defer — connected=true bonded=false " +
                        "(await CLIENT_HELLO) attempt=${attempt + 1}/$SYNC_NOW_BOND_WAIT_ATTEMPTS",
                )
                syncNowHandler.postDelayed(
                    { trySyncNowAfterBond(attempt + 1) },
                    SYNC_NOW_BOND_WAIT_MS,
                )
                return
            }
            Log.w(
                TAG_SYNC_NOW,
                "refusing SYNC_NOW — connected=true bonded=false after wait " +
                    "(alongside-only / re-bond MG in Devices)",
            )
            return
        }
        val liveName = s.advertisingName
        if (WhoopBleClient.isStaleUnwornSiblingName(liveName.orEmpty())) {
            Log.w(TAG_SYNC_NOW, "refusing SYNC_NOW — live strap looks like unworn 5AM ($liveName)")
            return
        }
        Log.i(TAG_SYNC_NOW, "SYNC_NOW → ble.syncNow() name=$liveName addr=${ble.lastDeviceAddress}")
        ble.syncNow()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification "Disconnect" action routes back here as a self-intent.
        if (intent?.action == ACTION_STOP) {
            runCatching { ble.disconnect() }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        // Must call startForeground promptly after startForegroundService(). If it fails (e.g. the
        // API 34 connectedDevice type needs BLUETOOTH_CONNECT and the user denied it) we stop cleanly
        // rather than crash — the connection itself keeps working in the foreground regardless.
        if (!startForegroundCompat(buildNotification(ble.state.value, null))) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Listen for the OS Bluetooth radio toggling so turning it off tears the link down at once (#314).
        // Guarded so repeat onStartCommands (every connect / OS restart) don't stack registrations.
        if (!bluetoothReceiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    bluetoothStateReceiver,
                    IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
            }.onSuccess { bluetoothReceiverRegistered = true }
        }

        // DEBUG AFK SignalHunt (Fold lock screen / Tailscale) — exported so adb can reach it.
        if (BuildConfig.DEBUG && !signalHuntReceiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    signalHuntReceiver,
                    IntentFilter(ACTION_SIGNAL_HUNT),
                    ContextCompat.RECEIVER_EXPORTED,
                )
            }.onSuccess { signalHuntReceiverRegistered = true }
        }
        // DEBUG AFK hist Sync now — exported so auto-gather can force 34→22→23 without UI.
        if (BuildConfig.DEBUG && !syncNowReceiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    syncNowReceiver,
                    IntentFilter(ACTION_SYNC_NOW),
                    ContextCompat.RECEIVER_EXPORTED,
                )
            }.onSuccess { syncNowReceiverRegistered = true }
        }

        // Keep the ongoing notification in step with the live connection state AND today's recovery
        // (the 15-min IntelligenceEngine recompute), so it re-posts when either changes — a glanceable
        // poor-man's Live Activity (#42). daysMergedFlow is the same merged store the dashboard reads.
        notifyJob?.cancel()
        notifyJob = scope.launch {
            combine(
                ble.state,
                // Defence-in-depth: a Room/disk error in this flow would otherwise propagate uncaught
                // out of scope.launch and kill the process — the FGS exists to protect the connection,
                // not to take it down. (Audited during #82, which proved unrelated/unreproducible —
                // this guard is belt-and-braces, not a diagnosed fix.) After catch{emit} the inner
                // flow completes; combine keeps running on ble.state with days frozen.
                // #797: the bounded merge (recentDaysMergedFlow) is enough here, the notification only reads
                // today's row; this stops a years-deep import re-merging the whole history on every change.
                repo.recentDaysMergedFlow("my-whoop").catch { emit(emptyList()) },
            ) { state, days ->
                // #911: resolve the day the way the dashboard does, via the LOGICAL local day (rolls at
                // 04:00, with the #304 pre-04:00 carve-out), NOT a naive LocalDate.now() that rolls at
                // midnight and starts looking up a brand-new, not-yet-scored calendar day. Two DISTINCT
                // rows come out, so the two surfaces keep their own honest contracts:
                val logicalKey = com.noop.ui.logicalDayKeyNow()
                val localKey = java.time.LocalDate.now().toString()
                //  - todayRow: the naive/unscored today row. The ongoing notification's Recovery line must
                //    stay on THIS (honest-null until tonight is scored), never on a carried prior-day
                //    figure, or the lock-screen would silently show yesterday's Recovery% as if it were
                //    live, with no provenance caption.
                //  - anchorRow: today's row when scored, else the freshest STRICTLY-PRIOR scored day carried
                //    over (via the SHARED `widgetAnchorRow`, mirroring TodayScreen + the #547 future-day
                //    guard). ONLY the widget uses this, so the 2x2 widget shows the same day as Today rather
                //    than blanking in the small hours before tonight is scored. This keeps the service
                //    symmetric with AppViewModel, where only the widget push reads the anchor.
                val todayRow = com.noop.ui.resolveTodayRow(days, logicalKey, localKey)
                val anchorRow = com.noop.ui.widgetAnchorRow(days, logicalKey, localKey)
                NotifyTick(
                    state = state,
                    todayRow = todayRow,
                    anchorRow = anchorRow,
                    // Illness watch in the background (gated on the opt-out pref): the FGS is the
                    // only long-lived collector, so this is what makes the early-warning reach a
                    // user who hasn't opened the app today.
                    illness = if (NoopPrefs.illnessWatch(this@WhoopConnectionService)) IllnessWatch.evaluate(days) else null,
                )
            }.catch { /* belt-and-braces: a frozen notification beats a dead process */ }
                // conflate + collect, NOT collectLatest (#82): the widget push suspends in Glance
                // machinery longer than the live-HR emission interval, so collectLatest cancelled
                // every push mid-flight and the widget starved on stale data the moment HR started
                // streaming. Conflation still processes only the latest value — just without the axe.
                .conflate()
                .collect { (state, todayRow, anchorRow, illness) ->
                // Honest-null: the notification's Recovery line reads the NAIVE today row, never the
                // carried anchor, so it stays blank until tonight's recovery actually lands (#911).
                postNotification(state, todayRow?.recovery)
                // Banner transition (clear → raised) → real system notification; the notifier's
                // persisted day gate dedupes against the app-open (AppViewModel) call site.
                if (lastIllnessAlert == null && illness != null) {
                    IllnessAlertNotifier.onEvaluated(this@WhoopConnectionService, illness)
                }
                lastIllnessAlert = illness
                // Battery alerts — low (≤15%) and charge-complete (100%). The once-per-crossing
                // dedupe is persisted in NoopPrefs (BatteryAlertPolicy), so no in-memory pct tracking.
                BatteryAlertNotifier.onBatteryUpdate(
                    this@WhoopConnectionService,
                    currPct = state.batteryPct?.roundToInt(),
                    charging = state.charging,
                )
                // Feed the home-screen widget from the same stream — this service is its heartbeat
                // while the app UI is closed. Throttled + no-op without a placed widget (the store
                // checks both); runCatching so a Glance hiccup never tears down the connection.
                runCatching {
                    val prior = WidgetSnapshotStore.load(this@WhoopConnectionService)
                    val alarmStore = SmartAlarmStore.from(this@WhoopConnectionService)
                    val is24 = NoopPrefs.use24HourClock(this@WhoopConnectionService)
                    val nextAlarm = com.noop.alarm.NextAlarmDisplay.soonestShortLabel(
                        phoneEnabled = alarmStore.enabled,
                        targetMinutes = alarmStore.targetMinutes,
                        windowMinutes = alarmStore.windowMinutes,
                        customAlarms = alarmStore.customAlarms,
                        nowMs = System.currentTimeMillis(),
                        is24Hour = is24,
                    )
                    WidgetSnapshotStore.push(
                        this@WhoopConnectionService,
                        WidgetSnapshot(
                            recoveryPct = anchorRow?.recovery?.roundToInt(),
                            // Rest = the sleep_performance composite from the anchor row's banked stage
                            // figures (pure, honest-null until last night is scored); Effort = the 0-100
                            // strain. Widget-only carry, so it shows the same day as Today. (#516/#911)
                            restPct = anchorRow?.let { RestScorer.restFromDaily(it)?.roundToInt() },
                            // ≤0 strain is calm TRIMP, not a scored load — match Today honesty (8.6.155/156).
                            effortPct = anchorRow?.strain?.takeIf { it > 0.0 }?.roundToInt(),
                            heartRate = state.heartRate,
                            batteryPct = com.noop.ui.resolveStrapBatteryDisplay(
                                this@WhoopConnectionService, state,
                            )?.pctInt ?: state.batteryPct?.roundToInt(),
                            connected = state.connected,
                            updatedAtMs = System.currentTimeMillis(),
                            stressTipTenths = prior.stressTipTenths,
                            nextAlarmLabel = nextAlarm,
                        ),
                    )
                }
            }
        }

        // Drive GPS route tracking from here so it OUTLIVES the UI (#215). While a GPS workout is
        // active we collect the platform location stream into the process-level [GpsSession]; the
        // ViewModel only observes that shared route. Gated on the active flag so the location radio is
        // off (and the FGS's location type unused) outside a GPS workout. Re-`start`s land here, so we
        // cancel + relaunch the gate, never stack collectors.
        gpsGateJob?.cancel()
        gpsGateJob = scope.launch {
            GpsSession.state
                .map { it.active }
                .distinctUntilChanged()
                .collect { active ->
                    gpsJob?.cancel()
                    gpsJob = null
                    if (active) {
                        // Re-post with the location service type added so background location is
                        // permitted while tracking; on Android 14+ a service that reads location in the
                        // background must declare the location FGS type. Reverted to connectedDevice-only
                        // when the workout ends (active=false re-posts the base type).
                        startForegroundCompat(buildNotification(ble.state.value, null), tracking = true)
                        // Workouts & GPS test mode (Test Centre): wire the GpsSession fix-progress sink to the
                        // .workouts-tagged strap log ONLY when the WORKOUTS mode is on (one SharedPreferences
                        // bool read here). When off, the sink stays null and the route fold is byte-identical.
                        GpsSession.workoutsLog =
                            if (com.noop.testcentre.TestCentre.from(applicationContext)
                                    .active(com.noop.testcentre.TestDomain.WORKOUTS)
                            ) {
                                { line -> ble.externalLog(line, com.noop.testcentre.TestDomain.WORKOUTS) }
                            } else {
                                null
                            }
                        gpsJob = launch {
                            // LocationTracker fails SAFE (no permission / no provider just ends the
                            // stream); runCatching guards an OEM throw so it can't tear down the FGS.
                            runCatching {
                                locationTracker.stream().collect { pt -> GpsSession.append(pt) }
                            }
                        }
                    } else {
                        GpsSession.workoutsLog = null   // route finished: drop the test-mode sink
                        startForegroundCompat(buildNotification(ble.state.value, null), tracking = false)
                    }
                }
        }

        // Smart alarm light-sleep + rested-wake watcher (#207). While the alarm is enabled and we're
        // inside the wake window, feed each live HR reading to the pure detectors; either an early
        // HR-rise cue or a rested (sleep-need / Charge) cue may advance the GUARANTEED alarm earlier
        // (the scheduler clamps to the window and can never move it later or cancel it).
        alarmJob?.cancel()
        alarmJob = scope.launch {
            val store = SmartAlarmStore.from(this@WhoopConnectionService)
            ble.state
                .map { it.heartRate ?: 0 }
                .conflate()
                .collect { hr ->
                    if (!store.enabled || store.scheduledDeadlineMs <= 0L) {
                        inAlarmWindow = false
                        restedSleepAnchorMs = 0L
                        return@collect
                    }
                    val now = System.currentTimeMillis()
                    val inWindow = now in store.scheduledWindowStartMs until store.scheduledDeadlineMs
                    if (inWindow && !inAlarmWindow) {
                        sleepWatcher.reset()
                        restedSleepAnchorMs = 0L
                    }
                    inAlarmWindow = inWindow
                    if (!inWindow) return@collect
                    // Coarse sleep span: first plausible low HR in-window → now.
                    if (hr in 35..90 && restedSleepAnchorMs == 0L) {
                        restedSleepAnchorMs = now
                    }
                    var advanced = false
                    if (sleepWatcher.shouldWake(hr)) {
                        SmartAlarmScheduler.advanceTo(this@WhoopConnectionService, store, now)
                        advanced = true
                    }
                    if (!advanced && store.wakeWhenRested) {
                        val sleepMin = if (restedSleepAnchorMs > 0L) {
                            ((now - restedSleepAnchorMs).coerceAtLeast(0L) / 60_000.0)
                        } else 0.0
                        // Prefer overnight Charge hint; fall back to window length as a sleep proxy so
                        // rested-wake can still fire from sleep-need alone when Charge isn't scored yet.
                        val charge = store.restedChargeHint.takeIf { it > 0 }?.toDouble()
                        val sleepProxy = sleepMin.coerceAtLeast(
                            ((now - store.scheduledWindowStartMs).coerceAtLeast(0L) / 60_000.0),
                        )
                        if (RestedWakeEvaluator.shouldWake(
                                sleepMinutesSoFar = sleepProxy,
                                sleepNeedMinutes = store.restedSleepNeedMinutes.toDouble(),
                                chargeScore = charge,
                                chargeThreshold = store.restedChargeThreshold.toDouble(),
                                sleepFraction = store.restedSleepNeedPercent / 100.0,
                            )
                        ) {
                            SmartAlarmScheduler.advanceTo(this@WhoopConnectionService, store, now)
                        }
                    }
                }
        }

        // START_NOT_STICKY: the FGS's job is to keep this process *alive* (which it does while
        // running, making OS kills unlikely). We deliberately do NOT resurrect after a kill, because
        // a fresh process has no strap/model context to reconnect with — the user reopening the app
        // re-establishes it. Resurrecting would only show a "Reconnecting…" notification that never
        // resolves.
        return START_NOT_STICKY
    }

    /** Promote to the foreground. Returns false (rather than throwing) if the platform refuses. When
     *  [tracking] a GPS workout we add the location FGS type — Android 14+ requires it for a service
     *  that reads location in the background (the manifest declares `connectedDevice|location`). */
    private fun startForegroundCompat(notification: Notification, tracking: Boolean = false): Boolean = runCatching {
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val locationType = if (tracking) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or locationType
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }.isSuccess

    /** Signature of the fields the notification actually renders (#216). The live HR stream emits ~1 Hz
     *  but the notification no longer shows BPM, so we only re-post when one of THESE changes — turning
     *  a per-beat wakeup into a handful of updates a day. */
    private var lastNotificationKey: String? = null

    private fun postNotification(state: LiveState, recoveryPct: Double? = null) {
        val stale5Am = WhoopBleClient.isStaleUnwornSiblingName(state.advertisingName.orEmpty())
        val key = listOf(
            state.connected,
            state.backfilling,
            recoveryPct?.roundToInt(),
            state.batteryPct?.roundToInt(),
            stale5Am,
        ).joinToString("|")
        if (key == lastNotificationKey) return
        lastNotificationKey = key
        // Defensive: a notify() throw (OEM quirk, revoked POST_NOTIFICATIONS on some ROMs) must not
        // crash the collector and tear down the connection we exist to keep alive.
        runCatching {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.notify(NOTIF_ID, buildNotification(state, recoveryPct))
        }
    }

    private fun buildNotification(state: LiveState, recoveryPct: Double?): Notification {
        // #216: deliberately NO live BPM in the title. A per-beat-changing notification forces the
        // foreground service to re-post (and wake the device) ~once a second all day, which is a real
        // battery cost for a number nobody reads off the lock screen. The title now reflects only the
        // connection / sync state, which changes rarely — see postNotification's dedup.
        val stale5Am = WhoopBleClient.isStaleUnwornSiblingName(state.advertisingName.orEmpty())
        val title = when {
            !state.connected   -> "Reconnecting to your WHOOP…"
            state.backfilling  -> "Syncing strap history…"
            stale5Am           -> "Connected · check strap"
            else               -> "Connected to your WHOOP"
        }
        val detail = buildList {
            when {
                !state.connected -> add("Keeping the link open")
                stale5Am -> add("Live name looks like an unworn 5AM sibling — tap · Use worn MG on the snackbar")
                else -> add("Streaming in the background")
            }
            recoveryPct?.let { add("Recovery ${it.roundToInt()}%") }
            state.batteryPct?.let { add("Strap ${it.roundToInt()}%") }
        }.joinToString("  ·  ")

        // Request code 2 when deep-linking Devices so UPDATE_CURRENT does not clobber a prior
        // plain-launch PendingIntent (and vice versa) while the sibling honesty flag flips.
        val openApp = PendingIntent.getActivity(
            this,
            if (stale5Am) 2 else 0,
            appLaunchIntent(this, openDevices = stale5Am),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopAction = PendingIntent.getService(
            this,
            1,
            Intent(this, WhoopConnectionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle(title)
            .setContentText(detail)
            .setContentIntent(openApp)
            .addAction(0, "Disconnect", stopAction)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Defensive: channel creation can throw on some OEM ROMs / under memory pressure; never let
        // that crash onStartCommand (it would take the FGS — and the connection — down with it).
        runCatching {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Strap connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while NOOP keeps your WHOOP connected in the background."
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            mgr.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (bluetoothReceiverRegistered) {
            // unregisterReceiver throws if it was never registered; the flag guards that, and runCatching
            // covers the rare case the OS already reclaimed it.
            runCatching { unregisterReceiver(bluetoothStateReceiver) }
            bluetoothReceiverRegistered = false
        }
        if (signalHuntReceiverRegistered) {
            runCatching { unregisterReceiver(signalHuntReceiver) }
            signalHuntReceiverRegistered = false
        }
        if (syncNowReceiverRegistered) {
            runCatching { unregisterReceiver(syncNowReceiver) }
            syncNowReceiverRegistered = false
        }
        signalHuntHandler.removeCallbacksAndMessages(null)
        syncNowHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "noop_strap_connection"
        private const val NOTIF_ID = 4201
        private const val TAG_SIGNAL_HUNT = "NoopSignalHunt"
        private const val TAG_SYNC_NOW = "NoopSyncNow"
        /** DEBUG: poll bond after SYNC_NOW while CLIENT_HELLO may still be in flight (~2s × 15 ≈ 30s). */
        private const val SYNC_NOW_BOND_WAIT_ATTEMPTS = 15
        private const val SYNC_NOW_BOND_WAIT_MS = 2_000L
        const val ACTION_STOP = "com.noop.ble.action.STOP_CONNECTION"
        /** DEBUG AFK SignalHunt — `adb shell am broadcast -a com.noop.debug.SIGNAL_HUNT --es mode all` */
        const val ACTION_SIGNAL_HUNT = "com.noop.debug.SIGNAL_HUNT"
        const val EXTRA_SIGNAL_HUNT_MODE = "mode"
        /** DEBUG AFK Sync now — `adb shell am broadcast -a com.noop.debug.SYNC_NOW -p com.noop.whoop.debug` */
        const val ACTION_SYNC_NOW = "com.noop.debug.SYNC_NOW"

        /**
         * Promote the process to the foreground so the strap stays connected. Safe to call when
         * already running. MUST be called from a foreground context (we call it from connect / the
         * Settings toggle) to satisfy Android 12+'s background-start rule. Defensive: any failure is
         * swallowed so it can never break the core connect flow.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, WhoopConnectionService::class.java),
                )
            }
        }

        /** Drop the foreground promotion. The connection itself is torn down by the caller. */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WhoopConnectionService::class.java)) }
        }
    }
}
