package com.aot.taskmap.data.local

import android.content.Context

object SettingsPreferences {
    const val MAP_STYLE_STANDARD = "standard"
    const val MAP_STYLE_TERRAIN = "terrain"

    private const val PREF_NAME = "settings_prefs"
    private const val KEY_FOLLOW_LOCATION = "follow_location"
    private const val KEY_SHOW_RADIUS = "show_radius"
    private const val KEY_CONFIRM_COMPLETE = "confirm_complete"
    private const val KEY_SEARCH_AUTO_EXPAND = "search_auto_expand"
    private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    private const val KEY_MAP_STYLE = "map_style"
    private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
    private const val KEY_SHOW_COMPLETED_MARKERS = "show_completed_markers"
    private const val KEY_NOTIFICATION_SOUND_ENABLED = "notification_sound_enabled"
    private const val KEY_NOTIFICATION_SOUND_URI = "notification_sound_uri"
    private const val KEY_AUTO_UPDATE_CHECK_ENABLED = "auto_update_check_enabled"
    private const val KEY_HIGHLIGHT_IMPORTANT_PLACES = "highlight_important_places"
    private const val KEY_LAST_MAP_STATE_SAVED = "last_map_state_saved"
    private const val KEY_LAST_MAP_LAT_BITS = "last_map_lat_bits"
    private const val KEY_LAST_MAP_LNG_BITS = "last_map_lng_bits"
    private const val KEY_LAST_MAP_ZOOM_BITS = "last_map_zoom_bits"

    data class MapViewport(
        val latitude: Double,
        val longitude: Double,
        val zoom: Double
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isFollowLocationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FOLLOW_LOCATION, true)

    fun setFollowLocationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOLLOW_LOCATION, enabled).apply()
    }

    fun isShowRadiusEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_RADIUS, true)

    fun setShowRadiusEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_RADIUS, enabled).apply()
    }

    fun isConfirmCompleteEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CONFIRM_COMPLETE, true)

    fun setConfirmCompleteEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONFIRM_COMPLETE, enabled).apply()
    }

    fun isSearchAutoExpandEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEARCH_AUTO_EXPAND, true)

    fun setSearchAutoExpandEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEARCH_AUTO_EXPAND, enabled).apply()
    }

    fun isNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getMapStyle(context: Context): String =
        prefs(context).getString(KEY_MAP_STYLE, MAP_STYLE_STANDARD) ?: MAP_STYLE_STANDARD

    fun setMapStyle(context: Context, style: String) {
        prefs(context).edit().putString(KEY_MAP_STYLE, style).apply()
    }

    fun isAnimationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ANIMATIONS_ENABLED, true)

    fun setAnimationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ANIMATIONS_ENABLED, enabled).apply()
    }

    fun isShowCompletedMarkersEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_COMPLETED_MARKERS, false)

    fun setShowCompletedMarkersEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_COMPLETED_MARKERS, enabled).apply()
    }

    fun isNotificationSoundEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NOTIFICATION_SOUND_ENABLED, true)

    fun setNotificationSoundEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_SOUND_ENABLED, enabled).apply()
    }

    fun isAutoUpdateCheckEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, true)

    fun setAutoUpdateCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE_CHECK_ENABLED, enabled).apply()
    }

    fun getNotificationSoundUri(context: Context): String? =
        prefs(context).getString(KEY_NOTIFICATION_SOUND_URI, null)

    fun setNotificationSoundUri(context: Context, uri: String?) {
        val editor = prefs(context).edit()
        if (uri.isNullOrBlank()) {
            editor.remove(KEY_NOTIFICATION_SOUND_URI)
        } else {
            editor.putString(KEY_NOTIFICATION_SOUND_URI, uri)
        }
        editor.apply()
    }

    fun isHighlightImportantPlacesEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIGHLIGHT_IMPORTANT_PLACES, true)

    fun setHighlightImportantPlacesEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_HIGHLIGHT_IMPORTANT_PLACES, enabled).apply()
    }

    fun saveLastMapViewport(context: Context, latitude: Double, longitude: Double, zoom: Double) {
        prefs(context).edit()
            .putBoolean(KEY_LAST_MAP_STATE_SAVED, true)
            .putLong(KEY_LAST_MAP_LAT_BITS, java.lang.Double.doubleToRawLongBits(latitude))
            .putLong(KEY_LAST_MAP_LNG_BITS, java.lang.Double.doubleToRawLongBits(longitude))
            .putLong(KEY_LAST_MAP_ZOOM_BITS, java.lang.Double.doubleToRawLongBits(zoom))
            .apply()
    }

    fun getLastMapViewport(context: Context): MapViewport? {
        val settings = prefs(context)
        if (!settings.getBoolean(KEY_LAST_MAP_STATE_SAVED, false)) return null

        val lat = java.lang.Double.longBitsToDouble(settings.getLong(KEY_LAST_MAP_LAT_BITS, 0L))
        val lng = java.lang.Double.longBitsToDouble(settings.getLong(KEY_LAST_MAP_LNG_BITS, 0L))
        val zoom = java.lang.Double.longBitsToDouble(settings.getLong(KEY_LAST_MAP_ZOOM_BITS, 0L))

        if (!lat.isFinite() || !lng.isFinite() || !zoom.isFinite()) return null
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        if (zoom <= 0.0) return null

        return MapViewport(latitude = lat, longitude = lng, zoom = zoom)
    }
}
