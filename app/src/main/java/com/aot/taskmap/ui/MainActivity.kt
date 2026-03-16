package com.aot.taskmap.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.aot.taskmap.BuildConfig
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.databinding.ActivityMainBinding
import com.aot.taskmap.service.LocationService
import com.aot.taskmap.ui.settings.GitHubUpdateChecker
import com.aot.taskmap.ui.settings.InAppUpdateManager
import com.aot.taskmap.ui.settings.UpdateVersionComparator
import com.aot.taskmap.ui.tasks.TaskShareManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastNavClickTime = 0L
    private var updateCheckTriggered = false

    companion object {
        const val EXTRA_TRIGGER_UPDATE_CHECK = "trigger_update_check"
        const val EXTRA_IMPORT_TASKS_TEXT = "import_tasks_text"
    }

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

        setupNavigation(savedInstanceState == null)
        requestLocationPermissionIfNeeded()
        handleIntent(intent)
        maybeCheckForUpdatesAfterAuthorization(intent, savedInstanceState == null)
    }

    override fun onResume() {
        super.onResume()
        // Если APK уже скачан, автоматически пробуем запустить установку при возврате в приложение.
        InAppUpdateManager.resumePendingInstallIfPossible(this)
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

    private fun setupNavigation(isFreshLaunch: Boolean) {
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

        if (isFreshLaunch) {
            openInitialTabIfNeeded()
        }
    }

    private fun openInitialTabIfNeeded() {
        if (SettingsPreferences.shouldOpenProfileTabOnFirstMainLaunch(this)) {
            SettingsPreferences.markProfileTabShown(this)
            binding.bottomNavigation.selectedItemId = R.id.profileFragment
        } else {
            binding.bottomNavigation.selectedItemId = R.id.mapFragment
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
        val sharedTasksText = intent?.getStringExtra(EXTRA_IMPORT_TASKS_TEXT)
            ?: TaskShareManager.extractShareTextFromIntent(intent)
        if (!sharedTasksText.isNullOrBlank()) {
            handleImportedTasksText(sharedTasksText)
            intent?.removeExtra(EXTRA_IMPORT_TASKS_TEXT)
            setIntent(intent)
        }

        intent?.getLongExtra("task_id", -1)?.let { taskId ->
            if (taskId != -1L) {
                // Навигация к задаче при необходимости
            }
        }
    }

    private fun maybeCheckForUpdatesAfterAuthorization(intent: Intent?, onlyOnFirstCreate: Boolean) {
        if (!onlyOnFirstCreate || updateCheckTriggered) return
        if (!SettingsPreferences.isAutoUpdateCheckEnabled(this)) return
        val shouldCheck = intent?.getBooleanExtra(EXTRA_TRIGGER_UPDATE_CHECK, false) == true
        if (!shouldCheck) return

        updateCheckTriggered = true
        intent.removeExtra(EXTRA_TRIGGER_UPDATE_CHECK)
        setIntent(intent)
        runPostAuthUpdateCheck()
    }

    private fun runPostAuthUpdateCheck() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GitHubUpdateChecker.fetchLatestRelease() }
            }

            result.onSuccess { release ->
                val remoteVersion = release.versionTag.ifBlank { release.releaseName ?: "" }
                if (!UpdateVersionComparator.isRemoteVersionNewer(remoteVersion, BuildConfig.VERSION_NAME)) return@onSuccess
                if (isFinishing || isDestroyed) return@onSuccess

                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(R.string.settings_update_available_title)
                    .setMessage(
                        getString(
                            R.string.settings_update_available_message,
                            remoteVersion,
                            BuildConfig.VERSION_NAME
                        )
                    )
                    .setPositiveButton(R.string.settings_update_action_download) { _, _ ->
                        val updateUrl = release.apkUrl
                        if (!updateUrl.isNullOrBlank()) {
                            startInAppUpdateDownload(updateUrl)
                        } else {
                            openUpdateLink(release.releasePageUrl ?: BuildConfig.UPDATE_RELEASES_PAGE)
                        }
                    }
                    .setNeutralButton(R.string.settings_update_open_releases) { _, _ ->
                        openUpdateLink(BuildConfig.UPDATE_RELEASES_PAGE)
                    }
                    .setNegativeButton(R.string.settings_update_action_later, null)
                    .show()
            }
        }
    }

    private fun openUpdateLink(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(
                this,
                getString(R.string.settings_update_check_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startInAppUpdateDownload(url: String) {
        InAppUpdateManager.startBackgroundDownload(this, url)
            .onSuccess {
                Toast.makeText(
                    this,
                    getString(R.string.update_download_started),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .onFailure {
                Toast.makeText(
                    this,
                    getString(R.string.update_download_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun handleImportedTasksText(sharedText: String) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                TaskShareManager.importTasksFromShareText(applicationContext, sharedText)
            }
            result.onSuccess { imported ->
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.tasks_import_success,
                        imported.addedCount,
                        imported.senderName
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this@MainActivity,
                    error.message ?: getString(R.string.tasks_import_error),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        maybeCheckForUpdatesAfterAuthorization(intent, onlyOnFirstCreate = true)
    }
}
