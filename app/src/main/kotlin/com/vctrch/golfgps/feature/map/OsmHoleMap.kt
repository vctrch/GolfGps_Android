package com.vctrch.golfgps.feature.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vctrch.golfgps.domain.*
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun OsmHoleMap(
    hole: HoleTarget,
    mapDisplayStyle: MapDisplayStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }
    val greenPoint = remember(hole.number) { GeoPoint(hole.green.latitude, hole.green.longitude) }

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
            mapView.overlays.add(
                Marker(mapView).apply {
                    position = greenPoint
                    title = "Green"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                },
            )
            hole.tee?.let { tee ->
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = GeoPoint(tee.latitude, tee.longitude)
                        title = "Tee"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
            }
            mapView.controller.setCenter(greenPoint)
            mapView.invalidate()
        },
        onRelease = { mapView ->
            mapView.onPause()
            mapViewHolder.value = null
        },
    )
}
