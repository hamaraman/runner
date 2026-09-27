package com.runner.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** 러닝 중 코칭(음성 안내·페이스 알림·자동 일시정지) 설정. */
@Serializable
data class CoachSettings(
    val voiceEnabled: Boolean = true,
    val kmAnnounce: Boolean = true,
    val paceAlert: Boolean = true,
    val autoPause: Boolean = true,
    /** 훈련표에 목표가 없을 때 쓰는 목표 페이스(초/km). null이면 알림 없음. */
    val manualTargetPaceSec: Int? = null,
)

object TargetPace {
    /** 수동 목표 페이스에 주는 허용 범위(±초) */
    const val MANUAL_TOLERANCE_SEC = 10

    /**
     * 오늘 운동 종류에 맞는 목표 페이스 범위. 인터벌은 구간마다 페이스가 달라 알림하지 않는다.
     */
    fun forWorkout(type: WorkoutType?, zones: PaceZones?): IntRange2? {
        if (type == null || zones == null) return null
        return when (type) {
            WorkoutType.EASY, WorkoutType.LONG -> zones.easy
            WorkoutType.TEMPO -> zones.tempo
            WorkoutType.RACE -> IntRange2(zones.race - 5, zones.race + 5)
            WorkoutType.INTERVAL, WorkoutType.REST -> null
        }
    }

    fun manual(paceSec: Int?): IntRange2? =
        paceSec?.let { IntRange2(it - MANUAL_TOLERANCE_SEC, it + MANUAL_TOLERANCE_SEC) }
}

/** 1km(또는 splitM)를 지날 때마다 구간 기록을 만들어준다. */
class KmSplitTracker(private val splitM: Double = 1000.0) {
    data class Split(val km: Int, val splitSec: Long, val elapsedSec: Long)

    private var nextKm = 1
    private var lastMarkElapsed = 0L

    /** 새로 통과한 km 지점들(보통 0~1개). */
    fun update(distanceM: Double, elapsedSec: Long): List<Split> {
        val result = mutableListOf<Split>()
        while (distanceM >= nextKm * splitM) {
            result += Split(nextKm, elapsedSec - lastMarkElapsed, elapsedSec)
            lastMarkElapsed = elapsedSec
            nextKm++
        }
        return result
    }
}

enum class PaceStatus { TOO_FAST, TOO_SLOW, ON_TARGET }

/**
 * 목표 페이스 이탈 알림 판단. 약 10초마다 [check]를 호출한다고 가정.
 * - 워밍업 시간 동안은 알리지 않음
 * - 연속 [confirmChecks]번 벗어나야 알림(GPS 흔들림 대비)
 * - 같은 알림은 [cooldownMs] 동안 반복하지 않음
 * - 이탈 알림 후 목표 범위로 돌아오면 한 번 칭찬
 */
class PaceAlertPolicy(
    private val target: IntRange2,
    private val toleranceSec: Int = 5,
    private val warmupMs: Long = 120_000,
    private val cooldownMs: Long = 90_000,
    private val confirmChecks: Int = 2,
) {
    private var streakStatus: PaceStatus? = null
    private var streak = 0
    private var lastAlerted: PaceStatus? = null
    private var lastAlertAt = Long.MIN_VALUE / 2

    fun classify(paceSec: Double): PaceStatus = when {
        paceSec < target.min - toleranceSec -> PaceStatus.TOO_FAST
        paceSec > target.max + toleranceSec -> PaceStatus.TOO_SLOW
        else -> PaceStatus.ON_TARGET
    }

    /** 알려야 할 상태가 있으면 반환, 없으면 null. */
    fun check(paceSec: Double?, elapsedMs: Long): PaceStatus? {
        if (paceSec == null || elapsedMs < warmupMs) return null
        val status = classify(paceSec)
        if (status == streakStatus) streak++ else {
            streakStatus = status
            streak = 1
        }
        if (streak < confirmChecks) return null

        if (status == PaceStatus.ON_TARGET) {
            // 이탈 알림을 한 뒤 돌아왔을 때만 한 번 알려준다
            if (lastAlerted == PaceStatus.TOO_FAST || lastAlerted == PaceStatus.TOO_SLOW) {
                lastAlerted = PaceStatus.ON_TARGET
                lastAlertAt = elapsedMs
                return PaceStatus.ON_TARGET
            }
            return null
        }
        if (status != lastAlerted || elapsedMs - lastAlertAt >= cooldownMs) {
            lastAlerted = status
            lastAlertAt = elapsedMs
            return status
        }
        return null
    }
}

