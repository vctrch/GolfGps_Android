package com.vctrch.golfgps.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.vctrch.golfgps.domain.*
import com.google.android.gms.maps.model.LatLng as MapsLatLng

@Composable
fun GoogleHoleMap(
    hole: HoleTarget,
    userLocation: LatLng?,
    locationAuthorized: Boolean = false,
    mapDisplayStyle: MapDisplayStyle,
    modifier: Modifier = Modifier,
    onCameraCenterChanged: (LatLng) -> Unit = {},
) {
    val green = MapsLatLng(hole.green.latitude, hole.green.longitude)
    val tee = hole.tee?.let { MapsLatLng(it.latitude, it.longitude) }
    // Only draw the player when they're realistically on this hole, so a stale or faraway fix
    // (e.g. an emulator's default location) doesn't stretch overlays across the whole region.
    val player =
        userLocation
            ?.takeIf { hole.isPlayerOnHole(it) }
            ?.let { MapsLatLng(it.latitude, it.longitude) }

    val greenMarker = rememberUpdatedMarkerState(green)
    // Optional markers still need a remembered state; unused when tee/player is null.
    val teeMarker = rememberUpdatedMarkerState(tee ?: green)
    val playerMarker = rememberUpdatedMarkerState(player ?: green)

    val density = LocalContext.current.resources.displayMetrics.density
    val markerSizePx = remember(density) { HoleMapMarkerIcons.sizePx(density) }
    val greenIcon =
        remember(hole.greenMappingConfidence, markerSizePx) {
            BitmapDescriptorFactory.fromBitmap(
                HoleMapMarkerIcons.bitmap(
                    HoleMapMarkerKind.GREEN,
                    HoleMapMarkerPalette.greenFill(hole.greenMappingConfidence),
                    markerSizePx,
                ),
            )
        }
    val teeIcon =
        remember(hole.teeMappingConfidence, markerSizePx) {
            BitmapDescriptorFactory.fromBitmap(
                HoleMapMarkerIcons.bitmap(
                    HoleMapMarkerKind.TEE,
                    HoleMapMarkerPalette.teeFill(hole.teeMappingConfidence),
                    markerSizePx,
                ),
            )
        }
    val playerIcon =
        remember(markerSizePx) {
            BitmapDescriptorFactory.fromBitmap(
                HoleMapMarkerIcons.bitmap(
                    HoleMapMarkerKind.PLAYER,
                    HoleMapMarkerPalette.PLAYER_FILL,
                    markerSizePx,
                ),
            )
        }

    val camera =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(green, 16f)
        }
    var userAdjustedCamera by remember { mutableStateOf(false) }

    LaunchedEffect(hole.number) {
        userAdjustedCamera = false
    }

    LaunchedEffect(camera.isMoving, camera.cameraMoveStartedReason) {
        if (camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            userAdjustedCamera = true
        }
        if (!camera.isMoving) {
            val target = camera.position.target
            onCameraCenterChanged(LatLng(target.latitude, target.longitude))
        }
    }

    // Frame tee/green when the hole mapping changes — not on every GPS tick, and not after pinch-zoom.
    LaunchedEffect(hole.number, hole.green, hole.tee, userAdjustedCamera) {
        if (userAdjustedCamera) return@LaunchedEffect
        val framingPoints =
            buildList {
                add(green)
                tee?.let { add(it) }
            }
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
        properties =
            MapProperties(
                mapType = mapDisplayStyle.toGoogleMapType(),
                isMyLocationEnabled = locationAuthorized,
            ),
        uiSettings =
            MapUiSettings(
                zoomControlsEnabled = true,
                myLocationButtonEnabled = locationAuthorized,
            ),
    ) {
        // The hole itself: tee -> green.
        tee?.let { Polyline(points = listOf(it, green), color = Color(0xCCFFFFFF), width = 4f) }
        Marker(
            state = greenMarker,
            title = HoleMapMarkerPalette.greenTitle(hole.greenMappingConfidence),
            icon = greenIcon,
            anchor = PIN_ANCHOR,
        )
        if (tee != null) {
            Marker(
                state = teeMarker,
                title = hole.teeMappingConfidence.shortLabel,
                icon = teeIcon,
                anchor = PIN_ANCHOR,
            )
        }
        // The live shot: player -> green.
        player?.let {
            Polyline(points = listOf(it, green), color = Color(0xFFFFFFFF), width = 8f)
            Polyline(points = listOf(it, green), color = Color(0xFF1B5E20), width = 4f)
            Marker(
                state = playerMarker,
                title = "You",
                icon = playerIcon,
                anchor = PLAYER_ANCHOR,
            )
        }
    }
}

private const val BOUNDS_PADDING_PX = 180
private val PIN_ANCHOR = Offset(0.5f, 1.0f)
private val PLAYER_ANCHOR = Offset(0.5f, 0.5f)

private fun MapDisplayStyle.toGoogleMapType(): MapType {
    return when (this) {
        MapDisplayStyle.STANDARD -> MapType.NORMAL
        MapDisplayStyle.SATELLITE -> MapType.SATELLITE
        MapDisplayStyle.HYBRID -> MapType.HYBRID
    }
}
