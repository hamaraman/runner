package com.runner.core

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

object Pace {
    /** 초/km. 50m 미만이면 의미 없는 값이라 null. */
    fun secPerKm(distanceM: Double, durationSec: Long): Double? =
        if (distanceM < 50.0 || durationSec <= 0) null else durationSec / (distanceM / 1000.0)

    /** 330.0 -> "5'30\"" */
    fun format(secPerKm: Double?): String {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite()) return "-'--\""
        val total = secPerKm.roundToInt()
        return "%d'%02d\"".format(total / 60, total % 60)
    }

    /** 3725 -> "1:02:05", 605 -> "10:05" */
    fun formatDuration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** "1:02:05" 또는 "25:30" -> 초. 형식이 틀리면 null. */
    fun parseDuration(text: String): Long? {
        val parts = text.trim().split(":").map { it.toLongOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3 || parts.any { it < 0 }) return null
        return parts.fold(0L) { acc, v -> acc * 60 + v }.takeIf { it > 0 }
    }

    /** Riegel 공식으로 다른 거리 기록 예측: T2 = T1 * (D2/D1)^1.06 */
    fun riegel(knownKm: Double, knownSec: Long, targetKm: Double): Long =
        (knownSec * (targetKm / knownKm).pow(1.06)).roundToLong()

    /**
     * 최근 기록 하나로 훈련 페이스 구간을 추정한다.
     * - 인터벌 ≈ 5K 페이스, 템포 ≈ 10K~하프 페이스 사이, 이지 ≈ 마라톤 페이스 + 10~20%.
     * 정밀한 VDOT 표가 아닌 실용적 근사치.
     */
    fun zones(knownKm: Double, knownSec: Long, goal: RaceDistance): PaceZones {
        fun paceAt(km: Double) = riegel(knownKm, knownSec, km) / km
        val p5 = paceAt(5.0)
        val p10 = paceAt(10.0)
        val pHalf = paceAt(21.0975)
        val pFull = paceAt(42.195)
        return PaceZones(
            easy = IntRange2((pFull * 1.10).roundToInt(), (pFull * 1.22).roundToInt()),
            tempo = IntRange2(p10.roundToInt(), pHalf.roundToInt()),
            interval = IntRange2((p5 * 0.97).roundToInt(), p5.roundToInt()),
            race = paceAt(goal.km).roundToInt(),
        )
    }

    fun formatRange(r: IntRange2): String = "${format(r.min.toDouble())}~${format(r.max.toDouble())}"
}
