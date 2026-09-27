package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkRunTest {
    private fun w(day: Long, type: WorkoutType = WorkoutType.EASY, done: Boolean = false) = Workout(day, type, 5.0, "", done)
    private fun plan(vararg weeks: List<Workout>) = TrainingPlan(RaceDistance.TEN_K, 20, "", null,
        weeks.mapIndexed { i, ws -> TrainingWeek(i, "기초", 10.0, ws) })
    private fun TrainingPlan.done() = weeks.flatMap { it.workouts }.filter { it.done }.map { it.dateEpochDay }

    @Test fun sameDayFirst() = assertEquals(listOf(3L), plan(listOf(w(1), w(2, WorkoutType.REST), w(3))).markRun(3).done())

    @Test fun otherDayChecksEarliestOpenInWeek() =
        assertEquals(listOf(1L), plan(listOf(w(1), w(2, WorkoutType.REST), w(3))).markRun(2).done())

    @Test fun skipsDoneAndRace() =
        assertEquals(listOf(1L, 3L), plan(listOf(w(1, done = true), w(3), w(5, WorkoutType.RACE))).markRun(3).markRun(4).done())

    @Test fun otherWeekUntouched() =
        assertEquals(emptyList<Long>(), plan(listOf(w(1)), listOf(w(8, WorkoutType.REST))).markRun(8).done())
}
