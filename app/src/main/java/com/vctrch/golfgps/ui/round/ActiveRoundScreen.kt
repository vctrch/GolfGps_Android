package com.vctrch.golfgps.ui.round

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.vctrch.golfgps.domain.GeoMath
import com.vctrch.golfgps.domain.model.HoleTarget
import com.vctrch.golfgps.domain.model.LoadedCourse
import com.vctrch.golfgps.domain.model.MapDisplayStyle
import com.vctrch.golfgps.ui.theme.GolfTheme
import com.vctrch.golfgps.viewmodel.RoundUiState
import com.google.android.gms.maps.model.LatLng as MapsLatLng

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
            YardageHero(state = state, hole = hole)
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
            Text(
                "OpenGolfAPI · OpenStreetMap",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun YardageHero(
    state: RoundUiState,
    hole: HoleTarget,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = GolfTheme.Fairway),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Hole ${hole.number}",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
            val yards = state.distanceToGreen()
            Text(
                text = yards?.let { "${GeoMath.formattedYardage(it)} yds to green" } ?: "Waiting for GPS",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HoleMapSection(
    hole: HoleTarget,
    userLocation: com.vctrch.golfgps.domain.model.LatLng?,
    mapDisplayStyle: MapDisplayStyle,
    onMapDisplayStyleChange: (MapDisplayStyle) -> Unit,
) {
    val green = MapsLatLng(hole.green.latitude, hole.green.longitude)
    val camera =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(green, 16f)
        }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(20.dp)),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(mapType = mapDisplayStyle.toMapType()),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = userLocation != null),
        ) {
            Marker(state = MarkerState(green), title = "Green")
            hole.tee?.let { tee ->
                Marker(state = MarkerState(MapsLatLng(tee.latitude, tee.longitude)), title = "Tee")
            }
        }
        Text(
            text = "Hole ${hole.number}",
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        MapStylePicker(
            mapDisplayStyle = mapDisplayStyle,
            onMapDisplayStyleChange = onMapDisplayStyleChange,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
        )
    }
}

@Composable
private fun MapStylePicker(
    mapDisplayStyle: MapDisplayStyle,
    onMapDisplayStyleChange: (MapDisplayStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(mapDisplayStyle.title) },
            leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapDisplayStyle.entries.forEach { style ->
                DropdownMenuItem(
                    text = { Text(style.title) },
                    onClick = {
                        onMapDisplayStyleChange(style)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HolePicker(
    course: LoadedCourse,
    selectedHoleNumber: Int,
    onSelectHole: (Int) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        course.holes.forEach { hole ->
            AssistChip(
                onClick = { onSelectHole(hole.number) },
                label = { Text("${hole.number}") },
                modifier = Modifier.padding(vertical = 2.dp),
                border =
                    if (hole.number == selectedHoleNumber) {
                        AssistChipDefaults.assistChipBorder(enabled = true, borderColor = GolfTheme.Fairway)
                    } else {
                        AssistChipDefaults.assistChipBorder(enabled = true)
                    },
            )
        }
    }
}

private fun MapDisplayStyle.toMapType(): MapType {
    return when (this) {
        MapDisplayStyle.STANDARD -> MapType.NORMAL
        MapDisplayStyle.SATELLITE -> MapType.SATELLITE
        MapDisplayStyle.HYBRID -> MapType.HYBRID
    }
}
