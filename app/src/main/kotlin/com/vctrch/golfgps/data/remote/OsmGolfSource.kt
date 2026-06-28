package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.GeoMath
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LatLng
import com.vctrch.golfgps.domain.ScorecardHole
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Parameters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

interface OsmGolfSource {
    suspend fun loadHoleTargets(
        courseCenter: LatLng,
        osmCourseId: Long?,
        courseName: String?,
        scorecard: List<ScorecardHole>,
        userLocation: LatLng? = null,
    ): List<HoleTarget>
}

class OverpassGolfSource(
    private val client: HttpClient,
    private val endpoints: List<String> = DEFAULT_ENDPOINTS,
) : OsmGolfSource {
    private data class ResolvedCourse(val osmType: String, val id: Long, val center: LatLng?)

    private data class Stage(val anchors: List<LatLng>, val radiusMeters: Int)

    private data class Candidate(
        val osmType: String,
        val id: Long,
        val name: String,
        val center: LatLng,
    )

    override suspend fun loadHoleTargets(
        courseCenter: LatLng,
        osmCourseId: Long?,
        courseName: String?,
        scorecard: List<ScorecardHole>,
        userLocation: LatLng?,
    ): List<HoleTarget> {
        val effectivePlayer = effectivePlayerLocation(userLocation, courseCenter)
        val resolved =
            osmCourseId?.let { ResolvedCourse("way", it, null) }
                ?: discoverCourse(courseCenter, courseName)

        if (resolved != null && resolved.osmType in AREA_OSM_TYPES) {
            val elements =
                runCatching { runOverpassRaced(courseAreaHoleQuery(resolved.osmType, resolved.id)) }
                    .getOrNull()
                    .orEmpty()
            if (elements.isNotEmpty()) {
                val parsed =
                    OSMHoleParser.holeTargets(
                        elements = elements,
                        scorecard = scorecard,
                        courseCenter = courseCenter,
                        trustCourseArea = true,
                        playerLocation = effectivePlayer,
                    )
                if (parsed.isNotEmpty()) return parsed
            }
        }
        return aroundSearch(courseCenter, scorecard, userLocation, effectivePlayer)
    }

    private suspend fun aroundSearch(
        courseCenter: LatLng,
        scorecard: List<ScorecardHole>,
        userLocation: LatLng?,
        effectivePlayer: LatLng?,
    ): List<HoleTarget> {
        val queryCoordinate = queryCoordinate(courseCenter, userLocation)
        val expectedHoleNumbers = scorecard.map { it.number }.toSet()
        var lastElements: List<OverpassElement> = emptyList()
        var bestParsed: List<HoleTarget> = emptyList()

        val stages =
            listOf(
                Stage(listOf(courseCenter), 2_000),
                Stage(uniqueAnchors(listOf(courseCenter, queryCoordinate)), 3_000),
                Stage(
                    uniqueAnchors(
                        listOf(courseCenter, queryCoordinate) + (userLocation?.let { listOf(it) } ?: emptyList()),
                    ),
                    AROUND_RADIUS_METERS,
                ),
            )

        for ((index, stage) in stages.withIndex()) {
            val payload =
                runCatching { runOverpassRaced(multiAnchorAroundQuery(stage.anchors, stage.radiusMeters)) }
                    .getOrNull() ?: break
            lastElements = unionElements(lastElements + payload)
            val filtered = filterElementsToCourse(lastElements, courseCenter, maxYards = 3_500)
            val parsed =
                OSMHoleParser.holeTargets(
                    elements = filtered,
                    scorecard = scorecard,
                    courseCenter = courseCenter,
                    trustCourseArea = false,
                    playerLocation = effectivePlayer,
                )
            if (parsed.size > bestParsed.size) bestParsed = parsed

            val mappedNumbers = parsed.map { it.number }.toSet()
            val allScorecardHolesMapped =
                expectedHoleNumbers.isNotEmpty() && mappedNumbers.containsAll(expectedHoleNumbers)
            if (allScorecardHolesMapped || index == stages.lastIndex) break
        }

        return bestParsed
    }

    private fun multiAnchorAroundQuery(
        anchors: List<LatLng>,
        radiusMeters: Int,
    ): String {
        val anchorList =
            anchors.flatMap { anchor ->
                FEATURE_QUERIES.map { feature ->
                    "$feature(around:$radiusMeters,${anchor.latitude},${anchor.longitude});"
                }
            }
        val body = anchorList.joinToString(separator = "\n  ")
        return "[out:json][timeout:$OVERPASS_TIMEOUT_SECONDS];\n($body);\nout geom;"
    }

    private fun uniqueAnchors(anchors: List<LatLng>): List<LatLng> {
        val results = mutableListOf<LatLng>()
        for (anchor in anchors) {
            if (results.none { GeoMath.yards(it, anchor) < 150 }) results.add(anchor)
        }
        return results
    }

    private fun unionElements(elements: List<OverpassElement>): List<OverpassElement> {
        val seen = mutableSetOf<Long>()
        val results = mutableListOf<OverpassElement>()
        for (element in elements) {
            val id = element.id
            if (id == null) {
                results.add(element)
            } else if (seen.add(id)) {
                results.add(element)
            }
        }
        return results
    }

    private fun filterElementsToCourse(
        elements: List<OverpassElement>,
        courseCenter: LatLng,
        maxYards: Int,
    ): List<OverpassElement> =
        elements.filter { element ->
            val anchor = element.coordinate ?: GeoMath.centroid(element.geometryCoordinates) ?: return@filter true
            GeoMath.yards(courseCenter, anchor) <= maxYards
        }

    private fun queryCoordinate(
        courseCenter: LatLng,
        userLocation: LatLng?,
    ): LatLng {
        if (userLocation == null) return courseCenter
        return if (GeoMath.yards(courseCenter, userLocation) <= AROUND_RADIUS_METERS) userLocation else courseCenter
    }

    private suspend fun discoverCourse(
        coordinate: LatLng,
        courseName: String?,
    ): ResolvedCourse? {
        val query =
            """
            [out:json][timeout:$OVERPASS_TIMEOUT_SECONDS];
            nwr["leisure"="golf_course"](around:5000,${coordinate.latitude},${coordinate.longitude});
            out tags center;
            """.trimIndent()
        val elements = runCatching { runOverpassRaced(query) }.getOrNull() ?: return null

        val candidates =
            elements.mapNotNull { element ->
                val id = element.id ?: return@mapNotNull null
                val type = element.type ?: return@mapNotNull null
                val center = element.coordinate ?: return@mapNotNull null
                Candidate(type, id, element.tags?.get("name") ?: "", center)
            }
        if (candidates.isEmpty()) return null

        if (!courseName.isNullOrEmpty()) {
            val best =
                candidates
                    .map { it to courseNameMatchScore(courseName, it.name) }
                    .filter { it.second > 0 }
                    .sortedWith(
                        compareByDescending<Pair<Candidate, Int>> { it.second }
                            .thenBy { GeoMath.yards(coordinate, it.first.center) },
                    )
                    .firstOrNull()
                    ?.first
            if (best != null && isDiscoveryPlausible(best.center, coordinate)) {
                return ResolvedCourse(best.osmType, best.id, best.center)
            }
        }

        val nearby = candidates.filter { GeoMath.yards(coordinate, it.center) <= 1_200 }
        val only = nearby.singleOrNull()
        if (only != null && isDiscoveryPlausible(only.center, coordinate)) {
            return ResolvedCourse(only.osmType, only.id, only.center)
        }
        return null
    }

    /** Queries every endpoint in parallel and returns the first non-empty result, cancelling the rest. */
    private suspend fun runOverpassRaced(query: String): List<OverpassElement> =
        coroutineScope {
            val channel = Channel<List<OverpassElement>?>(endpoints.size)
            val jobs =
                endpoints.map { endpoint ->
                    launch { channel.send(runCatching { runOverpassQuery(query, endpoint) }.getOrNull()) }
                }
            var result: List<OverpassElement> = emptyList()
            var received = 0
            while (received < endpoints.size) {
                val value = channel.receive()
                received++
                if (!value.isNullOrEmpty()) {
                    result = value
                    break
                }
            }
            jobs.forEach { it.cancel() }
            result
        }

    private suspend fun runOverpassQuery(
        query: String,
        endpoint: String,
    ): List<OverpassElement> {
        val response =
            client.post(endpoint) {
                timeout {
                    requestTimeoutMillis = OVERPASS_REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = OVERPASS_REQUEST_TIMEOUT_MS
                }
                setBody(FormDataContent(Parameters.build { append("data", query) }))
            }
        return response.body<OverpassResponse>().elements
    }

    private fun courseAreaHoleQuery(
        osmType: String,
        id: Long,
    ): String =
        """
        [out:json][timeout:$OVERPASS_TIMEOUT_SECONDS];
        $osmType($id);
        map_to_area -> .course;
        (
          way(area.course)["golf"="hole"];
        );
        out geom;
        """.trimIndent()

    private fun effectivePlayerLocation(
        userLocation: LatLng?,
        courseCenter: LatLng,
    ): LatLng? {
        if (userLocation == null) return null
        return if (GeoMath.yards(courseCenter, userLocation) <= AROUND_RADIUS_METERS) userLocation else null
    }

    private fun isDiscoveryPlausible(
        discoveredCenter: LatLng,
        openGolfCenter: LatLng,
    ): Boolean = GeoMath.yards(openGolfCenter, discoveredCenter) <= MAX_DISCOVERY_OFFSET_YARDS

    private fun courseNameMatchScore(
        apiName: String,
        osmName: String,
    ): Int {
        val api = normalizedCourseName(apiName)
        val osm = normalizedCourseName(osmName)
        if (api.isEmpty() || osm.isEmpty()) return 0
        if (api == osm) return 100

        val shared = nameTokens(apiName).intersect(nameTokens(osmName))
        if (shared.size >= 2) return 50 + shared.size

        if (api.length < 8 || osm.length < 8) return 0
        if (api.contains(osm) || osm.contains(api)) return 10
        return 0
    }

    private fun nameTokens(name: String): Set<String> =
        name
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { token -> token.length >= 3 && token !in NAME_STOP_WORDS }
            .toSet()

    private fun normalizedCourseName(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    companion object {
        val DEFAULT_ENDPOINTS =
            listOf(
                "https://overpass.kumi.systems/api/interpreter",
                "https://overpass-api.de/api/interpreter",
            )
        private val AREA_OSM_TYPES = setOf("way", "relation")
        private val FEATURE_QUERIES =
            listOf(
                "way[\"golf\"=\"hole\"]",
                "way[\"golf\"=\"fairway\"]",
                "way[\"golf\"=\"green\"]",
                "way[\"golf\"=\"tee\"]",
                "node[\"golf\"=\"green\"]",
                "node[\"golf\"=\"pin\"]",
            )
        private const val AROUND_RADIUS_METERS = 8_000
        private const val OVERPASS_TIMEOUT_SECONDS = 25

        // `map_to_area` on a large relation can take ~40s on a loaded public Overpass instance, so
        // the per-request budget must exceed the server-side `[timeout:25]` plus transfer/queue time.
        private const val OVERPASS_REQUEST_TIMEOUT_MS = 50_000L
        private const val MAX_DISCOVERY_OFFSET_YARDS = 2_500
        private val NAME_STOP_WORDS = setOf("golf", "course", "club", "links", "the", "and")
    }
}
