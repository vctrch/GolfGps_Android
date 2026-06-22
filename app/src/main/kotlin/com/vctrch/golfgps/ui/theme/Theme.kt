package com.vctrch.golfgps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = GolfTheme.Fairway,
        secondary = GolfTheme.Accent,
        background = Color(0xFFF2F2F7),
        surface = Color.White,
    )

private val DarkColors =
    darkColorScheme(
        primary = GolfTheme.Accent,
        secondary = GolfTheme.Fairway,
    )

@Composable
fun GolfGpsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
