package com.aot.taskmap.ui.map

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.aot.taskmap.R
import com.aot.taskmap.data.local.SettingsPreferences
import com.aot.taskmap.databinding.DialogAddTaskBinding
import com.aot.taskmap.databinding.FragmentMapBinding
import com.aot.taskmap.domain.model.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.Locale

class MapFragment : Fragment() {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val taskOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()

    private var shouldAutoCenter = true
    private var isAddMode = false
    private var isSearchExpanded = false

    private val searchResults = mutableListOf<Pair<String, GeoPoint>>()
    private lateinit var searchAdapter: ArrayAdapter<String>

    private data class MarkerIconOption(
        val key: String,
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int
    )

    private data class MarkerColorOption(
        val radioId: Int,
        @ColorRes val colorRes: Int,
        @StringRes val labelRes: Int
    )

    private val markerIconOptions = listOf(
        MarkerIconOption("pin", R.drawable.ic_marker_pin, R.string.marker_icon_pin),
        MarkerIconOption("flag", R.drawable.ic_marker_flag, R.string.marker_icon_flag),
        MarkerIconOption("star", R.drawable.ic_marker_star, R.string.marker_icon_star)
    )

    private val markerColorOptions = listOf(
        MarkerColorOption(R.id.color_blue, R.color.marker_blue, R.string.marker_color_blue),
        MarkerColorOption(R.id.color_green, R.color.marker_green, R.string.marker_color_green),
        MarkerColorOption(R.id.color_orange, R.color.marker_orange, R.string.marker_color_orange),
        MarkerColorOption(R.id.color_red, R.color.marker_red, R.string.marker_color_red)
    )

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true -> {
                enableMyLocation()
                getCurrentLocation()
            }
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                enableMyLocation()
                getCurrentLocation()
            }
            else -> {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.map_toast_location_permission),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Инициализация карты
        Configuration.getInstance().userAgentValue = requireContext().packageName
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.controller.setZoom(15.0)

        val defaultLocation = GeoPoint(51.2, 58.3)
        binding.mapView.controller.setCenter(defaultLocation)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        shouldAutoCenter = SettingsPreferences.isFollowLocationEnabled(requireContext())

        createNotificationChannel()
        requestLocationPermissions()
        setupFab()
        setupSearch()
        observeTasks()
        setupMapInteractionListener()
        setupMapTapToAdd()
        applySearchInitialState()
        animateChrome()
    }

    private fun setupFab() {
        binding.fabAddTask.setOnClickListener {
            isAddMode = true
            Toast.makeText(
                requireContext(),
                getString(R.string.map_toast_add_marker),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.fabMyLocation.setOnClickListener {
            // Однократное центрирование и обновление позиции
            shouldAutoCenter = SettingsPreferences.isFollowLocationEnabled(requireContext())
            getCurrentLocation()
        }
    }

    private fun setupSearch() {
        searchAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        binding.searchQuery.setAdapter(searchAdapter)
        binding.searchQuery.threshold = 1

        binding.buttonSearch.setOnClickListener {
            val query = binding.searchQuery.text?.toString()?.trim().orEmpty()
            performSearch(query)
        }

        binding.searchQuery.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                val query = binding.searchQuery.text?.toString()?.trim().orEmpty()
                performSearch(query)
                true
            } else {
                false
            }
        }

        binding.searchQuery.setOnItemClickListener { _, _, position, _ ->
            if (position in searchResults.indices) {
                val (title, point) = searchResults[position]
                binding.searchQuery.setText(title, false)
                moveToSearchResult(point)
            }
        }

        binding.searchFab.setOnClickListener {
            expandSearchUi()
        }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.search_empty),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val results = withContext(Dispatchers.IO) {
                if (!Geocoder.isPresent()) {
                    emptyList()
                } else {
                    try {
                        Geocoder(requireContext(), Locale.getDefault())
                            .getFromLocationName(query, 10)
                            .orEmpty()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }

            searchResults.clear()
            searchAdapter.clear()

            if (results.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.search_not_found),
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            results.forEach { address ->
                val title = listOfNotNull(
                    address.featureName,
                    address.thoroughfare,
                    address.subThoroughfare,
                    address.locality
                ).distinct().joinToString(", ").ifBlank {
                    address.getAddressLine(0) ?: getString(R.string.map_result_default)
                }

                val point = GeoPoint(address.latitude, address.longitude)
                searchResults.add(title to point)
                searchAdapter.add(title)
            }
            searchAdapter.notifyDataSetChanged()
            binding.searchQuery.showDropDown()

            if (searchResults.size == 1) {
                val (title, point) = searchResults.first()
                binding.searchQuery.setText(title, false)
                moveToSearchResult(point)
            } else {
                showSearchResultsDialog(searchResults)
            }
        }
    }

    private fun moveToSearchResult(point: GeoPoint) {
        // Перемещаем карту к выбранному адресу без постановки метки
        binding.mapView.controller.animateTo(point)
    }

    private fun showSearchResultsDialog(results: List<Pair<String, GeoPoint>>) {
        val titles = results.map { it.first }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.search_choose_title))
            .setItems(titles) { _, index ->
                val (title, point) = results[index]
                binding.searchQuery.setText(title, false)
                moveToSearchResult(point)
            }
            .setNegativeButton(getString(R.string.search_choose_cancel), null)
            .show()
    }

    private fun observeTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeTasks.collect { tasks ->
                        updateMapMarkers(tasks)
                    }
                }
                launch {
                    viewModel.currentLocation.collect { location ->
                        location?.let {
                            val geoPoint = GeoPoint(it.first, it.second)
                            if (shouldAutoCenter && SettingsPreferences.isFollowLocationEnabled(requireContext())) {
                                binding.mapView.controller.animateTo(geoPoint)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateMapMarkers(tasks: List<Task>) {
        if (taskOverlays.isNotEmpty()) {
            binding.mapView.overlays.removeAll(taskOverlays.toSet())
            taskOverlays.clear()
        }

        val showRadius = SettingsPreferences.isShowRadiusEnabled(requireContext())

        tasks.forEach { task ->
            val marker = Marker(binding.mapView).apply {
                position = GeoPoint(task.latitude, task.longitude)
                title = task.title
                snippet = task.description
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                val markerDrawable = buildMarkerDrawable(task)
                if (markerDrawable != null) {
                    icon = markerDrawable
                }
                isDraggable = false
                setOnMarkerClickListener { _, _ ->
                    viewModel.selectTask(task)
                    showTaskDetailsDialog(task)
                    true
                }
            }

            if (showRadius) {
                val circle = Polygon().apply {
                    points = Polygon.pointsAsCircle(marker.position, task.radius.toDouble())
                    fillColor = applyAlpha(task.markerColor, 40)
                    outlinePaint.color = applyAlpha(task.markerColor, 120)
                    outlinePaint.strokeWidth = 2f
                }

                binding.mapView.overlays.add(circle)
                taskOverlays.add(circle)
            }

            binding.mapView.overlays.add(marker)
            taskOverlays.add(marker)
        }

        binding.mapView.invalidate()
    }

    private fun buildMarkerDrawable(task: Task): Drawable? {
        val option = resolveMarkerIconOption(task.markerIcon)
        val drawable = ContextCompat.getDrawable(requireContext(), option.iconRes)?.mutate()
        if (drawable != null) {
            DrawableCompat.setTint(drawable, task.markerColor)
            if (task.isCompleted) {
                drawable.alpha = 160
            }
        }
        return drawable
    }

    private fun resolveMarkerIconOption(key: String?): MarkerIconOption {
        return markerIconOptions.firstOrNull { it.key == key } ?: markerIconOptions.first()
    }

    private fun applyAlpha(color: Int, alpha: Int): Int {
        val safeAlpha = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (safeAlpha shl 24)
    }

    private fun resolveMarkerColorName(color: Int): String {
        val match = markerColorOptions.firstOrNull {
            ContextCompat.getColor(requireContext(), it.colorRes) == color
        }
        return match?.let { getString(it.labelRes) }
            ?: getString(markerColorOptions.first().labelRes)
    }

    private fun resolveMarkerIconName(key: String?): String {
        return getString(resolveMarkerIconOption(key).labelRes)
    }

    private fun selectMarkerColor(group: RadioGroup, color: Int) {
        val match = markerColorOptions.firstOrNull {
            ContextCompat.getColor(requireContext(), it.colorRes) == color
        }
        group.check(match?.radioId ?: markerColorOptions.first().radioId)
    }

    private fun getSelectedMarkerColor(group: RadioGroup, defaultColor: Int): Int {
        val selectedId = group.checkedRadioButtonId
        if (selectedId == View.NO_ID) return defaultColor
        val selected = group.findViewById<RadioButton>(selectedId)
        val colorRes = selected?.tag as? Int
        return colorRes?.let { ContextCompat.getColor(requireContext(), it) } ?: defaultColor
    }

    private fun updateRadiusLabel(binding: DialogAddTaskBinding, radius: Int) {
        binding.textRadiusValue.text =
            getString(R.string.marker_radius_value_format, radius)
    }

    private fun setupMapTapToAdd() {
        binding.mapView.overlays.add(
            MapEventsOverlay(
                object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                        if (isAddMode && p != null) {
                            isAddMode = false
                            showAddTaskDialog(p.latitude, p.longitude)
                            return true
                        }
                        return false
                    }

                    override fun longPressHelper(p: GeoPoint?): Boolean = false
                }
            )
        )
    }

    private fun showAddTaskDialog(latitude: Double, longitude: Double) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.marker_blue)

        dialogBinding.editLatitude.setText(latitude.toString())
        dialogBinding.editLongitude.setText(longitude.toString())
        dialogBinding.editTitle.setText("")
        dialogBinding.editDescription.setText("")

        val radius = 100
        dialogBinding.sliderRadius.value = radius.toFloat()
        updateRadiusLabel(dialogBinding, radius)
        dialogBinding.sliderRadius.addOnChangeListener { _, value, _ ->
            updateRadiusLabel(dialogBinding, value.toInt())
        }

        selectMarkerColor(dialogBinding.radioMarkerColor, defaultColor)

        val iconAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            markerIconOptions.map { getString(it.labelRes) }
        )
        dialogBinding.dropdownIcon.setAdapter(iconAdapter)
        val defaultIcon = resolveMarkerIconOption("pin")
        dialogBinding.dropdownIcon.setText(getString(defaultIcon.labelRes), false)
        dialogBinding.dropdownIcon.tag = defaultIcon.key
        dialogBinding.dropdownIcon.setOnItemClickListener { _, _, position, _ ->
            val option = markerIconOptions.getOrNull(position) ?: defaultIcon
            dialogBinding.dropdownIcon.tag = option.key
        }

        dialogBinding.switchNotification.isChecked = true
        dialogBinding.switchAutoRemove.isChecked = false

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.map_add_task_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.map_add_task_positive)) { _, _ ->
                val title = dialogBinding.editTitle.text?.toString()?.trim().orEmpty()
                val description = dialogBinding.editDescription.text?.toString()?.trim().orEmpty()
                val latitudeValue = dialogBinding.editLatitude.text?.toString()?.toDoubleOrNull()
                    ?: latitude
                val longitudeValue = dialogBinding.editLongitude.text?.toString()?.toDoubleOrNull()
                    ?: longitude
                val radiusValue = dialogBinding.sliderRadius.value.toInt()
                val enableNotification = dialogBinding.switchNotification.isChecked
                val autoRemove = dialogBinding.switchAutoRemove.isChecked
                val color = getSelectedMarkerColor(dialogBinding.radioMarkerColor, defaultColor)
                val iconKey = dialogBinding.dropdownIcon.tag as? String ?: defaultIcon.key

                if (title.isNotBlank()) {
                    viewModel.createTask(
                        title = title,
                        description = description,
                        latitude = latitudeValue,
                        longitude = longitudeValue,
                        address = "",
                        radius = radiusValue,
                        enableNotification = enableNotification,
                        markerColor = color,
                        markerIcon = iconKey,
                        autoRemoveAfterTrigger = autoRemove
                    )
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.map_task_added),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.map_task_title_required),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.map_add_task_negative), null)
            .show()
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.marker_blue)

        dialogBinding.editLatitude.setText(task.latitude.toString())
        dialogBinding.editLongitude.setText(task.longitude.toString())
        dialogBinding.editTitle.setText(task.title)
        dialogBinding.editDescription.setText(task.description)

        dialogBinding.sliderRadius.value = task.radius.toFloat()
        updateRadiusLabel(dialogBinding, task.radius)
        dialogBinding.sliderRadius.addOnChangeListener { _, value, _ ->
            updateRadiusLabel(dialogBinding, value.toInt())
        }

        selectMarkerColor(dialogBinding.radioMarkerColor, task.markerColor)

        val iconAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            markerIconOptions.map { getString(it.labelRes) }
        )
        dialogBinding.dropdownIcon.setAdapter(iconAdapter)
        val currentIcon = resolveMarkerIconOption(task.markerIcon)
        dialogBinding.dropdownIcon.setText(getString(currentIcon.labelRes), false)
        dialogBinding.dropdownIcon.tag = currentIcon.key
        dialogBinding.dropdownIcon.setOnItemClickListener { _, _, position, _ ->
            val option = markerIconOptions.getOrNull(position) ?: currentIcon
            dialogBinding.dropdownIcon.tag = option.key
        }

        dialogBinding.switchNotification.isChecked = task.isNotificationEnabled
        dialogBinding.switchAutoRemove.isChecked = task.autoRemoveAfterTrigger

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.map_edit_task_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.map_save_task)) { _, _ ->
                val title = dialogBinding.editTitle.text?.toString()?.trim().orEmpty()
                val description = dialogBinding.editDescription.text?.toString()?.trim().orEmpty()
                val latitudeValue = dialogBinding.editLatitude.text?.toString()?.toDoubleOrNull()
                    ?: task.latitude
                val longitudeValue = dialogBinding.editLongitude.text?.toString()?.toDoubleOrNull()
                    ?: task.longitude
                val radiusValue = dialogBinding.sliderRadius.value.toInt()
                val enableNotification = dialogBinding.switchNotification.isChecked
                val autoRemove = dialogBinding.switchAutoRemove.isChecked
                val color = getSelectedMarkerColor(dialogBinding.radioMarkerColor, defaultColor)
                val iconKey = dialogBinding.dropdownIcon.tag as? String ?: currentIcon.key

                if (title.isNotBlank()) {
                    viewModel.updateTask(
                        task.copy(
                            title = title,
                            description = description,
                            latitude = latitudeValue,
                            longitude = longitudeValue,
                            radius = radiusValue,
                            isNotificationEnabled = enableNotification,
                            autoRemoveAfterTrigger = autoRemove,
                            markerColor = color,
                            markerIcon = iconKey
                        )
                    )
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.map_task_title_required),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.map_add_task_negative), null)
            .show()
    }

    private fun showTaskDetailsDialog(task: Task) {
        val statusText = if (task.isCompleted) {
            getString(R.string.map_status_done)
        } else {
            getString(R.string.map_status_in_progress)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(task.title)
            .setMessage(
                """
                ${task.description}

                ${getString(R.string.map_task_coords)}: ${task.latitude}, ${task.longitude}
                ${getString(R.string.map_task_radius)}: ${task.radius} м
                ${getString(R.string.map_task_color)}: ${resolveMarkerColorName(task.markerColor)}
                ${getString(R.string.map_task_icon)}: ${resolveMarkerIconName(task.markerIcon)}
                ${getString(R.string.map_task_auto_remove)}: ${if (task.autoRemoveAfterTrigger) getString(R.string.map_confirm_yes) else getString(R.string.map_confirm_no)}
                ${getString(R.string.map_task_status)}: $statusText
                """.trimIndent()
            )
            .setPositiveButton(
                if (task.isCompleted) getString(R.string.map_action_restore)
                else getString(R.string.map_action_complete)
            ) { _, _ ->
                showConfirmToggleDialog(task)
            }
            .setNegativeButton(getString(R.string.map_action_delete)) { _, _ ->
                viewModel.deleteTask(task)
            }
            .setNeutralButton(getString(R.string.map_action_edit)) { _, _ ->
                showEditTaskDialog(task)
            }
            .show()
    }

    private fun showConfirmToggleDialog(task: Task) {
        val action = if (task.isCompleted) {
            getString(R.string.map_action_restore).lowercase(Locale.getDefault())
        } else {
            getString(R.string.map_action_complete).lowercase(Locale.getDefault())
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.map_confirm_title))
            .setMessage(getString(R.string.map_confirm_message, action))
            .setPositiveButton(getString(R.string.map_confirm_yes)) { _, _ ->
                viewModel.toggleTaskCompletion(task)
            }
            .setNegativeButton(getString(R.string.map_confirm_no), null)
            .show()
    }

    private fun requestLocationPermissions() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
                getCurrentLocation()
            }
            else -> {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val provider = GpsMyLocationProvider(requireContext())
            if (myLocationOverlay == null) {
                myLocationOverlay = MyLocationNewOverlay(provider, binding.mapView).apply {
                    enableMyLocation()
                    // Убираем синюю область точности под меткой пользователя
                    isDrawAccuracyEnabled = false
                    disableFollowLocation()
                    val iconDrawable = ContextCompat.getDrawable(
                        requireContext(),
                        R.drawable.ic_my_location_arrow
                    )
                    if (iconDrawable != null) {
                        val bmp = drawableToBitmap(iconDrawable)
                        setPersonIcon(bmp)
                        setDirectionIcon(bmp)
                    }
                }
                binding.mapView.overlays.add(myLocationOverlay)
            }
            provider.startLocationProvider { location, _ ->
                activity?.runOnUiThread {
                    location?.let {
                        viewModel.updateCurrentLocation(it.latitude, it.longitude)
                    }
                }
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                location?.let {
                    viewModel.updateCurrentLocation(it.latitude, it.longitude)
                    val geoPoint = GeoPoint(it.latitude, it.longitude)
                    binding.mapView.controller.animateTo(geoPoint)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "task_reminders",
                getString(R.string.map_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.map_channel_desc)
                enableVibration(true)
                enableLights(true)
                lightColor = Color.BLUE
            }

            val notificationManager = requireContext()
                .getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun setupMapInteractionListener() {
        binding.mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                // Пользователь двигает карту — отключаем автослежение
                shouldAutoCenter = false
                collapseSearchUi()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                // Зум тоже отключает автослежение, чтобы карта не прыгала
                shouldAutoCenter = false
                return false
            }
        })
    }

    private fun animateChrome() {
        val interpolator = FastOutSlowInInterpolator()

        if (binding.searchCard.visibility == View.VISIBLE) {
            binding.searchCard.alpha = 0f
            binding.searchCard.translationY = -12f
            binding.searchCard.scaleX = 0.96f
            binding.searchCard.scaleY = 0.96f
            binding.searchCard.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(260)
                .setInterpolator(interpolator)
                .start()
        }

        binding.fabAddTask.scaleX = 0.85f
        binding.fabAddTask.scaleY = 0.85f
        binding.fabAddTask.alpha = 0f
        binding.fabAddTask.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(interpolator)
            .start()

        binding.fabMyLocation.scaleX = 0.85f
        binding.fabMyLocation.scaleY = 0.85f
        binding.fabMyLocation.alpha = 0f
        binding.fabMyLocation.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(interpolator)
            .start()

        binding.searchFab.scaleX = 0.85f
        binding.searchFab.scaleY = 0.85f
        binding.searchFab.alpha = 0f
        binding.searchFab.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(interpolator)
            .start()
    }

    private fun applySearchInitialState() {
        val autoExpand = SettingsPreferences.isSearchAutoExpandEnabled(requireContext())
        isSearchExpanded = autoExpand
        binding.searchCard.visibility = if (autoExpand) View.VISIBLE else View.GONE
        binding.searchFab.visibility = if (autoExpand) View.GONE else View.VISIBLE
        binding.searchCard.alpha = if (autoExpand) 1f else 0f
        binding.searchFab.alpha = if (autoExpand) 0f else 1f
    }

    private fun collapseSearchUi() {
        if (!isSearchExpanded) return
        isSearchExpanded = false
        binding.searchCard.animate()
            .alpha(0f)
            .translationY(-12f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(220)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { binding.searchCard.visibility = View.GONE }
            .start()
        binding.searchFab.visibility = View.VISIBLE
        binding.searchFab.scaleX = 0.85f
        binding.searchFab.scaleY = 0.85f
        binding.searchFab.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        hideKeyboard()
    }

    private fun expandSearchUi() {
        if (isSearchExpanded) return
        isSearchExpanded = true
        binding.searchCard.visibility = View.VISIBLE
        binding.searchCard.alpha = 0f
        binding.searchCard.translationY = -12f
        binding.searchCard.scaleX = 0.92f
        binding.searchCard.scaleY = 0.92f
        binding.searchCard.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        binding.searchFab.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(180)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { binding.searchFab.visibility = View.GONE }
            .start()
        binding.searchQuery.requestFocus()
        showKeyboard()
    }

    private fun showKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.showSoftInput(binding.searchQuery, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(binding.searchQuery.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        // Перезапускаем слой геопозиции корректно через provider
        enableMyLocation()

        // Обновляем поведение после изменения настроек
        shouldAutoCenter = SettingsPreferences.isFollowLocationEnabled(requireContext())
        updateMapMarkers(viewModel.activeTasks.value)

        if (!isSearchExpanded) {
            binding.searchFab.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        // Останавливаем слой геопозиции, чтобы не было неконсистентного состояния
        myLocationOverlay?.disableMyLocation()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
