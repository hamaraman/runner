package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoeTest {
    @Test fun totalKmCountsOnlyOwnRunsPlusBase() {
        val runs = listOf(RunRecord("a", 0, 0, 5000.0, shoeId = "s"), RunRecord("b", 0, 0, 3000.0, shoeId = "x"), RunRecord("c", 0, 0, 1000.0))
        assertEquals(105.0, Shoe("s", "페가수스", baseKm = 100.0).totalKm(runs), 1e-9)
    }
}
