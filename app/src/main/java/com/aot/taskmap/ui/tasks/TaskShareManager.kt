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
    private const val IMPORT_HTTPS_HOST = "visualyniver.onrender.com"
    private const val HTTPS_LINK_PREFIX = "https://$IMPORT_HTTPS_HOST/i/"

    private const val PAYLOAD_VERSION_LEGACY = 1
    private const val PAYLOAD_VERSION_COMPACT = 2

    private const val DEFAULT_RADIUS_METERS = 100
    private const val DEFAULT_MARKER_COLOR = 0xFF2196F3.toInt()
    private const val DEFAULT_MARKER_ICON = "pin"
    private const val DEFAULT_CATEGORY = "general"

    private const val FLAG_AUTO_REMOVE = 1
    private const val FLAG_NOTIFICATIONS_ENABLED = 1 shl 1
    private const val FLAG_COMPLETED = 1 shl 2

    private val importLinkRegex = Regex(
        "(?:(?:aot://tasks/(?:i/|import\\?(?:data|d)=))|(?:https://visualyniver\\.onrender\\.com/(?:i/|tasks/import\\?(?:data|d)=)))([A-Za-z0-9_-]+)"
    )

    private data class SenderInfo(
        val name: String,
        val id: String
    )

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
        val payloadJson = buildCompactPayloadJson(senderName, senderId, tasks)
        val encodedPayload = encodePayload(payloadJson)
        return "$HTTPS_LINK_PREFIX$encodedPayload"
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

    private fun buildCompactPayloadJson(senderName: String, senderId: String, tasks: List<Task>): String {
        return JSONObject().apply {
            put("v", PAYLOAD_VERSION_COMPACT)
            put("e", System.currentTimeMillis())
            put(
                "s",
                JSONObject().apply {
                    put("n", senderName)
                    put("i", senderId)
                }
            )
            put("t", JSONArray().apply {
                tasks.forEach { task ->
                    val flags =
                        (if (task.autoRemoveAfterTrigger) FLAG_AUTO_REMOVE else 0) or
                            (if (task.isNotificationEnabled) FLAG_NOTIFICATIONS_ENABLED else 0) or
                            (if (task.isCompleted) FLAG_COMPLETED else 0)
                    put(
                        JSONArray().apply {
                            put(task.title)
                            put(task.description)
                            put(task.latitude)
                            put(task.longitude)
                            put(task.address)
                            put(task.radius.coerceIn(5, 250))
                            put(task.markerColor)
                            put(task.markerIcon)
                            put(task.category)
                            put(flags)
                        }
                    )
                }
            })
        }.toString()
    }

    private suspend fun parseAndImportTasks(context: Context, payloadJson: String): TaskImportResult {
        val root = JSONObject(payloadJson)
        val payloadVersion = root.optInt("v", root.optInt("version", -1))
        val sender = when (payloadVersion) {
            PAYLOAD_VERSION_COMPACT -> extractSenderInfoCompact(context, root)
            PAYLOAD_VERSION_LEGACY -> extractSenderInfoLegacy(context, root)
            else -> throw IllegalArgumentException(context.getString(R.string.tasks_import_error_unsupported_version))
        }

        val importedTasks = when (payloadVersion) {
            PAYLOAD_VERSION_COMPACT -> parseCompactTasks(context, root, sender.name)
            PAYLOAD_VERSION_LEGACY -> parseLegacyTasks(context, root, sender.name)
            else -> emptyList()
        }

        if (importedTasks.isEmpty()) {
            throw IllegalArgumentException(context.getString(R.string.tasks_import_error_empty))
        }

        val repository = TaskRepository(TaskDatabase.getDatabase(context).taskDao())
        repository.insertTasks(importedTasks)
        return TaskImportResult(
            senderName = sender.name,
            senderId = sender.id,
            addedCount = importedTasks.size
        )
    }

    private fun extractSenderInfoCompact(context: Context, root: JSONObject): SenderInfo {
        val sender = root.optJSONObject("s") ?: JSONObject()
        val name = sender.optString("n").trim()
            .ifBlank { context.getString(R.string.tasks_import_unknown_sender) }
        val id = sender.optString("i").trim().ifBlank { "unknown" }
        return SenderInfo(name, id)
    }

    private fun extractSenderInfoLegacy(context: Context, root: JSONObject): SenderInfo {
        val sender = root.optJSONObject("sender") ?: JSONObject()
        val name = sender.optString("name").trim()
            .ifBlank { context.getString(R.string.tasks_import_unknown_sender) }
        val id = sender.optString("id").trim().ifBlank { "unknown" }
        return SenderInfo(name, id)
    }

    private fun parseCompactTasks(
        context: Context,
        root: JSONObject,
        senderName: String
    ): List<Task> {
        val tasksArray = root.optJSONArray("t") ?: JSONArray()
        if (tasksArray.length() == 0) return emptyList()

        val importedTasks = mutableListOf<Task>()
        for (index in 0 until tasksArray.length()) {
            val item = tasksArray.optJSONArray(index) ?: continue
            val title = item.optString(0).trim().ifBlank {
                context.getString(R.string.tasks_import_default_title)
            }
            val description = item.optString(1).trim()
            val lat = item.optDouble(2, Double.NaN)
            val lon = item.optDouble(3, Double.NaN)
            if (!lat.isFinite() || !lon.isFinite()) continue

            val flags = if (item.length() > 9 && !item.isNull(9)) {
                item.optInt(9, FLAG_NOTIFICATIONS_ENABLED)
            } else {
                FLAG_NOTIFICATIONS_ENABLED
            }

            val importedDescription = buildImportedDescription(context, senderName, description)
            importedTasks += Task(
                title = title,
                description = importedDescription,
                latitude = lat,
                longitude = lon,
                address = item.optString(4, ""),
                radius = item.optInt(5, DEFAULT_RADIUS_METERS).coerceIn(5, 250),
                markerColor = item.optInt(6, DEFAULT_MARKER_COLOR),
                markerIcon = item.optString(7, DEFAULT_MARKER_ICON),
                category = item.optString(8, DEFAULT_CATEGORY),
                autoRemoveAfterTrigger = flags and FLAG_AUTO_REMOVE != 0,
                isNotificationEnabled = flags and FLAG_NOTIFICATIONS_ENABLED != 0,
                isCompleted = flags and FLAG_COMPLETED != 0,
                completedAt = if (flags and FLAG_COMPLETED != 0) {
                    System.currentTimeMillis()
                } else {
                    null
                }
            )
        }
        return importedTasks
    }

    private fun parseLegacyTasks(
        context: Context,
        root: JSONObject,
        senderName: String
    ): List<Task> {
        val tasksArray = root.optJSONArray("tasks") ?: JSONArray()
        if (tasksArray.length() == 0) return emptyList()

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
            val isCompleted = item.optBoolean("isCompleted", false)
            importedTasks += Task(
                title = title,
                description = importedDescription,
                latitude = lat,
                longitude = lon,
                address = item.optString("address", ""),
                radius = item.optInt("radius", DEFAULT_RADIUS_METERS).coerceIn(5, 250),
                markerColor = item.optInt("markerColor", DEFAULT_MARKER_COLOR),
                markerIcon = item.optString("markerIcon", DEFAULT_MARKER_ICON),
                category = item.optString("category", DEFAULT_CATEGORY),
                autoRemoveAfterTrigger = item.optBoolean("autoRemoveAfterTrigger", false),
                isNotificationEnabled = item.optBoolean("isNotificationEnabled", true),
                isCompleted = isCompleted,
                completedAt = if (isCompleted) {
                    System.currentTimeMillis()
                } else {
                    null
                }
            )
        }
        return importedTasks
    }

    private fun buildImportedDescription(context: Context, senderName: String, original: String): String {
        val senderLine = context.getString(R.string.tasks_import_sender_line, senderName)
        if (original.startsWith(senderLine, ignoreCase = true)) return original
        return if (original.isBlank()) senderLine else "$senderLine\n$original"
    }

    private fun extractDataToken(rawText: String): String? {
        val trimmed = rawText.trim()

        extractDataTokenFromUri(trimmed)?.let { return it }

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

    private fun extractDataTokenFromUri(raw: String): String? {
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        return when (scheme) {
            "aot" -> extractFromCustomUri(uri)
            "https" -> extractFromHttpsUri(uri)
            else -> null
        }
    }

    private fun extractFromCustomUri(uri: Uri): String? {
        if (!uri.host.equals("tasks", ignoreCase = true)) return null
        val pathSegments = uri.pathSegments.orEmpty()
        if (pathSegments.isEmpty()) {
            return uri.getQueryParameter("d") ?: uri.getQueryParameter("data")
        }
        return when (pathSegments.firstOrNull()) {
            "i" -> pathSegments.getOrNull(1)
            "import" -> uri.getQueryParameter("d") ?: uri.getQueryParameter("data")
            else -> null
        }
    }

    private fun extractFromHttpsUri(uri: Uri): String? {
        if (!uri.host.equals(IMPORT_HTTPS_HOST, ignoreCase = true)) return null
        val pathSegments = uri.pathSegments.orEmpty()
        if (pathSegments.isEmpty()) return null
        return when (pathSegments.firstOrNull()) {
            "i" -> pathSegments.getOrNull(1)
            "tasks" -> {
                if (pathSegments.getOrNull(1) == "import") {
                    uri.getQueryParameter("d") ?: uri.getQueryParameter("data")
                } else {
                    null
                }
            }
            else -> null
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
