package com.vctrch.golfgps.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.vctrch.golfgps.domain.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        @SuppressLint("MissingPermission")
        fun locationUpdates(): Flow<LatLng?> =
            callbackFlow {
                val request =
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                        .setMinUpdateIntervalMillis(500L)
                        .build()

                val callback =
                    object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            val location = result.lastLocation ?: return
                            trySend(location.toLatLng())
                        }
                    }

                fusedClient.requestLocationUpdates(request, callback, null)
                awaitClose { fusedClient.removeLocationUpdates(callback) }
            }

        private fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)
    }
