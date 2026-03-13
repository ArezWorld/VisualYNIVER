package com.aot.taskmap.ui.settings

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aot.taskmap.data.local.SettingsPreferences
import java.util.concurrent.TimeUnit

object UpdateCheckScheduler {

    private const val PERIODIC_WORK_NAME = "aot_update_check_periodic"
    private const val IMMEDIATE_WORK_NAME = "aot_update_check_immediate"

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        if (!SettingsPreferences.isAutoUpdateCheckEnabled(appContext)) {
            cancel(appContext)
            return
        }
        enqueuePeriodic(appContext)
        enqueueImmediate(appContext)
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        WorkManager.getInstance(appContext).cancelUniqueWork(IMMEDIATE_WORK_NAME)
        WorkManager.getInstance(appContext).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    private fun enqueuePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun enqueueImmediate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
