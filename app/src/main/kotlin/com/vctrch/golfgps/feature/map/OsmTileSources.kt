package com.vctrch.golfgps.feature.map

import com.vctrch.golfgps.domain.MapDisplayStyle
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

/**
 * Esri ArcGIS tiles use Z/Y/X (not slippy Z/X/Y). Plain [org.osmdroid.tileprovider.tilesource.XYTileSource]
 * would shuffle imagery.
 */
private fun esriZyXTileSource(
    name: String,
    baseUrl: String,
    imageEnding: String = "",
): OnlineTileSourceBase =
    object : OnlineTileSourceBase(
        name,
        0,
        19,
        256,
        imageEnding,
        arrayOf(baseUrl),
        "Esri",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return getBaseUrl() +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) +
                mImageFilenameEnding
        }
    }

private val esriWorldImagery =
    esriZyXTileSource(
        name = "EsriWorldImagery",
        baseUrl = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
        imageEnding = ".jpg",
    )

/** Place/road labels for hybrid (drawn as a transparent [org.osmdroid.views.overlay.TilesOverlay]). */
val esriReferenceLabels: OnlineTileSourceBase =
    esriZyXTileSource(
        name = "EsriReferenceLabels",
        baseUrl =
            "https://server.arcgisonline.com/ArcGIS/rest/services/Reference/" +
                "World_Boundaries_and_Places/MapServer/tile/",
    )

fun MapDisplayStyle.toOsmTileSource(): ITileSource {
    return when (this) {
        MapDisplayStyle.STANDARD -> TileSourceFactory.MAPNIK
        MapDisplayStyle.SATELLITE,
        MapDisplayStyle.HYBRID,
        -> esriWorldImagery
    }
}

fun MapDisplayStyle.osmLabelsOverlaySource(): ITileSource? {
    return when (this) {
        MapDisplayStyle.HYBRID -> esriReferenceLabels
        MapDisplayStyle.STANDARD,
        MapDisplayStyle.SATELLITE,
        -> null
    }
}

fun MapDisplayStyle.osmAttribution(): String {
    return when (this) {
        MapDisplayStyle.STANDARD -> "© OpenStreetMap"
        MapDisplayStyle.SATELLITE -> "© Esri"
        MapDisplayStyle.HYBRID -> "© Esri · OpenStreetMap"
    }
}
