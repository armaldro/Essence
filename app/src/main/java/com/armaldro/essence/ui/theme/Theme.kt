package com.armaldro.essence.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightInk = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    secondary = InkMuted,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Paper,
    outline = Ink,
    outlineVariant = InkMuted
)

private val NightInk = darkColorScheme(
    primary = InkNight,
    onPrimary = PaperNight,
    secondary = InkMutedNight,
    onSecondary = PaperNight,
    background = PaperNight,
    onBackground = InkNight,
    surface = PaperNight,
    onSurface = InkNight,
    surfaceVariant = PaperNight,
    onSurfaceVariant = InkMutedNight,
    surfaceContainer = PaperNight,
    surfaceContainerHigh = PaperNight,
    surfaceContainerHighest = PaperNight,
    outline = InkNight,
    outlineVariant = InkMutedNight
)

/** Flat, monochrome theme with no ripples, so the UI reads like an e-ink display. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EssenceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) NightInk else LightInk,
        typography = Typography
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null, content = content)
    }
}
