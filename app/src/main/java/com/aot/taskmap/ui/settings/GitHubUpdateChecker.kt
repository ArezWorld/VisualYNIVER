package com.aot.taskmap.ui.settings

import com.aot.taskmap.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class GitHubReleaseInfo(
    val versionTag: String,
    val releaseName: String?,
    val apkUrl: String?
)

object GitHubUpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun fetchLatestRelease(): GitHubReleaseInfo {
        val request = Request.Builder()
            .url(BuildConfig.UPDATE_RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "${BuildConfig.UPDATE_REPO_NAME}-updater")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API error: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) throw IOException("Empty GitHub response")

            val json = JSONObject(body)
            val versionTag = json.optString("tag_name")
            val releaseName = json.optString("name").takeIf { it.isNotBlank() }
            val releasePage = json.optString("html_url").takeIf { it.isNotBlank() }

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val asset = assets.optJSONObject(index) ?: continue
                    val fileName = asset.optString("name")
                    val contentType = asset.optString("content_type")
                    if (
                        fileName.endsWith(".apk", ignoreCase = true) ||
                        contentType.equals("application/vnd.android.package-archive", ignoreCase = true)
                    ) {
                        apkUrl = asset.optString("browser_download_url")
                        if (!apkUrl.isNullOrBlank()) break
                    }
                }
            }

            return GitHubReleaseInfo(
                versionTag = versionTag,
                releaseName = releaseName,
                apkUrl = apkUrl ?: releasePage
            )
        }
    }
}
