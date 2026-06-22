package com.vctrch.golfgps.feature.round

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vctrch.golfgps.data.local.UserPreferencesRepository
import com.vctrch.golfgps.data.repository.CourseRepository
import com.vctrch.golfgps.domain.*
import com.vctrch.golfgps.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoundUiState(
    val searchQuery: String = "",
    val searchResults: List<GolfCourseSummary> = emptyList(),
    val isSearching: Boolean = false,
    val isLoadingCourse: Boolean = false,
    val errorMessage: String? = null,
    val courseLoadError: String? = null,
    val loadedCourse: LoadedCourse? = null,
    val selectedHoleNumber: Int = 1,
    val userLocation: LatLng? = null,
) {
    val trimmedSearchQuery: String get() = searchQuery.trim()
    val isSearchActive: Boolean get() = trimmedSearchQuery.length >= MINIMUM_SEARCH_QUERY_LENGTH
    val isRoundReady: Boolean get() = loadedCourse != null && currentHole != null
    val currentHole: HoleTarget?
        get() = loadedCourse?.holes?.firstOrNull { it.number == selectedHoleNumber }

    fun distanceToGreen(): Int? {
        val hole = currentHole ?: return null
        val location = userLocation ?: return null
        return GeoMath.yards(location, hole.green)
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
        locationRepository: LocationRepository,
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

        init {
            viewModelScope.launch {
                locationRepository.locationUpdates().collect { location ->
                    _uiState.update { it.copy(userLocation = location) }
                }
            }
        }

        fun onSearchQueryChange(query: String) {
            _uiState.update { it.copy(searchQuery = query) }
            scheduleSearch()
        }

        fun clearSearch() {
            onSearchQueryChange("")
        }

        fun selectCourse(course: GolfCourseSummary) {
            viewModelScope.launch {
                _uiState.update {
                    it.copy(isLoadingCourse = true, courseLoadError = null, errorMessage = null)
                }
                try {
                    val loaded = courseRepository.loadCourseBasics(course.id)
                    _uiState.update {
                        it.copy(
                            isLoadingCourse = false,
                            loadedCourse = loaded,
                            selectedHoleNumber = loaded.holes.firstOrNull()?.number ?: 1,
                        )
                    }
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

        fun endRound() {
            _uiState.update {
                it.copy(
                    loadedCourse = null,
                    selectedHoleNumber = 1,
                    courseLoadError = null,
                )
            }
        }

        fun selectHole(number: Int) {
            _uiState.update { it.copy(selectedHoleNumber = number) }
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
        }
    }
