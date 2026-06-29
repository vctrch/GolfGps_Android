package com.vctrch.golfgps.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object GolfTheme {
    val Fairway = Color(red = 0.12f, green = 0.42f, blue = 0.28f)
    val FairwayDark = Color(red = 0.06f, green = 0.28f, blue = 0.18f)
    val Accent = Color(red = 0.20f, green = 0.72f, blue = 0.45f)
    val CardShadow = Color.Black.copy(alpha = 0.08f)

    val HeroGradient: Brush = Brush.linearGradient(listOf(Fairway, FairwayDark))
}
