package com.zagot.zagotplus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A "fake" text field that displays value but suppresses the system keyboard.
 * Used with CustomNumpad for direct input in tablet kiosk mode.
 * 
 * On click: clears the value (via onClearAndSelect callback) and selects this field.
 */
@Composable
fun InputDisplayBox(
    label: String,
    value: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    suffix: String = "",
    placeholder: String = "0"
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            isActive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        },
        animationSpec = tween(150),
        label = "borderColor"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive && enabled) 2.dp else 1.dp,
        animationSpec = tween(150),
        label = "borderWidth"
    )
    
    // Keep background white/surface, only highlight border
    val containerColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier.then(
            if (enabled) Modifier.clickable(onClick = onClick) else Modifier
        ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                // Value with optional blinking cursor
                val displayValue = value.ifEmpty { placeholder }
                val cursorColor = MaterialTheme.colorScheme.primary
                val textColor = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    value.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
                
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    modifier = if (isActive && enabled) {
                        Modifier.drawBehind {
                            // Draw cursor line at the end
                            val cursorX = size.width + 2.dp.toPx()
                            drawLine(
                                color = cursorColor,
                                start = Offset(cursorX, 4.dp.toPx()),
                                end = Offset(cursorX, size.height - 4.dp.toPx()),
                                strokeWidth = 2.dp.toPx()
                            )
                        }
                    } else Modifier
                )
                
                if (suffix.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }
            }
        }
    }
}
