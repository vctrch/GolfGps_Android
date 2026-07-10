package com.vctrch.golfgps.feature.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vctrch.golfgps.data.analytics.GolfAnalytics
import com.vctrch.golfgps.data.local.UserPreferencesRepository
import com.vctrch.golfgps.data.repository.CourseRepository
import com.vctrch.golfgps.domain.*
import com.vctrch.golfgps.location.LocationRepository
import com.vctrch.golfgps.location.LocationUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoundUiState(
    val searchQuery: String = "",
    val searchResults: List<GolfCourseSummary> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingCourse: Boolean = false,
    val isEnhancingHoles: Boolean = false,
    val errorMessage: String? = null,
    val courseLoadError: String? = null,
    val loadedCourse: LoadedCourse? = null,
    val lastSelectedCourse: GolfCourseSummary? = null,
    val selectedHoleNumber: Int = 1,
    val userLocation: LatLng? = null,
    val locationStatus: LocationUiStatus = LocationUiStatus(),
) {
    val trimmedSearchQuery: String get() = searchQuery.trim()
    val isSearchActive: Boolean get() = trimmedSearchQuery.length >= MINIMUM_SEARCH_QUERY_LENGTH
    val isRoundReady: Boolean get() = loadedCourse != null && currentHole != null
    val isRoundUnavailable: Boolean
        get() = courseLoadError != null && loadedCourse == null && lastSelectedCourse != null
    val currentHole: HoleTarget?
        get() = loadedCourse?.holes?.firstOrNull { it.number == selectedHoleNumber }

    val currentHoleIndex: Int
        get() {
            val holes = loadedCourse?.holes ?: return 0
            return holes.indexOfFirst { it.number == selectedHoleNumber }.coerceAtLeast(0)
        }

    val currentHoleNeedsGPS: Boolean
        get() = currentHole?.hasReliableGreenPosition == false

    val needsHoleGPSRefinement: Boolean
        get() = loadedCourse?.holes?.any { !it.hasReliableGreenPosition } == true

    fun distanceToGreen(): Int? {
        val hole = currentHole ?: return null
        val location = userLocation ?: return null
        return hole.playerYardsToGreen(location)
    }

    fun distanceToTee(): Int? {
        val hole = currentHole ?: return null
        val location = userLocation ?: return null
        return hole.playerYardsToTee(location)
    }

    companion object {
        const val MINIMUM_SEARCH_QUERY_LENGTH = 2
    }
}

