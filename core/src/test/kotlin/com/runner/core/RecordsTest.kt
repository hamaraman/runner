package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecordsTest {
    @Test fun bestWindowPicksFastestConsecutive() {
        assertEquals(590L, Records.bestWindowSec(listOf(300, 310, 290, 300, 320), 2))
        assertNull(Records.bestWindowSec(listOf(300), 5))
    }

    @Test fun newBestsSkipsFirstRunAndFlagsLongest() {
        val a = RunRecord("a", 0, 600, 2000.0)
        assertEquals(emptyList<String>(), Records.newBests(a, emptyList()))
        assertEquals(listOf("최장 거리"), Records.newBests(RunRecord("b", 0, 900, 3000.0), listOf(a)))
    }

    @Test fun weekStreakCountsBackFromLastWeekIfIdle() {
        val wed = LocalDate.of(2026, 10, 14)
        val days = listOf(wed.minusWeeks(1), wed.minusWeeks(2), wed.minusWeeks(4))
        assertEquals(2, Records.weekStreak(days, wed))
        assertEquals(3, Records.weekStreak(days + wed, wed))
        assertEquals(0, Records.weekStreak(emptyList(), wed))
    }
}
