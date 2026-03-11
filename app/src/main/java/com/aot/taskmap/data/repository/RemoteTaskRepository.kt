package com.aot.taskmap.data.repository

import com.aot.taskmap.data.model.TaskDto
import com.aot.taskmap.data.remote.ApiClient
import com.aot.taskmap.domain.model.Task
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class RemoteTaskRepository(private val apiClient: ApiClient) {

    suspend fun fetchTasks(completed: Boolean? = null): Result<List<Task>> {
        return apiClient.getTasks(completed).map { tasks ->
            tasks.map { it.toDomain() }
        }
    }

    suspend fun createTask(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        address: String,
        radius: Int,
        enableNotification: Boolean
    ): Result<Task> {
        return apiClient.createTask(
            title = title,
            description = description,
            latitude = latitude,
            longitude = longitude,
            address = address,
            radius = radius,
            isNotificationEnabled = enableNotification
        ).map { it.toDomain() }
    }

    suspend fun updateTask(task: Task): Result<Task> {
        val updates = mapOf(
            "title" to task.title,
            "description" to task.description,
            "latitude" to task.latitude,
            "longitude" to task.longitude,
            "address" to task.address,
            "radius" to task.radius,
            "is_completed" to task.isCompleted,
            "is_notification_enabled" to task.isNotificationEnabled
        )
        return apiClient.updateTask(task.id, updates).map { it.toDomain() }
    }

    suspend fun deleteTask(taskId: Long): Result<Unit> {
        return apiClient.deleteTask(taskId)
    }

    suspend fun toggleTaskCompletion(taskId: Long): Result<Task> {
        return apiClient.toggleTask(taskId).map { it.toDomain() }
    }

    private fun TaskDto.toDomain(): Task {
        val createdAtMs = parseIsoToEpochMillis(createdAt) ?: System.currentTimeMillis()
        val completedAtMs = parseIsoToEpochMillis(completedAt)
        return Task(
            id = id,
            title = title,
            description = description,
            latitude = latitude,
            longitude = longitude,
            address = address,
            radius = radius,
            isCompleted = isCompleted,
            isNotificationEnabled = isNotificationEnabled,
            createdAt = createdAtMs,
            completedAt = completedAtMs
        )
    }

    private fun parseIsoToEpochMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            val localDateTime = LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME)
            localDateTime.toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }
}
