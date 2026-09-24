package com.runner.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** 두 좌표 사이 거리(m), 하버사인 공식. */
    fun distanceM(a: TrackPoint, b: TrackPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * GPS 튐 현상을 걸러내기 위한 판정.
     * 정확도가 나쁘거나(>maxAccuracyM), 사람이 낼 수 없는 속도(>maxSpeedMps)면 버린다.
     */
    fun isPlausible(
        prev: TrackPoint?,
        next: TrackPoint,
        maxAccuracyM: Float = 25f,
        maxSpeedMps: Double = 12.0,
    ): Boolean {
        if (next.accuracyM > maxAccuracyM) return false
        if (prev == null) return true
        val dt = (next.timeMs - prev.timeMs) / 1000.0
        if (dt <= 0) return false
        return distanceM(prev, next) / dt <= maxSpeedMps
    }

    fun totalDistanceM(points: List<TrackPoint>): Double =
        points.zipWithNext { a, b -> if (a.segment == b.segment) distanceM(a, b) else 0.0 }.sum()

    /**
     * 1km 단위 구간 기록(초). 일시정지 구간(segment가 바뀌는 사이)의 시간·거리는 제외.
     * 마지막 1km 미만 구간은 포함하지 않는다.
     */
    fun splitsSec(points: List<TrackPoint>, splitM: Double = 1000.0): List<Long> {
        val splits = mutableListOf<Long>()
        var dist = 0.0
        var timeMs = 0.0
        for ((a, b) in points.zipWithNext()) {
            if (a.segment != b.segment) continue
            var d = distanceM(a, b)
            var t = (b.timeMs - a.timeMs).toDouble()
            // 한 구간이 1km 경계를 넘으면 비율대로 나눠 담는다
            while (dist + d >= splitM && d > 0) {
                val need = splitM - dist
                val tPart = t * (need / d)
                splits += ((timeMs + tPart) / 1000.0).toLong()
                d -= need
                t -= tPart
                dist = 0.0
                timeMs = 0.0
            }
            dist += d
            timeMs += t
        }
        return splits
    }
}
