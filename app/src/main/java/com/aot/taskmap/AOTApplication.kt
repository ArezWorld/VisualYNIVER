package com.aot.taskmap

import android.app.Application
import com.aot.taskmap.data.local.ThemePreferences
import org.osmdroid.config.Configuration

class AOTApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        ThemePreferences.applyTheme(this)
        
        // Configure OSMDroid
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir
            tileFileSystemCacheMaxBytes = 300L * 1024L * 1024L
            tileFileSystemCacheTrimBytes = 220L * 1024L * 1024L
        }
    }
}
