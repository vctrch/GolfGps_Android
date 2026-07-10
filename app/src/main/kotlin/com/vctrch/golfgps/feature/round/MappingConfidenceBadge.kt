package com.vctrch.golfgps.feature.round

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.GreenMappingConfidence
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.TeeMappingConfidence
import com.vctrch.golfgps.domain.greenMappingConfidence
import com.vctrch.golfgps.domain.teeMappingConfidence
import com.vctrch.golfgps.ui.theme.GolfTheme

@Composable
fun HoleMappingConfidenceRow(
    hole: HoleTarget,
    heroStyle: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MappingConfidenceBadge(
                label = hole.greenMappingConfidence.shortLabel,
                icon = greenIcon(hole.greenMappingConfidence),
                tint = if (heroStyle) Color.White else greenTint(hole.greenMappingConfidence),
                heroStyle = heroStyle,
            )
            MappingConfidenceBadge(
                label = hole.teeMappingConfidence.shortLabel,
                icon = teeIcon(hole.teeMappingConfidence),
                tint = if (heroStyle) Color.White.copy(alpha = 0.9f) else teeTint(hole.teeMappingConfidence),
                heroStyle = heroStyle,
            )
        }
        if (!heroStyle) {
            Text(
                text = confidenceDetail(hole),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MappingConfidenceBadge(
    label: String,
    icon: ImageVector,
    tint: Color,
    heroStyle: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = if (heroStyle) 0.18f else 0.12f),
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal =
                        if (heroStyle) {
                            10.dp
                        } else {
                            8.dp
                        },
                    vertical =
                        if (heroStyle) {
                            6.dp
                        } else {
                            4.dp
                        },
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                text = label,
                color = tint,
                style =
                    if (heroStyle) {
                        MaterialTheme.typography.labelMedium
                    } else {
                        MaterialTheme.typography.labelSmall
                    },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun confidenceDetail(hole: HoleTarget): String {
    return if (hole.teeMappingConfidence == TeeMappingConfidence.MAPPED) {
        hole.greenMappingConfidence.detail
    } else {
        "${hole.greenMappingConfidence.detail} ${hole.teeMappingConfidence.detail}"
    }
}

private fun greenIcon(confidence: GreenMappingConfidence): ImageVector =
    when (confidence) {
        GreenMappingConfidence.LOADING -> Icons.Default.Sync
        GreenMappingConfidence.ESTIMATED -> Icons.Default.HelpOutline
        GreenMappingConfidence.MAPPED -> Icons.Default.Flag
    }

private fun teeIcon(confidence: TeeMappingConfidence): ImageVector =
    when (confidence) {
        TeeMappingConfidence.MAPPED -> Icons.Default.Flag
        TeeMappingConfidence.MATCHED,
        TeeMappingConfidence.FAIRWAY_DERIVED,
        TeeMappingConfidence.ESTIMATED,
        -> Icons.Default.HelpOutline
        TeeMappingConfidence.UNAVAILABLE,
        TeeMappingConfidence.NOT_MAPPED,
        -> Icons.Default.HelpOutline
    }

private fun greenTint(confidence: GreenMappingConfidence): Color =
    when (confidence) {
        GreenMappingConfidence.LOADING -> Color(0xFFE67E22)
        GreenMappingConfidence.ESTIMATED -> Color(0xFFD98C1F)
        GreenMappingConfidence.MAPPED -> GolfTheme.Accent
    }

private fun teeTint(confidence: TeeMappingConfidence): Color =
    when (confidence) {
        TeeMappingConfidence.UNAVAILABLE,
        TeeMappingConfidence.NOT_MAPPED,
        -> Color.Gray
        TeeMappingConfidence.MATCHED,
        TeeMappingConfidence.FAIRWAY_DERIVED,
        TeeMappingConfidence.ESTIMATED,
        -> Color(0xFFE67E22)
        TeeMappingConfidence.MAPPED -> GolfTheme.Accent
    }

fun greenPickerColor(confidence: GreenMappingConfidence): Color =
    when (confidence) {
        GreenMappingConfidence.LOADING -> Color(0xFFE67E22).copy(alpha = 0.18f)
        GreenMappingConfidence.ESTIMATED -> Color(0xFFD98C1F).copy(alpha = 0.18f)
        GreenMappingConfidence.MAPPED -> GolfTheme.Fairway.copy(alpha = 0.16f)
    }

fun greenPickerBorder(confidence: GreenMappingConfidence): Color =
    when (confidence) {
        GreenMappingConfidence.LOADING -> Color(0xFFE67E22)
        GreenMappingConfidence.ESTIMATED -> Color(0xFFD98C1F)
        GreenMappingConfidence.MAPPED -> GolfTheme.Fairway
    }
