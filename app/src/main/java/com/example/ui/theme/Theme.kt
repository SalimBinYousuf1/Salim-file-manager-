package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SalimPrimaryDark,
    onPrimary = SalimOnPrimaryDark,
    primaryContainer = SalimPrimaryContainerDark,
    onPrimaryContainer = SalimOnPrimaryContainerDark,
    secondary = SalimSecondaryDark,
    onSecondary = SalimOnSecondaryDark,
    secondaryContainer = SalimSecondaryContainerDark,
    onSecondaryContainer = SalimOnSecondaryContainerDark,
    tertiary = SalimTertiaryDark,
    onTertiary = SalimOnTertiaryDark,
    background = SalimBackgroundDark,
    onBackground = SalimOnBackgroundDark,
    surface = SalimSurfaceDark,
    onSurface = SalimOnSurfaceDark,
    surfaceVariant = SalimSurfaceVariantDark,
    onSurfaceVariant = SalimOnSurfaceVariantDark,
    error = SalimErrorDark,
    onError = SalimOnErrorDark,
    errorContainer = SalimErrorContainerDark,
    onErrorContainer = SalimOnErrorContainerDark,
)

private val LightColorScheme = lightColorScheme(
    primary = SalimPrimaryLight,
    onPrimary = SalimOnPrimaryLight,
    primaryContainer = SalimPrimaryContainerLight,
    onPrimaryContainer = SalimOnPrimaryContainerLight,
    secondary = SalimSecondaryLight,
    onSecondary = SalimOnSecondaryLight,
    secondaryContainer = SalimSecondaryContainerLight,
    onSecondaryContainer = SalimOnSecondaryContainerLight,
    tertiary = SalimTertiaryLight,
    onTertiary = SalimOnTertiaryLight,
    background = SalimBackgroundLight,
    onBackground = SalimOnBackgroundLight,
    surface = SalimSurfaceLight,
    onSurface = SalimOnSurfaceLight,
    surfaceVariant = SalimSurfaceVariantLight,
    onSurfaceVariant = SalimOnSurfaceVariantLight,
    error = SalimErrorLight,
    onError = SalimOnErrorLight,
    errorContainer = SalimErrorContainerLight,
    onErrorContainer = SalimOnErrorContainerLight,
)

private val HighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF0F0F0F),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color.White,
    error = Color(0xFFFF5252),
    onError = Color.Black
)

private val HighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF00227B),
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color.Black,
    error = Color(0xFFB00020),
    onError = Color.White
)

@Composable
fun SalimTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    MyApplicationTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        highContrast = highContrast,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Default false to keep Salim's crisp signature identity
    highContrast: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        highContrast && darkTheme -> HighContrastDarkColorScheme
        highContrast && !darkTheme -> HighContrastLightColorScheme
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
