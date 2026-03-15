package com.aot.taskmap.ui.tasks

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.TaskDatabase
import com.aot.taskmap.data.repository.TaskRepository
import com.aot.taskmap.domain.model.Task
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

data class TaskImportResult(
    val senderName: String,
    val senderId: String,
    val addedCount: Int
)

object TaskShareManager {
    private const val LINK_PREFIX = "aot://tasks/import?data="
    private const val PAYLOAD_VERSION = 1
    private val importLinkRegex = Regex("aot://tasks/import\\?data=([A-Za-z0-9_-]+)")

    fun extractShareTextFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }?.trim()?.takeIf { it.isNotBlank() }
    }

    fun buildShareLink(context: Context, tasks: List<Task>): String {
        val senderName = SettingsPreferences.getEffectiveProfileName(context)
        val senderId = SettingsPreferences.getOrCreateSenderId(context)

        val json = JSONObject().apply {
            put("version", PAYLOAD_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put(
                "sender",
                JSONObject().apply {
                    put("name", senderName)
                    put("id", senderId)
                }
            )
            put("tasks", JSONArray().apply {
                tasks.forEach { task ->
                    put(
                        JSONObject().apply {
                            put("title", task.title)
                            put("description", task.description)
                            put("latitude", task.latitude)
                            put("longitude", task.longitude)
                            put("address", task.address)
                            put("radius", task.radius)
                            put("markerColor", task.markerColor)
                            put("markerIcon", task.markerIcon)
                            put("category", task.category)
                            put("autoRemoveAfterTrigger", task.autoRemoveAfterTrigger)
                            put("isNotificationEnabled", task.isNotificationEnabled)
                            put("isCompleted", task.isCompleted)
                        }
                    )
                }
            })
        }.toString()

        val encodedPayload = encodePayload(json)
        return "$LINK_PREFIX$encodedPayload"
    }

    fun buildShareMessage(context: Context, tasks: List<Task>): String {
        val link = buildShareLink(context, tasks)
        return context.getString(R.string.tasks_export_message_template, tasks.size, link)
    }

    suspend fun importTasksFromShareText(context: Context, rawText: String): Result<TaskImportResult> {
        return runCatching {
            val dataToken = extractDataToken(rawText)
                ?: throw IllegalArgumentException(context.getString(R.string.tasks_import_error_invalid_link))
            val payloadJson = decodePayload(dataToken)
            parseAndImportTasks(context, payloadJson)
        }
    }

    private suspend fun parseAndImportTasks(context: Context, payloadJson: String): TaskImportResult {
        val root = JSONObject(payloadJson)
        val payloadVersion = root.optInt("version", -1)
        if (payloadVersion != PAYLOAD_VERSION) {
            throw IllegalArgumentException(context.getString(R.string.tasks_import_error_unsupported_version))
        }

        val sender = root.optJSONObject("sender") ?: JSONObject()
        val senderName = sender.optString("name").trim()
            .ifBlank { context.getString(R.string.tasks_import_unknown_sender) }
        val senderId = sender.optString("id").trim().ifBlank { "unknown" }

        val tasksArray = root.optJSONArray("tasks") ?: JSONArray()
        if (tasksArray.length() == 0) {
            throw IllegalArgumentException(context.getString(R.string.tasks_import_error_empty))
        }

        val importedTasks = mutableListOf<Task>()
        for (i in 0 until tasksArray.length()) {
            val item = tasksArray.optJSONObject(i) ?: continue
            val title = item.optString("title").trim().ifBlank {
                context.getString(R.string.tasks_import_default_title)
            }
            val description = item.optString("description").trim()
            val lat = item.optDouble("latitude", Double.NaN)
            val lon = item.optDouble("longitude", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue

            val importedDescription = buildImportedDescription(context, senderName, description)
            importedTasks += Task(
                title = title,
                description = importedDescription,
                latitude = lat,
                longitude = lon,
                address = item.optString("address", ""),
                radius = item.optInt("radius", 100).coerceIn(5, 250),
                markerColor = item.optInt("markerColor", 0xFF2196F3.toInt()),
                markerIcon = item.optString("markerIcon", "pin"),
                category = item.optString("category", "general"),
                autoRemoveAfterTrigger = item.optBoolean("autoRemoveAfterTrigger", false),
                isNotificationEnabled = item.optBoolean("isNotificationEnabled", true),
                isCompleted = item.optBoolean("isCompleted", false),
                completedAt = if (item.optBoolean("isCompleted", false)) {
                    System.currentTimeMillis()
                } else {
                    null
                }
            )
        }

        if (importedTasks.isEmpty()) {
            throw IllegalArgumentException(context.getString(R.string.tasks_import_error_empty))
        }

        val repository = TaskRepository(TaskDatabase.getDatabase(context).taskDao())
        repository.insertTasks(importedTasks)
        return TaskImportResult(
            senderName = senderName,
            senderId = senderId,
            addedCount = importedTasks.size
        )
    }

    private fun buildImportedDescription(context: Context, senderName: String, original: String): String {
        val senderLine = context.getString(R.string.tasks_import_sender_line, senderName)
        if (original.startsWith(senderLine, ignoreCase = true)) return original
        return if (original.isBlank()) senderLine else "$senderLine\n$original"
    }

    private fun extractDataToken(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.startsWith(LINK_PREFIX)) {
            return Uri.parse(trimmed).getQueryParameter("data")
        }

        val regexMatch = importLinkRegex.find(trimmed)
        if (regexMatch != null) {
            return regexMatch.groupValues.getOrNull(1)
        }

        return if (trimmed.matches(Regex("^[A-Za-z0-9_-]+$"))) {
            trimmed
        } else {
            null
        }
    }

    private fun encodePayload(json: String): String {
        val compressed = gzip(json.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(
            compressed,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private fun decodePayload(dataToken: String): String {
        val compressed = decodeBase64UrlSafe(dataToken)
        return runCatching {
            val stream = GZIPInputStream(ByteArrayInputStream(compressed))
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse {
            String(compressed, Charsets.UTF_8)
        }
    }

    private fun decodeBase64UrlSafe(raw: String): ByteArray {
        val normalized = raw.trim().let { token ->
            val padding = (4 - token.length % 4) % 4
            token + "=".repeat(padding)
        }
        return Base64.decode(normalized, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    private fun gzip(input: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { gzip ->
            gzip.write(input)
        }
        return output.toByteArray()
    }
}

