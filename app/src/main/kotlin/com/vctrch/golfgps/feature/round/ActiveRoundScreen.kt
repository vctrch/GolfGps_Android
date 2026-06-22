package com.vctrch.golfgps.feature.round

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.*
import com.vctrch.golfgps.feature.map.HoleMapSection
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
