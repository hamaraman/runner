package com.runner.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 목표 대회에 맞춘 주간 훈련표 생성기.
 *
 * 원칙(일반적인 러닝 코칭 가이드라인 기반):
 * - 주간 거리 증가는 최대 10%, 4주차마다 회복 주(80%)
 * - 초반은 기초(이지런 위주), 이후 강화(인터벌/템포) 단계
 * - 대회 전 테이퍼로 볼륨을 줄이고, 마지막 주 마지막 날이 대회
 */
object TrainingPlanGenerator {
    const val MAX_WEEKS = 24

    data class Input(
        val goal: RaceDistance,
        val raceDate: LocalDate,
        val today: LocalDate,
        val currentWeeklyKm: Double,
        val runsPerWeek: Int,
        val raceName: String = "",
        val zones: PaceZones? = null,
    )

    fun generate(input: Input): TrainingPlan {
        require(!input.raceDate.isBefore(input.today)) { "대회 날짜가 이미 지났어요." }
        val runs = input.runsPerWeek.coerceIn(3, 6)
        val daysInclusive = ChronoUnit.DAYS.between(input.today, input.raceDate) + 1
        val weeks = (daysInclusive / 7).toInt().coerceIn(1, MAX_WEEKS)
        // 대회일이 마지막 주의 마지막 날(offset 6)이 되도록 시작일을 역산
        val start = input.raceDate.minusDays(weeks * 7L - 1)

        val taperWeeks = min(
            when (input.goal) {
                RaceDistance.FIVE_K, RaceDistance.TEN_K -> 0
                RaceDistance.HALF -> 1
                RaceDistance.FULL -> 2
            },
            max(0, weeks - 2),
        )
        val buildWeeks = weeks - 1 - taperWeeks
        val baseWeeks = (buildWeeks * 0.4).roundToInt()

        val startKm = max(input.currentWeeklyKm, 10.0)
        val peakKm = max(startKm, peakWeeklyKm(input.goal))
        val slots = slotsFor(runs)

        var lastBuildKm = startKm
        var progressKm = startKm
        val result = mutableListOf<TrainingWeek>()
        for (w in 0 until weeks) {
            val weekStart = start.plusDays(w * 7L)
            val week = when {
                w == weeks - 1 -> raceWeek(w, weekStart, input, slots)
                w >= buildWeeks -> {
                    val factor = if (w - buildWeeks == 0 && taperWeeks == 2) 0.75 else 0.6
                    normalWeek(w, "테이퍼", weekStart, lastBuildKm * factor, input, slots, quality = true)
                }
                else -> {
                    val recovery = w % 4 == 3
                    val km = if (recovery) progressKm * 0.8 else progressKm
                    val phase = when {
                        recovery -> "회복"
                        w < baseWeeks -> "기초"
                        else -> "강화"
                    }
                    if (!recovery) {
                        lastBuildKm = km
                        progressKm = min(peakKm, progressKm * 1.10)
                    }
                    normalWeek(w, phase, weekStart, km, input, slots, quality = !recovery && w >= baseWeeks)
                }
            }
            result += week
        }
        return TrainingPlan(
            goal = input.goal,
            raceDateEpochDay = input.raceDate.toEpochDay(),
            raceName = input.raceName,
            zones = input.zones,
            weeks = result,
        )
    }

    fun peakWeeklyKm(goal: RaceDistance): Double = when (goal) {
        RaceDistance.FIVE_K -> 30.0
        RaceDistance.TEN_K -> 40.0
        RaceDistance.HALF -> 50.0
        RaceDistance.FULL -> 65.0
    }

    private fun longRunCapKm(goal: RaceDistance): Double = when (goal) {
        RaceDistance.FIVE_K -> 10.0
        RaceDistance.TEN_K -> 14.0
        RaceDistance.HALF -> 19.0
        RaceDistance.FULL -> 32.0
    }

    /** 주 내 요일 오프셋(0~6). 6 = 장거리(대회 요일과 동일). */
    private data class Slots(val quality: List<Int>, val easy: List<Int>, val long: Int = 6)

    private fun slotsFor(runs: Int) = when (runs) {
        3 -> Slots(quality = listOf(1), easy = listOf(3))
        4 -> Slots(quality = listOf(1), easy = listOf(3, 5))
        5 -> Slots(quality = listOf(1, 3), easy = listOf(0, 5))
        else -> Slots(quality = listOf(1, 3), easy = listOf(0, 2, 5))
    }

