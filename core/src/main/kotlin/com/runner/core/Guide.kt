package com.runner.core

import kotlin.math.roundToInt

/**
 * 훈련 시작(가이드 러닝)의 한 구간. 종료 조건은 셋 중 하나:
 * 이 구간에서 distanceM 만큼 달리기 / sec 초 지나기 / 전체 누적 거리가 untilTotalM 에 닿기.
 * pace 가 있으면 그 구간에서만 페이스 알림. rep/reps 는 인터벌 빠른 구간 회차(1부터).
 */
data class GuideStep(
    val label: String,
    val distanceM: Double? = null,
    val sec: Int? = null,
    val untilTotalM: Double? = null,
    val pace: IntRange2? = null,
    val rep: Int = 0,
    val reps: Int = 0,
)

object GuideSteps {
    private const val WARMUP_M = 2000.0
    private const val TEMPO_SEC = 1500

    /** 훈련표 생성기(TrainingPlanGenerator)의 설명과 같은 구성으로 구간을 만든다. 휴식일은 빈 리스트. */
    fun forWorkout(plan: TrainingPlan, workout: Workout): List<GuideStep> {
        val z = plan.zones
        val totalM = workout.distanceKm * 1000
        val main = when (workout.type) {
            WorkoutType.REST -> return emptyList()
            WorkoutType.EASY, WorkoutType.LONG, WorkoutType.RACE ->
                return listOf(GuideStep(workout.type.label, distanceM = totalM, pace = TargetPace.forWorkout(workout.type, z)))
            WorkoutType.TEMPO -> if (isRaceWeek(plan, workout)) {
                listOf(
                    GuideStep("가볍게", distanceM = 3000.0),
                    GuideStep("대회 페이스", distanceM = 1000.0, pace = TargetPace.forWorkout(WorkoutType.RACE, z)),
                )
            } else {
                listOf(GuideStep("워밍업", distanceM = WARMUP_M), GuideStep("템포", sec = TEMPO_SEC, pace = z?.tempo))
            }
            WorkoutType.INTERVAL -> {
                val (fastM, jogM, reps) = when (plan.goal) {
                    RaceDistance.FIVE_K, RaceDistance.TEN_K -> Triple(400.0, 200.0, 8)
                    RaceDistance.HALF, RaceDistance.FULL -> Triple(1000.0, 400.0, 5)
                }
                buildList {
                    add(GuideStep("워밍업", distanceM = WARMUP_M))
                    for (rep in 1..reps) {
                        add(GuideStep("빠르게", distanceM = fastM, pace = z?.interval, rep = rep, reps = reps))
                        if (rep < reps) add(GuideStep("조깅", distanceM = jogM))
                    }
                }
            }
        }
        // 템포처럼 시간 구간이 있으면 거리를 알 수 없으니 쿨다운을 둔다(남은 거리가 없으면 트래커가 바로 넘긴다)
        val knownM = main.sumOf { it.distanceM ?: 0.0 }
        val timed = main.any { it.sec != null }
        return if (timed || knownM < totalM) main + GuideStep("쿨다운", untilTotalM = totalM) else main
    }

    private fun isRaceWeek(plan: TrainingPlan, workout: Workout) =
        plan.weeks.firstOrNull { workout in it.workouts }?.workouts?.any { it.type == WorkoutType.RACE } == true
}

/**
 * 누적 거리·경과 시간으로 지금 몇 번째 구간인지 판정한다. 서비스가 넘기는 값만 쓰므로
 * 일시정지 중(값이 그대로)엔 진행하지 않는다. 마지막 구간을 넘기면 index == steps.size.
 */
class GuideTracker(val steps: List<GuideStep>) {
    var index = -1
        private set
    val finished get() = index >= steps.size

    private var startM = 0.0
    private var startSec = 0L

    /** 이번 호출에서 새로 시작된 구간 인덱스들(완료되면 steps.size 포함). 첫 호출은 [0]. */
    fun update(distanceM: Double, elapsedSec: Long): List<Int> {
        if (finished) return emptyList()
        val started = mutableListOf<Int>()
        if (index < 0) started += begin(0, distanceM, elapsedSec)
        // GPS가 튀어 여러 구간을 한 번에 지나도 하나씩 넘긴다
        while (!finished && isDone(steps[index], distanceM, elapsedSec)) {
            // 끝난 구간의 목표 지점에서 다음 구간을 시작해야 초과분이 다음 구간에 반영된다
            val done = steps[index]
            started += begin(
                index + 1,
                done.distanceM?.let { startM + it } ?: distanceM,
                done.sec?.let { startSec + it } ?: elapsedSec,
            )
        }
        return started
    }

    /** 예: "빠르게 3/8 · 120m 남음", "템포 · 12:30 남음", "쿨다운 · 0.8km 남음" */
    fun progressText(distanceM: Double, elapsedSec: Long): String {
        if (finished || index < 0) return "훈련 완료"
        val step = steps[index]
        val name = if (step.reps > 0) "${step.label} ${step.rep}/${step.reps}" else step.label
        val left = when {
            step.sec != null -> Pace.formatDuration((step.sec - (elapsedSec - startSec)).coerceAtLeast(0))
            step.distanceM != null -> meters(step.distanceM - (distanceM - startM), short = step.distanceM <= 1000)
            else -> meters(step.untilTotalM!! - distanceM, short = false)
        }
        return "$name · $left 남음"
    }

    private fun begin(i: Int, distanceM: Double, elapsedSec: Long): Int {
        index = i
        startM = distanceM
        startSec = elapsedSec
        return i
    }

    private fun isDone(step: GuideStep, distanceM: Double, elapsedSec: Long) = when {
        step.sec != null -> elapsedSec - startSec >= step.sec
        step.distanceM != null -> distanceM - startM >= step.distanceM
        else -> distanceM >= step.untilTotalM!!
    }

    private fun meters(m: Double, short: Boolean): String {
        val left = m.coerceAtLeast(0.0)
        return if (short) "${left.roundToInt()}m" else "%.1fkm".format(left / 1000)
    }
}
