package com.aot.taskmap.data.model

data class UserDto(
    val id: Int,
    val username: String,
    val email: String,
    val isActive: Boolean
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String
)

data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

data class TaskDto(
    val id: Long,
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val radius: Int,
    val markerColor: Int,
    val markerIcon: String,
    val category: String,
    val autoRemoveAfterTrigger: Boolean,
    val isCompleted: Boolean,
    val isNotificationEnabled: Boolean,
    val createdAt: String,
    val completedAt: String?,
    val userId: Long
)

data class TaskCreateRequest(
    val title: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val radius: Int = 100,
    val markerColor: Int = 0xFF2196F3.toInt(),
    val markerIcon: String = "pin",
    val category: String = "general",
    val autoRemoveAfterTrigger: Boolean = false,
    val isNotificationEnabled: Boolean = true
)

data class TaskUpdateRequest(
    val title: String? = null,
    val description: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val radius: Int? = null,
    val markerColor: Int? = null,
    val markerIcon: String? = null,
    val category: String? = null,
    val autoRemoveAfterTrigger: Boolean? = null,
    val isCompleted: Boolean? = null,
    val isNotificationEnabled: Boolean? = null
)
