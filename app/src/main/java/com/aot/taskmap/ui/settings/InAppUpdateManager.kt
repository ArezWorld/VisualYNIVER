package com.aot.taskmap.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aot.taskmap.R
import java.io.File

object InAppUpdateManager {

    private const val PREFS_NAME = "in_app_update_prefs"
    private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
    private const val KEY_PENDING_APK_URI = "pending_apk_uri"
    private const val KEY_PENDING_APK_PATH = "pending_apk_path"
    private const val KEY_CLEAR_DATA_AFTER_INSTALL = "clear_data_after_install"
    private const val KEY_UNKNOWN_SOURCES_PROMPT_SHOWN = "unknown_sources_prompt_shown"
    private const val DEFAULT_UPDATE_FILE_NAME = "AOT-update.apk"

    fun startBackgroundDownload(context: Context, apkUrl: String): Result<Long> {
        return runCatching {
            val appContext = context.applicationContext
            val downloadManager = appContext.getSystemService(DownloadManager::class.java)
                ?: throw IllegalStateException("DownloadManager unavailable")

            val pendingId = getPendingDownloadId(appContext)
            if (pendingId != -1L) {
                downloadManager.remove(pendingId)
                clearPendingDownloadId(appContext)
                clearDataClearRequiredAfterInstall(appContext)
            }
            clearPendingApkUri(appContext)
            clearPendingApkPath(appContext)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                appContext.packageManager.canRequestPackageInstalls()
            ) {
                clearUnknownSourcesPromptShown(appContext)
            }

            val targetDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("External files dir unavailable")
            val targetFileName = resolveTargetApkName(apkUrl)
            val targetFile = File(targetDir, targetFileName)
            if (targetFile.exists()) {
                targetFile.delete()
            }
            savePendingApkPath(appContext, targetFile.absolutePath)

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(appContext.getString(R.string.update_download_title))
                .setDescription(appContext.getString(R.string.update_download_description))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    appContext,
                    Environment.DIRECTORY_DOWNLOADS,
                    targetFileName
                )

