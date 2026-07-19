package com.vctrch.golfgps.data.repository

import com.vctrch.golfgps.data.local.CourseDataCache
import com.vctrch.golfgps.data.remote.GapFillContext
import com.vctrch.golfgps.data.remote.OSMHoleLoadResult
import com.vctrch.golfgps.data.remote.OSMHoleParser
import com.vctrch.golfgps.data.remote.OpenGolfApi
import com.vctrch.golfgps.data.remote.OsmGolfSource
import com.vctrch.golfgps.domain.*
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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

        /** Instant offline/open path: validated cached basics + OSM merge for the selected course. */
        suspend fun cachedBasics(summary: GolfCourseSummary): LoadedCourse? =
            cache.cachedBasics(summary.id, matching = summary)

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
                val cachedOsm = cache.validatedCachedOsmHoles(summary).orEmpty()
                val holes = OSMHoleParser.mergeHoles(fallbackHoles, cachedOsm, scorecard)
                val loaded = LoadedCourse(summary = summary, scorecard = scorecard, holes = holes)
                if (scorecard.isNotEmpty()) {
                    cache.saveBasics(loaded)
                }
                loaded
            } catch (_: Exception) {
                cache.cachedBasics(summary.id, matching = summary)
                    ?: throw IllegalStateException("Couldn't load course")
            }
        }

        /**
         * Fetches real per-hole tee/green geometry from OpenStreetMap (cache-first, then Overpass)
         * and returns [loaded] with that geometry merged in. Returns `null` when no OSM data is
         * available so the caller can keep the scorecard's course-center fallback. Best-effort: any
         * failure (offline, rate limit, unmapped course, timeout) yields `null`.
         *
         * @param forceNetwork when true, skips the cache-only short-circuit and always hits Overpass
         * (used by manual "Reload hole GPS").
         * @param userLocation optional GPS used to bias Overpass discovery near the player.
         */
        suspend fun enrichWithOsmGreens(
            loaded: LoadedCourse,
            forceNetwork: Boolean = false,
            userLocation: LatLng? = null,
        ): LoadedCourse? {
            val summary = loaded.summary
            val result =
                try {
                    if (forceNetwork) {
                        fetchAndCacheOsmHoles(loaded, userLocation)
                    } else {
                        val cached = cache.validatedCachedOsmHoles(summary)
                        if (cached != null) {
                            OSMHoleLoadResult(cached, GapFillContext.EMPTY)
                        } else {
                            fetchAndCacheOsmHoles(loaded, userLocation)
                        }
                    }
                } catch (_: Exception) {
                    OSMHoleLoadResult(emptyList(), GapFillContext.EMPTY)
                }
            if (result.holes.none { CourseLoaderSupport.isMappedOsmHole(it) }) return null

            val merged =
                OSMHoleParser.mergeHoles(
                    baseline = loaded.holes,
                    osm = result.holes,
                    scorecard = loaded.scorecard,
                    gapContext = result.gapFillContext,
                )
            if (!CourseCacheValidation.osmHolesMatchCourse(merged, summary) &&
                result.holes.none { CourseLoaderSupport.isMappedOsmHole(it) }
            ) {
                return null
            }
            return loaded.copy(holes = merged)
        }

        private suspend fun fetchAndCacheOsmHoles(
            loaded: LoadedCourse,
            userLocation: LatLng?,
        ): OSMHoleLoadResult {
            val summary = loaded.summary
            var lastError: Exception? = null
            repeat(OSM_NETWORK_ATTEMPTS) { attempt ->
                try {
                    val result =
                        osmGolfSource.loadHoleTargets(
                            courseCenter = LatLng(summary.latitude, summary.longitude),
                            osmCourseId = summary.osmId,
                            courseName = summary.name,
                            scorecard = loaded.scorecard,
                            userLocation = userLocation,
                        )
                    val mapped = result.holes.filter { CourseLoaderSupport.isMappedOsmHole(it) }
                    if (mapped.isNotEmpty() && CourseCacheValidation.osmHolesMatchCourse(mapped, summary)) {
                        cache.saveOsmHoles(summary.id, summary, mapped)
                    }
                    return result
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (attempt < OSM_NETWORK_ATTEMPTS - 1) {
                        delay(OSM_RETRY_DELAY_MS)
                    }
                }
            }
            val cached = cache.validatedCachedOsmHoles(summary)
            if (cached != null) return OSMHoleLoadResult(cached, GapFillContext.EMPTY)
            throw lastError ?: IllegalStateException("OSM enrichment failed")
        }

        companion object {
            private const val OSM_NETWORK_ATTEMPTS = 2
            private const val OSM_RETRY_DELAY_MS = 1_000L
        }
    }
