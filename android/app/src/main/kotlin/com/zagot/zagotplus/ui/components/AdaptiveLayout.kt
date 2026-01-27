package com.zagot.zagotplus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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

/**
 * Adaptive button height for kiosk use.
 * Larger buttons on tablets for easier touch targeting.
 */
@Composable
fun adaptiveButtonHeight(): Dp {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 56.dp      // Standard phone button
        ScreenSize.MEDIUM -> 72.dp       // Larger for small tablets
        ScreenSize.EXPANDED -> 80.dp     // Extra large for kiosk
    }
}

/**
 * Adaptive primary button height (main action buttons).
 * Even larger for critical actions like "Add Position" or "Finalize".
 */
@Composable
fun adaptivePrimaryButtonHeight(): Dp {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 64.dp      // Phone primary button
        ScreenSize.MEDIUM -> 80.dp       // Tablet primary
        ScreenSize.EXPANDED -> 96.dp     // Kiosk primary - very prominent
    }
}

/**
 * Adaptive text field minimum height.
 * Taller fields on tablets for easier input.
 */
@Composable
fun adaptiveTextFieldMinHeight(): Dp {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 56.dp
        ScreenSize.MEDIUM -> 64.dp
        ScreenSize.EXPANDED -> 72.dp
    }
}

/**
 * Typography scale for highlighted text (totals, prices).
 * Larger display text on tablets for visibility.
 */
@Composable
fun adaptiveDisplayScale(): Float {
    return when (rememberScreenSize()) {
        ScreenSize.COMPACT -> 1.0f
        ScreenSize.MEDIUM -> 1.15f
        ScreenSize.EXPANDED -> 1.3f
    }
}

/**
 * Two-column layout for tablets, single column for phones.
 * Used for input/output split (e.g., weight entry + total display).
 *
 * On phones: Content stacks vertically (leftContent then rightContent)
 * On tablets: Content splits into two columns side-by-side
 *
 * @param modifier Modifier for the container
 * @param leftWeight Weight of left column (0.0-1.0), right gets remainder
 * @param spacing Horizontal spacing between columns on tablets
 * @param verticalAlignment Vertical alignment of columns on tablets
 * @param leftContent Content for left column (or top on phones)
 * @param rightContent Content for right column (or bottom on phones)
 */
@Composable
fun AdaptiveTwoColumn(
    modifier: Modifier = Modifier,
    leftWeight: Float = 0.55f,
    spacing: Dp = 24.dp,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    leftContent: @Composable ColumnScope.() -> Unit,
    rightContent: @Composable ColumnScope.() -> Unit
) {
    if (isTablet()) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = verticalAlignment
        ) {
            Column(
                modifier = Modifier.weight(leftWeight)
            ) {
                leftContent()
            }
            Column(
                modifier = Modifier.weight(1f - leftWeight)
            ) {
                rightContent()
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            leftContent()
            rightContent()
        }
    }
}

/**
 * Master-detail layout for tablets: scrollable list on left, fixed panel on right.
 * Falls back to stacked layout on phones where detail appears below master.
 *
 * On phones: masterContent fills available space, detailContent at bottom
 * On tablets: masterContent on left (scrollable), detailContent fixed on right
 *
 * @param modifier Modifier for the container
 * @param masterWeight Weight of master (list) area (0.0-1.0)
 * @param spacing Horizontal spacing between panels on tablets
 * @param masterContent The scrollable list content
 * @param detailContent The fixed detail/action panel content
 */
@Composable
fun AdaptiveMasterDetail(
    modifier: Modifier = Modifier,
    masterWeight: Float = 0.6f,
    spacing: Dp = 24.dp,
    masterContent: @Composable ColumnScope.() -> Unit,
    detailContent: @Composable ColumnScope.() -> Unit
) {
    if (isTablet()) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            Column(
                modifier = Modifier
                    .weight(masterWeight)
                    .fillMaxHeight()
            ) {
                masterContent()
            }
            Column(
                modifier = Modifier
                    .weight(1f - masterWeight)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                detailContent()
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                masterContent()
            }
            detailContent()
        }
    }
}
