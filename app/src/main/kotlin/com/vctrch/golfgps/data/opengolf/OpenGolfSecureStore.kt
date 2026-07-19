package com.vctrch.golfgps.data.opengolf

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class OpenGolfSecureStore(
    private val prefs: SharedPreferences,
) {
    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var email: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var playerId: String?
        get() = prefs.getString(KEY_PLAYER_ID, null)
        set(value) = prefs.edit().putString(KEY_PLAYER_ID, value).apply()

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "opengolf_secure"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_EMAIL = "email"
        private const val KEY_PLAYER_ID = "player_id"

        fun create(context: Context): OpenGolfSecureStore {
            val masterKey =
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
            val prefs =
                EncryptedSharedPreferences.create(
                    context,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            return OpenGolfSecureStore(prefs)
        }
    }
}
