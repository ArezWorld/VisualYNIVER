package com.aot.taskmap.ui.auth

import android.content.Context
import android.os.Build
import com.aot.taskmap.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object AuthErrorFormatter {

    fun format(context: Context, error: Throwable, fallbackMessage: String, baseUrl: String): String {
        val message = error.message.orEmpty()
        if (message.contains("10.0.2.2") && !isProbablyEmulator()) {
            return context.getString(R.string.error_server_emulator_host, baseUrl)
        }

        return when (error) {
            is ConnectException,
            is SocketTimeoutException,
            is UnknownHostException -> context.getString(R.string.error_server_unreachable)
            else -> message.ifBlank { fallbackMessage }
        }
    }

    private fun isProbablyEmulator(): Boolean {
        return Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("emulator", ignoreCase = true) ||
            Build.MODEL.contains("Emulator", ignoreCase = true) ||
            Build.MODEL.contains("Android SDK built for", ignoreCase = true) ||
            Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
    }
}
