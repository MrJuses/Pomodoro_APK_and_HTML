package dk.decpt.focustaper

import kotlin.math.max

/**
 * Timer state. While running, only [endAt] (wall-clock ms) matters: the remaining time is
 * derived from it, so nothing drifts and a killed process can resume from storage.
 */
data class TimerState(
    var plan: String,
    var idx: Int = 0,
    var remMs: Long = 0,   // remaining time while paused
    var lenMs: Long = 0,   // length of the current block (grows with +1 min)
    var endAt: Long? = null,
    var done: Boolean = false,
)

/** Pure state machine: no Android types, time is passed in, so it is unit-testable on the JVM. */
class Engine(val cfg: Config, var st: TimerState) {
    var segs: List<Seg> = Plans.build(cfg.plan, cfg)
        private set

    init {
        if (st.plan != cfg.plan || st.idx !in segs.indices || st.lenMs <= 0) st = fresh()
    }

    private fun full(i: Int): Long = segs[i].min * 60_000L

    fun fresh() = TimerState(cfg.plan, 0, full(0), full(0), null, false)

    val running: Boolean get() = st.endAt != null
    val current: Seg get() = segs[st.idx]
    val next: Seg? get() = segs.getOrNull(st.idx + 1)

    fun remaining(now: Long): Long = st.endAt?.let { max(0L, it - now) } ?: st.remMs

    fun progress(now: Long): Float =
        if (st.done) 1f else (1f - remaining(now).toFloat() / st.lenMs).coerceIn(0f, 1f)

    fun start(now: Long) {
        if (st.done) st = fresh()
        st.endAt = now + st.remMs
    }

    fun pause(now: Long) {
        st.remMs = remaining(now)
        st.endAt = null
    }

    fun toggle(now: Long) = if (running) pause(now) else start(now)

    /** fromEnd: chain from the scheduled end, so catching up after a long sleep stays exact. */
    private fun advance(now: Long, fromEnd: Boolean) {
        val wasRunning = running
        val prevEnd = st.endAt
        if (st.idx >= segs.size - 1) {
            st.done = true; st.endAt = null; st.remMs = 0
            return
        }
        st.idx++
        st.remMs = full(st.idx)
        st.lenMs = st.remMs
        st.endAt = if (wasRunning && cfg.autoStart) {
            (if (fromEnd && prevEnd != null) prevEnd else now) + st.remMs
        } else null
    }

    /** Advance past every block that has ended by [now]. Returns how many transitions happened. */
    fun catchUp(now: Long): Int {
        var n = 0
        while (n < 100) {
            val end = st.endAt ?: break
            if (now < end) break
            advance(now, fromEnd = true)
            n++
        }
        return n
    }

    fun skip(now: Long) {
        if (st.done) return
        if (running) st.remMs = remaining(now)
        advance(now, fromEnd = false)
    }

    fun jump(i: Int, now: Long) {
        if (i !in segs.indices) return
        val wasRunning = running
        st.idx = i; st.done = false
        st.remMs = full(i); st.lenMs = st.remMs
        st.endAt = if (wasRunning) now + st.remMs else null
    }

    fun restartBlock(now: Long) {
        if (st.done) return
        st.remMs = full(st.idx); st.lenMs = st.remMs
        if (running) st.endAt = now + st.remMs
    }

    fun adjust(minutes: Int, now: Long) {
        if (st.done) return
        val d = minutes * 60_000L
        val end = st.endAt
        if (end != null) st.endAt = max(now + 1000, end + d) else st.remMs = max(1000L, st.remMs + d)
        st.lenMs = max(st.lenMs + d, remaining(now))
    }

    fun reset() { st = fresh() }

    fun selectPlan(key: String) {
        cfg.plan = key
        segs = Plans.build(key, cfg)
        st = fresh()
    }

    /** Re-derive the plan after a settings change, keeping progress in the current block. */
    fun rebuild() {
        val old = segs.getOrNull(st.idx)
        segs = Plans.build(cfg.plan, cfg)
        if (old == null || st.idx !in segs.indices || segs[st.idx].phase != old.phase) {
            st = fresh(); return
        }
        val untouched = !running && st.remMs == st.lenMs
        if (untouched && !st.done) {
            st.remMs = full(st.idx); st.lenMs = st.remMs
        }
    }

    fun planRemaining(now: Long): Long =
        if (st.done) 0 else remaining(now) + segs.drop(st.idx + 1).sumOf { it.min * 60_000L }

    /** (n, of): this is work block n out of `of`. */
    fun workPosition(): Pair<Int, Int> {
        var n = 0; var of = 0
        segs.forEachIndexed { i, s -> if (s.phase == Phase.WORK) { of++; if (i <= st.idx) n++ } }
        return n to of
    }
}
