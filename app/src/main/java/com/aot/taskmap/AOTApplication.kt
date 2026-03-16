package com.aot.taskmap

import android.app.Application
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.ui.settings.UpdateCheckScheduler
import org.osmdroid.config.Configuration
import java.io.File

class AOTApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        ThemePreferences.applyTheme(this)
        
        // Configure OSMDroid
        val osmdroidBaseDir = File(filesDir, "osmdroid").apply { mkdirs() }
        val osmdroidTileDir = File(osmdroidBaseDir, "tiles").apply { mkdirs() }
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = osmdroidBaseDir
            osmdroidTileCache = osmdroidTileDir

            // Увеличенный кэш и очереди уменьшают повторную загрузку тайлов при зуме/скролле.
            cacheMapTileCount = 420.toShort()
            cacheMapTileOvershoot = 144.toShort()
            tileDownloadThreads = 3.toShort()
            tileDownloadMaxQueueSize = 140.toShort()
            tileFileSystemThreads = 8.toShort()
            tileFileSystemMaxQueueSize = 220.toShort()
            tileFileSystemCacheMaxBytes = 768L * 1024L * 1024L
            tileFileSystemCacheTrimBytes = 560L * 1024L * 1024L
            expirationExtendedDuration = 7L * 24L * 60L * 60L * 1000L
        }

        UpdateCheckScheduler.refresh(this)
    }
}
