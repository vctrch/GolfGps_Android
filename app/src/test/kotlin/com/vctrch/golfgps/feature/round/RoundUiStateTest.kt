package com.vctrch.golfgps.feature.round

import com.vctrch.golfgps.domain.*
import com.vctrch.golfgps.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundUiStateTest {
    @Test
    fun isSearchActive_requiresMinimumQueryLength() {
        assertFalse(RoundUiState(searchQuery = "a").isSearchActive)
        assertFalse(RoundUiState(searchQuery = " ").isSearchActive)
        assertTrue(RoundUiState(searchQuery = "pe").isSearchActive)
        assertTrue(RoundUiState(searchQuery = "  pe  ").isSearchActive)
    }

    @Test
    fun isRoundReady_requiresLoadedCourseAndCurrentHole() {
        assertFalse(RoundUiState().isRoundReady)

        val loaded = TestFixtures.loadedCourse()
        val ready =
            RoundUiState(
                loadedCourse = loaded,
                selectedHoleNumber = 1,
            )
        assertTrue(ready.isRoundReady)
        assertEquals(1, ready.currentHole?.number)
    }

    @Test
    fun distanceToGreen_nullWithoutLocationOrHole() {
        assertNull(RoundUiState().distanceToGreen())

        val withoutLocation =
            RoundUiState(
                loadedCourse = TestFixtures.loadedCourse(),
                selectedHoleNumber = 1,
            )
        assertNull(withoutLocation.distanceToGreen())
    }

    @Test
    fun distanceToGreen_computesFromUserLocation() {
        val loaded = TestFixtures.loadedCourse()
        val userLocation = LatLng(32.8328, -117.2713)
        val state =
            RoundUiState(
                loadedCourse = loaded,
                selectedHoleNumber = 1,
                userLocation = userLocation,
            )

        val distance = state.distanceToGreen()

        assertTrue(distance != null && distance >= 0)
    }
}
