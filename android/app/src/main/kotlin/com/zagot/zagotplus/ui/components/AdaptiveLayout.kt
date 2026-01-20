package com.zagot.zagotplus.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen size category for adaptive layouts.
 */
enum class ScreenSize {
    /** Phone in portrait mode (< 600dp) */
    COMPACT,
    /** Phone in landscape or small tablet (600-840dp) */
    MEDIUM,
    /** Large tablet or desktop (> 840dp) */
    EXPANDED
}

/**
 * Composition local providing current screen size category.
 */
val LocalScreenSize = compositionLocalOf { ScreenSize.COMPACT }

/**
 * Determines the screen size category based on screen width.
 */
@Composable
fun rememberScreenSize(): ScreenSize {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    
    return when {
        screenWidth < 600 -> ScreenSize.COMPACT
        screenWidth < 840 -> ScreenSize.MEDIUM
        else -> ScreenSize.EXPANDED
    }
}

/**
 * Returns true if the device is a tablet (medium or expanded screen).
 */
@Composable
fun isTablet(): Boolean {
    val screenSize = rememberScreenSize()
    return screenSize != ScreenSize.COMPACT
}

/**
 * Adaptive padding based on screen size.
 * - Compact: 16dp
 * - Medium: 24dp
 * - Expanded: 32dp
 */
@Composable
fun adaptivePadding(): Dp {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 16.dp
        ScreenSize.MEDIUM -> 24.dp
        ScreenSize.EXPANDED -> 32.dp
    }
}

/**
 * Adaptive horizontal padding for content areas.
 */
@Composable
fun adaptiveHorizontalPadding(): Dp {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 16.dp
        ScreenSize.MEDIUM -> 32.dp
        ScreenSize.EXPANDED -> 48.dp
    }
}

/**
 * Adaptive maximum content width for improved readability on large screens.
 * Returns null for compact screens (use full width).
 */
@Composable
fun adaptiveMaxContentWidth(): Dp? {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> null
        ScreenSize.MEDIUM -> 720.dp
        ScreenSize.EXPANDED -> 900.dp
    }
}

/**
 * Number of columns for grid layouts.
 */
@Composable
fun adaptiveGridColumns(): Int {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 2
        ScreenSize.MEDIUM -> 3
        ScreenSize.EXPANDED -> 4
    }
}

/**
 * Wrapper modifier that constrains content width on tablets
 * while keeping phone layouts unchanged.
 */
@Composable
fun Modifier.adaptiveWidth(): Modifier {
    val maxWidth = adaptiveMaxContentWidth()
    return if (maxWidth != null) {
        this.widthIn(max = maxWidth)
    } else {
        this.fillMaxWidth()
    }
}

/**
 * Adaptive container that centers content on tablets with maximum width.
 */
@Composable
fun AdaptiveContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val screenSize = rememberScreenSize()
    val horizontalPadding = adaptiveHorizontalPadding()
    
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.TopCenter
    ) {
        val maxContentWidth = when (screenSize) {
            ScreenSize.COMPACT -> maxWidth
            ScreenSize.MEDIUM -> minOf(maxWidth, 720.dp)
            ScreenSize.EXPANDED -> minOf(maxWidth, 900.dp)
        }
        
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.widthIn(max = maxContentWidth)
        ) {
            content()
        }
    }
}

/**
 * Adaptive spacing between items in lists.
 */
@Composable
fun adaptiveItemSpacing(): Dp {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 8.dp
        ScreenSize.MEDIUM -> 12.dp
        ScreenSize.EXPANDED -> 16.dp
    }
}

/**
 * Adaptive card elevation for depth perception on tablets.
 */
@Composable
fun adaptiveCardElevation(): Dp {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 1.dp
        ScreenSize.MEDIUM -> 2.dp
        ScreenSize.EXPANDED -> 4.dp
    }
}
