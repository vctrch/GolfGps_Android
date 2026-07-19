package com.vctrch.golfgps.data.opengolf

/** OpenGolf settings provided once at the Hilt boundary (from BuildConfig). */
data class OpenGolfConfig(
    val apiKey: String,
    val baseUrl: String,
    val clientId: String,
    val redirectUri: String,
) {
    val apiBaseUrl: String get() = baseUrl.trimEnd('/')

    val hasApiKey: Boolean get() = apiKey.isNotBlank()
}
