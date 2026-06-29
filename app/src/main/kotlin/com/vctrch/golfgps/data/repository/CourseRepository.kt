package com.vctrch.golfgps.data.repository

import com.vctrch.golfgps.data.local.CourseDataCache
import com.vctrch.golfgps.data.remote.OSMHoleParser
import com.vctrch.golfgps.data.remote.OpenGolfApi
import com.vctrch.golfgps.data.remote.OsmGolfSource
import com.vctrch.golfgps.domain.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepository
    @Inject
    constructor(
        private val openGolfApi: OpenGolfApi,
        private val osmGolfSource: OsmGolfSource,
        private val cache: CourseDataCache,
    ) {
        suspend fun searchCourses(query: String): List<GolfCourseSummary> {
            return openGolfApi.searchCourses(query)
        }

        /**
         * Loads the scorecard quickly and merges in any *already cached* OSM greens. This never
         * touches the network for OSM, so the round can open immediately; live OSM greens are
         * fetched separately via [enrichWithOsmGreens].
         */
        suspend fun loadCourseBasics(summary: GolfCourseSummary): LoadedCourse {
            return try {
                val (_, scorecard) = openGolfApi.loadCourse(summary.id)
                // Keep the summary from search (it always has valid coordinates); the detail
                // endpoint is only used for the per-hole scorecard.
                val fallbackHoles = CourseLoaderSupport.fallbackHoles(scorecard, summary)
                val cachedOsm = cache.cachedOsmHoles(summary.id) ?: emptyList()
                val holes = OSMHoleParser.mergeHoles(fallbackHoles, cachedOsm, scorecard)
                val loaded = LoadedCourse(summary = summary, scorecard = scorecard, holes = holes)
                if (scorecard.isNotEmpty()) {
                    cache.saveBasics(loaded)
                }
                loaded
            } catch (_: Exception) {
                cache.cachedBasics(summary.id)
                    ?: throw IllegalStateException("Couldn't load course")
            }
        }

        /**
         * Fetches real per-hole tee/green geometry from OpenStreetMap (cache-first, then Overpass)
         * and returns [loaded] with that geometry merged in. Returns `null` when no OSM data is
         * available so the caller can keep the scorecard's course-center fallback. Best-effort: any
         * failure (offline, rate limit, unmapped course, timeout) yields `null`.
         */
        suspend fun enrichWithOsmGreens(loaded: LoadedCourse): LoadedCourse? {
            val summary = loaded.summary
            val osmHoles =
                try {
                    cache.cachedOsmHoles(summary.id) ?: fetchAndCacheOsmHoles(loaded)
                } catch (_: Exception) {
                    emptyList()
                }
            if (osmHoles.none { CourseLoaderSupport.isMappedOsmHole(it) }) return null

            val merged = OSMHoleParser.mergeHoles(loaded.holes, osmHoles, loaded.scorecard)
            return loaded.copy(holes = merged)
        }

        private suspend fun fetchAndCacheOsmHoles(loaded: LoadedCourse): List<HoleTarget> {
            val summary = loaded.summary
            val holes =
                osmGolfSource.loadHoleTargets(
                    courseCenter = LatLng(summary.latitude, summary.longitude),
                    osmCourseId = summary.osmId,
                    courseName = summary.name,
                    scorecard = loaded.scorecard,
                    userLocation = null,
                )
            val mapped = holes.filter { CourseLoaderSupport.isMappedOsmHole(it) }
            if (mapped.isNotEmpty()) {
                cache.saveOsmHoles(summary.id, summary, mapped)
            }
            return holes
        }
    }