    private fun normalWeek(
        index: Int,
        phase: String,
        weekStart: LocalDate,
        targetKm: Double,
        input: Input,
        slots: Slots,
        quality: Boolean,
    ): TrainingWeek {
        val z = input.zones
        val longKm = roundHalf(min(targetKm * 0.30, longRunCapKm(input.goal)))
        val qualityKm = roundHalf(max(targetKm * 0.17, 5.0))
        val restKm = targetKm - longKm - qualityKm * slots.quality.size
        val easyCount = slots.easy.size
        val easyKm = roundHalf(max(restKm / easyCount, 3.0))

        val workouts = mutableListOf<Workout>()
        slots.quality.forEachIndexed { i, day ->
            val date = weekStart.plusDays(day.toLong()).toEpochDay()
            workouts += when {
                !quality -> Workout(date, WorkoutType.EASY, qualityKm, easyDesc(z) + " 마지막에 100m 가속주 4~6회")
                i == 0 && (slots.quality.size > 1 || index % 2 == 0) ->
                    Workout(date, WorkoutType.INTERVAL, qualityKm, intervalDesc(input.goal, z))
                else -> Workout(date, WorkoutType.TEMPO, qualityKm, tempoDesc(z))
            }
        }
        slots.easy.forEach { day ->
            workouts += Workout(weekStart.plusDays(day.toLong()).toEpochDay(), WorkoutType.EASY, easyKm, easyDesc(z))
        }
        workouts += Workout(
            weekStart.plusDays(slots.long.toLong()).toEpochDay(),
            WorkoutType.LONG,
            longKm,
            "장거리 · " + easyDesc(z),
        )
        return TrainingWeek(index, phase, roundHalf(workouts.sumOf { it.distanceKm }), withRestDays(weekStart, workouts))
    }

    private fun raceWeek(index: Int, weekStart: LocalDate, input: Input, slots: Slots): TrainingWeek {
        val z = input.zones
        val racePace = z?.race?.let { " (목표 ${Pace.format(it.toDouble())}/km)" } ?: ""
        val workouts = listOf(
            Workout(
                weekStart.plusDays(slots.quality.first().toLong()).toEpochDay(),
                WorkoutType.TEMPO,
                5.0,
                "가볍게 3km + 대회 페이스 1km$racePace + 쿨다운",
            ),
            Workout(weekStart.plusDays(3).toEpochDay(), WorkoutType.EASY, 4.0, "컨디션 유지용 짧은 조깅"),
            Workout(
                weekStart.plusDays(6).toEpochDay(),
                WorkoutType.RACE,
                input.goal.km,
                "${input.raceName.ifBlank { "대회" }} ${input.goal.label}$racePace 화이팅!",
            ),
        )
        return TrainingWeek(index, "대회 주", roundHalf(workouts.sumOf { it.distanceKm }), withRestDays(weekStart, workouts))
    }

    private fun withRestDays(weekStart: LocalDate, runs: List<Workout>): List<Workout> {
        val byDay = runs.associateBy { it.dateEpochDay }
        return (0..6).map { d ->
            val day = weekStart.plusDays(d.toLong()).toEpochDay()
            byDay[day] ?: Workout(day, WorkoutType.REST, 0.0, "휴식 또는 스트레칭·보강운동")
        }
    }

    private fun easyDesc(z: PaceZones?) =
        z?.let { "이지 페이스 ${Pace.formatRange(it.easy)}" } ?: "대화가 가능한 편한 속도"

    private fun tempoDesc(z: PaceZones?) =
        "워밍업 2km + 템포 20~30분" + (z?.let { " (${Pace.formatRange(it.tempo)})" } ?: " (약간 힘든 속도)") + " + 쿨다운"

    private fun intervalDesc(goal: RaceDistance, z: PaceZones?): String {
        val set = when (goal) {
            RaceDistance.FIVE_K, RaceDistance.TEN_K -> "400m × 8 (사이 200m 조깅)"
            RaceDistance.HALF, RaceDistance.FULL -> "1km × 5 (사이 400m 조깅)"
        }
        return "워밍업 2km + $set" + (z?.let { " @ ${Pace.formatRange(it.interval)}" } ?: "") + " + 쿨다운"
    }

    private fun roundHalf(km: Double) = (km * 2).roundToInt() / 2.0
}
