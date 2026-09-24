package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test fun oneDegreeLatitudeIsAbout111Km() {
        val d = Geo.distanceM(TrackPoint(37.0, 127.0, 0), TrackPoint(38.0, 127.0, 0))
        assertEquals(111_195.0, d, 100.0)
    }

    @Test fun rejectsInaccurateAndTeleportingPoints() {
        val a = TrackPoint(37.5665, 126.9780, 0, 5f)
        assertFalse(Geo.isPlausible(a, a.copy(timeMs = 1000, accuracyM = 50f)))
        // 1초에 약 1.1km 이동 = 불가능
        assertFalse(Geo.isPlausible(a, TrackPoint(37.5765, 126.9780, 1000, 5f)))
        // 1초에 약 3m 이동 = 정상
        assertTrue(Geo.isPlausible(a, TrackPoint(37.56653, 126.9780, 1000, 5f)))
    }

    @Test fun totalDistanceSumsSegments() {
        val pts = listOf(TrackPoint(37.0, 127.0, 0), TrackPoint(37.001, 127.0, 1), TrackPoint(37.002, 127.0, 2))
        assertEquals(222.4, Geo.totalDistanceM(pts), 1.0)
    }
}

class SplitsTest {
    // 위도 0.001도 ≈ 111.2m. 10초마다 한 칸 이동(약 5'00"/km보다 조금 느림)
    private fun line(n: Int, segment: Int = 0, startIdx: Int = 0, startMs: Long = 0) =
        (0 until n).map { TrackPoint(37.0 + (startIdx + it) * 0.001, 127.0, startMs + it * 10_000L, segment = segment) }

    @Test fun splitsEveryKilometer() {
        val pts = line(20) // 19칸 ≈ 2.11km
        val splits = Geo.splitsSec(pts)
        assertEquals(2, splits.size)
        splits.forEach { assertEquals(89.9, it.toDouble(), 1.5) } // 1000/111.2*10초
    }

    @Test fun pauseGapIsExcluded() {
        val first = line(9)
        // 10분 쉬고, 다른 위치에서 재개
        val second = line(9, segment = 1, startIdx = 50, startMs = first.last().timeMs + 600_000)
        val pts = first + second
        assertEquals(16 * 111.2, Geo.totalDistanceM(pts), 5.0)
        val splits = Geo.splitsSec(pts)
        assertEquals(1, splits.size)
        assertTrue(splits[0] < 100)
    }
}
