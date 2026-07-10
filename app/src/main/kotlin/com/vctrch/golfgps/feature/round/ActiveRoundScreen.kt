package com.vctrch.golfgps.feature.round

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.GeoMath
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LoadedCourse
import com.vctrch.golfgps.domain.MapDisplayStyle
import com.vctrch.golfgps.domain.TeeMappingConfidence
import com.vctrch.golfgps.domain.greenMappingConfidence
import com.vctrch.golfgps.domain.showsEstimatedQualifier
import com.vctrch.golfgps.domain.teeMappingConfidence
import com.vctrch.golfgps.feature.map.HoleMapSection
import com.vctrch.golfgps.location.LocationUiStatus
import com.vctrch.golfgps.ui.theme.GolfTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRoundScreen(
    state: RoundUiState,
    mapDisplayStyle: MapDisplayStyle,
    onMapDisplayStyleChange: (MapDisplayStyle) -> Unit,
    onEndRound: () -> Unit,
    onSelectHole: (Int) -> Unit,
    onPreviousHole: () -> Unit,
    onNextHole: () -> Unit,
    onReloadHoleGPS: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onRequestPreciseLocation: () -> Unit,
    isAndroidAutoConnected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val course = state.loadedCourse ?: return
    val hole = state.currentHole ?: return

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(course.summary.name) },
                navigationIcon = {
                    IconButton(onClick = onEndRound) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "End round")
                    }
                },
                actions = {
                    IconButton(onClick = onReloadHoleGPS, enabled = !state.isLoadingCourse) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload hole GPS")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HoleHeader(course = course, hole = hole, holeIndex = state.currentHoleIndex)
            if (isAndroidAutoConnected) {
                AndroidAutoConnectedBanner()
            }
            YardageHero(
                state = state,
                hole = hole,
                onOpenLocationSettings = onOpenLocationSettings,
                onRequestPreciseLocation = onRequestPreciseLocation,
            )
            if (state.isEnhancingHoles) {
                HoleMapLoadingBanner()
            }
            HoleMapSection(
                hole = hole,
                userLocation = state.userLocation,
                mapDisplayStyle = mapDisplayStyle,
                onMapDisplayStyleChange = onMapDisplayStyleChange,
            )
            HolePicker(course = course, selectedHoleNumber = state.selectedHoleNumber, onSelectHole = onSelectHole)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(onClick = onPreviousHole, label = { Text("Previous") })
                AssistChip(onClick = onNextHole, label = { Text("Next") })
            }
            CourseMappingCard(
                course = course,
                selectedHoleNumber = state.selectedHoleNumber,
                onSelectHole = onSelectHole,
            )
            Text(
                hole.greenMappingConfidence.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "OpenGolfAPI · OpenStreetMap",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HoleHeader(
    course: LoadedCourse,
    hole: HoleTarget,
    holeIndex: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Hole ${hole.number}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            hole.par?.let {
                Text(
                    "Par $it",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = GolfTheme.Fairway,
                )
            }
            Text(
                "${holeIndex + 1} of ${course.holes.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AndroidAutoConnectedBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GolfTheme.Fairway.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Showing on Android Auto",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = GolfTheme.Fairway,
        )
    }
}

@Composable
private fun HoleMapLoadingBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GolfTheme.Fairway.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Sync, contentDescription = null, tint = GolfTheme.Fairway)
        Text(
            "Loading hole map…",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun YardageHero(
    state: RoundUiState,
    hole: HoleTarget,
    onOpenLocationSettings: () -> Unit,
    onRequestPreciseLocation: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(GolfTheme.HeroGradient, RoundedCornerShape(24.dp))
                    .padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HoleMappingConfidenceRow(hole = hole, heroStyle = true)

                val liveYards = state.distanceToGreen()
                when {
                    liveYards != null -> {
                        HeroYardage(yards = liveYards, estimated = hole.showsEstimatedQualifier)
                    }
                    state.userLocation == null -> {
                        Text(
                            "Waiting for GPS",
                            color = Color.White.copy(alpha = 0.95f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    state.currentHoleNeedsGPS -> {
                        Text(
                            "—",
                            color = Color.White.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Hole map still loading",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    else -> {
                        val holeLength = hole.holeLengthYards()
                        if (holeLength != null) {
                            Text(
                                "${GeoMath.formattedYardage(holeLength)} yds",
                                color = Color.White,
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Tee to green — move to the course for live yardage",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                "Waiting for GPS",
                                color = Color.White.copy(alpha = 0.95f),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                val teeYards = state.distanceToTee()
                when {
                    teeYards != null -> {
                        Text(
                            "${GeoMath.formattedYardage(teeYards)} yds to tee",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    hole.teeMappingConfidence == TeeMappingConfidence.NOT_MAPPED -> {
                        Text(
                            hole.teeMappingConfidence.shortLabel,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    hole.tee != null -> {
                        Text(
                            hole.teeMappingConfidence.shortLabel,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                LocationStatusBanner(
                    status = state.locationStatus,
                    hasUserLocation = state.userLocation != null,
                    onOpenSettings = onOpenLocationSettings,
                    onRequestPrecise = onRequestPreciseLocation,
                )
            }
        }
    }
}

@Composable
private fun LocationStatusBanner(
    status: LocationUiStatus,
    hasUserLocation: Boolean,
    onOpenSettings: () -> Unit,
    onRequestPrecise: () -> Unit,
) {
    when {
        status.locationServicesDisabled -> {
            LocationBanner(
                message = "Location Services are off on this device.",
                actionTitle = "Open Settings",
                onAction = onOpenSettings,
            )
        }
        status.needsSettings -> {
            LocationBanner(
                message = "Allow location access for live yardage.",
                actionTitle = "Open Settings",
                onAction = onOpenSettings,
            )
        }
        status.isAuthorized && !hasUserLocation -> {
            LocationBanner(
                message = "Waiting for GPS — stay outdoors with a clear view of the sky.",
                actionTitle = null,
                onAction = null,
            )
        }
        status.isAuthorized && !status.isPreciseLocationEnabled -> {
            LocationBanner(
                message = "Precise Location is off — yardage may be inaccurate.",
                actionTitle = "Enable Precise",
                onAction = onRequestPrecise,
            )
        }
    }
}

@Composable
private fun LocationBanner(
    message: String,
    actionTitle: String?,
    onAction: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Default.LocationOff,
            contentDescription = null,
            tint = Color(0xFFFFB74D),
            modifier = Modifier.size(18.dp),
        )
        Text(
            message,
            color = Color(0xFFFFB74D),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        if (actionTitle != null && onAction != null) {
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) {
                Text(actionTitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun HeroYardage(
    yards: Int,
    estimated: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                GeoMath.formattedYardage(yards),
                color = Color.White,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "yds to green",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (estimated) {
            Text(
                "ESTIMATED",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HolePicker(
    course: LoadedCourse,
    selectedHoleNumber: Int,
    onSelectHole: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Jump to hole",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            course.holes.forEach { hole ->
                val selected = hole.number == selectedHoleNumber
                val confidence = hole.greenMappingConfidence
                Surface(
                    onClick = { onSelectHole(hole.number) },
                    shape = CircleShape,
                    color = if (selected) GolfTheme.Fairway else greenPickerColor(confidence),
                    border =
                        BorderStroke(
                            width = if (selected) 0.dp else 1.5.dp,
                            color = if (selected) Color.Transparent else greenPickerBorder(confidence),
                        ),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            "${hole.number}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
