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
    private const val KEY_OFFLINE_MAP_ENABLED = "offline_map_enabled"
    private const val KEY_MAP_STYLE = "map_style"

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

    fun isOfflineMapEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OFFLINE_MAP_ENABLED, false)

    fun setOfflineMapEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_OFFLINE_MAP_ENABLED, enabled).apply()
    }

    fun getMapStyle(context: Context): String =
        prefs(context).getString(KEY_MAP_STYLE, MAP_STYLE_STANDARD) ?: MAP_STYLE_STANDARD

    fun setMapStyle(context: Context, style: String) {
        prefs(context).edit().putString(KEY_MAP_STYLE, style).apply()
    }
}
