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

    // Надежный базовый глобальный слой OpenStreetMap.
    val openStreetMap: ITileSource = TileSourceFactory.MAPNIK

    // Бесплатный красивый глобальный слой CartoDB Voyager.
    val cartoVoyager: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "CartoVoyager",
        0,
        20,
        256,
        ".png",
        arrayOf(
            "https://a.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://b.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://c.basemaps.cartocdn.com/rastertiles/voyager/",
            "https://d.basemaps.cartocdn.com/rastertiles/voyager/"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$x/$y${imageFilenameEnding()}"
        }
    }

    // Бесплатный топографический слой OpenTopoMap.
    val openTopoMap: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "OpenTopoMap",
        0,
        17,
        256,
        ".png",
        arrayOf(
            "https://a.tile.opentopomap.org/",
            "https://b.tile.opentopomap.org/",
            "https://c.tile.opentopomap.org/"
        )
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val zoom = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "$baseUrl$zoom/$x/$y${imageFilenameEnding()}"
        }
    }

    fun resolveByStyle(style: String): ITileSource {
        return when (style) {
            SettingsPreferences.MAP_STYLE_TERRAIN -> openStreetMap
            SettingsPreferences.MAP_STYLE_VOYAGER -> cartoVoyager
            SettingsPreferences.MAP_STYLE_TOPO -> openTopoMap
            "openstreetmap" -> openStreetMap
            "carto_voyager" -> cartoVoyager
            "open_topo" -> openTopoMap
            else -> russian2Gis
        }
    }
}
