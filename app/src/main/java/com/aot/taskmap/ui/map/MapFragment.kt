package com.aot.taskmap.ui.map

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
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
import com.google.android.material.color.MaterialColors
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.selects.select
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
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
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class MapFragment : Fragment() {

    companion object {
        private const val MIN_MAP_ZOOM = 4.0
        private const val MAX_MAP_ZOOM = 20.0
        private val WORLD_BOUNDING_BOX = BoundingBox(85.0, 180.0, -85.0, -180.0)
    }

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MapViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private val taskOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
    private val importantPlaceOverlays = linkedMapOf<String, Marker>()
    private val importantPlaceStates = linkedMapOf<String, ImportantPlace>()
    private var searchResultOverlay: Marker? = null

    private var shouldAutoCenter = true
    private var isAddMode = false
    private var isSearchExpanded = false

    private val recentPlaces = mutableListOf<Pair<String, GeoPoint>>()
    private val searchResults = mutableListOf<Pair<String, GeoPoint>>()
    private lateinit var searchAdapter: ArrayAdapter<String>
    private var searchSuggestionJob: Job? = null
    private var importantPlacesRefreshJob: Job? = null
    private var isImportantPlacesFetchInFlight = false
    private var hasPendingImportantPlacesRefresh = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private var importantPlacesRefreshRunnable: Runnable? = null
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
    private val tilePrefetchDelayMs = 480L
    private var prefetchTilesRunnable: Runnable? = null
    private var activeTilePrefetchTask: CacheManager.CacheManagerTask? = null
    private var lastTilePrefetchKey: String? = null
    private val searchHistoryPrefsName = "map_search_history"
    private val recentPlacesKey = "recent_places"
    private val recentPlacesLimit = 8
    private val importantPlacesCache = linkedMapOf<String, ImportantPlacesCacheEntry>()
    private val importantPlaceIconCache = mutableMapOf<String, Drawable>()
    private val importantPlaceIconKeys = mutableMapOf<String, String>()
    private val importantPlacesCacheTtlMs = 3 * 60_000L
    private val importantPlacesCacheSoftReuseMs = 40_000L
    private val importantPlacesCacheMaxEntries = 24
    private val importantPlacesMotionRefreshDelayMs = 140L
    private var activeTasksCache = emptyList<Task>()
    private var completedTasksCache = emptyList<Task>()
    private val nominatimSearchUrl = "https://nominatim.openstreetmap.org/search"
    private val overpassApiUrls = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter",
        "https://overpass.openstreetmap.ru/cgi/interpreter"
    )
    private val searchHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
    }
    private val importantPlacesHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    private var lastSuccessfulOverpassEndpoint: String? = null

    private data class SearchViewport(
        val west: Double,
        val north: Double,
        val east: Double,
        val south: Double
    )

    private data class ImportantPlace(
        val title: String,
        val category: String,
        val point: GeoPoint,
        val brandKey: String? = null
    )

    private data class ImportantPlacesCacheEntry(
        val timestampMs: Long,
        val viewport: SearchViewport,
        val places: List<ImportantPlace>
    )

    private data class CuratedImportantPlace(
        val title: String,
        val category: String,
        val point: GeoPoint,
        val brandKey: String? = null,
        val minZoom: Double,
        val visibleRadiusMeters: Double
    )

    private data class ImportantPlacesConfig(
        val radiusMeters: Int,
        val maxItems: Int,
        val amenityRegex: String,
        val includeShopLayer: Boolean
    )

    private enum class PoiIconCropMode {
        FIT,
        TOP_BADGE,
        WIDE_FIT
    }

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
        MarkerIconOption("star", R.id.icon_star, R.drawable.ic_marker_pin, R.string.marker_icon_star),
        MarkerIconOption("target", R.id.icon_target, R.drawable.ic_marker_target, R.string.marker_icon_target),
        MarkerIconOption("briefcase", R.id.icon_briefcase, R.drawable.ic_marker_briefcase, R.string.marker_icon_briefcase)
    )

    private val curatedImportantPlaces = listOf(
        CuratedImportantPlace(
            title = "Новотроицкий филиал МИСиС",
            category = "university",
            point = GeoPoint(51.1949998, 58.3101847),
            brandKey = "misis",
            minZoom = 14.2,
            visibleRadiusMeters = 4500.0
        )
    )

    // Базовый размер иконок для POI: Магнит, Пятёрочка и банк.
    private val defaultPoiIconDp = 18f
    // Банковские иконки делаем немного крупнее базовых.
    private val bankPoiIconDp = 20f
    // МИСИС оставляем в более широком формате, так как логотип прямоугольный.
    private val misisPoiIconHeightDp = 28f
    private val misisPoiIconWidthDp = 56f

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
        configureMapViewportBounds()
        val hasRestoredViewport = restoreLastMapViewportOrDefault()
        applyMapPresentation()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        shouldAutoCenter = SettingsPreferences.isFollowLocationEnabled(requireContext()) &&
            !hasRestoredViewport

        requestLocationPermissions()
        setupFab()
        setupSearch()
        observeTasks()
        setupMapInteractionListener()
        setupMapTapToAdd()
        applySearchInitialState()
        animateChrome()
        scheduleImportantPlacesRefresh(immediate = true)
        scheduleTilePrefetch()
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
            getCurrentLocation(forceCenter = true)
        }

        binding.fabZoomIn.setOnClickListener {
            val nextZoom = (binding.mapView.zoomLevelDouble + 1.0).coerceAtMost(MAX_MAP_ZOOM)
            binding.mapView.controller.animateTo(binding.mapView.mapCenter, nextZoom, 280L)
        }
        binding.fabZoomOut.setOnClickListener {
            val nextZoom = (binding.mapView.zoomLevelDouble - 1.0).coerceAtLeast(MIN_MAP_ZOOM)
            binding.mapView.controller.animateTo(binding.mapView.mapCenter, nextZoom, 280L)
        }
    }

    private fun configureMapViewportBounds() {
        binding.mapView.setScrollableAreaLimitDouble(WORLD_BOUNDING_BOX)
        binding.mapView.minZoomLevel = MIN_MAP_ZOOM
        binding.mapView.maxZoomLevel = MAX_MAP_ZOOM
    }

    private fun applyMapPresentation() {
        val tileSource = MapTileSources.resolveByStyle(SettingsPreferences.getMapStyle(requireContext()))
        val currentTileSourceName = binding.mapView.tileProvider.tileSource?.name()
        if (currentTileSourceName != tileSource.name()) {
            binding.mapView.setTileSource(tileSource)
            lastTilePrefetchKey = null
        }
        // Включаем сеть, но кеш osmdroid используется всегда: скачанные оффлайн-тайлы
        // останутся доступными даже без интернета.
        binding.mapView.setUseDataConnection(true)
        binding.mapView.invalidate()
        scheduleTilePrefetch()
    }

    private fun restoreLastMapViewportOrDefault(): Boolean {
        val restoredViewport = SettingsPreferences.getLastMapViewport(requireContext())
        if (restoredViewport != null) {
            binding.mapView.controller.setZoom(restoredViewport.zoom.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM))
            binding.mapView.controller.setCenter(
                clampMapCenter(GeoPoint(restoredViewport.latitude, restoredViewport.longitude))
            )
            return true
        }

        // Резервная стартовая точка только для самого первого запуска.
        binding.mapView.controller.setZoom(15.0)
        binding.mapView.controller.setCenter(clampMapCenter(GeoPoint(51.2, 58.3)))
        return false
    }

    private fun saveCurrentMapViewport() {
        if (!isAdded || _binding == null) return
        val center = binding.mapView.mapCenter ?: return
        val latitude = center.latitude
        val longitude = center.longitude
        val zoom = binding.mapView.zoomLevelDouble
        if (!latitude.isFinite() || !longitude.isFinite() || !zoom.isFinite()) return
        SettingsPreferences.saveLastMapViewport(requireContext(), latitude, longitude, zoom)
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
                    if (query.isBlank()) {
                        clearSearchResultOverlay()
                    }
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
        val appContext = context?.applicationContext ?: return
        val viewport = captureSearchViewport()
        searchSuggestionJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(if (animationsEnabled()) 260L else 120L)
            val suggestions = withContext(Dispatchers.IO) {
                fetchSearchCandidates(
                    query = query,
                    limit = 10,
                    viewport = viewport,
                    geocoderContext = appContext,
                    userAgentPackage = appContext.packageName
                )
            }

            if (!isAdded || _binding == null) return@launch
            val currentQuery = binding.searchQuery.text?.toString()?.trim().orEmpty()
            if (currentQuery != query) return@launch

            searchResults.clear()
            searchAdapter.clear()
            suggestions.forEach { (label, point) ->
                searchResults.add(label to point)
                searchAdapter.add(label)
            }
            searchAdapter.notifyDataSetChanged()
            if (suggestions.isNotEmpty() && isSearchExpanded) {
                binding.searchQuery.showDropDown()
            } else if (binding.searchQuery.isPopupShowing) {
                binding.searchQuery.dismissDropDown()
            }
        }
    }

    private fun captureSearchViewport(): SearchViewport? {
        val box = binding.mapView.boundingBox ?: return null
        return SearchViewport(
            west = box.lonWest,
            north = box.latNorth,
            east = box.lonEast,
            south = box.latSouth
        )
    }

    private fun expandViewport(
        viewport: SearchViewport,
        factor: Double
    ): SearchViewport {
        val latSpan = (viewport.north - viewport.south).coerceAtLeast(0.01)
        val lonSpan = (viewport.east - viewport.west).coerceAtLeast(0.01)
        val latPadding = latSpan * factor
        val lonPadding = lonSpan * factor
        return SearchViewport(
            west = (viewport.west - lonPadding).coerceAtLeast(-180.0),
            north = (viewport.north + latPadding).coerceAtMost(85.0),
            east = (viewport.east + lonPadding).coerceAtMost(180.0),
            south = (viewport.south - latPadding).coerceAtLeast(-85.0)
        )
    }

    private fun scheduleImportantPlacesRefresh(immediate: Boolean = false) {
        importantPlacesRefreshRunnable?.let { uiHandler.removeCallbacks(it) }
        val refresh = Runnable {
            refreshImportantPlacesNow()
        }
        importantPlacesRefreshRunnable = refresh
        if (immediate) {
            refresh.run()
        } else {
            uiHandler.postDelayed(refresh, importantPlacesMotionRefreshDelayMs)
        }
    }

    private fun refreshImportantPlacesNow() {
        importantPlacesRefreshRunnable = null
        if (isImportantPlacesFetchInFlight) {
            hasPendingImportantPlacesRefresh = true
            return
        }
        if (!isAdded || _binding == null) return
        val safeContext = context ?: return
        if (!SettingsPreferences.isHighlightImportantPlacesEnabled(safeContext)) {
            clearImportantPlaceOverlays()
            return
        }

        val viewport = captureSearchViewport() ?: return
        val fetchViewport = expandViewport(viewport, 0.55)
        val zoom = binding.mapView.zoomLevelDouble
        val config = resolveImportantPlacesConfig(zoom)
        val appContext = safeContext.applicationContext
        val userAgentPackage = appContext.packageName
        val cacheKey = buildImportantPlacesCacheKey(fetchViewport, zoom, config)
        val now = System.currentTimeMillis()
        val cachedEntry = importantPlacesCache[cacheKey]
        val cachedPlaces = cachedEntry
            ?.takeIf {
                now - it.timestampMs <= importantPlacesCacheTtlMs &&
                    doViewportsOverlap(it.viewport, fetchViewport)
            }
            ?.places
        val reusableCachedPlaces = collectCachedImportantPlacesForViewport(
            viewport = fetchViewport,
            now = now,
            paddingFactor = 0.95
        )
        val visibleCachedPlaces = dedupeImportantPlaces(reusableCachedPlaces + cachedPlaces.orEmpty())

        // Сначала показываем всё, что уже есть локально для видимой области,
        // чтобы значки не исчезали при сдвиге камеры и не зависели от центра карты.
        val immediatePlaces = mergeCuratedImportantPlaces(
            places = visibleCachedPlaces,
            viewport = expandViewport(viewport, 0.28),
            zoom = zoom
        ).take(config.maxItems)
        if (immediatePlaces.isNotEmpty()) {
            updateImportantPlaceOverlays(
                places = immediatePlaces,
                viewport = viewport,
                maxVisibleItems = config.maxItems
            )
        }
        if (cachedEntry != null &&
            now - cachedEntry.timestampMs <= importantPlacesCacheSoftReuseMs &&
            doViewportsOverlap(cachedEntry.viewport, fetchViewport)
        ) {
            return
        }

        isImportantPlacesFetchInFlight = true
        importantPlacesRefreshJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                delay(if (importantPlaceStates.isEmpty()) 8L else if (animationsEnabled()) 18L else 6L)
                val places = fetchImportantPlaces(
                    config = config,
                    userAgentPackage = userAgentPackage,
                    viewport = fetchViewport
                )
                if (!isAdded || _binding == null) return@launch
                if (!SettingsPreferences.isHighlightImportantPlacesEnabled(appContext)) {
                    clearImportantPlaceOverlays()
                    return@launch
                }
                if (places.isNotEmpty()) {
                    saveImportantPlacesCache(cacheKey, fetchViewport, places)
                }
                val effectivePlaces = when {
                    places.isNotEmpty() -> places
                    visibleCachedPlaces.isNotEmpty() -> visibleCachedPlaces
                    !cachedPlaces.isNullOrEmpty() -> cachedPlaces
                    else -> emptyList()
                }
                val mergedPlaces = mergeCuratedImportantPlaces(
                    places = effectivePlaces,
                    viewport = expandViewport(viewport, 0.28),
                    zoom = zoom
                ).take(config.maxItems)
                updateImportantPlaceOverlays(
                    places = mergedPlaces,
                    viewport = viewport,
                    maxVisibleItems = config.maxItems
                )
            } finally {
                isImportantPlacesFetchInFlight = false
                if (hasPendingImportantPlacesRefresh) {
                    hasPendingImportantPlacesRefresh = false
                    scheduleImportantPlacesRefresh(immediate = true)
                }
            }
        }
    }

    private fun scheduleTilePrefetch() {
        prefetchTilesRunnable?.let { uiHandler.removeCallbacks(it) }
        prefetchTilesRunnable = null
        // Защита от TileSourcePolicyException на ряде источников:
        // отключаем background prefetch тайлов и оставляем только обычную загрузку карты.
        activeTilePrefetchTask?.cancel(true)
        activeTilePrefetchTask = null
        lastTilePrefetchKey = null
    }

    private fun prefetchVisibleTiles() {
        if (!isAdded || _binding == null) return
        val tileSource = binding.mapView.tileProvider.tileSource
        if (!isTilePrefetchAllowed(tileSource)) return
        val center = binding.mapView.mapCenter ?: return
        val zoomInt = binding.mapView.zoomLevelDouble.roundToInt().coerceIn(3, 19)
        val tileSourceName = tileSource?.name().orEmpty()
        val latBucket = (center.latitude * 30.0).roundToInt()
        val lonBucket = (center.longitude * 30.0).roundToInt()
        val prefetchKey = "$tileSourceName:$zoomInt:$latBucket:$lonBucket"
        if (prefetchKey == lastTilePrefetchKey) return
        lastTilePrefetchKey = prefetchKey

        val box = binding.mapView.boundingBox ?: return
        val latSpan = (box.latNorth - box.latSouth).coerceAtLeast(0.01)
        val lonSpan = (box.lonEast - box.lonWest).coerceAtLeast(0.01)
        val latPadding = (latSpan * 0.45).coerceAtLeast(0.006)
        val lonPadding = (lonSpan * 0.45).coerceAtLeast(0.006)
        val expanded = BoundingBox(
            (box.latNorth + latPadding).coerceAtMost(85.0),
            (box.lonEast + lonPadding).coerceAtMost(180.0),
            (box.latSouth - latPadding).coerceAtLeast(-85.0),
            (box.lonWest - lonPadding).coerceAtLeast(-180.0)
        )

        val minZoom = (zoomInt - 1).coerceAtLeast(3)
        val maxZoom = (zoomInt + 1).coerceAtMost(19)
        activeTilePrefetchTask?.cancel(true)
        val cacheManager = runCatching { CacheManager(binding.mapView) }.getOrNull() ?: return
        activeTilePrefetchTask = runCatching {
            cacheManager.downloadAreaAsyncNoUI(
                requireContext().applicationContext,
                expanded,
                minZoom,
                maxZoom,
                object : CacheManager.CacheManagerCallback {
                    override fun onTaskComplete() = Unit
                    override fun updateProgress(
                        progress: Int,
                        currentZoomLevel: Int,
                        zoomMin: Int,
                        zoomMax: Int
                    ) = Unit
                    override fun downloadStarted() = Unit
                    override fun setPossibleTilesInArea(total: Int) = Unit
                    override fun onTaskFailed(errors: Int) = Unit
                }
            )
        }.getOrNull()
    }

    private fun isTilePrefetchAllowed(tileSource: ITileSource?): Boolean {
        val onlineSource = tileSource as? OnlineTileSourceBase ?: return false
        val policy = onlineSource.tileSourcePolicy
        return policy.acceptsBulkDownload() && policy.acceptsPreventive()
    }

    private fun resolveImportantPlacesConfig(zoom: Double): ImportantPlacesConfig {
        return when {
            zoom >= 17.5 -> ImportantPlacesConfig(
                radiusMeters = 650,
                maxItems = 18,
                amenityRegex = "bank|atm",
                includeShopLayer = true
            )
            zoom >= 16.0 -> ImportantPlacesConfig(
                radiusMeters = 1000,
                maxItems = 14,
                amenityRegex = "bank|atm",
                includeShopLayer = true
            )
            zoom >= 14.5 -> ImportantPlacesConfig(
                radiusMeters = 1600,
                maxItems = 10,
                amenityRegex = "bank|atm",
                includeShopLayer = true
            )
            zoom >= 12.5 -> ImportantPlacesConfig(
                radiusMeters = 2200,
                maxItems = 8,
                amenityRegex = "bank|atm",
                includeShopLayer = true
            )
            zoom >= 10.0 -> ImportantPlacesConfig(
                radiusMeters = 2800,
                maxItems = 6,
                amenityRegex = "bank|atm",
                includeShopLayer = true
            )
            else -> ImportantPlacesConfig(
                radiusMeters = 3600,
                maxItems = 5,
                amenityRegex = "bank|atm",
                includeShopLayer = true
            )
        }
    }

    private fun buildImportantPlacesCacheKey(
        viewport: SearchViewport,
        zoom: Double,
        config: ImportantPlacesConfig
    ): String {
        val latitude = (viewport.north + viewport.south) / 2.0
        val longitude = (viewport.west + viewport.east) / 2.0
        val latSpan = (viewport.north - viewport.south).coerceAtLeast(0.01)
        val lonSpan = (viewport.east - viewport.west).coerceAtLeast(0.01)
        val cellSizeDegrees = max(0.002, max(latSpan, lonSpan) * 0.25)
        val latBucket = (latitude / cellSizeDegrees).roundToLong()
        val lonBucket = (longitude / cellSizeDegrees).roundToLong()
        val zoomBucket = (zoom * 2.0).roundToLong()
        val spanBucket = ((latSpan + lonSpan) * 100.0).roundToLong()
        return listOf(
            zoomBucket.toString(),
            latBucket.toString(),
            lonBucket.toString(),
            spanBucket.toString(),
            config.radiusMeters.toString(),
            config.maxItems.toString(),
            config.includeShopLayer.toString()
        ).joinToString(":")
    }

    private fun saveImportantPlacesCache(
        cacheKey: String,
        viewport: SearchViewport,
        places: List<ImportantPlace>
    ) {
        importantPlacesCache[cacheKey] = ImportantPlacesCacheEntry(
            timestampMs = System.currentTimeMillis(),
            viewport = viewport,
            places = places
        )
        while (importantPlacesCache.size > importantPlacesCacheMaxEntries) {
            val oldestKey = importantPlacesCache.entries.firstOrNull()?.key ?: break
            importantPlacesCache.remove(oldestKey)
        }
    }

    private suspend fun fetchImportantPlaces(
        config: ImportantPlacesConfig,
        userAgentPackage: String,
        viewport: SearchViewport?
    ): List<ImportantPlace> = supervisorScope {
        val box = viewport ?: return@supervisorScope emptyList()
        val south = minOf(box.south, box.north)
        val north = maxOf(box.south, box.north)
        val west = minOf(box.west, box.east)
        val east = maxOf(box.west, box.east)
        val shopLayer = if (config.includeShopLayer) {
            """
              node($south,$west,$north,$east)["shop"~"supermarket|convenience"];
              way($south,$west,$north,$east)["shop"~"supermarket|convenience"];
            """.trimIndent()
        } else {
            ""
        }

        val query = """
            [out:json][timeout:8];
            (
              node($south,$west,$north,$east)["amenity"~"${config.amenityRegex}"];
              way($south,$west,$north,$east)["amenity"~"${config.amenityRegex}"];
              $shopLayer
            );
            out center;
        """.trimIndent()

        val endpoints = prioritizedOverpassApiUrls()
        if (endpoints.isEmpty()) return@supervisorScope emptyList()

        val overpassRequests = endpoints.map { endpoint ->
            async(Dispatchers.IO) {
                val places = fetchImportantPlacesFromEndpoint(
                    endpoint = endpoint,
                    query = query,
                    maxItems = config.maxItems,
                    userAgentPackage = userAgentPackage
                )
                endpoint to places
            }
        }
        val fallbackRequest = async(Dispatchers.IO) {
            "nominatim_fallback" to fetchImportantPlacesFromNominatimFallback(
                viewport = viewport,
                maxItems = config.maxItems,
                userAgentPackage = userAgentPackage
            )
        }

        val pending = (overpassRequests + fallbackRequest).toMutableList()
        var firstCompletedNonNull: List<ImportantPlace>? = null
        while (pending.isNotEmpty()) {
            val (endpoint, places) = select<Pair<String, List<ImportantPlace>?>> {
                pending.forEach { deferred ->
                    deferred.onAwait { result -> result }
                }
            }
            pending.removeAll { it.isCompleted }

            if (places != null) {
                if (places.isNotEmpty()) {
                    if (endpoint != "nominatim_fallback") {
                        lastSuccessfulOverpassEndpoint = endpoint
                    }
                    pending.forEach { it.cancel() }
                    return@supervisorScope places
                }
                if (firstCompletedNonNull == null) {
                    firstCompletedNonNull = places
                }
            }
        }
        return@supervisorScope firstCompletedNonNull.orEmpty()
    }

    private suspend fun fetchImportantPlacesFromNominatimFallback(
        viewport: SearchViewport?,
        maxItems: Int,
        userAgentPackage: String
    ): List<ImportantPlace> = supervisorScope {
        val box = viewport ?: return@supervisorScope emptyList()
        val endpoint = nominatimSearchUrl.toHttpUrlOrNull() ?: return@supervisorScope emptyList()
        val queryPairs = listOf(
            "магнит" to "supermarket",
            "пятерочка" to "supermarket",
            "bank" to "bank",
            "atm" to "bank"
        )
        val perQueryLimit = (maxItems / 2).coerceIn(3, 8)

        val deferredQueries = queryPairs.map { (queryText, fallbackCategory) ->
            async(Dispatchers.IO) {
                val requestUrl = endpoint.newBuilder()
                    .addQueryParameter("q", queryText)
                    .addQueryParameter("format", "jsonv2")
                    .addQueryParameter("addressdetails", "1")
                    .addQueryParameter("limit", perQueryLimit.toString())
                    .addQueryParameter("bounded", "1")
                    .addQueryParameter(
                        "viewbox",
                        "${box.west},${box.north},${box.east},${box.south}"
                    )
                    .addQueryParameter("accept-language", "ru")
                    .build()

                val request = Request.Builder()
                    .url(requestUrl)
                    .header("User-Agent", "$userAgentPackage/important-places-fallback")
                    .build()

                runCatching {
                    importantPlacesHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use emptyList()
                        val body = response.body?.string().orEmpty()
                        parseNominatimFallbackResponse(body, fallbackCategory, perQueryLimit)
                    }
                }.getOrDefault(emptyList())
            }
        }

        val aggregated = linkedMapOf<String, ImportantPlace>()
        deferredQueries.forEach { deferred ->
            deferred.await().forEach { place ->
                val key = "${place.point.latitude.format(5)}:${place.point.longitude.format(5)}:${place.category}"
                aggregated[key] = place
            }
        }
        return@supervisorScope aggregated.values.take(maxItems)
    }

    private fun parseNominatimFallbackResponse(
        body: String,
        fallbackCategory: String,
        maxItems: Int
    ): List<ImportantPlace> {
        if (body.isBlank()) return emptyList()
        val array = JSONArray(body)
        val result = mutableListOf<ImportantPlace>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            if (!lat.isFinite() || !lon.isFinite()) continue

            val clazz = item.optString("class").lowercase(Locale.ROOT)
            val type = item.optString("type").lowercase(Locale.ROOT)
            val category = mapNominatimCategory(clazz, type, fallbackCategory)
            val title = item.optString("name").trim().ifBlank {
                resolveImportantPlaceCategoryLabel(category)
            }
            val brandKey = resolveImportantPlaceBrandKey(
                title = title,
                brand = item.optString("name"),
                category = category
            )
            if (!isSupportedImportantPlace(category, brandKey)) continue

            result += ImportantPlace(
                title = title,
                category = category,
                point = GeoPoint(lat, lon),
                brandKey = brandKey
            )
            if (result.size >= maxItems) break
        }
        return result
    }

    private fun mapNominatimCategory(
        clazz: String,
        type: String,
        fallbackCategory: String
    ): String {
        val raw = listOf(clazz, type, fallbackCategory)
            .joinToString(":")
            .lowercase(Locale.ROOT)

        return when {
            raw.contains("supermarket") || raw.contains("shop") || raw.contains("mall") -> "supermarket"
            raw.contains("bank") || raw.contains("atm") -> "bank"
            else -> fallbackCategory
        }
    }

    private fun isSupportedImportantPlace(category: String, brandKey: String?): Boolean {
        if (brandKey == "magnit" || brandKey == "pyaterochka" || brandKey == "misis") {
            return true
        }
        return category == "bank" || category == "atm"
    }

    private fun prioritizedOverpassApiUrls(): List<String> {
        val preferred = lastSuccessfulOverpassEndpoint
        if (preferred.isNullOrBlank()) return overpassApiUrls
        if (!overpassApiUrls.contains(preferred)) return overpassApiUrls

        return buildList {
            add(preferred)
            overpassApiUrls.forEach { endpoint ->
                if (endpoint != preferred) add(endpoint)
            }
        }
    }

    private fun fetchImportantPlacesFromEndpoint(
        endpoint: String,
        query: String,
        maxItems: Int,
        userAgentPackage: String
    ): List<ImportantPlace>? {
        val request = Request.Builder()
            .url(endpoint)
            .post(query.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .header("User-Agent", "$userAgentPackage/important-places")
            .build()

        return runCatching {
            importantPlacesHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().orEmpty()
                parseImportantPlacesResponse(body, maxItems)
            }
        }.getOrNull()
    }

    private fun parseImportantPlacesResponse(
        body: String,
        maxItems: Int
    ): List<ImportantPlace> {
        if (body.isBlank()) return emptyList()
        val json = JSONObject(body)
        val elements = json.optJSONArray("elements") ?: return emptyList()
        val unique = linkedMapOf<String, ImportantPlace>()

        for (index in 0 until elements.length()) {
            val item = elements.optJSONObject(index) ?: continue
            val center = item.optJSONObject("center")
            val nodeLat = item.optDouble("lat", Double.NaN)
            val nodeLon = item.optDouble("lon", Double.NaN)
            val lat = if (nodeLat.isFinite()) {
                nodeLat
            } else {
                center?.optDouble("lat", Double.NaN) ?: Double.NaN
            }
            val lon = if (nodeLon.isFinite()) {
                nodeLon
            } else {
                center?.optDouble("lon", Double.NaN) ?: Double.NaN
            }
            if (!lat.isFinite() || !lon.isFinite()) continue

            val tags = item.optJSONObject("tags") ?: JSONObject()
            val category = tags.optString("amenity")
                .ifBlank { tags.optString("shop") }
                .ifBlank { tags.optString("tourism") }
                .ifBlank { tags.optString("leisure") }
                .lowercase(Locale.ROOT)
            if (category.isBlank()) continue

            val brand = tags.optString("brand").trim()
                .ifBlank { tags.optString("operator").trim() }
            val title = tags.optString("name").trim()
                .ifBlank { brand }
                .ifBlank { resolveImportantPlaceCategoryLabel(category) }
            val brandKey = resolveImportantPlaceBrandKey(
                title = title,
                brand = brand,
                category = category
            )
            if (!isSupportedImportantPlace(category, brandKey)) continue
            val key = "${lat.format(5)}:${lon.format(5)}:$category"
            unique[key] = ImportantPlace(
                title = title,
                category = category,
                point = GeoPoint(lat, lon),
                brandKey = brandKey
            )
            if (unique.size >= maxItems) break
        }

        return unique.values.toList()
    }

    private fun mergeCuratedImportantPlaces(
        places: List<ImportantPlace>,
        viewport: SearchViewport?,
        zoom: Double
    ): List<ImportantPlace> {
        val merged = linkedMapOf<String, ImportantPlace>()
        val visiblePlaces = filterPlacesToViewport(places, viewport)
        val viewportCenter = viewport?.let { currentViewport ->
            GeoPoint(
                (currentViewport.north + currentViewport.south) / 2.0,
                (currentViewport.east + currentViewport.west) / 2.0
            )
        }
        visiblePlaces.forEach { place ->
            val key = buildImportantPlaceKey(place)
            merged[key] = place
        }
        curatedImportantPlaces.forEach { curated ->
            if (zoom < curated.minZoom) return@forEach
            if (!isPointInsideViewport(curated.point, viewport, paddingFactor = 0.2)) return@forEach
            if (viewportCenter != null &&
                curated.point.distanceToAsDouble(viewportCenter) > curated.visibleRadiusMeters
            ) {
                return@forEach
            }
            val place = ImportantPlace(
                title = curated.title,
                category = curated.category,
                point = curated.point,
                brandKey = curated.brandKey
            )
            merged[buildImportantPlaceKey(place)] = place
        }
        return merged.values.toList()
    }

    private fun collectCachedImportantPlacesForViewport(
        viewport: SearchViewport,
        now: Long = System.currentTimeMillis(),
        paddingFactor: Double = 0.75
    ): List<ImportantPlace> {
        val unique = linkedMapOf<String, ImportantPlace>()
        importantPlacesCache.entries
            .sortedByDescending { it.value.timestampMs }
            .forEach { (_, cachedEntry) ->
                if (now - cachedEntry.timestampMs > importantPlacesCacheTtlMs) return@forEach
                if (!doViewportsOverlap(cachedEntry.viewport, viewport, paddingFactor = 0.45)) {
                    return@forEach
                }
                filterPlacesToViewport(
                    places = cachedEntry.places,
                    viewport = viewport,
                    paddingFactor = paddingFactor
                ).forEach { place ->
                    val key = buildImportantPlaceKey(place)
                    unique[key] = place
                }
            }
        return unique.values.toList()
    }

    private fun dedupeImportantPlaces(places: List<ImportantPlace>): List<ImportantPlace> {
        val unique = linkedMapOf<String, ImportantPlace>()
        places.forEach { place ->
            unique[buildImportantPlaceKey(place)] = place
        }
        return unique.values.toList()
    }

    private fun filterPlacesToViewport(
        places: List<ImportantPlace>,
        viewport: SearchViewport?,
        paddingFactor: Double = 0.18
    ): List<ImportantPlace> {
        if (viewport == null) return places
        return places.filter { place ->
            isPointInsideViewport(place.point, viewport, paddingFactor = paddingFactor)
        }
    }

    private fun buildImportantPlaceKey(place: ImportantPlace): String {
        val normalizedTitle = place.title
            .lowercase(Locale.ROOT)
            .replace("\\s+".toRegex(), " ")
            .trim()
        return buildString {
            append(place.point.latitude.format(5))
            append(':')
            append(place.point.longitude.format(5))
            append(':')
            append(place.category)
            append(':')
            append(place.brandKey.orEmpty())
            append(':')
            append(normalizedTitle)
        }
    }

    // Небольшой запас по краям экрана нужен, чтобы значки не "мигали" при минимальном сдвиге карты.
    private fun isPointInsideViewport(
        point: GeoPoint,
        viewport: SearchViewport?,
        paddingFactor: Double = 0.0
    ): Boolean {
        if (viewport == null) return true
        val latPadding = (viewport.north - viewport.south).coerceAtLeast(0.0) * paddingFactor
        val lonPadding = (viewport.east - viewport.west).coerceAtLeast(0.0) * paddingFactor
        val south = viewport.south - latPadding
        val north = viewport.north + latPadding
        val west = viewport.west - lonPadding
        val east = viewport.east + lonPadding
        return point.latitude in south..north && point.longitude in west..east
    }

    private fun doViewportsOverlap(
        first: SearchViewport,
        second: SearchViewport,
        paddingFactor: Double = 0.08
    ): Boolean {
        val expandedFirst = expandViewport(first, paddingFactor)
        val expandedSecond = expandViewport(second, paddingFactor)
        val horizontalOverlap =
            expandedFirst.west <= expandedSecond.east && expandedSecond.west <= expandedFirst.east
        val verticalOverlap =
            expandedFirst.south <= expandedSecond.north && expandedSecond.south <= expandedFirst.north
        return horizontalOverlap && verticalOverlap
    }

    private fun resolveImportantPlaceCategoryLabel(category: String): String {
        return when (category) {
            "hospital", "clinic" -> "Больница"
            "pharmacy" -> "Аптека"
            "police" -> "Полиция"
            "fire_station" -> "Пожарная часть"
            "fuel" -> "АЗС"
            "bus_station" -> "Автовокзал"
            "school" -> "Школа"
            "college" -> "Колледж"
            "university" -> "Университет"
            "kindergarten" -> "Детский сад"
            "supermarket" -> "Супермаркет"
            "convenience", "mall", "department_store" -> "Магазин"
            "cafe", "restaurant", "fast_food" -> "Кафе"
            "bank", "atm" -> "Банк"
            "post_office" -> "Почта"
            "library" -> "Библиотека"
            "parking" -> "Парковка"
            "museum", "attraction", "viewpoint" -> "Достопримечательность"
            "park" -> "Парк"
            else -> "Важное место"
        }
    }

    private fun resolveImportantPlaceBrandKey(
        title: String,
        brand: String?,
        category: String
    ): String? {
        val probe = buildString {
            append(title)
            append(' ')
            append(brand.orEmpty())
        }.lowercase(Locale.ROOT)

        if (probe.contains("мисис") || probe.contains("misis")) {
            return "misis"
        }

        if (category == "bank" || category == "atm") {
            return "bank_generic"
        }

        return when {
            probe.contains("магнит") || probe.contains("magnit") -> "magnit"
            probe.contains("пятероч") || probe.contains("пятёроч") ||
                probe.contains("pyater") || probe.contains("5ka") ->
                "pyaterochka"
            else -> null
        }
    }

    private fun updateImportantPlaceOverlays(
        places: List<ImportantPlace>,
        viewport: SearchViewport? = captureSearchViewport(),
        maxVisibleItems: Int = Int.MAX_VALUE
    ) {
        if (!isAdded || _binding == null) return
        if (!SettingsPreferences.isHighlightImportantPlacesEnabled(requireContext())) {
            clearImportantPlaceOverlays()
            return
        }

        val retainedPlaces = if (viewport == null) {
            emptyList()
        } else {
            importantPlaceStates.values.filter { existing ->
                isPointInsideViewport(existing.point, viewport, paddingFactor = 1.12)
            }
        }
        val finalPlaces = limitImportantPlacesForDisplay(
            places = dedupeImportantPlaces(retainedPlaces + places),
            viewport = viewport,
            maxVisibleItems = maxVisibleItems
        )
        val desiredKeys = finalPlaces.mapTo(linkedSetOf()) { buildImportantPlaceKey(it) }
        var hasChanges = false

        importantPlaceOverlays.entries
            .toList()
            .forEach { (key, marker) ->
                if (desiredKeys.contains(key)) return@forEach
                binding.mapView.overlays.remove(marker)
                importantPlaceOverlays.remove(key)
                importantPlaceStates.remove(key)
                importantPlaceIconKeys.remove(key)
                hasChanges = true
            }

        finalPlaces.forEach { place ->
            val key = buildImportantPlaceKey(place)
            val marker = importantPlaceOverlays[key]
            val snippet = resolveImportantPlaceCategoryLabel(place.category)
            val iconKey = buildImportantPlaceIconKey(place)
            if (marker == null) {
                val newMarker = Marker(binding.mapView).apply {
                    position = place.point
                    title = place.title
                    this.snippet = snippet
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    icon = buildImportantPlaceIconDrawable(place)
                }
                importantPlaceOverlays[key] = newMarker
                importantPlaceStates[key] = place
                importantPlaceIconKeys[key] = iconKey
                binding.mapView.overlays.add(newMarker)
                hasChanges = true
            } else {
                val previousPlace = importantPlaceStates[key]
                if (previousPlace == null ||
                    previousPlace.point.latitude != place.point.latitude ||
                    previousPlace.point.longitude != place.point.longitude
                ) {
                    marker.position = place.point
                    hasChanges = true
                }
                if (marker.title != place.title) {
                    marker.title = place.title
                    hasChanges = true
                }
                if (marker.snippet != snippet) {
                    marker.snippet = snippet
                    hasChanges = true
                }
                if (importantPlaceIconKeys[key] != iconKey) {
                    marker.icon = buildImportantPlaceIconDrawable(place)
                    importantPlaceIconKeys[key] = iconKey
                    hasChanges = true
                }
                importantPlaceStates[key] = place
            }
        }

        if (hasChanges) {
            binding.mapView.invalidate()
        }
    }

    private fun clearImportantPlaceOverlays() {
        if (importantPlaceOverlays.isEmpty()) return
        binding.mapView.overlays.removeAll(importantPlaceOverlays.values.toList())
        importantPlaceOverlays.clear()
        importantPlaceStates.clear()
        importantPlaceIconKeys.clear()
        binding.mapView.invalidate()
    }

    private fun limitImportantPlacesForDisplay(
        places: List<ImportantPlace>,
        viewport: SearchViewport?,
        maxVisibleItems: Int
    ): List<ImportantPlace> {
        if (places.size <= maxVisibleItems) return places
        val center = viewport?.let {
            GeoPoint(
                (it.north + it.south) / 2.0,
                (it.east + it.west) / 2.0
            )
        } ?: binding.mapView.mapCenter ?: return places.take(maxVisibleItems)

        return places
            .sortedWith(
                compareBy<ImportantPlace> { resolveImportantPlacePriority(it) }
                    .thenBy { it.point.distanceToAsDouble(center) }
            )
            .take(maxVisibleItems)
    }

    private fun resolveImportantPlacePriority(place: ImportantPlace): Int {
        return when {
            place.brandKey == "misis" || place.title.contains("МИСиС", ignoreCase = true) -> 0
            place.brandKey == "magnit" || place.brandKey == "pyaterochka" -> 1
            place.category == "bank" || place.category == "atm" || place.brandKey == "bank_generic" -> 2
            else -> 3
        }
    }

    private fun resolveImportantPlaceIconSpec(place: ImportantPlace): Pair<Int, PoiIconCropMode> {
        return when (place.brandKey) {
            "magnit" -> R.drawable.ic_poi_brand_magnit to PoiIconCropMode.FIT
            "pyaterochka" -> R.drawable.ic_poi_brand_pyaterochka to PoiIconCropMode.TOP_BADGE
            "bank_generic" -> R.drawable.ic_poi_bank_generic to PoiIconCropMode.FIT
            "misis" -> R.drawable.ic_poi_brand_misis to PoiIconCropMode.WIDE_FIT
            else -> {
                when {
                    place.title.contains("МИСиС", ignoreCase = true) ->
                        R.drawable.ic_poi_brand_misis to PoiIconCropMode.WIDE_FIT
                    place.category == "bank" || place.category == "atm" ->
                        R.drawable.ic_poi_bank_generic to PoiIconCropMode.FIT
                    else ->
                        R.drawable.ic_poi_default_pin to PoiIconCropMode.FIT
                }
            }
        }
    }

    private fun buildImportantPlaceIconKey(place: ImportantPlace): String {
        val (iconWidthPx, iconHeightPx) = resolveImportantPlaceIconSizePx(place)
        val (iconRes, cropMode) = resolveImportantPlaceIconSpec(place)
        return "$iconRes:${iconWidthPx}x${iconHeightPx}:$cropMode"
    }

    private fun resolveImportantPlaceIconSizePx(place: ImportantPlace): Pair<Int, Int> {
        val density = resources.displayMetrics.density
        return if (place.brandKey == "misis" || place.title.contains("МИСиС", ignoreCase = true)) {
            val width = (misisPoiIconWidthDp * density).roundToInt().coerceAtLeast(44)
            val height = (misisPoiIconHeightDp * density).roundToInt().coerceAtLeast(22)
            width to height
        } else if (place.category == "bank" || place.category == "atm" || place.brandKey == "bank_generic") {
            val side = (bankPoiIconDp * density).roundToInt().coerceAtLeast(22)
            side to side
        } else {
            // Все обычные POI выравниваем по размеру значка Магнита.
            val side = (defaultPoiIconDp * density).roundToInt().coerceAtLeast(20)
            side to side
        }
    }

    private fun buildImportantPlaceIconDrawable(place: ImportantPlace): Drawable? {
        val (iconWidthPx, iconHeightPx) = resolveImportantPlaceIconSizePx(place)
        val (iconRes, cropMode) = resolveImportantPlaceIconSpec(place)
        val cacheKey = "$iconRes:${iconWidthPx}x${iconHeightPx}:$cropMode"
        importantPlaceIconCache[cacheKey]
            ?.constantState
            ?.newDrawable(resources)
            ?.mutate()
            ?.let { return it }

        val source = ContextCompat.getDrawable(
            requireContext(),
            iconRes
        )?.mutate() ?: return null

        val bitmap = when (source) {
            is BitmapDrawable -> renderPoiBitmap(
                source = source.bitmap ?: return null,
                iconWidthPx = iconWidthPx,
                iconHeightPx = iconHeightPx,
                cropMode = cropMode
            )
            else -> {
                Bitmap.createBitmap(iconWidthPx, iconHeightPx, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    source.setBounds(0, 0, iconWidthPx, iconHeightPx)
                    source.draw(canvas)
                }
            }
        }
        val drawable = BitmapDrawable(resources, bitmap)
        importantPlaceIconCache[cacheKey] = drawable
        return drawable
    }

    private fun renderPoiBitmap(
        source: Bitmap,
        iconWidthPx: Int,
        iconHeightPx: Int,
        cropMode: PoiIconCropMode
    ): Bitmap {
        val preparedSource = when (cropMode) {
            PoiIconCropMode.TOP_BADGE -> {
                val cropSide = min(source.width, (source.height * 0.82f).roundToInt())
                val left = ((source.width - cropSide) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(source, left, 0, cropSide, cropSide.coerceAtMost(source.height))
            }
            PoiIconCropMode.WIDE_FIT -> source
            PoiIconCropMode.FIT -> source
        }

        return try {
            when (cropMode) {
                PoiIconCropMode.WIDE_FIT -> {
                    val safeWidth = iconWidthPx.coerceAtLeast(2)
                    val safeHeight = iconHeightPx.coerceAtLeast(2)
                    val scale = min(
                        safeWidth.toFloat() / preparedSource.width.toFloat(),
                        safeHeight.toFloat() / preparedSource.height.toFloat()
                    )
                    val targetWidth = (preparedSource.width * scale).roundToInt().coerceAtLeast(1)
                    val targetHeight = (preparedSource.height * scale).roundToInt().coerceAtLeast(1)
                    val scaled = Bitmap.createScaledBitmap(preparedSource, targetWidth, targetHeight, true)
                    Bitmap.createBitmap(safeWidth, safeHeight, Bitmap.Config.ARGB_8888).also { canvasBitmap ->
                        val canvas = Canvas(canvasBitmap)
                        val left = (safeWidth - targetWidth) / 2f
                        val top = (safeHeight - targetHeight) / 2f
                        canvas.drawBitmap(scaled, left, top, null)
                        if (scaled !== preparedSource && !scaled.isRecycled) {
                            scaled.recycle()
                        }
                    }
                }
                else -> Bitmap.createScaledBitmap(preparedSource, iconWidthPx, iconHeightPx, true)
            }
        } finally {
            if (preparedSource !== source && !preparedSource.isRecycled) {
                preparedSource.recycle()
            }
        }
    }

    private fun Double.format(fractionDigits: Int): String {
        return "%.${fractionDigits}f".format(Locale.US, this)
    }

    private fun fetchSearchCandidates(
        query: String,
        limit: Int,
        viewport: SearchViewport?,
        geocoderContext: Context?,
        userAgentPackage: String
    ): List<Pair<String, GeoPoint>> {
        val unique = linkedMapOf<String, GeoPoint>()

        fetchNominatimCandidates(query, limit, viewport, userAgentPackage).forEach { (label, point) ->
            val normalized = normalizeSearchLabel(label)
            if (normalized.isNotBlank() && !unique.containsKey(normalized)) {
                unique[normalized] = point
            }
        }

        fetchGeocoderCandidates(query, limit, geocoderContext).forEach { (label, point) ->
            val normalized = normalizeSearchLabel(label)
            if (normalized.isNotBlank() && !unique.containsKey(normalized)) {
                unique[normalized] = point
            }
        }

        return unique.entries
            .map { it.key to it.value }
            .take(limit)
    }

    private fun fetchNominatimCandidates(
        query: String,
        limit: Int,
        viewport: SearchViewport?,
        userAgentPackage: String
    ): List<Pair<String, GeoPoint>> {
        val endpoint = nominatimSearchUrl.toHttpUrlOrNull() ?: return emptyList()
        val urlBuilder = endpoint.newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("accept-language", Locale.getDefault().toLanguageTag())

        viewport?.let { box ->
            urlBuilder
                .addQueryParameter(
                    "viewbox",
                    "${box.west},${box.north},${box.east},${box.south}"
                )
                .addQueryParameter("bounded", "0")
        }

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Accept", "application/json")
            .header("User-Agent", "$userAgentPackage/map-search")
            .build()

        return try {
            searchHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()

                val jsonArray = JSONArray(body)
                val items = mutableListOf<Pair<String, GeoPoint>>()
                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(index) ?: continue
                    val lat = item.optString("lat").toDoubleOrNull() ?: continue
                    val lon = item.optString("lon").toDoubleOrNull() ?: continue
                    val label = buildNominatimLabel(item)
                    if (label.isBlank()) continue
                    items.add(label to GeoPoint(lat, lon))
                }
                items
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchGeocoderCandidates(
        query: String,
        limit: Int,
        geocoderContext: Context?
    ): List<Pair<String, GeoPoint>> {
        if (!Geocoder.isPresent()) return emptyList()
        val context = geocoderContext ?: return emptyList()
        return try {
            Geocoder(context, Locale.getDefault())
                .getFromLocationName(query, limit)
                .orEmpty()
                .mapNotNull { address ->
                    val label = buildAddressLabel(address)
                    if (label.isBlank()) {
                        null
                    } else {
                        label to GeoPoint(address.latitude, address.longitude)
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun buildAddressLabel(address: android.location.Address): String {
        val feature = address.featureName?.takeIf { it.isNotBlank() }
        val street = listOfNotNull(address.thoroughfare, address.subThoroughfare)
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotBlank() }
        val locality = listOfNotNull(
            address.locality,
            address.subAdminArea,
            address.adminArea
        ).firstOrNull { it.isNotBlank() }
        val country = address.countryName?.takeIf { it.isNotBlank() }

        return listOfNotNull(feature, street, locality, country)
            .distinct()
            .joinToString(", ")
            .ifBlank { address.getAddressLine(0).orEmpty() }
    }

    private fun buildNominatimLabel(item: JSONObject): String {
        val displayName = item.optString("display_name").trim()
        val name = item.optString("name").trim()
        return when {
            name.isNotBlank() && displayName.isNotBlank() &&
                !displayName.startsWith(name, ignoreCase = true) -> "$name, $displayName"
            displayName.isNotBlank() -> displayName
            name.isNotBlank() -> name
            else -> item.optString("type").trim()
        }
    }

    private fun normalizeSearchLabel(label: String): String {
        return label.replace("\\s+".toRegex(), " ").trim()
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) {
            clearSearchResultOverlay()
            Toast.makeText(
                requireContext(),
                getString(R.string.search_empty),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val appContext = context?.applicationContext ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val viewport = captureSearchViewport()
            val results = withContext(Dispatchers.IO) {
                fetchSearchCandidates(
                    query = query,
                    limit = 10,
                    viewport = viewport,
                    geocoderContext = appContext,
                    userAgentPackage = appContext.packageName
                )
            }
            if (!isAdded || _binding == null) return@launch

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

            results.forEach { (title, point) ->
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
        showSearchResultOverlay(title, point)
        binding.mapView.controller.animateTo(point)
    }

    private fun showSearchResultOverlay(title: String, point: GeoPoint) {
        clearSearchResultOverlay()
        val icon = buildSearchResultIconDrawable()
        val marker = Marker(binding.mapView).apply {
            position = point
            this.title = title
            snippet = getString(R.string.map_result_default)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            this.icon = icon
        }
        searchResultOverlay = marker
        binding.mapView.overlays.add(marker)
        binding.mapView.invalidate()
    }

    private fun buildSearchResultIconDrawable(): Drawable? {
        val iconPx = (defaultPoiIconDp * resources.displayMetrics.density).roundToInt().coerceAtLeast(20)
        val cacheKey = "search_result_pin:$iconPx"
        importantPlaceIconCache[cacheKey]
            ?.constantState
            ?.newDrawable(resources)
            ?.mutate()
            ?.let { return it }

        val source = ContextCompat.getDrawable(
            requireContext(),
            R.drawable.ic_search_result_pin
        )?.mutate() ?: return null

        val bitmap = when (source) {
            is BitmapDrawable -> {
                val rawBitmap = source.bitmap ?: return null
                Bitmap.createScaledBitmap(rawBitmap, iconPx, iconPx, true)
            }
            else -> {
                Bitmap.createBitmap(iconPx, iconPx, Bitmap.Config.ARGB_8888).also { bitmap ->
                    val canvas = Canvas(bitmap)
                    source.setBounds(0, 0, iconPx, iconPx)
                    source.draw(canvas)
                }
            }
        }
        return BitmapDrawable(resources, bitmap).also { drawable ->
            importantPlaceIconCache[cacheKey] = drawable
        }
    }

    private fun clearSearchResultOverlay() {
        val overlay = searchResultOverlay ?: return
        binding.mapView.overlays.remove(overlay)
        searchResultOverlay = null
        binding.mapView.invalidate()
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
                        activeTasksCache = tasks
                        refreshDisplayedMarkers()
                    }
                }
                launch {
                    viewModel.completedTasks.collect { tasks ->
                        completedTasksCache = tasks
                        refreshDisplayedMarkers()
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

    private fun shouldShowCompletedMarkers(): Boolean {
        return SettingsPreferences.isShowCompletedMarkersEnabled(requireContext())
    }

    private fun refreshDisplayedMarkers() {
        if (!isAdded || _binding == null) return
        val tasksToShow = if (shouldShowCompletedMarkers()) {
            completedTasksCache + activeTasksCache
        } else {
            activeTasksCache
        }
        updateMapMarkers(tasksToShow)
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

    private fun resolveMarkerIconName(key: String?): String {
        return getString(resolveMarkerIconOption(key).labelRes)
    }

    private fun buildColorIndicatorLabel(label: String, color: Int): SpannableString {
        val indicator = "●"
        val fullText = "$label: $indicator"
        return SpannableString(fullText).apply {
            setSpan(
                ForegroundColorSpan(color),
                fullText.length - indicator.length,
                fullText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            setSpan(
                RelativeSizeSpan(1.35f),
                fullText.length - indicator.length,
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
        button.tag = color
    }

    private fun extractSelectedColorFromButton(button: MaterialButton, fallbackColor: Int): Int {
        return (button.tag as? Int) ?: fallbackColor
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
            toggleSelectionPanel(
                targetPanel = dialogBinding.panelColorPicker,
                targetButton = dialogBinding.buttonToggleColorPicker,
                secondaryPanel = dialogBinding.panelIconPicker,
                secondaryButton = dialogBinding.buttonToggleIconPicker
            )
        }
        dialogBinding.buttonToggleIconPicker.setOnClickListener {
            toggleSelectionPanel(
                targetPanel = dialogBinding.panelIconPicker,
                targetButton = dialogBinding.buttonToggleIconPicker,
                secondaryPanel = dialogBinding.panelColorPicker,
                secondaryButton = dialogBinding.buttonToggleColorPicker
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
                val color = extractSelectedColorFromButton(
                    dialogBinding.buttonCustomColor,
                    selectedMarkerColor
                )
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
                    markerColor = color,
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
            toggleSelectionPanel(
                targetPanel = dialogBinding.panelColorPicker,
                targetButton = dialogBinding.buttonToggleColorPicker,
                secondaryPanel = dialogBinding.panelIconPicker,
                secondaryButton = dialogBinding.buttonToggleIconPicker
            )
        }
        dialogBinding.buttonToggleIconPicker.setOnClickListener {
            toggleSelectionPanel(
                targetPanel = dialogBinding.panelIconPicker,
                targetButton = dialogBinding.buttonToggleIconPicker,
                secondaryPanel = dialogBinding.panelColorPicker,
                secondaryButton = dialogBinding.buttonToggleColorPicker
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
                val color = extractSelectedColorFromButton(
                    dialogBinding.buttonCustomColor,
                    selectedMarkerColor
                )
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
                        markerColor = color,
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
        dialogBinding.textColor.text = buildColorIndicatorLabel(
            getString(R.string.map_task_color),
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
                val iconBitmap = resolveLocationMarkerBitmap()
                if (iconBitmap != null) {
                    setPersonIcon(iconBitmap)
                    setDirectionIcon(iconBitmap)
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

    private fun resolveLocationMarkerBitmap(): Bitmap? {
        if (!isAdded) return null
        val context = requireContext()
        val shouldUseAvatar = SettingsPreferences.isUseAvatarLocationMarkerEnabled(context)
        if (shouldUseAvatar) {
            val avatarBitmap = loadProfileAvatarBitmap()
            if (avatarBitmap != null) return avatarBitmap
        }

        val iconDrawable = ContextCompat.getDrawable(context, R.drawable.ic_my_location_arrow)
        return iconDrawable?.let { drawableToBitmap(it) }
    }

    private fun loadProfileAvatarBitmap(): Bitmap? {
        val context = requireContext()
        val avatarUriString = SettingsPreferences.getProfileAvatarUri(context) ?: return null
        val avatarUri = runCatching { android.net.Uri.parse(avatarUriString) }.getOrNull() ?: return null
        return runCatching {
            context.contentResolver.openInputStream(avatarUri)?.use { input ->
                val decoded = BitmapFactory.decodeStream(input) ?: return@use null
                createLocationAvatarBitmap(decoded)
            }
        }.getOrNull()
    }

    private fun createLocationAvatarBitmap(source: Bitmap): Bitmap {
        val density = resources.displayMetrics.density
        val iconSize = (52f * density).roundToInt().coerceAtLeast(96)
        val strokeWidthPx = 3f * density
        val outerPadding = 2f * density
        val avatarDiameter = (iconSize - outerPadding * 2f - strokeWidthPx * 2f)
            .roundToInt()
            .coerceAtLeast(1)

        val croppedAvatar = centerCropBitmap(source, avatarDiameter)
        val output = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val centerX = iconSize / 2f
        val centerY = iconSize / 2f
        val outerRadius = iconSize / 2f - outerPadding
        val avatarRadius = avatarDiameter / 2f
        val ringRadius = avatarRadius + strokeWidthPx

        val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        }
        val avatarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(croppedAvatar, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = resolveThemeColor(com.google.android.material.R.attr.colorPrimary)
        }

        canvas.drawCircle(centerX, centerY, outerRadius, surfacePaint)
        canvas.drawCircle(centerX, centerY, avatarRadius, avatarPaint)
        canvas.drawCircle(centerX, centerY, ringRadius - strokeWidthPx / 2f, borderPaint)

        if (croppedAvatar != source && !croppedAvatar.isRecycled) {
            croppedAvatar.recycle()
        }

        return output
    }

    private fun centerCropBitmap(source: Bitmap, targetSize: Int): Bitmap {
        if (source.width == targetSize && source.height == targetSize) return source

        val scale = max(
            targetSize / source.width.toFloat(),
            targetSize / source.height.toFloat()
        )
        val scaledWidth = (source.width * scale).roundToInt().coerceAtLeast(targetSize)
        val scaledHeight = (source.height * scale).roundToInt().coerceAtLeast(targetSize)
        val scaledBitmap = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true)
        val left = ((scaledWidth - targetSize) / 2).coerceAtLeast(0)
        val top = ((scaledHeight - targetSize) / 2).coerceAtLeast(0)

        return Bitmap.createBitmap(scaledBitmap, left, top, targetSize, targetSize).also {
            if (scaledBitmap != source && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }
        }
    }

    private fun resolveThemeColor(attrRes: Int): Int {
        return MaterialColors.getColor(binding.root, attrRes)
    }

    private fun getCurrentLocation(forceCenter: Boolean = false) {
        if (!isAdded || _binding == null) return
        val hasFine = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            requestLocationPermissions()
            return
        }
        if (hasFine || hasCoarse) {
            val cancellationToken = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                if (hasFine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).addOnSuccessListener { location ->
                val fallbackStateLocation = viewModel.currentLocation.value?.let {
                    GeoPoint(it.first, it.second)
                }
                val candidatePoint: GeoPoint? = when {
                    location != null -> GeoPoint(location.latitude, location.longitude)
                    myLocationOverlay?.myLocation != null -> myLocationOverlay?.myLocation
                    else -> fallbackStateLocation
                }
                candidatePoint?.let { geoPoint ->
                    if (forceCenter) {
                        binding.mapView.controller.animateTo(geoPoint)
                    }
                    viewModel.updateCurrentLocation(geoPoint.latitude, geoPoint.longitude)
                }
            }
        }
    }

    private fun toggleSelectionPanel(
        targetPanel: View,
        targetButton: MaterialButton,
        secondaryPanel: View,
        secondaryButton: MaterialButton
    ) {
        val shouldExpand = targetPanel.visibility != View.VISIBLE
        togglePanel(targetPanel, targetButton, shouldExpand)
        if (shouldExpand && secondaryPanel.visibility == View.VISIBLE) {
            togglePanel(secondaryPanel, secondaryButton, false)
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
                clampMapViewport()
                collapseSearchUi()
                hideBottomNavigationForMapMotion()
                hideMapHudForMotion()
                scheduleImportantPlacesRefresh()
                scheduleTilePrefetch()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                // Зум тоже отключает автослежение, чтобы карта не прыгала
                shouldAutoCenter = false
                clampMapViewport()
                collapseSearchUi()
                hideBottomNavigationForMapMotion()
                hideMapHudForMotion()
                scheduleImportantPlacesRefresh()
                scheduleTilePrefetch()
                return false
            }
        })
    }

    private fun clampMapViewport() {
        if (!isAdded || _binding == null) return

        val currentZoom = binding.mapView.zoomLevelDouble
        val clampedZoom = currentZoom.coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
        if (abs(clampedZoom - currentZoom) > 0.0001) {
            binding.mapView.controller.setZoom(clampedZoom)
        }

        val currentCenter = binding.mapView.mapCenter?.let {
            GeoPoint(it.latitude, it.longitude)
        } ?: return
        val clampedCenter = clampMapCenter(currentCenter)
        if (
            abs(clampedCenter.latitude - currentCenter.latitude) > 0.000001 ||
            abs(clampedCenter.longitude - currentCenter.longitude) > 0.000001
        ) {
            binding.mapView.controller.setCenter(clampedCenter)
        }
    }

    private fun clampMapCenter(center: GeoPoint): GeoPoint {
        val box = binding.mapView.boundingBox
        val latHalfSpan = ((box?.latNorth ?: center.latitude) - (box?.latSouth ?: center.latitude))
            .coerceAtLeast(0.0) / 2.0
        val lonHalfSpan = ((box?.lonEast ?: center.longitude) - (box?.lonWest ?: center.longitude))
            .coerceAtLeast(0.0) / 2.0

        val minLatitude = (-85.0 + latHalfSpan).coerceAtMost(85.0)
        val maxLatitude = (85.0 - latHalfSpan).coerceAtLeast(-85.0)
        val minLongitude = (-180.0 + lonHalfSpan).coerceAtMost(180.0)
        val maxLongitude = (180.0 - lonHalfSpan).coerceAtLeast(-180.0)

        val clampedLatitude = when {
            minLatitude > maxLatitude -> 0.0
            else -> center.latitude.coerceIn(minLatitude, maxLatitude)
        }
        val clampedLongitude = when {
            minLongitude > maxLongitude -> 0.0
            else -> center.longitude.coerceIn(minLongitude, maxLongitude)
        }

        return GeoPoint(clampedLatitude, clampedLongitude)
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
        // По умолчанию показываем только кнопку поиска, чтобы интерфейс карты оставался чистым.
        isSearchExpanded = false
        binding.searchCard.visibility = View.GONE
        binding.searchFab.visibility = View.VISIBLE
        binding.searchCard.alpha = 0f
        binding.searchFab.alpha = 1f
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
        val hasSavedViewport = SettingsPreferences.getLastMapViewport(requireContext()) != null
        shouldAutoCenter = selectedTask == null &&
            SettingsPreferences.isFollowLocationEnabled(requireContext()) &&
            !hasSavedViewport
        applyMapPresentation()
        refreshDisplayedMarkers()
        scheduleImportantPlacesRefresh(immediate = true)
        selectedTask?.let {
            binding.mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
        }

        if (!isSearchExpanded) {
            binding.searchFab.visibility = View.VISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        saveCurrentMapViewport()
        binding.mapView.onPause()
        importantPlacesRefreshRunnable?.let { uiHandler.removeCallbacks(it) }
        importantPlacesRefreshRunnable = null
        hasPendingImportantPlacesRefresh = false
        isImportantPlacesFetchInFlight = false
        importantPlacesRefreshJob?.cancel()
        prefetchTilesRunnable?.let { uiHandler.removeCallbacks(it) }
        prefetchTilesRunnable = null
        activeTilePrefetchTask?.cancel(true)
        activeTilePrefetchTask = null
        // Останавливаем слой геопозиции, чтобы не было неконсистентного состояния
        myLocationOverlay?.disableMyLocation()
        showBottomNavigationAfterMotion()
        showMapHudAfterMotion()
    }

    override fun onDestroyView() {
        searchSuggestionJob?.cancel()
        importantPlacesRefreshJob?.cancel()
        hasPendingImportantPlacesRefresh = false
        isImportantPlacesFetchInFlight = false
        restoreBottomNavRunnable?.let { uiHandler.removeCallbacks(it) }
        restoreBottomNavRunnable = null
        restoreMapHudRunnable?.let { uiHandler.removeCallbacks(it) }
        restoreMapHudRunnable = null
        importantPlacesRefreshRunnable?.let { uiHandler.removeCallbacks(it) }
        importantPlacesRefreshRunnable = null
        prefetchTilesRunnable?.let { uiHandler.removeCallbacks(it) }
        prefetchTilesRunnable = null
        activeTilePrefetchTask?.cancel(true)
        activeTilePrefetchTask = null
        clearImportantPlaceOverlays()
        importantPlaceIconCache.clear()
        importantPlacesCache.clear()
        clearSearchResultOverlay()
        super.onDestroyView()
        _binding = null
    }
}
