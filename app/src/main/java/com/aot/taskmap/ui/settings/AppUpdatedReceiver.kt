package com.aot.taskmap.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.aot.taskmap.ui.auth.LoginActivity

class AppUpdatedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pendingResult = goAsync()
        Thread {
            try {
                if (InAppUpdateManager.consumeDataClearRequiredAfterInstall(context)) {
                    AppDataResetter.clearAllLocalData(context)
                }

                Handler(Looper.getMainLooper()).post {
                    val launchIntent = Intent(context, LoginActivity::class.java).apply {
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
