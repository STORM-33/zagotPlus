package com.zagot.zagotplus.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardTab
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Custom numeric keypad for tablet kiosk mode.
 * Fills available space dynamically using weights.
 * 
 * Layout (4 columns, action button spans 2 rows):
 * ```
 * 1   2   3   ⌫
 * 4   5   6   NEXT
 * 7   8   9   ║
 * 0   .       ║ + / ✓
 * ```
 */
@Composable
fun CustomNumpad(
    onNumberClick: (Char) -> Unit,
    onDecimalClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onNextFieldClick: () -> Unit,
    onActionClick: () -> Unit,
    actionEnabled: Boolean = true,
    isEditMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val spacing = 6.dp
    
    // Action button color: yellow for edit mode, primary for add mode
    val actionButtonColor = if (isEditMode) {
        Color(0xFFFFC107) // Amber/Yellow for edit
    } else {
        MaterialTheme.colorScheme.primary
    }
    val actionContentColor = if (isEditMode) {
        Color.Black
    } else {
        MaterialTheme.colorScheme.onPrimary
    }

    Row(
        modifier = modifier.fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(spacing)
    ) {
        // Left side: 3 columns of digits
        Column(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Row 1: 1 2 3
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                NumpadDigitButton('1', onNumberClick, haptic)
                NumpadDigitButton('2', onNumberClick, haptic)
                NumpadDigitButton('3', onNumberClick, haptic)
            }

            // Row 2: 4 5 6
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                NumpadDigitButton('4', onNumberClick, haptic)
                NumpadDigitButton('5', onNumberClick, haptic)
                NumpadDigitButton('6', onNumberClick, haptic)
            }

            // Row 3: 7 8 9
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                NumpadDigitButton('7', onNumberClick, haptic)
                NumpadDigitButton('8', onNumberClick, haptic)
                NumpadDigitButton('9', onNumberClick, haptic)
            }

            // Row 4: 0 (wide) .
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                NumpadWideDigitButton('0', onNumberClick, haptic, weight = 2f)
                NumpadTextButton(
                    text = ".",
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDecimalClick() 
                    }
                )
            }
        }

        // Right side: 1 column with Backspace, Next, and tall Action button
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            // Backspace
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackspaceClick() 
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, "Видалити", Modifier.size(28.dp))
                }
            }
            
            // Next field
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNextFieldClick() 
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.KeyboardTab, "Далі", Modifier.size(28.dp))
                }
            }
            
            // Tall Action button (spans remaining 2 rows weight)
            Button(
                onClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onActionClick() 
                },
                enabled = actionEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f), // Two rows worth
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = actionButtonColor,
                    contentColor = actionContentColor
                )
            ) {
                // Use icon instead of text
                Icon(
                    imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = if (isEditMode) "Змінити" else "Додати",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.NumpadDigitButton(
    digit: Char,
    onClick: (Char) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick(digit) 
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RowScope.NumpadWideDigitButton(
    digit: Char,
    onClick: (Char) -> Unit,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    weight: Float
) {
    Card(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable { 
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick(digit) 
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RowScope.NumpadTextButton(
    text: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
