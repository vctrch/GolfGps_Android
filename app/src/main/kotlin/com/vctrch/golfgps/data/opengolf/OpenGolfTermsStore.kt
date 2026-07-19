package com.vctrch.golfgps.data.opengolf

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.openGolfTermsDataStore by preferencesDataStore(name = "opengolf_terms")

@Singleton
class OpenGolfTermsStore
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        val acceptedVersion: Flow<String?> =
            context.openGolfTermsDataStore.data.map { prefs ->
                prefs[ACCEPTED_VERSION_KEY]?.takeIf { it.isNotBlank() }
            }

        suspend fun acceptedVersionSnapshot(): String? = acceptedVersion.first()

        suspend fun accept(version: String) {
            val trimmed = version.trim()
            if (trimmed.isEmpty()) return
            context.openGolfTermsDataStore.edit { prefs ->
                prefs[ACCEPTED_VERSION_KEY] = trimmed
            }
        }

        suspend fun hasAccepted(version: String): Boolean {
            return acceptedVersionSnapshot() == version.trim()
        }

        private companion object {
            val ACCEPTED_VERSION_KEY = stringPreferencesKey("accepted_terms_version")
        }
    }
