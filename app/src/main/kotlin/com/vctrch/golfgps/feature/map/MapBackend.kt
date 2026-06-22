package com.vctrch.golfgps.feature.map

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.vctrch.golfgps.BuildConfig

enum class MapBackend {
    GOOGLE,
    OPEN_STREET_MAP,
}

fun resolveMapBackend(context: Context): MapBackend {
    if (BuildConfig.USE_OSM_MAP) return MapBackend.OPEN_STREET_MAP
    if (!hasGooglePlayServices(context)) return MapBackend.OPEN_STREET_MAP
    if (BuildConfig.MAPS_API_KEY.isBlank() || BuildConfig.MAPS_API_KEY == "YOUR_MAPS_API_KEY") {
        return MapBackend.OPEN_STREET_MAP
    }
    return MapBackend.GOOGLE
}

private fun hasGooglePlayServices(context: Context): Boolean {
    return GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
}
