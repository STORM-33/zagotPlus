package com.zagot.zagotplus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * Centralized spacing values for consistent UI spacing throughout the app.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}

/**
 * Centralized corner radius values for consistent rounded corners.
 */
object Corners {
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    
    val smallShape = RoundedCornerShape(small)
    val mediumShape = RoundedCornerShape(medium)
    val largeShape = RoundedCornerShape(large)
}

/**
 * Centralized elevation values for consistent card/surface elevation.
 */
object Elevation {
    val none = 0.dp
    val low = 2.dp
    val medium = 4.dp
    val high = 8.dp
}

/**
 * Touch target sizes for accessibility.
 */
object TouchTargets {
    val minimum = 48.dp
    val comfortable = 56.dp
    val large = 64.dp
}