            val downloadId = downloadManager.enqueue(request)
            savePendingDownloadId(appContext, downloadId)
            // После обновления сохраняем пользовательские данные (задачи/метки/профиль).
            clearDataClearRequiredAfterInstall(appContext)
            downloadId
        }
    }

    fun handleDownloadComplete(context: Context, downloadId: Long): DownloadCompleteResult {
        val appContext = context.applicationContext
        val expectedId = getPendingDownloadId(appContext)
        if (expectedId == -1L || expectedId != downloadId) {
            return DownloadCompleteResult.Ignored
        }

        val downloadManager = appContext.getSystemService(DownloadManager::class.java)
            ?: run {
                clearPendingDownloadId(appContext)
                clearDataClearRequiredAfterInstall(appContext)
                return DownloadCompleteResult.Failed
            }

        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                clearPendingDownloadId(appContext)
                clearDataClearRequiredAfterInstall(appContext)
                return DownloadCompleteResult.Failed
            }

            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex == -1) {
                clearPendingDownloadId(appContext)
                clearDataClearRequiredAfterInstall(appContext)
                return DownloadCompleteResult.Failed
            }
            val status = cursor.getInt(statusIndex)
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                clearPendingDownloadId(appContext)
                clearDataClearRequiredAfterInstall(appContext)
                return DownloadCompleteResult.Failed
            }
        }

        val apkUri = resolveDownloadedApkUri(appContext, downloadManager, downloadId)
        clearPendingDownloadId(appContext)

        if (apkUri == null) {
            clearPendingApkPath(appContext)
            clearDataClearRequiredAfterInstall(appContext)
            return DownloadCompleteResult.Failed
        }
        savePendingApkUri(appContext, apkUri.toString())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            return DownloadCompleteResult.RequiresInstallPermission
        }

        return if (installDownloadedApk(appContext, apkUri)) {
            clearPendingApkUri(appContext)
            clearPendingApkPath(appContext)
            DownloadCompleteResult.InstallStarted
        } else {
            clearDataClearRequiredAfterInstall(appContext)
            DownloadCompleteResult.Failed
        }
    }

    fun resumePendingInstallIfPossible(context: Context): DownloadCompleteResult {
        val appContext = context.applicationContext
        val pendingUri = getPendingApkUri(appContext) ?: return DownloadCompleteResult.Ignored

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            return DownloadCompleteResult.RequiresInstallPermission
        }

        return if (installDownloadedApk(appContext, Uri.parse(pendingUri))) {
            clearPendingApkUri(appContext)
            clearPendingApkPath(appContext)
            DownloadCompleteResult.InstallStarted
        } else {
            clearDataClearRequiredAfterInstall(appContext)
            DownloadCompleteResult.Failed
        }
    }

    fun openUnknownSourcesSettings(context: Context) {
        val appContext = context.applicationContext
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${appContext.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    }

    fun shouldPromptUnknownSourcesSettings(context: Context): Boolean {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (appContext.packageManager.canRequestPackageInstalls()) {
            clearUnknownSourcesPromptShown(appContext)
            return false
        }

        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasShown = prefs.getBoolean(KEY_UNKNOWN_SOURCES_PROMPT_SHOWN, false)
        if (!wasShown) {
            prefs.edit().putBoolean(KEY_UNKNOWN_SOURCES_PROMPT_SHOWN, true).apply()
            return true
        }
        return false
    }

    fun consumeDataClearRequiredAfterInstall(context: Context): Boolean {
        val settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val required = settings.getBoolean(KEY_CLEAR_DATA_AFTER_INSTALL, false)
        if (required) {
            settings.edit().remove(KEY_CLEAR_DATA_AFTER_INSTALL).apply()
        }
        return required
    }

    private fun installDownloadedApk(context: Context, apkUri: Uri): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = apkUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }
            appContext.startActivity(installIntent)
        }.isSuccess
    }

    private fun resolveDownloadedApkUri(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long
    ): Uri? {
        downloadManager.getUriForDownloadedFile(downloadId)?.let { return it }

        val localUri = queryDownloadedFileUri(context, downloadManager, downloadId)
        if (localUri != null) {
            return localUri
        }

        val filePath = getPendingApkPath(context) ?: return null
        val file = File(filePath)
        if (!file.exists() || file.length() <= 0L) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun queryDownloadedFileUri(
        context: Context,
        downloadManager: DownloadManager,
        downloadId: Long
    ): Uri? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) return null
            val localUriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (localUriIndex == -1) return null
            val localUri = cursor.getString(localUriIndex).orEmpty()
            if (localUri.isBlank()) return null

            val parsed = Uri.parse(localUri)
            if (parsed.scheme.equals("content", ignoreCase = true)) {
                return parsed
            }

            val file = when {
                parsed.scheme.equals("file", ignoreCase = true) -> File(parsed.path.orEmpty())
                else -> File(localUri)
            }
            if (!file.exists() || file.length() <= 0L) return null
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    private fun resolveTargetApkName(apkUrl: String): String {
        val rawName = Uri.parse(apkUrl)
            .lastPathSegment
            ?.substringAfterLast('/')
            ?.trim()
            .orEmpty()
        if (rawName.isBlank()) return DEFAULT_UPDATE_FILE_NAME

        val sanitized = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (sanitized.isBlank()) return DEFAULT_UPDATE_FILE_NAME
        return if (sanitized.endsWith(".apk", ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized.apk"
        }
    }

    private fun getPendingDownloadId(context: Context): Long {
        return context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_PENDING_DOWNLOAD_ID, -1L)
    }

    private fun savePendingDownloadId(context: Context, downloadId: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_PENDING_DOWNLOAD_ID, downloadId)
            .apply()
    }

    private fun clearPendingDownloadId(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_DOWNLOAD_ID)
            .apply()
    }

    private fun getPendingApkUri(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_APK_URI, null)
    }

    private fun savePendingApkUri(context: Context, apkUri: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_APK_URI, apkUri)
            .apply()
    }

    private fun clearPendingApkUri(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_APK_URI)
            .apply()
    }

    private fun getPendingApkPath(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PENDING_APK_PATH, null)
    }

    private fun savePendingApkPath(context: Context, apkPath: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_APK_PATH, apkPath)
            .apply()
    }

    private fun clearPendingApkPath(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_APK_PATH)
            .apply()
    }

    private fun markDataClearRequiredAfterInstall(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CLEAR_DATA_AFTER_INSTALL, true)
            .apply()
    }

    private fun clearDataClearRequiredAfterInstall(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CLEAR_DATA_AFTER_INSTALL)
            .apply()
    }

    private fun clearUnknownSourcesPromptShown(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_UNKNOWN_SOURCES_PROMPT_SHOWN)
            .apply()
    }
}

enum class DownloadCompleteResult {
    InstallStarted,
    RequiresInstallPermission,
    Failed,
    Ignored
}
