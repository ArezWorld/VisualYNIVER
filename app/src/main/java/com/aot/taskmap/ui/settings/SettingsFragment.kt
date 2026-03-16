package com.aot.taskmap.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.aot.taskmap.BuildConfig
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.databinding.FragmentSettingsBinding
import com.aot.taskmap.service.LocationService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var pendingEnableNotifications = false
    private var updateCheckInProgress = false

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val context = context ?: return@registerForActivityResult
        if (pendingEnableNotifications) {
            pendingEnableNotifications = false
            if (isGranted) {
                SettingsPreferences.setNotificationsEnabled(context, true)
                _binding?.switchNotifications?.isChecked = true
                updateNotificationSoundUi(true)
                startLocationService()
            } else {
                _binding?.switchNotifications?.isChecked = false
                updateNotificationSoundUi(false)
            }
        }
    }

    private val ringtonePickerRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val context = context ?: return@registerForActivityResult
        val pickedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                Uri::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }

        SettingsPreferences.setNotificationSoundUri(context, pickedUri?.toString())
        updateNotificationSoundLabel()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.textVersion.text = getString(R.string.settings_version_format, BuildConfig.VERSION_NAME)
        binding.textDeveloper.text = getString(R.string.settings_developers)
        binding.buttonCheckUpdates.setOnClickListener {
            checkForUpdates()
        }
        binding.buttonOpenReleases.setOnClickListener {
            openUpdateLink(BuildConfig.UPDATE_RELEASES_PAGE)
        }

        val context = requireContext()
        binding.switchAutoUpdateCheck.isChecked =
            SettingsPreferences.isAutoUpdateCheckEnabled(context)
        binding.switchAutoUpdateCheck.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setAutoUpdateCheckEnabled(context, checked)
            UpdateCheckScheduler.refresh(context)
        }

        binding.switchDarkTheme.isChecked = ThemePreferences.isDarkMode(context)
        binding.switchDarkTheme.setOnCheckedChangeListener { _, checked ->
            val theme = ThemePreferences.getTheme(context)
            if (
                !checked &&
                (theme == ThemePreferences.THEME_RED_BLACK ||
                    theme == ThemePreferences.THEME_STEEL)
            ) {
                binding.switchDarkTheme.isChecked = true
                return@setOnCheckedChangeListener
            }
            ThemePreferences.setDarkMode(context, checked)
        }

        binding.radioAppTheme.check(
            when (ThemePreferences.getTheme(context)) {
                ThemePreferences.THEME_WHITE -> binding.radioThemeWhite.id
                ThemePreferences.THEME_BLUE -> binding.radioThemeBlue.id
                ThemePreferences.THEME_STEEL -> binding.radioThemeSteel.id
                ThemePreferences.THEME_RED_BLACK -> binding.radioThemeRedBlack.id
                else -> binding.radioThemePurple.id
            }
        )
        binding.radioAppTheme.setOnCheckedChangeListener { _, checkedId ->
            val selectedTheme = when (checkedId) {
                binding.radioThemeWhite.id -> ThemePreferences.THEME_WHITE
                binding.radioThemeBlue.id -> ThemePreferences.THEME_BLUE
                binding.radioThemeSteel.id -> ThemePreferences.THEME_STEEL
                binding.radioThemeRedBlack.id -> ThemePreferences.THEME_RED_BLACK
                else -> ThemePreferences.THEME_PURPLE
            }
            if (selectedTheme != ThemePreferences.getTheme(context)) {
                ThemePreferences.setTheme(context, selectedTheme)
                if (
                    (selectedTheme == ThemePreferences.THEME_RED_BLACK ||
                        selectedTheme == ThemePreferences.THEME_STEEL) &&
                    !binding.switchDarkTheme.isChecked
                ) {
                    binding.switchDarkTheme.isChecked = true
                }
                activity?.recreate()
            }
        }

        binding.switchAnimations.isChecked =
            SettingsPreferences.isAnimationsEnabled(context)
        binding.switchAnimations.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setAnimationsEnabled(context, checked)
        }

        binding.switchFollowLocation.isChecked =
            SettingsPreferences.isFollowLocationEnabled(context)
        binding.switchFollowLocation.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setFollowLocationEnabled(context, checked)
        }

        binding.switchUseAvatarLocationMarker.isChecked =
            SettingsPreferences.isUseAvatarLocationMarkerEnabled(context)
        binding.switchUseAvatarLocationMarker.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setUseAvatarLocationMarkerEnabled(context, checked)
        }

        binding.switchShowRadius.isChecked =
            SettingsPreferences.isShowRadiusEnabled(context)
        binding.switchShowRadius.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setShowRadiusEnabled(context, checked)
        }

        binding.switchShowCompletedMarkers.isChecked =
            SettingsPreferences.isShowCompletedMarkersEnabled(context)
        binding.switchShowCompletedMarkers.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setShowCompletedMarkersEnabled(context, checked)
        }

        binding.switchHighlightImportantPlaces.isChecked =
            SettingsPreferences.isHighlightImportantPlacesEnabled(context)
        binding.switchHighlightImportantPlaces.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setHighlightImportantPlacesEnabled(context, checked)
        }

        val selectedStyle = SettingsPreferences.getMapStyle(context)
        binding.radioMapStyle.check(
            when (selectedStyle) {
                SettingsPreferences.MAP_STYLE_TERRAIN -> binding.radioMapTerrain.id
                SettingsPreferences.MAP_STYLE_VOYAGER -> binding.radioMapVoyager.id
                SettingsPreferences.MAP_STYLE_TOPO -> binding.radioMapTopo.id
                else -> binding.radioMapStandard.id
            }
        )
        binding.radioMapStyle.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                binding.radioMapTerrain.id -> SettingsPreferences.MAP_STYLE_TERRAIN
                binding.radioMapVoyager.id -> SettingsPreferences.MAP_STYLE_VOYAGER
                binding.radioMapTopo.id -> SettingsPreferences.MAP_STYLE_TOPO
                else -> SettingsPreferences.MAP_STYLE_STANDARD
            }
            SettingsPreferences.setMapStyle(context, style)
        }

        binding.switchConfirmComplete.isChecked =
            SettingsPreferences.isConfirmCompleteEnabled(context)
        binding.switchConfirmComplete.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setConfirmCompleteEnabled(context, checked)
        }

        binding.switchNotifications.isChecked =
            SettingsPreferences.isNotificationsEnabled(context)
        binding.switchNotificationSound.isChecked =
            SettingsPreferences.isNotificationSoundEnabled(context)
        updateNotificationSoundUi(binding.switchNotifications.isChecked)
        binding.switchNotificationSound.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setNotificationSoundEnabled(context, checked)
            updateNotificationSoundUi(binding.switchNotifications.isChecked)
        }
        binding.buttonPickNotificationSound.setOnClickListener {
            openNotificationSoundPicker()
        }
        updateNotificationSoundLabel()
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            updateNotificationSoundUi(checked)
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingEnableNotifications = true
                    binding.switchNotifications.isChecked = false
                    updateNotificationSoundUi(false)
                    notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    return@setOnCheckedChangeListener
                }
                SettingsPreferences.setNotificationsEnabled(context, true)
                startLocationService()
            } else {
                SettingsPreferences.setNotificationsEnabled(context, false)
                stopLocationService()
            }
        }
    }

    private fun updateNotificationSoundUi(notificationsEnabled: Boolean) {
        if (_binding == null) return
        binding.switchNotificationSound.isEnabled = notificationsEnabled
        binding.buttonPickNotificationSound.isEnabled =
            notificationsEnabled && binding.switchNotificationSound.isChecked
        binding.textNotificationSoundDesc.alpha = if (notificationsEnabled) 1f else 0.5f
        binding.textNotificationSoundValue.alpha =
            if (notificationsEnabled && binding.switchNotificationSound.isChecked) 1f else 0.5f
    }

    private fun openNotificationSoundPicker() {
        val context = context ?: return
        val existingUri = SettingsPreferences.getNotificationSoundUri(context)
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_TITLE,
                getString(R.string.settings_notification_sound_picker_title)
            )
        }
        ringtonePickerRequest.launch(pickerIntent)
    }

    private fun updateNotificationSoundLabel() {
        if (_binding == null) return
        val context = context ?: return
        val selectedUri = SettingsPreferences.getNotificationSoundUri(context)
            ?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val title = runCatching {
            RingtoneManager.getRingtone(context, selectedUri)?.getTitle(context)
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: getString(R.string.settings_notification_sound_default)

        binding.textNotificationSoundValue.text =
            getString(R.string.settings_notification_sound_selected, title)
    }

    private fun startLocationService() {
        val context = context ?: return
        if (!hasLocationPermission(context)) return
        if (LocationService.isRunning()) return
        val serviceIntent = Intent(context, LocationService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    private fun stopLocationService() {
        val context = context ?: return
        val serviceIntent = Intent(context, LocationService::class.java)
        context.stopService(serviceIntent)
    }

    private fun checkForUpdates() {
        if (updateCheckInProgress) return
        updateCheckInProgress = true
        setUpdateButtonLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { GitHubUpdateChecker.fetchLatestRelease() }
            }

            if (!isAdded || _binding == null) {
                updateCheckInProgress = false
                return@launch
            }

            setUpdateButtonLoading(false)
            updateCheckInProgress = false

            result.fold(
                onSuccess = { release ->
                    val remoteVersion = release.versionTag.ifBlank { release.releaseName ?: "" }
                    if (!UpdateVersionComparator.isRemoteVersionNewer(remoteVersion, BuildConfig.VERSION_NAME)) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.settings_update_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@fold
                    }

                    val message = getString(
                        R.string.settings_update_available_message,
                        remoteVersion,
                        BuildConfig.VERSION_NAME
                    )

                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.settings_update_available_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.settings_update_action_download) { _, _ ->
                            val url = release.apkUrl ?: BuildConfig.UPDATE_LATEST_APK_URL
                            startInAppUpdateDownload(url)
                        }
                        .setNegativeButton(R.string.settings_update_action_later, null)
                        .show()
                },
                onFailure = {
                    showUpdateFallbackDialog()
                }
            )
        }
    }

    private fun showUpdateFallbackDialog() {
        if (!isAdded || _binding == null) return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_update_check_failed)
            .setMessage(R.string.settings_update_check_failed_download)
            .setPositiveButton(R.string.settings_update_action_download) { _, _ ->
                startInAppUpdateDownload(BuildConfig.UPDATE_LATEST_APK_URL)
            }
            .setNeutralButton(R.string.settings_update_open_releases) { _, _ ->
                openUpdateLink(BuildConfig.UPDATE_RELEASES_PAGE)
            }
            .setNegativeButton(R.string.settings_update_action_later, null)
            .show()
    }

    private fun startInAppUpdateDownload(url: String) {
        val context = context ?: return
        InAppUpdateManager.startBackgroundDownload(context, url)
            .onSuccess {
                Toast.makeText(
                    context,
                    getString(R.string.update_download_started),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .onFailure {
                Toast.makeText(
                    context,
                    getString(R.string.update_download_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun setUpdateButtonLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.buttonCheckUpdates.isEnabled = !isLoading
        binding.buttonCheckUpdates.text = getString(
            if (isLoading) R.string.settings_check_updates_loading
            else R.string.settings_check_updates
        )
    }

    private fun openUpdateLink(url: String) {
        val context = context ?: return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(
                context,
                getString(R.string.settings_update_check_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun hasLocationPermission(context: android.content.Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
