package com.aot.taskmap.ui.settings

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.aot.taskmap.R
import java.io.File

object InAppUpdateManager {

    private const val PREFS_NAME = "in_app_update_prefs"
    private const val KEY_PENDING_DOWNLOAD_ID = "pending_download_id"
    private const val UPDATE_FILE_NAME = "AOT-update.apk"

    fun startBackgroundDownload(context: Context, apkUrl: String): Result<Long> {
        return runCatching {
            val appContext = context.applicationContext
            val downloadManager = appContext.getSystemService(DownloadManager::class.java)
                ?: throw IllegalStateException("DownloadManager unavailable")

            val pendingId = getPendingDownloadId(appContext)
            if (pendingId != -1L) {
                downloadManager.remove(pendingId)
                clearPendingDownloadId(appContext)
            }

            val targetDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("External files dir unavailable")
            val targetFile = File(targetDir, UPDATE_FILE_NAME)
            if (targetFile.exists()) {
                targetFile.delete()
            }

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
                    UPDATE_FILE_NAME
                )

            val downloadId = downloadManager.enqueue(request)
            savePendingDownloadId(appContext, downloadId)
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
                return DownloadCompleteResult.Failed
            }

        val query = DownloadManager.Query().setFilterById(downloadId)
        downloadManager.query(query).use { cursor ->
            if (cursor == null || !cursor.moveToFirst()) {
                clearPendingDownloadId(appContext)
                return DownloadCompleteResult.Failed
            }

            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex == -1) {
                clearPendingDownloadId(appContext)
                return DownloadCompleteResult.Failed
            }
            val status = cursor.getInt(statusIndex)
            if (status != DownloadManager.STATUS_SUCCESSFUL) {
                clearPendingDownloadId(appContext)
                return DownloadCompleteResult.Failed
            }
        }

        val apkUri = downloadManager.getUriForDownloadedFile(downloadId)
        clearPendingDownloadId(appContext)

        if (apkUri == null) {
            return DownloadCompleteResult.Failed
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !appContext.packageManager.canRequestPackageInstalls()
        ) {
            return DownloadCompleteResult.RequiresInstallPermission
        }

        return if (installDownloadedApk(appContext, apkUri)) {
            DownloadCompleteResult.InstallStarted
        } else {
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

    private fun installDownloadedApk(context: Context, apkUri: Uri): Boolean {
        val appContext = context.applicationContext
        return runCatching {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            appContext.startActivity(installIntent)
        }.isSuccess
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
}

enum class DownloadCompleteResult {
    InstallStarted,
    RequiresInstallPermission,
    Failed,
    Ignored
}
