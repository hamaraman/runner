package com.runner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpxTest {
    @Test
    fun splitsSegmentsAndWritesPoints() {
        val run = RunRecord(
            id = "r", startedAtMs = 0, durationSec = 60, distanceM = 100.0,
            points = listOf(
                TrackPoint(37.5, 127.0, 0, segment = 0),
                TrackPoint(37.6, 127.1, 1000, segment = 0),
                TrackPoint(37.7, 127.2, 5000, segment = 1),
            ),
        )
        val gpx = Gpx.from(run)
        assertEquals(2, Regex("<trkseg>").findAll(gpx).count())
        assertEquals(3, Regex("<trkpt ").findAll(gpx).count())
        assertTrue(gpx.contains("""lat="37.6" lon="127.1""""))
        assertTrue(gpx.contains("<time>1970-01-01T00:00:01Z</time>"))
    }
}
