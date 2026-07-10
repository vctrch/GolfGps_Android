package com.vctrch.golfgps.data.remote

import com.vctrch.golfgps.domain.ScorecardHole

/** Picks a default OpenGolf tee set and merges published yardages into the scorecard. */
object OpenGolfTeeYardages {
    private val preferredNames = listOf("white", "blue", "gold", "green", "red", "yellow", "black")

    fun preferredTeeKey(tees: List<OpenGolfTeeSet>): String? {
        if (tees.isEmpty()) return null
        val ranked = tees.sortedBy { it.yardage ?: 0 }
        for (name in preferredNames) {
            ranked.firstOrNull { it.teeKey.lowercase().contains(name) }?.let { return it.teeKey }
        }
        return if (ranked.size == 1) ranked[0].teeKey else ranked[ranked.size / 2].teeKey
    }

    fun yardageLookupKey(teeKey: String): String {
        val normalized = teeKey.lowercase()
        val dash = normalized.indexOf('-')
        return if (dash >= 0) normalized.substring(0, dash) else normalized
    }

    fun yardageByHole(
        detail: OpenGolfCourseDetail,
        teeKey: String? = null,
    ): Map<Int, Int> {
        val holes = detail.holesData.orEmpty()
        if (holes.isEmpty()) return emptyMap()
        val resolvedKey = teeKey ?: preferredTeeKey(detail.tees.orEmpty()) ?: return emptyMap()
        val lookup = yardageLookupKey(resolvedKey)
        return holes.mapNotNull { hole ->
            val number = hole.number ?: return@mapNotNull null
            val yards = resolveYardage(hole.yardages, resolvedKey, lookup) ?: return@mapNotNull null
            number to yards
        }.toMap()
    }

    fun mergeYardages(
        scorecard: List<ScorecardHole>,
        yardageByHole: Map<Int, Int>,
    ): List<ScorecardHole> {
        if (yardageByHole.isEmpty()) return scorecard
        return scorecard.map { hole ->
            val yardage = yardageByHole[hole.number] ?: return@map hole
            hole.copy(yardage = yardage)
        }
    }

    private fun resolveYardage(
        yardages: Map<String, Int>?,
        teeKey: String,
        lookup: String,
    ): Int? {
        if (yardages.isNullOrEmpty()) return null
        val normalized = yardages.mapKeys { it.key.lowercase() }
        normalized[lookup]?.let { return it }
        normalized[teeKey.lowercase()]?.let { return it }
        for ((key, value) in normalized) {
            if (key.startsWith(lookup) || lookup.startsWith(key)) return value
        }
        return null
    }
}
