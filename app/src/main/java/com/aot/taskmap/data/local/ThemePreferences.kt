package com.aot.taskmap.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.aot.taskmap.R

object ThemePreferences {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_DARK_MODE = "dark_mode"

    const val THEME_WHITE = "white"
    const val THEME_PURPLE = "purple"
    const val THEME_BLACK = "black"
    const val THEME_BLUE = "blue"
    const val THEME_STEEL = "steel"
    const val THEME_RED_BLACK = "red_black"

    fun getTheme(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_APP_THEME, THEME_PURPLE) ?: THEME_PURPLE
    }

    fun setTheme(context: Context, themeKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_APP_THEME, themeKey).apply()
    }

    fun isDarkMode(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun setDarkMode(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
        applyTheme(enabled)
    }

    fun getThemeRes(context: Context): Int {
        return when (getTheme(context)) {
            THEME_WHITE -> R.style.Theme_TaskMap_White
            THEME_BLACK -> R.style.Theme_TaskMap_Black
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
}
