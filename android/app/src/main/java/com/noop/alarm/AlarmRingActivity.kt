package com.noop.alarm

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.noop.NoopApplication
import com.noop.ui.AppearanceMode
import com.noop.ui.AppearancePrefs

/**
 * Full-screen wake surface for the phone smart alarm. Optional math challenge to dismiss;
 * at the hard deadline, can force math + louder cue when live HR looks drowsy.
 */
class AlarmRingActivity : Activity() {

    private var ringtone: Ringtone? = null
    private var problem = AlarmMathChallenge.next()
    private var requireMath = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val store = SmartAlarmStore.from(this)
        val smart = intent.getBooleanExtra(EXTRA_SMART, false)
        val isFinal = !smart
        val hr = (application as? NoopApplication)?.ble?.state?.value?.heartRate
        requireMath = AlarmMathChallenge.requireMath(
            mathEnabled = store.mathChallengeEnabled,
            mathOnDrowsy = store.mathOnDrowsyHr,
            hrBpm = hr,
            drowsyThreshold = store.drowsyHrBpm,
            isFinalDeadline = isFinal,
        )
        if (requireMath && store.mathOnDrowsyHr && isFinal && hr != null && hr < store.drowsyHrBpm) {
            // Louder final cue when drowsy path engages.
            startRingtone(louder = true)
        } else {
            startRingtone(louder = false)
        }

        // Honour AppearancePrefs so light-mode users don't get a hard lacquer slab at wake.
        AppearancePrefs.load(this)
        val dark = resolvesDarkUi()
        val bg = if (dark) 0xFF0C0D10.toInt() else 0xFFE8E4DC.toInt()
        val titleInk = if (dark) 0xFFF2F0EA.toInt() else 0xFF1A1E24.toInt()
        val bodyInk = if (dark) 0xFFA8A49A.toInt() else 0xFF5A564E.toInt()
        val accentInk = if (dark) 0xFFD4A84B.toInt() else 0xFF8A6A20.toInt()
        val hintInk = if (dark) 0xFF6E6A62.toInt() else 0xFF8A8680.toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = if (smart) "Early wake" else "Wake up"
            textSize = 28f
            setTextColor(titleInk)
        }
        val body = TextView(this).apply {
            text = if (smart) {
                "Light-sleep cue inside your window."
            } else {
                "Hard deadline — time to get up."
            }
            textSize = 16f
            setTextColor(bodyInk)
            setPadding(0, 16, 0, 24)
        }
        root.addView(title)
        root.addView(body)

        val mathPrompt = TextView(this).apply {
            textSize = 22f
            setTextColor(accentInk)
            setPadding(0, 8, 0, 12)
        }
        val answer = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Answer"
            setTextColor(titleInk)
            setHintTextColor(hintInk)
            textSize = 20f
        }
        if (requireMath) {
            problem = AlarmMathChallenge.next()
            mathPrompt.text = "Solve to dismiss · ${problem.prompt} ="
            root.addView(mathPrompt)
            root.addView(answer)
        }

        val dismiss = Button(this).apply {
            text = if (requireMath) "Check & dismiss" else "Dismiss"
            setOnClickListener {
                if (requireMath) {
                    val typed = answer.text?.toString()?.trim()?.toIntOrNull()
                    if (typed != problem.answer) {
                        Toast.makeText(this@AlarmRingActivity, "Try again", Toast.LENGTH_SHORT).show()
                        problem = AlarmMathChallenge.next()
                        mathPrompt.text = "Solve to dismiss · ${problem.prompt} ="
                        answer.text?.clear()
                        return@setOnClickListener
                    }
                }
                stopRingtone()
                (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.cancel(SmartAlarmReceiver.NOTIF_ID)
                finish()
            }
        }
        root.addView(dismiss)
        setContentView(root)
    }

    /** Match Compose [NoopTheme] dark resolve without spinning up Compose. */
    private fun resolvesDarkUi(): Boolean = when (AppearancePrefs.mode) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.SYSTEM -> {
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }

    private fun startRingtone(louder: Boolean) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.also { tone ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                tone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                tone.isLooping = true
                if (louder) tone.volume = 1f
            }
            runCatching { tone.play() }
        }
    }

    private fun stopRingtone() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    override fun onDestroy() {
        stopRingtone()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SMART = "smart"
        const val EXTRA_REQUIRE_MATH = "require_math"

        fun intent(context: Context, smart: Boolean): Intent =
            Intent(context, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_SMART, smart)
    }
}
