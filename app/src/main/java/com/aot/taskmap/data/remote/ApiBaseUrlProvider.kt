package com.aot.taskmap.data.remote

import android.content.Context
import com.aot.taskmap.BuildConfig
import com.aot.taskmap.data.local.SettingsPreferences
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ApiBaseUrlProvider {

    private const val DEFAULT_URL = "http://10.0.2.2:8000"

    fun resolve(context: Context): String {
        val runtimeOverride = SettingsPreferences.getApiBaseUrlOverride(context)
        if (!runtimeOverride.isNullOrBlank()) return normalize(runtimeOverride)

        val configuredUrl = BuildConfig.API_BASE_URL.trim()
        if (configuredUrl.isBlank()) return DEFAULT_URL
        return normalize(configuredUrl)
    }

    fun resolve(): String {
        val configuredUrl = BuildConfig.API_BASE_URL.trim()
        if (configuredUrl.isBlank()) return DEFAULT_URL
        return normalize(configuredUrl)
    }

    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return DEFAULT_URL

        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }

        return withScheme.toHttpUrlOrNull()
            ?.newBuilder()
            ?.build()
            ?.toString()
            ?.trimEnd('/')
            ?: withScheme.trimEnd('/')
    }
}
