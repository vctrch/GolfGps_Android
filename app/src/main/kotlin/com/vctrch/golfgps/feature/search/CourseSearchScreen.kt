package com.vctrch.golfgps.feature.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GolfCourse
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.domain.GolfCourseSummary
import com.vctrch.golfgps.feature.round.RoundUiState
import com.vctrch.golfgps.feature.support.SupportDeveloperCard
import com.vctrch.golfgps.ui.theme.GolfTheme

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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
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

        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

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
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        state.courseLoadError?.let { message ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }

        when {
            state.isSearchActive -> {
                SearchResultsSection(
                    state = state,
                    onCourseSelected = onCourseSelected,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                SearchEmptyState(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(16.dp))
                SupportDeveloperCard()
            }
        }
    }
}

@Composable
private fun SearchResultsSection(
    state: RoundUiState,
    onCourseSelected: (GolfCourseSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Results",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))

        when {
            state.searchResults.isEmpty() && !state.isSearching -> {
                Text("No courses found. Try a different name or nearby city.")
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.searchResults, key = { it.id }) { course ->
                        CourseResultCard(course = course, onClick = { onCourseSelected(course) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.GolfCourse,
            contentDescription = null,
            tint = GolfTheme.Fairway,
            modifier = Modifier.height(48.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Type at least 2 characters to search")
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
