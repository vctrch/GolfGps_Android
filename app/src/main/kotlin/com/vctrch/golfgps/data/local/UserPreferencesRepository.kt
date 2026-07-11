package com.vctrch.golfgps.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vctrch.golfgps.domain.MapDisplayStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "golfgps_preferences",
)

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {
    val mapDisplayStyle: Flow<MapDisplayStyle> =
        dataStore.data.map { prefs ->
            val raw = prefs[MAP_STYLE_KEY]
            MapDisplayStyle.entries.firstOrNull { it.storageKey == raw } ?: MapDisplayStyle.STANDARD
        }

    /** When false, Firebase Analytics and Crashlytics collection are disabled. Default true. */
    val usageDiagnosticsEnabled: Flow<Boolean> =
        dataStore.data.map { prefs ->
            prefs[USAGE_DIAGNOSTICS_KEY] ?: true
        }

    suspend fun setMapDisplayStyle(style: MapDisplayStyle) {
        dataStore.edit { prefs ->
            prefs[MAP_STYLE_KEY] = style.storageKey
        }
    }

    suspend fun setUsageDiagnosticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[USAGE_DIAGNOSTICS_KEY] = enabled
        }
    }

    companion object {
        private val MAP_STYLE_KEY = stringPreferencesKey("map_display_style")
        private val USAGE_DIAGNOSTICS_KEY = booleanPreferencesKey("usage_diagnostics_enabled")

        fun create(context: Context): UserPreferencesRepository {
            return UserPreferencesRepository(context.userPreferencesDataStore)
        }
    }
}
