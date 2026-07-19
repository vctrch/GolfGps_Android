package com.vctrch.golfgps.feature.contribute

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vctrch.golfgps.data.opengolf.OpenGolfCorrectionField
import com.vctrch.golfgps.data.remote.OSMCourseLinks
import com.vctrch.golfgps.domain.HoleTarget
import com.vctrch.golfgps.domain.LoadedCourse
import com.vctrch.golfgps.ui.theme.GolfTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImproveCourseMappingCard(
    course: LoadedCourse,
    hole: HoleTarget,
    viewModel: CourseCorrectionViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val auth by viewModel.authState.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var showOsmTools by remember { mutableStateOf(false) }
    var fieldMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(auth.phase) {
        viewModel.handleAuthPhase(auth.phase)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = GolfTheme.Fairway)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Course details & account",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            viewModel.summarySubtitle(auth.isSignedIn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                }
            }

            if (expanded) {
                Text(
                    "To add or fix a tee, green, or pin, use Add tee, green, or pin on the map. " +
                        "Pan so the crosshair sits on the spot, then mark it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (auth.isSignedIn) {
                    Text(
                        "Signed in as ${auth.email}",
                        color = GolfTheme.Fairway,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TextButton(onClick = viewModel::presentSignIn) {
                        Text("Manage OpenGolf account")
                    }
                    TextButton(onClick = viewModel::signOut) {
                        Text("Sign out")
                    }

                    Text("Suggest a course fact correction", fontWeight = FontWeight.SemiBold)
                    ExposedDropdownMenuBox(
                        expanded = fieldMenuExpanded,
                        onExpandedChange = { fieldMenuExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = state.correctionField.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Field") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fieldMenuExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = fieldMenuExpanded,
                            onDismissRequest = { fieldMenuExpanded = false },
                        ) {
                            OpenGolfCorrectionField.entries.forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(field.label) },
                                    onClick = {
                                        viewModel.setCorrectionField(field)
                                        fieldMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.correctionValue,
                        onValueChange = viewModel::setCorrectionValue,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Proposed value") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Note (optional)") },
                    )
                    Button(
                        onClick = { viewModel.submitCorrection(course.summary.id) },
                        enabled = state.canSubmit,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isSubmitting) "Submitting…" else "Submit correction")
                    }
                } else {
                    Button(
                        onClick = viewModel::presentSignIn,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create account or sign in")
                    }
                }

                state.statusMessage?.let { Text(it, color = GolfTheme.Fairway) }
                state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                TextButton(onClick = { showOsmTools = !showOsmTools }) {
                    Text(if (showOsmTools) "Hide OpenStreetMap tools" else "OpenStreetMap tools")
                }
                if (showOsmTools) {
                    Text(
                        "Use OSM to draw fairways and course boundaries. " +
                            "Point locations (tee/green/pin) should be marked on the map above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            val coordinate = OSMCourseLinks.preferredEditCoordinate(hole, course.summary)
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(OSMCourseLinks.editMap(coordinate))),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Edit hole ${hole.number} on OpenStreetMap")
                    }
                    course.summary.osmId?.let { osmId ->
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(OSMCourseLinks.courseWay(osmId))),
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open course boundary on OSM")
                        }
                    }
                }
            }
        }
    }

    when (val sheet = state.presentedSheet) {
        CourseCorrectionViewModel.PresentedSheet.SignIn -> {
            OpenGolfSignInSheet(
                authStore = viewModel.openGolfAuthStore(),
                onDismiss = viewModel::dismissSheet,
            )
        }
        is CourseCorrectionViewModel.PresentedSheet.Terms -> {
            OpenGolfTermsSheet(
                challenge = sheet.challenge,
                onDismiss = viewModel::dismissSheet,
                onAccept = {
                    viewModel.acceptTerms(sheet.challenge.version)
                    viewModel.submitCorrection(course.summary.id)
                },
            )
        }
        null -> Unit
    }
}
