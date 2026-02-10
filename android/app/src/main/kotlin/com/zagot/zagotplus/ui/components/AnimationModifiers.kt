package com.zagot.zagotplus.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import java.math.BigDecimal

/**
 * Wrapper that animates list items appearing/disappearing.
 * Used around cards in LazyColumn items for smooth realtime sync transitions.
 *
 * Items fade in + expand vertically on appearance.
 * Combine with Modifier.animateItemPlacement() on the parent for reorder animation.
 */
@Composable
fun AnimatedListItem(
    modifier: Modifier = Modifier,
    durationMs: Int = 300,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMs)) +
                expandVertically(
                    animationSpec = tween(durationMs),
                    expandFrom = Alignment.Top,
                ),
        exit = fadeOut(animationSpec = tween(durationMs)) +
                shrinkVertically(
                    animationSpec = tween(durationMs),
                    shrinkTowards = Alignment.Top,
                ),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * A modifier that adds a bouncy scale animation on press for tactile feedback.
 * The element scales down when pressed and springs back when released.
 */
fun Modifier.bouncyClick(
    enabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = 400f
        ),
        label = "bouncyScale"
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            if (enabled) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = { onClick() }
                )
            }
        }
}

/**
 * Animated text that smoothly transitions between value changes using slide+fade.
 * Used for numeric displays (balance, weights, totals) that update via sync.
 */
@Composable
fun AnimatedValueText(
    targetValue: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    AnimatedContent(
        targetState = targetValue,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically { -it / 2 })
                .togetherWith(fadeOut(tween(200)) + slideOutVertically { it / 2 })
        },
        label = "valueChange"
    ) { value ->
        Text(
            text = value,
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
        )
    }
}

/**
 * Rolling number counter that springs from current value to target.
 * Used for numeric displays (weights, amounts, totals) that change via sync.
 *
 * @param targetValue the number to animate toward
 * @param formatter converts the animated BigDecimal to display string (e.g., "1,234.56 кг")
 * @param modifier standard modifier
 * @param style text style
 * @param color text color
 * @param fontWeight optional font weight
 * @param textAlign optional text alignment
 * @param stiffness spring stiffness — lower = slower/smoother, higher = snappier. Default 300f.
 */
@Composable
fun AnimatedCounter(
    targetValue: BigDecimal,
    formatter: (BigDecimal) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    stiffness: Float = 300f,
    animate: Boolean = true,
) {
    if (!animate) {
        Text(
            text = formatter(targetValue),
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
        )
    } else {
        val animatable = remember { Animatable(targetValue.toFloat()) }

        LaunchedEffect(targetValue) {
            animatable.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = spring(stiffness = stiffness)
            )
        }

        Text(
            text = formatter(BigDecimal(animatable.value.toDouble())),
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            textAlign = textAlign,
        )
    }
}
