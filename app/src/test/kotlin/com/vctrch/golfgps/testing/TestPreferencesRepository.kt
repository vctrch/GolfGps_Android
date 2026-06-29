package com.vctrch.golfgps.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.vctrch.golfgps.data.local.UserPreferencesRepository
import java.io.File

fun createTestPreferencesRepository(): UserPreferencesRepository {
    val file = File.createTempFile("golfgps_prefs_", ".preferences_pb")
    val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { file },
        )
    return UserPreferencesRepository(dataStore)
}
