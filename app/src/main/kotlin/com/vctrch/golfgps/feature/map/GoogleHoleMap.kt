package com.vctrch.golfgps.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.vctrch.golfgps.domain.*
import com.google.android.gms.maps.model.LatLng as MapsLatLng

@Composable
fun GoogleHoleMap(
    hole: HoleTarget,
    userLocation: LatLng?,
    mapDisplayStyle: MapDisplayStyle,
    modifier: Modifier = Modifier,
) {
    val green = MapsLatLng(hole.green.latitude, hole.green.longitude)
    val tee = hole.tee?.let { MapsLatLng(it.latitude, it.longitude) }
    // Only draw/frame the player when they're realistically on this hole, so a stale or faraway fix
    // (e.g. an emulator's default location) doesn't stretch the view across the whole region.
    val player =
        userLocation
            ?.takeIf { hole.isPlayerOnHole(it) }
            ?.let { MapsLatLng(it.latitude, it.longitude) }

    val camera =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(green, 16f)
        }

    // Always frame the hole mapping (tee -> green); include the player only when on the hole so an
    // off-course fix never distorts the view. Falls back to centering on the green when alone.
    val framingPoints =
        buildList {
            add(green)
            tee?.let { add(it) }
            player?.let { add(it) }
        }
    LaunchedEffect(framingPoints) {
        if (framingPoints.size >= 2) {
            val builder = LatLngBounds.builder()
            framingPoints.forEach { builder.include(it) }
            camera.animate(CameraUpdateFactory.newLatLngBounds(builder.build(), BOUNDS_PADDING_PX))
        } else {
            camera.animate(CameraUpdateFactory.newLatLngZoom(green, 16f))
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(mapType = mapDisplayStyle.toGoogleMapType()),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = userLocation != null),
    ) {
        // The hole itself: tee -> green.
        tee?.let { Polyline(points = listOf(it, green), color = Color(0xCCFFFFFF), width = 4f) }
        Marker(state = MarkerState(green), title = "Green")
        tee?.let { Marker(state = MarkerState(it), title = "Tee") }
        // The live shot: player -> green.
        player?.let {
            Polyline(points = listOf(it, green), color = Color(0xFFFFFFFF), width = 8f)
            Polyline(points = listOf(it, green), color = Color(0xFF1B5E20), width = 4f)
            Marker(state = MarkerState(it), title = "You")
        }
    }
}

private const val BOUNDS_PADDING_PX = 180

private fun MapDisplayStyle.toGoogleMapType(): MapType {
    return when (this) {
        MapDisplayStyle.STANDARD -> MapType.NORMAL
        MapDisplayStyle.SATELLITE -> MapType.SATELLITE
        MapDisplayStyle.HYBRID -> MapType.HYBRID
    }
}
