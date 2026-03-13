package com.aot.taskmap.data.remote

import com.aot.taskmap.data.model.LoginResponse
import com.aot.taskmap.data.model.TaskDto
import com.aot.taskmap.data.model.UserDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiClient(private val baseUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var token: String? = null

    fun setToken(newToken: String?) {
        token = newToken
    }

    fun getToken(): String? = token

    private fun createJsonBody(data: Map<String, Any>): RequestBody {
        val json = JSONObject(data).toString()
        return json.toRequestBody("application/json".toMediaType())
    }

    private fun parseErrorMessage(body: String?, fallback: String): String {
        if (body.isNullOrBlank()) return fallback
        return try {
            val json = JSONObject(body)
            json.optString("detail", fallback)
        } catch (e: Exception) {
            body
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<UserDto> =
        withContext(Dispatchers.IO) {
            try {
                val body = createJsonBody(
                    mapOf(
                        "username" to username,
                        "email" to email,
                        "password" to password
                    )
                )
                val request = Request.Builder()
                    .url("$baseUrl/register")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    Result.success(
                        UserDto(
                            id = json.getInt("id"),
                            username = json.getString("username"),
                            email = json.getString("email"),
                            isActive = json.getBoolean("is_active")
                        )
                    )
                } else {
                    val error = parseErrorMessage(response.body?.string(), "Registration error")
                    Result.failure(Exception(error))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun login(username: String, password: String): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = createJsonBody(
                    mapOf(
                        "username" to username,
                        "password" to password
                    )
                )
                val request = Request.Builder()
                    .url("$baseUrl/token")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val newToken = json.getString("access_token")
                    token = newToken
                    Result.success(LoginResponse(newToken, "bearer"))
                } else {
                    val error = parseErrorMessage(response.body?.string(), "Login error")
                    Result.failure(Exception(error))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loginWithGoogle(idToken: String): Result<LoginResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = createJsonBody(mapOf("id_token" to idToken))
                val request = Request.Builder()
                    .url("$baseUrl/auth/google")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    val newToken = json.getString("access_token")
                    token = newToken
                    Result.success(LoginResponse(newToken, "bearer"))
                } else {
                    val error = parseErrorMessage(response.body?.string(), "Google login error")
                    Result.failure(Exception(error))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getTasks(completed: Boolean? = null): Result<List<TaskDto>> =
        withContext(Dispatchers.IO) {
            try {
                val urlBuilder = StringBuilder("$baseUrl/tasks")
                if (completed != null) {
                    urlBuilder.append("?completed=$completed")
                }

                val request = Request.Builder()
                    .url(urlBuilder.toString())
                    .addHeader("Authorization", "Bearer $token")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonArray = org.json.JSONArray(response.body?.string() ?: "[]")
                    val tasks = mutableListOf<TaskDto>()
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        tasks.add(parseTask(json))
                    }
                    Result.success(tasks)
                } else {
                    Result.failure(Exception("Failed to fetch tasks: ${response.code}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getTask(taskId: Long): Result<TaskDto> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/tasks/$taskId")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                Result.success(parseTask(json))
            } else {
                Result.failure(Exception("Task not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTask(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radius: Int,
        markerColor: Int,
        markerIcon: String,
        category: String,
        autoRemoveAfterTrigger: Boolean,
        isNotificationEnabled: Boolean
    ): Result<TaskDto> = withContext(Dispatchers.IO) {
        try {
            val body = createJsonBody(
                mapOf(
                    "title" to title,
                    "description" to description,
                    "latitude" to latitude,
                    "longitude" to longitude,
                    "address" to address,
                    "radius" to radius,
                    "marker_color" to markerColor,
                    "marker_icon" to markerIcon,
                    "category" to category,
                    "auto_remove_after_trigger" to autoRemoveAfterTrigger,
                    "is_notification_enabled" to isNotificationEnabled
                )
            )
            val request = Request.Builder()
                .url("$baseUrl/tasks")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                Result.success(parseTask(json))
            } else {
                Result.failure(Exception("Failed to create task"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTask(taskId: Long, updates: Map<String, Any>): Result<TaskDto> =
        withContext(Dispatchers.IO) {
            try {
                val body = createJsonBody(updates)
                val request = Request.Builder()
                    .url("$baseUrl/tasks/$taskId")
                    .addHeader("Authorization", "Bearer $token")
                    .put(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val json = JSONObject(response.body?.string() ?: "{}")
                    Result.success(parseTask(json))
                } else {
                    Result.failure(Exception("Failed to update task"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteTask(taskId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/tasks/$taskId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete task"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleTask(taskId: Long): Result<TaskDto> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/tasks/$taskId/toggle")
                .addHeader("Authorization", "Bearer $token")
                .post(createJsonBody(emptyMap()))
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                Result.success(parseTask(json))
            } else {
                Result.failure(Exception("Failed to toggle task status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTask(json: JSONObject): TaskDto {
        val completedAt = if (json.isNull("completed_at")) null else json.getString("completed_at")
        return TaskDto(
            id = json.getLong("id"),
            title = json.getString("title"),
            description = json.optString("description", ""),
            latitude = json.getDouble("latitude"),
            longitude = json.getDouble("longitude"),
            address = json.optString("address", ""),
            radius = json.optInt("radius", 100),
            markerColor = json.optInt("marker_color", 0xFF2196F3.toInt()),
            markerIcon = json.optString("marker_icon", "pin"),
            category = json.optString("category", "general"),
            autoRemoveAfterTrigger = json.optBoolean("auto_remove_after_trigger", false),
            isCompleted = json.getBoolean("is_completed"),
            isNotificationEnabled = json.getBoolean("is_notification_enabled"),
            createdAt = json.getString("created_at"),
            completedAt = completedAt,
            userId = json.getLong("user_id")
        )
    }
}
