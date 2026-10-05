package dk.decpt.focustaper

enum class Phase(val label: String) { WORK("Work"), BREAK("Break"), LONG("Long break") }

data class Seg(val phase: Phase, val min: Int)

data class Config(
    var plan: String = "taper60",
    var longBreak: Int = 20,
    var taperBreak: Int = 10,
    var customWork: Int = 45,
    var customBreak: Int = 10,
    var customReps: Int = 3,
    var autoStart: Boolean = true,
    var chime: Boolean = true,
)

data class PlanDef(val key: String, val group: String, val name: String, val sub: String)

object Plans {
    val all = listOf(
        PlanDef("taper60", "taper", "Full day", "60·50·40·30·20·10"),
        PlanDef("taper50", "taper", "Easy start", "50·40·30·20·10"),
        PlanDef("classic", "classic", "Pomodoro", "25/5 × 4"),
        PlanDef("deep", "classic", "Deep work", "50/10 × 3"),
        PlanDef("ultra", "classic", "Ultradian", "90/20 × 2"),
        PlanDef("custom", "custom", "Custom", ""),
    )

    fun def(key: String): PlanDef = all.firstOrNull { it.key == key } ?: all[0]

    fun build(key: String, cfg: Config): List<Seg> = when (key) {
        "taper50" -> taper(50, cfg)
        "classic" -> cycles(25, 5, 4, cfg)
        "deep" -> cycles(50, 10, 3, cfg)
        "ultra" -> cycles(90, 20, 2, cfg)
        "custom" -> cycles(cfg.customWork, cfg.customBreak, cfg.customReps, cfg)
        else -> taper(60, cfg)
    }

    /** n × (work / break); the final break is replaced by the long break (none if longBreak = 0). */
    fun cycles(work: Int, brk: Int, n: Int, cfg: Config): List<Seg> = buildList {
        for (i in 0 until n) {
            add(Seg(Phase.WORK, work))
            if (i < n - 1) {
                if (brk > 0) add(Seg(Phase.BREAK, brk))
            } else if (cfg.longBreak > 0) add(Seg(Phase.LONG, cfg.longBreak))
        }
    }

    /** start, start-10, … 10 minutes of work with taper breaks between; long break after the 10. */
    fun taper(start: Int, cfg: Config): List<Seg> = buildList {
        var m = start
        while (m >= 10) {
            add(Seg(Phase.WORK, m))
            if (m > 10) {
                if (cfg.taperBreak > 0) add(Seg(Phase.BREAK, cfg.taperBreak))
            } else if (cfg.longBreak > 0) add(Seg(Phase.LONG, cfg.longBreak))
            m -= 10
        }
    }

    fun totalMin(s: List<Seg>): Int = s.sumOf { it.min }
}
