package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceTest {
    @Test fun formatsPace() {
        assertEquals("5'30\"", Pace.format(330.0))
        assertEquals("-'--\"", Pace.format(null))
    }

    @Test fun formatsAndParsesDuration() {
        assertEquals("1:02:05", Pace.formatDuration(3725))
        assertEquals("10:05", Pace.formatDuration(605))
        assertEquals(3725L, Pace.parseDuration("1:02:05"))
        assertEquals(1530L, Pace.parseDuration("25:30"))
        assertNull(Pace.parseDuration("abc"))
        assertNull(Pace.parseDuration(""))
    }

    @Test fun secPerKmIgnoresTinyDistances() {
        assertNull(Pace.secPerKm(10.0, 60))
        assertEquals(300.0, Pace.secPerKm(1000.0, 300)!!, 0.001)
    }

    @Test fun riegelPrediction() {
        // 5K 25:00 -> 10K 약 52:07
        val tenK = Pace.riegel(5.0, 1500, 10.0)
        assertTrue(tenK in 3120..3135)
    }

    @Test fun zonesAreOrderedFastToSlow() {
        val z = Pace.zones(10.0, 3000, RaceDistance.HALF)
        assertTrue(z.interval.max <= z.tempo.min)
        assertTrue(z.tempo.max <= z.easy.min)
        assertTrue(z.easy.min < z.easy.max)
    }
}
