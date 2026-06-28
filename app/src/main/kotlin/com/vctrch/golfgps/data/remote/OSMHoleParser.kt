package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.GeoMath
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.HoleTargetSource
import com.vctrch.golfgps.domain.LatLng
import com.vctrch.golfgps.domain.ScorecardHole

/**
 * Turns raw OpenStreetMap golf geometry (Overpass elements) into per-hole [HoleTarget]s with real
 * tee and green positions. Ported from the iOS app's `OSMHoleParser` so Android matches it feature
 * for feature: `golf=hole` ways, `golf=fairway` + `ref` components, `golf=tee` / `golf=green` /
 * `golf=pin` nodes, dogleg-aware endpoints, tee/green orientation, and inferred gap holes.
 */
object OSMHoleParser {
    private const val MIN_HOLE_LENGTH_YARDS = 40
    private const val MAX_HOLE_LENGTH_YARDS = 900
    private const val MAX_GREEN_DISTANCE_FROM_COURSE_YARDS = 3_500
    private const val UNNUMBERED_GREEN_MATCH_YARDS = 450

    private data class RawHole(
        val number: Int,
        val tee: LatLng,
        val green: LatLng,
        val par: Int?,
        // When true, tee/green were already oriented from fairway/tee/green tags — don't re-route.
        val trustOrientation: Boolean,
    )

    private data class NumberedCoordinate(
        val number: Int?,
        val coordinate: LatLng,
    )

    fun holeTargets(
        elements: List<OverpassElement>,
        scorecard: List<ScorecardHole>,
        courseCenter: LatLng,
        trustCourseArea: Boolean = false,
        playerLocation: LatLng? = null,
    ): List<HoleTarget> {
        val parByHole = scorecard.mapNotNull { hole -> hole.par?.let { hole.number to it } }.toMap()
        val greens = mutableListOf<NumberedCoordinate>()
        val pins = mutableListOf<NumberedCoordinate>()
        val holeWays = mutableListOf<RawHole>()

        for (element in elements) {
            val tags = element.tags ?: continue
            val number = holeNumber(tags)
            when (tags["golf"]) {
                "hole" -> {
                    val geometry = element.geometry ?: continue
                    if (geometry.size < 2) continue
                    val coords = geometry.map { LatLng(it.lat, it.lon) }
                    val (firstCoord, lastCoord) = wayEndpoints(coords)
                    val resolvedNumber = number ?: (holeWays.size + 1)
                    val par = tags["par"]?.toIntOrNull() ?: parByHole[resolvedNumber]
                    holeWays.add(
                        RawHole(
                            number = resolvedNumber,
                            tee = firstCoord,
                            green = lastCoord,
                            par = par,
                            trustOrientation = false,
                        ),
                    )
                }
                "green" -> {
                    (element.coordinate ?: GeoMath.centroid(element.geometryCoordinates))?.let {
                        greens.add(NumberedCoordinate(number, it))
                    }
                }
                "pin" -> {
                    (element.coordinate ?: GeoMath.centroid(element.geometryCoordinates))?.let {
                        pins.add(NumberedCoordinate(number, it))
                    }
                }
            }
        }

        val merged =
            mergeRawHoles(
                holeWays = holeWays,
                components = componentHoles(elements, parByHole),
            )

        val mapped =
            finalizeHoles(
                rawHoles = merged.sortedBy { it.number },
                pins = pins,
                greens = greens,
                parByHole = parByHole,
                courseCenter = courseCenter,
                trustCourseArea = trustCourseArea,
                playerLocation = playerLocation,
            )
        val unnumberedGreens = greens.filter { it.number == null }.map { it.coordinate }
        return fillSequentialGaps(
            holes = mapped,
            scorecard = scorecard,
            unnumberedGreens = unnumberedGreens,
            trustCourseArea = trustCourseArea,
        )
    }

