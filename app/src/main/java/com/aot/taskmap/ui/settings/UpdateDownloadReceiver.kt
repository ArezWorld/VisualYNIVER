package com.aot.taskmap.ui.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.aot.taskmap.R

class UpdateDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId == -1L) return

        when (InAppUpdateManager.handleDownloadComplete(context, downloadId)) {
            DownloadCompleteResult.InstallStarted -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.update_install_started),
                    Toast.LENGTH_LONG
                ).show()
            }

            DownloadCompleteResult.RequiresInstallPermission -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.update_install_permission_required),
                    Toast.LENGTH_LONG
                ).show()
                if (InAppUpdateManager.shouldPromptUnknownSourcesSettings(context)) {
                    InAppUpdateManager.openUnknownSourcesSettings(context)
                }
            }

            DownloadCompleteResult.Failed -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.update_download_failed),
                    Toast.LENGTH_LONG
                ).show()
            }

            DownloadCompleteResult.Ignored -> Unit
        }
    }
}
