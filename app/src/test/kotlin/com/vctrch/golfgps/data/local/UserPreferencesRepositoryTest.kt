package com.vctrch.golfgps.data.local

import com.vctrch.golfgps.domain.MapDisplayStyle
import com.vctrch.golfgps.testing.createTestPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UserPreferencesRepositoryTest {
    @Test
    fun mapDisplayStyle_defaultsToStandard() =
        runTest {
            val repository = createTestPreferencesRepository()

            assertEquals(MapDisplayStyle.STANDARD, repository.mapDisplayStyle.first())
        }

    @Test
    fun setMapDisplayStyle_persistsSelection() =
        runTest {
            val repository = createTestPreferencesRepository()

            repository.setMapDisplayStyle(MapDisplayStyle.SATELLITE)

            assertEquals(MapDisplayStyle.SATELLITE, repository.mapDisplayStyle.first())
        }
}