/**
 * 멈춤/재출발 감지. GPS는 서 있어도 몇 m씩 흔들리므로 '반경 안에 머무는 시간'으로 판단한다.
 * 시간은 단조 증가하는 시계(ms)를 써야 한다.
 */
class AutoPauseDetector(
    private val stopWindowMs: Long = 10_000,
    private val stopRadiusM: Double = 8.0,
    private val resumeWindowMs: Long = 5_000,
    private val resumeDistanceM: Double = 7.0,
) {
    enum class Event { PAUSED, RESUMED }

    /** [atMs]: PAUSED면 멈춘 것으로 판단되는 시작 시각, RESUMED면 다시 출발한 시각 */
    data class Change(val event: Event, val atMs: Long)

    private data class Fix(val point: TrackPoint, val t: Long)

    var paused = false
        private set
    private val fixes = ArrayDeque<Fix>()

    fun reset() {
        paused = false
        fixes.clear()
    }

    fun feed(lat: Double, lng: Double, tMs: Long): Change? {
        fixes.addLast(Fix(TrackPoint(lat, lng, tMs), tMs))
        val keepFrom = tMs - maxOf(stopWindowMs, resumeWindowMs) - 5_000
        while (fixes.isNotEmpty() && fixes.first().t < keepFrom) fixes.removeFirst()
        val last = fixes.last()

        if (!paused) {
            // 마지막 위치 반경 안에 계속 머문 가장 이른 시각을 찾는다.
            // (멈추기 직전 몇 초도 반경에 들어와 실제보다 조금 이르게 잡힐 수 있다)
            var since = last.t
            for (i in fixes.indices.reversed()) {
                if (Geo.distanceM(fixes[i].point, last.point) > stopRadiusM) break
                since = fixes[i].t
            }
            if (tMs - since >= stopWindowMs) {
                paused = true
                return Change(Event.PAUSED, since)
            }
        } else {
            val first = fixes.firstOrNull { it.t >= tMs - resumeWindowMs } ?: return null
            if (first !== last && Geo.distanceM(first.point, last.point) >= resumeDistanceM) {
                paused = false
                fixes.clear()
                fixes.addLast(last)
                return Change(Event.RESUMED, tMs)
            }
        }
        return null
    }
}

/** 음성 안내 문장(한국어). */
object VoiceText {
    /** 330 -> "5분 30초" */
    fun pace(sec: Double?): String {
        if (sec == null || sec.isNaN() || sec.isInfinite()) return "측정 중"
        val total = sec.roundToInt()
        val m = total / 60
        val s = total % 60
        return if (s == 0) "${m}분" else "${m}분 ${s}초"
    }

    fun duration(sec: Long): String {
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return buildList {
            if (h > 0) add("${h}시간")
            if (m > 0) add("${m}분")
            if (s > 0 || (h == 0L && m == 0L)) add("${s}초")
        }.joinToString(" ")
    }

    fun km(split: KmSplitTracker.Split, avgPaceSec: Double?): String =
        "${split.km}킬로미터. 이번 구간 ${pace(split.splitSec.toDouble())}, 평균 페이스 ${pace(avgPaceSec)}."

    fun paceAlert(status: PaceStatus, target: IntRange2): String = when (status) {
        PaceStatus.TOO_FAST -> "페이스가 빨라요. 조금 천천히. 목표는 ${pace(target.min.toDouble())}에서 ${pace(target.max.toDouble())}예요."
        PaceStatus.TOO_SLOW -> "페이스가 느려요. 조금 더 힘내세요. 목표는 ${pace(target.min.toDouble())}에서 ${pace(target.max.toDouble())}예요."
        PaceStatus.ON_TARGET -> "좋아요. 목표 페이스예요."
    }

    fun start(target: IntRange2?): String =
        "러닝을 시작합니다." + (target?.let { " 목표 페이스는 ${pace(it.min.toDouble())}에서 ${pace(it.max.toDouble())}예요." } ?: "")

    fun finish(distanceM: Double, durationSec: Long, avgPaceSec: Double?): String =
        "러닝 종료. 총 ${"%.2f".format(distanceM / 1000)}킬로미터, 시간 ${duration(durationSec)}, 평균 페이스 ${pace(avgPaceSec)}."
}
