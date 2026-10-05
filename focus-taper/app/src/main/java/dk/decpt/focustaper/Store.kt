package dk.decpt.focustaper

import android.content.Context

/** SharedPreferences is the single source of truth, shared by the activity and the alarm receiver. */
object Store {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("focustaper", Context.MODE_PRIVATE)

    fun load(ctx: Context): Engine {
        val p = prefs(ctx)
        val cfg = Config(
            plan = p.getString("plan", null) ?: "taper60",
            longBreak = p.getInt("long", 20),
            taperBreak = p.getInt("taper_break", 10),
            customWork = p.getInt("cw", 45),
            customBreak = p.getInt("cb", 10),
            customReps = p.getInt("cn", 3),
            autoStart = p.getBoolean("auto", true),
            chime = p.getBoolean("chime", true),
        )
        val st = TimerState(
            plan = p.getString("st_plan", null) ?: "",
            idx = p.getInt("st_idx", 0),
            remMs = p.getLong("st_rem", 0),
            lenMs = p.getLong("st_len", 0),
            endAt = p.getLong("st_end", -1).takeIf { it >= 0 },
            done = p.getBoolean("st_done", false),
        )
        return Engine(cfg, st)
    }

    fun save(ctx: Context, e: Engine) {
        val c = e.cfg
        val s = e.st
        prefs(ctx).edit()
            .putString("plan", c.plan)
            .putInt("long", c.longBreak)
            .putInt("taper_break", c.taperBreak)
            .putInt("cw", c.customWork)
            .putInt("cb", c.customBreak)
            .putInt("cn", c.customReps)
            .putBoolean("auto", c.autoStart)
            .putBoolean("chime", c.chime)
            .putString("st_plan", s.plan)
            .putInt("st_idx", s.idx)
            .putLong("st_rem", s.remMs)
            .putLong("st_len", s.lenMs)
            .putLong("st_end", s.endAt ?: -1L)
            .putBoolean("st_done", s.done)
            .apply()
    }
}
