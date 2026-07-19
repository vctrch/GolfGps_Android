package com.vctrch.golfgps.feature.contribute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vctrch.golfgps.data.opengolf.OpenGolfMomentType
import com.vctrch.golfgps.domain.LatLng

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributeSubmitSheet(
    holeNumber: Int,
    state: HoleContributionViewModel.UiState,
    userLocation: LatLng?,
    onTypeChange: (OpenGolfMomentType) -> Unit,
    onNoteChange: (String) -> Unit,
    onUseGps: (LatLng) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val types = listOf(OpenGolfMomentType.TEE, OpenGolfMomentType.GREEN, OpenGolfMomentType.PIN)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Submit to OpenGolf", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Hole $holeNumber", style = MaterialTheme.typography.titleMedium)
            Text(
                state.coordinateSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("What are you marking?", fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = state.selectedType == type,
                        onClick = { onTypeChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, types.size),
                    ) {
                        Text(type.label)
                    }
                }
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g. back tee, front pin") },
            )
            userLocation?.let { gps ->
                TextButton(onClick = { onUseGps(gps) }) {
                    Text("Use my GPS instead")
                }
            }
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = onSubmit,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "Submitting…" else "Submit")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
