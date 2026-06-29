package com.vctrch.golfgps.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.vctrch.golfgps.domain.LatLng
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
        @ApplicationContext private val context: Context,
    ) {
        private val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        fun hasLocationPermission(): Boolean {
            val fine =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val coarse =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        @SuppressLint("MissingPermission")
        fun locationUpdates(): Flow<LatLng?> =
            callbackFlow {
                // Without permission, emit nothing rather than throwing a SecurityException. The
                // caller re-collects this flow once permission is granted.
                if (!hasLocationPermission()) {
                    awaitClose { }
                    return@callbackFlow
                }

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

                try {
                    // Seed with the last known fix, then request an active one (more reliable on
                    // emulators, which often have no cached location).
                    fusedClient.lastLocation.addOnSuccessListener { location ->
                        location?.let { trySend(it.toLatLng()) }
                    }
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            location?.let { trySend(it.toLatLng()) }
                        }
                    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                } catch (e: SecurityException) {
                    close(e)
                    return@callbackFlow
                }

                awaitClose { fusedClient.removeLocationUpdates(callback) }
            }

        private fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)
    }
