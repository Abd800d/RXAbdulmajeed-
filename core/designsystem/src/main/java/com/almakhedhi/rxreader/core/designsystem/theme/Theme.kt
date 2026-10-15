package com.almakhedhi.rxreader.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = TealPrimary,
    onPrimary = Color.White,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealDark,
    secondary = GoldAccent,
    onSecondary = InkPrimary,
    secondaryContainer = LightGold,
    onSecondaryContainer = InkPrimary,
    tertiary = TealDark,
    onTertiary = Color.White,
    background = AppBackground,
    onBackground = InkPrimary,
    surface = Color.White,
    onSurface = InkPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = CardOutline,
    outlineVariant = DecorativeMint,
)

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = DarkTealInk,
    primaryContainer = TealDark,
    onPrimaryContainer = TealContainer,
    secondary = GoldAccent,
    onSecondary = InkPrimary,
    secondaryContainer = TealDark,
    onSecondaryContainer = Color.White,
    background = DarkBackground,
    onBackground = Color(0xFFE6F2F0),
    surface = DarkSurface,
    onSurface = Color(0xFFE6F2F0),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFB9CFCB),
    outline = Color(0xFF2C4A47),
)

@Composable
fun RxReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = RxTypography,
        content = content,
    )
}
