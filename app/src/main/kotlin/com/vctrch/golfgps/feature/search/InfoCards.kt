package com.vctrch.golfgps.feature.search

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.data.remote.OpenGolfCourseLinks
import com.vctrch.golfgps.domain.GolfCourseSummary
import com.vctrch.golfgps.ui.theme.GolfTheme

@Composable
fun DataSourcesInfoCard(modifier: Modifier = Modifier) {
    ExpandableInfoCard(
        title = "How course information works",
        subtitle =
            "This app combines free course listings with community-drawn maps. " +
                "Tap for a look at what we show and what to expect on the course.",
        icon = { Icon(Icons.Default.Map, contentDescription = null, tint = GolfTheme.Fairway) },
        modifier = modifier,
    ) {
        InfoSection(
            title = "Where the data comes from",
            items =
                listOf(
                    "OpenGolf lists course names, pars, and scorecards — the same open database this app searches.",
                    "Hole positions come from volunteer maps on OpenStreetMap. " +
                        "Quality depends on how thoroughly each course has been drawn.",
                    "After you pick a course, the app loads map details in the background " +
                        "and matches them to the scorecard.",
                ),
        )
        InfoSection(
            title = "How we pick a green",
            items =
                listOf(
                    "When someone has marked a pin or green for that hole, we use it.",
                    "Otherwise we follow the fairway line on the map and place the green at the far end of the hole.",
                    "If a hole is missing on the map, we estimate its green between neighboring mapped holes.",
                ),
        )
        InfoSection(
            title = "What to expect",
            items =
                listOf(
                    "Some courses are mapped hole by hole; others only have a handful of fairways drawn.",
                    "Estimated holes can still show yardage to green, " +
                        "but they may be less exact than fully mapped ones.",
                    "Your yardage also depends on phone signal, open sky, and precise location.",
                ),
        )
        Text(
            "Data from OpenGolf and OpenStreetMap contributors",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun HelpMapCourseCard(
    recentCourse: GolfCourseSummary? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ExpandableInfoCard(
        title = "Help map a course",
        subtitle =
            "OpenGolf is a community driven resource anyone can edit. " +
                "Tap to fix a scorecard, add a missing course, or improve map data.",
        icon = { Icon(Icons.Default.VolunteerActivism, contentDescription = null, tint = GolfTheme.Fairway) },
        modifier = modifier,
    ) {
        Text(
            "Open a course on OpenGolf to edit its scorecard, or follow the map link " +
                "on that page to draw tees, fairways and greens on OpenStreetMap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recentCourse != null) {
            LinkRow(
                title = "Improve ${recentCourse.name}",
                subtitle = "Open this course on OpenGolf",
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(OpenGolfCourseLinks.coursePage(recentCourse))),
                    )
                },
            )
        }
        LinkRow(
            title = "Find a course to edit",
            subtitle = "Search the OpenGolf directory",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OpenGolfCourseLinks.SEARCH)))
            },
        )
        LinkRow(
            title = "Add a missing course",
            subtitle = "Submit it to OpenGolf for review",
            onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OpenGolfCourseLinks.SUBMIT_COURSE)))
            },
        )
    }
}

@Composable
private fun ExpandableInfoCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
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
                    icon()
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            subtitle,
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
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = { content() })
            }
        }
    }
}

@Composable
private fun InfoSection(
    title: String,
    items: List<String>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        items.forEach { item ->
            Text(
                "• $item",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, tint = GolfTheme.Fairway)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
