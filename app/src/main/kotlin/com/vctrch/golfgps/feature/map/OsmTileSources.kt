package com.vctrch.golfgps.feature.map

import com.vctrch.golfgps.domain.MapDisplayStyle
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource

private val esriWorldImagery =
    XYTileSource(
        "EsriWorldImagery",
        0,
        19,
        256,
        ".jpg",
        arrayOf(
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
        ),
    )

fun MapDisplayStyle.toOsmTileSource(): ITileSource {
    return when (this) {
        MapDisplayStyle.STANDARD -> TileSourceFactory.MAPNIK
        MapDisplayStyle.SATELLITE -> esriWorldImagery
        MapDisplayStyle.HYBRID -> TileSourceFactory.MAPNIK
    }
}
