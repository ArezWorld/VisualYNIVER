package com.aot.taskmap.service

import com.aot.taskmap.domain.model.Task
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object TrackingModeResolver {

    enum class TrackingMode {
        BALANCED,
        PRECISE
    }

    fun resolveMode(
        tasks: List<Task>,
        currentLat: Double?,
        currentLng: Double?,
        preciseThresholdMeters: Double = 750.0
    ): TrackingMode? {
        if (tasks.isEmpty()) return null
        if (currentLat == null || currentLng == null) return TrackingMode.BALANCED

        val nearestDistance = tasks.minOfOrNull { task ->
            calculateDistanceMeters(currentLat, currentLng, task.latitude, task.longitude)
        } ?: Double.MAX_VALUE

        return if (nearestDistance <= preciseThresholdMeters) {
            TrackingMode.PRECISE
        } else {
            TrackingMode.BALANCED
        }
    }

    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