    fun holeNumber(tags: Map<String, String>): Int? {
        tags["ref"]?.let { parseNumericHoleTag(it) }?.let { return it }
        tags["hole"]?.let { parseNumericHoleTag(it) }?.let { return it }
        tags["name"]?.let { parseHoleNumberFromLabel(it) }?.let { return it }
        tags["description"]?.let { parseHoleNumberFromLabel(it) }?.let { return it }
        return null
    }

    /** Accepts `4`, `04`, and labels like `Hole 4` / `#4` used on some OSM fairways and greens. */
    private fun parseNumericHoleTag(value: String): Int? {
        val trimmed = value.trim()
        trimmed.toIntOrNull()?.let { return it }
        return parseHoleNumberFromLabel(trimmed)
    }

    private fun parseHoleNumberFromLabel(label: String): Int? {
        val normalized = label.lowercase()
        normalized.filter { it.isDigit() }.toIntOrNull()?.let { if (it in 1..18) return it }
        val tokens = normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        for (index in tokens.indices) {
            if (tokens[index] == "hole" && index + 1 < tokens.size) {
                tokens[index + 1].toIntOrNull()?.let { if (it in 1..18) return it }
            }
            tokens[index].toIntOrNull()?.let { if (it in 1..18) return it }
        }
        return null
    }

    /** Courses like Salina Country Club map `golf=fairway` + `ref` per hole instead of `golf=hole` ways. */
    private fun componentHoles(
        elements: List<OverpassElement>,
        parByHole: Map<Int, Int>,
    ): List<RawHole> {
        val fairways = mutableMapOf<Int, List<LatLng>>()
        val tees = mutableMapOf<Int, LatLng>()
        val holeGreens = mutableMapOf<Int, LatLng>()
        val unnumberedGreens = mutableListOf<LatLng>()

        for (element in elements) {
            val tags = element.tags ?: continue
            val number = holeNumber(tags)
            when (tags["golf"]) {
                "fairway" -> {
                    val ref = number ?: continue
                    val coords = element.geometryCoordinates
                    if (coords.size < 2) continue
                    val existing = fairways[ref]
                    if (existing == null || fairwaySpanYards(coords) > fairwaySpanYards(existing)) {
                        fairways[ref] = coords
                    }
                }
                "tee" -> {
                    val ref = number ?: continue
                    val coordinate = element.coordinate ?: GeoMath.centroid(element.geometryCoordinates) ?: continue
                    tees[ref] = coordinate
                }
                "green" -> {
                    val coordinate = element.coordinate ?: GeoMath.centroid(element.geometryCoordinates) ?: continue
                    if (number != null) {
                        holeGreens[number] = coordinate
                    } else {
                        unnumberedGreens.add(coordinate)
                    }
                }
            }
        }

        associateUnnumberedGreens(fairways, tees, holeGreens, unnumberedGreens)

        val refs = (fairways.keys + tees.keys + holeGreens.keys).toSortedSet()
        return refs.mapNotNull { ref ->
            val fairway = fairways[ref]
            if (fairway != null) {
                val (endA, endB) = wayEndpoints(fairway)
                val tee = tees[ref] ?: endA
                val oriented = orientUsingExplicitTee(tee, endA, endB, holeGreens[ref])
                RawHole(
                    number = ref,
                    tee = oriented.first,
                    green = oriented.second,
                    par = parByHole[ref],
                    trustOrientation = true,
                )
            } else {
                val tee = tees[ref]
                val green = holeGreens[ref]
                if (tee != null && green != null) {
                    RawHole(ref, tee, green, parByHole[ref], trustOrientation = true)
                } else {
                    null
                }
            }
        }
    }

