package dk.decpt.focustaper

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build

/**
 * Two jobs:
 *  1. Keep one exact alarm set for the end of the running block (wakes TimerReceiver even in Doze).
 *  2. Keep the ongoing notification in sync. It uses the system Chronometer in countdown mode,
 *     so the countdown ticks in the shade and on the lock screen without our process running.
 */
object Notifier {
    const val ACTION_END = "dk.decpt.focustaper.END"
    const val ACTION_TOGGLE = "dk.decpt.focustaper.TOGGLE"
    const val ACTION_NEXT = "dk.decpt.focustaper.NEXT"

    private const val CH_ONGOING = "timer"
    private const val CH_WORK = "work_start"
    private const val CH_BREAK = "break_start"
    private const val ID_ONGOING = 1
    private const val ID_ALERT = 2

    private fun nm(ctx: Context) = ctx.getSystemService(NotificationManager::class.java)

    fun ensureChannels(ctx: Context) {
        val ongoing = NotificationChannel(CH_ONGOING, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Countdown for the current block"
            setShowBadge(false)
        }
        // Separate channels so work and break can get different sounds in system settings.
        val work = NotificationChannel(CH_WORK, "Work block starts", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        }
        val brk = NotificationChannel(CH_BREAK, "Break starts", NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
        }
        nm(ctx).createNotificationChannels(listOf(ongoing, work, brk))
    }

    private fun broadcast(ctx: Context, action: String, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, req,
            Intent(ctx, TimerReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(ctx: Context): PendingIntent =
        PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun sync(ctx: Context, e: Engine, now: Long = System.currentTimeMillis()) {
        ensureChannels(ctx)
        scheduleAlarm(ctx, e)
        postOngoing(ctx, e, now)
    }

    private fun scheduleAlarm(ctx: Context, e: Engine) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val op = broadcast(ctx, ACTION_END, 1)
        val end = e.st.endAt
        if (end == null) {
            am.cancel(op); return
        }
        try {
            if (canExact(ctx)) {
                // setAlarmClock is exempt from Doze's 9-minute limit on allow-while-idle alarms.
                am.setAlarmClock(AlarmManager.AlarmClockInfo(end, openApp(ctx)), op)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, op)
            }
        } catch (se: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, op)
        }
    }

    private fun title(e: Engine): String {
        val seg = e.current
        return if (seg.phase == Phase.WORK) {
            val (n, of) = e.workPosition()
            "Work · $n of $of"
        } else seg.phase.label
    }

    private fun postOngoing(ctx: Context, e: Engine, now: Long) {
        if (e.st.done) {
            nm(ctx).cancel(ID_ONGOING); return
        }
        val pal = Palette(false)
        val nextText = e.next?.let { "Next: ${it.phase.label} ${it.min} min" } ?: "Last block"
        val b = Notification.Builder(ctx, CH_ONGOING)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title(e))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setColor(pal.phase(e.current.phase))
            .setContentIntent(openApp(ctx))

        val end = e.st.endAt
        if (end != null) {
            b.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(end)
                .setShowWhen(true)
                .setContentText(nextText)
                .addAction(action(ctx, R.drawable.ic_pause, "Pause", ACTION_TOGGLE, 2))
        } else {
            b.setShowWhen(false)
                .setContentText("Paused · ${Fmt.mmss(e.remaining(now))} left · $nextText")
                .addAction(action(ctx, R.drawable.ic_play, "Resume", ACTION_TOGGLE, 2))
        }
        b.addAction(action(ctx, R.drawable.ic_next, "Next", ACTION_NEXT, 3))
        nm(ctx).notify(ID_ONGOING, b.build())
    }

    private fun action(ctx: Context, icon: Int, label: String, act: String, req: Int): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(ctx, icon), label, broadcast(ctx, act, req)).build()

    /** Heads-up + sound when a block ends on its own. */
    fun alert(ctx: Context, e: Engine) {
        if (!e.cfg.chime) return
        ensureChannels(ctx)
        val done = e.st.done
        val seg = e.current
        val channel = if (!done && seg.phase == Phase.WORK) CH_WORK else CH_BREAK
        val text = if (done) "Plan complete" else "${seg.phase.label} · ${seg.min} min"
        val sub = when {
            done -> "Nice work."
            e.running -> "Started automatically"
            else -> "Tap Resume to start"
        }
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(text)
            .setContentText(sub)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setColor(Palette(false).phase(if (done) Phase.LONG else seg.phase))
            .setContentIntent(openApp(ctx))
            .build()
        nm(ctx).notify(ID_ALERT, n)
    }
}

object Fmt {
    fun mmss(ms: Long): String {
        val s = (ms + 999) / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    fun dur(ms: Long): String {
        val m = Math.round(ms / 60_000.0).toInt()
        val h = m / 60
        val r = m % 60
        return when {
            h == 0 -> "${r}m"
            r == 0 -> "${h}h"
            else -> "${h}h ${r}m"
        }
    }
}
