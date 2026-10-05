package dk.decpt.focustaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineTest {
    private val min = 60_000L
    private fun seq(s: List<Seg>) = s.joinToString(" ") { "${it.phase.name[0]}${it.min}" }

    @Test fun taperSequences() {
        val cfg = Config()
        assertEquals("W60 B10 W50 B10 W40 B10 W30 B10 W20 B10 W10 L20", seq(Plans.build("taper60", cfg)))
        assertEquals("W50 B10 W40 B10 W30 B10 W20 B10 W10 L20", seq(Plans.build("taper50", cfg)))
    }

    @Test fun classicAndCustom() {
        val cfg = Config(longBreak = 15, customWork = 45, customBreak = 0, customReps = 2)
        assertEquals("W25 B5 W25 B5 W25 B5 W25 L15", seq(Plans.build("classic", cfg)))
        assertEquals("W45 W45 L15", seq(Plans.build("custom", cfg)))
        cfg.longBreak = 0
        assertEquals("W25 B5 W25 B5 W25 B5 W25", seq(Plans.build("classic", cfg)))
    }

    @Test fun catchUpChainsFromScheduledEnd() {
        val e = Engine(Config(), TimerState("taper60"))
        e.start(0)
        // 71 min later: 60 work + 10 break done, 1 min into the 50
        assertEquals(2, e.catchUp(71 * min))
        assertEquals(2, e.st.idx)
        assertEquals(49 * min, e.remaining(71 * min))
        assertEquals(Pair(2, 6), e.workPosition())
    }

    @Test fun autoStartOffStopsAtNextBlock() {
        val e = Engine(Config(autoStart = false), TimerState("taper60"))
        e.start(0)
        assertEquals(1, e.catchUp(65 * min))
        assertNull(e.st.endAt)
        assertEquals(10 * min, e.remaining(65 * min))
    }

    @Test fun finishesAfterLastBlock() {
        val e = Engine(Config(plan = "taper50"), TimerState("taper50"))
        e.start(0)
        e.catchUp(1000 * min)
        assertTrue(e.st.done)
        assertEquals(0L, e.planRemaining(1000 * min))
    }

    @Test fun pauseResumeKeepsRemaining() {
        val e = Engine(Config(plan = "classic"), TimerState("classic"))
        e.start(0); e.pause(10 * min)
        assertEquals(15 * min, e.remaining(99 * min))
        e.start(100 * min)
        assertEquals(115 * min, e.st.endAt)
    }

    @Test fun adjustAndRebuild() {
        val cfg = Config()
        val e = Engine(cfg, TimerState("taper60"))
        e.adjust(5, 0)
        assertEquals(65 * min, e.remaining(0))
        e.jump(11, 0)                 // the long break, untouched
        cfg.longBreak = 35; e.rebuild()
        assertEquals(35 * min, e.remaining(0))
    }
}
