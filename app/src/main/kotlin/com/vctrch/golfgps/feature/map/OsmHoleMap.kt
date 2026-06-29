package com.vctrch.golfgps.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vctrch.golfgps.domain.*
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Composable
fun OsmHoleMap(
    hole: HoleTarget,
    userLocation: LatLng?,
    mapDisplayStyle: MapDisplayStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }
    val greenPoint = remember(hole.number) { GeoPoint(hole.green.latitude, hole.green.longitude) }
    val teePoint = remember(hole.number) { hole.tee?.let { GeoPoint(it.latitude, it.longitude) } }
    // Only draw/frame the player when they're realistically on this hole, so a stale or faraway fix
    // doesn't stretch the view across the whole region.
    val playerPoint =
        userLocation
            ?.takeIf { hole.isPlayerOnHole(it) }
            ?.let { GeoPoint(it.latitude, it.longitude) }

    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapViewHolder.value?.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapViewHolder.value?.onPause()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapViewHolder.value?.onPause()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            MapView(context).apply {
                setMultiTouchControls(true)
                setTileSource(mapDisplayStyle.toOsmTileSource())
                controller.setZoom(16.0)
                controller.setCenter(greenPoint)
                mapViewHolder.value = this
            }
        },
        update = { mapView ->
            mapView.setTileSource(mapDisplayStyle.toOsmTileSource())
            mapView.overlays.clear()

            // The hole itself: tee -> green.
            if (teePoint != null) {
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        setPoints(listOf(teePoint, greenPoint))
                        outlinePaint.strokeWidth = 4f
                        outlinePaint.color = HOLE_LINE_COLOR
                    },
                )
            }

            // The live shot: player -> green.
            if (playerPoint != null) {
                mapView.overlays.add(
                    Polyline(mapView).apply {
                        setPoints(listOf(playerPoint, greenPoint))
                        outlinePaint.strokeWidth = 8f
                        outlinePaint.color = PLAYER_LINE_COLOR
                    },
                )
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = playerPoint
                        title = "You"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
            }

            mapView.overlays.add(
                Marker(mapView).apply {
                    position = greenPoint
                    title = "Green"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                },
            )
            teePoint?.let {
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = it
                        title = "Tee"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
            }

            // Always frame the hole mapping (tee -> green); include the player only when on the hole.
            val framingPoints =
                buildList {
                    add(greenPoint)
                    teePoint?.let { add(it) }
                    playerPoint?.let { add(it) }
                }
            if (framingPoints.size >= 2) {
                val box = BoundingBox.fromGeoPoints(framingPoints)
                mapView.post { mapView.zoomToBoundingBox(box, true, MAP_PADDING_PX) }
            } else {
                mapView.controller.setCenter(greenPoint)
            }
            mapView.invalidate()
        },
        onRelease = { mapView ->
            mapView.onPause()
            mapViewHolder.value = null
        },
    )
}

private const val MAP_PADDING_PX = 80
private const val PLAYER_LINE_COLOR = 0xFF1B5E20.toInt()
private const val HOLE_LINE_COLOR = 0xCCFFFFFF.toInt()
