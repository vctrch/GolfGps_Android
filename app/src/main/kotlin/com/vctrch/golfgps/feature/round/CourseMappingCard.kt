package com.vctrch.golfgps.feature.round

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LoadedCourse
import com.vctrch.golfgps.domain.TeeMappingConfidence
import com.vctrch.golfgps.domain.greenEstimatedCount
import com.vctrch.golfgps.domain.greenLoadingCount
import com.vctrch.golfgps.domain.greenMappedCount
import com.vctrch.golfgps.domain.greenMappingConfidence
import com.vctrch.golfgps.domain.teeMappingConfidence
import com.vctrch.golfgps.domain.teeNotOnMapCount
import com.vctrch.golfgps.domain.teeOnMapCount
import com.vctrch.golfgps.domain.teePossibleCount
import com.vctrch.golfgps.ui.theme.GolfTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseMappingCard(
    course: LoadedCourse,
    selectedHoleNumber: Int,
    onSelectHole: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, tint = GolfTheme.Fairway)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Course mapping",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            summarySubtitle(course),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    MappingLegend()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        course.holes.forEach { hole ->
                            HoleGridButton(
                                hole = hole,
                                selected = hole.number == selectedHoleNumber,
                                onClick = { onSelectHole(hole.number) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MappingLegend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LegendRow(color = GolfTheme.Fairway, label = "Mapped green")
        LegendRow(color = Color(0xFFD98C1F), label = "Estimated green")
        LegendRow(color = Color.Gray, label = "Map loading")
        LegendRow(color = GolfTheme.Accent, label = "Mapped tee box")
        LegendRow(color = Color(0xFFE67E22), label = "Possible tee")
    }
}

@Composable
private fun LegendRow(
    color: Color,
    label: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = color) {}
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HoleGridButton(
    hole: HoleTarget,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val confidence = hole.greenMappingConfidence
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) GolfTheme.Fairway else greenPickerColor(confidence),
        border =
            BorderStroke(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else greenPickerBorder(confidence),
            ),
        modifier = Modifier.width(48.dp).height(52.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${hole.number}",
                fontWeight = FontWeight.SemiBold,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                modifier = Modifier.size(6.dp),
                shape = CircleShape,
                color = teeDotColor(hole.teeMappingConfidence, selected),
            ) {}
        }
    }
}

private fun teeDotColor(
    confidence: TeeMappingConfidence,
    selected: Boolean,
): Color {
    if (selected) {
        return when (confidence) {
            TeeMappingConfidence.MAPPED -> Color.White
            TeeMappingConfidence.MATCHED,
            TeeMappingConfidence.FAIRWAY_DERIVED,
            TeeMappingConfidence.ESTIMATED,
            -> Color.White.copy(alpha = 0.7f)
            else -> Color.Transparent
        }
    }
    return when (confidence) {
        TeeMappingConfidence.MAPPED -> GolfTheme.Accent
        TeeMappingConfidence.MATCHED,
        TeeMappingConfidence.FAIRWAY_DERIVED,
        TeeMappingConfidence.ESTIMATED,
        -> Color(0xFFE67E22)
        else -> Color.Transparent
    }
}

private fun summarySubtitle(course: LoadedCourse): String {
    val greenParts =
        listOfNotNull(
            course.greenMappedCount.takeIf { it > 0 }?.let { "$it mapped greens" },
            course.greenEstimatedCount.takeIf { it > 0 }?.let { "$it estimated greens" },
            course.greenLoadingCount.takeIf { it > 0 }?.let { "$it loading" },
        )
    val teeParts =
        listOfNotNull(
            course.teeOnMapCount.takeIf { it > 0 }?.let { "$it mapped tee boxes" },
            course.teePossibleCount.takeIf { it > 0 }?.let { "$it possible tees" },
            course.teeNotOnMapCount.takeIf { it > 0 }?.let { "$it without a mapped tee" },
        )
    val greens = greenParts.joinToString(" · ").ifEmpty { "Checking hole map…" }
    val tees = teeParts.joinToString(" · ").ifEmpty { "No tee positions yet" }
    return "$greens · $tees"
}
