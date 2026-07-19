package com.vctrch.golfgps.feature.contribute

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.vctrch.golfgps.data.opengolf.OpenGolfTermsChallenge
import com.vctrch.golfgps.ui.theme.GolfTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenGolfTermsSheet(
    challenge: OpenGolfTermsChallenge,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Accept OpenGolf terms",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "OpenGolf requires accepting terms version ${challenge.version} before contributions can be submitted.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, challenge.termsUrl.toUri()))
                },
            ) {
                Text("Read terms", color = GolfTheme.Fairway)
            }
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) {
                Text("I accept")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Not now")
            }
        }
    }
}
