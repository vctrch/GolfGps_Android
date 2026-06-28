package com.vctrch.golfgps.feature.support

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vctrch.golfgps.data.billing.TipResult
import com.vctrch.golfgps.ui.theme.GolfTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportDeveloperCard(
    modifier: Modifier = Modifier,
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val tipProducts by viewModel.tipProducts.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.tipResults.collect { result ->
            val message =
                when (result) {
                    TipResult.Thanks -> "Thanks for the tip!"
                    TipResult.Canceled -> null
                    is TipResult.Error -> result.message
                }
            message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
                "Golf GPS is free and built on open data. If it helps your game, " +
                    "an optional tip keeps it growing.",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (tipProducts.isEmpty()) {
                Text(
                    "Tips aren't available right now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tipProducts.forEach { product ->
                        OutlinedButton(
                            onClick = { activity?.let { viewModel.onTipClicked(it, product) } },
                            enabled = activity != null,
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = GolfTheme.Fairway,
                                modifier = Modifier.padding(end = 6.dp),
                            )
                            Text(product.formattedPrice)
                        }
                    }
                }
            }
        }
    }
}
