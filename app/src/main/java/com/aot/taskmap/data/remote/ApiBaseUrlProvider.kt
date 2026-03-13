package com.aot.taskmap.data.remote

import com.aot.taskmap.BuildConfig

object ApiBaseUrlProvider {

    private const val DEFAULT_URL = "http://10.0.2.2:8000"

    fun resolve(): String {
        val configuredUrl = BuildConfig.API_BASE_URL.trim()
        if (configuredUrl.isBlank()) return DEFAULT_URL
        return configuredUrl.trimEnd('/')
    }
}
