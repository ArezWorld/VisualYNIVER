package com.aot.taskmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
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
import com.aot.taskmap.service.TrackingModeResolver.TrackingMode
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
    private var isLocationUpdatesRunning = false
    private var currentTrackingMode = TrackingMode.BALANCED
    private var lastKnownLatitude: Double? = null
    private var lastKnownLongitude: Double? = null
    private var isForegroundStarted = false

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val NOTIFICATION_ID = 1001
        @Volatile
        private var running = false

        fun isRunning(): Boolean = running
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        taskDao = TaskDatabase.getDatabase(applicationContext).taskDao()
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!isForegroundStarted) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(getString(R.string.service_tracking_text))
            )
            isForegroundStarted = true
        }
        if (tasksJob?.isActive != true) {
            startTrackingTasks()
        }
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

    private fun startLocationUpdates(mode: TrackingMode) {
        if (!hasLocationPermission()) return
        if (isLocationUpdatesRunning && currentTrackingMode == mode) return

        if (isLocationUpdatesRunning) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }

        val locationRequest = when (mode) {
            TrackingMode.BALANCED -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    45_000L
                ).apply {
                    setMinUpdateIntervalMillis(25_000L)
                    setMinUpdateDistanceMeters(60f)
                    setWaitForAccurateLocation(false)
                }.build()
            }
            TrackingMode.PRECISE -> {
                LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    15_000L
                ).apply {
                    setMinUpdateIntervalMillis(8_000L)
                    setMinUpdateDistanceMeters(15f)
                    setWaitForAccurateLocation(true)
                }.build()
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            currentTrackingMode = mode
            isLocationUpdatesRunning = true
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun stopLocationUpdates() {
        if (!isLocationUpdatesRunning) return
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesRunning = false
    }

    private fun startTrackingTasks() {
        tasksJob?.cancel()
        tasksJob = serviceScope.launch {
            taskDao.getTasksWithNotificationsFlow().collect { tasks ->
                trackedTasks = tasks
                val activeIds = tasks.map { it.id }.toSet()
                lastInsideState.keys.retainAll(activeIds)
                refreshTrackingMode()
            }
        }
    }

    private fun checkProximityToTasks(currentLat: Double, currentLng: Double) {
        lastKnownLatitude = currentLat
        lastKnownLongitude = currentLng
        val snapshot = trackedTasks
        for (task in snapshot) {
            val distance = TrackingModeResolver.calculateDistanceMeters(
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
        refreshTrackingMode()
    }

    private fun showTaskNotification(task: Task) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

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
            .setContentTitle(getString(R.string.service_task_nearby))
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
            .setContentTitle(getString(R.string.service_tracking_title))
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
                getString(R.string.map_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.map_channel_desc)
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun refreshTrackingMode() {
        val mode = TrackingModeResolver.resolveMode(
            tasks = trackedTasks,
            currentLat = lastKnownLatitude,
            currentLng = lastKnownLongitude
        )
        if (mode == null) {
            stopLocationUpdates()
            return
        }
        startLocationUpdates(mode)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
        isForegroundStarted = false
        stopLocationUpdates()
        tasksJob?.cancel()
        serviceScope.cancel()
    }
}
