package com.runner.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanGeneratorTest {
    private val today = LocalDate.of(2026, 9, 24)

    private fun plan(goal: RaceDistance, days: Long, weeklyKm: Double = 20.0, runs: Int = 4) =
        TrainingPlanGenerator.generate(
            TrainingPlanGenerator.Input(goal, today.plusDays(days), today, weeklyKm, runs, "테스트 대회",
                Pace.zones(10.0, 3300, goal)),
        )

    @Test fun lastWorkoutIsRaceOnRaceDay() {
        val p = plan(RaceDistance.HALF, 70)
        val last = p.weeks.last().workouts.last()
        assertEquals(WorkoutType.RACE, last.type)
        assertEquals(today.plusDays(70).toEpochDay(), last.dateEpochDay)
    }

    @Test fun planNeverStartsBeforeToday() {
        for (days in listOf(0L, 6L, 30L, 100L, 400L)) {
            val first = plan(RaceDistance.TEN_K, days).weeks.first().workouts.first()
            // 1주 미만 남은 경우만 과거 날짜를 포함할 수 있음
            if (days >= 6) assertTrue("days=$days", first.dateEpochDay >= today.toEpochDay())
        }
    }

    @Test fun capsAtMaxWeeks() {
        assertEquals(TrainingPlanGenerator.MAX_WEEKS, plan(RaceDistance.FULL, 400).weeks.size)
    }

    @Test fun weeklyVolumeRampsGradually() {
        val weeks = plan(RaceDistance.FULL, 16 * 7 - 1, weeklyKm = 25.0).weeks
        val build = weeks.filter { it.phase == "기초" || it.phase == "강화" }
        build.zipWithNext().forEach { (a, b) ->
            assertTrue("${a.totalKm} -> ${b.totalKm}", b.totalKm <= a.totalKm * 1.2 + 1.0)
        }
    }

    @Test fun taperReducesVolume() {
        val weeks = plan(RaceDistance.FULL, 16 * 7 - 1, weeklyKm = 40.0).weeks
        val peak = weeks.filter { it.phase == "강화" }.maxOf { it.totalKm }
        weeks.filter { it.phase == "테이퍼" }.forEach { assertTrue(it.totalKm < peak) }
    }

    @Test fun everyWeekHasSevenDaysAndRequestedRunCount() {
        val p = plan(RaceDistance.TEN_K, 60, runs = 5)
        p.weeks.forEach { w ->
            assertEquals(7, w.workouts.size)
            if (w.phase != "대회 주") assertEquals(5, w.workouts.count { it.type != WorkoutType.REST })
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPastRace() {
        TrainingPlanGenerator.generate(
            TrainingPlanGenerator.Input(RaceDistance.FIVE_K, today.minusDays(1), today, 10.0, 3),
        )
    }

    @Test fun autoTargetPicksEarliestUpcomingRace() {
        fun race(id: String, days: Long) = Race(id, id, today.plusDays(days).toEpochDay(), RaceDistance.TEN_K)
        val races = listOf(race("late", 60), race("past", -3), race("soon", 30))
        assertEquals("soon", TrainingPlanGenerator.autoTarget(races, null, today)?.id)
        // 더 늦은 대회를 향한 계획은 먼저 열리는 대회로 바꾼다
        assertEquals("soon", TrainingPlanGenerator.autoTarget(races, plan(RaceDistance.TEN_K, 60), today)?.id)
        // 이미 가장 이른 대회(또는 더 이른 대회)를 향한 계획은 그대로 둔다
        assertEquals(null, TrainingPlanGenerator.autoTarget(races, plan(RaceDistance.TEN_K, 30), today))
        assertEquals(null, TrainingPlanGenerator.autoTarget(races, plan(RaceDistance.TEN_K, 20), today))
        assertEquals(null, TrainingPlanGenerator.autoTarget(listOf(race("past", -3)), null, today))
    }
}