    /** Match `golf=green` features missing a hole number to the nearest fairway end. */
    private fun associateUnnumberedGreens(
        fairways: Map<Int, List<LatLng>>,
        tees: Map<Int, LatLng>,
        holeGreens: MutableMap<Int, LatLng>,
        candidates: List<LatLng>,
    ) {
        for (greenCoord in candidates) {
            var bestRef: Int? = null
            var bestDistance = Int.MAX_VALUE
            for ((ref, fairway) in fairways) {
                if (holeGreens[ref] != null) continue
                val (endA, endB) = wayEndpoints(fairway)
                val tee = tees[ref] ?: endA
                val greenEnd = preferredGreenEnd(tee, endA, endB)
                val distance = GeoMath.yards(greenCoord, greenEnd)
                if (distance <= UNNUMBERED_GREEN_MATCH_YARDS && distance < bestDistance) {
                    bestDistance = distance
                    bestRef = ref
                }
            }
            bestRef?.let { holeGreens[it] = greenCoord }
        }
    }

    private fun preferredGreenEnd(
        tee: LatLng,
        endA: LatLng,
        endB: LatLng,
    ): LatLng = if (GeoMath.yards(tee, endA) >= GeoMath.yards(tee, endB)) endA else endB

    private fun mergeRawHoles(
        holeWays: List<RawHole>,
        components: List<RawHole>,
    ): List<RawHole> {
        val byNumber = components.associateBy { it.number }.toMutableMap()
        for (hole in holeWays) {
            if (byNumber[hole.number] == null) byNumber[hole.number] = hole
        }
        return byNumber.values.toList()
    }

    private fun fairwaySpanYards(coords: List<LatLng>): Int {
        val (endA, endB) = wayEndpoints(coords)
        return GeoMath.yards(endA, endB)
    }

    private fun orientUsingExplicitTee(
        explicitTee: LatLng,
        endA: LatLng,
        endB: LatLng,
        explicitGreen: LatLng?,
    ): Pair<LatLng, LatLng> {
        val distA = GeoMath.yards(explicitTee, endA)
        val distB = GeoMath.yards(explicitTee, endB)
        var tee = if (distA <= distB) endA else endB
        var green = if (distA <= distB) endB else endA
        if (explicitGreen != null) {
            green = explicitGreen
            if (GeoMath.yards(tee, green) < MIN_HOLE_LENGTH_YARDS) {
                tee = if (distA <= distB) endB else endA
            }
        }
        return tee to green
    }

    private fun finalizeHoles(
        rawHoles: List<RawHole>,
        pins: List<NumberedCoordinate>,
        greens: List<NumberedCoordinate>,
        parByHole: Map<Int, Int>,
        courseCenter: LatLng,
        trustCourseArea: Boolean,
        playerLocation: LatLng?,
    ): List<HoleTarget> {
        var previousGreen: LatLng? = null
        val results = mutableListOf<HoleTarget>()

        for (hole in rawHoles) {
            val hasTaggedGreen = hasExplicitGreen(hole.number, pins, greens)
            val oriented: Pair<LatLng, LatLng> =
                if (hole.trustOrientation || hasTaggedGreen) {
                    hole.tee to hole.green
                } else {
                    orientTeeAndGreen(hole.tee, hole.green, previousGreen, courseCenter, playerLocation)
                }

            val length = GeoMath.yards(oriented.first, oriented.second)
            val lengthOk = length in MIN_HOLE_LENGTH_YARDS..MAX_HOLE_LENGTH_YARDS
            val explicit = hole.trustOrientation || hasTaggedGreen
            if (length < MIN_HOLE_LENGTH_YARDS || !(lengthOk || explicit)) continue

            val greenCoord = resolveGreen(hole.number, oriented.second, pins, greens)

            if (!trustCourseArea) {
                if (GeoMath.yards(courseCenter, greenCoord) > MAX_GREEN_DISTANCE_FROM_COURSE_YARDS) continue
            }

            previousGreen = greenCoord
            results.add(
                HoleTarget(
                    number = hole.number,
                    par = hole.par ?: parByHole[hole.number],
                    tee = oriented.first,
                    green = greenCoord,
                    source = HoleTargetSource.OPEN_STREET_MAP,
                ),
            )
        }

        return results
    }

