package com.vctrch.golfgps.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.*
import com.vctrch.golfgps.ui.theme.GolfTheme

@Composable
fun HoleMapSection(
    hole: HoleTarget,
    userLocation: LatLng?,
    mapDisplayStyle: MapDisplayStyle,
    onMapDisplayStyleChange: (MapDisplayStyle) -> Unit,
    isPlaceMode: Boolean = false,
    isSignedIn: Boolean = false,
    contributionStatus: String? = null,
    contributionError: String? = null,
    onBeginPlaceMode: () -> Unit = {},
    onCancelPlaceMode: () -> Unit = {},
    onMarkHere: (LatLng) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapBackend = remember { resolveMapBackend(context) }
    val waitingForMap = !hole.hasReliableGreenPosition
    var mapCenter by remember(hole.number) { mutableStateOf(hole.green) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(380.dp)
                .clip(RoundedCornerShape(20.dp)),
    ) {
        Box(modifier = Modifier.fillMaxSize().alpha(if (waitingForMap) 0.45f else 1f)) {
            when (mapBackend) {
                MapBackend.GOOGLE ->
                    GoogleHoleMap(
                        hole = hole,
                        userLocation = userLocation,
                        mapDisplayStyle = mapDisplayStyle,
                        onCameraCenterChanged = { mapCenter = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                MapBackend.OPEN_STREET_MAP ->
                    OsmHoleMap(
                        hole = hole,
                        userLocation = userLocation,
                        mapDisplayStyle = mapDisplayStyle,
                        onCameraCenterChanged = { mapCenter = it },
                        modifier = Modifier.fillMaxSize(),
                    )
            }
        }

        if (isPlaceMode) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(22.dp)
                        .background(GolfTheme.Fairway.copy(alpha = 0.9f), RoundedCornerShape(999.dp)),
            )
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

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            contributionError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            contributionStatus?.let {
                Text(
                    it,
                    color = GolfTheme.Fairway,
                    style = MaterialTheme.typography.labelMedium,
                    modifier =
                        Modifier
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            if (isPlaceMode) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancelPlaceMode) { Text("Cancel") }
                    Button(onClick = { onMarkHere(mapCenter) }) {
                        Text("Mark here")
                    }
                }
            } else {
                AssistChip(
                    onClick = onBeginPlaceMode,
                    label = {
                        Text(
                            if (isSignedIn) "Add tee, green, or pin" else "Create account to mark the map",
                        )
                    },
                    leadingIcon = { Icon(Icons.Default.AddLocationAlt, contentDescription = null) },
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }

        if (waitingForMap) {
            Text(
                text = "Hole map loading",
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (mapBackend == MapBackend.OPEN_STREET_MAP) {
            Text(
                text = mapDisplayStyle.osmAttribution(),
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 72.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapStylePicker(
    mapDisplayStyle: MapDisplayStyle,
    onMapDisplayStyleChange: (MapDisplayStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { expanded = false }
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(mapDisplayStyle.title) },
            leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
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
