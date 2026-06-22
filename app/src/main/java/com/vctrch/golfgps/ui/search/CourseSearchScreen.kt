package com.vctrch.golfgps.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.model.GolfCourseSummary
import com.vctrch.golfgps.ui.theme.GolfTheme
import com.vctrch.golfgps.viewmodel.RoundUiState

@Composable
fun CourseSearchScreen(
    state: RoundUiState,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCourseSelected: (GolfCourseSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = GolfTheme.Fairway)
                Text("Find your course", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "Search thousands of US courses with free, open scorecard data.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Course name or city") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )

        if (state.isSearching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                Text("Searching courses…")
            }
        }

        state.errorMessage?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        if (state.isSearchActive) {
            Text(
                "Results",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.searchResults.isEmpty() && !state.isSearching) {
                Text("No courses found. Try a different name or nearby city.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.searchResults, key = { it.id }) { course ->
                        CourseResultCard(course = course, onClick = { onCourseSelected(course) })
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Default.GolfCourse,
                    contentDescription = null,
                    tint = GolfTheme.Fairway,
                    modifier = Modifier.height(48.dp),
                )
                Text("Type at least 2 characters to search")
            }
            Spacer(modifier = Modifier.weight(1f))
            SupportCardPlaceholder()
        }
    }
}

@Composable
private fun CourseResultCard(
    course: GolfCourseSummary,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            listOfNotNull(course.city, course.state).joinToString(", ").takeIf { it.isNotEmpty() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (course.holesCount != null && course.parTotal != null) {
                Text("${course.holesCount} holes · Par ${course.parTotal}", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun SupportCardPlaceholder() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Support Golf GPS",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = GolfTheme.Fairway,
            )
            Text(
                "Golf GPS is free! Optional tips via Google Play Billing can be added in a follow-up.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
