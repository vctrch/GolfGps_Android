package com.vctrch.golfgps.domain

/** How a tee coordinate was derived — mirrors iOS `TeeMappingSource`. */
enum class TeeMappingSource {
    /** OSM `golf=tee` tagged for this hole. */
    TAGGED,

    /** Orphan tee box matched using scorecard yardage. */
    MATCHED,

    /** Tee end of a fairway / `golf=hole` way — not a mapped tee box. */
    FAIRWAY,

    /** Estimated during gap-fill between mapped neighbors. */
    INFERRED,
}

enum class GreenMappingConfidence {
    LOADING,
    ESTIMATED,
    MAPPED,
    ;

    val shortLabel: String
        get() =
            when (this) {
                LOADING -> "Map loading"
                ESTIMATED -> "Estimated green"
                MAPPED -> "Mapped green"
            }

    val detail: String
        get() =
            when (this) {
                LOADING -> "Waiting for hole map data — yardage unavailable until OpenStreetMap loads."
                ESTIMATED -> "Green placed between neighboring mapped holes because this fairway is missing on the map."
                MAPPED -> "Green position comes from community map data for this hole."
            }
}

enum class TeeMappingConfidence {
    UNAVAILABLE,
    NOT_MAPPED,
    FAIRWAY_DERIVED,
    ESTIMATED,
    MATCHED,
    MAPPED,
    ;

    val shortLabel: String
        get() =
            when (this) {
                UNAVAILABLE -> "Tee unavailable"
                NOT_MAPPED -> "Tee not on map"
                FAIRWAY_DERIVED, MATCHED, ESTIMATED -> "Possible tee"
                MAPPED -> "Mapped tee"
            }

    val detail: String
        get() =
            when (this) {
                UNAVAILABLE -> "Tee yardage appears once the hole green is mapped."
                NOT_MAPPED -> "No tee box marked for this hole — we only show tees when the map supports them."
                FAIRWAY_DERIVED, MATCHED, ESTIMATED ->
                    "Tee is approximate — treat yardage as a guide, not an exact pin."
                MAPPED -> "Tee position comes from a mapped tee box on the community map."
            }
}

val HoleTarget.greenMappingConfidence: GreenMappingConfidence
    get() =
        when (source) {
            HoleTargetSource.SCORECARD_FALLBACK -> GreenMappingConfidence.LOADING
            HoleTargetSource.OPEN_STREET_MAP_INFERRED -> GreenMappingConfidence.ESTIMATED
            HoleTargetSource.OPEN_STREET_MAP -> GreenMappingConfidence.MAPPED
        }

val HoleTarget.resolvedTeeSource: TeeMappingSource?
    get() = if (tee == null) null else teeSource

val HoleTarget.teeMappingConfidence: TeeMappingConfidence
    get() {
        if (!hasReliableGreenPosition) return TeeMappingConfidence.UNAVAILABLE
        if (tee == null) return TeeMappingConfidence.NOT_MAPPED
        return when (resolvedTeeSource) {
            TeeMappingSource.TAGGED -> TeeMappingConfidence.MAPPED
            TeeMappingSource.MATCHED -> TeeMappingConfidence.MATCHED
            TeeMappingSource.FAIRWAY -> TeeMappingConfidence.FAIRWAY_DERIVED
            TeeMappingSource.INFERRED -> TeeMappingConfidence.ESTIMATED
            null -> TeeMappingConfidence.NOT_MAPPED
        }
    }

val HoleTarget.hasConfirmedTeeOnMap: Boolean
    get() = resolvedTeeSource == TeeMappingSource.TAGGED

val HoleTarget.showsEstimatedQualifier: Boolean
    get() = greenMappingConfidence == GreenMappingConfidence.ESTIMATED

val LoadedCourse.greenMappedCount: Int
    get() = holes.count { it.greenMappingConfidence == GreenMappingConfidence.MAPPED }

val LoadedCourse.greenEstimatedCount: Int
    get() = holes.count { it.greenMappingConfidence == GreenMappingConfidence.ESTIMATED }

val LoadedCourse.greenLoadingCount: Int
    get() = holes.count { it.greenMappingConfidence == GreenMappingConfidence.LOADING }

val LoadedCourse.teeOnMapCount: Int
    get() = holes.count { it.teeMappingConfidence == TeeMappingConfidence.MAPPED }

val LoadedCourse.teePossibleCount: Int
    get() =
        holes.count {
            when (it.teeMappingConfidence) {
                TeeMappingConfidence.MATCHED,
                TeeMappingConfidence.FAIRWAY_DERIVED,
                TeeMappingConfidence.ESTIMATED,
                -> true
                else -> false
            }
        }

val LoadedCourse.teeNotOnMapCount: Int
    get() = holes.count { it.teeMappingConfidence == TeeMappingConfidence.NOT_MAPPED }
