package com.aot.taskmap.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.aot.taskmap.ui.StartupActivity

class AppUpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        Thread {
            try {
                // На новых версиях не очищаем локальные данные после установки обновления.
                InAppUpdateManager.consumeDataClearRequiredAfterInstall(context)

                Handler(Looper.getMainLooper()).post {
                    val launchIntent = Intent(context, StartupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(launchIntent)
                }
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
