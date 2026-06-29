package com.vctrch.golfgps.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.*

@Composable
fun HoleMapSection(
    hole: HoleTarget,
    userLocation: LatLng?,
    mapDisplayStyle: MapDisplayStyle,
    onMapDisplayStyleChange: (MapDisplayStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapBackend = remember { resolveMapBackend(context) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(340.dp)
                .clip(RoundedCornerShape(20.dp)),
    ) {
        when (mapBackend) {
            MapBackend.GOOGLE ->
                GoogleHoleMap(
                    hole = hole,
                    userLocation = userLocation,
                    mapDisplayStyle = mapDisplayStyle,
                    modifier = Modifier.fillMaxSize(),
                )
            MapBackend.OPEN_STREET_MAP ->
                OsmHoleMap(
                    hole = hole,
                    userLocation = userLocation,
                    mapDisplayStyle = mapDisplayStyle,
                    modifier = Modifier.fillMaxSize(),
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
        if (mapBackend == MapBackend.OPEN_STREET_MAP) {
            Text(
                text = "© OpenStreetMap",
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
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
