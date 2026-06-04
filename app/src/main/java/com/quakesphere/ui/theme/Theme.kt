package com.quakesphere.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = SpaceBlack,
    primaryContainer = NavyBlue,
    onPrimaryContainer = PaleBlue,
    secondary = CyanBlue,
    onSecondary = SpaceBlack,
    secondaryContainer = DarkSlate,
    onSecondaryContainer = LightBlue,
    tertiary = AccentTeal,
    onTertiary = SpaceBlack,
    background = SpaceBlack,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = MagGreat,
    onError = SpaceBlack,
    outline = TextMuted,
    outlineVariant = DarkSlate,
    scrim = SpaceBlack
)

@Composable
fun QuakeSphereTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = QuakeSphereTypography,
        content = content
    )
}
