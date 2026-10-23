package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTest {
    private val zones = PaceZones(
        easy = IntRange2(360, 390),
        tempo = IntRange2(300, 315),
        interval = IntRange2(270, 285),
        race = 310,
    )

    private fun plan(goal: RaceDistance, vararg workouts: Workout, z: PaceZones? = zones) =
        TrainingPlan(goal, 0, "", z, listOf(TrainingWeek(0, "", 0.0, workouts.toList())))

    private fun wo(type: WorkoutType, km: Double, day: Long = 1) = Workout(day, type, km, "")

    private fun steps(goal: RaceDistance, w: Workout, vararg others: Workout, z: PaceZones? = zones) =
        GuideSteps.forWorkout(plan(goal, w, *others, z = z), w)

    @Test fun easyIsSingleDistanceStep() {
        val s = steps(RaceDistance.TEN_K, wo(WorkoutType.EASY, 6.0))
        assertEquals(1, s.size)
        assertEquals(6000.0, s[0].distanceM!!, 0.0)
        assertEquals(zones.easy, s[0].pace)
    }

    @Test fun intervalTenK() {
        val s = steps(RaceDistance.TEN_K, wo(WorkoutType.INTERVAL, 8.0))
        assertEquals(17, s.size)
        assertEquals("워밍업", s.first().label)
        assertEquals(2000.0, s.first().distanceM!!, 0.0)
        assertEquals("쿨다운", s.last().label)
        assertEquals(8000.0, s.last().untilTotalM!!, 0.0)
        val fast = s.filter { it.label == "빠르게" }
        assertEquals(8, fast.size)
        assertEquals((1..8).toList(), fast.map { it.rep })
        fast.forEach {
            assertEquals(400.0, it.distanceM!!, 0.0)
            assertEquals(zones.interval, it.pace)
            assertEquals(8, it.reps)
        }
        val jog = s.filter { it.label == "조깅" }
        assertEquals(7, jog.size)
        jog.forEach { assertEquals(200.0, it.distanceM!!, 0.0); assertNull(it.pace) }
        assertEquals("빠르게", s[s.size - 2].label)
    }

    @Test fun intervalHalf() {
        val s = steps(RaceDistance.HALF, wo(WorkoutType.INTERVAL, 10.0))
        assertEquals(5, s.count { it.label == "빠르게" && it.distanceM == 1000.0 })
        assertEquals(4, s.count { it.label == "조깅" && it.distanceM == 400.0 })
    }

    @Test fun tempo() {
        val s = steps(RaceDistance.TEN_K, wo(WorkoutType.TEMPO, 7.0))
        assertEquals(listOf("워밍업", "템포", "쿨다운"), s.map { it.label })
        assertEquals(2000.0, s[0].distanceM!!, 0.0)
        assertEquals(1500, s[1].sec)
        assertEquals(zones.tempo, s[1].pace)
        assertEquals(7000.0, s[2].untilTotalM!!, 0.0)
    }

    @Test fun raceWeekTempo() {
        val s = steps(RaceDistance.TEN_K, wo(WorkoutType.TEMPO, 5.0), wo(WorkoutType.RACE, 10.0, day = 7))
        assertEquals(listOf("가볍게", "대회 페이스", "쿨다운"), s.map { it.label })
        assertEquals(3000.0, s[0].distanceM!!, 0.0)
        assertEquals(1000.0, s[1].distanceM!!, 0.0)
        assertEquals(IntRange2(305, 315), s[1].pace)
        assertEquals(5000.0, s[2].untilTotalM!!, 0.0)
    }

    @Test fun cooldownSkippedWhenNoRoom() {
        assertEquals("쿨다운", steps(RaceDistance.TEN_K, wo(WorkoutType.INTERVAL, 7.0)).last().label)
        assertEquals("빠르게", steps(RaceDistance.TEN_K, wo(WorkoutType.INTERVAL, 6.0)).last().label)
    }

    @Test fun noZonesStillBuildsSteps() {
        val s = steps(RaceDistance.TEN_K, wo(WorkoutType.INTERVAL, 8.0), z = null)
        assertEquals(17, s.size)
        assertTrue(s.all { it.pace == null })
    }

    @Test fun restIsEmpty() {
        assertTrue(steps(RaceDistance.TEN_K, wo(WorkoutType.REST, 0.0)).isEmpty())
    }
}
