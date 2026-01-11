package com.zagot.zagotplus.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    // Primary - Forest Green
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    
    // Secondary - Warm Brown
    secondary = Brown40,
    onSecondary = Color.White,
    secondaryContainer = Brown90,
    onSecondaryContainer = Brown10,
    
    // Tertiary - Golden Yellow
    tertiary = Yellow40,
    onTertiary = Color.White,
    tertiaryContainer = Yellow90,
    onTertiaryContainer = Yellow10,
    
    // Error
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    
    // Background & Surface
    background = Gray99,
    onBackground = Gray10,
    surface = Gray99,
    onSurface = Gray10,
    surfaceVariant = GrayVariant90,
    onSurfaceVariant = GrayVariant30,
    
    // Outline
    outline = GrayVariant50,
    outlineVariant = GrayVariant80
)

private val DarkColorScheme = darkColorScheme(
    // Primary - Forest Green
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    
    // Secondary - Warm Brown
    secondary = Brown80,
    onSecondary = Brown20,
    secondaryContainer = Brown30,
    onSecondaryContainer = Brown90,
    
    // Tertiary - Golden Yellow
    tertiary = Yellow80,
    onTertiary = Yellow20,
    tertiaryContainer = Yellow30,
    onTertiaryContainer = Yellow90,
    
    // Error
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    
    // Background & Surface
    background = Gray10,
    onBackground = Gray90,
    surface = Gray10,
    onSurface = Gray90,
    surfaceVariant = GrayVariant30,
    onSurfaceVariant = GrayVariant80,
    
    // Outline
    outline = GrayVariant60,
    outlineVariant = GrayVariant30
)

@Composable
fun ZagotPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Update status bar color
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
