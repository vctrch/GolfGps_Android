package com.vctrch.golfgps.feature.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Switches between idle and active-round templates as the phone round state changes.
 * Invalidates on snapshot updates so the host re-requests [onGetTemplate].
 */
class RootCarScreen(
    carContext: CarContext,
    private val activeRoundSession: ActiveRoundSession,
) : Screen(carContext) {
    private var collectJob: Job? = null
    private var showsTeeOnMap = false
    private var lastCourseId: String? = null
    private var lastHoleNumber: Int? = null

    init {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    collectJob =
                        lifecycleScope.launch {
                            activeRoundSession.snapshot.collectLatest { snapshot ->
                                if (snapshot.courseId != lastCourseId ||
                                    snapshot.selectedHoleNumber != lastHoleNumber
                                ) {
                                    showsTeeOnMap = false
                                    lastCourseId = snapshot.courseId
                                    lastHoleNumber = snapshot.selectedHoleNumber
                                }
                                invalidate()
                            }
                        }
                }

                override fun onStop(owner: LifecycleOwner) {
                    collectJob?.cancel()
                    collectJob = null
                }
            },
        )
    }

    override fun onGetTemplate(): Template {
        val snapshot = activeRoundSession.snapshot.value
        return if (snapshot.isRoundReady && snapshot.hole != null) {
            ActiveRoundCarTemplates.build(
                carContext = carContext,
                snapshot = snapshot,
                showsTeeOnMap = showsTeeOnMap,
                onPrevious = {
                    activeRoundSession.previousHole()
                    invalidate()
                },
                onNext = {
                    activeRoundSession.nextHole()
                    invalidate()
                },
                onToggleTee = {
                    showsTeeOnMap = !showsTeeOnMap
                    invalidate()
                },
                onRefresh = { invalidate() },
            )
        } else {
            IdleCarTemplates.build(carContext)
        }
    }
}
