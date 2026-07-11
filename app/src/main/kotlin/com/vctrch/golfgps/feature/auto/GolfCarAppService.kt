package com.vctrch.golfgps.feature.auto

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import com.vctrch.golfgps.BuildConfig
import com.vctrch.golfgps.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class GolfCarAppService : CarAppService() {
    @Inject
    lateinit var activeRoundSession: ActiveRoundSession

    override fun createHostValidator(): HostValidator {
        return if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(R.array.android_auto_hosts_allowlist)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        return GolfCarSession(activeRoundSession)
    }

    @Deprecated("Deprecated in Java")
    override fun onCreateSession(): Session {
        return GolfCarSession(activeRoundSession)
    }
}
