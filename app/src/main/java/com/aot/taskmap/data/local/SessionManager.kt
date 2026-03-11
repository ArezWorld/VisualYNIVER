package com.aot.taskmap.data.local

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    
    companion object {
        private const val PREF_NAME = "AOT_Preferences"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    var authToken: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()
    
    var userId: Long
        get() = prefs.getLong(KEY_USER_ID, -1)
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()
    
    var username: String?
        get() = prefs.getString(KEY_USERNAME, null)
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()
    
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()
    
    fun saveSession(token: String, userId: Long, username: String) {
        authToken = token
        this.userId = userId
        this.username = username
        isLoggedIn = true
    }
    
    fun clearSession() {
        prefs.edit().clear().apply()
    }
    
    fun isSessionLoggedIn(): Boolean = isLoggedIn && authToken != null
}
