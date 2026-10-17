package de.mirrafit.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibration and sound – directly via the Android APIs, no WebView. */
object Feedback {

    private fun vibrator(ctx: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= 31) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * Pattern as in the HTML app: vibrate, pause, vibrate, ... (all in milliseconds).
     * buzz(ctx, 30, 60, 30) = short, pause, short.
     * Returns false if the device reports no vibrator or an error occurred.
     */
    @Suppress("DEPRECATION")
    fun buzz(ctx: Context, vararg pattern: Long): Boolean {
        return try {
            val v = vibrator(ctx)
            if (v == null || !v.hasVibrator()) return false
            val timings = longArrayOf(0L) + pattern
            val effect = VibrationEffect.createWaveform(timings, -1)
            // Marked as "alarm": vibrates even if touch feedback is off or the phone is on silent.
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            v.vibrate(effect, attrs)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Three short beeps (the last one higher), via the media stream. */
    fun beep(onDone: () -> Unit = {}) {
        val tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: RuntimeException) {
            onDone()
            return
        }
        val h = Handler(Looper.getMainLooper())
        //tone.startTone(ToneGenerator.TONE_SUP_DIAL, 200)
        h.postDelayed({ tone.startTone(ToneGenerator.TONE_SUP_DIAL, 300) }, 0)
        h.postDelayed({ tone.startTone(ToneGenerator.TONE_SUP_DIAL, 300) }, 400)
        h.postDelayed({
            tone.release()
            onDone()
        }, 1100)
    }
}

/**
 * End of the rest timer. In the foreground the app reports the end itself (see AppViewModel).
 * Additionally an exact alarm runs via AlarmManager so it also vibrates and beeps with the
 * screen off or in the background.
 */
object RestAlarm {
    private const val REQUEST_CODE = 4711
    private var firedFor = 0L

    /** true = this timer end has not been reported yet (prevents double sound/vibration). */
    @Synchronized
    fun claim(endMs: Long): Boolean {
        if (firedFor == endMs) return false
        firedFor = endMs
        return true
    }

    private fun pending(ctx: Context, endMs: Long, sound: Boolean, vibrate: Boolean): PendingIntent {
        val i = Intent(ctx, RestAlarmReceiver::class.java)
        i.putExtra("end", endMs)
        i.putExtra("sound", sound)
        i.putExtra("vibrate", vibrate)
        return PendingIntent.getBroadcast(
            ctx,
            REQUEST_CODE,
            i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(ctx: Context, endMs: Long, sound: Boolean, vibrate: Boolean) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, endMs, sound, vibrate)
        try {
            val exact = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs, pi)
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, 0L, true, true))
    }
}

class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val end = intent.getLongExtra("end", 0L)
        if (!RestAlarm.claim(end)) return // the app already reported the end itself
        val pendingResult = goAsync()
        val vibrate = intent.getBooleanExtra("vibrate", true)
        val sound = intent.getBooleanExtra("sound", true)
        if (vibrate) Feedback.buzz(context, 400, 150, 400)
        if (sound) {
            Feedback.beep { pendingResult.finish() }
        } else {
            pendingResult.finish()
        }
    }
}
