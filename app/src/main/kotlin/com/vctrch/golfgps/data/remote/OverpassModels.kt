package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.LatLng
import kotlinx.serialization.Serializable

@Serializable
data class OverpassResponse(
    val elements: List<OverpassElement> = emptyList(),
)

@Serializable
data class OverpassElement(
    val type: String? = null,
    val id: Long? = null,
    val tags: Map<String, String>? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    // `out center` returns a way's representative point here; `out geom` puts lat/lon on nodes.
    val center: OverpassNode? = null,
    val geometry: List<OverpassNode>? = null,
) {
    val coordinate: LatLng?
        get() =
            when {
                lat != null && lon != null -> LatLng(lat, lon)
                center != null -> LatLng(center.lat, center.lon)
                else -> null
            }

    val geometryCoordinates: List<LatLng>
        get() = geometry?.map { LatLng(it.lat, it.lon) } ?: emptyList()
}

@Serializable
data class OverpassNode(
    val lat: Double,
    val lon: Double,
)
