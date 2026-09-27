package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KmSplitTrackerTest {
    @Test fun emitsOncePerKilometer() {
        val t = KmSplitTracker()
        assertTrue(t.update(999.0, 300).isEmpty())
        assertEquals(listOf(KmSplitTracker.Split(1, 301, 301)), t.update(1000.5, 301))
        assertTrue(t.update(1500.0, 450).isEmpty())
        assertEquals(KmSplitTracker.Split(2, 290, 591), t.update(2001.0, 591).single())
    }

    @Test fun handlesJumpOverSeveralKilometers() {
        assertEquals(listOf(1, 2), KmSplitTracker().update(2100.0, 600).map { it.km })
    }
}

class TargetPaceTest {
    private val zones = PaceZones(IntRange2(380, 410), IntRange2(320, 335), IntRange2(290, 300), 330)

    @Test fun picksZoneByWorkout() {
        assertEquals(zones.easy, TargetPace.forWorkout(WorkoutType.LONG, zones))
        assertEquals(zones.tempo, TargetPace.forWorkout(WorkoutType.TEMPO, zones))
        assertEquals(IntRange2(325, 335), TargetPace.forWorkout(WorkoutType.RACE, zones))
        assertNull(TargetPace.forWorkout(WorkoutType.INTERVAL, zones))
        assertNull(TargetPace.forWorkout(WorkoutType.EASY, null))
        assertEquals(IntRange2(350, 370), TargetPace.manual(360))
    }
}

class PaceAlertPolicyTest {
    private val target = IntRange2(360, 390)

    @Test fun silentDuringWarmup() {
        val p = PaceAlertPolicy(target)
        assertNull(p.check(300.0, 10_000))
        assertNull(p.check(300.0, 20_000))
    }

    @Test fun needsConfirmationThenCoolsDown() {
        val p = PaceAlertPolicy(target)
        assertNull(p.check(300.0, 130_000))
        assertEquals(PaceStatus.TOO_FAST, p.check(300.0, 140_000))
        assertNull(p.check(300.0, 150_000)) // 쿨다운 중
        assertEquals(PaceStatus.TOO_FAST, p.check(300.0, 240_000)) // 90초 경과
    }

    @Test fun praisesReturnToTargetOnlyAfterAlert() {
        val p = PaceAlertPolicy(target)
        assertNull(p.check(370.0, 130_000))
        assertNull(p.check(370.0, 140_000)) // 처음부터 목표 안: 조용히
        p.check(450.0, 150_000)
        assertEquals(PaceStatus.TOO_SLOW, p.check(450.0, 160_000))
        p.check(375.0, 170_000)
        assertEquals(PaceStatus.ON_TARGET, p.check(375.0, 180_000))
        assertNull(p.check(375.0, 190_000))
    }

    @Test fun toleranceAvoidsBorderlineAlerts() {
        val p = PaceAlertPolicy(target)
        assertEquals(PaceStatus.ON_TARGET, p.classify(356.0))
        assertEquals(PaceStatus.TOO_FAST, p.classify(354.0))
        assertEquals(PaceStatus.TOO_SLOW, p.classify(396.0))
    }
}

class AutoPauseDetectorTest {
    // 위도 0.00001도 ≈ 1.1m
    private fun feedLine(d: AutoPauseDetector, fromSec: Int, toSec: Int, mPerSec: Double, startLat: Double): Pair<Double, AutoPauseDetector.Change?> {
        var lat = startLat
        var change: AutoPauseDetector.Change? = null
        for (s in fromSec..toSec) {
            d.feed(lat, 127.0, s * 1000L)?.let { change = it }
            lat += mPerSec / 111_195.0
        }
        return lat to change
    }

    @Test fun runningDoesNotPause() {
        val d = AutoPauseDetector()
        val (_, change) = feedLine(d, 0, 60, 3.0, 37.0)
        assertNull(change)
        assertFalse(d.paused)
    }

    @Test fun pausesAfterStandingStillAndResumesWhenMoving() {
        val d = AutoPauseDetector()
        val (lat, _) = feedLine(d, 0, 30, 3.0, 37.0)
        // 서서 GPS가 ±3m 흔들림
        var change: AutoPauseDetector.Change? = null
        for (s in 31..50) {
            val jitter = if (s % 2 == 0) 3.0 / 111_195.0 else -3.0 / 111_195.0
            d.feed(lat + jitter, 127.0, s * 1000L)?.let { change = it }
        }
        assertEquals(AutoPauseDetector.Event.PAUSED, change!!.event)
        // 반경(8m) 안에 멈추기 직전 몇 초가 포함돼 실제보다 약간(수 초) 이르게 잡힌다
        assertTrue("stopped around 31s, got ${change!!.atMs}", change!!.atMs in 27_000..32_000)
        assertTrue(d.paused)

        val (_, resumed) = feedLine(d, 51, 60, 3.0, lat)
        assertEquals(AutoPauseDetector.Event.RESUMED, resumed!!.event)
        assertFalse(d.paused)
    }
}

class RecentPaceTest {
    @Test fun usesOnlyRecentWindowOfCurrentSegment() {
        // 1초에 약 2.78m(6'00"/km)로 120초
        val pts = (0..120).map { TrackPoint(37.0 + it * 2.778 / 111_195.0, 127.0, it * 1000L) }
        assertEquals(360.0, Geo.recentPaceSec(pts)!!, 3.0)
        // 새 구간이 막 시작되면 데이터가 부족해 null
        val resumed = pts + TrackPoint(37.1, 127.0, 200_000, segment = 1)
        assertNull(Geo.recentPaceSec(resumed))
    }
}

class VoiceTextTest {
    @Test fun readsNaturally() {
        assertEquals("5분 30초", VoiceText.pace(330.0))
        assertEquals("6분", VoiceText.pace(360.0))
        assertEquals("1시간 2분 5초", VoiceText.duration(3725))
        assertEquals("28분", VoiceText.duration(1680))
        assertEquals(
            "3킬로미터. 이번 구간 5분 20초, 평균 페이스 5분 31초.",
            VoiceText.km(KmSplitTracker.Split(3, 320, 993), 331.0),
        )
    }
}