    private fun hasExplicitGreen(
        holeNumber: Int,
        pins: List<NumberedCoordinate>,
        greens: List<NumberedCoordinate>,
    ): Boolean = pins.any { it.number == holeNumber } || greens.any { it.number == holeNumber }

    /** Farthest pair of nodes on the hole way — tee and green for doglegs, not just first/last. */
    private fun wayEndpoints(coords: List<LatLng>): Pair<LatLng, LatLng> {
        val first = coords.firstOrNull() ?: return LatLng(0.0, 0.0) to LatLng(0.0, 0.0)
        if (coords.size < 2) return first to first

        var bestDistance = 0
        var bestPair = coords[0] to coords[coords.size - 1]
        for (i in coords.indices) {
            for (j in (i + 1) until coords.size) {
                val distance = GeoMath.yards(coords[i], coords[j])
                if (distance > bestDistance) {
                    bestDistance = distance
                    bestPair = coords[i] to coords[j]
                }
            }
        }
        return bestPair
    }

    /** Tee is near the previous green (or course center on hole 1); green is the other end. */
    private fun orientTeeAndGreen(
        tee: LatLng,
        green: LatLng,
        previousGreen: LatLng?,
        courseCenter: LatLng,
        playerLocation: LatLng?,
    ): Pair<LatLng, LatLng> {
        val reference = previousGreen ?: playerLocation ?: courseCenter
        val teeDist = GeoMath.yards(reference, tee)
        val greenDist = GeoMath.yards(reference, green)
        return if (teeDist <= greenDist) tee to green else green to tee
    }

    private fun resolveGreen(
        holeNumber: Int,
        orientedGreen: LatLng,
        pins: List<NumberedCoordinate>,
        greens: List<NumberedCoordinate>,
    ): LatLng {
        pins.firstOrNull { it.number == holeNumber }?.let { return it.coordinate }
        greens.firstOrNull { it.number == holeNumber }?.let { return it.coordinate }
        return orientedGreen
    }

    /** Fills scorecard holes missing from OSM (e.g. Salina CC has fairways 5 and 8 but not 6-7). */
    private fun fillSequentialGaps(
        holes: List<HoleTarget>,
        scorecard: List<ScorecardHole>,
        unnumberedGreens: List<LatLng>,
        trustCourseArea: Boolean,
    ): List<HoleTarget> {
        if (!trustCourseArea) return holes

        val byNumber = holes.associateBy { it.number }.toMutableMap()
        val parByHole = scorecard.mapNotNull { hole -> hole.par?.let { hole.number to it } }.toMap()
        val scorecardNumbers = scorecard.map { it.number }.sorted()
        if (scorecardNumbers.isEmpty()) return holes

        for (number in scorecardNumbers) {
            if (byNumber[number] != null) continue
            val lowerNumber = scorecardNumbers.lastOrNull { it < number && byNumber[it] != null } ?: continue
            val lower = byNumber[lowerNumber] ?: continue
            val upperNumber = scorecardNumbers.firstOrNull { it > number && byNumber[it] != null } ?: continue
            val upper = byNumber[upperNumber] ?: continue
            val gapSpan = upperNumber - lowerNumber
            if (gapSpan < 2 || gapSpan > 4) continue

            val upperAnchor = upper.tee ?: upper.green
            val corridorStart = lower.green
            val totalCorridorYards = GeoMath.yards(corridorStart, upperAnchor)
            if (totalCorridorYards <= MIN_HOLE_LENGTH_YARDS * gapSpan) continue

            val step = number - lowerNumber
            val tee =
                teeForGapHole(number, step, gapSpan, corridorStart, upperAnchor, totalCorridorYards, byNumber)
            val green =
                greenForGapHole(step, gapSpan, corridorStart, upperAnchor, totalCorridorYards, unnumberedGreens)

            if (GeoMath.yards(tee, green) < MIN_HOLE_LENGTH_YARDS) continue

            byNumber[number] =
                HoleTarget(
                    number = number,
                    par = parByHole[number],
                    tee = tee,
                    green = green,
                    source = HoleTargetSource.OPEN_STREET_MAP_INFERRED,
                )
        }

        return byNumber.values.sortedBy { it.number }
    }

