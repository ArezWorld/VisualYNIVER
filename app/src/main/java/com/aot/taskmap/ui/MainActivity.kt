package com.aot.taskmap.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.databinding.ActivityMainBinding
import com.aot.taskmap.service.LocationService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastNavClickTime = 0L

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // После выдачи геолокации сразу продолжаем штатный сценарий по уведомлениям/сервису
        if (hasLocationPermission()) {
            requestNotificationPermission()
        }
    }

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && SettingsPreferences.isNotificationsEnabled(this)) {
            startLocationService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemePreferences.getThemeRes(this))
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        requestLocationPermissionIfNeeded()
        handleIntent(intent)
    }

    private fun requestLocationPermissionIfNeeded() {
        if (hasLocationPermission()) {
            requestNotificationPermission()
            return
        }
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)
        // Повторное нажатие на текущую вкладку игнорируем
        binding.bottomNavigation.setOnItemReselectedListener { }
        // Защита от двойных быстрых нажатий при переходе на вкладку «Карта»
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val now = System.currentTimeMillis()
            if (now - lastNavClickTime < 400) {
                return@setOnItemSelectedListener false
            }
            if (navController.currentDestination?.id == item.itemId) {
                return@setOnItemSelectedListener false
            }
            lastNavClickTime = now
            NavigationUI.onNavDestinationSelected(item, navController)
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.mapFragment) {
                supportActionBar?.hide()
            } else {
                supportActionBar?.show()
            }
        }
        if (navController.currentDestination?.id == R.id.mapFragment) {
            supportActionBar?.hide()
        } else {
            supportActionBar?.show()
        }
    }

    private fun requestNotificationPermission() {
        if (!SettingsPreferences.isNotificationsEnabled(this)) {
            stopLocationService()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startLocationService()
                }
                else -> {
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            startLocationService()
        }
    }

    private fun stopLocationService() {
        val serviceIntent = Intent(this, LocationService::class.java)
        stopService(serviceIntent)
    }

    private fun startLocationService() {
        if (!hasLocationPermission()) return
        if (LocationService.isRunning()) return
        val serviceIntent = Intent(this, LocationService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getLongExtra("task_id", -1)?.let { taskId ->
            if (taskId != -1L) {
                // Навигация к задаче при необходимости
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
}
