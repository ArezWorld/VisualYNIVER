package com.aot.taskmap.ui.map

import com.aot.taskmap.data.local.SettingsPreferences
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

object MapTileSources {
    // Бесплатный российский слой 2GIS.
    val russian2Gis: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "TwoGisMap",
        0,
        19,
        256,
        ".png",
        arrayOf("https://tile2.maps.2gis.com/tiles")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl?x=$x&y=$y&z=$zoom&v=1"
        }
    }

    // Надёжный альтернативный слой без смещения геопозиции.
    val openStreetMap: ITileSource = TileSourceFactory.MAPNIK

    fun resolveByStyle(style: String): ITileSource {
        return when (style) {
            SettingsPreferences.MAP_STYLE_TERRAIN -> openStreetMap
            else -> russian2Gis
        }
    }
}