    /** Fills missing scorecard holes when neighboring holes already have OSM coordinates. */
    fun fillGapHoles(
        allHoles: List<HoleTarget>,
        scorecard: List<ScorecardHole>,
    ): List<HoleTarget> {
        if (scorecard.isEmpty()) return allHoles
        val mapped = allHoles.filter { it.hasReliableGreenPosition }
        if (mapped.size < 2) return allHoles

        val gapFilled =
            fillSequentialGaps(
                holes = mapped,
                scorecard = scorecard,
                unnumberedGreens = emptyList(),
                trustCourseArea = true,
            )
        val gapByNumber = gapFilled.associateBy { it.number }
        return allHoles
            .map { gapByNumber[it.number] ?: it }
            .sortedBy { it.number }
    }

    private fun teeForGapHole(
        number: Int,
        step: Int,
        gapSpan: Int,
        corridorStart: LatLng,
        upperAnchor: LatLng,
        totalCorridorYards: Int,
        byNumber: Map<Int, HoleTarget>,
    ): LatLng {
        byNumber[number - 1]?.let { return it.green }
        if (step <= 1) return corridorStart
        val priorStep = step - 1
        val priorDistance =
            maxOf(
                MIN_HOLE_LENGTH_YARDS,
                Math.round(totalCorridorYards.toDouble() * priorStep / gapSpan).toInt(),
            )
        return GeoMath.coordinate(
            corridorStart,
            upperAnchor,
            minOf(priorDistance, totalCorridorYards - MIN_HOLE_LENGTH_YARDS),
        )
    }

    private fun greenForGapHole(
        step: Int,
        gapSpan: Int,
        corridorStart: LatLng,
        upperAnchor: LatLng,
        totalCorridorYards: Int,
        unnumberedGreens: List<LatLng>,
    ): LatLng {
        val distanceAlong =
            maxOf(
                MIN_HOLE_LENGTH_YARDS,
                minOf(
                    totalCorridorYards - MIN_HOLE_LENGTH_YARDS,
                    Math.round(totalCorridorYards.toDouble() * step / gapSpan).toInt(),
                ),
            )
        val estimatedGreen = GeoMath.coordinate(corridorStart, upperAnchor, distanceAlong)
        return nearestGreen(estimatedGreen, unnumberedGreens, maxDeltaYards = 220) ?: estimatedGreen
    }

    private fun nearestGreen(
        target: LatLng,
        candidates: List<LatLng>,
        maxDeltaYards: Int,
    ): LatLng? {
        val nearest = candidates.minByOrNull { GeoMath.yards(target, it) } ?: return null
        return if (GeoMath.yards(target, nearest) <= maxDeltaYards) nearest else null
    }

    /** Prefer OSM coordinates per hole number; keep scorecard holes OSM didn't map. */
    fun mergeHoles(
        baseline: List<HoleTarget>,
        osm: List<HoleTarget>,
        scorecard: List<ScorecardHole> = emptyList(),
    ): List<HoleTarget> {
        val osmByNumber = osm.filter { it.hasReliableGreenPosition }.associateBy { it.number }
        val merged = baseline.map { hole -> osmByNumber[hole.number] ?: hole }
        val baselineNumbers = baseline.map { it.number }.toSet()
        val extra = osm.filter { it.number !in baselineNumbers }.sortedBy { it.number }
        val combined = (merged + extra).sortedBy { it.number }
        return fillGapHoles(combined, scorecard)
    }
}
