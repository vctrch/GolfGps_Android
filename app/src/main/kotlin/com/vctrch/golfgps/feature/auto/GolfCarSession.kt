package com.vctrch.golfgps.feature.auto

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class GolfCarSession(
    private val activeRoundSession: ActiveRoundSession,
) : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    activeRoundSession.setAutoConnected(true)
                }

                override fun onStop(owner: LifecycleOwner) {
                    activeRoundSession.setAutoConnected(false)
                }
            },
        )
        return RootCarScreen(carContext, activeRoundSession)
    }
}
