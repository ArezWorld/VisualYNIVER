package com.aot.taskmap.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
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
    private val lastNotifiedAt = mutableMapOf<Long, Long>()

    companion object {
        private const val SERVICE_CHANNEL_ID = "task_tracking_service"
        private const val ALERT_CHANNEL_ID_SOUND_DEFAULT = "task_reminders_sound_default"
        private const val ALERT_CHANNEL_ID_SILENT = "task_reminders_silent"
        private const val ALERT_CHANNEL_ID_CUSTOM_PREFIX = "task_reminders_sound_custom_"
        const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_COOLDOWN_MS = 60_000L
        @Volatile
        private var running = false

        fun isRunning(): Boolean = running
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createNotificationChannels()
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
                lastNotifiedAt.keys.retainAll(activeIds)
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
                if (isInside && canNotifyTask(task.id)) {
                    showTaskNotification(task)
                    if (task.autoRemoveAfterTrigger) {
                        serviceScope.launch {
                            taskDao.deleteTaskById(task.id)
                        }
                    }
                }
                continue
            }

            if (!wasInside && isInside && canNotifyTask(task.id)) {
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

    private fun canNotifyTask(taskId: Long): Boolean {
        val now = System.currentTimeMillis()
        val last = lastNotifiedAt[taskId] ?: 0L
        if (now - last < NOTIFICATION_COOLDOWN_MS) return false
        lastNotifiedAt[taskId] = now
        return true
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
            putExtra("task_lat", task.latitude)
            putExtra("task_lng", task.longitude)
            putExtra("task_title", task.title)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            task.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundEnabled = SettingsPreferences.isNotificationSoundEnabled(this)
        val channelId = resolveAlertChannelId(soundEnabled)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
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

        if (!soundEnabled) {
            notificationBuilder.setSilent(true)
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            val soundUri = resolveNotificationSoundUri()
            notificationBuilder.setSound(soundUri)
        }

        val notification = notificationBuilder.build()

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

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.service_tracking_title))
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val notificationManager = getSystemService(NotificationManager::class.java)
        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            getString(R.string.service_tracking_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.service_tracking_text)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(serviceChannel)

        // Базовые каналы: без звука и со стандартным системным звуком.
        ensureAlertChannel(notificationManager, ALERT_CHANNEL_ID_SILENT, null)
        ensureAlertChannel(
            notificationManager,
            ALERT_CHANNEL_ID_SOUND_DEFAULT,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
    }

    private fun resolveAlertChannelId(soundEnabled: Boolean): String {
        if (!soundEnabled) return ALERT_CHANNEL_ID_SILENT
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return ALERT_CHANNEL_ID_SOUND_DEFAULT

        val manager = getSystemService(NotificationManager::class.java)
        val soundUri = resolveNotificationSoundUri()
        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return if (
            soundUri == null ||
            soundUri.toString() == defaultUri?.toString()
        ) {
            ALERT_CHANNEL_ID_SOUND_DEFAULT
        } else {
            val idSuffix = Integer.toUnsignedString(soundUri.toString().hashCode(), 16)
            val channelId = "$ALERT_CHANNEL_ID_CUSTOM_PREFIX$idSuffix"
            ensureAlertChannel(manager, channelId, soundUri)
            channelId
        }
    }

    private fun resolveNotificationSoundUri(): Uri? {
        val rawUri = SettingsPreferences.getNotificationSoundUri(this)
        val parsed = rawUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        return parsed ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun ensureAlertChannel(
        notificationManager: NotificationManager,
        channelId: String,
        soundUri: Uri?
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(channelId) != null) return

        val channelName = if (soundUri == null) {
            getString(R.string.map_channel_name_silent)
        } else {
            getString(R.string.map_channel_name_sound)
        }
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = getString(R.string.map_channel_desc)
            enableVibration(true)
            enableLights(true)
            setSound(soundUri, if (soundUri == null) null else audioAttributes)
        }
        notificationManager.createNotificationChannel(channel)
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
