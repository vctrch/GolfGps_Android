package com.vctrch.golfgps.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFixFilterTest {
    @Test
    fun shouldAccept_rejectsNegativeAccuracy() {
        val location = sample(accuracy = -1f)
        assertFalse(LocationFixFilter.shouldAccept(location, comparedTo = null))
    }

    @Test
    fun shouldAccept_rejectsStaleFix() {
        val location = sample(accuracy = 10f, ageMs = 130_000L)
        assertFalse(LocationFixFilter.shouldAccept(location, comparedTo = null))
    }

    @Test
    fun shouldAccept_acceptsFreshFirstFix() {
        val location = sample(accuracy = 12f)
        assertTrue(LocationFixFilter.shouldAccept(location, comparedTo = null))
    }

    @Test
    fun isBetterLocation_prefersMoreAccurateFix() {
        val current = sample(accuracy = 80f, ageMs = 5_000L)
        val candidate = sample(accuracy = 20f)
        assertTrue(LocationFixFilter.isBetterLocation(candidate, current))
    }

    @Test
    fun bestLocation_prefersAccurateFreshFix() {
        val coarse = sample(accuracy = 400f, ageMs = 1_000L)
        val accurate = sample(accuracy = 15f)
        val best = LocationFixFilter.bestLocation(listOf(coarse, accurate))
        assertNotNull(best)
        assertTrue(best!!.accuracyMeters <= 200f)
    }

    private fun sample(
        accuracy: Float,
        ageMs: Long = 0L,
        lat: Double = 32.83,
        lon: Double = -117.27,
    ): LocationSample {
        return LocationSample(
            latitude = lat,
            longitude = lon,
            accuracyMeters = accuracy,
            timeMillis = System.currentTimeMillis() - ageMs,
        )
    }
}
