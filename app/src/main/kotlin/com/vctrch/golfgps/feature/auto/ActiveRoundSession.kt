package com.vctrch.golfgps.feature.auto

import com.vctrch.golfgps.domain.GeoMath
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LatLng
import com.vctrch.golfgps.domain.LoadedCourse
import com.vctrch.golfgps.domain.showsEstimatedQualifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared round snapshot for Android Auto. The phone [com.vctrch.golfgps.feature.round.RoundViewModel]
 * publishes state here; Car App screens read it and invoke hole-navigation callbacks.
 */
@Singleton
class ActiveRoundSession
    @Inject
    constructor() {
        data class Snapshot(
            val isAutoConnected: Boolean = false,
            val isRoundReady: Boolean = false,
            val courseName: String? = null,
            val courseId: String? = null,
            val hole: HoleTarget? = null,
            val holeIndex: Int = 0,
            val holeCount: Int = 0,
            val selectedHoleNumber: Int = 1,
            val userLocation: LatLng? = null,
            val yardsToGreen: Int? = null,
            val yardsToTee: Int? = null,
            val teeToPinYards: Int? = null,
        )

        private val _snapshot = MutableStateFlow(Snapshot())
        val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

        @Volatile
        private var onPreviousHole: (() -> Unit)? = null

        @Volatile
        private var onNextHole: (() -> Unit)? = null

        fun bindHoleActions(
            onPrevious: () -> Unit,
            onNext: () -> Unit,
        ) {
            onPreviousHole = onPrevious
            onNextHole = onNext
        }

        fun unbindHoleActions() {
            onPreviousHole = null
            onNextHole = null
        }

        fun setAutoConnected(connected: Boolean) {
            _snapshot.update { it.copy(isAutoConnected = connected) }
        }

        fun publish(
            loadedCourse: LoadedCourse?,
            selectedHoleNumber: Int,
            userLocation: LatLng?,
        ) {
            val hole = loadedCourse?.holes?.firstOrNull { it.number == selectedHoleNumber }
            val holeIndex =
                loadedCourse?.holes?.indexOfFirst { it.number == selectedHoleNumber }?.coerceAtLeast(0) ?: 0
            val yardsToGreen =
                if (hole != null && userLocation != null) hole.playerYardsToGreen(userLocation) else null
            val yardsToTee =
                if (hole != null && userLocation != null) hole.playerYardsToTee(userLocation) else null
            val teeToPinYards = hole?.holeLengthYards()
            _snapshot.update {
                it.copy(
                    isRoundReady = loadedCourse != null && hole != null,
                    courseName = loadedCourse?.summary?.name,
                    courseId = loadedCourse?.summary?.id,
                    hole = hole,
                    holeIndex = holeIndex,
                    holeCount = loadedCourse?.holes?.size ?: 0,
                    selectedHoleNumber = selectedHoleNumber,
                    userLocation = userLocation,
                    yardsToGreen = yardsToGreen,
                    yardsToTee = yardsToTee,
                    teeToPinYards = teeToPinYards,
                )
            }
        }

        fun clearRound() {
            _snapshot.update {
                Snapshot(isAutoConnected = it.isAutoConnected)
            }
        }

        fun previousHole() {
            onPreviousHole?.invoke()
        }

        fun nextHole() {
            onNextHole?.invoke()
        }

        companion object {
            fun yardageDetail(
                greenDistance: Int?,
                holeMapped: Boolean,
                estimatedGreen: Boolean = false,
                teeToPinYards: Int? = null,
            ): String {
                if (greenDistance != null) {
                    val yards = GeoMath.formattedYardage(greenDistance)
                    return if (estimatedGreen) "$yards yds to pin (est.)" else "$yards yds to pin"
                }
                if (teeToPinYards != null) {
                    val yards = GeoMath.formattedYardage(teeToPinYards)
                    return if (estimatedGreen) "$yards yds tee to pin (est.)" else "$yards yds tee to pin"
                }
                if (holeMapped) {
                    return if (estimatedGreen) "Estimated green · waiting for GPS" else "Waiting for GPS"
                }
                return "Green position pending"
            }

            fun teeYardageDetail(
                teeDistance: Int?,
                hole: HoleTarget,
            ): String {
                if (teeDistance != null) {
                    return "${GeoMath.formattedYardage(teeDistance)} yds to tee"
                }
                if (hole.tee != null) {
                    return "Waiting for GPS"
                }
                return "Tee not mapped"
            }

            fun poiPickerTitle(holeNumber: Int): String = "Hole $holeNumber"

            fun Snapshot.greenYardageLabel(): String {
                val currentHole = hole ?: return "Green position pending"
                return yardageDetail(
                    greenDistance = yardsToGreen,
                    holeMapped = currentHole.hasReliableGreenPosition,
                    estimatedGreen = currentHole.showsEstimatedQualifier,
                    teeToPinYards = teeToPinYards,
                )
            }

            fun Snapshot.teeYardageLabel(): String {
                val currentHole = hole ?: return "Tee not mapped"
                return teeYardageDetail(yardsToTee, currentHole)
            }
        }
    }
