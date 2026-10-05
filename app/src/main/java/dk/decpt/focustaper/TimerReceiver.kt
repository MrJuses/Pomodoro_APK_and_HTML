package dk.decpt.focustaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the end-of-block alarm, notification buttons, and re-arming after reboot or update. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val now = System.currentTimeMillis()
        val e = Store.load(ctx)
        when (intent.action) {
            Notifier.ACTION_END -> if (e.catchUp(now) > 0) Notifier.alert(ctx, e)
            Notifier.ACTION_TOGGLE -> e.toggle(now)
            Notifier.ACTION_NEXT -> e.skip(now)
            // Alarms are wiped on reboot; skip past whatever ended meanwhile, then re-arm.
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> e.catchUp(now)
        }
        Store.save(ctx, e)
        Notifier.sync(ctx, e, now)
    }
}
