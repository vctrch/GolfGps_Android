package com.vctrch.golfgps.feature.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
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
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

@Composable
fun OsmHoleMap(
    hole: HoleTarget,
    userLocation: LatLng?,
    mapDisplayStyle: MapDisplayStyle,
    modifier: Modifier = Modifier,
    onCameraCenterChanged: (LatLng) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }
    val lastFrameKey = remember { mutableStateOf<String?>(null) }
    val labelsOverlayHolder = remember { mutableStateOf<TilesOverlay?>(null) }
    val labelsStyleHolder = remember { mutableStateOf<MapDisplayStyle?>(null) }
    val greenPoint = remember(hole.number, hole.green) { GeoPoint(hole.green.latitude, hole.green.longitude) }
    val teePoint = remember(hole.number, hole.tee) { hole.tee?.let { GeoPoint(it.latitude, it.longitude) } }
    // Only draw the player when they're realistically on this hole, so a stale or faraway fix
    // doesn't stretch overlays across the whole region.
    val playerPoint =
        userLocation
            ?.takeIf { hole.isPlayerOnHole(it) }
            ?.let { GeoPoint(it.latitude, it.longitude) }
    val frameKey =
        "${hole.number}:${hole.green.latitude},${hole.green.longitude}:" +
            "${hole.tee?.latitude},${hole.tee?.longitude}"
    val density = context.resources.displayMetrics.density
    val markerSizePx = remember(density) { HoleMapMarkerIcons.sizePx(density) }
    val greenBitmap =
        remember(hole.greenMappingConfidence, markerSizePx) {
            HoleMapMarkerIcons.bitmap(
                HoleMapMarkerKind.GREEN,
                HoleMapMarkerPalette.greenFill(hole.greenMappingConfidence),
                markerSizePx,
            )
        }
    val teeBitmap =
        remember(hole.teeMappingConfidence, markerSizePx) {
            HoleMapMarkerIcons.bitmap(
                HoleMapMarkerKind.TEE,
                HoleMapMarkerPalette.teeFill(hole.teeMappingConfidence),
                markerSizePx,
            )
        }
    val playerBitmap =
        remember(markerSizePx) {
            HoleMapMarkerIcons.bitmap(
                HoleMapMarkerKind.PLAYER,
                HoleMapMarkerPalette.PLAYER_FILL,
                markerSizePx,
            )
        }

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
            mapView.overlays.removeAll { it !is TilesOverlay }

            val labelsSource = mapDisplayStyle.osmLabelsOverlaySource()
            if (labelsSource == null) {
                labelsOverlayHolder.value?.let { overlay ->
                    mapView.overlays.remove(overlay)
                    overlay.onDetach(mapView)
                }
                labelsOverlayHolder.value = null
                labelsStyleHolder.value = mapDisplayStyle
            } else if (labelsStyleHolder.value != mapDisplayStyle || labelsOverlayHolder.value == null) {
                labelsOverlayHolder.value?.let { overlay ->
                    mapView.overlays.remove(overlay)
                    overlay.onDetach(mapView)
                }
                val provider = MapTileProviderBasic(context, labelsSource)
                val labelsOverlay =
                    TilesOverlay(provider, context).apply {
                        loadingBackgroundColor = Color.TRANSPARENT
                        loadingLineColor = Color.TRANSPARENT
                    }
                labelsOverlayHolder.value = labelsOverlay
                labelsStyleHolder.value = mapDisplayStyle
                mapView.overlays.add(0, labelsOverlay)
            } else {
                val existing = labelsOverlayHolder.value!!
                mapView.overlays.remove(existing)
                mapView.overlays.add(0, existing)
            }

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
                        applyOpaqueIcon(playerBitmap, context.resources)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    },
                )
            }

            mapView.overlays.add(
                Marker(mapView).apply {
                    position = greenPoint
                    title = HoleMapMarkerPalette.greenTitle(hole.greenMappingConfidence)
                    applyOpaqueIcon(greenBitmap, context.resources)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                },
            )
            teePoint?.let {
                mapView.overlays.add(
                    Marker(mapView).apply {
                        position = it
                        title = hole.teeMappingConfidence.shortLabel
                        applyOpaqueIcon(teeBitmap, context.resources)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    },
                )
            }

            // Frame tee/green when the hole mapping changes — not on every GPS tick (preserves pinch-zoom).
            if (lastFrameKey.value != frameKey) {
                lastFrameKey.value = frameKey
                val framingPoints =
                    buildList {
                        add(greenPoint)
                        teePoint?.let { add(it) }
                    }
                if (framingPoints.size >= 2) {
                    val box = BoundingBox.fromGeoPoints(framingPoints)
                    mapView.post { mapView.zoomToBoundingBox(box, true, MAP_PADDING_PX) }
                } else {
                    mapView.controller.setCenter(greenPoint)
                }
            }
            val center = mapView.mapCenter
            onCameraCenterChanged(LatLng(center.latitude, center.longitude))
            mapView.invalidate()
        },
        onRelease = { mapView ->
            labelsOverlayHolder.value?.onDetach(mapView)
            labelsOverlayHolder.value = null
            mapView.onPause()
            mapViewHolder.value = null
        },
    )
}

private const val MAP_PADDING_PX = 80
private const val PLAYER_LINE_COLOR = 0xFF1B5E20.toInt()
private const val HOLE_LINE_COLOR = 0xCCFFFFFF.toInt()

private fun Marker.applyOpaqueIcon(
    bitmap: Bitmap,
    resources: Resources,
) {
    val drawable = BitmapDrawable(resources, bitmap)
    drawable.setBounds(0, 0, bitmap.width, bitmap.height)
    icon = drawable
}
