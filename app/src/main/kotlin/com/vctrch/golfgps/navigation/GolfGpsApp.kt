package com.vctrch.golfgps.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vctrch.golfgps.feature.round.*
import com.vctrch.golfgps.feature.search.*
import com.vctrch.golfgps.ui.theme.GolfGpsTheme

@Composable
fun GolfGpsApp() {
    GolfGpsTheme {
        RequestLocationPermissionOnLaunch()
        val viewModel: RoundViewModel = hiltViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val mapDisplayStyle by viewModel.mapDisplayStyle.collectAsStateWithLifecycle()

        when {
            state.isLoadingCourse -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.isRoundReady -> {
                ActiveRoundScreen(
                    state = state,
                    mapDisplayStyle = mapDisplayStyle,
                    onMapDisplayStyleChange = viewModel::setMapDisplayStyle,
                    onEndRound = viewModel::endRound,
                    onSelectHole = viewModel::selectHole,
                    onPreviousHole = viewModel::previousHole,
                    onNextHole = viewModel::nextHole,
                )
            }
            else -> {
                CourseSearchScreen(
                    state = state,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    onClearSearch = viewModel::clearSearch,
                    onCourseSelected = viewModel::selectCourse,
                )
            }
        }
    }
}

@Composable
private fun RequestLocationPermissionOnLaunch() {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { },
        )

    LaunchedEffect(Unit) {
        val fineGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!fineGranted || !coarseGranted) {
            launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}
