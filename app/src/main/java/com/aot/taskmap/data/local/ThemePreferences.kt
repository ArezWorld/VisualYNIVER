package com.aot.taskmap.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.aot.taskmap.R

object ThemePreferences {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val LEGACY_THEME_BLACK = "black"

    const val THEME_WHITE = "white"
    const val THEME_PURPLE = "purple"
    const val THEME_BLUE = "blue"
    const val THEME_STEEL = "steel"
    const val THEME_RED_BLACK = "red_black"

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val rawTheme = prefs.getString(KEY_APP_THEME, THEME_PURPLE)
        val normalizedTheme = normalizeTheme(rawTheme)
        if (rawTheme != normalizedTheme) {
            prefs.edit().putString(KEY_APP_THEME, normalizedTheme).apply()
        }
        return normalizedTheme
    }

    fun setTheme(context: Context, themeKey: String) {
        val normalizedTheme = normalizeTheme(themeKey)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_THEME, normalizedTheme).apply()
        if (requiresDarkMode(normalizedTheme) && !isDarkMode(context)) {
            setDarkMode(context, true)
        }
    }

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        val effectiveEnabled = if (!enabled && requiresDarkMode(getTheme(context))) true else enabled
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, effectiveEnabled).apply()
        applyTheme(effectiveEnabled)
    }

    fun getThemeRes(context: Context): Int {
        return when (getTheme(context)) {
            THEME_WHITE -> R.style.Theme_TaskMap_White
            THEME_BLUE -> R.style.Theme_TaskMap_Blue
            THEME_STEEL -> R.style.Theme_TaskMap_Steel
            THEME_RED_BLACK -> R.style.Theme_TaskMap_RedBlack
            else -> R.style.Theme_TaskMap_Purple
        }
    }

    fun applyTheme(context: Context) {
        applyTheme(isDarkMode(context))
    }

    private fun applyTheme(enabled: Boolean) {
        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun normalizeTheme(themeKey: String?): String {
        return when (themeKey) {
            THEME_WHITE,
            THEME_PURPLE,
            THEME_BLUE,
            THEME_STEEL,
            THEME_RED_BLACK -> themeKey
            LEGACY_THEME_BLACK -> THEME_PURPLE
            else -> THEME_PURPLE
        }
    }

    private fun requiresDarkMode(themeKey: String): Boolean {
        return themeKey == THEME_STEEL || themeKey == THEME_RED_BLACK
    }
}
