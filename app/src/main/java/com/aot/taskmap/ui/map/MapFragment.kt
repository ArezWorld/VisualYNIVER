package com.aot.taskmap.ui.map

import android.Manifest
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
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
import com.aot.taskmap.databinding.DialogRgbColorPickerBinding
import com.aot.taskmap.databinding.DialogTaskDetailsBinding
import com.aot.taskmap.databinding.FragmentMapBinding
import com.aot.taskmap.domain.model.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.json.JSONArray
import org.json.JSONObject
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

    private val recentPlaces = mutableListOf<Pair<String, GeoPoint>>()
    private val searchResults = mutableListOf<Pair<String, GeoPoint>>()
    private lateinit var searchAdapter: ArrayAdapter<String>
    private var searchSuggestionJob: Job? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var restoreBottomNavRunnable: Runnable? = null
    private var restoreMapHudRunnable: Runnable? = null
    private var isBottomNavigationHidden = false
    private var isMapHudHidden = false
    private val bottomNavRestoreDelayMs = 260L
    private val bottomNavHideDurationMs = 190L
    private val bottomNavShowDurationMs = 170L
    private val mapHudRestoreDelayMs = 260L
    private val mapHudHideDurationMs = 170L
    private val mapHudShowDurationMs = 150L
    private val searchHistoryPrefsName = "map_search_history"
    private val recentPlacesKey = "recent_places"
    private val recentPlacesLimit = 8

    private data class MarkerIconOption(
        val key: String,
        val radioId: Int,
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int
    )

    private data class MarkerColorOption(
        val radioId: Int,
        @ColorRes val colorRes: Int,
        @StringRes val labelRes: Int
    )

    private val markerIconOptions = listOf(
        MarkerIconOption("pin", R.id.icon_pin, R.drawable.ic_marker_pin, R.string.marker_icon_pin),
        MarkerIconOption("flag", R.id.icon_flag, R.drawable.ic_marker_flag, R.string.marker_icon_flag),
        MarkerIconOption("star", R.id.icon_star, R.drawable.ic_marker_star, R.string.marker_icon_star),
        MarkerIconOption("target", R.id.icon_target, R.drawable.ic_marker_target, R.string.marker_icon_target),
        MarkerIconOption("briefcase", R.id.icon_briefcase, R.drawable.ic_marker_briefcase, R.string.marker_icon_briefcase)
    )

    private val markerColorOptions = listOf(
        MarkerColorOption(R.id.color_blue, R.color.marker_blue, R.string.marker_color_blue),
        MarkerColorOption(R.id.color_green, R.color.marker_green, R.string.marker_color_green),
        MarkerColorOption(R.id.color_orange, R.color.marker_orange, R.string.marker_color_orange),
        MarkerColorOption(R.id.color_red, R.color.marker_red, R.string.marker_color_red),
        MarkerColorOption(R.id.color_purple, R.color.marker_purple, R.string.marker_color_purple),
        MarkerColorOption(R.id.color_cyan, R.color.marker_cyan, R.string.marker_color_cyan),
        MarkerColorOption(R.id.color_pink, R.color.marker_pink, R.string.marker_color_pink),
        MarkerColorOption(R.id.color_yellow, R.color.marker_yellow, R.string.marker_color_yellow),
        MarkerColorOption(R.id.color_lime, R.color.marker_lime, R.string.marker_color_lime),
        MarkerColorOption(R.id.color_black, R.color.marker_black, R.string.marker_color_black)
    )

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!isAdded || _binding == null) return@registerForActivityResult
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
                context?.let { safeContext ->
                    Toast.makeText(
                        safeContext,
                        getString(R.string.map_toast_location_permission),
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.setBuiltInZoomControls(false)
        binding.mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.mapView.setTilesScaledToDpi(true)
        // Отключаем повтор мира при сильном отдалении, чтобы не было двух карт на экране.
        binding.mapView.setHorizontalMapRepetitionEnabled(false)
        binding.mapView.setVerticalMapRepetitionEnabled(false)
        binding.mapView.controller.setZoom(15.0)
        applyMapPresentation()

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
            viewModel.selectTask(null)
            shouldAutoCenter = true
            getCurrentLocation()
        }

        binding.fabZoomIn.setOnClickListener {
            val nextZoom = (binding.mapView.zoomLevelDouble + 1.0).coerceAtMost(20.0)
            binding.mapView.controller.animateTo(binding.mapView.mapCenter, nextZoom, 280L)
        }
        binding.fabZoomOut.setOnClickListener {
            val nextZoom = (binding.mapView.zoomLevelDouble - 1.0).coerceAtLeast(2.0)
            binding.mapView.controller.animateTo(binding.mapView.mapCenter, nextZoom, 280L)
        }
    }

    private fun applyMapPresentation() {
        val tileSource = MapTileSources.resolveByStyle(SettingsPreferences.getMapStyle(requireContext()))
        binding.mapView.setTileSource(tileSource)
        binding.mapView.setUseDataConnection(!SettingsPreferences.isOfflineMapEnabled(requireContext()))
        binding.mapView.invalidate()
    }

    private fun animationsEnabled(): Boolean {
        return SettingsPreferences.isAnimationsEnabled(requireContext())
    }

    private fun setupSearch() {
        loadRecentPlaces()
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
                moveToSearchResult(title, point)
            }
        }

        binding.searchFab.setOnClickListener {
            expandSearchUi()
        }

        binding.searchQuery.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val query = binding.searchQuery.text?.toString()?.trim().orEmpty()
                if (query.length < 2) {
                    showRecentPlaces(query)
                }
            }
        }

        binding.searchQuery.setOnClickListener {
            val query = binding.searchQuery.text?.toString()?.trim().orEmpty()
            if (query.length < 2) {
                showRecentPlaces(query)
            }
        }

        binding.searchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim().orEmpty()
                if (query.length < 2) {
                    searchSuggestionJob?.cancel()
                    showRecentPlaces(query)
                    return
                }
                loadSearchSuggestions(query)
            }
        })
    }

    private fun loadRecentPlaces() {
        val prefs = requireContext().getSharedPreferences(searchHistoryPrefsName, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(recentPlacesKey, "[]").orEmpty()
        recentPlaces.clear()
        try {
            val jsonArray = JSONArray(rawJson)
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val lat = item.optDouble("lat", Double.NaN)
                val lon = item.optDouble("lon", Double.NaN)
                if (title.isNotBlank() && lat.isFinite() && lon.isFinite()) {
                    recentPlaces.add(title to GeoPoint(lat, lon))
                }
            }
        } catch (_: Exception) {
            // Если история повреждена, начинаем с пустого списка.
            recentPlaces.clear()
        }
    }

    private fun persistRecentPlaces() {
        val jsonArray = JSONArray()
        recentPlaces.take(recentPlacesLimit).forEach { (title, point) ->
            val item = JSONObject()
                .put("title", title)
                .put("lat", point.latitude)
                .put("lon", point.longitude)
            jsonArray.put(item)
        }
        requireContext()
            .getSharedPreferences(searchHistoryPrefsName, Context.MODE_PRIVATE)
            .edit()
            .putString(recentPlacesKey, jsonArray.toString())
            .apply()
    }

    private fun rememberRecentPlace(title: String, point: GeoPoint) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) return
        recentPlaces.removeAll { it.first.equals(cleanTitle, ignoreCase = true) }
        recentPlaces.add(0, cleanTitle to GeoPoint(point.latitude, point.longitude))
        if (recentPlaces.size > recentPlacesLimit) {
            recentPlaces.subList(recentPlacesLimit, recentPlaces.size).clear()
        }
        persistRecentPlaces()
    }

    private fun showRecentPlaces(query: String) {
        val filtered = if (query.isBlank()) {
            recentPlaces
        } else {
            recentPlaces.filter { (title, _) -> title.contains(query, ignoreCase = true) }
        }

        searchResults.clear()
        searchAdapter.clear()
        filtered.forEach { (title, point) ->
            searchResults.add(title to point)
            searchAdapter.add(title)
        }
        searchAdapter.notifyDataSetChanged()

        if (filtered.isNotEmpty() && isSearchExpanded) {
            binding.searchQuery.showDropDown()
        } else if (binding.searchQuery.isPopupShowing) {
            binding.searchQuery.dismissDropDown()
        }
    }

    private fun loadSearchSuggestions(query: String) {
        searchSuggestionJob?.cancel()
        searchSuggestionJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(if (animationsEnabled()) 260L else 120L)
            val suggestions = withContext(Dispatchers.IO) {
                if (!Geocoder.isPresent()) {
                    emptyList()
                } else {
                    try {
                        Geocoder(requireContext(), Locale.getDefault())
                            .getFromLocationName(query, 10)
                            .orEmpty()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }

            if (!isAdded || _binding == null) return@launch
            val currentQuery = binding.searchQuery.text?.toString()?.trim().orEmpty()
            if (currentQuery != query) return@launch

            val unique = linkedMapOf<String, GeoPoint>()
            suggestions.forEach { address ->
                val label = buildSuggestionLabel(address)
                if (label.isNotBlank() && !unique.containsKey(label)) {
                    unique[label] = GeoPoint(address.latitude, address.longitude)
                }
            }

            searchResults.clear()
            searchAdapter.clear()
            unique.forEach { (label, point) ->
                searchResults.add(label to point)
                searchAdapter.add(label)
            }
            searchAdapter.notifyDataSetChanged()
            if (unique.isNotEmpty() && isSearchExpanded) {
                binding.searchQuery.showDropDown()
            }
        }
    }

    private fun buildSuggestionLabel(address: android.location.Address): String {
        val city = listOfNotNull(
            address.locality,
            address.subAdminArea,
            address.adminArea
        ).firstOrNull { it.isNotBlank() }
        val country = address.countryName?.takeIf { it.isNotBlank() }
        return when {
            city != null && country != null -> "$city, $country"
            city != null -> city
            !address.featureName.isNullOrBlank() -> address.featureName
            else -> address.getAddressLine(0).orEmpty()
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
                moveToSearchResult(title, point)
            } else {
                showSearchResultsDialog(searchResults)
            }
        }
    }

    private fun moveToSearchResult(title: String, point: GeoPoint) {
        rememberRecentPlace(title, point)
        // Перемещаем карту к выбранному адресу без постановки метки
        binding.mapView.controller.animateTo(point)
    }

    private fun showSearchResultsDialog(results: List<Pair<String, GeoPoint>>) {
        val titles = results.map { it.first }.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.search_choose_title))
            .setItems(titles) { _, index ->
                val (title, point) = results[index]
                binding.searchQuery.setText(title, false)
                moveToSearchResult(title, point)
            }
            .setNegativeButton(getString(R.string.search_choose_cancel), null)
            .show()
        dialog.setCanceledOnTouchOutside(false)
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
                            if (shouldAutoCenter && viewModel.selectedTask.value == null) {
                                binding.mapView.controller.animateTo(geoPoint)
                            }
                        }
                    }
                }
                launch {
                    viewModel.selectedTask.collect { task ->
                        task ?: return@collect
                        shouldAutoCenter = false
                        val point = GeoPoint(task.latitude, task.longitude)
                        binding.mapView.controller.animateTo(point)
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

    private fun selectMarkerIcon(group: RadioGroup, key: String?) {
        val option = resolveMarkerIconOption(key)
        group.check(option.radioId)
    }

    private fun getSelectedMarkerIcon(group: RadioGroup): MarkerIconOption {
        val checkedId = group.checkedRadioButtonId
        return markerIconOptions.firstOrNull { it.radioId == checkedId } ?: markerIconOptions.first()
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

    private fun buildColoredLabel(label: String, value: String, valueColor: Int): SpannableString {
        val fullText = "$label: $value"
        return SpannableString(fullText).apply {
            setSpan(
                ForegroundColorSpan(valueColor),
                label.length + 2,
                fullText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun animateCoordinatesCard(card: View, show: Boolean) {
        if (!animationsEnabled()) {
            card.visibility = if (show) View.VISIBLE else View.GONE
            card.alpha = if (show) 1f else 0f
            card.translationY = 0f
            return
        }
        if (show) {
            card.visibility = View.VISIBLE
            card.translationY = -16f
            card.alpha = 0f
            card.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(220)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            card.animate()
                .translationY(-16f)
                .alpha(0f)
                .setDuration(180)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    card.visibility = View.GONE
                    card.translationY = 0f
                }
                .start()
        }
    }

    private fun togglePanel(panel: View, trigger: MaterialButton, expand: Boolean) {
        val iconRes = if (expand) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        trigger.setIconResource(iconRes)
        if (!animationsEnabled()) {
            panel.visibility = if (expand) View.VISIBLE else View.GONE
            return
        }
        if (expand) {
            panel.visibility = View.VISIBLE
            panel.alpha = 0f
            panel.translationY = -18f
            panel.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(320)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        } else {
            panel.animate()
                .alpha(0f)
                .translationY(-18f)
                .setDuration(220)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    panel.visibility = View.GONE
                    panel.translationY = 0f
                    panel.alpha = 1f
                }
                .start()
        }
    }

    private fun selectMarkerColor(group: RadioGroup, color: Int) {
        val match = markerColorOptions.firstOrNull {
            ContextCompat.getColor(requireContext(), it.colorRes) == color
        }
        if (match != null) {
            group.check(match.radioId)
        } else {
            group.clearCheck()
        }
    }

    private fun getSelectedMarkerColor(group: RadioGroup, fallbackColor: Int): Int {
        val selectedId = group.checkedRadioButtonId
        if (selectedId == View.NO_ID) return fallbackColor
        val selectedOption = markerColorOptions.firstOrNull { it.radioId == selectedId }
        return selectedOption?.let { ContextCompat.getColor(requireContext(), it.colorRes) }
            ?: fallbackColor
    }

    private fun updateCustomColorButton(button: MaterialButton, color: Int) {
        val hexColor = String.format("#%06X", 0xFFFFFF and color)
        button.contentDescription = "${getString(R.string.marker_pick_custom_color)} $hexColor"
        button.iconTint = ColorStateList.valueOf(color)
    }

    private fun showRgbColorPickerDialog(
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        val dialogBinding = DialogRgbColorPickerBinding.inflate(layoutInflater)

        dialogBinding.sliderRed.value = Color.red(initialColor).toFloat()
        dialogBinding.sliderGreen.value = Color.green(initialColor).toFloat()
        dialogBinding.sliderBlue.value = Color.blue(initialColor).toFloat()

        fun currentColor(): Int {
            return Color.rgb(
                dialogBinding.sliderRed.value.toInt(),
                dialogBinding.sliderGreen.value.toInt(),
                dialogBinding.sliderBlue.value.toInt()
            )
        }

        fun updatePreview() {
            val red = dialogBinding.sliderRed.value.toInt()
            val green = dialogBinding.sliderGreen.value.toInt()
            val blue = dialogBinding.sliderBlue.value.toInt()
            val color = Color.rgb(red, green, blue)
            dialogBinding.viewColorPreview.setBackgroundColor(color)
            dialogBinding.textHexColor.text = String.format("#%02X%02X%02X", red, green, blue)
            dialogBinding.textRedValue.text = getString(R.string.marker_color_red_value, red)
            dialogBinding.textGreenValue.text = getString(R.string.marker_color_green_value, green)
            dialogBinding.textBlueValue.text = getString(R.string.marker_color_blue_value, blue)
        }

        dialogBinding.sliderRed.addOnChangeListener { _, _, _ -> updatePreview() }
        dialogBinding.sliderGreen.addOnChangeListener { _, _, _ -> updatePreview() }
        dialogBinding.sliderBlue.addOnChangeListener { _, _, _ -> updatePreview() }
        updatePreview()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.marker_color_picker_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.map_confirm_yes)) { _, _ ->
                onColorSelected(currentColor())
            }
            .setNegativeButton(getString(R.string.map_add_task_negative), null)
            .show()
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
        var selectedMarkerColor = defaultColor

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

        selectMarkerColor(dialogBinding.radioMarkerColor, selectedMarkerColor)
        updateCustomColorButton(dialogBinding.buttonCustomColor, selectedMarkerColor)
        dialogBinding.radioMarkerColor.setOnCheckedChangeListener { group, _ ->
            selectedMarkerColor = getSelectedMarkerColor(group, selectedMarkerColor)
            updateCustomColorButton(dialogBinding.buttonCustomColor, selectedMarkerColor)
        }
        dialogBinding.buttonCustomColor.setOnClickListener {
            showRgbColorPickerDialog(selectedMarkerColor) { customColor ->
                selectedMarkerColor = customColor
                selectMarkerColor(dialogBinding.radioMarkerColor, customColor)
                updateCustomColorButton(dialogBinding.buttonCustomColor, customColor)
            }
        }

        val defaultIcon = resolveMarkerIconOption("pin")
        selectMarkerIcon(dialogBinding.radioMarkerIcon, defaultIcon.key)
        dialogBinding.panelColorPicker.visibility = View.GONE
        dialogBinding.panelIconPicker.visibility = View.GONE
        dialogBinding.buttonToggleColorPicker.setIconResource(R.drawable.ic_expand_more)
        dialogBinding.buttonToggleIconPicker.setIconResource(R.drawable.ic_expand_more)
        dialogBinding.buttonToggleColorPicker.setOnClickListener {
            val open = dialogBinding.panelColorPicker.visibility != View.VISIBLE
            togglePanel(
                panel = dialogBinding.panelColorPicker,
                trigger = dialogBinding.buttonToggleColorPicker,
                expand = open
            )
        }
        dialogBinding.buttonToggleIconPicker.setOnClickListener {
            val open = dialogBinding.panelIconPicker.visibility != View.VISIBLE
            togglePanel(
                panel = dialogBinding.panelIconPicker,
                trigger = dialogBinding.buttonToggleIconPicker,
                expand = open
            )
        }

        dialogBinding.switchNotification.isChecked = true
        dialogBinding.switchAutoRemove.isChecked = false
        enableDialogKeyboardDismiss(dialogBinding)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.map_add_task_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.map_add_task_positive), null)
            .setNegativeButton(getString(R.string.map_add_task_negative), null)
            .create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val title = dialogBinding.editTitle.text?.toString()?.trim().orEmpty()
                val description = dialogBinding.editDescription.text?.toString()?.trim().orEmpty()
                val latitudeValue = dialogBinding.editLatitude.text?.toString()?.toDoubleOrNull()
                    ?: latitude
                val longitudeValue = dialogBinding.editLongitude.text?.toString()?.toDoubleOrNull()
                    ?: longitude
                val radiusValue = dialogBinding.sliderRadius.value.toInt()
                val enableNotification = dialogBinding.switchNotification.isChecked
                val autoRemove = dialogBinding.switchAutoRemove.isChecked
                val iconKey = getSelectedMarkerIcon(dialogBinding.radioMarkerIcon).key

                if (title.isBlank()) {
                    dialogBinding.editTitle.error = getString(R.string.map_task_title_required)
                    dialogBinding.editTitle.requestFocus()
                    return@setOnClickListener
                }
                dialogBinding.editTitle.error = null

                viewModel.createTask(
                    title = title,
                    description = description,
                    latitude = latitudeValue,
                    longitude = longitudeValue,
                    address = "",
                    radius = radiusValue,
                    enableNotification = enableNotification,
                    markerColor = selectedMarkerColor,
                    markerIcon = iconKey,
                    category = iconKey,
                    autoRemoveAfterTrigger = autoRemove
                )
                Toast.makeText(
                    requireContext(),
                    getString(R.string.map_task_added),
                    Toast.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(false)
        animateMenuOpen(dialogBinding.root)
    }

    private fun showEditTaskDialog(task: Task) {
        val dialogBinding = DialogAddTaskBinding.inflate(layoutInflater)
        var selectedMarkerColor = task.markerColor

        dialogBinding.editLatitude.setText(task.latitude.toString())
        dialogBinding.editLongitude.setText(task.longitude.toString())
        dialogBinding.editTitle.setText(task.title)
        dialogBinding.editDescription.setText(task.description)

        val clampedRadius = task.radius.coerceIn(5, 250)
        dialogBinding.sliderRadius.value = clampedRadius.toFloat()
        updateRadiusLabel(dialogBinding, clampedRadius)
        dialogBinding.sliderRadius.addOnChangeListener { _, value, _ ->
            updateRadiusLabel(dialogBinding, value.toInt())
        }

        selectMarkerColor(dialogBinding.radioMarkerColor, selectedMarkerColor)
        updateCustomColorButton(dialogBinding.buttonCustomColor, selectedMarkerColor)
        dialogBinding.radioMarkerColor.setOnCheckedChangeListener { group, _ ->
            selectedMarkerColor = getSelectedMarkerColor(group, selectedMarkerColor)
            updateCustomColorButton(dialogBinding.buttonCustomColor, selectedMarkerColor)
        }
        dialogBinding.buttonCustomColor.setOnClickListener {
            showRgbColorPickerDialog(selectedMarkerColor) { customColor ->
                selectedMarkerColor = customColor
                selectMarkerColor(dialogBinding.radioMarkerColor, customColor)
                updateCustomColorButton(dialogBinding.buttonCustomColor, customColor)
            }
        }

        val currentIcon = resolveMarkerIconOption(task.markerIcon)
        selectMarkerIcon(dialogBinding.radioMarkerIcon, currentIcon.key)
        dialogBinding.panelColorPicker.visibility = View.GONE
        dialogBinding.panelIconPicker.visibility = View.GONE
        dialogBinding.buttonToggleColorPicker.setIconResource(R.drawable.ic_expand_more)
        dialogBinding.buttonToggleIconPicker.setIconResource(R.drawable.ic_expand_more)
        dialogBinding.buttonToggleColorPicker.setOnClickListener {
            val open = dialogBinding.panelColorPicker.visibility != View.VISIBLE
            togglePanel(
                panel = dialogBinding.panelColorPicker,
                trigger = dialogBinding.buttonToggleColorPicker,
                expand = open
            )
        }
        dialogBinding.buttonToggleIconPicker.setOnClickListener {
            val open = dialogBinding.panelIconPicker.visibility != View.VISIBLE
            togglePanel(
                panel = dialogBinding.panelIconPicker,
                trigger = dialogBinding.buttonToggleIconPicker,
                expand = open
            )
        }

        dialogBinding.switchNotification.isChecked = task.isNotificationEnabled
        dialogBinding.switchAutoRemove.isChecked = task.autoRemoveAfterTrigger
        enableDialogKeyboardDismiss(dialogBinding)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.map_edit_task_title))
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.map_save_task), null)
            .setNegativeButton(getString(R.string.map_add_task_negative), null)
            .create()
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                val title = dialogBinding.editTitle.text?.toString()?.trim().orEmpty()
                val description = dialogBinding.editDescription.text?.toString()?.trim().orEmpty()
                val latitudeValue = dialogBinding.editLatitude.text?.toString()?.toDoubleOrNull()
                    ?: task.latitude
                val longitudeValue = dialogBinding.editLongitude.text?.toString()?.toDoubleOrNull()
                    ?: task.longitude
                val radiusValue = dialogBinding.sliderRadius.value.toInt()
                val enableNotification = dialogBinding.switchNotification.isChecked
                val autoRemove = dialogBinding.switchAutoRemove.isChecked
                val iconKey = getSelectedMarkerIcon(dialogBinding.radioMarkerIcon).key

                if (title.isBlank()) {
                    dialogBinding.editTitle.error = getString(R.string.map_task_title_required)
                    dialogBinding.editTitle.requestFocus()
                    return@setOnClickListener
                }
                dialogBinding.editTitle.error = null

                viewModel.updateTask(
                    task.copy(
                        title = title,
                        description = description,
                        latitude = latitudeValue,
                        longitude = longitudeValue,
                        radius = radiusValue,
                        isNotificationEnabled = enableNotification,
                        autoRemoveAfterTrigger = autoRemove,
                        markerColor = selectedMarkerColor,
                        markerIcon = iconKey,
                        category = iconKey
                    )
                )
                dialog.dismiss()
            }
        }
        dialog.show()
        dialog.setCanceledOnTouchOutside(false)
        animateMenuOpen(dialogBinding.root)
    }

    private fun showTaskDetailsDialog(task: Task) {
        val dialogBinding = DialogTaskDetailsBinding.inflate(layoutInflater)
        val statusText = if (task.isCompleted) {
            getString(R.string.map_status_done)
        } else {
            getString(R.string.map_status_in_progress)
        }

        dialogBinding.textTitle.text = task.title
        dialogBinding.textDescription.text = task.description.ifBlank {
            getString(R.string.map_result_default)
        }
        dialogBinding.textRadius.text =
            getString(R.string.map_task_radius_trigger) + ": ${task.radius} м"
        dialogBinding.textStatus.text =
            getString(R.string.map_task_status) + ": $statusText"
        dialogBinding.textColor.text = buildColoredLabel(
            getString(R.string.map_task_color),
            resolveMarkerColorName(task.markerColor),
            task.markerColor
        )
        dialogBinding.textIcon.text =
            getString(R.string.map_task_icon) + ": ${resolveMarkerIconName(task.markerIcon)}"
        dialogBinding.textAutoRemove.text =
            getString(R.string.map_task_auto_remove) + ": " +
            if (task.autoRemoveAfterTrigger) getString(R.string.map_confirm_yes)
            else getString(R.string.map_confirm_no)
        dialogBinding.textCoordinates.text =
            "${task.latitude}, ${task.longitude}"

        var coordinatesVisible = false
        dialogBinding.buttonToggleCoordinates.setOnClickListener {
            coordinatesVisible = !coordinatesVisible
            dialogBinding.buttonToggleCoordinates.text = if (coordinatesVisible) {
                getString(R.string.map_action_hide_coordinates)
            } else {
                getString(R.string.map_action_show_coordinates)
            }
            animateCoordinatesCard(dialogBinding.cardCoordinates, coordinatesVisible)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
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
        dialog.setCanceledOnTouchOutside(false)
        animateMenuOpen(dialogBinding.root)
    }

    private fun showConfirmToggleDialog(task: Task) {
        val action = if (task.isCompleted) {
            getString(R.string.map_action_restore).lowercase(Locale.getDefault())
        } else {
            getString(R.string.map_action_complete).lowercase(Locale.getDefault())
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.map_confirm_title))
            .setMessage(getString(R.string.map_confirm_message, action))
            .setPositiveButton(getString(R.string.map_confirm_yes)) { _, _ ->
                viewModel.toggleTaskCompletion(task)
            }
            .setNegativeButton(getString(R.string.map_confirm_no), null)
            .show()
        dialog.setCanceledOnTouchOutside(false)
    }

    private fun requestLocationPermissions() {
        if (!isAdded) return
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                enableMyLocation()
                getCurrentLocation()
            }
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
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
        if (!isAdded || _binding == null) return
        val hasFine = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            val provider = GpsMyLocationProvider(requireContext())
            myLocationOverlay?.let { oldOverlay ->
                oldOverlay.disableMyLocation()
                binding.mapView.overlays.remove(oldOverlay)
            }
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
                runOnFirstFix {
                    myLocation?.let { location ->
                        viewModel.updateCurrentLocation(location.latitude, location.longitude)
                    }
                }
            }
            binding.mapView.overlays.add(myLocationOverlay)
            binding.mapView.invalidate()
        }
    }

    private fun getCurrentLocation() {
        if (!isAdded || _binding == null) return
        val hasFine = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                location?.let {
                    viewModel.updateCurrentLocation(it.latitude, it.longitude)
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
                hideBottomNavigationForMapMotion()
                hideMapHudForMotion()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                // Зум тоже отключает автослежение, чтобы карта не прыгала
                shouldAutoCenter = false
                collapseSearchUi()
                hideBottomNavigationForMapMotion()
                hideMapHudForMotion()
                return false
            }
        })
    }

    private fun mapHudViews(): List<View> {
        return listOf(
            binding.fabAddTask,
            binding.fabMyLocation,
            binding.fabZoomIn,
            binding.fabZoomOut
        )
    }

    private fun hideMapHudForMotion() {
        restoreMapHudRunnable?.let { uiHandler.removeCallbacks(it) }

        if (!isMapHudHidden) {
            isMapHudHidden = true
            val offset = 18f * resources.displayMetrics.density
            mapHudViews().forEach { hudView ->
                hudView.isEnabled = false
                hudView.isClickable = false
                if (animationsEnabled()) {
                    hudView.clearAnimation()
                    hudView.visibility = View.VISIBLE
                    hudView.animate()
                        .alpha(0f)
                        .translationY(offset)
                        .setDuration(mapHudHideDurationMs)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .withEndAction {
                            hudView.visibility = View.INVISIBLE
                            hudView.alpha = 1f
                            hudView.translationY = 0f
                        }
                        .start()
                } else {
                    hudView.visibility = View.INVISIBLE
                    hudView.alpha = 1f
                    hudView.translationY = 0f
                }
            }
        }

        val restore = Runnable { showMapHudAfterMotion() }
        restoreMapHudRunnable = restore
        uiHandler.postDelayed(restore, mapHudRestoreDelayMs)
    }

    private fun showMapHudAfterMotion() {
        restoreMapHudRunnable?.let { uiHandler.removeCallbacks(it) }
        restoreMapHudRunnable = null

        if (!isMapHudHidden) return
        isMapHudHidden = false

        val offset = 18f * resources.displayMetrics.density
        mapHudViews().forEach { hudView ->
            hudView.isEnabled = true
            hudView.isClickable = true
            if (animationsEnabled()) {
                hudView.clearAnimation()
                hudView.visibility = View.VISIBLE
                hudView.alpha = 0f
                hudView.translationY = offset
                hudView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(mapHudShowDurationMs)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .withEndAction {
                        hudView.alpha = 1f
                        hudView.translationY = 0f
                    }
                    .start()
            } else {
                hudView.visibility = View.VISIBLE
                hudView.alpha = 1f
                hudView.translationY = 0f
            }
        }
    }

    private fun enableDialogKeyboardDismiss(dialogBinding: DialogAddTaskBinding) {
        val editableFields = setOf<View>(
            dialogBinding.editTitle,
            dialogBinding.editDescription,
            dialogBinding.editLatitude,
            dialogBinding.editLongitude
        )
        installDialogTouchDismiss(dialogBinding.root, editableFields)
    }

    private fun installDialogTouchDismiss(view: View, editableFields: Set<View>) {
        if (view !is EditText) {
            view.setOnTouchListener { touchedView, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    editableFields.forEach { it.clearFocus() }
                    hideDialogKeyboard(touchedView)
                }
                false
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                installDialogTouchDismiss(view.getChildAt(index), editableFields)
            }
        }
    }

    private fun hideDialogKeyboard(anchor: View) {
        val imm = ContextCompat.getSystemService(requireContext(), InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(anchor.windowToken, 0)
    }

    private fun hideBottomNavigationForMapMotion() {
        val bottomNavigation = activity?.findViewById<View>(R.id.bottom_navigation) ?: return
        restoreBottomNavRunnable?.let { uiHandler.removeCallbacks(it) }

        if (!isBottomNavigationHidden) {
            isBottomNavigationHidden = true
            val hideDistance = resolveBottomNavigationHideDistance(bottomNavigation)
            bottomNavigation.isEnabled = false
            bottomNavigation.isClickable = false
            if (animationsEnabled()) {
                bottomNavigation.clearAnimation()
                bottomNavigation.visibility = View.VISIBLE
                bottomNavigation.alpha = 1f
                bottomNavigation.animate()
                    .translationY(hideDistance)
                    .setDuration(bottomNavHideDurationMs)
                    .setInterpolator(FastOutSlowInInterpolator())
                    .withEndAction {
                        bottomNavigation.visibility = View.INVISIBLE
                        bottomNavigation.translationY = hideDistance
                        bottomNavigation.alpha = 1f
                    }
                    .start()
            } else {
                bottomNavigation.visibility = View.INVISIBLE
                bottomNavigation.translationY = hideDistance
                bottomNavigation.alpha = 1f
            }
        }

        val restore = Runnable { showBottomNavigationAfterMotion() }
        restoreBottomNavRunnable = restore
        uiHandler.postDelayed(restore, bottomNavRestoreDelayMs)
    }

    private fun showBottomNavigationAfterMotion() {
        val bottomNavigation = activity?.findViewById<View>(R.id.bottom_navigation) ?: return
        restoreBottomNavRunnable?.let { uiHandler.removeCallbacks(it) }
        restoreBottomNavRunnable = null

        if (!isBottomNavigationHidden) return
        isBottomNavigationHidden = false

        if (animationsEnabled()) {
            bottomNavigation.visibility = View.VISIBLE
            bottomNavigation.translationY = resolveBottomNavigationHideDistance(bottomNavigation)
            bottomNavigation.alpha = 1f
            bottomNavigation.animate()
                .translationY(0f)
                .setDuration(bottomNavShowDurationMs)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    bottomNavigation.translationY = 0f
                    bottomNavigation.alpha = 1f
                }
                .start()
        } else {
            bottomNavigation.visibility = View.VISIBLE
            bottomNavigation.translationY = 0f
            bottomNavigation.alpha = 1f
        }
        bottomNavigation.isEnabled = true
        bottomNavigation.isClickable = true
    }

    private fun resolveBottomNavigationHideDistance(bottomNavigation: View): Float {
        val density = resources.displayMetrics.density
        val fallbackHeight = 56f * density
        val height = bottomNavigation.height
            .takeIf { it > 0 }
            ?: bottomNavigation.measuredHeight.takeIf { it > 0 }
            ?: fallbackHeight.toInt()
        return height + (18f * density)
    }

    private fun animateChrome() {
        if (!animationsEnabled()) {
            binding.searchCard.alpha = if (binding.searchCard.visibility == View.VISIBLE) 1f else 0f
            binding.searchCard.translationY = 0f
            binding.searchCard.scaleX = 1f
            binding.searchCard.scaleY = 1f

            binding.fabAddTask.alpha = 1f
            binding.fabAddTask.scaleX = 1f
            binding.fabAddTask.scaleY = 1f

            binding.fabMyLocation.alpha = 1f
            binding.fabMyLocation.scaleX = 1f
            binding.fabMyLocation.scaleY = 1f

            binding.searchFab.alpha = if (binding.searchFab.visibility == View.VISIBLE) 1f else 0f
            binding.searchFab.scaleX = 1f
            binding.searchFab.scaleY = 1f

            binding.fabZoomIn.alpha = 1f
            binding.fabZoomIn.scaleX = 1f
            binding.fabZoomIn.scaleY = 1f
            binding.fabZoomOut.alpha = 1f
            binding.fabZoomOut.scaleX = 1f
            binding.fabZoomOut.scaleY = 1f
            return
        }
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
                .setDuration(440)
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
            .setDuration(420)
            .setInterpolator(interpolator)
            .start()

        binding.fabMyLocation.scaleX = 0.85f
        binding.fabMyLocation.scaleY = 0.85f
        binding.fabMyLocation.alpha = 0f
        binding.fabMyLocation.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(420)
            .setInterpolator(interpolator)
            .start()

        binding.searchFab.scaleX = 0.85f
        binding.searchFab.scaleY = 0.85f
        binding.searchFab.alpha = 0f
        binding.searchFab.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(420)
            .setInterpolator(interpolator)
            .start()

        binding.fabZoomIn.scaleX = 0.78f
        binding.fabZoomIn.scaleY = 0.78f
        binding.fabZoomIn.alpha = 0f
        binding.fabZoomIn.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(430)
            .setInterpolator(interpolator)
            .start()

        binding.fabZoomOut.scaleX = 0.78f
        binding.fabZoomOut.scaleY = 0.78f
        binding.fabZoomOut.alpha = 0f
        binding.fabZoomOut.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(430)
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
        if (!animationsEnabled()) {
            binding.searchCard.clearAnimation()
            binding.searchFab.clearAnimation()
            binding.searchCard.visibility = View.GONE
            binding.searchCard.alpha = 0f
            binding.searchCard.translationY = 0f
            binding.searchCard.scaleX = 1f
            binding.searchCard.scaleY = 1f
            binding.searchFab.visibility = View.VISIBLE
            binding.searchFab.alpha = 1f
            binding.searchFab.scaleX = 1f
            binding.searchFab.scaleY = 1f
            hideKeyboard()
            return
        }
        binding.searchCard.animate()
            .alpha(0f)
            .translationY(-12f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(420)
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
            .setDuration(360)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        hideKeyboard()
    }

    private fun expandSearchUi() {
        if (isSearchExpanded) return
        isSearchExpanded = true
        if (!animationsEnabled()) {
            binding.searchCard.clearAnimation()
            binding.searchFab.clearAnimation()
            binding.searchCard.visibility = View.VISIBLE
            binding.searchCard.alpha = 1f
            binding.searchCard.translationY = 0f
            binding.searchCard.scaleX = 1f
            binding.searchCard.scaleY = 1f
            binding.searchFab.visibility = View.GONE
            binding.searchFab.alpha = 0f
            binding.searchFab.scaleX = 1f
            binding.searchFab.scaleY = 1f
            binding.searchQuery.requestFocus()
            showKeyboard()
            return
        }
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
            .setDuration(480)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
        binding.searchFab.animate()
            .alpha(0f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(360)
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

    private fun animateMenuOpen(view: View) {
        if (!animationsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = 14f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(FastOutSlowInInterpolator())
            .start()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        // Перезапускаем слой геопозиции корректно через provider
        enableMyLocation()
        showBottomNavigationAfterMotion()
        showMapHudAfterMotion()

        // Обновляем поведение после изменения настроек
        val selectedTask = viewModel.selectedTask.value
        shouldAutoCenter = selectedTask == null &&
            SettingsPreferences.isFollowLocationEnabled(requireContext())
        applyMapPresentation()
        updateMapMarkers(viewModel.activeTasks.value)
        selectedTask?.let {
            binding.mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
        }

        if (!isSearchExpanded) {
            binding.searchFab.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        // Останавливаем слой геопозиции, чтобы не было неконсистентного состояния
        myLocationOverlay?.disableMyLocation()
        showBottomNavigationAfterMotion()
        showMapHudAfterMotion()
    }

    override fun onDestroyView() {
        searchSuggestionJob?.cancel()
        restoreBottomNavRunnable?.let { uiHandler.removeCallbacks(it) }
        restoreBottomNavRunnable = null
        restoreMapHudRunnable?.let { uiHandler.removeCallbacks(it) }
        restoreMapHudRunnable = null
        super.onDestroyView()
        _binding = null
    }
}
