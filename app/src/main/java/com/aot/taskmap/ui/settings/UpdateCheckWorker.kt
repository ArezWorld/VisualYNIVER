package com.aot.taskmap.ui.settings

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aot.taskmap.BuildConfig
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.ui.MainActivity

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SettingsPreferences.isAutoUpdateCheckEnabled(applicationContext)) {
            return Result.success()
        }

        val release = runCatching { GitHubUpdateChecker.fetchLatestRelease() }
            .getOrElse { return Result.retry() }

        val remoteVersion = release.versionTag.ifBlank { release.releaseName ?: "" }.trim()
        if (remoteVersion.isBlank()) return Result.success()

        if (!UpdateVersionComparator.isRemoteVersionNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
            return Result.success()
        }

        val lastNotifiedVersion = SettingsPreferences.getLastNotifiedUpdateVersion(applicationContext)
        if (lastNotifiedVersion == remoteVersion) {
            return Result.success()
        }

        if (!hasNotificationPermission()) {
            return Result.success()
        }

        showUpdateNotification(remoteVersion)
        SettingsPreferences.setLastNotifiedUpdateVersion(applicationContext, remoteVersion)
        return Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun showUpdateNotification(remoteVersion: String) {
        createNotificationChannelIfNeeded()
        if (!hasNotificationPermission()) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TRIGGER_UPDATE_CHECK, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, UPDATE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.update_available_notification_title))
            .setContentText(
                applicationContext.getString(
                    R.string.update_available_notification_text,
                    remoteVersion
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    applicationContext.getString(
                        R.string.update_available_notification_text,
                        remoteVersion
                    )
                )
            )
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(UPDATE_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
            ?: return
        if (manager.getNotificationChannel(UPDATE_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            UPDATE_CHANNEL_ID,
            applicationContext.getString(R.string.update_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(R.string.update_notification_channel_desc)
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val UPDATE_CHANNEL_ID = "aot_update_notifications"
        private const val UPDATE_NOTIFICATION_ID = 44002
    }
}
