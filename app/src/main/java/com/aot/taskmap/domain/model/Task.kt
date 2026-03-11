package com.aot.taskmap.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val radius: Int = 100, // Radius in meters for geofence
    val isCompleted: Boolean = false,
    val isNotificationEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
