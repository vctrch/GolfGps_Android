package com.vctrch.golfgps

import android.app.Application
import com.vctrch.golfgps.data.analytics.GolfAnalytics
import com.vctrch.golfgps.data.local.UserPreferencesRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import javax.inject.Inject

@HiltAndroidApp
class GolfGpsApplication : Application() {
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    @Inject
    lateinit var analytics: GolfAnalytics

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        applicationScope.launch {
            userPreferencesRepository.usageDiagnosticsEnabled.collectLatest { enabled ->
                analytics.setCollectionEnabled(enabled)
            }
        }
    }
}
