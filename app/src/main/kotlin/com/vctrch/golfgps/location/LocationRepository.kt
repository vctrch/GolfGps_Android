package com.vctrch.golfgps.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.Settings
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class LocationUiStatus(
    val locationServicesDisabled: Boolean = false,
    val needsSettings: Boolean = false,
    val isAuthorized: Boolean = false,
    val isPreciseLocationEnabled: Boolean = true,
    val hasFix: Boolean = false,
)

@Singleton
class LocationRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val fusedClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

        private val _status = MutableStateFlow(refreshStatus(hasFix = false))
        val status: StateFlow<LocationUiStatus> = _status.asStateFlow()

        fun hasLocationPermission(): Boolean {
            val fine =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val coarse =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        fun hasFineLocationPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        fun refreshPermissionStatus(hasFix: Boolean = _status.value.hasFix) {
            _status.value = refreshStatus(hasFix)
        }

        fun openAppSettings() {
            val intent =
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun openLocationSettings() {
            val intent =
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        /**
         * Android has no temporary full-accuracy API like iOS. When only coarse location is granted,
         * open app settings so the user can enable precise location.
         */
        fun requestPreciseLocationIfNeeded() {
            if (!hasLocationPermission()) return
            if (hasFineLocationPermission()) return
            openAppSettings()
        }

        @SuppressLint("MissingPermission")
        fun locationUpdates(): Flow<LatLng?> =
            callbackFlow {
                refreshPermissionStatus(hasFix = false)
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
                            _status.update { refreshStatus(hasFix = true) }
                            trySend(location.toLatLng())
                        }
                    }

                try {
                    fusedClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            _status.update { refreshStatus(hasFix = true) }
                            trySend(it.toLatLng())
                        }
                    }
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { location ->
                            location?.let {
                                _status.update { refreshStatus(hasFix = true) }
                                trySend(it.toLatLng())
                            }
                        }
                    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                } catch (e: SecurityException) {
                    close(e)
                    return@callbackFlow
                }

                awaitClose { fusedClient.removeLocationUpdates(callback) }
            }

        private fun refreshStatus(hasFix: Boolean): LocationUiStatus {
            val servicesEnabled = isSystemLocationEnabled()
            val authorized = hasLocationPermission()
            return LocationUiStatus(
                locationServicesDisabled = !servicesEnabled,
                needsSettings = servicesEnabled && !authorized,
                isAuthorized = authorized,
                isPreciseLocationEnabled = !authorized || hasFineLocationPermission() || Build.VERSION.SDK_INT < 31,
                hasFix = hasFix && authorized,
            )
        }

        private fun isSystemLocationEnabled(): Boolean {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                @Suppress("DEPRECATION")
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        }

        private fun Location.toLatLng(): LatLng = LatLng(latitude, longitude)
    }