@HiltViewModel
class RoundViewModel
    @Inject
    constructor(
        private val courseRepository: CourseRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val locationRepository: LocationRepository,
        private val analytics: GolfAnalytics,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(RoundUiState())
        val uiState: StateFlow<RoundUiState> = _uiState.asStateFlow()

        val mapDisplayStyle: StateFlow<MapDisplayStyle> =
            userPreferencesRepository.mapDisplayStyle.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                MapDisplayStyle.STANDARD,
            )

        private var searchJob: Job? = null
        private var activeSearchId = 0
        private var locationJob: Job? = null
        private var osmEnhancementJob: Job? = null
        private var osmEnhancementGeneration = 0
        private var osmRefinementAttempts = 0
        private var lastOsmRefinementAtMs = 0L

        init {
            observeLocation()
            viewModelScope.launch {
                locationRepository.status.collect { status ->
                    _uiState.update { it.copy(locationStatus = status) }
                }
            }
        }

        fun refreshLocationUpdates() {
            locationRepository.refreshPermissionStatus()
            observeLocation()
        }

        fun openLocationSettings() {
            if (_uiState.value.locationStatus.locationServicesDisabled) {
                locationRepository.openLocationSettings()
            } else {
                locationRepository.openAppSettings()
            }
        }

        fun requestPreciseLocation() {
            locationRepository.requestPreciseLocationIfNeeded()
        }

        private fun observeLocation() {
            locationJob?.cancel()
            locationJob =
                viewModelScope.launch {
                    locationRepository
                        .locationUpdates()
                        .catch { /* Ignore location errors; keep last fix. */ }
                        .collect { location ->
                            _uiState.update { it.copy(userLocation = location) }
                            if (location != null) {
                                refineHoleGPSIfNeeded(location)
                            }
                        }
                }
        }

        fun onSearchQueryChange(query: String) {
            _uiState.update { it.copy(searchQuery = query, courseLoadError = null) }
            scheduleSearch()
        }

        fun clearSearch() {
            onSearchQueryChange("")
        }

        fun selectCourse(course: GolfCourseSummary) {
            analytics.logCourseSelected(course)
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        isLoadingCourse = true,
                        courseLoadError = null,
                        errorMessage = null,
                        lastSelectedCourse = course,
                    )
                }
                try {
                    var loaded = courseRepository.loadCourseBasics(course)
                    if (loaded.holes.isEmpty()) {
                        loaded = courseRepository.enrichWithOsmGreens(loaded) ?: loaded
                    }
                    if (loaded.holes.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoadingCourse = false,
                                courseLoadError = "No hole data is available for this course yet.",
                            )
                        }
                        return@launch
                    }
                    osmRefinementAttempts = 0
                    lastOsmRefinementAtMs = 0L
                    analytics.logRoundStarted(course, loaded.holes.size)
                    _uiState.update {
                        it.copy(
                            isLoadingCourse = false,
                            loadedCourse = loaded,
                            selectedHoleNumber = loaded.holes.firstOrNull()?.number ?: 1,
                            courseLoadError = null,
                        )
                    }
                    beginBackgroundHoleGPSRefresh(force = false)
                } catch (_: Exception) {
                    _uiState.update {
                        it.copy(
                            isLoadingCourse = false,
                            courseLoadError = "Couldn't load course. Check your connection and try again.",
                        )
                    }
                }
            }
        }

        fun retryCourseLoad() {
            val course = _uiState.value.lastSelectedCourse ?: return
            selectCourse(course)
        }

        fun reloadHoleGPS() {
            val courseId = _uiState.value.loadedCourse?.summary?.id ?: return
            analytics.logReloadHoleGps(courseId)
            beginBackgroundHoleGPSRefresh(force = true)
        }

        fun refineHoleGPSIfNeeded(userLocation: LatLng) {
            val state = _uiState.value
            if (state.loadedCourse == null) return
            if (!state.needsHoleGPSRefinement && !state.currentHoleNeedsGPS) return
            if (osmEnhancementJob?.isActive == true || state.isEnhancingHoles) return
            if (osmRefinementAttempts >= MAX_OSM_REFINEMENT_ATTEMPTS) return
            val now = System.currentTimeMillis()
            if (now - lastOsmRefinementAtMs < OSM_REFINEMENT_COOLDOWN_MS) return
            osmRefinementAttempts += 1
            lastOsmRefinementAtMs = now
            beginBackgroundHoleGPSRefresh(force = false, searchNear = userLocation)
        }

        private fun beginBackgroundHoleGPSRefresh(
            force: Boolean,
            searchNear: LatLng? = _uiState.value.userLocation,
        ) {
            val course = _uiState.value.loadedCourse ?: return
            if (!force && osmEnhancementJob?.isActive == true) return

            osmEnhancementJob?.cancel()
            osmEnhancementGeneration += 1
            val generation = osmEnhancementGeneration
            osmEnhancementJob =
                viewModelScope.launch {
                    _uiState.update { it.copy(isEnhancingHoles = true) }
                    try {
                        val enriched =
                            courseRepository.enrichWithOsmGreens(
                                loaded = course,
                                forceNetwork = force,
                                userLocation = searchNear,
                            ) ?: return@launch
                        if (generation != osmEnhancementGeneration) return@launch
                        analytics.logOsmEnrichment(
                            courseId = enriched.summary.id,
                            mappedHoleCount = enriched.holes.count { it.hasReliableGreenPosition },
                            forced = force,
                        )
                        _uiState.update { state ->
                            if (state.loadedCourse?.summary?.id == enriched.summary.id) {
                                state.copy(loadedCourse = enriched)
                            } else {
                                state
                            }
                        }
                    } finally {
                        if (generation == osmEnhancementGeneration) {
                            _uiState.update { it.copy(isEnhancingHoles = false) }
                        }
                    }
                }
        }

        fun endRound() {
            analytics.logRoundEnded(_uiState.value.loadedCourse?.summary?.id)
            osmEnhancementJob?.cancel()
            osmEnhancementGeneration += 1
            osmRefinementAttempts = 0
            lastOsmRefinementAtMs = 0L
            _uiState.update {
                it.copy(
                    loadedCourse = null,
                    selectedHoleNumber = 1,
                    courseLoadError = null,
                    isEnhancingHoles = false,
                    lastSelectedCourse = null,
                )
            }
        }

        fun selectHole(number: Int) {
            analytics.logHoleSelected(number)
            _uiState.update { it.copy(selectedHoleNumber = number) }
            _uiState.value.userLocation?.let { refineHoleGPSIfNeeded(it) }
        }

        fun previousHole() {
            val holes = _uiState.value.loadedCourse?.holes ?: return
            val index = holes.indexOfFirst { it.number == _uiState.value.selectedHoleNumber }
            if (index > 0) {
                selectHole(holes[index - 1].number)
            }
        }

        fun nextHole() {
            val holes = _uiState.value.loadedCourse?.holes ?: return
            val index = holes.indexOfFirst { it.number == _uiState.value.selectedHoleNumber }
            if (index >= 0 && index < holes.lastIndex) {
                selectHole(holes[index + 1].number)
            }
        }

        fun setMapDisplayStyle(style: MapDisplayStyle) {
            viewModelScope.launch {
                userPreferencesRepository.setMapDisplayStyle(style)
            }
        }

        private fun scheduleSearch() {
            searchJob?.cancel()
            val query = _uiState.value.trimmedSearchQuery
            if (query.length < RoundUiState.MINIMUM_SEARCH_QUERY_LENGTH) {
                _uiState.update {
                    it.copy(searchResults = emptyList(), isSearching = false, errorMessage = null)
                }
                return
            }

            val searchId = ++activeSearchId
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    if (searchId != activeSearchId) return@launch
                    _uiState.update { it.copy(isSearching = true, errorMessage = null) }
                    try {
                        val results = courseRepository.searchCourses(query)
                        if (searchId != activeSearchId) return@launch
                        analytics.logSearch(query, results.size)
                        _uiState.update { it.copy(searchResults = results, isSearching = false) }
                    } catch (_: Exception) {
                        if (searchId != activeSearchId) return@launch
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                errorMessage = "Search failed. Try again.",
                                searchResults = emptyList(),
                            )
                        }
                    }
                }
        }

        companion object {
            private const val SEARCH_DEBOUNCE_MS = 350L
            private const val MAX_OSM_REFINEMENT_ATTEMPTS = 5
            private const val OSM_REFINEMENT_COOLDOWN_MS = 12_000L
        }
    }
