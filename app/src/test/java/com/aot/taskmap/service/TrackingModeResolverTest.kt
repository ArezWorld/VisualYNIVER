package com.aot.taskmap.service

import com.aot.taskmap.domain.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingModeResolverTest {

    @Test
    fun `returns null when no tasks available`() {
        val mode = TrackingModeResolver.resolveMode(emptyList(), 51.2, 58.3)

        assertEquals(null, mode)
    }

    @Test
    fun `returns balanced when location is unknown`() {
        val tasks = listOf(sampleTask(latitude = 51.2, longitude = 58.3))

        val mode = TrackingModeResolver.resolveMode(tasks, null, null)

        assertEquals(TrackingModeResolver.TrackingMode.BALANCED, mode)
    }

    @Test
    fun `returns precise when nearest task is close enough`() {
        val tasks = listOf(sampleTask(latitude = 51.2005, longitude = 58.3005))

        val mode = TrackingModeResolver.resolveMode(tasks, 51.2, 58.3, 750.0)

        assertEquals(TrackingModeResolver.TrackingMode.PRECISE, mode)
    }

    @Test
    fun `returns balanced when nearest task is far away`() {
        val tasks = listOf(sampleTask(latitude = 52.0, longitude = 59.0))

        val mode = TrackingModeResolver.resolveMode(tasks, 51.2, 58.3, 750.0)

        assertEquals(TrackingModeResolver.TrackingMode.BALANCED, mode)
    }

    @Test
    fun `distance calculation returns plausible value`() {
        val distance = TrackingModeResolver.calculateDistanceMeters(51.2, 58.3, 51.2005, 58.3005)

        assertTrue(distance in 60.0..90.0)
    }

    private fun sampleTask(latitude: Double, longitude: Double): Task {
        return Task(
            title = "Тест",
            latitude = latitude,
            longitude = longitude
        )
    }
}
