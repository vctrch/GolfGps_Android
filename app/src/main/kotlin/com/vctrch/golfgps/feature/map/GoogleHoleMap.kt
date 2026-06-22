package com.vctrch.golfgps.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
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
    val camera =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(green, 16f)
        }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = camera,
        properties = MapProperties(mapType = mapDisplayStyle.toGoogleMapType()),
        uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = userLocation != null),
    ) {
        Marker(state = MarkerState(green), title = "Green")
        hole.tee?.let { tee ->
            Marker(state = MarkerState(MapsLatLng(tee.latitude, tee.longitude)), title = "Tee")
        }
    }
}

private fun MapDisplayStyle.toGoogleMapType(): MapType {
    return when (this) {
        MapDisplayStyle.STANDARD -> MapType.NORMAL
        MapDisplayStyle.SATELLITE -> MapType.SATELLITE
        MapDisplayStyle.HYBRID -> MapType.HYBRID
    }
}
