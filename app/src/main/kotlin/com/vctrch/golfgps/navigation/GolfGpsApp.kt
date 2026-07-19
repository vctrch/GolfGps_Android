package com.vctrch.golfgps.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vctrch.golfgps.R
import com.vctrch.golfgps.feature.round.*
import com.vctrch.golfgps.feature.search.*
import com.vctrch.golfgps.ui.theme.GolfGpsTheme

@Composable
fun GolfGpsApp() {
    GolfGpsTheme {
        val viewModel: RoundViewModel = hiltViewModel()
        RequestLocationPermissionWithRationale(onPermissionGranted = viewModel::refreshLocationUpdates)
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val mapDisplayStyle by viewModel.mapDisplayStyle.collectAsStateWithLifecycle()
        val isAndroidAutoConnected by viewModel.isAndroidAutoConnected.collectAsStateWithLifecycle()

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
                    onReloadHoleGPS = viewModel::reloadHoleGPS,
                    onOpenLocationSettings = viewModel::openLocationSettings,
                    onRequestPreciseLocation = viewModel::requestPreciseLocation,
                    isAndroidAutoConnected = isAndroidAutoConnected,
                )
            }
            state.isRoundUnavailable -> {
                RoundUnavailableScreen(
                    errorMessage = state.courseLoadError,
                    onRetry = viewModel::retryCourseLoad,
                    onBackToSearch = viewModel::endRound,
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
private fun RequestLocationPermissionWithRationale(onPermissionGranted: () -> Unit) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { results ->
                if (results.values.any { it }) onPermissionGranted()
            },
        )

    fun hasLocationPermission(): Boolean {
        val fineGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val coarseGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission()) {
            onPermissionGranted()
        } else {
            showRationale = true
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text(stringResource(R.string.location_rationale_title)) },
            text = { Text(stringResource(R.string.location_rationale_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        launcher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.location_rationale_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text(stringResource(R.string.location_rationale_not_now))
                }
            },
        )
    }
}
