package com.aot.taskmap.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.data.local.ThemePreferences
import com.aot.taskmap.databinding.FragmentSettingsBinding
import com.aot.taskmap.service.LocationService
import com.aot.taskmap.ui.map.MapTileSources
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var pendingEnableNotifications = false
    private val offlineRegions = listOf(
        OfflineRegion(
            R.string.settings_region_moscow,
            BoundingBox(56.05, 38.10, 54.90, 36.20),
            8,
            14
        ),
        OfflineRegion(
            R.string.settings_region_spb,
            BoundingBox(60.35, 31.40, 59.35, 29.25),
            8,
            14
        ),
        OfflineRegion(
            R.string.settings_region_sverdlovsk,
            BoundingBox(61.20, 64.80, 56.00, 56.00),
            7,
            13
        ),
        OfflineRegion(
            R.string.settings_region_tatarstan,
            BoundingBox(56.70, 54.00, 53.90, 47.20),
            7,
            13
        ),
        OfflineRegion(
            R.string.settings_region_novosibirsk,
            BoundingBox(57.90, 86.00, 53.20, 74.80),
            7,
            13
        )
    )

    private data class OfflineRegion(
        @StringRes val titleRes: Int,
        val box: BoundingBox,
        val zoomMin: Int,
        val zoomMax: Int
    )

    private val notificationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val context = context ?: return@registerForActivityResult
        if (pendingEnableNotifications) {
            pendingEnableNotifications = false
            if (isGranted) {
                SettingsPreferences.setNotificationsEnabled(context, true)
                _binding?.switchNotifications?.isChecked = true
                startLocationService()
            } else {
                _binding?.switchNotifications?.isChecked = false
            }
        }
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

        binding.textVersion.text = getString(R.string.settings_version)
        binding.textDeveloper.text = getString(R.string.settings_developers)

        val context = requireContext()

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

        binding.switchSearchExpand.isChecked =
            SettingsPreferences.isSearchAutoExpandEnabled(context)
        binding.switchSearchExpand.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setSearchAutoExpandEnabled(context, checked)
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

        binding.switchShowRadius.isChecked =
            SettingsPreferences.isShowRadiusEnabled(context)
        binding.switchShowRadius.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setShowRadiusEnabled(context, checked)
        }

        binding.switchOfflineMap.isChecked =
            SettingsPreferences.isOfflineMapEnabled(context)
        binding.switchOfflineMap.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setOfflineMapEnabled(context, checked)
        }

        val selectedStyle = SettingsPreferences.getMapStyle(context)
        binding.radioMapStyle.check(
            when (selectedStyle) {
                SettingsPreferences.MAP_STYLE_TERRAIN -> binding.radioMapTerrain.id
                else -> binding.radioMapStandard.id
            }
        )
        binding.radioMapStyle.setOnCheckedChangeListener { _, checkedId ->
            val style = when (checkedId) {
                binding.radioMapTerrain.id -> SettingsPreferences.MAP_STYLE_TERRAIN
                else -> SettingsPreferences.MAP_STYLE_STANDARD
            }
            SettingsPreferences.setMapStyle(context, style)
        }

        val regionAdapter = ArrayAdapter(
            context,
            android.R.layout.simple_spinner_item,
            offlineRegions.map { getString(it.titleRes) }
        )
        regionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerOfflineRegion.adapter = regionAdapter
        binding.buttonDownloadRegion.setOnClickListener {
            val index = binding.spinnerOfflineRegion.selectedItemPosition
            if (index !in offlineRegions.indices) return@setOnClickListener
            startRegionDownload(offlineRegions[index])
        }

        binding.switchConfirmComplete.isChecked =
            SettingsPreferences.isConfirmCompleteEnabled(context)
        binding.switchConfirmComplete.setOnCheckedChangeListener { _, checked ->
            SettingsPreferences.setConfirmCompleteEnabled(context, checked)
        }

        binding.switchNotifications.isChecked =
            SettingsPreferences.isNotificationsEnabled(context)
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    pendingEnableNotifications = true
                    binding.switchNotifications.isChecked = false
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

    private fun startRegionDownload(region: OfflineRegion) {
        val context = context ?: return
        val tempMapView = MapView(context)
        tempMapView.setTileSource(MapTileSources.resolveByStyle(SettingsPreferences.getMapStyle(context)))
        tempMapView.setUseDataConnection(true)

        val cacheManager = try {
            CacheManager(tempMapView)
        } catch (_: Exception) {
            tempMapView.onDetach()
            Toast.makeText(
                context,
                getString(R.string.settings_download_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        Toast.makeText(context, getString(R.string.settings_download_started), Toast.LENGTH_SHORT).show()

        var finished = false
        fun finishOnce(message: String, enableOfflineMode: Boolean = false) {
            if (finished) return
            finished = true
            activity?.runOnUiThread {
                if (enableOfflineMode) {
                    SettingsPreferences.setOfflineMapEnabled(context, true)
                    _binding?.switchOfflineMap?.isChecked = true
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            tempMapView.onDetach()
        }

        cacheManager.downloadAreaAsyncNoUI(
            context,
            region.box,
            region.zoomMin,
            region.zoomMax,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() {
                    finishOnce(getString(R.string.settings_download_finished), enableOfflineMode = true)
                }

                override fun updateProgress(
                    progress: Int,
                    currentZoomLevel: Int,
                    zoomMin: Int,
                    zoomMax: Int
                ) = Unit

                override fun downloadStarted() = Unit

                override fun setPossibleTilesInArea(total: Int) = Unit

                override fun onTaskFailed(errors: Int) {
                    finishOnce(getString(R.string.settings_download_failed, errors))
                }
            }
        )
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
