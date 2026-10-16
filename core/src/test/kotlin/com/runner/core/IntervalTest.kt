package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntervalTest {
    private val steps = listOf(IntervalStep(false, 10), IntervalStep(true, 20))

    @Test fun findsCurrentStep() {
        assertEquals(IntervalPosition(0, 10), IntervalPrograms.at(steps, 0))
        assertEquals(IntervalPosition(0, 1), IntervalPrograms.at(steps, 9))
        assertEquals(IntervalPosition(1, 20), IntervalPrograms.at(steps, 10))
        assertNull(IntervalPrograms.at(steps, 30))
    }

    @Test fun beginnerHasNoEmptyWalks() {
        IntervalPrograms.beginner.forEach { s -> s.steps.forEach { assert(it.sec > 0) { s.name } } }
        assertEquals(8, IntervalPrograms.beginner.size)
    }
}
