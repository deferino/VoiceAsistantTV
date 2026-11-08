package com.voiceasistanttv.stt

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

/**
 * Sleep timer ("30 dakika sonra muzigi durdur" / "stop the music in 30 minutes"): an alarm that presses "pause" on the
 * player that was active last when the time is up. The alarm belongs to the system, so it also fires if this app's process
 * was stopped in between (it does not survive a restart of the TV).
 */
object SleepTimer {
    private const val TAG = "VoiceTV"
    private const val ACTION = "com.voiceasistanttv.stt.SLEEP_TIMER"

    private fun pending(ctx: Context, create: Boolean): PendingIntent? {
        val app = ctx.applicationContext
        val intent = Intent(app, SleepTimerReceiver::class.java).setAction(ACTION)
        val flags = (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE) or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(app, 7, intent, flags)
    }

    private fun alarms(ctx: Context): AlarmManager = ctx.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Starts (or restarts) the timer. Returns false if the alarm could not be set. */
    fun start(ctx: Context, minutes: Int): Boolean {
        val pi = pending(ctx, true) ?: return false
        val am = alarms(ctx)
        val at = SystemClock.elapsedRealtime() + minutes * 60_000L
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // No permission for an exact alarm: a few minutes of slack do not matter for a sleep timer.
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pi)
            }
        } catch (e: Exception) {
            Log.w(TAG, "sleep timer: could not set the alarm: ${e.message}")
            return false
        }
        Log.i(TAG, "sleep timer: set for $minutes min")
        return true
    }

    /** Cancels the timer. Returns true if one was set. */
    fun cancel(ctx: Context): Boolean {
        val pi = pending(ctx, false)
        if (pi == null) return false
        try {
            alarms(ctx).cancel(pi)
            pi.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "sleep timer: cancel failed: ${e.message}")
        }
        Log.i(TAG, "sleep timer: cancelled")
        return true
    }

    /** The time is up: pause the player (twice, in case the first press was lost) and tell the user. */
    fun fire(ctx: Context, done: () -> Unit) {
        Log.i(TAG, "sleep timer: time is up")
        VoiceCommands.sendMedia(ctx, VoiceCommands.Media.PAUSE)
        try {
            Toast.makeText(ctx.applicationContext, Lang.s(R.string.cmd_timer_fired), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val am = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (am.isMusicActive) VoiceCommands.sendMedia(ctx, VoiceCommands.Media.PAUSE)
            done()
        }, 1500L)
    }
}

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pr = goAsync()
        SleepTimer.fire(context) { pr.finish() }
    }
}
