package com.runner.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test fun comparesNumerically() {
        assertTrue(isNewerVersion("v0.4.2", "0.4.1"))
        assertTrue(isNewerVersion("v0.10.0", "0.9.9"))
        assertTrue(isNewerVersion("v1.0", "0.9.9"))
        assertFalse(isNewerVersion("v0.4.1", "0.4.1"))
        assertFalse(isNewerVersion("v0.4.0", "0.4.1"))
        assertFalse(isNewerVersion("v0.4", "0.4.0"))
    }
}
