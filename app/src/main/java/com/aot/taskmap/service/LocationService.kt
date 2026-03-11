package com.aot.taskmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.aot.taskmap.R
import com.aot.taskmap.data.local.TaskDatabase
import com.aot.taskmap.domain.model.Task
import com.aot.taskmap.ui.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var taskDao: com.aot.taskmap.data.local.TaskDao

    private var tasksJob: Job? = null
    private var trackedTasks: List<Task> = emptyList()
    private val lastInsideState = mutableMapOf<Long, Boolean>()

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        taskDao = TaskDatabase.getDatabase(applicationContext).taskDao()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Tracking location..."))
        startLocationUpdates()
        startTrackingTasks()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    checkProximityToTasks(location.latitude, location.longitude)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            10_000L
        ).apply {
            setMinUpdateIntervalMillis(5_000L)
            setWaitForAccurateLocation(false)
        }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun startTrackingTasks() {
        tasksJob?.cancel()
        tasksJob = serviceScope.launch {
            taskDao.getTasksWithNotificationsFlow().collect { tasks ->
                trackedTasks = tasks
                val activeIds = tasks.map { it.id }.toSet()
                lastInsideState.keys.retainAll(activeIds)
            }
        }
    }

    private fun checkProximityToTasks(currentLat: Double, currentLng: Double) {
        val snapshot = trackedTasks
        for (task in snapshot) {
            val distance = calculateDistance(
                currentLat, currentLng,
                task.latitude, task.longitude
            )
            val isInside = distance <= task.radius
            val hasState = lastInsideState.containsKey(task.id)
            val wasInside = lastInsideState[task.id] == true

            if (!hasState) {
                lastInsideState[task.id] = isInside
                continue
            }

            if (!wasInside && isInside) {
                showTaskNotification(task)
                if (task.autoRemoveAfterTrigger) {
                    serviceScope.launch {
                        taskDao.deleteTaskById(task.id)
                    }
                }
            }

            lastInsideState[task.id] = isInside
        }
    }

    private fun calculateDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val earthRadius = 6_371_000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun showTaskNotification(task: Task) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("task_id", task.id)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Task nearby")
            .setContentText(task.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${task.title}\n${task.description}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(task.id.toInt(), notification)
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("AOT - Task tracking")
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Location-based task reminders"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tasksJob?.cancel()
        serviceScope.cancel()
    }
}
