package com.vctrch.golfgps.location

import com.vctrch.golfgps.domain.GeoMath
import com.vctrch.golfgps.domain.LatLng

/** Platform-agnostic GPS sample used for fix-quality filtering. */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val timeMillis: Long,
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}

/**
 * Filters location fixes the way iOS LocationService does: reject stale / absurd accuracy,
 * and prefer fresher or more accurate updates for live yardage.
 */
object LocationFixFilter {
    fun shouldAccept(
        location: LocationSample,
        comparedTo: LocationSample?,
    ): Boolean {
        if (location.accuracyMeters < 0f) return false
        if (location.accuracyMeters > 10_000f) return false
        if (ageInSeconds(location) >= 120.0) return false

        val current = comparedTo ?: return true

        val currentAge = ageInSeconds(current)
        val maxDrift = maxOf(current.accuracyMeters + 150f, 200f)

        if (location.timeMillis > current.timeMillis && location.accuracyMeters <= maxDrift) {
            return true
        }

        if (currentAge > 5.0 &&
            location.timeMillis >= current.timeMillis &&
            location.accuracyMeters <= maxOf(current.accuracyMeters + 200f, 500f)
        ) {
            return true
        }

        if (GeoMath.meters(location.toLatLng(), current.toLatLng()) >= 1.0) {
            return true
        }

        return isBetterLocation(location, current)
    }

    fun isBetterLocation(
        candidate: LocationSample,
        current: LocationSample,
    ): Boolean {
        val candidateAge = ageInSeconds(candidate)
        val currentAge = ageInSeconds(current)

        if (candidate.accuracyMeters + 15f < current.accuracyMeters) {
            return true
        }

        if (candidate.timeMillis > current.timeMillis &&
            candidate.accuracyMeters <= current.accuracyMeters + 75f
        ) {
            return true
        }

        if (currentAge > 10.0 &&
            candidateAge < 30.0 &&
            candidate.accuracyMeters <= maxOf(current.accuracyMeters, 500f)
        ) {
            return true
        }

        return false
    }

    fun bestLocation(from: List<LocationSample>): LocationSample? {
        val valid = from.filter { it.accuracyMeters >= 0f && it.accuracyMeters <= 10_000f }
        if (valid.isEmpty()) return null
        val accuratePool = valid.filter { it.accuracyMeters <= 200f }
        val pool = accuratePool.ifEmpty { valid }
        return pool.maxByOrNull { it.timeMillis }
    }

    fun ageInSeconds(
        location: LocationSample,
        nowMillis: Long = System.currentTimeMillis(),
    ): Double {
        val ageMs = nowMillis - location.timeMillis
        return maxOf(0.0, ageMs / 1_000.0)
    }
}
